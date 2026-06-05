package kotlin.comparisons;

/**
 * Clean model of the {@code kotlin.comparisons.ComparisonsKt} facade members bmc4j needs. The
 * {@code Comparator} that Kotlin generates for {@code sortedBy { keySelector }} (and {@code
 * compareBy}/{@code maxBy} friends) implements {@code compare} by calling
 * {@code ComparisonsKt.compareValues(a, b)} on the selected keys. The real facade reaches kotlin-
 * stdlib internals JBMC stubs to a NONDET int — which silently unsoundens any sort/min/max-by. This
 * models the documented contract: natural-ordering compare via {@code Comparable.compareTo}, with
 * nulls ordered first (a null is "less than" any non-null, two nulls are equal). Sound for boxed
 * primitives and Strings' lexicographic compareTo.
 */
public final class ComparisonsKt {

    private ComparisonsKt() {
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static int compareValues(Comparable a, Comparable b) {
        if (a == b) {
            return 0;
        }
        if (a == null) {
            return -1;
        }
        if (b == null) {
            return 1;
        }
        return a.compareTo(b);
    }
}
