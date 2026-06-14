package org.bmc4j.contracts

/**
 * The contracts authoring DSL - a typed pre/post-condition front-end over the existing enforce/summarize
 * backend (no new engine). A contract is a top-level `val` in a contracts source file:
 *
 * ```kotlin
 * val deposit = contractFor(Account::deposit) {            // (Account, Int) -> Unit ; receiver is `self`
 *     whenPrecondition("amount positive") { self, amount -> amount > 0 }
 *         .thenPostCondition("balance grew by amount") { before, after, amount, ret ->
 *             after.balance == before.balance + amount
 *         }
 *         .updatesOnly { self, amount -> self.balance }
 *
 *     whenPrecondition("amount is zero") { self, amount -> amount == 0 }
 *         .thenThrows<IllegalArgumentException>("rejects zero")
 * }
 * ```
 *
 * The unbound method reference threads the receiver in as `self` (a static target simply has no `self`
 * slot - the form is uniform), and the predicate lambdas are fully typed from the reference signature:
 * `self`/`before`/`after`/`ret` are concrete types, not stringly-typed string predicates. The two drivers
 * (per the issue): contract code you do not own (no annotation needed) and refactor-safe typed authoring.
 *
 * The arity-specific [ContractBuilder1] / [PreconditionCase1] etc. carry the reference's type parameters
 * (`Self`, the argument types, the result `R`) so kotlinc types each predicate lambda's parameters
 * directly from the reference; there is no codegen for arg binding.
 *
 * ## How it reaches the backend (execute, then translate)
 * The `contractFor(...) { ... }` block **executes at build time**: the body lambda runs, each
 * `whenPrecondition(...).thenPostCondition(...)` / `.thenThrows<E>(...)` / `.updatesOnly { ... }` appends a
 * case (label + frame + expected exit) to a [ContractDefinition], and the returned definition
 * **self-registers** into [ContractRegistry]. The bmc4j Gradle plugin drains the registry after the
 * contracts source set compiles, then lowers each definition onto the enforce-proof / call-site machinery.
 * The predicate **implementation handles** are read by a narrow static decode of the same `contractFor`
 * site (the indy-bootstrap-handle read `Bmc.assumeEvery` uses), zipped 1:1 with registration order, and
 * lowered to DIRECT `invokestatic`/`invokevirtual` calls (never a megamorphic `FunctionN.invoke`).
 */

// --- member references (unbound; receiver threads in as `self`) -------------------------------------

/** An unbound zero-argument member reference `(Self) -> R`. */
fun interface MemberRef0<Self, R> {
    fun invoke(self: Self): R
}

/** An unbound single-argument member reference `(Self, A) -> R`. */
fun interface MemberRef1<Self, A, R> {
    fun invoke(self: Self, a: A): R
}

/** An unbound two-argument member reference `(Self, A, B) -> R`. */
fun interface MemberRef2<Self, A, B, R> {
    fun invoke(self: Self, a: A, b: B): R
}

// --- precondition predicates (over `self` pre-state + the call arguments) ---------------------------

fun interface Pre0<Self> {
    fun test(self: Self): Boolean
}

fun interface Pre1<Self, A> {
    fun test(self: Self, a: A): Boolean
}

fun interface Pre2<Self, A, B> {
    fun test(self: Self, a: A, b: B): Boolean
}

// --- postcondition predicates (over `before`/`after` pre/post-state, the call arguments, and `ret`) --

fun interface Post0<Self, R> {
    fun test(before: Self, after: Self, ret: R): Boolean
}

fun interface Post1<Self, A, R> {
    fun test(before: Self, after: Self, a: A, ret: R): Boolean
}

fun interface Post2<Self, A, B, R> {
    fun test(before: Self, after: Self, a: A, b: B, ret: R): Boolean
}

// --- frame predicates (the locations a case may change; only their reads matter) --------------------

fun interface Frame0<Self> {
    fun locations(self: Self)
}

fun interface Frame1<Self, A> {
    fun locations(self: Self, a: A)
}

fun interface Frame2<Self, A, B> {
    fun locations(self: Self, a: A, b: B)
}

// --- enforcement knobs ------------------------------------------------------------------------------

/** How much of a contract is proved versus trusted - a single, blanket, contract-level flag. */
enum class EnforcementLevel {
    /** Default. Predicates are audited pure-and-closed AND the enforce proof verifies the contract
     *  against the real body. Fully sound, nothing trusted. */
    MUST_BE_PURE,

    /** Skip the purity audit (you vouch the predicates are pure); the enforce proof still runs, so the
     *  contract is still checked against the body. Trust surface: predicate purity only. */
    TRUSTED_PURE,

    /** Enforce nothing: the contract is assumed (a trusted axiom) and the enforce proof is skipped.
     *  A verdict that uses a `NONE` contract is tainted `[NONE: assumed]` and that taint rides up. */
    NONE
}

/** The expected verdict of a contract's generated enforce proof. A deliberately-false contract pins
 *  [REFUTED] so its self-asserting demo turns green by refutation, expressed IN the DSL (a parameter on
 *  [contractFor]), never an annotation wrapper. */
enum class ExpectEnforce {
    VERIFIED,
    REFUTED
}

// --- the spine: contractFor(member, level, expect) { ... } ------------------------------------------

