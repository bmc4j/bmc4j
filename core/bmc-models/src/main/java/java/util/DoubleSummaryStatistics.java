package java.util;

import java.util.function.DoubleConsumer;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcNotNeeded;
import org.bmc4j.models.audit.BmcUnmodelable;

import static org.bmc4j.analysis.BmcUnmodelledReached.fail;

/**
 * BMC model of {@link java.util.DoubleSummaryStatistics} — a mutable {@code count}/{@code sum}/{@code
 * min}/{@code max} accumulator. The sound surface ({@code accept}, {@code getCount}, {@code getSum},
 * {@code getAverage}) is modeled with plain double arithmetic, which is bit-precise sound under JBMC.
 *
 * <p>{@code getMin}/{@code getMax} are LOUD {@link BmcUnmodelable}: the JDK's running min/max use
 * {@code Math.min}/{@code Math.max} TOTAL-ORDER semantics over NaN and signed zero (a {@code -0.0}
 * recorded as the minimum, NaN poisoning subsequent compares), which is the {@code Double.compare} /
 * {@code doubleToLongBits} FP total-order wall — one of the only two unsound double ops under JBMC. A
 * primitive {@code <}/{@code >} model would silently diverge from the JDK on NaN/{@code -0.0} (the
 * differential suite would catch it), so they fail loudly rather than lie. The accumulator still tracks
 * min/max internally (so {@code accept} matches the JDK's encounter-order contract for the running sum),
 * but the getters that EXPOSE the total order are walled off.
 *
 * <p>This is the FP-total-order boundary, not laziness: {@code getSum}/{@code getAverage} ARE modeled.
 *
 * <p>Note: the JDK uses compensated (Kahan) summation for {@code getSum}; this bounded model uses a plain
 * running sum, which is the soundly-modelable double addition. Differential conformance exercises it over
 * exactly-representable inputs where the two agree.
 */
@BmcNotNeeded(member = "andThen(java.util.function.DoubleConsumer)", reason = "DoubleConsumer.andThen default — composes this accept with a second consumer; sound inline bytecode under JBMC (two accept calls), no model needed")
public class DoubleSummaryStatistics implements DoubleConsumer {

    private long count;
    private double sum;
    private double min = Double.POSITIVE_INFINITY;
    private double max = Double.NEGATIVE_INFINITY;

    public DoubleSummaryStatistics() {
    }

    @Override
    @BmcModelConforms("differential (SummaryStatisticsConformanceTest) + @BmcProof (proofs.stream DoubleStreamLaws)")
    public void accept(double value) {
        count++;
        sum += value;
        // primitive </> tracking — sound as an internal accumulator; the total-order CONTRACT is walled
        // off at getMin/getMax. (Plain compares, not Math.min/max, so this never routes doubleToLongBits.)
        if (value < min) {
            min = value;
        }
        if (value > max) {
            max = value;
        }
    }

    @BmcModelConforms("differential (SummaryStatisticsConformanceTest) + @BmcProof (proofs.stream DoubleStreamLaws)")
    public void combine(DoubleSummaryStatistics other) {
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
    public final double getSum() {
        return sum;
    }

    @BmcModelConforms("differential (SummaryStatisticsConformanceTest) + @BmcProof (proofs.stream DoubleStreamLaws)")
    public final double getAverage() {
        return count > 0 ? sum / count : 0.0d;
    }

    @BmcUnmodelable(reason = "DoubleSummaryStatistics.getMin exposes the running min under Double.compare TOTAL order (NaN, -0.0<+0.0) via doubleToLongBits — the FP total-order wall, unsound under JBMC; a primitive-< model would diverge from the JDK on NaN/signed zero")
    public final double getMin() {
        throw fail("bmc4j: unmodelled member java.util.DoubleSummaryStatistics.getMin() — Double.compare total order (NaN/-0.0) via doubleToLongBits is unsound under JBMC");
    }

    @BmcUnmodelable(reason = "DoubleSummaryStatistics.getMax exposes the running max under Double.compare TOTAL order (NaN, -0.0<+0.0) via doubleToLongBits — the FP total-order wall, unsound under JBMC; a primitive-> model would diverge from the JDK on NaN/signed zero")
    public final double getMax() {
        throw fail("bmc4j: unmodelled member java.util.DoubleSummaryStatistics.getMax() — Double.compare total order (NaN/-0.0) via doubleToLongBits is unsound under JBMC");
    }
}
