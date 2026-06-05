package org.bmc4j.gradle;

import org.gradle.api.Action;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Nested;

/**
 * The {@code bmc { }} DSL block.
 *
 * <pre>{@code
 * bmc {
 *     unwind = 16                       // default loop bound for proofs
 *     // jbmcPath = "/opt/cbmc/bin/jbmc" // use a local binary instead of the bundled engine
 * }
 * }</pre>
 */
public abstract class BmcExtensionConfig {

    /**
     * Optional path to an existing JBMC binary. When set, the bundled engine
     * dependency is not added and this binary is used instead — useful for an
     * internal mirror, a custom build, or air-gapped environments.
     */
    public abstract Property<String> getJbmcPath();

    /** Default loop/recursion unwinding bound for proofs that don't override it. */
    public abstract Property<Integer> getUnwind();

    /**
     * Default per-proof wall-clock budget in seconds. When a proof doesn't reach a verdict
     * in time, its engine process tree is force-killed and the proof is reported {@code UNKNOWN}
     * (undecided — still fails, but distinctly from a refutation). A proof's
     * {@code @BmcProof(timeoutSeconds=…)} overrides this. Unset / {@code 0} means no timeout (proofs run
     * to completion). Overridable at the command line with {@code -Dbmc.timeoutSeconds}.
     */
    public abstract Property<Integer> getTimeoutSeconds();

    /**
     * How many proofs to verify <b>concurrently</b> — each runs its own {@code jbmc} process, and
     * proofs are independent, so this scales near-linearly. Defaults to the number of available
     * processors. Set to {@code 1} to run proofs serially (e.g. if heavy proofs strain memory).
     */
    public abstract Property<Integer> getParallelism();

    /**
     * SAT/SMT backend for JBMC. Default is JBMC's built-in MiniSat. SMT solvers ({@code "z3"},
     * {@code "boolector"}, {@code "cvc4"}, {@code "cvc5"}) — which must be on {@code PATH} — can be
     * much faster on array/bitvector-heavy proofs; any other value is passed to {@code --sat-solver}
     * (e.g. {@code "cadical"}, {@code "glucose"}).
     */
    public abstract Property<String> getSolver();

    /** Path/command for an external SMT2 solver binary (used with {@code --smt2}); overrides {@link
     *  #getSolver()}. Use when the solver isn't on {@code PATH}. */
    public abstract Property<String> getSolverCmd();

    /**
     * Directory holding the SMT solver binary (e.g. the dir containing {@code z3}). It's prepended to
     * jbmc's PATH so {@link #getSolver()} / {@code @BmcProof(solver=…)} can find the solver without it
     * being on the global {@code PATH}. Keep machine-specific paths out of the repo — set it from a
     * Gradle property, e.g. {@code solverPath = providers.gradleProperty("z3Path").orNull}.
     */
    public abstract Property<String> getSolverPath();

    /**
     * Show per-proof progress while the {@code test} task runs ("proving X", "OK/REFUTED X (Ns)")
     * plus a final summary, at Gradle's lifecycle level so it's visible in a normal {@code gradlew
     * test}. On by default — proofs can take seconds each, and silence looks like a hang. Set to
     * {@code false} for quiet CI logs.
     */
    public abstract Property<Boolean> getProgress();

    /**
     * Per-proof verdict caching. When {@code true} (the default), a proof that
     * <b>passed with a deterministic verdict</b> ({@code VERIFIED}, or {@code REFUTED}/{@code VACUOUS}
     * for a fail-on-purpose proof whose {@code expect} declares exactly that) and whose inputs
     * (bytecode, flags, engine + runtime semantics) are unchanged is skipped on the next run and
     * reported passed from the cache under {@code build/bmc4j/verdict-cache/} — so "nothing changed"
     * runs are near-free. Only expectation-matching passes are ever cached; failures always re-run
     * live, and {@code TIMEOUT}/{@code UNKNOWN} are never cached even when expected (machine-dependent).
     * Set {@code false} (or pass {@code -Dbmc.noCache=true}) to force full
     * re-verification every time. The cache lives under {@code build/}, so {@code gradlew clean} clears it.
     */
    public abstract Property<Boolean> getCache();

    /**
     * Build-wide acknowledged nondet stubs: methods every proof may rely on as havoc'd
     * stand-ins without warning. JBMC stubs any callee it has no body for to a nondet result; bmc4j
     * footnotes that on green proofs (and, under {@link #getStrictStubs()}, turns an unacknowledged stub
     * into UNKNOWN). Listing a method here silences it suite-wide; a proof can add more with
     * {@code @BmcProof(allowStubs = …)}. Entries are fully-qualified method names with an optional
     * trailing wildcard: {@code "java.util.Formatter.format"}, {@code "java.util.Formatter.*"}, or
     * {@code "java.util.*"}.
     */
    public abstract ListProperty<String> getAllowStubs();

