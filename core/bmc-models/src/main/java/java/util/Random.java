package java.util;

import static org.bmc4j.analysis.BmcUnmodelledReached.fail;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcModelTail;
import org.bmc4j.models.audit.BmcUnmodelable;
import org.cprover.CProver;

/**
 * Clean BMC model of {@link java.util.Random} — the "prove for every random outcome" model. Replaces the
 * real one on JBMC's analysis classpath only (the real JVM ignores {@code java.*} models).
 *
 * <h2>The two contracts of a PRNG, kept rigorously separate</h2>
 * A {@code Random} has two observable contracts, and only one of them is soundly modelable as
 * nondeterminism:
 *
 * <ul>
 *   <li><b>The range / distribution contract IS the ideal BMC model.</b> {@code nextInt(bound)} returns
 *       some value in {@code [0, bound)}, {@code nextBoolean()} returns some {@code boolean}, and so on.
 *       Modeling each draw as a <em>nondeterministic</em> value within its documented range means a proof
 *       that uses it holds for <em>EVERY</em> value the RNG could ever produce — strictly stronger than
 *       sampling. That is the valuable part: {@code nextInt(6) + 1} is proven to land in {@code 1..6} for
 *       all outcomes at once, not for the few a test happened to draw.</li>
 *   <li><b>The seeded-determinism contract is NOT modelable as nondet — and modeling it that way would be
 *       UNSOUND.</b> {@code new Random(42).nextInt() == new Random(42).nextInt()} is <em>true</em> in
 *       reality (same seed ⇒ same sequence), but a nondet draw would let the two sides differ and FALSELY
 *       REFUTE it. Reproducing seeded determinism needs the exact LCG algorithm — out of scope for a
 *       bounded model.</li>
 * </ul>
 *
 * <h2>The clean separation that keeps this sound</h2>
 * The no-arg constructor and the draw methods are modeled as nondet-in-range (sound, valuable). The
 * <b>seeded</b> surface — the {@code Random(long)} constructor and {@link #setSeed(long)} — is a LOUD
 * stub routed through {@code BmcUnmodelledReached} (an honest {@code UNKNOWN} under JBMC, never a silent
 * wrong value). Because you <em>cannot construct a seeded {@code Random} without going through the loud
 * constructor</em>, a seeded / reproducibility proof hits the loud stub and reports {@code UNKNOWN}
 * (honest) — the false-refutation above can never arise. An <em>unseeded</em> {@code Random}'s draws are
 * sound nondet. This separation is the whole design.
 *
 * <p>Per the library's no-{@code double} convention, {@code nextDouble}/{@code nextFloat}/
 * {@code nextGaussian} and the {@code doubles()}/{@code ints()}/{@code longs()} stream families are also
 * loud (the streams would need an unbounded element count; the no-arg {@code ints()}/{@code longs()} are
 * infinite). They live in the {@link BmcModelTail} together with the rest of the exotic
 * {@code RandomGenerator} default-method surface ({@code nextExponential}, the {@code from(...)} adapter,
 * {@code isDeprecated}, the bounded {@code float}/{@code double} overloads, {@code nextBytes}). Reaching
 * any of them is a NAMED, LOUD {@code UNKNOWN}, never a silent nondet stub.
 */
@BmcModelTail(reason = "exotic java.util.Random / RandomGenerator remainder — the double-valued draws "
        + "(nextDouble/nextFloat/nextGaussian/nextExponential, no-double policy), the ints()/longs()/"
        + "doubles() nondet streams (unbounded element count), nextBytes, the from(RandomGenerator) "
        + "adapter, and isDeprecated; loud (UNKNOWN) under JBMC if reached")
public class Random {

    /** The unseeded constructor — no state to keep, since every draw is fresh nondet. */
    public Random() {
    }

