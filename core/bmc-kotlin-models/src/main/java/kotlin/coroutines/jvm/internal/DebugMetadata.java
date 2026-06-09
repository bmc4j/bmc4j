package kotlin.coroutines.jvm.internal;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Clean model of the synthetic {@code @kotlin.coroutines.jvm.internal.DebugMetadata} annotation the
 * Kotlin compiler stamps onto every generated coroutine state machine's {@code invokeSuspend}. The real
 * annotation only carries source-position / spilled-variable metadata read reflectively by the coroutine
 * debugger; it has no effect on the suspend logic.
 *
 * <p>Bundled — rather than left to resolve against the real kotlin-stdlib jar — so that EVERY
 * {@code kotlin.coroutines.jvm.internal.*} type a generated continuation references (its supertype
 * {@code ContinuationImpl} AND this method annotation) lives in the SAME (bundled) classpath source.
 * When this annotation type resolves against the stdlib jar instead, the continuation class straddles two
 * classpath sources, and JBMC must lazily link the bundled continuation's subtype-&gt;supertype cast edge
 * ({@code checkcast Continuation}, re-resolved on each loop unwind of a state machine) across sources —
 * the order-dependent link the older-Kotlin legs intermittently drop, havoc'ing the cast (a spurious
 * "Dynamic cast check" REFUTED, e.g. the loop-bodied {@code countTo} on the kotlin-2.3.21 leg). The
 * member set mirrors the real annotation exactly so the stamped values bind here.
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface DebugMetadata {

    String c() default "";

    String f() default "";

    int[] i() default {};

    int[] l() default {};

    String m() default "";

    String[] n() default {};

    int[] nl() default {};

    String[] s() default {};

    int[] v() default {};
}
