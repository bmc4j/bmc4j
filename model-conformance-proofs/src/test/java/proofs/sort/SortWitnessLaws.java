package proofs.sort;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;
import org.bmc4j.Verdict;

/**
 * Model proofs (axis 2) for the comparator-driven {@code sort}/{@code sorted} surface that bmc4j now
 * models via the nondet sorted-permutation witness ({@code java.util.BmcSortWitness}):
 * {@link java.util.stream.Stream#sorted(Comparator)}, {@link ArrayList#sort(Comparator)},
 * {@link LinkedList#sort(Comparator)}, {@link Collections#sort(List, Comparator)} and the
 * {@link Arrays}{@code .sort(T[], Comparator)} overloads (full + ranged).
 *
 * <p>The witness havocs an output of the same length and {@code assume}s it is (a) a bijective
 * permutation of the input and (b) non-decreasing under the comparator. These laws pin BOTH halves:
 * <ul>
 *   <li><b>ORDERED</b> — the result is non-decreasing under the comparator (natural via {@code ASC},
 *       and a reverse lambda comparator), for both concrete and symbolic inputs.</li>
 *   <li><b>PERMUTATION</b> — the multiset is preserved: a known element's presence/sum survives, and
 *       the size is unchanged.</li>
 *   <li><b>NEGATIVE</b> — {@link #sorted_is_not_identity_when_unsorted()} declares {@code expect =
 *       REFUTED}: claiming the result equals the (unsorted) input is false, so the witness is doing
 *       real ordering work, not returning the input untouched.</li>
 * </ul>
 *
 * <p>Comparators are explicit DESUGARED lambdas over the unboxed int (NOT {@code
 * Comparator.naturalOrder()}, whose boxed {@code Comparable.compareTo} dispatch is unsound under
 * JBMC). Inputs are kept tiny (n &le; 4) so each proof is decidable well under budget.
 */
class SortWitnessLaws {

    // Desugared SAM comparators — JBMC devirtualizes compare(). Natural ascending and reverse.
    private static final Comparator<Integer> ASC = (a, b) -> a.intValue() - b.intValue();
    private static final Comparator<Integer> DESC = (a, b) -> b.intValue() - a.intValue();

    // ---- Stream.sorted(Comparator) -----------------------------------------------------------------

    @BmcProof(unwind = 4)
    void stream_sorted_orders_ascending() {
        List<Integer> xs = List.of(3, 1, 2);
        List<Integer> out = xs.stream().sorted(ASC).toList();
        Bmc.check(out.size() == 3 && out.get(0) == 1 && out.get(1) == 2 && out.get(2) == 3);
    }

    @BmcProof(unwind = 4)
    void stream_sorted_reverse_comparator() {
        List<Integer> xs = List.of(1, 3, 2);
        List<Integer> out = xs.stream().sorted(DESC).toList();
        Bmc.check(out.size() == 3 && out.get(0) == 3 && out.get(1) == 2 && out.get(2) == 1);
    }

    /** ORDERED + PERMUTATION over symbolic distinct inputs: {b,a} with a<b sorts to {a,b}. */
    @BmcProof(unwind = 4)
    void stream_sorted_symbolic_two() {
        int a = Bmc.anyInt(0, 100);
        int b = Bmc.anyInt(101, 200); // strictly greater than a
        List<Integer> out = List.of(b, a).stream().sorted(ASC).toList();
        Bmc.check(out.size() == 2 && out.get(0) == a && out.get(1) == b);
    }

    // ---- ArrayList.sort(Comparator) ----------------------------------------------------------------

    @BmcProof(unwind = 4)
    void arraylist_sort_orders_ascending() {
        ArrayList<Integer> m = new ArrayList<>();
        m.add(3);
        m.add(1);
        m.add(2);
        m.sort(ASC);
        Bmc.check(m.get(0) == 1 && m.get(1) == 2 && m.get(2) == 3);
    }

