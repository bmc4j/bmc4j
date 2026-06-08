package proofs.primitives;

import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;
import org.bmc4j.Verdict;

/**
 * Conformance pins for primitive {@code double} arithmetic and comparison under JBMC's default
 * floating-point semantics ({@code floatbv} — bit-precise IEEE-754, the jbmc default with no flag).
 *
 * <p>The 2026-06 FP probe measured double arithmetic ({@code + - * /}) and primitive comparison
 * ({@code dcmpl}/{@code dcmpg}) to be SOUND and bit-precise, exactly like {@code float}. This suite
 * is the conformance record. <b>bmc4j still DISCOURAGES {@code double}</b> for two reasons:
 * (1) double formatting / stringification is UNSOUND in jbmc (dtoa unmodeled — {@code Double.toString},
 * {@code "" + aDouble} are unconstrained), so code that renders a double can be proven wrong things;
 * (2) symbolic double formulas are heavier for the solver. Prefer exact integer / {@code BigDecimal}
 * models. The proofs here are deliberately tightly bounded.
 *
 * <p>NOT covered (documented unsound — see {@code docs/coverage.md}): {@code Double.compare} (the JDK
 * total order — sign-correct for strictly-ordered finite values, but equal / -0.0 / NaN left
 * unconstrained), and double stringification.
 */
class DoubleArithmeticLaws {

    // ---- arithmetic identities --------------------------------------------

    @BmcProof
    void add_zero_identity() {
        double a = Bmc.anyDouble(-1.0e6, 1.0e6);
        Bmc.check(a + 0.0 == a);
    }

    @BmcProof
    void mul_one_identity() {
        double a = Bmc.anyDouble(-1.0e6, 1.0e6);
        Bmc.check(a * 1.0 == a);
    }

    @BmcProof
    void sub_self_is_zero() {
        double a = Bmc.anyDouble(-1.0e6, 1.0e6);
        Bmc.check(a - a == 0.0);
    }

    // ---- commutativity ----------------------------------------------------

    @BmcProof
    void add_is_commutative() {
        double a = Bmc.anyDouble(-1.0e6, 1.0e6);
        double b = Bmc.anyDouble(-1.0e6, 1.0e6);
        Bmc.check(a + b == b + a);
    }

    @BmcProof
    void mul_is_commutative() {
        double a = Bmc.anyDouble(-1.0e3, 1.0e3);
        double b = Bmc.anyDouble(-1.0e3, 1.0e3);
        Bmc.check(a * b == b * a);
    }

    // ---- the soundness proof: double add is NOT associative ---------------

    @BmcProof(expect = Verdict.REFUTED)
    void add_is_NOT_associative() {
        // Same bit-precision guard as FloatLaws: (a+b)+c == a+(b+c) is FALSE for doubles. If this
        // VERIFIES, jbmc has abstracted double add to real arithmetic and double proofs are suspect.
        double a = Bmc.anyDouble(-1.0e15, 1.0e15);
        double b = Bmc.anyDouble(-1.0e15, 1.0e15);
        double c = Bmc.anyDouble(-1.0e15, 1.0e15);
        Bmc.check((a + b) + c == a + (b + c));
    }

    // ---- primitive comparison laws ----------------------------------------

    @BmcProof
    void negative_zero_equals_positive_zero() {
        double negZero = -0.0;
        double posZero = 0.0;
        Bmc.check(negZero == posZero);
    }

    @BmcProof
    void nan_is_not_equal_to_itself() {
        double nan = Double.NaN;
        Bmc.check(nan != nan);
        Bmc.check(!(nan == nan));
        Bmc.check(!(nan < 0.0));
        Bmc.check(!(nan > 0.0));
    }

    @BmcProof
    void strict_ordering_of_finite_values() {
        double a = Bmc.anyDouble(-1.0e6, 1.0e6);
        double b = Bmc.anyDouble(-1.0e6, 1.0e6);
        Bmc.assume(a < b);
        Bmc.check(b > a);
        Bmc.check(a != b);
        Bmc.check(!(a > b));
    }
}
