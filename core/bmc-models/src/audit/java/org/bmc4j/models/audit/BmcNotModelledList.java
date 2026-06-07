package org.bmc4j.models.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Container for repeated {@link BmcNotModelled} declarations on a model class. Generated/used
 * implicitly by the {@code @Repeatable} mechanism; you never write this directly.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface BmcNotModelledList {
    BmcNotModelled[] value();
}
