package org.bmc4j.constraints

import javax.lang.model.element.Element

/**
 * Extracts [Constraint]s from the annotations on a field (or accessor) of an
 * object model. One implementation per source library — the Jakarta Bean
 * Validation implementation is `bmc-constraints-jakarta`; others (custom
 * annotation sets, JSR-305, …) can plug in by implementing this.
 */
interface ConstraintExtractor {

    /**
     * Constraints implied by the validation annotations on [element]; empty if none.
     *
     * Covers the plain boolean field constraints (`@Min`, `@Size`, …). Richer shapes — the temporal
     * shared-`now`, the `@Valid` cascade, container-element loops — are surfaced by [extractAll],
     * which defaults to wrapping this. Implementations supporting those override [extractAll].
     */
    fun extract(element: Element): List<Constraint>

    /**
     * The full set of constraints for [element]: boolean [ExtractedConstraints.constraints],
     * [ExtractedConstraints.statements] (cascade / container loops), and any
     * [ExtractedConstraints.nowParams] the temporal constraints reference. Defaults to the
     * boolean-only [extract] for extractors that don't implement the richer shapes.
     */
    fun extractAll(element: Element): ExtractedConstraints =
            ExtractedConstraints(constraints = extract(element))
}
