package org.bmc4j.junit;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
import org.bmc4j.BmcProof;
import org.bmc4j.engine.BmcRequest;
import org.bmc4j.engine.BmcUndecidedError;
import org.bmc4j.engine.BmcVerificationError;
import org.bmc4j.engine.JbmcResult;
import org.bmc4j.engine.ModelManifest;
import org.bmc4j.engine.ModelPolicy;
import org.bmc4j.engine.StubPolicy;
import org.bmc4j.engine.UserModel;
import org.bmc4j.engine.VerdictCache;
import org.bmc4j.engine.ReplayRenderer;
import org.bmc4j.engine.ReplayTestWriter;
import org.bmc4j.engine.VerificationBackend;
import org.bmc4j.engine.VerificationBackends;

import java.lang.reflect.Method;
import java.util.ArrayList;

/**
 * Runs a {@link BmcProof} method through a model-checking {@link VerificationBackend}
 * instead of executing its body. The extension is engine-agnostic: it builds a
 * {@link BmcRequest} from the proof and the test JVM's classpath
 * ({@code java.class.path}) and hands it to the selected backend (default JBMC;
 * {@code -Dbmc.backend=esbmc} to switch). On a violation, the proof fails with a
 * {@link BmcVerificationError} carrying a synthesized stack trace.
 */
public class BmcProofExtension implements InvocationInterceptor, ParameterResolver {

    private static final String UNWIND_PROP = "bmc.unwind";
    private static final String MAX_STRING_PROP = "bmc.maxStringLength";
    private static final String TIMEOUT_PROP = "bmc.timeoutSeconds";
    private static final int DEFAULT_UNWIND = 16;
    private static final int DEFAULT_MAX_STRING = 16;
    private static final int DEFAULT_TIMEOUT = 0; // 0 = no timeout (run to completion)

    private static final String SOLVER_PROP = "bmc.solver";

