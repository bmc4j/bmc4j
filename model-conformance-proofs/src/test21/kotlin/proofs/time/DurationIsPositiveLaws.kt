package proofs.time

import org.bmc4j.Bmc
import org.bmc4j.BmcProof
import java.time.Duration

/**
 * Duration.isPositive() only exists from Java 18 onward, so this proof compiles solely on the
 * 21+ floor — it lives in the jvm21+ source set that the build wires into the test compilation
 * only when bmcJvmTarget >= 21. The 17-floor sign laws (isZero/isNegative) stay in TimeLaws.
 */
class DurationIsPositiveLaws {

    private fun anySec(): Long = Bmc.anyInt(-1_000, 1_000).toLong()

    @BmcProof
    fun duration_isPositive_iff_seconds_positive() {
        val s = anySec()
        val d = Duration.ofSeconds(s)
        Bmc.check(d.isPositive == (s > 0L))
    }
}
