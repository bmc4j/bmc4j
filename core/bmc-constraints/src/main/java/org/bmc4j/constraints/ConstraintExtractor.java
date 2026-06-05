package org.bmc4j.constraints;

import javax.lang.model.element.Element;
import java.util.List;

/**
 * Extracts {@link Constraint}s from the annotations on a field (or accessor) of an
 * object model. One implementation per source library — the Jakarta Bean
 * Validation implementation is {@code bmc-constraints-jakarta}; others (custom
 * annotation sets, JSR-305, …) can plug in by implementing this.
 */
public interface ConstraintExtractor {

    /** Constraints implied by the validation annotations on {@code element}; empty if none. */
    List<Constraint> extract(Element element);
}
