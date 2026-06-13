package proofs.exceptions

import example.exceptions.checkedLabel
import org.bmc4j.Bmc
import org.bmc4j.BmcProof

/**
 * Kotlin regression for the exception-message-elision LIVE-LOCAL boundary (the Kotlin twin of
 * `proofs.errors.ExceptionMessageElisionProofs.live_local_survives_message_elision`).
 *
 * `checkedLabel` guards its argument with a `throw IllegalArgumentException("...$tag")` (a string-template
 * concat the elision rewrites) and carries a SYMBOLIC string through a local it returns. The argument is
 * `Bmc.anyAsciiString(...)` (never null), so the throw is never taken and nothing reads a message: AUTO
 * elides the message - but the returned `label` is LIVE. Eliding the message must leave it intact; a
 * rewrite that dropped the modified method's LocalVariableTable made `label` read back null, FALSE-REFUTING
 * this correct proof with a NullPointerException. VERIFIES with the fix, FALSE-REFUTES without it.
 */
class MessageElisionLiveLocalProofs {

    @BmcProof
    fun live_local_survives_message_elision() {
        val s = Bmc.anyAsciiString(2)          // a SYMBOLIC string, length 0..2
        val label = checkedLabel(s, 7)         // s != null: never throws; the message concat is elided
        Bmc.check(label.length <= 2)           // the carried symbolic string. NPE here without the fix.
    }
}
