package org.bmc4j.constraints

import javax.lang.model.element.Element

/**
 * Extracts [Constraint]s from the annotations on a field (or accessor) of an
 * object model. One implementation per source library — the Jakarta Bean
 * Validation implementation is `bmc-constraints-jakarta`; others (custom
 * annotation sets, JSR-305, …) can plug in by implementing this.
 */
interface ConstraintExtractor {

    /** Constraints implied by the validation annotations on [element]; empty if none. */
    fun extract(element: Element): List<Constraint>
}
