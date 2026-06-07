package kotlin.time;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcModelTail;

/**
 * Clean model of Kotlin's {@code kotlin.time.Duration} value class. Like the real type, {@code Duration}
 * is a {@code @JvmInline value class} over a single unit-discriminating bit-packed {@code Long}
 * ({@code rawValue}): the top 63 bits hold the magnitude, the low bit the unit (0 = nanoseconds,
 * 1 = milliseconds). At the JVM ABI a value class is erased to its backing {@code long}, so every public
 * member is a {@code static} method taking/returning that {@code long} — and the Kotlin compiler mangles
 * the names of members whose signatures mention the value type (e.g. {@code plus-LRDsOJo},
 * {@code getInWholeSeconds-impl}). This model reproduces that exact ABI; the dashed names are produced by
 * a post-compile bytecode rename in this module's build (Java identifiers cannot contain {@code '-'}), so
 * the consumer's {@code invokestatic kotlin/time/Duration."plus-LRDsOJo":(JJ)J} resolves to a modeled body
 * instead of an unmodeled stdlib chain that JBMC stubs to nondet (today's spurious REFUTED).
 *
 * <p><b>Representation:</b> the model reproduces the real bit-packing faithfully (same nanos/millis split,
 * same normalization, same {@code coerceIn(-MAX_MILLIS, MAX_MILLIS)} saturation at the boundary), so
 * conformance is exact rather than approximate: construction from units, {@code +}/{@code -},
 * {@code times}/{@code div} by an {@code Int} scalar (incl. the overflow-saturates-to-infinity path),
 * comparison, {@code inWhole*}, negatives, and the nanos/millis saturation boundary all match the JVM
 * {@code kotlin.time.Duration} (verified differentially in {@code KotlinDurationConformanceTest}).
 *
 * <p><b>Documented holes:</b> {@code toString}/{@code toIsoString}/{@code parse} (decimal/string formatting —
 * out of scope for a bounded model, same as {@code java.time}), the {@code Double} {@code times}/{@code div}
 * overloads and the {@code Duration / Duration -> Double} ratio (bmc4j avoids {@code double}; use the
 * {@code Int}-scalar {@code times}/{@code div}), the {@code Double} construction/arithmetic
 * overloads (bmc4j avoids {@code double}; use the {@code Int}/{@code Long} unit extensions), and
 * {@code TimeSource}/{@code TimeMark} (wall-clock, external world). Infinite durations
 * ({@code Duration.INFINITE}) are representable and compare/saturate correctly, but the {@code INVALID}
 * sentinel and parsing are not modeled. Members not modeled here remain JBMC nondet stubs — the same
 * facade-replacement caveat as every other Kotlin model.
 *
 * <p><b>Per-member audit + mangled ABI convention:</b> the per-member auditing gate enumerates the real
 * {@code kotlin.time.Duration} surface by reflection over kotlin-stdlib, whose value-class members carry
 * the kotlinc-mangled ABI names ({@code plus-LRDsOJo}, {@code getInWholeSeconds-impl}, …). Because the
 * model is authored with legal Java placeholder names that the build's {@code renameDurationAbi} pass
 * rewrites to those exact dashed names — carrying the {@code @BmcModelConforms} annotation along with the
 * renamed method — a modeled member keys against its real twin by the mangled name directly (no special
 * casing in the gate). The mangled-but-unmodeled members (toString/parse/Double overloads) fall into the
 * {@link BmcModelTail} below, enumerated under their mangled names in {@code docs/model-coverage.md}.
 */
@BmcModelTail(reason = "exotic kotlin.time.Duration value-class remainder under the mangled JVM ABI — "
        + "toString/toIsoString/parse formatting, Double times/div/ratio overloads (no-double policy), "
        + "TimeSource/TimeMark wall-clock; loud under JBMC if reached")
public final class Duration {

    // The ranges mirror the real kotlin.time.Duration: symmetric about zero, non-overlapping but adjacent
    // (the first value that doesn't fit the nanos range is exactly representable in millis).
    static final int NANOS_IN_MILLIS = 1_000_000;
    static final long MAX_MILLIS = Long.MAX_VALUE / 2;
    // ends in ..._999_999
    static final long MAX_NANOS = Long.MAX_VALUE / 2 / NANOS_IN_MILLIS * NANOS_IN_MILLIS - 1;
    static final long MAX_NANOS_IN_MILLIS = MAX_NANOS / NANOS_IN_MILLIS;

