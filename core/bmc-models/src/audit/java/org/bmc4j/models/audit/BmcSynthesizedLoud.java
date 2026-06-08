package org.bmc4j.models.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Build-internal marker stamped on a method whose body was SYNTHESIZED by the loud-body pass (a
 * class-level {@code @BmcUnmodelable(member=)} / {@code @BmcModelTail} member the model does not
 * really implement, given an {@code AssertionError}-throwing body so reaching it fails loudly under
 * JBMC). It is NOT a genuine model implementation: the auditing gate and the docs generator exclude
 * marked methods from the "modeled / conforming" surface so a tailed/declared member is reported as
 * such, not as modeled. You never write this by hand — the build adds it.
 *
 * <p>Retention is {@link RetentionPolicy#CLASS}; never needed at runtime.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
public @interface BmcSynthesizedLoud {
}
