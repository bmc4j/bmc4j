package org.bmc4j;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Pass raw extra arguments straight to the JBMC command line for one {@link BmcProof} method.
 *
 * <p>The {@link #value()} string is tokenized on whitespace and appended verbatim to the jbmc
 * invocation for this proof. It is a DELIBERATELY UNGUARDED escape hatch: the tokens are passed
 * through with no validation, no soundness checks, and no warnings. If a custom option causes a
 * proof to pass, the user owns that result.
 *
 * <pre>{@code
 * @BmcProof
 * @JbmcOptions("--object-bits 12")
 * void widget_invariant_holds() { ... }
 * }</pre>
 *
 * <p>The options string is part of the verdict-cache key, so setting or changing it forces a fresh
 * engine run. A proof with no {@code @JbmcOptions} keys identically to one without the annotation.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface JbmcOptions {

    /** Raw arguments passed to jbmc for this proof, tokenized on whitespace. */
    String value();
}
