package proofs.records;

import example.records.Point;
import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;
import org.bmc4j.Verdict;

/**
 * Record deconstruction patterns (Java 21+) in the {@code instanceof} form:
 * {@code if (o instanceof Point(int x, int y))}. This lowers to a plain {@code instanceof} type
 * check followed by calls to the record's accessors — it does NOT go through the {@code
 * SwitchBootstraps.typeSwitch} invokedynamic that a pattern-matching {@code switch} uses, so JBMC
 * handles it soundly with no desugaring needed.
 */
class RecordPatternProofs {

    // PASS over every symbolic Point: the components bound by the record pattern equal the record's
    // accessors. (Tautology only if JBMC actually executes the deconstruction + accessor calls.)
    @BmcProof
    void bound_components_match_accessors() {
        Point p = new Point(Bmc.anyInt(-1000, 1000), Bmc.anyInt(-1000, 1000));
        Object o = p;
        if (o instanceof Point(int x, int y)) {
            Bmc.check(x == p.x());
            Bmc.check(y == p.y());
        } else {
            Bmc.check(false); // a Point must always match the Point pattern
        }
    }

    // PASS: the deconstructing helper computes Manhattan distance correctly over symbolic input.
    @BmcProof
    void manhattan_is_sum_of_absolutes() {
        int a = Bmc.anyInt(-1000, 1000);
        int b = Bmc.anyInt(-1000, 1000);
        Bmc.check(Point.manhattan(new Point(a, b)) == Math.abs(a) + Math.abs(b));
    }

    // PASS: the pattern's type check is real — a non-Point subject does not match, so the helper
    // returns its sentinel.
    @BmcProof
    void non_point_does_not_match() {
        Object o = "not a point";
        Bmc.check(Point.manhattan(o) == -1);
    }

    // FAIL (the bug): Manhattan distance is never negative, so claiming it can be < 0 for a
    // deconstructed Point is false. BMC refutes it. (Also a sanity check that the accessors are
    // genuinely evaluated rather than nondet.)
    @BmcProof(expect = Verdict.REFUTED)
    void manhattan_is_never_zero() {
        int a = Bmc.anyInt(-1000, 1000);
        int b = Bmc.anyInt(-1000, 1000);
        Bmc.check(Point.manhattan(new Point(a, b)) != 0);
    }
}
