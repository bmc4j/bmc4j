package org.bmc4j.contracts

/**
 * The contracts authoring DSL - a typed front-end over the existing contract backend
 * ([org.bmc4j.engine.ContractEnforceProofGenerator] and the replace-stub machinery). A contract is
 * written as a typed pre/post-condition over an unbound method reference, so the predicate lambdas are
 * fully typed from the reference signature and refactor-safe (the issue's two drivers: contract code you
 * do not own, and typed authoring instead of stringly-typed `@Ensures("...")`).
 *
 * ```kotlin
 * contractFor(Account::project) {
 *     whenPrecondition("amount in range") { self, amount -> self.balance in 0..1000 && amount in 0..8 }
 *         .thenPostCondition("result at least balance") { before, after, amount, ret -> ret >= before.balance }
 * }
 * ```
 *
 * ## How it reaches the existing enforce-proof backend (no new engine)
 * `contractFor(...) { ... }` and its `whenPrecondition`/`thenPostCondition` calls are **markers**: their
 * bodies are never executed. The arguments are typed as **functional interfaces** (not `KFunction`), so
 * the compiler emits the method reference and each predicate lambda as `invokedynamic` SAM-conversion
 * sites whose bootstrap arguments carry a [java.lang.invoke.MethodHandle] to the underlying compiled
 * method (the dependency for the reference; a private static synthetic for a lambda body). A build-time
 * pass reads those handles **statically** - the exact technique [org.bmc4j.engine.AssumeContractBytecode]
 * uses to sidestep the invokedynamic fault line - and lowers the contract to the same artifacts the
 * annotation form produces:
 * - predicate calls are **direct `invokestatic`s to the lambda bodies** (a megamorphic `FunctionN.invoke`
 *   call is NOT devirtualized by JBMC, so the lowering never routes through it);
 * - an **enforce-proof** `@BmcProof` discharging `assume(pre); run real body; check(post)` against the
 *   real method, so a false contract turns the build red exactly as `@BmcContractsFor` does.
 *
 * ## Increment 1 scope
 * This first slice covers a single **instance method with one argument** (`Self.member(A): R`), the
 * minimal end-to-end typed pre/post path. `before`/`after` for arg-mutating or multi-subject methods,
 * the `updatesOnly` frame, `thenThrows<E>`, the enforcement levels, and the Java lowering target are
 * later phases (see the issue).
 */

/** An unbound single-argument instance-method reference `(Self, A) -> R`, the spine of a [contractFor].
 *  A functional interface (not `KFunction`) so `Account::project` SAM-converts to a LambdaMetafactory
 *  indy the build-time lowering decodes - threading the receiver in as `self`, so the predicate lambdas
 *  are typed from this signature. */
fun interface InstanceRef1<Self, A, R> {
    fun invoke(self: Self, arg: A): R
}

/** A precondition over the receiver pre-state (`self`) and the call argument. */
fun interface Precondition1<Self, A> {
    fun test(self: Self, arg: A): Boolean
}

/** A postcondition over the receiver pre-state (`before`), post-state (`after`), the call argument, and
 *  the result (`ret`). The two receiver params relate pre/post state directly - no `old(...)` marker. */
fun interface Postcondition1<Self, A, R> {
    fun test(before: Self, after: Self, arg: A, ret: R): Boolean
}

/**
 * Open a contract for the unbound instance-method reference [member] (receiver threaded as `self`). The
 * [body] declares one or more guarded `whenPrecondition(...).thenPostCondition(...)` cases. A marker -
 * never executed; the build-time lowering reads [member] and the predicate lambdas statically.
 *
 * Increment 1: a single-argument instance method `Self.member(A): R`.
 */
fun <Self, A, R> contractFor(member: InstanceRef1<Self, A, R>, body: ContractBuilder<Self, A, R>.() -> Unit) {
    // Marker. The reference [member] and the predicate lambdas inside [body] are decoded from bytecode by
    // ContractDslBytecode, which generates the enforce-proof. Calling the body here would run the
    // (never-evaluated) predicate lambdas, so we deliberately do not invoke it.
}

/** Builds the guarded cases of a [contractFor] block. A marker receiver - its methods are decoded, not
 *  run. Typed so the predicate lambdas bind `self`, the call argument, and `ret` from the reference. */
class ContractBuilder<Self, A, R> {

    /**
     * Open a guarded case whose precondition is [predicate] over `self` (the receiver pre-state) and the
     * call argument. [label] documents the case (carried into diagnostics in a later phase).
     */
    fun whenPrecondition(label: String, predicate: Precondition1<Self, A>): PreconditionCase<Self, A, R> =
            PreconditionCase()
}

/** A `when` case awaiting its postcondition. */
class PreconditionCase<Self, A, R> {

    /**
     * State the postcondition [predicate] over the receiver's pre-state (`before`) and post-state
     * (`after`), the call argument, and the result (`ret`). For increment 1's pure instance method
     * `before` and `after` are the same receiver state (no mutation), but the two-parameter shape is the
     * authoring surface the issue specifies; relating them directly removes any `old(...)` marker.
     */
    fun thenPostCondition(label: String, predicate: Postcondition1<Self, A, R>) {
        // Marker - decoded, not run.
    }
}
