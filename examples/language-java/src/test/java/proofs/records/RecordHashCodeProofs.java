package proofs.records;

import example.records.Point;
import example.records.User;
import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;
import org.bmc4j.Verdict;

/**
 * Record {@code hashCode} (Java 16+). javac compiles a record's {@code hashCode} to an {@code
 * invokedynamic} call site bootstrapped by {@code java.lang.runtime.ObjectMethods}; JBMC links that
 * indy to an unconstrained {@code int}, so without help every hashCode law below would be a
 * vacuous nondet claim. bmc4j desugars the bootstrap (see {@code StringBytecode}) to a deterministic
 * {@code 31*result + componentHash} fold over the components, with String components hashed by the
 * sound {@code BmcStrings.hashCode} (length + charAt) rather than JBMC's nondet {@code
 * String.hashCode}.
 *
 * <p><b>What we prove (the guaranteed contract).</b> The JDK deliberately leaves a record's exact
 * hashCode value <em>unspecified</em> ("derived from the components"), so these proofs assert only
 * what the spec guarantees and what matters for using records in hash-based collections: hashCode is
 * a <em>pure, deterministic function of the components</em> — equal records (equal components) have
 * equal hashCode, and repeated calls agree. We do NOT assert any specific magic constant.
 */
class RecordHashCodeProofs {

    // PASS over all symbolic Points: equal records (same components) have equal hashCode. This is the
    // core hashCode/equals contract and the property hash-based collections rely on. It is a tautology
    // ONLY because the desugared hashCode is a real, consistent function of the components — with the
    // raw ObjectMethods indy (nondet) JBMC could refute it.
    @BmcProof
    void equal_points_have_equal_hashCode() {
        int x = Bmc.anyInt(-1000, 1000);
        int y = Bmc.anyInt(-1000, 1000);
        Point a = new Point(x, y);
        Point b = new Point(x, y);
        Bmc.check(a.hashCode() == b.hashCode());
    }

    // PASS: hashCode is consistent across repeated calls on the same instance (no nondet inside).
    @BmcProof
    void hashCode_is_consistent_across_calls() {
        Point p = new Point(Bmc.anyInt(-1000, 1000), Bmc.anyInt(-1000, 1000));
        Bmc.check(p.hashCode() == p.hashCode());
    }

    // PASS: hashCode genuinely depends on the components — two Points that differ only in x can be
    // forced to disagree. (Sanity that the value is computed from components, not a constant/nondet.)
    // With x fixed-different and y equal, the fold 31*x + y differs, so the hashes differ.
    @BmcProof
    void hashCode_depends_on_components() {
        int y = Bmc.anyInt(-1000, 1000);
        Point a = new Point(1, y);
        Point b = new Point(2, y);
        Bmc.check(a.hashCode() != b.hashCode());
    }

    // PASS over symbolic String + int components: equal records with a reference (String) component
    // still hash equal. Exercises the BmcStrings.hashCode (length + charAt) path in the desugar, which
    // is the sound part JBMC's own String.hashCode cannot deliver. unwind covers the bounded name.
    @BmcProof(unwind = 8)
    void equal_users_with_string_component_have_equal_hashCode() {
        int id = Bmc.anyInt(0, 1000);
        String name = Bmc.anyString(4);
        User a = new User(id, name);
        User b = new User(id, name);
        Bmc.check(a.hashCode() == b.hashCode());
    }

    // FAIL (the bug): claiming two Points that differ only in y always hash EQUAL is false — the fold
    // 31*x + y separates them whenever the y values differ. BMC refutes it, proving hashCode is not a
    // constant and actually folds in the second component.
    @BmcProof(expect = Verdict.REFUTED)
    void differing_points_need_not_collide() {
        int x = Bmc.anyInt(-1000, 1000);
        Point a = new Point(x, 5);
        Point b = new Point(x, 6);
        Bmc.check(a.hashCode() == b.hashCode());
    }
}
