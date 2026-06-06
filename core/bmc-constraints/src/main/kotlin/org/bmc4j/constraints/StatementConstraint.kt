package org.bmc4j.constraints

/**
 * A constraint that emits one or more Java STATEMENTS into generated `assumeValid` code, rather
 * than a single boolean expression (which is what [Constraint] does). Needed for shapes that don't
 * reduce to one `Bmc.assume(expr)`:
 *
 *  - **`@Valid` cascade** — `if (x != null) { XConstraints.assumeValid(x); }` (a recursive call).
 *  - **container-element constraints** — a bounded loop over a collection's elements.
 *
 * Implementations return fully-formed statement text WITHOUT outer indentation; the generator
 * indents each emitted line uniformly.
 */
fun interface StatementConstraint {

    /**
     * @param accessor a Java expression that reads the value, e.g. `obj.address`
     * @return Java statement(s), newline-separated, with NO leading indentation per line
     */
    fun toStatements(accessor: String): String
}
