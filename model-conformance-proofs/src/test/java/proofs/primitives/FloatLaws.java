package proofs.primitives;

import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;
import org.bmc4j.Verdict;

/**
 * Conformance pins for primitive {@code float} arithmetic and comparison under JBMC's default
 * floating-point semantics ({@code floatbv} — bit-precise IEEE-754, the jbmc default with no flag).
 *
 * <p>These are NOT bmc-models: {@code float} arithmetic and the primitive {@code fcmpl}/{@code fcmpg}
 * comparisons are native jbmc operations. This suite is the conformance record that they are SOUND —
 * exactly as measured in the 2026-06 FP probe — so consumer proofs over {@code float} can be trusted.
 *
 * <h2>Soundness map (measured 2026-06)</h2>
 * <ul>
 *   <li><b>{@code + - * /} — SOUND, bit-precise.</b> Identities ({@code a+0==a}, {@code a*1==a} for
 *       finite {@code a}), commutativity ({@code a+b==b+a}, {@code a*b==b*a}) hold symbolically.</li>
 *   <li><b>Non-associativity — correctly REFUTED.</b> {@code (a+b)+c == a+(b+c)} does NOT hold for
 *       floats (rounding is order-dependent). The {@code expect = REFUTED} proof below PINS that jbmc
 *       stays bit-precise: if it ever started treating float add as associative (real-arithmetic
 *       abstraction), this proof would flip to VERIFIED and fail loudly. This is the soundness proof.</li>
 *   <li><b>Primitive {@code == < >} — SOUND</b> (native {@code fcmpl}/{@code fcmpg}): {@code -0.0f == 0.0f},
 *       {@code NaN != NaN}, every ordered comparison with NaN is false, strict ordering of finite
 *       values.</li>
 * </ul>
 *
 * <p>NOT covered here (documented unsound — see {@code docs/coverage.md}): the {@code Float.compare}
 * JDK total-order method (sign-correct for strictly-ordered finite values, but the equal / -0.0-vs-+0.0
 * / NaN cases are left UNCONSTRAINED by jbmc). Any model leaning on {@code Float.compare} for the IEEE
 * total order is unsound; this suite deliberately does not exercise it.
 */
class FloatLaws {

    // ---- arithmetic identities (finite, non-NaN inputs) -------------------

    @BmcProof
    void add_zero_identity() {
        // a + 0 == a for every finite float (the range bound excludes NaN/infinity).
        float a = Bmc.anyFloat(-1.0e30f, 1.0e30f);
        Bmc.check(a + 0.0f == a);
    }

    @BmcProof
    void mul_one_identity() {
        float a = Bmc.anyFloat(-1.0e30f, 1.0e30f);
        Bmc.check(a * 1.0f == a);
    }

    @BmcProof
    void sub_self_is_zero() {
        float a = Bmc.anyFloat(-1.0e30f, 1.0e30f);
        Bmc.check(a - a == 0.0f);
    }

    // ---- commutativity ----------------------------------------------------

    @BmcProof
    void add_is_commutative() {
        float a = Bmc.anyFloat(-1.0e18f, 1.0e18f);
        float b = Bmc.anyFloat(-1.0e18f, 1.0e18f);
        Bmc.check(a + b == b + a);
    }

    @BmcProof
    void mul_is_commutative() {
        float a = Bmc.anyFloat(-1.0e9f, 1.0e9f);
        float b = Bmc.anyFloat(-1.0e9f, 1.0e9f);
        Bmc.check(a * b == b * a);
    }

    // ---- the soundness proof: float add is NOT associative ----------------

    @BmcProof(expect = Verdict.REFUTED)
    void add_is_NOT_associative() {
        // (a+b)+c == a+(b+c) is FALSE for floats (rounding is order-dependent). jbmc must find a
        // counterexample. If this ever VERIFIES, jbmc has abstracted float add to real arithmetic
        // and every float proof in the suite is suspect — this guard catches that regression.
        float a = Bmc.anyFloat(-1.0e30f, 1.0e30f);
        float b = Bmc.anyFloat(-1.0e30f, 1.0e30f);
        float c = Bmc.anyFloat(-1.0e30f, 1.0e30f);
        Bmc.check((a + b) + c == a + (b + c));
    }

    // ---- primitive comparison laws ----------------------------------------

    @BmcProof
    void negative_zero_equals_positive_zero() {
        // Primitive == follows IEEE: -0.0f == +0.0f (distinct from the total order Float.compare uses).
        float negZero = -0.0f;
        float posZero = 0.0f;
        Bmc.check(negZero == posZero);
    }

    @BmcProof
    void nan_is_not_equal_to_itself() {
        float nan = Float.NaN;
        Bmc.check(nan != nan);          // IEEE: NaN compares unequal to everything, including itself
        Bmc.check(!(nan == nan));
        Bmc.check(!(nan < 0.0f));       // every ordered comparison with NaN is false
        Bmc.check(!(nan > 0.0f));
    }

    @BmcProof
    void strict_ordering_of_finite_values() {
        // For finite a < b, the native < and > agree and == is false.
        float a = Bmc.anyFloat(-1.0e30f, 1.0e30f);
        float b = Bmc.anyFloat(-1.0e30f, 1.0e30f);
        Bmc.assume(a < b);
        Bmc.check(b > a);
        Bmc.check(a != b);
        Bmc.check(!(a > b));
    }
}