    // --- Symbolic parameters --------------------------------------------------
    // A @BmcProof method may declare parameters; JBMC treats the entry function's
    // parameters as nondeterministic inputs (objects, strings, arrays included).
    // We never actually execute the body, so the values returned here are unused
    // placeholders that only satisfy JUnit's invocation machinery.

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        // Defer JUnit's own injected types (TestInfo, etc.) to their resolvers.
        String typeName = parameterContext.getParameter().getType().getName();
        return !typeName.startsWith("org.junit.");
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        Class<?> type = parameterContext.getParameter().getType();
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == boolean.class) return false;
        if (type == double.class) return 0.0;
        if (type == float.class) return 0.0f;
        if (type == short.class) return (short) 0;
        if (type == byte.class) return (byte) 0;
        if (type == char.class) return '\0';
        return null; // reference types: placeholder; JBMC supplies the real nondet value
    }

    @Override
    public void interceptTestMethod(Invocation<Void> invocation,
                                    ReflectiveInvocationContext<Method> invocationContext,
                                    ExtensionContext extensionContext) throws Throwable {
        // We verify the method rather than execute it.
        invocation.skip();

        Method method = invocationContext.getExecutable();
        String entryClass = method.getDeclaringClass().getName();
        String entryFunction = entryClass + "." + method.getName();
        BmcProof config = method.getAnnotation(BmcProof.class);
        BmcRequest request = requestFor(entryClass, entryFunction, config);

        // JBMC backend (symbolic, all-inputs). For concurrency correctness, see the
        // README's Lincheck guidance — @BmcProof proves logic soundness.
        VerificationBackend backend = VerificationBackends.select(request);

        // Verdict cache: a proof's verdict is a pure function of its inputs, so a green
        // proof whose inputs haven't changed need not be re-verified. Consult the cache on the
        // per-proof path; on a VERIFIED hit, skip the engine entirely. Reds are NEVER cached, so this
        // can only ever short-circuit a green. Fail-open: any cache error just falls through to a run.
        // The cache key uses the EFFECTIVE solver (per-proof override else -Dbmc.solver) so a
        // -Dbmc.solver change invalidates even with unchanged bytecode (cf. unwind/maxStringLength,
        // which are already effective on the request).
        String engineIdentity = backend.engineIdentity() + solverEnvSuffix();
        BmcRequest cacheRequest = cacheKeyRequest(request, config);
        org.bmc4j.Verdict expected = config.expect();
        VerdictCache.Hit hit = VerdictCache.lookupVerified(cacheRequest, engineIdentity);
        if (hit != null) {
            // Cached green: only ever a VERIFIED verdict. With a non-VERIFIED expectation that's an
            // expectation mismatch (judged fresh each run — the cache stores the FACT, never the pass).
            if (expected != org.bmc4j.Verdict.VERIFIED) {
                throw expectationMismatch(entryFunction, expected, org.bmc4j.Verdict.VERIFIED, null);
            }
            // Report passed without an engine run. (Progress line annotates "cached".)
            // The stub policy is RE-JUDGED here from the stored stub fact, so flipping
            // strictStubs / editing allowStubs re-decides without an engine re-run. A strict-mode
            // unacknowledged stub still turns a cached green into UNKNOWN.
            System.out.println("  bmc4j: " + entryFunction + " -> VERIFIED (cached)");
            applyModelPolicy(entryFunction);
            applyStubPolicy(entryFunction, config, hit.stubbedMethods());
            return;
        }

        JbmcResult result;
        try {
            result = backend.verify(request);
        } catch (BmcUndecidedError e) {
            // A pre-framed UNKNOWN out of the backend. Judged against the expectation like any
            // other verdict (an engine-INFRASTRUCTURE unknown never satisfies expected-UNKNOWN).
            enforceExpectation(entryFunction, expected, org.bmc4j.Verdict.UNKNOWN, e);
            return;
        } catch (BmcVerificationError e) {
            // A genuine pre-framed refutation — never reclassify; judge against the expectation.
            enforceExpectation(entryFunction, expected, org.bmc4j.Verdict.REFUTED, e);
            return;
        } catch (RuntimeException | Error e) {
            // Engine-INFRASTRUCTURE failure: the engine couldn't run / produce a verdict (e.g.
            // BundledEngine.extract() IOException, process start failure, a non-verdict
            // IllegalStateException out of Jbmc.exec). That is NOT a refutation — there is no
            // counterexample, nothing was proven wrong. Per the three-way verdict it
            // must surface as UNKNOWN (BmcUndecidedError), reserving REFUTED for an actual JBMC
            // counterexample. Reclassify it here, preserving the original cause for diagnosis.
            // (enforceExpectation rejects it for expected-UNKNOWN: a broken engine must never
            // masquerade as an undecidability demo.)
            enforceExpectation(entryFunction, expected,
                    org.bmc4j.Verdict.UNKNOWN, engineInfraUndecided(backend.id(), entryFunction, e));
            return;
        }

        // Store ONLY a VERIFIED verdict (storeIfVerified is a no-op for REFUTED/UNKNOWN/vacuous) so a
        // later unchanged run can skip the engine. Fail-open: a write error never affects the verdict.
        // Note this stores the FACT even when the expectation is non-VERIFIED — the expectation is
        // re-judged on every run (including cache hits), so a mismatch can never be cached away.
        VerdictCache.storeIfVerified(cacheRequest, engineIdentity, result);

        org.bmc4j.Verdict actual = actualVerdict(result);
        if (actual != org.bmc4j.Verdict.VERIFIED) {
            enforceExpectation(entryFunction, expected, actual,
                    toError(backend.id(), entryFunction, result, method));
            return;
        }
        if (expected != org.bmc4j.Verdict.VERIFIED) {
            // The dangerous drift: a fail-on-purpose proof came back green — the false claim has
            // stopped being refutable. Loud, named failure.
            throw expectationMismatch(entryFunction, expected, org.bmc4j.Verdict.VERIFIED, null);
        }
        // Verified as expected: apply the user-model trust policy FIRST (provenance footnote for
        // declared models, loud warning for an override, UNKNOWN under strictModels for an undeclared
        // override), then the nondet-stub policy. Default lenient mode keeps the proof green and prints
        // a one-line footnote for any unacknowledged stub (loud for a user-package stub); strictStubs
        // turns an unacknowledged stub into UNKNOWN (BmcUndecidedError). Acknowledged (allowStubs)
        // stubs are silent. A fully-modeled proof with no user models prints nothing.
        applyModelPolicy(entryFunction);
        applyStubPolicy(entryFunction, config, result.stubbedMethods());
    }

    /** Map an engine result onto the user-facing four-way verdict (vacuity is carried as a
     *  flavour of REFUTED internally, but is its own expectation externally). */
    static org.bmc4j.Verdict actualVerdict(JbmcResult result) {
        if (result.isVerified()) {
            return org.bmc4j.Verdict.VERIFIED;
        }
        if (result.isVacuous()) {
            return org.bmc4j.Verdict.VACUOUS;
        }
        if (result.isUnknown()) {
            // A wall-clock expiry is the structured TIMEOUT subtype; other undecided causes
            // (solver crash, unparseable output) stay plain UNKNOWN.
            return result.isTimeout() ? org.bmc4j.Verdict.TIMEOUT : org.bmc4j.Verdict.UNKNOWN;
        }
        return org.bmc4j.Verdict.REFUTED;
    }

    /**
     * Judge an actual non-VERIFIED verdict against the proof's expectation.
     *
     * <ul>
     *   <li><b>Match</b> → the proof PASSES (the framed error is swallowed; a confirmation line is
     *       printed so the run log still shows the real verdict). Exception: an engine-INFRASTRUCTURE
     *       UNKNOWN never satisfies expected-UNKNOWN — a broken engine isn't an undecidability demo.</li>
     *   <li><b>Expectation is VERIFIED</b> (the default) → rethrow the framed error unchanged: exactly
     *       the pre-{@code expect} behavior.</li>
     *   <li><b>Mismatch between two non-VERIFIED verdicts</b> → fail naming both, with the framed
     *       error attached as the cause.</li>
     * </ul>
     */
    static void enforceExpectation(String entryFunction, org.bmc4j.Verdict expected,
                                   org.bmc4j.Verdict actual, BmcVerificationError framed) {
        if (expected == org.bmc4j.Verdict.VERIFIED) {
            throw framed; // normal proof: any non-verified verdict fails as before
        }
        boolean infra = framed instanceof BmcUndecidedError
                && ((BmcUndecidedError) framed).isEngineInfrastructure();
        // TIMEOUT is the structured subtype of UNKNOWN: expect=UNKNOWN accepts a timeout too;
        // expect=TIMEOUT requires the budget to have actually fired.
        boolean match = expected == actual
                || (expected == org.bmc4j.Verdict.UNKNOWN && actual == org.bmc4j.Verdict.TIMEOUT);
        if (match && !infra) {
            System.out.println("  bmc4j: " + entryFunction + " -> " + actual + " (as expected)");
            return; // the declared verdict arrived: the fail-on-purpose proof passes
        }
        if (match) {
            // infra-UNKNOWN offered for expected-UNKNOWN/TIMEOUT: reject with the infra framing intact.
            BmcVerificationError err = new BmcVerificationError(
                    entryFunction + " expected " + expected + ", but the engine infrastructure failed before"
                            + " producing a verdict - that is not a real " + expected + " (fix the engine, then re-run)");
            err.initCause(framed);
            throw err;
        }
        throw expectationMismatch(entryFunction, expected, actual, framed);
    }

    /** A loud, both-verdicts-named expectation failure. */
    static BmcVerificationError expectationMismatch(String entryFunction, org.bmc4j.Verdict expected,
                                                    org.bmc4j.Verdict actual, BmcVerificationError cause) {
        StringBuilder sb = new StringBuilder();
        sb.append(entryFunction).append(" expected ").append(expected)
                .append(", got ").append(actual).append('\n');
        if (actual == org.bmc4j.Verdict.VERIFIED) {
            sb.append("  ! a fail-on-purpose proof came back green: the false claim has stopped being\n")
                    .append("    refutable - the desugar/guard this demo protects may have regressed.");
        } else {
            sb.append("  ! the proof still fails, but not the way it declares - inspect the attached\n")
                    .append("    cause and either fix the regression or update expect() if the new verdict\n")
                    .append("    is genuinely intended.");
        }
        BmcVerificationError err = new BmcVerificationError(sb.toString());
        if (cause != null) {
            err.initCause(cause);
        }
        return err;
    }

    private static final String STRICT_STUBS_PROP = "bmc.strictStubs";
    private static final String ALLOW_STUBS_PROP = "bmc.allowStubs";
    private static final String USER_PACKAGES_PROP = "bmc.userPackages";

    /**
     * Apply the stub policy to a VERIFIED proof's harvested stub list. Pure split of
     * fact-vs-policy: the same logic runs on a fresh result and on a cache hit (so re-judging is free).
     *
     * <ul>
     *   <li><b>lenient (default):</b> print a footnote listing the unacknowledged stubs; the proof stays
     *       green. A stub from the user's own package prints a louder config-bug warning.</li>
     *   <li><b>strict ({@code -Dbmc.strictStubs=true}):</b> any unacknowledged stub throws UNKNOWN
     *       (a {@link BmcUndecidedError}) with the stub list and the three remedies.</li>
     *   <li>Acknowledged stubs (per-proof {@code allowStubs} + build-wide {@code -Dbmc.allowStubs}) are
     *       silent in both modes.</li>
     * </ul>
     */
    static void applyStubPolicy(String entryFunction, BmcProof config, java.util.List<String> stubbed) {
        if (stubbed == null || stubbed.isEmpty()) {
            return;
        }
        StubPolicy policy = StubPolicy.judge(stubbed, effectiveAllowStubs(config),
                System.getProperty(USER_PACKAGES_PROP, ""));
        if (!policy.hasUnacknowledged()) {
            return; // every reached stub is acknowledged
        }
        if (strictStubs()) {
            throw new BmcUndecidedError(strictStubMessage(entryFunction, policy));
        }
        // Lenient: footnote (loud for user-package stubs), green either way.
        System.out.println(footnote(entryFunction, policy));
    }

    private static final String STRICT_MODELS_PROP = "bmc.strictModels";

    /**
     * Apply the user-model TRUST policy to a VERIFIED proof. Sibling of {@link #applyStubPolicy}: same
     * fact-vs-policy split (the facts — declared intent + the models present under {@code src/bmcModel} —
     * come from {@link ModelManifest}, read identically on a fresh result and a cache hit), and the same
     * footnote → warn → strict ladder.
     *
     * <ul>
     *   <li><b>provenance footnote (lenient + strict):</b> name every declared user model on this
     *       proof's analysis classpath; for {@code domain} models append the declared rationale, so a
     *       green proof that rests on an intentional divergence says so.</li>
     *   <li><b>override warning (lenient + strict):</b> a present user model shadowing a bundled/JDK
     *       verified model is warned loudly — you've replaced a checked stand-in with an unchecked one.</li>
     *   <li><b>strict ({@code -Dbmc.strictModels=true}):</b> a present user model with no intent
     *       declaration turns the verdict into UNKNOWN — no proof silently rests on an undeclared
     *       override.</li>
     * </ul>
     *
     * <p>Granularity note: relevance is "the user model was on this proof's analysis classpath", not
     * "provably called by this proof" — JBMC emits no per-proof which-model-was-linked report (only the
     * nondet-stub messages the stub policy uses), so this can over-attribute a model to a proof in the
     * same module that didn't actually touch it. The footnote wording is classpath-scoped accordingly.
     */
    static void applyModelPolicy(String entryFunction) {
        ModelManifest manifest = ModelManifest.fromSystemProperties();
        if (manifest.isEmpty()) {
            return; // no user models registered or present — nothing to surface
        }
        ModelPolicy policy = ModelPolicy.judge(manifest);
        if (!policy.hasAnyPresent()) {
            return;
        }
        if (strictModels() && policy.hasUndeclared()) {
            throw new BmcUndecidedError(strictModelMessage(entryFunction, policy));
        }
        System.out.println(modelFootnote(entryFunction, policy));
    }

    /** Whether strict-model mode is on: {@code -Dbmc.strictModels=true} (forwarded from {@code bmc {}}). */
    private static boolean strictModels() {
        return Boolean.parseBoolean(System.getProperty(STRICT_MODELS_PROP, "false"));
    }

    /** The lenient/strict provenance footnote: names the user models a green proof rested on. */
    private static String modelFootnote(String entryFunction, ModelPolicy policy) {
        StringBuilder sb = new StringBuilder();
        sb.append("  bmc4j: ").append(entryFunction)
                .append(" -> VERIFIED under user model(s) on the analysis classpath:");
        for (UserModel m : policy.declaredPresent()) {
            sb.append("\n      ");
            if (m.isDomain()) {
                sb.append("domain model ").append(m.className())
                        .append(" (assumes ").append(m.rationale()).append(')');
            } else {
                sb.append("conformant model ").append(m.className())
                        .append(" (claims JDK fidelity)");
            }
        }
        for (String undeclared : policy.undeclaredPresent()) {
            sb.append("\n      UNDECLARED model ").append(undeclared)
                    .append(" — no bmc { models { … } } intent; declare it conformant(...) or"
                            + " domain(\"why\"), or run -Dbmc.strictModels=true to fail on it.");
        }
        if (policy.hasOverriding()) {
            sb.append("\n  bmc4j: WARNING ").append(entryFunction)
                    .append(" shadows a bundled/verified model with an UNCHECKED user model: ")
                    .append(String.join(", ", policy.overriding()))
                    .append(" — your stand-in replaces bmc4j's verified one; verify it"
                            + " (conformant models can run the same conformance harness as bundled models)"
                            + " or mark it domain(\"why\") if the divergence is intentional.");
        }
        return sb.toString();
    }

    /** The strict-mode UNKNOWN message for an undeclared user-model override. */
    private static String strictModelMessage(String entryFunction, ModelPolicy policy) {
        StringBuilder sb = new StringBuilder();
        sb.append("JBMC could not trust ").append(entryFunction).append(" (UNKNOWN)\n");
        sb.append("  ? strictModels is on and this proof's classpath includes ")
                .append(policy.undeclaredPresent().size())
                .append(" undeclared user model(s):\n");
        sb.append("      ").append(String.join(", ", policy.undeclaredPresent())).append('\n');
        sb.append("    A user model is Bmc.assume() at classpath altitude — it can silently change what\n")
                .append("    the proof means. Without a declared intent the verdict isn't trustworthy.\n")
                .append("    No counterexample: this is NOT a refutation. Declare the model's intent:\n")
                .append("      - bmc { models { conformant(\"")
                .append(policy.undeclaredPresent().get(0))
                .append("\") } }  (it claims JDK fidelity — verifiable by the conformance harness); or\n")
                .append("      - bmc { models { domain(\"")
                .append(policy.undeclaredPresent().get(0))
                .append("\", \"why it diverges\") } }  (intentional divergence, footnoted on green proofs); or\n")
                .append("      - remove it from src/bmcModel if it shouldn't be shadowing.");
        return sb.toString();
    }

    /** Whether strict-stub mode is on: {@code -Dbmc.strictStubs=true} (forwarded from {@code bmc {}}). */
    private static boolean strictStubs() {
        return Boolean.parseBoolean(System.getProperty(STRICT_STUBS_PROP, "false"));
    }

    /** Per-proof {@code @BmcProof(allowStubs=…)} merged with build-wide {@code -Dbmc.allowStubs} (CSV). */
    static java.util.List<String> effectiveAllowStubs(BmcProof config) {
        java.util.List<String> out = new ArrayList<>();
        if (config != null && config.allowStubs() != null) {
            for (String s : config.allowStubs()) {
                if (s != null && !s.isBlank()) {
                    out.add(s.trim());
                }
            }
        }
        String build = System.getProperty(ALLOW_STUBS_PROP, "");
        for (String s : build.split(",")) {
            if (!s.isBlank()) {
                out.add(s.trim());
            }
        }
        return out;
    }

    /** The lenient-mode footnote: a one-line deprecation-style warning under the (passed) proof. */
    private static String footnote(String entryFunction, StubPolicy policy) {
        java.util.List<String> unack = policy.unacknowledged();
        StringBuilder sb = new StringBuilder();
        sb.append("  bmc4j: ").append(entryFunction).append(" -> VERIFIED with ")
                .append(unack.size()).append(unack.size() == 1 ? " nondet stub" : " nondet stubs")
                .append(" (verdict assumes they're pure / never throw):\n");
        sb.append("      ").append(String.join(", ", unack));
        if (policy.hasUserOwned()) {
            // A stub from the user's OWN classpath is almost always a missing dependency — warn loud
            // even in lenient mode (it's a config bug, not a JDK modeling gap).
            sb.append("\n  bmc4j: WARNING ").append(entryFunction)
                    .append(" stubbed a method from YOUR OWN classpath — likely a missing dependency,"
                            + " not a modeling gap: ")
                    .append(String.join(", ", policy.userOwned()));
        }
        sb.append("\n      acknowledge with @BmcProof(allowStubs = {\"")
                .append(unack.get(0)).append("\"}) or bmc { allowStubs = [...] }; model it in bmc-models;"
                        + " or run -Dbmc.strictStubs=true to fail on it.");
        return sb.toString();
    }

    /** The strict-mode UNKNOWN message: stub list + the three remedies (model / allowStubs / restructure). */
    private static String strictStubMessage(String entryFunction, StubPolicy policy) {
        StringBuilder sb = new StringBuilder();
        sb.append("JBMC could not decide ").append(entryFunction).append(" (UNKNOWN)\n");
        sb.append("  ? strictStubs is on and this proof reached ").append(policy.unacknowledged().size())
                .append(" unacknowledged nondet stub(s):\n");
        sb.append("      ").append(String.join(", ", policy.unacknowledged())).append('\n');
        if (policy.hasUserOwned()) {
            sb.append("      (").append(String.join(", ", policy.userOwned()))
                    .append(" is from YOUR OWN classpath — likely a missing dependency)\n");
        }
        sb.append("    The verdict rests on havoc'd stand-ins for those methods, so it isn't trustworthy.\n")
                .append("    No counterexample: this is NOT a refutation. To get a sound decision, either:\n")
                .append("      - model it: add a bounded stand-in in bmc-models / src/bmcModel; or\n")
                .append("      - acknowledge it: @BmcProof(allowStubs = {\"")
                .append(policy.unacknowledged().get(0))
                .append("\"}) or bmc { allowStubs = [...] } (if nondet is sound for what you prove); or\n")
                .append("      - restructure the proof so the method isn't reached.");
        return sb.toString();
    }

    /**
     * The request used for the verdict-cache key: identical to {@code request} but with the EFFECTIVE
     * solver baked into the solver field (per-proof {@code @BmcProof(solver=…)} else {@code -Dbmc.solver},
     * else ""). The plain {@code request} carries only the per-proof override, so without this a change to
     * the build-wide {@code -Dbmc.solver} default would not invalidate cached verdicts. unwind
     * and maxStringLength are already effective on {@code request}, so they need no adjustment here.
     */
    static BmcRequest cacheKeyRequest(BmcRequest request, BmcProof config) {
        String effSolver = effectiveSolver(config);
        if (effSolver.equals(request.solver())) {
            return request;
        }
        return new BmcRequest(request.entryClass(), request.entryFunction(), request.classpath(),
                request.unwind(), request.unwindingAssertions(), request.maxStringLength(),
                request.concurrent(), effSolver, request.timeoutSeconds());
    }

    /**
     * Solver-related sysprops that change what the engine actually does but aren't on the request — the
     * external-SAT / external-SMT2 solver and the solver PATH dir. Folded into the cache's engine
     * identity so e.g. enabling an external SAT solver invalidates cached verdicts. Empty
     * when none are set (the common case), so it doesn't perturb the default key.
     */
    private static String solverEnvSuffix() {
        StringBuilder sb = new StringBuilder();
        appendProp(sb, "bmc.externalSat");
        appendProp(sb, "bmc.solverCmd");
        appendProp(sb, "bmc.solverPath");
        return sb.length() == 0 ? "" : "|" + sb;
    }

    private static void appendProp(StringBuilder sb, String key) {
        String v = System.getProperty(key);
        if (v != null && !v.isBlank()) {
            sb.append(key).append('=').append(v.trim()).append(';');
        }
    }

    static BmcRequest requestFor(String entryClass, String entryFunction, BmcProof config) {
        return new BmcRequest(
                entryClass,
                entryFunction,
                System.getProperty("java.class.path"),
                resolveUnwind(config),
                config == null || config.unwindingAssertions(),
                resolveMaxStringLength(config),
                config != null && config.concurrent(),
                config == null ? "" : config.solver(),
                resolveTimeoutSeconds(config));
    }

    /**
     * The solver a proof will actually run under: its per-proof {@code @BmcProof(solver=...)} override
     * if set, otherwise the build/{@code -Dbmc.solver} default ("" = jbmc's built-in MiniSat).
     */
    static String effectiveSolver(BmcProof config) {
        if (config != null && config.solver() != null && !config.solver().isBlank()) {
            return config.solver().trim();
        }
        String prop = System.getProperty(SOLVER_PROP);
        return (prop == null || prop.isBlank()) ? "" : prop.trim();
    }

    static int resolveUnwind(BmcProof config) {
        if (config != null && config.unwind() > 0) {
            return config.unwind();
        }
        return intProp(UNWIND_PROP, DEFAULT_UNWIND);
    }

    /**
     * The symbolic-string length bound a proof will actually run under: its per-proof
     * {@code @BmcProof(maxStringLength=...)} override if {@code > 0}, otherwise the
     * build/{@code -Dbmc.maxStringLength} default (else {@link #DEFAULT_MAX_STRING}).
     */
    static int resolveMaxStringLength(BmcProof config) {
        if (config != null && config.maxStringLength() > 0) {
            return config.maxStringLength();
        }
        return intProp(MAX_STRING_PROP, DEFAULT_MAX_STRING);
    }

    /**
     * The wall-clock budget a proof will actually run under: its per-proof
     * {@code @BmcProof(timeoutSeconds=...)} override if {@code > 0}, otherwise the
     * build/{@code -Dbmc.timeoutSeconds} default (else {@link #DEFAULT_TIMEOUT} = no timeout).
     */
    static int resolveTimeoutSeconds(BmcProof config) {
        if (config != null && config.timeoutSeconds() > 0) {
            return config.timeoutSeconds();
        }
        return intProp(TIMEOUT_PROP, DEFAULT_TIMEOUT);
    }

    /**
     * Parse an int-valued {@code bmc.*} system property, or {@code fallback} when unset. A malformed
     * value FAILS LOUDLY — this tool's ethos is visible-over-silent, so a typo'd verification config
     * (e.g. {@code -Dbmc.unwind=1o}) must break the build, not silently run at the default.
     */
    private static int intProp(String key, int fallback) {
        String v = System.getProperty(key);
        if (v == null || v.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Invalid value for -D" + key + ": \"" + v + "\" is not an integer", e);
        }
    }

    /**
     * Reclassify an engine-INFRASTRUCTURE failure as UNKNOWN. When the engine-run path
     * throws a non-verdict exception (the bundled engine couldn't be extracted, the jbmc process
     * couldn't be started, output couldn't be obtained, etc.) there is no counterexample — nothing was
     * proven wrong, the engine just couldn't run. Per the three-way verdict that is UNKNOWN, not
     * a refutation: we wrap it in a {@link BmcUndecidedError} whose message carries the {@code (UNKNOWN)}
     * tag (so the runner line prints {@code UNKNOWN}, not {@code REFUTED}) and the undecided framing,
     * keeping the original throwable as the cause for diagnosis. REFUTED stays reserved for a real
     * parsed JBMC counterexample (which never reaches here — it comes back as a {@link JbmcResult} and
     * is framed by {@link #toError}).
     */
    static BmcUndecidedError engineInfraUndecided(String engineId, String entryFunction, Throwable cause) {
        StringBuilder sb = new StringBuilder();
        sb.append(engineId.toUpperCase()).append(" could not decide ").append(entryFunction)
                .append(" (UNKNOWN)\n");
        String detail = cause == null ? null : cause.getMessage();
        if (detail == null && cause != null) {
            detail = cause.getClass().getName();
        }
        sb.append("  ? engine infrastructure failed before a verdict: ")
                .append(detail == null ? "unknown error" : detail).append('\n');
        sb.append("    No counterexample: this is NOT a refutation — the engine could not be run (couldn't\n")
                .append("    start / extract / produce output), so nothing was proven wrong. To get a\n")
                .append("    decision, fix the infrastructure cause above (e.g. the bundled engine could not\n")
                .append("    extract, or the jbmc process could not start) and re-run.");
        BmcUndecidedError err = new BmcUndecidedError(sb.toString().stripTrailing(), true);
        if (cause != null) {
            err.initCause(cause);
        }
        return err;
    }

    static BmcVerificationError toError(String engineId, String entryFunction,
                                        JbmcResult result, Method proofMethod) {
        StringBuilder sb = new StringBuilder();
        if (result.isUnknown()) {
            // UNKNOWN: undecided within budget — NOT a refutation, so no counterexample.
            // Distinct exception type + message so a resource-exhaustion in CI is never mistaken for
            // "your code is wrong". Still fails the test: absence of a verdict is not a proof.
            sb.append(engineId.toUpperCase()).append(" could not decide ").append(entryFunction)
                    .append(" (UNKNOWN)\n");
            String reason = result.undecidedReason();
            sb.append("  ? ").append(reason == null ? "undecided within budget" : reason).append('\n');
            sb.append("    No counterexample: this is NOT a refutation — the engine ran out of budget or\n")
                    .append("    fell over before reaching a verdict. To get a decision, try one of:\n")
                    .append("      - raise unwind (the loop bound may be too high to solve in time):"
                            + " @BmcProof(unwind = ...)\n")
                    .append("      - give it more time: @BmcProof(timeoutSeconds = ...) or"
                            + " bmc { timeoutSeconds = N } / -Dbmc.timeoutSeconds\n")
                    .append("      - shrink the symbolic range with assume(...) (tighter bit-vector"
                            + " circuits solve far faster)\n")
                    .append("      - split the proof into smaller independent ones\n")
                    .append("      - add a method contract (@Requires/@Ensures) for the heavy callee so"
                            + " it's summarized, not re-explored\n")
                    .append("      - swap to an external SAT solver for string-free numeric proofs"
                            + " (bmc { externalSat = \"<dimacs-solver>\" }); note JBMC's SMT/z3 path is"
                            + " inert on this engine.");
            return new BmcUndecidedError(sb.toString().stripTrailing());
        }
        if (result.isVacuous()) {
            // Vacuity: the proof's assumptions are unsatisfiable, so it verified over an
            // empty input domain and checked nothing. Surface that as its own verdict, not a "refuted".
            sb.append(engineId.toUpperCase()).append(" found ").append(entryFunction)
                    .append(" VACUOUS\n");
            sb.append("  ✗ ").append(org.bmc4j.engine.BmcReachability.VACUOUS_MESSAGE).append('\n');
            sb.append("    no input satisfies every assume(...) — tighten or fix the assumptions ")
                    .append("(a contradictory pair, or a bound too small for the literals).");
            return new BmcVerificationError(sb.toString().stripTrailing());
        }
        sb.append(engineId.toUpperCase()).append(" refuted ").append(entryFunction).append('\n');
        for (JbmcResult.Violation v : result.violations()) {
            sb.append("  ✗ ").append(v.description());
            if (v.file() != null) {
                sb.append("  (").append(shortFile(v.file())).append(':').append(v.line()).append(')');
            }
            sb.append('\n');
            if (!v.counterexample().isEmpty()) {
                sb.append("    counterexample: ").append(String.join(", ", v.counterexample())).append('\n');
            }
        }
        // Replay block: render the first violation's counterexample as concrete Java the
        // developer can paste into a scratch test and debug. Verified/UNKNOWN/vacuous never reach here.
        if (!result.violations().isEmpty()) {
            JbmcResult.Violation first = result.violations().get(0);
            String replay = ReplayRenderer.render(entryFunction, proofMethod, first);
            if (replay != null) {
                sb.append(replay).append('\n');
                // v2: also write a runnable @Test scratch file, and point at it.
                String file = ReplayTestWriter.write(entryFunction, proofMethod, first);
                if (file != null) {
                    sb.append("    replay test written to: ").append(file).append('\n');
                }
            }
        }
        BmcVerificationError error = new BmcVerificationError(sb.toString().stripTrailing());

        // Attach the synthesized stack trace of the first violation so IDEs and
        // reports point straight at the offending line.
        if (!result.violations().isEmpty()) {
            java.util.List<StackTraceElement> stack = result.violations().get(0).stack();
            if (!stack.isEmpty()) {
                error.setStackTrace(stack.toArray(new StackTraceElement[0]));
            }
        }
        return error;
    }

    private static String shortFile(String file) {
        int slash = Math.max(file.lastIndexOf('/'), file.lastIndexOf('\\'));
        return slash >= 0 ? file.substring(slash + 1) : file;
    }
}
