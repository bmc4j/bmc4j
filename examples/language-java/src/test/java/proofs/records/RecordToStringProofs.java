package proofs.records;

import example.records.Point;
import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;
import org.bmc4j.Verdict;

/**
 * Record {@code toString} (Java 16+). javac compiles a record's {@code toString} to an {@code
 * invokedynamic} bootstrapped by {@code java.lang.runtime.ObjectMethods}, which JBMC links to an
 * unconstrained String. bmc4j desugars it (see {@code StringBytecode}) to the canonical
 * {@code "Name[c1=v1, c2=v2]"} built with the same sound {@code StringBuilder} machinery the string
 * concat desugar uses (numerics via {@code Integer/Long.toString}) — but ONLY for records whose
 * components all render soundly (primitive or String). A record with a non-String reference component
 * keeps its original (unsound) toString indy.
 *
 * <p>These proofs check the literal scaffolding is exact and the result tracks the components, which
 * is only provable because the desugared toString is a real String JBMC can read char-by-char.
 */
class RecordToStringProofs {

    // PASS: the result starts with the exact record prefix "Point[x=". This is the structural part of
    // the canonical form, independent of the (symbolic) component values. With the raw ObjectMethods
    // indy (nondet String) this prefix check would be refutable.
    @BmcProof(unwind = 20)
    void toString_has_canonical_prefix() {
        Point p = new Point(Bmc.anyInt(-1000, 1000), Bmc.anyInt(-1000, 1000));
        Bmc.check(p.toString().startsWith("Point[x="));
    }

    // PASS: the result ends with the closing bracket, and contains the ", y=" component separator.
    @BmcProof(unwind = 24)
    void toString_has_separator_and_suffix() {
        Point p = new Point(Bmc.anyInt(-1000, 1000), Bmc.anyInt(-1000, 1000));
        String s = p.toString();
        Bmc.check(s.endsWith("]"));
        Bmc.check(s.contains(", y="));
    }

    // PASS: for fixed components the whole canonical string is exact. Single-digit values keep the
    // length small so the char-wise equals fully unwinds.
    @BmcProof(unwind = 24)
    void toString_is_exact_for_fixed_components() {
        Point p = new Point(1, 2);
        Bmc.check(p.toString().equals("Point[x=1, y=2]"));
    }

    // FAIL (the bug): the record's name is "Point", not "Pointe" — so claiming the toString starts
    // with "Pointe[" is false. BMC refutes it, proving the prefix is the real literal, not nondet.
    @BmcProof(unwind = 20, expect = Verdict.REFUTED)
    void toString_prefix_is_not_misspelled() {
        Point p = new Point(Bmc.anyInt(-1000, 1000), Bmc.anyInt(-1000, 1000));
        Bmc.check(p.toString().startsWith("Pointe["));
    }
}
