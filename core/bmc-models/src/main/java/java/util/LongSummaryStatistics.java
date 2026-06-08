package java.util;

import java.util.function.IntConsumer;
import java.util.function.LongConsumer;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcNotNeeded;

/**
 * BMC model of {@link java.util.LongSummaryStatistics} — a mutable {@code count}/{@code sum}/{@code min}/
 * {@code max} accumulator over {@code long} values. Like {@link IntSummaryStatistics} it is fully modeled
 * with no tail and no unmodelable wall: {@code getMin}/{@code getMax} are INTEGER ({@code long})
 * comparison and {@code getAverage}'s {@code sum / count} is sound double division — bit-precise under
 * JBMC. The real class implements both {@code LongConsumer} and {@code IntConsumer}, so it carries both
 * {@code accept(long)} and the widening {@code accept(int)}.
 *
 * <p>{@code equals}/{@code hashCode}/{@code toString} are Object members excluded from the audit surface.
 */
@BmcNotNeeded(member = "andThen(java.util.function.LongConsumer)", reason = "LongConsumer.andThen default — composes this accept with a second consumer; sound inline bytecode under JBMC (two accept calls), no model needed")
@BmcNotNeeded(member = "andThen(java.util.function.IntConsumer)", reason = "IntConsumer.andThen default (inherited as a LongSummaryStatistics also implements IntConsumer) — composes this accept with a second consumer; sound inline bytecode under JBMC, no model needed")
public class LongSummaryStatistics implements LongConsumer, IntConsumer {

    private long count;
    private long sum;
    private long min = Long.MAX_VALUE;
    private long max = Long.MIN_VALUE;

    public LongSummaryStatistics() {
    }

    @Override
    @BmcModelConforms("differential (SummaryStatisticsConformanceTest) + @BmcProof (proofs.stream DoubleStreamLaws)")
    public void accept(int value) {
        accept((long) value);
    }

    @Override
    @BmcModelConforms("differential (SummaryStatisticsConformanceTest) + @BmcProof (proofs.stream DoubleStreamLaws)")
    public void accept(long value) {
        count++;
        sum += value;
        if (value < min) {
            min = value;
        }
        if (value > max) {
            max = value;
        }
    }

    @BmcModelConforms("differential (SummaryStatisticsConformanceTest) + @BmcProof (proofs.stream DoubleStreamLaws)")
    public void combine(LongSummaryStatistics other) {
        count += other.count;
        sum += other.sum;
        if (other.min < min) {
            min = other.min;
        }
        if (other.max > max) {
            max = other.max;
        }
    }

    @BmcModelConforms("differential (SummaryStatisticsConformanceTest) + @BmcProof (proofs.stream DoubleStreamLaws)")
    public final long getCount() {
        return count;
    }

    @BmcModelConforms("differential (SummaryStatisticsConformanceTest) + @BmcProof (proofs.stream DoubleStreamLaws)")
    public final long getSum() {
        return sum;
    }

    @BmcModelConforms("differential (SummaryStatisticsConformanceTest) + @BmcProof (proofs.stream DoubleStreamLaws)")
    public final long getMin() {
        return min;
    }

    @BmcModelConforms("differential (SummaryStatisticsConformanceTest) + @BmcProof (proofs.stream DoubleStreamLaws)")
    public final long getMax() {
        return max;
    }

    @BmcModelConforms("differential (SummaryStatisticsConformanceTest) + @BmcProof (proofs.stream DoubleStreamLaws)")
    public final double getAverage() {
        return count > 0 ? (double) sum / count : 0.0d;
    }
}