fun <Self, R> contractFor(
        member: MemberRef0<Self, R>,
        level: EnforcementLevel = EnforcementLevel.MUST_BE_PURE,
        expect: ExpectEnforce = ExpectEnforce.VERIFIED,
        body: ContractBuilder0<Self, R>.() -> Unit,
): ContractDefinition {
    val core = ContractCore(0, level, expect)
    ContractBuilder0<Self, R>(core).body()
    return core.finishAndRegister()
}

fun <Self, A, R> contractFor(
        member: MemberRef1<Self, A, R>,
        level: EnforcementLevel = EnforcementLevel.MUST_BE_PURE,
        expect: ExpectEnforce = ExpectEnforce.VERIFIED,
        body: ContractBuilder1<Self, A, R>.() -> Unit,
): ContractDefinition {
    val core = ContractCore(1, level, expect)
    ContractBuilder1<Self, A, R>(core).body()
    return core.finishAndRegister()
}

fun <Self, A, B, R> contractFor(
        member: MemberRef2<Self, A, B, R>,
        level: EnforcementLevel = EnforcementLevel.MUST_BE_PURE,
        expect: ExpectEnforce = ExpectEnforce.VERIFIED,
        body: ContractBuilder2<Self, A, B, R>.() -> Unit,
): ContractDefinition {
    val core = ContractCore(2, level, expect)
    ContractBuilder2<Self, A, B, R>(core).body()
    return core.finishAndRegister()
}

/** The non-generic accumulator the arity-typed builders/cases delegate to. Holds the case drafts in
 *  source order; the type parameters live only on the wrappers (so the lambdas are typed) and erase here. */
class ContractCore internal constructor(
        private val arity: Int,
        private val level: EnforcementLevel,
        private val expect: ExpectEnforce,
) {
    private val cases = ArrayList<CaseDraft>()

    internal fun open(label: String): CaseDraft {
        val draft = CaseDraft(label)
        cases.add(draft)
        return draft
    }

    internal fun finishAndRegister(): ContractDefinition {
        require(cases.isNotEmpty()) {
            "a contractFor(...) block declares no whenPrecondition - a contract needs at least one case."
        }
        val def = ContractDefinition(arity, level, expect, cases.map { it.toCase() })
        ContractRegistry.register(def)
        return def
    }
}

// --- arity 0 ----------------------------------------------------------------------------------------

class ContractBuilder0<Self, R> internal constructor(private val core: ContractCore) {
    fun whenPrecondition(label: String, predicate: Pre0<Self>): PreconditionCase0<Self, R> =
            PreconditionCase0(core.open(label))
}

class PreconditionCase0<Self, R> internal constructor(private val draft: CaseDraft) {
    fun thenPostCondition(label: String, predicate: Post0<Self, R>): PreconditionCase0<Self, R> {
        draft.postLabels.add(label); return this
    }

    inline fun <reified E : Throwable> thenThrows(label: String): PreconditionCase0<Self, R> {
        recordThrows(E::class.java, label); return this
    }

    @PublishedApi
    internal fun recordThrows(type: Class<out Throwable>, label: String) = draft.setThrows(type, label)

    fun updatesOnly(frame: Frame0<Self>): PreconditionCase0<Self, R> {
        draft.hasExplicitFrame = true; return this
    }
}

// --- arity 1 ----------------------------------------------------------------------------------------

class ContractBuilder1<Self, A, R> internal constructor(private val core: ContractCore) {
    fun whenPrecondition(label: String, predicate: Pre1<Self, A>): PreconditionCase1<Self, A, R> =
            PreconditionCase1(core.open(label))
}

class PreconditionCase1<Self, A, R> internal constructor(private val draft: CaseDraft) {
    fun thenPostCondition(label: String, predicate: Post1<Self, A, R>): PreconditionCase1<Self, A, R> {
        draft.postLabels.add(label); return this
    }

    inline fun <reified E : Throwable> thenThrows(label: String): PreconditionCase1<Self, A, R> {
        recordThrows(E::class.java, label); return this
    }

    @PublishedApi
    internal fun recordThrows(type: Class<out Throwable>, label: String) = draft.setThrows(type, label)

    fun updatesOnly(frame: Frame1<Self, A>): PreconditionCase1<Self, A, R> {
        draft.hasExplicitFrame = true; return this
    }
}

// --- arity 2 ----------------------------------------------------------------------------------------

class ContractBuilder2<Self, A, B, R> internal constructor(private val core: ContractCore) {
    fun whenPrecondition(label: String, predicate: Pre2<Self, A, B>): PreconditionCase2<Self, A, B, R> =
            PreconditionCase2(core.open(label))
}

class PreconditionCase2<Self, A, B, R> internal constructor(private val draft: CaseDraft) {
    fun thenPostCondition(label: String,
                          predicate: Post2<Self, A, B, R>): PreconditionCase2<Self, A, B, R> {
        draft.postLabels.add(label); return this
    }

    inline fun <reified E : Throwable> thenThrows(label: String): PreconditionCase2<Self, A, B, R> {
        recordThrows(E::class.java, label); return this
    }

    @PublishedApi
    internal fun recordThrows(type: Class<out Throwable>, label: String) = draft.setThrows(type, label)

    fun updatesOnly(frame: Frame2<Self, A, B>): PreconditionCase2<Self, A, B, R> {
        draft.hasExplicitFrame = true; return this
    }
}
