package proofs.environment

import org.bmc4j.Bmc
import org.bmc4j.BmcProof
import org.bmc4j.Verdict

/**
 * Litmus proof for the UNMODELED-ENVIRONMENTAL-VALUE class of failures (the okio.Buffer REFUTED case),
 * distilled onto the [EnvBucketedPool] synthetic analog of okio's thread-sharded SegmentPool.
 *
 * The single observable claim — "one write into a fresh pool yields size 1" — is concretely always true.
 * It comes back REFUTED iff the per-thread bucket index (Thread.getId() & (availableProcessors()*2-1))
 * is symbolic, because a symbolic index into the static free-list array makes the pulled cell's length
 * nondet. Modeling Thread.getId() and Runtime.availableProcessors() as constants makes the index
 * concrete and the proof VERIFIES.
 */
class EnvironmentBucketLaws {

    // The fresh-pool reset + per-bucket static init walk the full (now-concrete) bucket array, so the
    // unwind bound must cover availableProcessors()*2 + 1 = 17 iterations; pin it above the auto cap.
    @BmcProof(unwind = 20)
    fun one_write_into_fresh_pool_yields_size_one() {
        Bmc.check(EnvBucketedPool.writeOneIntoFreshPool() == 1)
    }

    /**
     * The non-uniform-pool companion: a distinct-seeded pool where the loaded cell's length genuinely
     * depends on the bucket index, so the claim is provable ONLY when the index is concrete — i.e. only
     * because Thread.getId() and Runtime.availableProcessors() are modeled as CONSTANTS (thread id 1,
     * mask 15 → bucket 1, length 1 → result 2). A bounded-nondet id would leave this unprovable; that is
     * the empirical reason constants, not bounded-nondet, are the right model.
     */
    @BmcProof(unwind = 20)
    fun one_write_into_distinct_pool_picks_the_concrete_bucket() {
        Bmc.check(EnvBucketedPool.writeOneIntoDistinctPool() == 2)
    }
}
