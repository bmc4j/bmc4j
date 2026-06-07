package example.purity

/**
 * A deliberately **impure** Kotlin instance method, for the purity-audit demo. [record] returns the
 * running total *and* mutates the receiver's pre-existing `total` field as a side effect — a
 * value-returning method that is not a function of its inputs.
 *
 * This is the most common impurity for an instance method: a `this`-mutation. A contract summarizes
 * only the *return value*, so if a caller's call site were redirected to a `record__stub`, the
 * increment of `total` would silently never happen, yet the generated enforce-proof (which checks
 * `@Ensures`, not purity) would still pass. bmc4j's purity audit closes that false-green: a `PUTFIELD`
 * on `this` is a write to pre-existing (non-fresh) state, so the receiver-mutation is rejected — the
 * same conservative bias as a `PUTSTATIC`. See `contracts.purity.LedgerContract` and
 * `proofs.purity.PurityAuditDemoTest`.
 */
class Ledger {

    /** Pre-existing receiver state the impure method mutates. */
    var total: Int = 0

    /** Adds `amount` to the running [total] and returns the new total — a heap write to the receiver,
     *  so this is NOT a legal contract target. */
    fun record(amount: Int): Int {
        total += amount // PUTFIELD on `this`: a caller-observable side effect a contract would drop
        return total
    }
}
