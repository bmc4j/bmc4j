package example.env;

/**
 * The "environment as a fixed value" case (the okio {@code HASH_BUCKET_COUNT} shape). A static
 * {@code <clinit>} reads a deterministic, unanalyzable query - {@link Environment#bucketCount()} on the
 * process-wide {@link #ENV} - into a {@code static final} bound that sizes an array and a fill loop.
 *
 * <p>Without help that bound is SYMBOLIC (the call has no analyzed body, so JBMC nondet-stubs it), so
 * {@code count()} can be anything and the array bound can't be closed.
 * {@code Bmc.assumeStable(ENV::bucketCount, n -> n == 8)} pins it to one fixed value for the whole run -
 * including this {@code <clinit>}, a call site a local {@code assume} can't reach - so the bound is the
 * concrete 8 and the proof verifies.
 */
public final class Buckets {

    /** The process-wide environment. Held in a static so the {@code <clinit>} below reads it; the proof
     *  pins {@code ENV.bucketCount()} via {@code assumeStable}. */
    public static Environment ENV;

    /** Read once in {@code <clinit>} from the (unanalyzable) environment query. */
    private static final int COUNT = ENV.bucketCount();

    private final int[] slots = new int[COUNT];

    public Buckets() {
        for (int i = 0; i < COUNT; i++) {
            slots[i] = i;
        }
    }

    /** The bound the {@code <clinit>} captured. */
    public int count() {
        return COUNT;
    }

    /**
     * The last filled slot's value, {@code slots[COUNT - 1]} (require {@code COUNT >= 1}). By
     * construction the fill loop sets it to {@code COUNT - 1} - proving that requires the loop fully
     * unwound, which needs the bound pinned to a fixed value.
     */
    public int lastSlot() {
        return slots[COUNT - 1];
    }
}
