package org.bmc4j.constraints

/**
 * Everything an extractor derives from one field's validation annotations: the boolean
 * [constraints] (each emitted as a `Bmc.assume(expr)`), any [statements] (the `@Valid` cascade and
 * container-element loops), and the shared temporal [nowParams] the field's temporal constraints
 * reference (so the processor can hoist one symbolic "now" per type to the class level).
 *
 * Plain field constraints (`@Min`, `@Size`, …) need only [constraints]; the richer fields stay empty.
 */
class ExtractedConstraints(
        @JvmField val constraints: List<Constraint> = emptyList(),
        @JvmField val statements: List<StatementConstraint> = emptyList(),
        @JvmField val nowParams: List<ConstraintCodeGenerator.NowParam> = emptyList()) {

    fun isEmpty(): Boolean = constraints.isEmpty() && statements.isEmpty()

    companion object {
        @JvmField
        val EMPTY = ExtractedConstraints()
    }
}
