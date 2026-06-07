package kotlin.time;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcModelTail;

/**
 * Clean model of Kotlin's {@code kotlin.time.DurationKt} top-level facade — the duration-construction
 * extension functions/properties on {@code Int}/{@code Long}. The inline unit extensions
 * ({@code 90.minutes}, {@code a.seconds}) compile to {@code DurationKt.toDuration(value, unit)}, which
 * returns the bit-packed {@code long} {@link Duration} ABI. The real facade reaches kotlin-stdlib
 * internals JBMC stubs (so even {@code a.seconds + b.seconds} spuriously refuted); this delegates to the
 * {@link Duration} model's faithful packing.
 *
 * <p>Only the {@code Int}/{@code Long} construction surface is modeled (bmc4j avoids {@code double}); the
 * {@code Double} overloads remain JBMC stubs, consistent with the model's documented holes.
 */
@BmcModelTail(reason = "DurationKt Double-valued toDuration overload (no-double policy) plus the mangled "
        + "extension-property getters; loud under JBMC if reached")
public final class DurationKt {

    private DurationKt() {
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static long toDuration(int value, DurationUnit unit) {
        return Duration.toDuration((long) value, unit);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static long toDuration(long value, DurationUnit unit) {
        return Duration.toDuration(value, unit);
    }

    // Non-inline extension-property getters (DurationKt.getSeconds(int), etc.). The unit extensions are
    // @InlineOnly so callers usually inline straight to toDuration above, but model these too: a facade
    // REPLACES the class, so an un-modeled getter would nondet-stub.

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static long getNanoseconds(int v) {
        return toDuration(v, DurationUnit.NANOSECONDS);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static long getNanoseconds(long v) {
        return toDuration(v, DurationUnit.NANOSECONDS);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static long getMicroseconds(int v) {
        return toDuration(v, DurationUnit.MICROSECONDS);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static long getMicroseconds(long v) {
        return toDuration(v, DurationUnit.MICROSECONDS);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static long getMilliseconds(int v) {
        return toDuration(v, DurationUnit.MILLISECONDS);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static long getMilliseconds(long v) {
        return toDuration(v, DurationUnit.MILLISECONDS);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static long getSeconds(int v) {
        return toDuration(v, DurationUnit.SECONDS);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static long getSeconds(long v) {
        return toDuration(v, DurationUnit.SECONDS);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static long getMinutes(int v) {
        return toDuration(v, DurationUnit.MINUTES);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static long getMinutes(long v) {
        return toDuration(v, DurationUnit.MINUTES);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static long getHours(int v) {
        return toDuration(v, DurationUnit.HOURS);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static long getHours(long v) {
        return toDuration(v, DurationUnit.HOURS);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static long getDays(int v) {
        return toDuration(v, DurationUnit.DAYS);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static long getDays(long v) {
        return toDuration(v, DurationUnit.DAYS);
    }
}
