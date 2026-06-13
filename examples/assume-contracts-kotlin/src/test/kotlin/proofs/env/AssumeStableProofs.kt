package proofs.env

import example.env.Buckets
import example.env.Environment
import org.bmc4j.Bmc
import org.bmc4j.BmcProof
import org.bmc4j.Verdict

/**
 * `Bmc.assumeStable` pins a deterministic, unanalyzable query to ONE fixed value for the whole run -
 * including the static initializer that reads it, a call site a local assume can't reach. The
 * "environment as a fixed value" case (okio's HASH_BUCKET_COUNT), driven from idiomatic Kotlin: a
 * method reference (env::bucketCount) plus a trailing-lambda predicate with `it`. The Kotlin
 * counterpart of proofs.env in examples/assume-contracts.
 */
class AssumeStableProofs {

    /**
     * VERIFIES: assumeStable(env::bucketCount) { it == 8 } pins the value the Buckets static
     * initializer reads - a call site a local assume could never reach - to one fixed symbol for the
     * whole run. The captured bound is then the concrete 8, so the array is sized 8 and
     * lastSlot() == count() - 1 can be proven by fully unwinding the fill loop.
     */
    @BmcProof(unwind = 10)
    fun `stable pins a clinit bound`() {
        val env: Environment = NondetEnvironment()
        Buckets.ENV = env
        Bmc.assumeStable(env::bucketCount) { it == 8 }
        val b = Buckets()
        Bmc.check(b.count() == 8)
        Bmc.check(b.lastSlot() == b.count() - 1)
    }

    /**
     * WITHOUT the pin the static-initializer-captured bound is SYMBOLIC (the unanalyzable bucketCount()
     * is nondet-stubbed), so count() can be anything - the claim count() == 8 is REFUTED. This is what
     * makes the pin LOAD-BEARING, and it reaches a call site (the static initializer) a local assume in
     * this proof body could not.
     */
    @BmcProof(expect = Verdict.REFUTED)
    fun `without the pin the value is symbolic`() {
        Buckets.ENV = NondetEnvironment()
        val b = Buckets()
        Bmc.check(b.count() == 8)
    }
}
