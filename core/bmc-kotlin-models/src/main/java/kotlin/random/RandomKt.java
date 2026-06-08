package kotlin.random;

import static org.bmc4j.analysis.BmcUnmodelledReached.fail;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcUnmodelable;

import java.util.NoSuchElementException;

/**
 * Clean model of the {@code kotlin.random.RandomKt} facade — home of the top-level {@code Random(seed)}
 * factories and the {@code IntRange.random}/{@code LongRange.random} extensions. This is the LOUD half of
 * the seeded/unseeded separation described on {@link kotlin.random.Random}.
 *
 * <p>{@code Random(Int)} and {@code Random(Long)} construct a <em>seeded</em> {@code XorWowRandom} whose
 * sequence is reproducible — {@code Random(42).nextInt() == Random(42).nextInt()} is <em>true</em>. A
 * nondet model would FALSELY REFUTE that, and reproducing the real sequence needs the exact
 * {@code XorWowRandom} algorithm (out of scope). So both factories are LOUD stubs: a seeded /
 * reproducibility proof reaching one reports {@code UNKNOWN} (honest), never a false counterexample.
 * Because the only way to obtain a seeded {@code Random} is through these factories, the false-refutation
 * can never arise — an <em>unseeded</em> {@code Random.Default} draws sound nondet (see
 * {@link kotlin.random.Random}).
 *
 * <p>The range-extension draws {@code nextInt(Random, IntRange)} / {@code nextLong(Random, LongRange)} are
 * MODELED by delegating to the modeled {@code Random.nextInt(from, until)} / {@code nextLong(from, until)}
 * nondet-in-range draws (with the stdlib's exact {@code last == MAX_VALUE} overflow handling and the
 * empty-range {@code NoSuchElementException}) — so a ranged draw is sound-for-every-outcome, never a
 * silent stub. The {@code fastLog2}/{@code takeUpperBits}/{@code checkRangeBounds}/
 * {@code boundsErrorMessage} stdlib internals stay loud {@link BmcUnmodelable} stubs (out of scope), so
 * the whole facade surface is per-member accounted — no {@code @BmcModelTail} catch-all.
 */
public final class RandomKt {

    private RandomKt() {
    }

    // --- range-extension draws: sound nondet-in-range via the modeled Random draws ---

    /** {@code IntRange.random(random)} → a draw in {@code [first, last]}, throwing on an empty range. */
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int nextInt(Random random, kotlin.ranges.IntRange range) {
        if (range.isEmpty()) {
            throw new NoSuchElementException("Cannot get random in empty range: " + range);
        }
        int first = range.getFirst();
        int last = range.getLast();
        if (last < Integer.MAX_VALUE) {
            return random.nextInt(first, last + 1);
        }
        if (first > Integer.MIN_VALUE) {
            return random.nextInt(first - 1, last) + 1;
        }
        return random.nextInt();
    }

    /** {@code LongRange.random(random)} → a draw in {@code [first, last]}, throwing on an empty range. */
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static long nextLong(Random random, kotlin.ranges.LongRange range) {
        if (range.isEmpty()) {
            throw new NoSuchElementException("Cannot get random in empty range: " + range);
        }
        long first = range.getFirst();
        long last = range.getLast();
        if (last < Long.MAX_VALUE) {
            return random.nextLong(first, last + 1);
        }
        if (first > Long.MIN_VALUE) {
            return random.nextLong(first - 1, last) + 1;
        }
        return random.nextLong();
    }

    // --- stdlib internals (loud stubs; a reach demotes to a member-named UNKNOWN) ---

    @BmcUnmodelable(reason = "stdlib internal (error-message formatting) — out of scope; loud if reached")
    public static String boundsErrorMessage(Object from, Object until) {
        throw fail("bmc4j: unmodelled member kotlin.random.RandomKt.boundsErrorMessage(java.lang.Object,"
                + "java.lang.Object) — stdlib internal error-message formatting");
    }

    @BmcUnmodelable(reason = "stdlib internal range-bounds check — out of scope; loud if reached")
    public static void checkRangeBounds(int from, int until) {
        throw fail("bmc4j: unmodelled member kotlin.random.RandomKt.checkRangeBounds(int,int) — stdlib internal");
    }

    @BmcUnmodelable(reason = "stdlib internal range-bounds check — out of scope; loud if reached")
    public static void checkRangeBounds(long from, long until) {
        throw fail("bmc4j: unmodelled member kotlin.random.RandomKt.checkRangeBounds(long,long) — stdlib internal");
    }

    @BmcUnmodelable(reason = "stdlib internal range-bounds check over doubles (no-double policy) — loud if reached")
    public static void checkRangeBounds(double from, double until) {
        throw fail("bmc4j: unmodelled member kotlin.random.RandomKt.checkRangeBounds(double,double) — stdlib internal");
    }

    @BmcUnmodelable(reason = "stdlib internal bit helper — out of scope; loud if reached")
    public static int fastLog2(int value) {
        throw fail("bmc4j: unmodelled member kotlin.random.RandomKt.fastLog2(int) — stdlib internal bit helper");
    }

    @BmcUnmodelable(reason = "stdlib internal bit helper — out of scope; loud if reached")
    public static int takeUpperBits(int bits, int bitCount) {
        throw fail("bmc4j: unmodelled member kotlin.random.RandomKt.takeUpperBits(int,int) — stdlib internal bit helper");
    }

    /**
     * LOUD: the seeded {@code Random(Int)} factory. A seeded {@code Random}'s sequence is reproducible;
     * modeling it as nondet would falsely refute {@code Random(7).nextInt() == Random(7).nextInt()}.
     * Honest {@code UNKNOWN}.
     */
    @BmcUnmodelable(reason = "seeded XorWowRandom factory — reproducible sequence; nondet would falsely "
            + "refute Random(seed) reproducibility, and the exact algorithm is out of scope")
    public static Random Random(int seed) {
        throw fail("bmc4j: unmodelled member kotlin.random.RandomKt.Random(int) — seeded XorWowRandom "
                + "(reproducible sequence) needs the exact algorithm (out of scope); a seeded Random is "
                + "honestly UNKNOWN, never nondet");
    }

    /**
     * LOUD: the seeded {@code Random(Long)} factory — same reasoning as {@link #Random(int)}.
     */
    @BmcUnmodelable(reason = "seeded XorWowRandom factory — reproducible sequence; nondet would falsely "
            + "refute Random(seed) reproducibility, and the exact algorithm is out of scope")
    public static Random Random(long seed) {
        throw fail("bmc4j: unmodelled member kotlin.random.RandomKt.Random(long) — seeded XorWowRandom "
                + "(reproducible sequence) needs the exact algorithm (out of scope); a seeded Random is "
                + "honestly UNKNOWN, never nondet");
    }
}