    // The erased backing field. Present so the model has the same shape as the real value class; the static
    // methods below operate on the raw long directly (matching the value-class ABI), not via instances.
    private final long rawValue;

    private Duration(long rawValue) {
        this.rawValue = rawValue;
    }

    // ---- raw-value helpers (mirror the stdlib's private inline helpers) ----

    private static long value(long raw) {
        return raw >> 1;
    }

    private static int unitDiscriminator(long raw) {
        return (int) raw & 1;
    }

    private static boolean isInNanos(long raw) {
        return unitDiscriminator(raw) == 0;
    }

    private static boolean isInMillis(long raw) {
        return unitDiscriminator(raw) == 1;
    }

    private static long nanosToMillis(long nanos) {
        return nanos / NANOS_IN_MILLIS;
    }

    private static long millisToNanos(long millis) {
        return millis * NANOS_IN_MILLIS;
    }

    private static long durationOfNanos(long normalNanos) {
        return normalNanos << 1;
    }

    private static long durationOfMillis(long normalMillis) {
        return (normalMillis << 1) + 1;
    }

    private static long durationOf(long normalValue, int unitDiscriminator) {
        return (normalValue << 1) + unitDiscriminator;
    }

    private static long durationOfNanosNormalized(long nanos) {
        if (nanos >= -MAX_NANOS && nanos <= MAX_NANOS) {
            return durationOfNanos(nanos);
        }
        return durationOfMillis(nanosToMillis(nanos));
    }

    private static long durationOfMillisNormalized(long millis) {
        if (millis >= -MAX_NANOS_IN_MILLIS && millis <= MAX_NANOS_IN_MILLIS) {
            return durationOfNanos(millisToNanos(millis));
        }
        return durationOfMillis(coerceIn(millis, -MAX_MILLIS, MAX_MILLIS));
    }

    private static long coerceIn(long v, long lo, long hi) {
        if (v < lo) {
            return lo;
        }
        if (v > hi) {
            return hi;
        }
        return v;
    }

    private static boolean isInfiniteMillis(long v) {
        return v == MAX_MILLIS || v == -MAX_MILLIS;
    }

    private static boolean isFiniteMillis(long v) {
        return -MAX_MILLIS < v && v < MAX_MILLIS;
    }

    private static boolean sameSign(long a, long b) {
        return (a ^ b) >= 0L;
    }

    // INFINITE / NEG_INFINITE raw values (millis range, magnitude MAX_MILLIS).
    private static final long INFINITE_RAW = durationOfMillis(MAX_MILLIS);
    private static final long NEG_INFINITE_RAW = durationOfMillis(-MAX_MILLIS);

    private static long addMillisWithoutOverflow(long thisMillis, long other) {
        if (isInfiniteMillis(thisMillis)) {
            return (isFiniteMillis(other) || sameSign(thisMillis, other)) ? thisMillis : INVALID_RAW_VALUE;
        }
        if (isInfiniteMillis(other)) {
            return other;
        }
        return coerceIn(thisMillis + other, -MAX_MILLIS, MAX_MILLIS);
    }

    static final long INVALID_RAW_VALUE = 0x7FFFFFFFFFFFC0DEL;

    // ---- construction (used by DurationKt.toDuration) ----

    /** Mirrors {@code Long.toDuration(unit)} for the integer-unit construction path. */
    @BmcModelConforms("differential (KotlinDurationConformanceTest) + @BmcProof (proofs.kotlintime)")
    public static long toDuration(long value, DurationUnit unit) {
        long maxNsInUnit = convertNanosToUnitSaturating(MAX_NANOS, unit);
        if (value >= -maxNsInUnit && value <= maxNsInUnit) {
            // fits the nanos range
            return durationOfNanos(value * unit.nanosScale);
        }
        // doesn't fit nanos: go to millis. value * (nanos-per-unit) / NANOS_IN_MILLIS, saturating.
        long millis = convertToMillisSaturating(value, unit);
        return durationOfMillis(coerceIn(millis, -MAX_MILLIS, MAX_MILLIS));
    }

