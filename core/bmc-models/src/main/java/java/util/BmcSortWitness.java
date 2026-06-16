package java.util;

import org.cprover.CProver;

/**
 * Shared nondet-witness for the comparator-driven {@code sort}/{@code sorted} model surface
 * ({@link java.util.stream.Stream#sorted(Comparator)}, {@link ArrayList#sort(Comparator)},
 * {@link LinkedList#sort(Comparator)}, {@link Arrays}{@code .sort(T[], Comparator)} and
 * {@link Collections}{@code .sort(List, Comparator)}).
 *
 * <p><b>Why a witness, not a real algorithm.</b> Running an actual sort (insertion/selection/merge)
 * over symbolic data makes every comparison a data-dependent branch; the swaps recurse on the data,
 * which is SAT-pathological. Instead we <em>havoc the output and constrain it</em> to be exactly what
 * a sort must produce — this covers every sort implementation at once and stays decidable.
 *
 * <p><b>The two constraints (both mandatory).</b> Over the bounded backing array of length {@code n}:
 * <ol>
 *   <li><b>PERMUTATION.</b> We pick a nondet permutation {@code perm[0..n)} of {@code [0, n)},
 *       {@code assume} it is a genuine bijection (each source index used exactly once), and define
 *       {@code result[i] = input[perm[i]]}. Because every output slot is literally one input element
 *       and the index map is a bijection, {@code result} is a true multiset permutation of the input —
 *       no element is invented, dropped, or duplicated. This is the soundness-critical piece: an
 *       ordered-only havoc could return an arbitrary sorted array unrelated to the input.</li>
 *   <li><b>ORDERED.</b> For every adjacent pair we {@code assume comparator.compare(result[i],
 *       result[i+1]) <= 0}. The comparator is the caller's explicit (desugared SAM) comparator, which
 *       JBMC devirtualizes; driving it by index is sound.</li>
 * </ol>
 *
 * <p><b>Stability.</b> The witness does NOT preserve the input order of equal elements — for two
 * elements the comparator deems equal, either ordering satisfies both constraints. That is sound for
 * the JDK contract of {@code Stream.sorted} (ordering only). The list/array {@code sort} are documented
 * stable, but stability is only observable when the elements carry data the comparator ignores
 * (e.g. sorting records by one field); the bounded-int proofs here never exercise that, and a faithful
 * stable model would require a fixed compare-swap sorting network — out of scope for this witness.
 */
public final class BmcSortWitness {

    private BmcSortWitness() {
    }

    /**
     * Return a fresh {@link ArrayList} that is a comparator-ordered permutation of {@code input}
     * (see the class doc). Leaves {@code input} untouched; callers that sort in place copy back.
     */
    @SuppressWarnings("unchecked")
    public static <T> ArrayList<T> sorted(List<? extends T> input, Comparator<? super T> comparator) {
        int n = input.size();
        // Snapshot the source so later in-place callers don't see their own writes mid-witness.
        Object[] src = new Object[n];
        for (int i = 0; i < n; i++) {
            src[i] = input.get(i);
        }

        // PERMUTATION: a nondet index map perm[0..n) assumed to be a bijection of [0, n).
        int[] perm = new int[n];
        boolean[] used = new boolean[n];
        for (int i = 0; i < n; i++) {
            int p = CProver.nondetInt();
            // Split into atomic bounds, not one `&&` (see Bmc.anyInt(int, int)).
            CProver.assume(p >= 0);
            CProver.assume(p < n);
            CProver.assume(!used[p]);   // each source index chosen at most once
            used[p] = true;             // ... over n picks => exactly once => a bijection
            perm[i] = p;
        }

        ArrayList<T> result = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            result.add((T) src[perm[i]]);
        }

        // ORDERED: non-decreasing under the caller's comparator.
        for (int i = 0; i + 1 < n; i++) {
            CProver.assume(comparator.compare(result.get(i), result.get(i + 1)) <= 0);
        }
        return result;
    }

    /**
     * In-place comparator-ordered permutation of {@code arr[0..n)} (used by the array {@code sort}
     * overloads). Writes the witness back into {@code arr}.
     */
    @SuppressWarnings("unchecked")
    public static <T> void sortInPlace(T[] arr, int fromIndex, int toIndex, Comparator<? super T> comparator) {
        int n = toIndex - fromIndex;
        Object[] src = new Object[n];
        for (int i = 0; i < n; i++) {
            src[i] = arr[fromIndex + i];
        }

        int[] perm = new int[n];
        boolean[] used = new boolean[n];
        for (int i = 0; i < n; i++) {
            int p = CProver.nondetInt();
            // Split into atomic bounds, not one `&&` (see Bmc.anyInt(int, int)).
            CProver.assume(p >= 0);
            CProver.assume(p < n);
            CProver.assume(!used[p]);
            used[p] = true;
            perm[i] = p;
        }

        for (int i = 0; i < n; i++) {
            arr[fromIndex + i] = (T) src[perm[i]];
        }
        for (int i = 0; i + 1 < n; i++) {
            CProver.assume(comparator.compare(arr[fromIndex + i], arr[fromIndex + i + 1]) <= 0);
        }
    }
}
