package proofs.valueclasses

import example.valueclasses.Port
import example.valueclasses.next
import example.valueclasses.safeNext
import org.bmc4j.Bmc
import org.bmc4j.BmcProof
import org.bmc4j.Verdict
import org.bmc4j.kotlin.assumeValid

/**
 * Value classes carry invariants in their constructor (`init { require(...) }`). JBMC runs that
 * init, so the invariant is verified — and `assumeValid { ... }` reuses it to produce a symbolic
 * instance that is valid by construction, the right starting point for a proof.
 */
class PortProofs {

    // PASS: assumeValid prunes every raw value the constructor would reject, so the symbolic Port
    // is in range. (Without assumeValid the constructor would *throw* on out-of-range inputs.)
    @BmcProof
    fun assume_valid_yields_in_range() {
        val p = assumeValid { Port(Bmc.anyInt()) }
        Bmc.check(p.number in 1..65535)
    }

    // PASS: the init { require(...) } genuinely runs under BMC — construction succeeds exactly when
    // the input is in range. (Tautology only if JBMC executes the value class's constructor body.)
    @BmcProof
    fun init_is_enforced() {
        val raw = Bmc.anyInt()
        var ok = true
        try {
            Port(raw)
        } catch (e: IllegalArgumentException) {
            ok = false
        }
        Bmc.check(ok == (raw in 1..65535))
    }

    // FAIL (the bug): next(p) constructs Port(number + 1); at p == 65535 that is 65536, which the
    // constructor rejects — so next throws. BMC finds the boundary input p.number == 65535.
    // Expected verdict: REFUTED - the max port's successor escapes the value-class invariant.
    @BmcProof(expect = Verdict.REFUTED)
    fun successor_never_overflows() {
        val p = assumeValid { Port(Bmc.anyInt()) }
        Bmc.check(next(p).number > p.number)
    }

    // PASS: the saturating fix constructs a valid Port for every valid input, never throwing.
    @BmcProof
    fun safe_successor_is_monotonic() {
        val p = assumeValid { Port(Bmc.anyInt()) }
        Bmc.check(safeNext(p).number >= p.number)
    }
}
