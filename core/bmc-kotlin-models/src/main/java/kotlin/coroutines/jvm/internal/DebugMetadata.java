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

    // The metadata format version — a scalar `int` (default 1), NOT an array. The real
    // kotlin.coroutines.jvm.internal.DebugMetadata.v returns `int`, and kotlinc stamps every
    // invokeSuspend with a scalar `@DebugMetadata(... v=2)`. Declaring it `int[]` here (as the
    // first bundling did) makes the stamped scalar value type-incompatible with the bundled
    // annotation, so the annotation type cannot bind to this bundled declaration and falls back
    // to resolving against the real kotlin-stdlib jar — re-straddling the continuation across two
    // classpath sources, which is exactly the cross-source split the bundling exists to remove.
    // The drop of the bundled continuation's subtype->supertype `checkcast Continuation` link is
    // order/depth-dependent, so a single-suspension state machine can survive it while a
    // multi-suspension one (e.g. computeTwice's 0/1/2 tableswitch) trips it and havocs the cast
    // (a spurious "Dynamic cast check" REFUTED). Matching the real scalar `int` makes the bundled
    // annotation bind single-source for every continuation shape.
    int v() default 1;
}
