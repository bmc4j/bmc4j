package org.bmc4j;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Opt ONE proof out of specific user models, so that proof is analysed against the REAL class
 * instead of the {@code src/bmcModel} substitution.
 *
 * <p>User models are GLOBAL: every proof's analysis classpath is overlaid with the compiled
 * {@code bmcModel} output, which shadows the real classes by fully-qualified name. This annotation
 * removes the named models' {@code .class} entries from that overlay for the proof it is attached
 * to, so JBMC links the real implementation for those classes while every other model still
 * applies.
 *
 * <pre>{@code
 * @BmcProof
 * @ExcludeModels({okio.Buffer.class, okio.Segment.class})
 * void buffer_invariant_holds_against_real_okio() { ... }
 * }</pre>
 *
 * <p>May be placed on a {@link BmcProof} method or on its declaring class. A method-level
 * annotation is MERGED with the class-level one (the union of both exclusion sets applies to that
 * method), so a class-wide exclusion plus a per-method addition both take effect.
 *
 * <p>SOUNDNESS: excluding a model means using the real class, which is strictly MORE faithful than
 * any model the user could write. So an exclusion can only ever make a proof harder, slower, or
 * UNKNOWN, never turn a real refutation into a false VERIFIED. No special soundness handling is
 * needed; it is safe by construction.
 *
 * <p>The set of excluded models is part of the verdict-cache key (the per-proof analysis classpath
 * varies by it), so adding, removing, or changing an exclusion forces a fresh engine run; a proof
 * with no {@code @ExcludeModels} keys identically to one without the annotation.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ExcludeModels {

    /** The classes whose user models this proof opts OUT of, analysing the real class instead. */
    Class<?>[] value();
}
