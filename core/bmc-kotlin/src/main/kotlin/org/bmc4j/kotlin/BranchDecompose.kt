package org.bmc4j.kotlin

import org.bmc4j.Bmc

/**
 * Kotlin sugar for the branch-decomposition marker (see [org.bmc4j.BmcBranchDecompose] for the full
 * semantics). Mark a cold branch of a `@BmcBranchDecompose`-annotated proof; bmc4j extracts it into a
 * separately-proven leaf (under `assume(condition)`) and discharges its trivial summary back into the
 * parent (under `assume(!condition)`).
 *
 * ```
 * @BmcProof
 * @BmcBranchDecompose
 * fun proof() {
 *     val x = Bmc.anyInt()
 *     coldBranch(x == Int.MIN_VALUE)   // the cold branch
 *     Bmc.check(property(x))
 * }
 * ```
 *
 * `inline`, so the call lands directly in the proof method's bytecode where the rewriter sees it, just
 * like `Bmc.check` / `Bmc.slice` - the boolean is analysed, never executed. At most one `coldBranch`
 * per proof in this increment.
 */
inline fun coldBranch(condition: Boolean) {
    Bmc.coldBranch(condition)
}
