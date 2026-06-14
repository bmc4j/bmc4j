package org.bmc4j;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Opt a proof into SOUND BRANCH DECOMPOSITION: extract one or more of the proof's branches into
 * separately-proven leaves and discharge each branch back into the parent as a call-site summary,
 * instead of exploring the whole proof as one monolithic formula.
 *
 * <p>This is a pure FEATURE FLAG (it carries no fields) - it is a separate annotation rather than
 * another {@link BmcProof} field because {@link BmcProof} already has many knobs. Add it next to
 * {@link BmcProof} on a proof whose body marks a cold branch with {@link Bmc#coldBranch(boolean)}:
 *
 * <pre>{@code
 * @BmcProof
 * @BmcBranchDecompose
 * void hot_path_localized() {
 *     int x = Bmc.anyInt();
 *     Bmc.coldBranch(x == Integer.MIN_VALUE);   // the cold, expensive branch
 *     // ... the rest of the proof ...
 * }
 * }</pre>
 *
 * <p><b>What it does (the cold-branch case).</b> bmc4j expands the ONE proof into independent derived
 * runs it fans across cores (the same fan-out as {@code domainSplit}):
 * <ul>
 *   <li>a LEAF run that re-verifies the proof under {@code assume(branchCondition)} - the branch
 *       extracted into its own obligation, carrying the branch path-condition as its PRECONDITION; and
 *   <li>a PARENT run that re-verifies the proof under {@code assume(!branchCondition)} - the branch's
 *       proven, trivial summary discharged at the call site, so the parent never re-explores it.
 * </ul>
 * Together the leaf and parent cover {@code cond || !cond} - the full domain - so this is a SOUND
 * case-split discharge, NOT dead-branch pruning: the branch is actually proven, not deleted. The proof
 * passes iff both the leaf and the parent VERIFIED; a refutation in either surfaces its counterexample.
 *
 * <p><b>Why it is valuable (it is not about avoiding re-solving).</b> jbmc already solves
 * incrementally when the external SAT solver is off, so decomposition is NOT primarily about avoiding
 * re-solving the same formula. Its value is three-fold:
 * <ul>
 *   <li><b>Parallelisation</b> - the leaf and the parent are independent obligations that prove
 *       concurrently on the shared jbmc pool; wall-clock is the critical path, not the sum.
 *   <li><b>Hot-path identification</b> - cost LOCALISES to the extracted branch, so the breakdown
 *       names the branch as the proof's hot spot ("cost-follows-extraction = localized").
 *   <li><b>Solver-agnostic</b> - it works the same way with an external SAT solver (kissat), where
 *       jbmc's own incrementality does not apply.
 * </ul>
 *
 * <p>Like {@code domainSplit}, the markers are analysed as bytecode, never executed. At most one
 * {@code coldBranch(...)} per proof in this increment; the localised-cost breakdown is printed
 * alongside the verdict.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface BmcBranchDecompose {
}
