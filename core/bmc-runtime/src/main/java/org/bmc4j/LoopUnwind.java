package org.bmc4j;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * PIN one named loop to a FIXED unwind bound for a single {@link BmcProof} method, overriding whatever
 * mode the proof otherwise uses for that loop.
 *
 * <p>The {@link #loop()} id is the engine's exact {@code --unwindset}-form loop id
 * ({@code pkg.Cls.method:(sig)ret.N}, e.g. {@code "java::okio.Buffer.readDecimalLong:()J.1"}). You do
 * NOT have to guess it: run the proof under {@link BmcProfile} and bmc4j prints, for every loop it
 * observed, the full targetable id plus a ready-to-paste {@code @LoopUnwind(loop = "...", bound = N)}
 * line — copy the one you want.
 *
 * <pre>{@code
 * @BmcProof
 * @LoopUnwind(loop = "java::okio.Buffer.readDecimalLong:()J.1", bound = 19)
 * void decimal_parse_holds() { ... }
 * }</pre>
 *
 * <p>It is {@link Repeatable}: stack one per loop you want to pin.
 *
 * <pre>{@code
 * @BmcProof
 * @LoopUnwind(loop = "java::pkg.Cls.scan:()V.0", bound = 8)
 * @LoopUnwind(loop = "java::pkg.Cls.scan:()V.1", bound = 4)
 * void scan_holds() { ... }
 * }</pre>
 *
 * <h2>Interaction with the other unwind controls</h2>
 * <ul>
 *   <li>A {@code @LoopUnwind}-pinned loop is treated as FIXED. The automatic {@code AUTO} /
 *       smart-unwind climb (which can only raise a firing loop's bound) will NOT raise a pinned loop —
 *       the bound you wrote is the bound that loop runs at.</li>
 *   <li>Loops you do NOT name keep whatever mode the proof already uses: an explicit
 *       {@code @BmcProof(unwind = N)} pins them all to N, {@code AUTO} discovers their bounds, smart
 *       unwinding bumps just the under-bounded ones. {@code @LoopUnwind} overrides ONLY the per-loop
 *       bound for the loops it names; the global bound and the {@code unwindMax} climb cap still govern
 *       every unnamed loop.</li>
 * </ul>
 *
 * <h2>Soundness: a completeness/cost knob, NEVER a soundness knob</h2>
 * The global {@code --unwinding-assertions} stays ON regardless of any pin. So pinning a loop TOO LOW
 * does not produce a false VERIFIED — it correctly yields UNKNOWN (the unwinding assertion for that
 * loop fires: "bound too small"). Raising a per-loop bound only ever adds covered iterations; it can
 * never turn a real verdict into a wrong one. Pinning is purely a completeness/cost lever (cap a loop
 * you know the trip count of, so its bound does not inflate the formula on every other loop), exactly
 * like the {@code @BmcProof(unwind = ...)} / {@code unwindMax} controls.
 *
 * <p>The set of pinned loops is part of the verdict-cache key (it rides {@code BmcRequest.unwindSet}),
 * so adding, removing, or changing a pin forces a fresh engine run; a proof with no {@code @LoopUnwind}
 * keys identically to one without the annotation.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(LoopUnwinds.class)
public @interface LoopUnwind {

    /**
     * The engine {@code --unwindset}-form loop id to pin, e.g.
     * {@code "java::okio.Buffer.readDecimalLong:()J.1"}. Run the proof under {@link BmcProfile} to have
     * bmc4j print the exact id for every loop it observed.
     */
    String loop();

    /** The fixed unwind bound this loop runs at (a positive number of iterations). */
    int bound();
}
