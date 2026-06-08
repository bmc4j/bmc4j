package kotlin.random;

import static org.bmc4j.analysis.BmcUnmodelledReached.fail;

import org.bmc4j.models.audit.BmcModelTail;
import org.bmc4j.models.audit.BmcUnmodelable;

/**
 * Clean model of the {@code kotlin.random.RandomKt} facade — home of the top-level {@code Random(seed)}
 * factories. This is the LOUD half of the seeded/unseeded separation described on
 * {@link kotlin.random.Random}.
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
 * <p>The range-extension draws {@code nextInt(Random, IntRange)} / {@code nextLong(Random, LongRange)}
 * are absorbed by the {@link BmcModelTail} for now (they'd need {@code IntRange}/{@code LongRange}
 * field-accessor interop; a proof wanting a ranged draw uses the modeled {@code Random.nextInt(from,
 * until)} directly, which is sound nondet-in-range). The remaining {@code fastLog2}/{@code takeUpperBits}/
 * {@code checkRangeBounds}/{@code boundsErrorMessage} stdlib internals are tail too.
 */
@BmcModelTail(reason = "RandomKt remainder — the nextInt(Random,IntRange)/nextLong(Random,LongRange) "
        + "range-extension draws (use Random.nextInt(from,until) directly) and the stdlib internals "
        + "fastLog2/takeUpperBits/checkRangeBounds/boundsErrorMessage; loud (UNKNOWN) under JBMC if reached")
public final class RandomKt {

    private RandomKt() {
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
