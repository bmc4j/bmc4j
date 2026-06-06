package proofs.lateinitprops

import example.lateinitprops.Session
import org.bmc4j.Bmc
import org.bmc4j.BmcProof
import org.bmc4j.Verdict

/**
 * Pins `lateinit` semantics under BMC in all three directions: unguarded pre-init access refutes,
 * the `isInitialized` guard verifies, init-then-read verifies. (kotlinc lowers the read guard to
 * a field-null check whose failure path surfaces as a violation — the uninitialized access is a
 * real, findable defect, not a silently-null read.)
 */
class LateinitProofs {

    // FAIL (the bug): reading a lateinit property before initialization. Expected verdict:
    // REFUTED - the unguarded read is reachable with the property uninitialized.
    @BmcProof(expect = Verdict.REFUTED)
    fun read_before_init_is_a_defect() {
        val s = Session()
        Bmc.check(s.greetLength() >= 0)
    }

    // PASS: the isInitialized guard makes the read safe on every path.
    @BmcProof
    fun guarded_read_is_safe() {
        val s = Session()
        Bmc.check(s.safeGreetLength() == 0)
    }

    // PASS: initialize, then read — the promised lifecycle verifies end-to-end.
    @BmcProof
    fun init_then_read_is_safe() {
        val s = Session()
        s.start("ada")
        Bmc.check(s.greetLength() == 3)
    }
}
