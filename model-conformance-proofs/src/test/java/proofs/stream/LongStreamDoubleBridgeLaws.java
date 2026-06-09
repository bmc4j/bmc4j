package proofs.stream;

import java.util.LongSummaryStatistics;
import java.util.OptionalDouble;
import java.util.stream.LongStream;
import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;

/**
 * Model proofs (axis 2) for the {@code LongStream} -> double bridge ops pulled off the tail:
 * {@code average()}, {@code mapToDouble(LongToDoubleFunction)}, {@code asDoubleStream()}, and
 * {@code summaryStatistics()}. Mirrors {@link IntStreamDoubleBridgeLaws}: the (now audited)
 * {@code OptionalDouble}/{@code DoubleStream}/{@code LongSummaryStatistics} models make these modelable,
 * and the arithmetic is sound (long->double widening is exact for the small bounded values proofs use;
 * {@code average}'s {@code sum/count} is one sound double division — not the FP total-order wall).
 *
 * <p>Symbolic cases keep integer ranges TIGHT (the {@code symbolic_sum} lesson on full-width symbolic FP).
 */
class LongStreamDoubleBridgeLaws {

    @BmcProof
    void average_present_is_exact() {
        OptionalDouble o = LongStream.of(2L, 4L, 6L).average();
        Bmc.check(o.isPresent() && o.getAsDouble() == 4.0);
    }

    @BmcProof
    void average_empty_is_absent() {
        Bmc.check(LongStream.empty().average().isEmpty());
    }

    @BmcProof
    void mapToDouble_then_sum() {
        double s = LongStream.of(1L, 2L, 3L).mapToDouble(x -> x * 1.5).sum();
        Bmc.check(s == 9.0);
    }

    @BmcProof
    void asDoubleStream_widens_and_sums() {
        double s = LongStream.of(3L, 4L, 5L).asDoubleStream().sum();
        Bmc.check(s == 12.0);
    }

    @BmcProof
    void summaryStatistics_count_sum_min_max() {
        // LongSummaryStatistics min/max are INTEGER (long) comparison — sound.
        LongSummaryStatistics stats = LongStream.of(3L, 1L, 2L).summaryStatistics();
        Bmc.check(stats.getCount() == 3L && stats.getSum() == 6L
                && stats.getMin() == 1L && stats.getMax() == 3L);
    }

    @BmcProof
    void summaryStatistics_average_exact() {
        LongSummaryStatistics stats = LongStream.of(2L, 4L, 6L).summaryStatistics();
        Bmc.check(stats.getAverage() == 4.0);
    }

    /**
     * Symbolic: asDoubleStream().sum() equals the long sum widened, for all small inputs. The cost is
     * the symbolic FP-adder bit-width (the {@code symbolic_sum} lesson), so the operand window is kept
     * tight (±32); long->double widening is exact for every value in this window, so the narrowed-but-
     * still-symbolic range (crossing zero, both signs) proves the same identity. It ran ~111s fresh
     * over ±100 — close to the slow-CI budget wall — and well under it here.
     */
    @BmcProof
    void symbolic_asDoubleStream_sum_matches_long_sum() {
        long a = Bmc.anyLong(-32L, 32L);
        long b = Bmc.anyLong(-32L, 32L);
        long c = Bmc.anyLong(-32L, 32L);
        double s = LongStream.of(a, b, c).asDoubleStream().sum();
        Bmc.check(s == (double) (a + b + c));
    }
}
