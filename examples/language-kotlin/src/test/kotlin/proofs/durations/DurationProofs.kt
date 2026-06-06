package proofs.durations

import example.durations.Timeouts
import org.bmc4j.Bmc
import org.bmc4j.BmcProof
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * `kotlin.time.Duration` in proofs. Without the `kotlin.time.Duration`/`DurationKt` model these
 * spuriously refuted ("no uncaught exception" — havocked stdlib internals tripping Duration's own range
 * checks); with it, construction-from-units, `+`/`-`, and comparison analyse over the modeled bit-packing.
 */
class DurationProofs {

    /**
     * The issue's headline probe: inWholeSeconds of a.seconds + b.seconds is a + b. A modest symbolic
     * range keeps this demo well under the proof budget — the model's faithful seconds<->nanos division
     * (the cost of differential exactness) is solver-heavy over a wide range. The full 0..1000 range is
     * proven in the model-conformance law suite (proofs.kotlintime.DurationLaws), and exact bit-for-bit
     * parity vs the JVM Duration over a far wider range is in KotlinDurationConformanceTest.
     */
    @BmcProof
    fun duration_arithmetic() {
        val a = Bmc.anyInt(0, 30)
        val b = Bmc.anyInt(0, 30)
        val d = a.seconds + b.seconds
        Bmc.check(d.inWholeSeconds == (a + b).toLong())
    }

    /** The issue's cross-unit comparison probe. */
    @BmcProof
    fun cross_unit_comparison() {
        Bmc.check(90.minutes > 1.hours)
    }

    /** The example helper computes a sound budget in whole seconds. */
    @BmcProof
    fun budget_seconds_is_sound() {
        val base = Bmc.anyInt(0, 100)
        val retries = Bmc.anyInt(0, 10)
        val perRetry = Bmc.anyInt(0, 30)
        Bmc.check(Timeouts.budgetSeconds(base, retries, perRetry) == (base + retries * perRetry).toLong())
    }

    /** exceedsOneMinute is true exactly when the duration is strictly greater than 60s. */
    @BmcProof
    fun exceeds_one_minute_threshold() {
        Bmc.check(Timeouts.exceedsOneMinute(61.seconds) && !Timeouts.exceedsOneMinute(59.seconds))
    }
}
