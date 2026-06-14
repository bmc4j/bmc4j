package org.bmc4j;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Opt a proof into AUTOMATIC, SOUND BRANCH DECOMPOSITION: bmc4j discovers a branch in the proof method
 * by bytecode CFG analysis (you mark NOTHING), EXTRACTS it into a separately-proven synthetic method,
 * proves that method against an automatically-derived SUMMARY, and discharges the summary back into the
 * parent proof at the call site - instead of exploring the whole proof as one monolithic formula.
 *
 * <p>This is a pure FEATURE FLAG (it carries no fields). Add it next to {@link BmcProof} on a proof
 * whose body computes a value through an {@code if/else} (or {@code when}/ternary) expression:
 *
 * <pre>{@code
 * @BmcProof
 * @BmcBranchDecompose
 * void clamp_stays_in_range() {
 *     int x = Bmc.anyInt();
 *     int r = (x < -10) ? -10 : (x > 10) ? 10 : x;   // bmc4j discovers and extracts this branch
 *     Bmc.check(r >= -10 && r <= 10);
 * }
 * }</pre>
 *
 * <p><b>What it does.</b> bmc4j finds the first top-level value branch in the method and turns the ONE
 * proof into two independent derived runs it fans across cores (the same fan-out as {@code domainSplit}):
 * <ul>
 *   <li>a LEAF run that proves the EXTRACTED branch satisfies an automatically-derived SUMMARY of its
 *       input/output relation - the branch verified on its own; and
 *   <li>a PARENT run that re-verifies the proof with the inline branch REPLACED by a call to a
 *       summarize stub ({@code r = nondet(); assume(summary); ...}), so the parent never re-explores the
 *       branch's internal control flow - it sees only the flat relation predicate.
 * </ul>
 * The proof PASSES iff BOTH the leaf and the parent VERIFIED; a refutation in either surfaces its
 * counterexample (a bug inside the branch fails the leaf, a bug in the remainder fails the parent).
 *
 * <p><b>Why it is SOUND and as PRECISE as inlining.</b> The summary is the branch's EXACT input/output
 * relation (under each arm guard {@code Ci}, the result equals that arm's value {@code ei}), not a lossy
 * abstraction. The leaf proves the real branch satisfies it - so the post the parent ASSUMES is
 * GUARANTEED, never an unsound narrowing - and the parent assumes exactly that relation, so it loses no
 * information the inlined arms carried. Leaf and parent are an assume-guarantee decomposition: real
 * compositional verification, the sound successor to dead-branch pruning.
 *
 * <p><b>Why it is valuable.</b> Decomposing RESTRUCTURES the parent's formula: a branch with complex
 * internal control flow (nested conditionals, a loop) becomes a single flat relation predicate the
 * solver can simplify independently of the rest of the proof, and the leaf proves that branch
 * concurrently on the shared jbmc pool. A decomposed, structurally-simpler formula can collapse far
 * faster in the solver even at equal nominal size.
 *
 * <p><b>Scope (increment 1).</b> The FIRST top-level value branch of the proof method is discovered and
 * extracted one level deep; the arms must be side-effect free (the proof idiom {@code val r = if (...)
 * ... else ...}). A proof whose method has no such branch is NOT decomposed and runs as an ordinary
 * proof. The branch markers are analysed as bytecode, never executed.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface BmcBranchDecompose {
}
