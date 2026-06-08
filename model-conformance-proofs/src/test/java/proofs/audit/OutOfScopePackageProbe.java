package proofs.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Date;
import java.util.ArrayList;

import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;
import org.bmc4j.Verdict;
import org.bmc4j.engine.BmcRequest;
import org.bmc4j.engine.JbmcResult;
import org.bmc4j.engine.VerificationBackend;
import org.bmc4j.engine.VerificationBackends;
import org.junit.jupiter.api.Test;

/**
 * HARD-soundness floor for the deliberately-out-of-scope PACKAGE waiver
 * ({@code bmc { notModeledPackages { "java.sql.*"; "javax.swing.*" } }}, configured in this module's
 * build). The non-negotiable invariant: a package waiver may CLASSIFY and DOCUMENT only — it must NEVER
 * suppress. Reaching a class under a declared package must still produce a LOUD, member-named
 * <em>out-of-scope (declared)</em> {@code UNKNOWN} — never a silent nondet stub, never a path to a false
 * {@code VERIFIED}. This is the same loudness the per-member tail guarantees ({@link LoudUnmodelledProbe}),
 * with a DISTINCT reason so a reviewer can tell "deliberately declined" from "model gap not yet filled".
 *
 * <p>The two ends of the precedence rule are both pinned here:
 * <ul>
 *   <li>{@link #reaching_a_declared_out_of_scope_class_is_loud_unknown()} — a reach into the declared
 *       {@code java.sql} package surfaces as out-of-scope UNKNOWN (loud, member-named), NOT a false green;
 *   <li>{@link #a_modeled_class_inside_a_waived_package_is_still_modeled()} — even when its whole package
 *       is declared out of scope, a class bmc4j MODELS is still the model (the registry wins over the
 *       waiver): it has a body, is never nondet-stubbed, so the waiver never touches it and the proof
 *       VERIFIES.
 * </ul>
 */
class OutOfScopePackageProbe {

    /**
     * In-suite end-to-end pin: reaching {@link java.sql.Date} — under the declared-out-of-scope
     * {@code java.sql.*} package — must resolve to a LOUD out-of-scope (declared) UNKNOWN, run through
     * the full extension with the build-forwarded {@code -Dbmc.notModeledPackages}. {@code expect = UNKNOWN}:
     * the probe passes while the reach is honestly undecided (NOT VERIFIED — a silent stub would be a
     * false green; NOT REFUTED — that would claim the user's code is wrong). Goes red if a future change
     * ever lets a declared-package reach ride a silent stub to green.
     */
    @BmcProof(expect = Verdict.UNKNOWN)
    void reaching_a_declared_out_of_scope_class_is_undecided() {
        Date d = new Date(0L);
        long t = d.getTime(); // java.sql.Date.getTime — declared out of scope, no model body -> loud UNKNOWN
        Bmc.check(t == 0L); // never trusted: the out-of-scope reach demotes the verdict to UNKNOWN
    }

    /**
     * Drives the backend directly to assert the out-of-scope reach NAMES the member as an opaque symbol
     * (never silently stubbed) AND does not verify — so the UNKNOWN the extension raises carries the
     * member name, the LOUD half of the invariant. (The extension's distinct out-of-scope framing is
     * unit-pinned in {@code BmcProofExtensionTest.outOfScopePackageUndecided_*}.)
     */
    @Test
    void reaching_a_declared_out_of_scope_class_is_loud_unknown() {
        JbmcResult result = runEntry(
                "proofs.audit.OutOfScopePackageProbe.reaching_a_declared_out_of_scope_class_is_undecided");
        assertFalse(result.isVerified(),
                "a proof reaching a DECLARED out-of-scope class must NEVER silently VERIFY "
                        + "(the package-waiver loudness invariant)");
        assertTrue(result.stubbedMethods().stream().anyMatch(m -> m.contains("java.sql.Date")),
                "the reached out-of-scope member must be NAMED as an opaque symbol, not silently stubbed: "
                        + result.stubbedMethods());

        // The demotion the extension applies to such a reach is the typed OUT_OF_SCOPE UNKNOWN
        // (retryable=false) — the kind that surfaces in the ProofSummary / proof-results comment so a
        // reviewer can tell a DELIBERATELY declined area from a model gap. Pinned through the same
        // extension helpers the live demotion uses (the build forwards java.sql.* as out of scope).
        var declaredGlobs = java.util.List.of("java.sql.*");
        var demoted = org.bmc4j.junit.BmcProofExtension.outOfScopeStubsToDemote(
                result.stubbedMethods(), declaredGlobs, java.util.List.of());
        assertFalse(demoted.isEmpty(),
                "the declared-package reach must demote to a loud out-of-scope UNKNOWN: " + result.stubbedMethods());
        var err = org.bmc4j.junit.BmcProofExtension.outOfScopePackageUndecided("jbmc",
                "proofs.audit.OutOfScopePackageProbe.reaching_a_declared_out_of_scope_class_is_undecided", demoted);
        assertEquals(org.bmc4j.engine.UnknownKind.OUT_OF_SCOPE, err.getKind(),
                "a declared out-of-scope reach yields kind=OUT_OF_SCOPE");
        assertFalse(err.getKind().retryable,
                "OUT_OF_SCOPE is a deterministic declared decline: NOT retryable");
    }

    /**
     * Registry-WINS precedence control: {@link java.util.ArrayList} is MODELED by bmc4j. This proof
     * reaches it and VERIFIES — a model body is never nondet-stubbed, so even if {@code java.util.*} were
     * declared out of scope (the glob WOULD match the ArrayList member — unit-pinned in
     * {@code BmcProofExtensionTest.matchesNotModeledPackage_*}), the waiver only ever consumes harvested
     * STUBS, which a modeled class never produces. The registry therefore wins over a package waiver with
     * no special casing — modeled classes are simply not in the stub stream the waiver inspects.
     */
    @BmcProof
    void a_modeled_class_inside_a_waived_package_is_still_modeled() {
        ArrayList<Integer> a = new ArrayList<>();
        a.add(7);
        Bmc.check(a.get(0) == 7); // ArrayList is modeled: verifies, never demoted by a package waiver
    }

    // --- helpers ---------------------------------------------------------------

    private JbmcResult runEntry(String entryFunction) {
        BmcRequest req = new BmcRequest(
                "proofs.audit.OutOfScopePackageProbe",
                entryFunction,
                System.getProperty("java.class.path"), 16, true, 16, "", 0);
        VerificationBackend backend = VerificationBackends.select(req);
        return backend.verify(req);
    }
}