    // MAX_NANOS expressed in `unit` (rounding toward zero), used as the nanos-range bound test.
    private static long convertNanosToUnitSaturating(long nanos, DurationUnit unit) {
        return nanos / unit.nanosScale;
    }

    // value (in `unit`) -> milliseconds, saturating to MAX_MILLIS on overflow. unit >= MILLISECONDS here in
    // practice (smaller units always fit the nanos range for any value that reaches this branch).
    private static long convertToMillisSaturating(long value, DurationUnit unit) {
        long millisPerUnit = unit.nanosScale / NANOS_IN_MILLIS;
        if (millisPerUnit == 0L) {
            // sub-millisecond unit reaching the millis branch: convert via nanos with saturation.
            return coerceIn(value / (NANOS_IN_MILLIS / unit.nanosScale), -MAX_MILLIS, MAX_MILLIS);
        }
        // saturating multiply value * millisPerUnit
        long r = value * millisPerUnit;
        if (millisPerUnit != 0 && r / millisPerUnit != value) {
            // overflowed
            return value > 0 ? MAX_MILLIS : -MAX_MILLIS;
        }
        return r;
    }

    // ---- arithmetic ----

    @BmcModelConforms("differential (KotlinDurationConformanceTest) + @BmcProof (proofs.kotlintime)")
    public static long plus(long a, long b) {
        if (unitDiscriminator(a) == unitDiscriminator(b)) {
            if (isInNanos(a)) {
                return durationOfNanosNormalized(value(a) + value(b));
            }
            long it = addMillisWithoutOverflow(value(a), value(b));
            if (it == INVALID_RAW_VALUE) {
                throw new IllegalArgumentException(
                        "Summing infinite durations of different signs yields an undefined result.");
            }
            if (isInfiniteMillis(it)) {
                return durationOfMillis(it);
            }
            return durationOfMillisNormalized(it);
        }
        if (isInMillis(a)) {
            return addValuesMixedRanges(value(a), value(b));
        }
        return addValuesMixedRanges(value(b), value(a));
    }

    private static long addValuesMixedRanges(long thisMillis, long otherNanos) {
        long otherMillis = nanosToMillis(otherNanos);
        long resultMillis = addMillisWithoutOverflow(thisMillis, otherMillis);
        if (resultMillis >= -MAX_NANOS_IN_MILLIS && resultMillis <= MAX_NANOS_IN_MILLIS) {
            long otherNanoRemainder = otherNanos - millisToNanos(otherMillis);
            return durationOfNanos(millisToNanos(resultMillis) + otherNanoRemainder);
        }
        return durationOfMillis(resultMillis);
    }

    @BmcModelConforms("differential (KotlinDurationConformanceTest) + @BmcProof (proofs.kotlintime)")
    public static long unaryMinus(long raw) {
        return durationOf(-value(raw), unitDiscriminator(raw));
    }

    @BmcModelConforms("differential (KotlinDurationConformanceTest) + @BmcProof (proofs.kotlintime)")
    public static long minus(long a, long b) {
        return plus(a, unaryMinus(b));
    }

    // ---- scalar multiply / divide by an Int (the Double overloads stay declined: no-double policy) ----
    //
    // Faithful port of the stdlib's value-class times(Int)/div(Int), including the same
    // overflow-saturates-to-infinity behavior and the nanos<->millis range handling, so the bit-packed
    // result matches kotlin.time.Duration exactly (differentially verified). The Int-returning sign is
    // -1/0/1 like kotlin's .sign.

    private static int signOf(long v) {
        return v < 0 ? -1 : (v > 0 ? 1 : 0);
    }

    private static int signOf(int v) {
        return v < 0 ? -1 : (v > 0 ? 1 : 0);
    }

