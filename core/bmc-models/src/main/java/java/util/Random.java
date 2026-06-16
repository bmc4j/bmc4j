package java.util;

import static org.bmc4j.analysis.BmcUnmodelledReached.fail;

import java.util.random.RandomGenerator;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import org.bmc4j.models.audit.BmcModelConforms;
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
 * <h2>The {@code double}/{@code float} draws ARE modeled (nondet-in-range)</h2>
 * {@code nextDouble()}/{@code nextFloat()} return some value in {@code [0, 1)} and the bounded
 * {@code nextDouble(bound)}/{@code (origin, bound)} (and the {@code float} twins) return some value in
 * their documented range — each as a <em>nondeterministic</em> primitive draw constrained by
 * {@code CProver.assume}. This is sound: a primitive {@code double}/{@code float} comparison under JBMC
 * is bit-precise, and the {@code assume(v >= 0 && v < 1)} excludes NaN/±Inf (which compare false to
 * everything), so the modeled draw really is a finite value in range — proven for EVERY outcome, exactly
 * like the integral draws. (The old no-{@code double} convention does not apply to a draw whose only
 * operations are an in-range {@code assume}; the FP quirks bite ordering/hashing, not a range gate.)
 *
 * <p>Per-member LOUD ({@code @BmcUnmodelable}, honest {@code UNKNOWN} under JBMC if reached): the seeded
 * surface ({@code Random(long)} / {@code setSeed}, the LCG), {@code nextGaussian} (polar method:
 * {@code Math.log}/{@code sqrt} + a rejection loop), the {@code doubles()}/{@code ints()}/{@code longs()}
 * stream families (unbounded element count; the no-arg {@code ints()}/{@code longs()} are infinite),
 * {@code nextBytes}, {@code nextExponential}, the {@code from(RandomGenerator)} adapter, the protected
 * {@code next(int)} bit primitive, and {@code isDeprecated}. Reaching any is a NAMED, LOUD
 * {@code UNKNOWN}, never a silent nondet stub. The class-level {@code @BmcModelTail} is gone: every real
 * member is now an explicit per-member decision (modeled or loud).
 */
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

    // --- double / float draws (nondet-in-range — sound, see the class doc) ----------------------------
    // A primitive double/float comparison is bit-precise under JBMC, and the in-range assume excludes
    // NaN/±Inf (they compare false to every bound), so each draw is a finite value in its documented
    // range, proven for EVERY outcome. No FP ordering/hashing is involved here, so this is sound.

    /** Some value in {@code [0, 1)} — for every outcome. */
    @BmcModelConforms("nondet-in-range [0,1) double — proven for every outcome (JavaRandomLaws model proofs)")
    public double nextDouble() {
        double v = CProver.nondetDouble();
        // Split into atomic bounds, not one `&&` (see Bmc.anyInt(int, int)); each still excludes NaN.
        CProver.assume(v >= 0.0);
        CProver.assume(v < 1.0);
        return v;
    }

    /** Some value in {@code [0, bound)} — for every outcome. Throws on a non-finite/{@code <= 0} bound. */
    @BmcModelConforms("nondet-in-range [0,bound) double + bound check — JavaRandomLaws model proofs")
    public double nextDouble(double bound) {
        if (!(bound > 0.0 && bound < Double.POSITIVE_INFINITY)) {
            throw new IllegalArgumentException("bound must be finite and positive");
        }
        double v = CProver.nondetDouble();
        // Split into atomic bounds, not one `&&` (see Bmc.anyInt(int, int)); each still excludes NaN.
        CProver.assume(v >= 0.0);
        CProver.assume(v < bound);
        return v;
    }

    /** Some value in {@code [origin, bound)} — for every outcome. Throws on a bad/non-finite range. */
    @BmcModelConforms("nondet-in-range [origin,bound) double + range check — JavaRandomLaws model proofs")
    public double nextDouble(double origin, double bound) {
        if (!(origin < bound && origin > Double.NEGATIVE_INFINITY && bound < Double.POSITIVE_INFINITY)) {
            throw new IllegalArgumentException("bound must be greater than origin and the range finite");
        }
        double v = CProver.nondetDouble();
        // Split into atomic bounds, not one `&&` (see Bmc.anyInt(int, int)); each still excludes NaN.
        CProver.assume(v >= origin);
        CProver.assume(v < bound);
        return v;
    }

    /** Some value in {@code [0, 1)} — for every outcome. */
    @BmcModelConforms("nondet-in-range [0,1) float — proven for every outcome (JavaRandomLaws model proofs)")
    public float nextFloat() {
        float v = CProver.nondetFloat();
        // Split into atomic bounds, not one `&&` (see Bmc.anyInt(int, int)); each still excludes NaN.
        CProver.assume(v >= 0.0f);
        CProver.assume(v < 1.0f);
        return v;
    }

    /** Some value in {@code [0, bound)} — for every outcome. Throws on a non-finite/{@code <= 0} bound. */
    @BmcModelConforms("nondet-in-range [0,bound) float + bound check — JavaRandomLaws model proofs")
    public float nextFloat(float bound) {
        if (!(bound > 0.0f && bound < Float.POSITIVE_INFINITY)) {
            throw new IllegalArgumentException("bound must be finite and positive");
        }
        float v = CProver.nondetFloat();
        // Split into atomic bounds, not one `&&` (see Bmc.anyInt(int, int)); each still excludes NaN.
        CProver.assume(v >= 0.0f);
        CProver.assume(v < bound);
        return v;
    }

    /** Some value in {@code [origin, bound)} — for every outcome. Throws on a bad/non-finite range. */
    @BmcModelConforms("nondet-in-range [origin,bound) float + range check — JavaRandomLaws model proofs")
    public float nextFloat(float origin, float bound) {
        if (!(origin < bound && origin > Float.NEGATIVE_INFINITY && bound < Float.POSITIVE_INFINITY)) {
            throw new IllegalArgumentException("bound must be greater than origin and the range finite");
        }
        float v = CProver.nondetFloat();
        // Split into atomic bounds, not one `&&` (see Bmc.anyInt(int, int)); each still excludes NaN.
        CProver.assume(v >= origin);
        CProver.assume(v < bound);
        return v;
    }

    // --- loud walls (honest UNKNOWN under JBMC if reached) --------------------------------------------

    @BmcUnmodelable(reason = "the protected next(int) bit primitive exposes the LCG — modeling it as nondet "
            + "would break seeded determinism; the draw methods nondet directly instead")
    protected int next(int bits) {
        throw fail("bmc4j: unmodelled member java.util.Random.next(int) — the LCG bit primitive is out of "
                + "scope (seeded determinism); the public draws nondet directly, honestly UNKNOWN here");
    }

    @BmcUnmodelable(reason = "nextGaussian uses the polar method (Math.log/sqrt + a rejection loop) — no "
            + "sound bounded nondet model")
    public double nextGaussian() {
        throw fail("bmc4j: unmodelled member java.util.Random.nextGaussian() — the polar method "
                + "(Math.log/sqrt + rejection loop) has no sound bounded model; honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "parameterized Gaussian — same polar-method wall as nextGaussian()")
    public double nextGaussian(double mean, double stddev) {
        throw fail("bmc4j: unmodelled member java.util.Random.nextGaussian(double, double) — the polar "
                + "method (Math.log/sqrt + rejection loop) has no sound bounded model; honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "exponential draw via -log(nextDouble) — transcendental, no sound bounded model")
    public double nextExponential() {
        throw fail("bmc4j: unmodelled member java.util.Random.nextExponential() — the -log(U) transform is "
                + "transcendental with no sound bounded model; honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "fills a byte[] from the LCG stream — seeded determinism, no sound nondet model")
    public void nextBytes(byte[] bytes) {
        throw fail("bmc4j: unmodelled member java.util.Random.nextBytes(byte[]) — filling from the LCG "
                + "stream is seeded determinism, out of scope; honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "infinite IntStream — unbounded element count, can't unwind")
    public IntStream ints() {
        throw fail("bmc4j: unmodelled member java.util.Random.ints() — an infinite stream has an unbounded "
                + "element count and cannot be unwound; honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "sized IntStream — unbounded element count for a bounded model")
    public IntStream ints(long streamSize) {
        throw fail("bmc4j: unmodelled member java.util.Random.ints(long) — a sized random stream has an "
                + "unbounded element count for the bounded model; honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "infinite ranged IntStream — unbounded element count")
    public IntStream ints(int randomNumberOrigin, int randomNumberBound) {
        throw fail("bmc4j: unmodelled member java.util.Random.ints(int, int) — an infinite stream has an "
                + "unbounded element count and cannot be unwound; honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "sized ranged IntStream — unbounded element count")
    public IntStream ints(long streamSize, int randomNumberOrigin, int randomNumberBound) {
        throw fail("bmc4j: unmodelled member java.util.Random.ints(long, int, int) — a sized random stream "
                + "has an unbounded element count for the bounded model; honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "infinite LongStream — unbounded element count, can't unwind")
    public LongStream longs() {
        throw fail("bmc4j: unmodelled member java.util.Random.longs() — an infinite stream has an unbounded "
                + "element count and cannot be unwound; honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "sized LongStream — unbounded element count")
    public LongStream longs(long streamSize) {
        throw fail("bmc4j: unmodelled member java.util.Random.longs(long) — a sized random stream has an "
                + "unbounded element count for the bounded model; honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "infinite ranged LongStream — unbounded element count")
    public LongStream longs(long randomNumberOrigin, long randomNumberBound) {
        throw fail("bmc4j: unmodelled member java.util.Random.longs(long, long) — an infinite stream has an "
                + "unbounded element count and cannot be unwound; honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "sized ranged LongStream — unbounded element count")
    public LongStream longs(long streamSize, long randomNumberOrigin, long randomNumberBound) {
        throw fail("bmc4j: unmodelled member java.util.Random.longs(long, long, long) — a sized random "
                + "stream has an unbounded element count for the bounded model; honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "infinite DoubleStream — unbounded element count, can't unwind")
    public DoubleStream doubles() {
        throw fail("bmc4j: unmodelled member java.util.Random.doubles() — an infinite stream has an "
                + "unbounded element count and cannot be unwound; honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "sized DoubleStream — unbounded element count")
    public DoubleStream doubles(long streamSize) {
        throw fail("bmc4j: unmodelled member java.util.Random.doubles(long) — a sized random stream has an "
                + "unbounded element count for the bounded model; honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "infinite ranged DoubleStream — unbounded element count")
    public DoubleStream doubles(double randomNumberOrigin, double randomNumberBound) {
        throw fail("bmc4j: unmodelled member java.util.Random.doubles(double, double) — an infinite stream "
                + "has an unbounded element count and cannot be unwound; honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "sized ranged DoubleStream — unbounded element count")
    public DoubleStream doubles(long streamSize, double randomNumberOrigin, double randomNumberBound) {
        throw fail("bmc4j: unmodelled member java.util.Random.doubles(long, double, double) — a sized random "
                + "stream has an unbounded element count for the bounded model; honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "adapts an arbitrary RandomGenerator — open-universe device, no sound model")
    public static Random from(RandomGenerator generator) {
        throw fail("bmc4j: unmodelled member java.util.Random.from(java.util.random.RandomGenerator) — "
                + "adapting an arbitrary generator is an open-universe device with no sound model; "
                + "honestly UNKNOWN");
    }

    @BmcUnmodelable(reason = "RandomGenerator deprecation flag — no analysis-relevant behavior to model")
    public boolean isDeprecated() {
        throw fail("bmc4j: unmodelled member java.util.Random.isDeprecated() — a deprecation flag with no "
                + "analysis-relevant behavior; honestly UNKNOWN");
    }

    // --- internal nondet-in-range helpers ------------------------------------------------------------
    // Equivalent to Bmc.anyInt(lo, hi) / anyLong(lo, hi), inlined here so the model has no dependency on
    // the bmc-runtime Bmc facade (which is not on the models' compile classpath).

    private static int Bmc_anyIntInRange(int lo, int hi) {
        int v = CProver.nondetInt();
        // Split into atomic bounds, not one `&&` (see Bmc.anyInt(int, int)).
        CProver.assume(v >= lo);
        CProver.assume(v <= hi);
        return v;
    }

    private static long Bmc_anyLongInRange(long lo, long hi) {
        long v = CProver.nondetLong();
        // Split into atomic bounds, not one `&&` (see Bmc.anyInt(int, int)).
        CProver.assume(v >= lo);
        CProver.assume(v <= hi);
        return v;
    }
}
