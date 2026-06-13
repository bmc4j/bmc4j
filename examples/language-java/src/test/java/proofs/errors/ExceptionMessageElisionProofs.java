package proofs.errors;

import example.errors.Parser;
import example.errors.Validator;
import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;
import org.bmc4j.RemoveExceptionMessages;
import org.bmc4j.Verdict;

/**
 * Litmus proofs for bmc4j's <b>exception-message elision</b> — dropping the construction of a thrown
 * exception's message when no code observes it, so a proof over a function that builds an expensive
 * dynamic error message on a dead branch becomes tractable.
 *
 * <p>Two properties are pinned here:
 * <ul>
 *   <li><b>Effectiveness</b> ({@link #doubled_is_tractable_when_message_elided}): a function whose dead
 *       branch builds an expensive message verifies under the default AUTO elision — the proof's cone
 *       observes no exception message, so the gate clears and the expensive message construction is
 *       never symbolically executed. Without elision the same proof is UNKNOWN (the message loop
 *       poisons it); run it with {@code -Dbmc.removeExceptionMessages=off} to see the before.</li>
 *   <li><b>Soundness</b> ({@link #message_content_is_NOT_elided_when_observed}): when a proof DOES
 *       observe an exception message (it reads {@code getMessage()}), the AUTO gate must NOT elide —
 *       otherwise the message would become {@code null} and an assertion over its content would be
 *       silently wrong. The observer's presence in the cone suppresses elision, so the real message is
 *       proven.</li>
 * </ul>
 */
class ExceptionMessageElisionProofs {

    // EFFECTIVENESS: doubleOrThrow doubles small inputs and throws (with a byte->String message) for
    // large ones. The throw branch is genuinely REACHABLE over this proof's input range, so BMC cannot
    // prune it and must encode the message's byte-decode. We exercise that range but never read the
    // message, so this proof's cone has NO message observer -> AUTO elision drops the byte-decode and the
    // proof is tractable and VERIFIES. Without elision (-Dbmc.removeExceptionMessages=off) the byte->String
    // materialization poisons it and the proof goes UNKNOWN — the UNKNOWN->VERIFIED flip this feature
    // delivers.
    @BmcProof
    void doubled_is_tractable_when_message_elided() {
        int n = Bmc.anyInt(0, 2_000_000);
        try {
            int r = Parser.doubleOrThrow(n);
            // Reached only for n <= 1_000_000; the doubling contract holds there.
            Bmc.check(r == 2 * n);
        } catch (IllegalArgumentException e) {
            // The overflow branch fires for large n. We observe NOTHING about the exception — not its
            // message — so eliding the (expensive) message construction is sound here.
        }
    }

    // SOUNDNESS: this proof reads e.getMessage(), so it observes an exception message — AUTO must NOT
    // elide. requirePositive throws "not positive" for a non-positive input; we prove that the thrown
    // message is exactly that. If the gate WRONGLY elided, getMessage() would be null and contains()
    // would throw NPE / the property would refute — so a VERIFIED here certifies the gate suppressed
    // elision in the presence of an observer. (Forcing elision here with removeExceptionMessages = ON would make
    // this REFUTE — the message is genuinely needed.)
    @BmcProof
    void message_content_is_NOT_elided_when_observed() {
        int n = Bmc.anyInt(-1000, 0);
        try {
            Validator.requirePositive(n);
            Bmc.check(false); // unreachable: n <= 0 always throws
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            Bmc.check(msg != null && msg.equals("not positive"));
        }
    }

    // SOUNDNESS, the inverse: FORCING elision (removeExceptionMessages = ON) over the SAME message-observing proof
    // drops the message to null, so the content assertion fails — a true REFUTED. This pins that forced
    // elision is a real, observable change (not a silent no-op) and that the framework reports it
    // honestly rather than as a false green.
    @BmcProof(removeExceptionMessages = RemoveExceptionMessages.ON, expect = Verdict.REFUTED)
    void forcing_elision_over_an_observed_message_refutes() {
        int n = Bmc.anyInt(-1000, 0);
        try {
            Validator.requirePositive(n);
            Bmc.check(false);
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            Bmc.check(msg != null && msg.equals("not positive"));
        }
    }
}
