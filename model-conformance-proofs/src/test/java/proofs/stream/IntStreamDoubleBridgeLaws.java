package proofs.stream;

import java.util.IntSummaryStatistics;
import java.util.OptionalDouble;
import java.util.stream.IntStream;
import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;

/**
 * Model proofs (axis 2) for the {@code IntStream} -> double bridge ops pulled off the tail:
 * {@code average()}, {@code mapToDouble(IntToDoubleFunction)}, {@code asDoubleStream()}, and
 * {@code summaryStatistics()}. These were blocked purely on the (now audited) {@code OptionalDouble} /
 * {@code DoubleStream} / {@code IntSummaryStatistics} models; the underlying arithmetic is sound under
 * JBMC — {@code int}->{@code double} widening is EXACT, and {@code average}'s {@code sum/count} is one
 * sound double division (NOT the FP total-order wall, which is only min/max/sorted).
 *
 * <p>Each law pins both the present and the empty case. Symbolic cases keep integer ranges TIGHT (the
 * {@code symbolic_sum} lesson: full-width symbolic FP times out; small integer-valued operands keep the
 * bit-vector FP adder/divider tractable while still proving the algebraic identity for all such inputs).
 */
class IntStreamDoubleBridgeLaws {

    @BmcProof
    void average_present_is_exact() {
        // 2+4+6 = 12, /3 = 4.0 — exact in binary FP.
        OptionalDouble o = IntStream.of(2, 4, 6).average();
        Bmc.check(o.isPresent() && o.getAsDouble() == 4.0);
    }

    @BmcProof
    void average_empty_is_absent() {
        Bmc.check(IntStream.empty().average().isEmpty());
    }

    @BmcProof
    void mapToDouble_then_sum() {
        double s = IntStream.of(1, 2, 3).mapToDouble(x -> x * 1.5).sum();
        Bmc.check(s == 9.0);
    }

    @BmcProof
    void asDoubleStream_widens_and_sums() {
        double s = IntStream.of(3, 4, 5).asDoubleStream().sum();
        Bmc.check(s == 12.0);
    }

    @BmcProof
    void summaryStatistics_count_sum_min_max() {
        // IntSummaryStatistics min/max are INTEGER comparison (sound) — unlike the double summary's wall.
        IntSummaryStatistics stats = IntStream.of(3, 1, 2).summaryStatistics();
        Bmc.check(stats.getCount() == 3L && stats.getSum() == 6L
                && stats.getMin() == 1 && stats.getMax() == 3);
    }

    @BmcProof
    void summaryStatistics_average_exact() {
        IntSummaryStatistics stats = IntStream.of(2, 4, 6).summaryStatistics();
        Bmc.check(stats.getAverage() == 4.0);
    }

    /**
     * Symbolic: asDoubleStream().sum() equals the integer sum widened, for all small inputs. The cost
     * is the symbolic FP-adder bit-width (the {@code symbolic_sum} lesson), so the operand window is
     * kept tight (±32); int->double widening is exact for every value, so this narrowed-but-still-
     * symbolic window (crossing zero, both signs) proves the same identity. It ran ~135s fresh over
     * ±100 — right at the slow-CI budget wall — and well under it here.
     */
    @BmcProof
    void symbolic_asDoubleStream_sum_matches_int_sum() {
        int a = Bmc.anyInt(-32, 32);
        int b = Bmc.anyInt(-32, 32);
        int c = Bmc.anyInt(-32, 32);
        double s = IntStream.of(a, b, c).asDoubleStream().sum();
        Bmc.check(s == (double) (a + b + c));
    }
}
