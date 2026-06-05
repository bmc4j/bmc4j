package kotlin.ranges;

/**
 * Clean model of the {@code kotlin.ranges.RangesKt} facade members bmc4j needs. Kotlin's INLINE
 * {@code associate}/{@code associateBy}/{@code associateWith} emit
 * {@code RangesKt.coerceAtLeast(mapCapacity(size), 16)} to compute their {@code LinkedHashMap}
 * initial capacity. The real facade reaches kotlin-stdlib internals JBMC stubs to nondet, which then
 * poisons the {@code LinkedHashMap(int)} ctor; this models the trivial {@code max(a, b)} semantics so
 * the (capacity-ignoring) bounded map model is sized soundly. Only the {@code (int,int)} overload is
 * modeled — the others remain JBMC stubs, as before.
 */
public final class RangesKt {

    private RangesKt() {
    }

    public static int coerceAtLeast(int value, int minimumValue) {
        return value < minimumValue ? minimumValue : value;
    }

    public static long coerceAtLeast(long value, long minimumValue) {
        return value < minimumValue ? minimumValue : value;
    }
}
