package java.util;

import java.util.function.IntConsumer;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcNotNeeded;

/**
 * BMC model of {@link java.util.IntSummaryStatistics} — a mutable {@code count}/{@code sum}/{@code min}/
 * {@code max} accumulator over {@code int} values. Fully modeled with no tail and no unmodelable wall:
 * {@code getMin}/{@code getMax} are INTEGER comparison (no {@code Double.compare} total order), and
 * {@code getAverage}'s {@code sum / count} is sound double division — all bit-precise under JBMC. (Only
 * {@code DoubleSummaryStatistics.getMin}/{@code getMax} hit the FP total-order wall; the integer summary
 * statistics do not.)
 *
 * <p>{@code equals}/{@code hashCode}/{@code toString} are Object members excluded from the audit surface.
 */
@BmcNotNeeded(member = "andThen(java.util.function.IntConsumer)", reason = "IntConsumer.andThen default — composes this accept with a second consumer; sound inline bytecode under JBMC (two accept calls), no model needed")
public class IntSummaryStatistics implements IntConsumer {

    private long count;
    private long sum;
    private int min = Integer.MAX_VALUE;
    private int max = Integer.MIN_VALUE;

    public IntSummaryStatistics() {
    }

    @Override
    @BmcModelConforms("differential (SummaryStatisticsConformanceTest) + @BmcProof (proofs.stream DoubleStreamLaws)")
    public void accept(int value) {
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
    public void combine(IntSummaryStatistics other) {
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
    public final int getMin() {
        return min;
    }

    @BmcModelConforms("differential (SummaryStatisticsConformanceTest) + @BmcProof (proofs.stream DoubleStreamLaws)")
    public final int getMax() {
        return max;
    }

    @BmcModelConforms("differential (SummaryStatisticsConformanceTest) + @BmcProof (proofs.stream DoubleStreamLaws)")
    public final double getAverage() {
        return count > 0 ? (double) sum / count : 0.0d;
    }
}
