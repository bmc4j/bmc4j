package org.bmc4j;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The container annotation Java/Kotlin synthesize when a proof method carries more than one
 * {@link LoopInvariant}. You never write this directly -- repeat {@link LoopInvariant} instead.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface LoopInvariants {

    LoopInvariant[] value();
}
