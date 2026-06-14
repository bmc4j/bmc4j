package org.bmc4j.contracts

/**
 * The process-global registry the contracts DSL self-registers into. Each top-level
 * `val name = contractFor(...) { ... }` runs its body at build time and registers one
 * [ContractDefinition]; the bmc4j Gradle plugin executes the contracts source set's facade `<clinit>`s
 * (forcing those `val` initializers to run), then [drain]s the registry and lowers each definition onto
 * the enforce/summarize backend.
 *
 * The structural facts captured here (case order, labels, frame presence, expected exception types,
 * expect-verdict, level) are zipped 1:1 - in registration order - with the predicate implementation
 * handles read by a narrow static decode of the same `contractFor(...)` site. Execution order is the
 * source order, so the two channels line up.
 */
object ContractRegistry {

    private val definitions = ArrayList<ContractDefinition>()

    @JvmStatic
    @Synchronized
    fun register(definition: ContractDefinition) {
        definitions.add(definition)
    }

    /** Snapshot of every registered definition, in registration (source) order. */
    @JvmStatic
    @Synchronized
    fun snapshot(): List<ContractDefinition> = ArrayList(definitions)

    /** Clear the registry (used by the build pass between contracts facades, and by tests). */
    @JvmStatic
    @Synchronized
    fun clear() {
        definitions.clear()
    }
}

/** One finalized DSL contract: the contracted member's arity, the enforcement level + expect-verdict,
 *  and the ordered guarded cases. The contracted method and predicate bodies are resolved separately
 *  from the contracts facade bytecode (the impl handles), zipped against this in registration order. */
class ContractDefinition internal constructor(
        /** Number of value arguments of the contracted method (excludes the threaded receiver). */
        @JvmField val arity: Int,
        @JvmField val level: EnforcementLevel,
        @JvmField val expect: ExpectEnforce,
        @JvmField val cases: List<Case>,
)

/** A single `when -> then` case: its precondition label, the (zero or more) postcondition labels, an
 *  optional must-throw exception (`thenThrows<E>`), and whether an explicit `updatesOnly` frame was
 *  declared. The structural counterpart of the predicate handles decoded from bytecode. */
class Case internal constructor(
        @JvmField val preconditionLabel: String,
        @JvmField val postconditionLabels: List<String>,
        /** Internal name of the must-throw exception type, or null for a normal-return case. */
        @JvmField val throwsType: String?,
        @JvmField val throwsLabel: String?,
        @JvmField val hasExplicitFrame: Boolean,
)

/** Mutable accumulator for a case while its `whenPrecondition(...)...` chain is still building. Public so
 *  the inline `thenThrows<E>` (in the same package) can record the reified exception type. */
class CaseDraft internal constructor(@JvmField val preconditionLabel: String) {
    @JvmField val postLabels = ArrayList<String>()
    @JvmField var throwsType: String? = null
    @JvmField var throwsLabel: String? = null
    @JvmField var hasExplicitFrame = false

    fun setThrows(type: Class<out Throwable>, label: String) {
        throwsType = type.name.replace('.', '/')
        throwsLabel = label
    }

    internal fun toCase(): Case = Case(preconditionLabel, postLabels.toList(), throwsType, throwsLabel,
            hasExplicitFrame)
}
