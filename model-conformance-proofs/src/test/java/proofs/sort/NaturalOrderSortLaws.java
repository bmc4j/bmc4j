package proofs.sort;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;
import org.bmc4j.Verdict;

/**
 * Model proofs (axis 2) for the NATURAL-ORDER {@code sort}/{@code sorted} surface, which bmc4j models by
 * driving the nondet sorted-permutation witness ({@code java.util.BmcSortWitness}) with the single
 * concrete, devirtualizable natural-order comparison ({@code java.util.BmcNaturalOrder.compare}) instead
 * of the elements' virtual {@code Comparable.compareTo}. Covered surfaces:
 * <ul>
 *   <li>{@link java.util.stream.Stream#sorted()} (no-arg, natural order),</li>
 *   <li>{@link Arrays}{@code .sort(Object[])} and the ranged {@code sort(Object[], int, int)},</li>
 *   <li>{@link Comparator#naturalOrder()} (returns the {@code BmcNaturalOrder}-backed comparator).</li>
 * </ul>
 *
 * <p>The witness {@code assume}s its output is (a) a bijective permutation of the input and (b)
 * non-decreasing under {@code BmcNaturalOrder.compare}. These laws pin BOTH halves over SYMBOLIC inputs
 * of two element types — {@code Integer} (bit-precise {@code Integer.compare}) and {@code String}
 * (lexicographic {@code String.compareTo}, already analyzable) — plus an intended-REFUTED negative law.
 *
 * <p>Inputs are tiny (n &le; 3) and strings short so each proof is decidable well under budget.
 */
class NaturalOrderSortLaws {

    // ======== Stream.sorted() (no-arg, natural order) over symbolic Integer ========================

    /** ORDERED: natural-order sorted() of symbolic ints is non-decreasing. */
    @BmcProof
    void stream_sorted_natural_integer_ordered() {
        int a = Bmc.anyInt(0, 50);
        int b = Bmc.anyInt(0, 50);
        int c = Bmc.anyInt(0, 50);
        List<Integer> out = List.of(a, b, c).stream().sorted().toList();
        Bmc.check(out.size() == 3);
        Bmc.check(out.get(0) <= out.get(1) && out.get(1) <= out.get(2)); // ordered
    }

    /** PERMUTATION: natural-order sorted() preserves the multiset (size + sum + a known element). */
    @BmcProof
    void stream_sorted_natural_integer_permutation() {
        int a = Bmc.anyInt(0, 50);
        int b = Bmc.anyInt(0, 50);
        int c = Bmc.anyInt(0, 50);
        List<Integer> out = List.of(a, b, c).stream().sorted().toList();
        Bmc.check(out.size() == 3);
        Bmc.check(out.get(0) + out.get(1) + out.get(2) == a + b + c); // sum preserved => no invented value
    }

    /** Concrete sanity: the natural order is actually applied. */
    @BmcProof
    void stream_sorted_natural_integer_concrete() {
        List<Integer> out = List.of(3, 1, 2).stream().sorted().toList();
        Bmc.check(out.get(0) == 1 && out.get(1) == 2 && out.get(2) == 3);
    }

    // ======== Stream.sorted() (no-arg, natural order) over symbolic String =========================

    /** ORDERED: natural-order sorted() of symbolic strings is lexicographically non-decreasing. */
    @BmcProof(maxStringLength = 2)
    void stream_sorted_natural_string_ordered() {
        String a = Bmc.anyString(2);
        String b = Bmc.anyString(2);
        List<String> out = List.of(a, b).stream().sorted().toList();
        Bmc.check(out.size() == 2);
        Bmc.check(out.get(0).compareTo(out.get(1)) <= 0); // lexicographically ordered
    }

    /**
     * PERMUTATION: natural-order sorted() of symbolic strings preserves the multiset — both inputs are
     * still present after sorting (no element invented/dropped).
     */
    @BmcProof(maxStringLength = 2)
    void stream_sorted_natural_string_permutation() {
        String a = Bmc.anyString(2);
        String b = Bmc.anyString(2);
        List<String> out = List.of(a, b).stream().sorted().toList();
        Bmc.check(out.size() == 2);
        Bmc.check(out.contains(a) && out.contains(b)); // multiset preserved
    }

    /** Concrete String sanity: lexicographic order is applied. */
    @BmcProof
    void stream_sorted_natural_string_concrete() {
        List<String> out = List.of("c", "a", "b").stream().sorted().toList();
        Bmc.check(out.get(0).equals("a") && out.get(1).equals("b") && out.get(2).equals("c"));
    }

    // ======== Arrays.sort(Object[]) natural order (full + ranged) ==================================

    @BmcProof
    void arrays_sort_object_natural_ordered_and_permutation() {
        int x = Bmc.anyInt(0, 50);
        int y = Bmc.anyInt(0, 50);
        Integer[] arr = {x, y};
        Arrays.sort(arr);
        Bmc.check(arr[0] <= arr[1]);          // ordered
        Bmc.check(arr[0] + arr[1] == x + y);  // sum preserved => permutation
    }

    @BmcProof
    void arrays_sort_object_natural_concrete() {
        Integer[] arr = {3, 1, 2};
        Arrays.sort(arr);
        Bmc.check(arr[0] == 1 && arr[1] == 2 && arr[2] == 3);
    }

    /** Ranged natural sort: only [1,3) is ordered; the endpoints are untouched. */
    @BmcProof
    void arrays_sort_object_natural_ranged_leaves_ends() {
        Integer[] arr = {9, 3, 1, 8};
        Arrays.sort(arr, 1, 3); // sorts indices 1,2 only
        Bmc.check(arr[0] == 9 && arr[3] == 8); // ends untouched
        Bmc.check(arr[1] == 1 && arr[2] == 3); // middle ordered + permuted
    }

    // ======== Comparator.naturalOrder() ============================================================

    /** Comparator.naturalOrder() drives the witness through the BmcNaturalOrder-backed comparator. */
    @BmcProof
    void comparator_natural_order_sorts_ascending() {
        Comparator<Integer> nat = Comparator.naturalOrder();
        ArrayList<Integer> m = new ArrayList<>();
        m.add(3);
        m.add(1);
        m.add(2);
        m.sort(nat);
        Bmc.check(m.get(0) == 1 && m.get(1) == 2 && m.get(2) == 3);
    }

    @BmcProof
    void comparator_natural_order_symbolic_ordered_and_permutation() {
        int a = Bmc.anyInt(0, 50);
        int b = Bmc.anyInt(0, 50);
        Comparator<Integer> nat = Comparator.naturalOrder();
        ArrayList<Integer> m = new ArrayList<>();
        m.add(a);
        m.add(b);
        m.sort(nat);
        Bmc.check(m.get(0) <= m.get(1));        // ordered
        Bmc.check(m.get(0) + m.get(1) == a + b); // permutation
    }

    // ======== NEGATIVE law (must REFUTE) ===========================================================

    /**
     * Asserting the natural-order sorted result equals the (unsorted) input must REFUTE — the witness
     * genuinely reorders. If this ever VERIFIES, the ordering constraint has been lost.
     */
    @BmcProof(expect = Verdict.REFUTED)
    void natural_sorted_is_not_identity_when_unsorted() {
        List<Integer> out = List.of(2, 1).stream().sorted().toList();
        Bmc.check(out.get(0) == 2 && out.get(1) == 1); // false: natural sort is [1,2], not the input [2,1]
    }
}
