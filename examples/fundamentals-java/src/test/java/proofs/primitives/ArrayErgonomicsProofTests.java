package proofs.primitives;

import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;
import org.bmc4j.Verdict;

/**
 * Proofs for the symbolic-array ergonomics helpers
 * ({@code anyArrayOfInts}/{@code anyArrayOfLongs}, {@code assumeSorted}/{@code assumeStrictlySorted}).
 *
 * <p>These compose existing {@code anyInt}/{@code assume} primitives, so they introduce no new
 * soundness surface — the proofs below just confirm the composition does something real: the ranged
 * constructor bounds every element, sortedness orders the array, and (the load-bearing one) WITHOUT
 * {@code assumeSorted} the ordering claim is refutable. {@code unwind} covers the fill loop plus the
 * pairwise sortedness loop for the array length used.
 */
class ArrayErgonomicsProofTests {

    /** PASSES: a sorted, range-bounded array has a[0] <= a[last] for every allowed array. */
    @BmcProof(unwind = 8)
    void sorted_array_first_le_last() {
        int[] a = Bmc.anyArrayOfInts(4, -3, 3);
        Bmc.assumeSorted(a);
        Bmc.check(a[0] <= a[3]);
    }

    /** PASSES: the ranged constructor bounds every element to [lo, hi]. */
    @BmcProof(unwind = 8)
    void ranged_constructor_respects_bounds() {
        int[] a = Bmc.anyArrayOfInts(4, -3, 3);
        for (int i = 0; i < a.length; i++) {
            Bmc.check(a[i] >= -3 && a[i] <= 3);
        }
    }

    /** PASSES: strict sorting forces adjacent elements distinct. */
    @BmcProof(unwind = 8)
    void strictly_sorted_array_has_distinct_neighbours() {
        int[] a = Bmc.anyArrayOfInts(4, -3, 3);
        Bmc.assumeStrictlySorted(a);
        for (int i = 1; i < a.length; i++) {
            Bmc.check(a[i - 1] != a[i]);
        }
    }

    /**
     * FAILS on purpose: WITHOUT {@code assumeSorted}, "a[0] <= a[last]" is refutable — JBMC finds an
     * out-of-order array. This is the control that proves {@code assumeSorted} genuinely narrows the
     * domain (compare with {@link #sorted_array_first_le_last()}).
     */
    // Expected verdict: REFUTED - an unsorted array can have a[0] > a[last].
    @BmcProof(expect = Verdict.REFUTED, unwind = 8)
    void unsorted_array_first_not_always_le_last() {
        int[] a = Bmc.anyArrayOfInts(4, -3, 3);
        Bmc.check(a[0] <= a[3]);
    }

    /** PASSES: long arrays sort the same way. */
    @BmcProof(unwind = 8)
    void sorted_long_array_first_le_last() {
        long[] a = Bmc.anyArrayOfLongs(4, -3L, 3L);
        Bmc.assumeSorted(a);
        Bmc.check(a[0] <= a[3]);
    }
}