    /**
     * The SEEDED constructor is a LOUD stub, NOT nondet. Modeling a seeded {@code Random}'s draws as
     * nondet would falsely refute {@code new Random(42).nextInt() == new Random(42).nextInt()} (true in
     * reality). Reproducing it needs the exact LCG — out of scope — so we make constructing a seeded
     * {@code Random} an honest {@code UNKNOWN}: any reproducibility proof trips here and is correctly
     * undecided rather than silently wrong. (Constructors are outside the per-member audit surface, so
     * this carries no annotation; its loud body is the whole point.)
     */
    public Random(long seed) {
        throw fail("bmc4j: unmodelled member java.util.Random.<init>(long) — seeded determinism needs the "
                + "exact LCG (out of scope); a seeded Random is honestly UNKNOWN, never nondet (which would "
                + "falsely refute new Random(42).nextInt() == new Random(42).nextInt())");
    }

    /**
     * LOUD: re-seeding has the same seeded-determinism problem as the {@code Random(long)} constructor —
     * nondet would be unsound. Honest {@code UNKNOWN}.
     */
    @BmcUnmodelable(reason = "re-seeding restores seeded determinism — same unsoundness as the seeded ctor; "
            + "the LCG is out of scope")
    public void setSeed(long seed) {
        throw fail("bmc4j: unmodelled member java.util.Random.setSeed(long) — re-seeding restores seeded "
                + "determinism (the exact LCG is out of scope); honestly UNKNOWN, never nondet");
    }

    /** Any {@code int} — the full 32-bit domain, exactly the JDK's range for {@code nextInt()}. */
    @BmcModelConforms("nondet-in-range — proven for every outcome (RandomLaws, model proofs)")
    public int nextInt() {
        return CProver.nondetInt();
    }

    /**
     * Some value in {@code [0, bound)} — proven for EVERY outcome the RNG could produce. Throws
     * {@link IllegalArgumentException} on {@code bound <= 0}, exactly like the JDK.
     */
    @BmcModelConforms("nondet-in-range [0,bound) + bound check — RandomLaws model proofs")
    public int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be positive");
        }
        // [0, bound-1] inclusive == [0, bound).
        return Bmc_anyIntInRange(0, bound - 1);
    }

    /**
     * Some value in {@code [origin, bound)} — for every outcome. Throws {@link IllegalArgumentException}
     * when {@code origin >= bound} (empty range), exactly like the JDK.
     */
    @BmcModelConforms("nondet-in-range [origin,bound) + range check — RandomLaws model proofs")
    public int nextInt(int origin, int bound) {
        if (origin >= bound) {
            throw new IllegalArgumentException("bound must be greater than origin");
        }
        return Bmc_anyIntInRange(origin, bound - 1);
    }

    /** Any {@code long} — the full 64-bit domain. */
    @BmcModelConforms("nondet-in-range — proven for every outcome (RandomLaws model proofs)")
    public long nextLong() {
        return CProver.nondetLong();
    }

    /** Some value in {@code [0, bound)} — for every outcome. Throws on {@code bound <= 0} like the JDK. */
    @BmcModelConforms("nondet-in-range [0,bound) + bound check — RandomLaws model proofs")
    public long nextLong(long bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be positive");
        }
        return Bmc_anyLongInRange(0, bound - 1);
    }

    /** Some value in {@code [origin, bound)} — for every outcome. Throws on {@code origin >= bound}. */
    @BmcModelConforms("nondet-in-range [origin,bound) + range check — RandomLaws model proofs")
    public long nextLong(long origin, long bound) {
        if (origin >= bound) {
            throw new IllegalArgumentException("bound must be greater than origin");
        }
        return Bmc_anyLongInRange(origin, bound - 1);
    }

    /** Either {@code true} or {@code false} — both outcomes considered. */
    @BmcModelConforms("nondet boolean — proven true-or-false for every outcome (RandomLaws model proofs)")
    public boolean nextBoolean() {
        return CProver.nondetBoolean();
    }

    // --- internal nondet-in-range helpers ------------------------------------------------------------
    // Equivalent to Bmc.anyInt(lo, hi) / anyLong(lo, hi), inlined here so the model has no dependency on
    // the bmc-runtime Bmc facade (which is not on the models' compile classpath).

    private static int Bmc_anyIntInRange(int lo, int hi) {
        int v = CProver.nondetInt();
        CProver.assume(v >= lo && v <= hi);
        return v;
    }

    private static long Bmc_anyLongInRange(long lo, long hi) {
        long v = CProver.nondetLong();
        CProver.assume(v >= lo && v <= hi);
        return v;
    }
}
