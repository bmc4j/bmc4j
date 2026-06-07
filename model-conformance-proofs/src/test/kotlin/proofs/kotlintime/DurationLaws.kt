package proofs.kotlintime

import org.bmc4j.Bmc
import org.bmc4j.BmcProof
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Model proofs (axis 2) for the `kotlin.time.Duration` value-class model. The inline unit extensions
 * (`a.seconds`, `90.minutes`) compile to `DurationKt.toDuration` + the mangled value-class ABI
 * (`Duration."plus-LRDsOJo"`, `"getInWholeSeconds-impl"`, `"compareTo-LRDsOJo"`); the real chain reaches
 * `DurationKt` internals JBMC stubs, so even `a.seconds + b.seconds` spuriously refuted. These laws pin
 * the model's algebra under JBMC. Exact differential parity vs the JVM `kotlin.time.Duration` (including
 * the nanos/millis saturation boundary) is in `conformance.TimeConformanceTest`.
 */
class DurationLaws {

    /**
     * inWholeSeconds of a.seconds + b.seconds is a + b (the issue's headline probe, symbolic). The range
     * is kept modest because the model's faithful seconds<->nanos division (the price of differential
     * exactness) is solver-heavy over a wide symbolic range; exact bit-for-bit parity over a much wider
     * range — incl. the nanos/millis saturation boundary — is in conformance.KotlinDurationConformanceTest.
     */
    @BmcProof
    fun seconds_add_inWholeSeconds() {
        val a = Bmc.anyInt(0, 60)
        val b = Bmc.anyInt(0, 60)
        val d = a.seconds + b.seconds
        Bmc.check(d.inWholeSeconds == (a + b).toLong())
    }

    /** Cross-unit comparison: 90 minutes is longer than 1 hour. */
    @BmcProof
    fun cross_unit_comparison() {
        Bmc.check(90.minutes > 1.hours)
    }

    /** seconds round-trips through inWholeSeconds (within the nanos range), incl. negatives. */
    @BmcProof
    fun seconds_roundtrip() {
        val a = Bmc.anyInt(-60, 60)
        Bmc.check(a.seconds.inWholeSeconds == a.toLong())
    }

    /** Unit equivalences: 60 seconds == 1 minute, 60 minutes == 1 hour. */
    @BmcProof
    fun unit_equivalences() {
        Bmc.check(60.seconds == 1.minutes && 60.minutes == 1.hours)
    }

    /** minus is the inverse of plus: (a + b) - b == a, observed in whole seconds. */
    @BmcProof
    fun minus_inverts_plus() {
        val a = Bmc.anyInt(0, 60)
        val b = Bmc.anyInt(0, 60)
        val sum = a.seconds + b.seconds
        Bmc.check((sum - b.seconds).inWholeSeconds == a.toLong())
    }

    /** Negation flips sign: -(a.seconds) is negative for a > 0 and has magnitude a. */
    @BmcProof
    fun negation_flips_sign() {
        val a = Bmc.anyInt(1, 60)
        val neg = -(a.seconds)
        Bmc.check(neg.isNegative() && neg.inWholeSeconds == (-a).toLong())
    }

    /** Comparison is consistent with the numeric second counts. */
    @BmcProof
    fun comparison_tracks_seconds() {
        val a = Bmc.anyInt(0, 1000)
        val b = Bmc.anyInt(0, 1000)
        Bmc.assume(a < b)
        Bmc.check(a.seconds < b.seconds)
    }
}