    @BmcModelConforms("differential (KotlinDurationConformanceTest) + @BmcProof (proofs.kotlintime)")
    public static long times(long raw, int scale) {
        if (isInfinite(raw)) {
            if (scale == 0) {
                throw new IllegalArgumentException(
                        "Multiplying infinite duration by zero yields an undefined result.");
            }
            return scale > 0 ? raw : unaryMinus(raw);
        }
        if (scale == 0) {
            return durationOfNanos(0L);   // ZERO
        }
        long value = value(raw);
        long result = value * scale;
        if (isInNanos(raw)) {
            // (MAX_NANOS / Int.MIN_VALUE) .. (-MAX_NANOS / Int.MIN_VALUE): the band where no scale can
            // overflow the nanos range. Int.MIN_VALUE is negative, so the low bound is negative.
            long lo = MAX_NANOS / Integer.MIN_VALUE;
            long hi = -MAX_NANOS / Integer.MIN_VALUE;
            if (value >= lo && value <= hi) {
                return durationOfNanos(result);
            }
            if (result / scale == value) {
                return durationOfNanosNormalized(result);
            }
            long millis = nanosToMillis(value);
            long remNanos = value - millisToNanos(millis);
            long resultMillis = millis * scale;
            long totalMillis = resultMillis + nanosToMillis(remNanos * scale);
            if (resultMillis / scale == millis && (totalMillis ^ resultMillis) >= 0) {
                return durationOfMillis(coerceIn(totalMillis, -MAX_MILLIS, MAX_MILLIS));
            }
            return signOf(value) * signOf(scale) > 0 ? INFINITE_RAW : NEG_INFINITE_RAW;
        }
        if (result / scale == value) {
            return durationOfMillis(coerceIn(result, -MAX_MILLIS, MAX_MILLIS));
        }
        return signOf(value) * signOf(scale) > 0 ? INFINITE_RAW : NEG_INFINITE_RAW;
    }

    @BmcModelConforms("differential (KotlinDurationConformanceTest) + @BmcProof (proofs.kotlintime)")
    public static long div(long raw, int scale) {
        if (scale == 0) {
            if (isPositive(raw)) {
                return INFINITE_RAW;
            }
            if (isNegative(raw)) {
                return NEG_INFINITE_RAW;
            }
            throw new IllegalArgumentException(
                    "Dividing zero duration by zero yields an undefined result.");
        }
        long value = value(raw);
        if (isInNanos(raw)) {
            return durationOfNanos(value / scale);
        }
        if (isInfinite(raw)) {
            return times(raw, signOf(scale));
        }
        long result = value / scale;
        if (result >= -MAX_NANOS_IN_MILLIS && result <= MAX_NANOS_IN_MILLIS) {
            long rem = millisToNanos(value - (result * scale)) / scale;
            return durationOfNanos(millisToNanos(result) + rem);
        }
        return durationOfMillis(result);
    }

    // ---- predicates ----

    @BmcModelConforms("differential (KotlinDurationConformanceTest) + @BmcProof (proofs.kotlintime)")
    public static boolean isNegative(long raw) {
        return raw < 0;
    }

    @BmcModelConforms("differential (KotlinDurationConformanceTest) + @BmcProof (proofs.kotlintime)")
    public static boolean isPositive(long raw) {
        return raw > 0;
    }

    @BmcModelConforms("differential (KotlinDurationConformanceTest) + @BmcProof (proofs.kotlintime)")
    public static boolean isInfinite(long raw) {
        return raw == INFINITE_RAW || raw == NEG_INFINITE_RAW;
    }

    @BmcModelConforms("differential (KotlinDurationConformanceTest) + @BmcProof (proofs.kotlintime)")
    public static boolean isFinite(long raw) {
        return !isInfinite(raw);
    }

    @BmcModelConforms("differential (KotlinDurationConformanceTest) + @BmcProof (proofs.kotlintime)")
    public static long getAbsoluteValue(long raw) {
        return isNegative(raw) ? unaryMinus(raw) : raw;
    }

    // ---- comparison ----

    @BmcModelConforms("differential (KotlinDurationConformanceTest) + @BmcProof (proofs.kotlintime)")
    public static int compareTo(long a, long b) {
        long compareBits = a ^ b;
        if (compareBits < 0 || ((int) compareBits & 1) == 0) {
            // different signs, or same sign / same range
            return Long.compare(a, b);
        }
        // same sign, different ranges: compare ranges (nanos < millis magnitude)
        int r = unitDiscriminator(a) - unitDiscriminator(b);
        return isNegative(a) ? -r : r;
    }

