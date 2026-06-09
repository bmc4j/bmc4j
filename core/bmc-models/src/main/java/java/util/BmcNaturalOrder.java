package java.util;

import static org.bmc4j.analysis.BmcUnmodelledReached.fail;

/**
 * Single concrete, devirtualizable natural-order comparison used by the natural-order
 * {@code sort}/{@code sorted} model surface ({@link java.util.stream.Stream#sorted()},
 * {@link Arrays}{@code .sort(Object[])} and its ranged overload, and the
 * {@link Comparator#naturalOrder()} the witness drives by index).
 *
 * <p><b>Why a single static method, not {@code ((Comparable) a).compareTo(b)}.</b> The natural-order
 * sort surfaces ordered their elements through a virtual {@code Comparable.compareTo} on the boxed,
 * unconstrained element type — a multi-implementor interface dispatch JBMC cannot devirtualize soundly
 * over the witness's nondet-indexed elements (the #169 devirt family). This method instead
 * {@code instanceof}-dispatches to BIT-PRECISE primitive comparisons for the JDK's builtin
 * {@link Comparable} types, so the comparison is a single concrete call the engine resolves cleanly.
 *
 * <p><b>Coverage (builtin Comparables only).</b> {@link Integer}, {@link Long}, {@link Short},
 * {@link Byte}, {@link Character}, {@link Boolean} via their bit-precise {@code compare(...)}, and
 * {@link String} (whose {@code compareTo} is already analyzable). Any other element type — including a
 * user-defined {@code Comparable} — is a GENUINE loud wall: the shipped model cannot enumerate unknown
 * types and the witness needs a known total order, so it routes through the
 * {@code org.bmc4j.analysis.BmcUnmodelledReached} sentinel and the verdict demotes to a member-named
 * UNKNOWN rather than proceeding on a fiction.
 *
 * <p>Floating-point ({@code Float}/{@code Double}) is deliberately ABSENT: their natural ordering is the
 * IEEE total order (NaN sorts high, {@code -0.0 < +0.0}) via {@code floatToIntBits}/{@code
 * doubleToLongBits}, the FP total-order wall this library treats as unsound under JBMC. They fall to the
 * loud default exactly like a user type.
 */
public final class BmcNaturalOrder {

    private BmcNaturalOrder() {
    }

    /**
     * The single natural-order {@link Comparator} instance — a concrete final implementor whose
     * {@code compare} delegates to {@link #compare(Object, Object)} and which JBMC devirtualizes (unlike
     * the JDK's package-private {@code NaturalOrderComparator} reached through
     * {@link Comparator#naturalOrder()}). Drives the {@link BmcSortWitness} ordering predicate for the
     * natural-order sort surfaces and is what the modeled {@link Comparator#naturalOrder()} returns.
     */
    public static final Comparator<Object> COMPARATOR = new Comparator<Object>() {
        @Override
        public int compare(Object a, Object b) {
            return BmcNaturalOrder.compare(a, b);
        }
    };

    /**
     * Total natural order over the builtin {@link Comparable} types, returning the sign convention of
     * {@code a.compareTo(b)} (negative if {@code a < b}, zero if equal, positive if {@code a > b}).
     * Throws the recognized loud failure for any non-builtin Comparable (see the class doc).
     */
    public static int compare(Object a, Object b) {
        if (a instanceof Integer && b instanceof Integer) {
            return Integer.compare((Integer) a, (Integer) b);
        }
        if (a instanceof Long && b instanceof Long) {
            return Long.compare((Long) a, (Long) b);
        }
        if (a instanceof Short && b instanceof Short) {
            return Short.compare((Short) a, (Short) b);
        }
        if (a instanceof Byte && b instanceof Byte) {
            return Byte.compare((Byte) a, (Byte) b);
        }
        if (a instanceof Character && b instanceof Character) {
            return Character.compare((Character) a, (Character) b);
        }
        if (a instanceof Boolean && b instanceof Boolean) {
            return Boolean.compare((Boolean) a, (Boolean) b);
        }
        if (a instanceof String && b instanceof String) {
            // String.compareTo is a plain lexicographic char loop JBMC analyzes soundly.
            return ((String) a).compareTo((String) b);
        }
        throw fail("bmc4j: unmodelled member — natural-order sort over a non-builtin Comparable "
                + "(BmcNaturalOrder.compare covers Integer/Long/Short/Byte/Character/Boolean/String only); "
                + "a user-defined Comparable.compareTo is an unknown total order JBMC cannot devirtualize "
                + "soundly over the witness — sort with an explicit Comparator instead");
    }
}