    /** PERMUTATION: the sorted multiset still sums the same and is the same size. */
    @BmcProof(unwind = 4)
    void arraylist_sort_preserves_multiset() {
        int a = Bmc.anyInt(0, 50);
        int b = Bmc.anyInt(0, 50);
        int c = Bmc.anyInt(0, 50);
        ArrayList<Integer> m = new ArrayList<>();
        m.add(a);
        m.add(b);
        m.add(c);
        m.sort(ASC);
        Bmc.check(m.size() == 3);
        Bmc.check(m.get(0) <= m.get(1) && m.get(1) <= m.get(2)); // ordered
        Bmc.check(m.get(0) + m.get(1) + m.get(2) == a + b + c);  // sum preserved => no invented value
    }

    // ---- LinkedList.sort(Comparator) ---------------------------------------------------------------

    @BmcProof(unwind = 4)
    void linkedlist_sort_orders_ascending() {
        LinkedList<Integer> m = new LinkedList<>();
        m.add(2);
        m.add(3);
        m.add(1);
        m.sort(ASC);
        Bmc.check(m.get(0) == 1 && m.get(1) == 2 && m.get(2) == 3);
    }

    // ---- Collections.sort(List, Comparator) --------------------------------------------------------

    @BmcProof(unwind = 8)
    void collections_sort_orders_ascending() {
        ArrayList<Integer> m = new ArrayList<>();
        m.add(4);
        m.add(2);
        m.add(3);
        m.add(1);
        Collections.sort(m, ASC);
        Bmc.check(m.get(0) == 1 && m.get(1) == 2 && m.get(2) == 3 && m.get(3) == 4);
    }

    @BmcProof(unwind = 4)
    void collections_sort_preserves_known_element() {
        ArrayList<Integer> m = new ArrayList<>();
        m.add(7);
        m.add(3);
        m.add(5);
        Collections.sort(m, ASC);
        // a known element still present (multiset preserved) and ordering holds
        Bmc.check(m.contains(7) && m.contains(3) && m.contains(5));
        Bmc.check(m.get(0) == 3 && m.get(1) == 5 && m.get(2) == 7);
    }

    // ---- Arrays.sort(T[], Comparator) (full + ranged) ----------------------------------------------

    @BmcProof(unwind = 4)
    void arrays_sort_orders_ascending() {
        Integer[] a = {3, 1, 2};
        Arrays.sort(a, ASC);
        Bmc.check(a[0] == 1 && a[1] == 2 && a[2] == 3);
    }

    @BmcProof(unwind = 4)
    void arrays_sort_symbolic_ordered_and_permutation() {
        int x = Bmc.anyInt(0, 50);
        int y = Bmc.anyInt(0, 50);
        Integer[] a = {x, y};
        Arrays.sort(a, ASC);
        Bmc.check(a[0] <= a[1]);               // ordered
        Bmc.check(a[0] + a[1] == x + y);       // sum preserved => permutation
    }

    /** Ranged sort: only [1,3) is ordered; the endpoints are left untouched. */
    @BmcProof(unwind = 4)
    void arrays_sort_ranged_leaves_ends_untouched() {
        Integer[] a = {9, 3, 1, 8};
        Arrays.sort(a, 1, 3, ASC); // sorts indices 1,2 only
        Bmc.check(a[0] == 9 && a[3] == 8);     // ends untouched
        Bmc.check(a[1] == 1 && a[2] == 3);     // middle ordered, permuted
    }

    // ---- NEGATIVE law (must REFUTE) ----------------------------------------------------------------

    /**
     * Asserting the sorted result equals the (unsorted) input must REFUTE — the witness genuinely
     * reorders. If this ever VERIFIES, the ordering constraint has been lost and the model would be
     * returning the input untouched.
     */
    @BmcProof(expect = Verdict.REFUTED)
    void sorted_is_not_identity_when_unsorted() {
        ArrayList<Integer> m = new ArrayList<>();
        m.add(2);
        m.add(1);
        m.sort(ASC);
        Bmc.check(m.get(0) == 2 && m.get(1) == 1); // false: sorted is [1,2], not the input [2,1]
    }
}
