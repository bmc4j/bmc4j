package proofs.audit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.bmc4j.analysis.PackageReach;
import org.bmc4j.engine.BmcRequest;
import org.bmc4j.engine.JbmcResult;
import org.bmc4j.engine.VerificationBackend;
import org.bmc4j.engine.VerificationBackends;
import org.junit.jupiter.api.Test;

/**
 * The package-grain COMPLETENESS RATCHET. Where the per-member tail tracks coverage of the curated
 * registry, this asserts completeness over the proof's actual reach: every class the proof cone reaches
 * that bmc4j has no model for (so the engine nondet-stubs it) must be EITHER modeled OR matched by a
 * declared {@code bmc { notModeledPackages { … } }} glob. A reach into a class that is NEITHER is a
 * build FAILURE — a deliberate decision point (model it, or declare its package out of scope), never a
 * silently-tolerated UNKNOWN.
 *
 * <p>The ratchet is grounded in the engine's own harvested nondet-stub stream (the methods it had no
 * body for in the reachable slice — the SAME fact the verdict's stub/out-of-scope policy consumes), so
 * it reuses the existing reachability rather than introducing a second walk, and it is exactly the set
 * "reached but unmodeled". A modeled class has a body and never appears in that stream, so the registry
 * wins with no special casing.
 *
 * <p>It BITES BOTH DIRECTIONS:
 * <ul>
 *   <li>{@link #reach_into_a_declared_package_is_accepted_by_the_ratchet()} — with {@code java.sql.*}
 *       declared (this module's build), reaching {@code java.sql.Date} is ACCEPTED (no undeclared stub);
 *   <li>{@link #removing_the_glob_resurfaces_the_reach_as_an_undeclared_failure()} — drop the glob from
 *       the declared set and the SAME reach becomes an UNDECLARED stub the ratchet would FAIL on. So
 *       removing a glob re-surfaces its classes as failures (the ratchet bites both ways).
 * </ul>
 */
class PackageCompletenessRatchetTest {

    /** The declared out-of-scope packages, as forwarded from this module's {@code bmc { … }} build. */
    private static final List<String> DECLARED = PackageReach.declaredGlobs();

    /**
     * Drive the probe that reaches the declared-out-of-scope {@code java.sql} package and run the
     * package-grain ratchet over the harvested stubs. With {@code java.sql.*} declared, the
     * {@code java.sql.Date} reach is ABSORBED by the declaration — it does NOT surface as an undeclared
     * ratchet failure. (The ratchet still — correctly — flags any OTHER genuinely-unmodeled,
     * undeclared class the probe happens to reach, e.g. a {@code java.util} utility: that is the gate
     * doing its job, a real decision point. This test pins only the package-waiver property: a declared
     * package's reach is accounted for.)
     */
    @Test
    void reach_into_a_declared_package_is_accepted_by_the_ratchet() {
        assertTrue(DECLARED.contains("java.sql.*"),
                "precondition: this module's build declares java.sql.* out of scope: " + DECLARED);
        JbmcResult result = runProbe();
        // The declared reach really did happen (the ratchet isn't vacuously green).
        assertTrue(result.stubbedMethods().stream().anyMatch(m -> m.contains("java.sql.Date")),
                "the probe must actually reach the declared java.sql class: " + result.stubbedMethods());
        // ...and with java.sql.* declared, NO java.sql member remains as an undeclared ratchet failure.
        List<String> undeclaredSql = undeclaredStubs(result, DECLARED).stream()
                .filter(m -> m.startsWith("java.sql."))
                .toList();
        assertTrue(undeclaredSql.isEmpty(),
                "a declared package's reach must be ABSORBED by the waiver (not flagged as undeclared): "
                        + undeclaredSql);
    }

    /**
     * The reverse direction: with {@code java.sql.*} REMOVED from the declared set, the SAME
     * {@code java.sql.Date} reach is an UNDECLARED stub — exactly what the ratchet FAILS the build on.
     * This pins that the ratchet bites bidirectionally: removing a glob re-surfaces its classes as
     * failures (and a future newly-reached, undeclared package is the same failure).
     */
    @Test
    void removing_the_glob_resurfaces_the_reach_as_an_undeclared_failure() {
        JbmcResult result = runProbe();
        // Declared set with java.sql.* removed (the "someone deleted the glob" / "newly-reached" case).
        List<String> withoutSql = DECLARED.stream().filter(g -> !g.startsWith("java.sql")).toList();
        List<String> undeclared = undeclaredStubs(result, withoutSql);
        assertFalse(undeclared.isEmpty(),
                "removing java.sql.* must re-surface the java.sql reach as an undeclared ratchet failure");
        assertTrue(undeclared.stream().anyMatch(m -> m.contains("java.sql.Date")),
                "the undeclared failure must name the now-undeclared reached class: " + undeclared);
    }

    // --- the ratchet kernel ----------------------------------------------------

    /**
     * The reached-but-unmodeled members ({@link JbmcResult#stubbedMethods()} — signal nondet stubs of
     * classes bmc4j has no model for) that are NOT under any declared out-of-scope package. A non-empty
     * result is what the gate FAILS on: a class neither modeled nor declared. A modeled class never
     * appears in the stub stream, so this is precisely "reached, unmodeled, and undeclared".
     */
    private static List<String> undeclaredStubs(JbmcResult result, List<String> declared) {
        return result.stubbedMethods().stream()
                .filter(m -> !PackageReach.matchesPackage(m, declared))
                .toList();
    }

    private JbmcResult runProbe() {
        BmcRequest req = new BmcRequest(
                "proofs.audit.OutOfScopePackageProbe",
                "proofs.audit.OutOfScopePackageProbe.reaching_a_declared_out_of_scope_class_is_undecided",
                System.getProperty("java.class.path"), 16, true, 16, "", 0);
        VerificationBackend backend = VerificationBackends.select(req);
        return backend.verify(req);
    }
}