    // ---- conversion to Long units ----

    @BmcModelConforms("differential (KotlinDurationConformanceTest) + @BmcProof (proofs.kotlintime)")
    public static long toLong(long raw, DurationUnit unit) {
        if (raw == INFINITE_RAW) {
            return Long.MAX_VALUE;
        }
        if (raw == NEG_INFINITE_RAW) {
            return Long.MIN_VALUE;
        }
        long v = value(raw);
        long storageScale = isInNanos(raw) ? 1L : (long) NANOS_IN_MILLIS;
        // v is in storage units (nanos or millis). Convert to `unit` with truncation toward zero,
        // saturating to the Long range — mirrors convertDurationUnit(Long, ...).
        long storageNanosScale = storageScale; // nanos-per-storage-unit (1 for nanos, 1e6 for millis)
        long targetScale = unit.nanosScale;     // nanos-per-target-unit
        if (storageNanosScale >= targetScale) {
            // scaling up: multiply (smaller storage unit -> bigger... actually storage->target where target
            // is coarser-or-equal); guard overflow by saturating.
            long factor = storageNanosScale / targetScale;
            long r = v * factor;
            if (factor != 0 && r / factor != v) {
                return v > 0 ? Long.MAX_VALUE : Long.MIN_VALUE;
            }
            return r;
        } else {
            long factor = targetScale / storageNanosScale;
            return v / factor;
        }
    }

    @BmcModelConforms("differential (KotlinDurationConformanceTest) + @BmcProof (proofs.kotlintime)")
    public static long getInWholeDays(long raw) {
        return toLong(raw, DurationUnit.DAYS);
    }

    @BmcModelConforms("differential (KotlinDurationConformanceTest) + @BmcProof (proofs.kotlintime)")
    public static long getInWholeHours(long raw) {
        return toLong(raw, DurationUnit.HOURS);
    }

    @BmcModelConforms("differential (KotlinDurationConformanceTest) + @BmcProof (proofs.kotlintime)")
    public static long getInWholeMinutes(long raw) {
        return toLong(raw, DurationUnit.MINUTES);
    }

    @BmcModelConforms("differential (KotlinDurationConformanceTest) + @BmcProof (proofs.kotlintime)")
    public static long getInWholeSeconds(long raw) {
        return toLong(raw, DurationUnit.SECONDS);
    }

    @BmcModelConforms("differential (KotlinDurationConformanceTest) + @BmcProof (proofs.kotlintime)")
    public static long getInWholeMilliseconds(long raw) {
        if (isInMillis(raw) && isFinite(raw)) {
            return value(raw);
        }
        return toLong(raw, DurationUnit.MILLISECONDS);
    }

    @BmcModelConforms("differential (KotlinDurationConformanceTest) + @BmcProof (proofs.kotlintime)")
    public static long getInWholeMicroseconds(long raw) {
        return toLong(raw, DurationUnit.MICROSECONDS);
    }

    @BmcModelConforms("differential (KotlinDurationConformanceTest) + @BmcProof (proofs.kotlintime)")
    public static long getInWholeNanoseconds(long raw) {
        long v = value(raw);
        if (isInNanos(raw)) {
            return v;
        }
        if (v > Long.MAX_VALUE / NANOS_IN_MILLIS) {
            return Long.MAX_VALUE;
        }
        if (v < Long.MIN_VALUE / NANOS_IN_MILLIS) {
            return Long.MIN_VALUE;
        }
        return millisToNanos(v);
    }

    // ---- equals / hashCode (value-class ABI: equals-impl0(long,long), hashCode-impl(long)) ----

    @BmcModelConforms("differential (KotlinDurationConformanceTest) + @BmcProof (proofs.kotlintime)")
    public static boolean equals0(long a, long b) {
        return a == b;
    }

    @BmcModelConforms("differential (KotlinDurationConformanceTest) + @BmcProof (proofs.kotlintime)")
    public static int hashCode(long raw) {
        return (int) (raw ^ (raw >>> 32));
    }
}
