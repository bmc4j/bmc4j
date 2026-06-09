package java.time.temporal;

import java.time.DateTimeException;
import java.io.Serializable;
import org.bmc4j.models.audit.BmcModelConforms;

/**
 * JBMC model of {@link java.time.temporal.ValueRange} — the valid-value range of a {@link TemporalField},
 * backed by the four real {@code long}s (smallest/largest minimum, smallest/largest maximum). The whole
 * surface reduces to integer comparisons/arithmetic JBMC reasons about precisely.
 *
 * <p>Modeled: the {@code of} factories (with the JDK's loud {@link IllegalArgumentException} when the
 * bounds are inconsistent), the four bound accessors, {@code isFixed}/{@code isIntValue}, the
 * value-validation predicates ({@code isValidValue}/{@code isValidIntValue}), the loud
 * {@code checkValidValue}/{@code checkValidIntValue} (throwing {@link DateTimeException} out of range,
 * exactly like the JDK), and {@code toString} (pure integer text, no dtoa/locale). {@code equals}/
 * {@code hashCode} compare the four bounds. All are validated bit-for-bit by the differential suite vs
 * the real JDK.
 *
 * <p>The whole real {@code ValueRange} surface is modeled (the four-long range is closed: factories,
 * bound accessors, validation predicates, the loud checks, equals/hashCode and the pure-integer
 * toString), so there is NO class-level {@code @BmcModelTail}: nothing falls through.
 */
public final class ValueRange implements Serializable {

    private final long minSmallest;
    private final long minLargest;
    private final long maxSmallest;
    private final long maxLargest;

    private ValueRange(long minSmallest, long minLargest, long maxSmallest, long maxLargest) {
        this.minSmallest = minSmallest;
        this.minLargest = minLargest;
        this.maxSmallest = maxSmallest;
        this.maxLargest = maxLargest;
    }

    @BmcModelConforms("differential (TimeConformanceTest)")
    public static ValueRange of(long min, long max) {
        if (min > max) {
            throw new IllegalArgumentException("Minimum value must be less than maximum value");
        }
        return new ValueRange(min, min, max, max);
    }

    @BmcModelConforms("differential (TimeConformanceTest)")
    public static ValueRange of(long min, long maxSmallest, long maxLargest) {
        return of(min, min, maxSmallest, maxLargest);
    }

    @BmcModelConforms("differential (TimeConformanceTest)")
    public static ValueRange of(long minSmallest, long minLargest, long maxSmallest, long maxLargest) {
        if (minSmallest > minLargest) {
            throw new IllegalArgumentException("Smallest minimum value must be less than largest minimum value");
        }
        if (maxSmallest > maxLargest) {
            throw new IllegalArgumentException("Smallest maximum value must be less than largest maximum value");
        }
        if (minLargest > maxLargest) {
            throw new IllegalArgumentException("Minimum value must be less than maximum value");
        }
        return new ValueRange(minSmallest, minLargest, maxSmallest, maxLargest);
    }

    @BmcModelConforms("differential (TimeConformanceTest)")
    public boolean isFixed() {
        return minSmallest == minLargest && maxSmallest == maxLargest;
    }

    @BmcModelConforms("differential (TimeConformanceTest)")
    public long getMinimum() {
        return minSmallest;
    }

    @BmcModelConforms("differential (TimeConformanceTest)")
    public long getLargestMinimum() {
        return minLargest;
    }

    @BmcModelConforms("differential (TimeConformanceTest)")
    public long getSmallestMaximum() {
        return maxSmallest;
    }

    @BmcModelConforms("differential (TimeConformanceTest)")
    public long getMaximum() {
        return maxLargest;
    }

    @BmcModelConforms("differential (TimeConformanceTest)")
    public boolean isIntValue() {
        return getMinimum() >= Integer.MIN_VALUE && getMaximum() <= Integer.MAX_VALUE;
    }

    @BmcModelConforms("differential (TimeConformanceTest)")
    public boolean isValidValue(long value) {
        return value >= getMinimum() && value <= getMaximum();
    }

    @BmcModelConforms("differential (TimeConformanceTest)")
    public boolean isValidIntValue(long value) {
        return isIntValue() && isValidValue(value);
    }

    @BmcModelConforms("differential (TimeConformanceTest)")
    public long checkValidValue(long value, TemporalField field) {
        if (!isValidValue(value)) {
            throw new DateTimeException(genInvalidFieldMessage(field, value));
        }
        return value;
    }

    @BmcModelConforms("differential (TimeConformanceTest)")
    public int checkValidIntValue(long value, TemporalField field) {
        if (!isValidIntValue(value)) {
            throw new DateTimeException(genInvalidFieldMessage(field, value));
        }
        return (int) value;
    }

    private String genInvalidFieldMessage(TemporalField field, long value) {
        if (field != null) {
            return "Invalid value for " + field + " (valid values " + this + "): " + value;
        }
        return "Invalid value (valid values " + this + "): " + value;
    }

    @Override
    @BmcModelConforms("differential (TimeConformanceTest)")
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof ValueRange)) {
            return false;
        }
        ValueRange other = (ValueRange) obj;
        return minSmallest == other.minSmallest && minLargest == other.minLargest
            && maxSmallest == other.maxSmallest && maxLargest == other.maxLargest;
    }

    @Override
    @BmcModelConforms("differential (TimeConformanceTest)")
    public int hashCode() {
        long hash = minSmallest + (minLargest << 16) + (minLargest >> 48) + (maxSmallest << 32)
            + (maxSmallest >> 32) + (maxLargest << 48) + (maxLargest >> 16);
        return (int) (hash ^ (hash >>> 32));
    }

    /**
     * The JDK's range text: {@code "min/largestMin - smallestMax/max"}, collapsing equal smallest/largest
     * bounds (so a fixed range like DAY_OF_WEEK renders {@code "1 - 7"}). Pure {@code long} text via
     * StringBuilder — no dtoa/locale — so it is sound under JBMC.
     */
    @Override
    @BmcModelConforms("differential (TimeConformanceTest)")
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append(minSmallest);
        if (minSmallest != minLargest) {
            buf.append('/').append(minLargest);
        }
        buf.append(" - ");
        buf.append(maxSmallest);
        if (maxSmallest != maxLargest) {
            buf.append('/').append(maxLargest);
        }
        return buf.toString();
    }
}
