package proofs.primitives

import org.bmc4j.Bmc
import org.bmc4j.BmcProof
import org.bmc4j.Verdict
import org.bmc4j.kotlin.anyIntArray
import org.bmc4j.kotlin.assumeSorted
import org.bmc4j.kotlin.assumeStrictlySorted

/**
 * Kotlin-idiomatic proofs for the symbolic-array ergonomics: `anyIntArray(length, range)` plus the
 * `IntArray.assumeSorted()` / `assumeStrictlySorted()` extensions, which delegate straight to [Bmc].
 */
class ArrayErgonomicsProofs {

    /** PASSES: a sorted, range-bounded array has a[0] <= a[last]. */
    @BmcProof(unwind = 8)
    fun `sorted array first le last`() {
        val a = anyIntArray(4, -3..3)
        a.assumeSorted()
        Bmc.check(a[0] <= a[3])
    }

    /** PASSES: strict sorting forces adjacent elements distinct. */
    @BmcProof(unwind = 8)
    fun `strictly sorted array has distinct neighbours`() {
        val a = anyIntArray(4, -3..3)
        a.assumeStrictlySorted()
        for (i in 1 until a.size) {
            Bmc.check(a[i - 1] != a[i])
        }
    }

    /** FAILS on purpose: without assumeSorted, a[0] <= a[last] is refutable. */
    // Expected verdict: REFUTED - an unsorted array can have a[0] > a[last].
    @BmcProof(expect = Verdict.REFUTED, unwind = 8)
    fun `unsorted array first not always le last`() {
        val a = anyIntArray(4, -3..3)
        Bmc.check(a[0] <= a[3])
    }
}
