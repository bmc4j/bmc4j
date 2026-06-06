package proofs.backingfields

import example.backingfields.SessionLog
import org.bmc4j.Bmc
import org.bmc4j.BmcProof
import org.bmc4j.Verdict

/**
 * An explicit backing field compiles to an ordinary JVM field of the backing type plus a getter
 * of the public type, so JBMC sees nothing new — and the pattern's promise ("only the class
 * mutates the list") is exactly the kind of invariant BMC can check at the mutation sites.
 */
class BackingFieldProofs {

    // FAIL (the bug): record() takes the duration on faith; one skewed clock and the total
    // goes negative. Expected verdict: REFUTED with a negative duration.
    @BmcProof(expect = Verdict.REFUTED)
    fun total_time_never_negative() {
        val log = SessionLog()
        log.record(Bmc.anyInt())
        Bmc.check(log.totalTime() >= 0)
    }

    // PASS: the clamped recorder preserves the invariant for every input.
    @BmcProof
    fun safe_total_time_never_negative() {
        val log = SessionLog()
        log.recordSafe(Bmc.anyInt())
        Bmc.check(log.totalTime() >= 0)
    }
}
