package org.bmc4j;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Attach an inductive invariant to a REAL loop in the code under proof so BMC SUMMARIZES it (a
 * one-iteration step check) instead of UNROLLING it N times. This is the annotation form of the
 * {@code Bmc.loop*} marker DSL: the marker DSL makes you re-author the loop body straight-line in the
 * proof; this annotation contracts the loop AS WRITTEN, recovering its header/guard/body/back-edge from
 * the bytecode and applying the same sound base/step/summary transform in place.
 *
 * <pre>{@code
 * @BmcProof(unwind = 1)
 * @LoopInvariant(loop = "java::proofs.MyProof.sumLoop:(I)I.0", predicate = "sumInvariant")
 * void sum_holds() {
 *     int r = sumLoop(n);          // the loop lives here; the annotation contracts it
 *     Bmc.check(r == n * (n - 1) / 2);
 * }
 *
 * // The predicate is a static boolean method whose PARAMETER NAMES match the loop's local names.
 * static boolean sumInvariant(int i, int s, int n) { return i >= 0 && i <= n && s == i * (i - 1) / 2; }
 * }</pre>
 *
 * <h2>The {@link #loop()} id</h2>
 * The exact {@code --unwindset}-form id ({@code java::pkg.Cls.method:(sig)ret.N}) -- the SAME form
 * {@link LoopUnwind} takes. Run the proof under {@link BmcProfile} to have bmc4j print the targetable id
 * for every loop it observed, then paste the one you want to contract.
 *
 * <h2>The {@link #predicate()} reference</h2>
 * The name of a {@code static boolean} method whose PARAMETER NAMES bind, by name, to the loop's locals
 * (recovered from the method's LocalVariableTable). The predicate may take a subset of the locals (the
 * ones the invariant references) plus any in-scope final the body does not write. It must be compiled
 * with parameter names retained ({@code -parameters}); bmc4j's example modules already are.
 *
 * <h2>Soundness</h2>
 * Identical to the marker DSL: the invariant is a CHECKED hint. The base case and the step preservation
 * are ASSERTED (engine-proven); only the summary (havoc the auto-computed assigns set + assume the
 * invariant + assume the negated guard) uses it. A WRONG invariant fails the base or the step assert and
 * REFUTES, never a false VERIFIED. The assigns set (the soundness-critical frame) is auto-computed from
 * the loop body's stores; a heap (field/array) frame is refused loud (UNKNOWN), never silently havoc'd.
 *
 * <p>SPIKE feature; {@link Repeatable} so a method with several contracted loops stacks one per loop.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(LoopInvariants.class)
public @interface LoopInvariant {

    /**
     * The engine {@code --unwindset}-form loop id to contract, e.g.
     * {@code "java::proofs.MyProof.sumLoop:(I)I.0"}. Run under {@link BmcProfile} to print every observed
     * loop's id.
     */
    String loop();

    /**
     * The name of the {@code static boolean} invariant predicate in the proof class. Its parameter names
     * bind by name to the contracted loop's local variables (via the LocalVariableTable).
     */
    String predicate();
}
