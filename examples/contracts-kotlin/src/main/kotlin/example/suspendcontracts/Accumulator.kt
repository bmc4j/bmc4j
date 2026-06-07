package example.suspendcontracts

/**
 * A deliberately **impure** `suspend` function, for the purity-audit demo on suspend targets. [add]
 * mutates the receiver's pre-existing `total` field as a side effect — real impurity, distinct from
 * the benign coroutine plumbing every suspend body contains. The purity audit's coroutine allowance
 * list lets through the state-machine plumbing (the fresh continuation, the `COROUTINE_SUSPENDED`
 * sentinel read) but still rejects `this`-mutation, so a contract on [add] is rejected at proof time —
 * exactly like the non-suspend `Ledger.record`.
 */
class Accumulator {

    /** Pre-existing receiver state the impure suspend method mutates. */
    var total: Int = 0

    /** A suspend helper, so [add] is lowered to a state machine (a real suspension point) — proving the
     *  audit sees through the coroutine plumbing yet still catches the receiver mutation underneath. */
    private suspend fun bump(x: Int): Int = x + 1

    /** Adds `amount` to the running [total] and returns it — a `PUTFIELD` on `this`, so NOT a legal
     *  contract target even though it is `suspend`. */
    suspend fun add(amount: Int): Int {
        total = bump(total - 1 + amount) // PUTFIELD on `this`: a caller-observable side effect
        return total
    }
}