    /**
     * Strict nondet-stub mode. When {@code true}, any <em>unacknowledged</em> stub a proof
     * reaches turns its verdict into UNKNOWN ({@code BmcUndecidedError}) — nothing was proven wrong, but
     * the verdict rests on havoc'd stand-ins, so it isn't trustworthy. Default {@code false} (lenient:
     * green + footnote). Overridable at the command line with {@code -Dbmc.strictStubs=true}, so flipping
     * it re-judges from the stored stub fact <em>without</em> re-running proofs (the stub list is cached).
     */
    public abstract Property<Boolean> getStrictStubs();

    /**
     * Package prefixes of the module under test. A stub from one of these — the user's own
     * code — is almost always a missing-dependency config bug, not a JDK modeling gap, so it is warned
     * loudly even in lenient mode (and forces UNKNOWN in strict mode). Comma/space-separated prefixes,
     * e.g. {@code userPackages = ["com.acme"]}. Overridable with {@code -Dbmc.userPackages}.
     */
    public abstract ListProperty<String> getUserPackages();

    // --- User models: declared intent + provenance ------------------------------------------------

    /**
     * Registered user models with their declared <b>intent</b>. A class under {@code src/bmcModel}
     * shadows its real counterpart on JBMC's analysis classpath; registering it here adds the trust
     * metadata bmc4j needs to put provenance on a verdict that rests on it.
     *
     * <pre>{@code
     * bmc {
     *     models {
     *         conformant("acme.FastList")                       // claims JDK fidelity
     *         domain("acme.NoCollisionMap", "keys are UUIDs, collision-free")  // intentional divergence
     *     }
     * }
     * }</pre>
     *
     * <p>A {@code domain} model encodes a constraint that deliberately diverges from the JDK -- it is
     * {@code Bmc.assume()} at classpath altitude -- so it requires a one-line rationale, which is
     * footnoted on every green proof that rests on it. A {@code conformant} model claims JDK fidelity and
     * can be checked by the same conformance harness as bundled models. Under {@link #getStrictModels()},
     * a model present under {@code src/bmcModel} but NOT registered here turns the verdict into UNKNOWN.
     */
    @Nested
    public abstract ModelSpec getModelSpec();

    /** Configure the registered user models -- see {@link #getModelSpec()}. */
    public void models(Action<? super ModelSpec> action) {
        action.execute(getModelSpec());
    }

    /**
     * Strict user-model mode, the {@code strictStubs} analog. When {@code true}, a model present under
     * {@code src/bmcModel} with no {@code bmc { models { ... } }} intent declaration turns the proof's
     * verdict into UNKNOWN ({@code BmcUndecidedError}) -- no proof silently rests on an undeclared
     * override. Default {@code false} (lenient: green + a loud "UNDECLARED model" footnote). Overridable
     * at the command line with {@code -Dbmc.strictModels=true}; like {@code strictStubs} it is read-time
     * policy, so flipping it re-judges without re-running proofs.
     */
    public abstract Property<Boolean> getStrictModels();

    /**
     * The {@code models { conformant(...) / domain(...) }} DSL block. A Gradle <b>managed</b> type
     * (abstract, no fields): its one property is the abstract {@link #getEntries()} list, which Gradle
     * instantiates; the {@code conformant} / {@code domain} methods append serialized declarations to it.
     */
    public abstract static class ModelSpec {

        /** Serialized declarations, one per entry as {@code intent|fqn|rationale}; joined by the plugin. */
        public abstract ListProperty<String> getEntries();

        /** Register a conformant user model (claims JDK fidelity). */
        public void conformant(String className) {
            require(className, "conformant");
            getEntries().add("conformant|" + className.trim() + "|");
        }

        /**
         * Register a domain user model (intentional divergence). {@code rationale} is required -- a
         * one-line explanation of the assumed constraint, footnoted on green proofs that rest on it.
         */
        public void domain(String className, String rationale) {
            require(className, "domain");
            if (rationale == null || rationale.isBlank()) {
                throw new IllegalArgumentException("bmc { models { domain(\"" + className
                        + "\", ...) } } requires a rationale: a domain model intentionally diverges from"
                        + " the JDK -- say how (e.g. \"keys are UUIDs, collision-free\").");
            }
            getEntries().add("domain|" + className.trim() + "|" + rationale.trim());
        }

        private static void require(String className, String intent) {
            if (className == null || className.isBlank()) {
                throw new IllegalArgumentException(
                        "bmc { models { " + intent + "(...) } } requires a class name");
            }
        }
    }
}
