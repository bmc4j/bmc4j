package proofs.constructreceiver

import example.constructreceiver.Receiver
import org.bmc4j.Bmc.anyInt
import org.bmc4j.Bmc.check
import org.bmc4j.BmcProof
import org.bmc4j.LoopUnwind

/**
 * Receiver construction: an INSTANCE `@BmcProof` reads its instance fields as the values their
 * initializers (run in `<init>`) produce, exactly as JUnit would — not as the nondet `this` jbmc would
 * otherwise synthesise.
 *
 * Each proof's state lives in INSTANCE fields on the proof class itself (initialised by Kotlin's primary
 * constructor, i.e. `<init>`). Without receiver construction:
 *  - [`an instance array is its initializer`] would false-REFUTE: `arr` reads as a nondet array ref
 *    (null -> NPE, or unknown length -> AIOOBE);
 *  - [`an instance scalar is its initializer`] would silently quantify over all `n` and the `n == 8`
 *    property would not hold (REFUTED).
 * With it (the default), both VERIFY.
 */
class ConstructReceiverProofs {

    // Instance field initialised in <init>: a concrete 5-element table.
    private val arr = intArrayOf(10, 20, 30, 40, 50)

    // Instance scalar initialised in <init>.
    private val n = 8

    /**
     * VERIFIES only because the receiver is constructed: every index in 0..4 is in bounds for the
     * concrete 5-element `arr`. Without construction `arr` is a nondet ref (null/unknown-length), so the
     * lookup false-REFUTES with an NPE/AIOOBE.
     */
    @BmcProof
    fun `an instance array is its initializer`() {
        val i = anyInt(0, 4)
        check(Receiver.bandLength(arr, i) >= 10)
    }

    /**
     * VERIFIES only because the receiver is constructed: `n` is its initializer, 8. Without construction
     * `n` is a silent nondet int and `n == 8` is REFUTED (it holds only for one of the quantified values).
     */
    @BmcProof
    fun `an instance scalar is its initializer`() {
        check(Receiver.isEight(n))
    }

    /**
     * Loop-id preservation: this instance proof has ONE loop, pinned by its engine loop id
     * `java::proofs.constructreceiver.ConstructReceiverProofs.loopOverInstanceArray:()V.0`. Constructing
     * the receiver (a separate static wrapper) must NOT renumber or move that loop — the pin still binds,
     * so the proof VERIFIES. (A non-plain backtick name would change the loop-id text; this one is plain
     * so the id is stable and easy to pin.) Reads the instance `arr` initializer too.
     */
    @BmcProof
    @LoopUnwind(
            loop = "java::proofs.constructreceiver.ConstructReceiverProofs.loopOverInstanceArray:()V.0",
            bound = 6)
    fun loopOverInstanceArray() {
        var sum = 0
        for (i in arr.indices) {
            sum += arr[i]
        }
        check(sum == 150) // 10+20+30+40+50, only true for the constructed `arr`
    }
}
