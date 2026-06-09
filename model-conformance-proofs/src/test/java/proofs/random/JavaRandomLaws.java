package proofs.random;

import java.util.Random;

import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;
import org.bmc4j.Verdict;

/**
 * Model proofs for the {@code java.util.Random} bounded-draw model — the "prove for every random
 * outcome" feature. Each draw is modeled as nondet-in-range, so these aren't sampling a few values:
 * JBMC proves the property holds for <em>EVERY</em> value the RNG could produce, all at once.
 *
 * <ul>
 *   <li>{@link #nextInt6_plus1_always_in_1_to_6()} — the dice idiom, proven for all outcomes.</li>
 *   <li>{@link #nextBoolean_is_true_or_false()} — both outcomes considered.</li>
 *   <li>bound-violation proofs — {@code nextInt(0)} / an empty range throw, exactly like the JDK.</li>
 *   <li>{@link #seeded_ctor_is_unknown_not_refuted()} — the seeded constructor is a LOUD stub, so a
 *       reproducibility-flavored proof is honestly UNKNOWN, never a false refutation.</li>
 * </ul>
 */
class JavaRandomLaws {

    /** The classic dice idiom — {@code nextInt(6) + 1} lands in {@code 1..6} for EVERY outcome. */
    @BmcProof
    void nextInt6_plus1_always_in_1_to_6() {
        Random r = new Random();
        int roll = r.nextInt(6) + 1;
        Bmc.check(roll >= 1 && roll <= 6);
    }

    /** {@code nextInt(bound)} is always within {@code [0, bound)} — proven for a symbolic bound. */
    @BmcProof
    void nextInt_bound_in_range_for_every_bound() {
        int bound = Bmc.anyInt(1, 1000);
        int v = new Random().nextInt(bound);
        Bmc.check(v >= 0 && v < bound);
    }

    /** {@code nextInt(origin, bound)} lands in {@code [origin, bound)} for every outcome. */
    @BmcProof
    void nextInt_origin_bound_in_range() {
        Random r = new Random();
        int v = r.nextInt(10, 20);
        Bmc.check(v >= 10 && v < 20);
    }

    /** {@code nextLong(bound)} is always within {@code [0, bound)}. */
    @BmcProof
    void nextLong_bound_in_range() {
        long v = new Random().nextLong(100L);
        Bmc.check(v >= 0 && v < 100L);
    }

    /** {@code nextBoolean()} is true or false — both outcomes are considered (trivially total). */
    @BmcProof
    void nextBoolean_is_true_or_false() {
        boolean b = new Random().nextBoolean();
        Bmc.check(b || !b);
    }

    /** {@code nextDouble()} lands in {@code [0, 1)} for EVERY outcome (the keystone double draw). */
    @BmcProof
    void nextDouble_always_in_0_to_1() {
        double d = new Random().nextDouble();
        Bmc.check(d >= 0.0 && d < 1.0);
    }

    /** {@code nextDouble(bound)} lands in {@code [0, bound)} for every outcome. */
    @BmcProof
    void nextDouble_bound_in_range() {
        double d = new Random().nextDouble(10.0);
        Bmc.check(d >= 0.0 && d < 10.0);
    }

    /** {@code nextDouble(origin, bound)} lands in {@code [origin, bound)} for every outcome. */
    @BmcProof
    void nextDouble_origin_bound_in_range() {
        double d = new Random().nextDouble(-2.5, 7.5);
        Bmc.check(d >= -2.5 && d < 7.5);
    }

    /** {@code nextFloat()} lands in {@code [0, 1)} for EVERY outcome. */
    @BmcProof
    void nextFloat_always_in_0_to_1() {
        float f = new Random().nextFloat();
        Bmc.check(f >= 0.0f && f < 1.0f);
    }

    /** {@code nextFloat(origin, bound)} lands in {@code [origin, bound)} for every outcome. */
    @BmcProof
    void nextFloat_origin_bound_in_range() {
        float f = new Random().nextFloat(1.0f, 4.0f);
        Bmc.check(f >= 1.0f && f < 4.0f);
    }

    /** A non-positive {@code nextDouble(bound)} throws {@link IllegalArgumentException}, like the JDK. */
    @BmcProof
    void nextDouble_nonpositive_bound_throws() {
        Random r = new Random();
        boolean threw = false;
        try {
            r.nextDouble(0.0);
        } catch (IllegalArgumentException e) {
            threw = true;
        }
        Bmc.check(threw);
    }

    /** {@code nextInt(0)} throws {@link IllegalArgumentException}, exactly like the JDK. */
    @BmcProof
    void nextInt_zero_bound_throws() {
        Random r = new Random();
        boolean threw = false;
        try {
            r.nextInt(0);
        } catch (IllegalArgumentException e) {
            threw = true;
        }
        Bmc.check(threw);
    }

    /** {@code nextInt(origin, bound)} with an empty range ({@code origin >= bound}) throws. */
    @BmcProof
    void nextInt_empty_range_throws() {
        Random r = new Random();
        boolean threw = false;
        try {
            r.nextInt(5, 3);
        } catch (IllegalArgumentException e) {
            threw = true;
        }
        Bmc.check(threw);
    }

    /**
     * The SEEDED constructor is a LOUD stub: a reproducibility proof reaching it is honestly UNKNOWN
     * (bmc4j's own modeling gap), NOT a false REFUTED that would claim the user's code has a
     * counterexample. This is the keystone of the soundness separation — you cannot construct a seeded
     * {@code Random} without tripping the loud stub, so the false refutation of
     * {@code new Random(42).nextInt() == new Random(42).nextInt()} can never arise.
     */
    @BmcProof(expect = Verdict.UNKNOWN)
    void seeded_ctor_is_unknown_not_refuted() {
        int a = new Random(42).nextInt(); // loud stub -> UNKNOWN, never nondet
        int b = new Random(42).nextInt();
        Bmc.check(a == b); // never evaluated; the verdict is UNKNOWN at construction
    }
}
