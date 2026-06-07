package example.durations

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * `kotlin.time.Duration` — a value class over a unit-discriminating bit-packed `Long`. bmc4j models
 * that exact packing plus the value-class ABI (`plus-LRDsOJo`, `getInWholeSeconds-impl`, …), so
 * Duration arithmetic/comparison is usable in proofs (see the proofs). `java.time.Duration` is the
 * other modeled option; this exercises the Kotlin one.
 */
object Timeouts {

    /** Total of a base timeout plus a per-retry budget, in whole seconds. */
    @JvmStatic
    fun budgetSeconds(base: Int, retries: Int, perRetry: Int): Long {
        val total: Duration = base.seconds + (retries * perRetry).seconds
        return total.inWholeSeconds
    }

    /** Whether a duration exceeds a one-minute deadline. */
    @JvmStatic
    fun exceedsOneMinute(d: Duration): Boolean = d > 1.minutes
}
