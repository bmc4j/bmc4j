package proofs.stream;

import java.util.OptionalLong;
import java.util.stream.LongStream;
import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;

/**
 * Model proofs (axis 2) for the {@code LongStream} terminal ops that return {@link OptionalLong} —
 * mirrors {@link IntStreamOptionalLaws}: {@code min()}, {@code max()}, {@code reduce(LongBinaryOperator)},
 * {@code findFirst()}, {@code findAny()}. These were the tail members PR #136 documented as blocked
 * purely on the unmodeled {@code OptionalLong} type; now an audited model. {@code average}/{@code
 * summaryStatistics} stay in the tail (double).
 */
class LongStreamOptionalLaws {

    @BmcProof
    void min_of_present() {
        OptionalLong o = LongStream.of(3, 1, 2).min();
        Bmc.check(o.isPresent() && o.getAsLong() == 1L);
    }

    @BmcProof(unwind = 4)
    void max_of_present() {
        OptionalLong o = LongStream.of(3, 1, 2).max();
        Bmc.check(o.isPresent() && o.getAsLong() == 3L);
    }

    @BmcProof
    void min_max_of_empty_are_absent() {
        Bmc.check(LongStream.empty().min().isEmpty());
        Bmc.check(LongStream.empty().max().isEmpty());
    }

    @BmcProof(unwind = 8)
    void reduce_no_identity_sums_when_present() {
        OptionalLong o = LongStream.of(1, 2, 3, 4).reduce((a, b) -> a + b);
        Bmc.check(o.isPresent() && o.getAsLong() == 10L);
    }

    @BmcProof
    void reduce_no_identity_empty_is_absent() {
        Bmc.check(LongStream.empty().reduce((a, b) -> a + b).isEmpty());
    }

    @BmcProof
    void findFirst_returns_head() {
        OptionalLong o = LongStream.of(7, 8, 9).findFirst();
        Bmc.check(o.isPresent() && o.getAsLong() == 7L);
    }

    @BmcProof
    void findAny_returns_an_element() {
        OptionalLong o = LongStream.of(7, 8, 9).findAny();
        Bmc.check(o.isPresent() && o.getAsLong() == 7L);
    }

    @BmcProof
    void findFirst_findAny_of_empty_are_absent() {
        Bmc.check(LongStream.empty().findFirst().isEmpty());
        Bmc.check(LongStream.empty().findAny().isEmpty());
    }

    /** Symbolic: min over three symbolic values is <= each element and equals one of them. */
    @BmcProof(unwind = 4)
    void symbolic_min_is_a_lower_bound() {
        long a = Bmc.anyLong(-1000, 1000);
        long b = Bmc.anyLong(-1000, 1000);
        long c = Bmc.anyLong(-1000, 1000);
        long m = LongStream.of(a, b, c).min().getAsLong();
        Bmc.check(m <= a && m <= b && m <= c && (m == a || m == b || m == c));
    }

    /** Symbolic: reduce(+) without identity equals the sum of the three elements. */
    @BmcProof(unwind = 4)
    void symbolic_reduce_sum() {
        long a = Bmc.anyLong(-1000, 1000);
        long b = Bmc.anyLong(-1000, 1000);
        long c = Bmc.anyLong(-1000, 1000);
        Bmc.check(LongStream.of(a, b, c).reduce((x, y) -> x + y).getAsLong() == a + b + c);
    }
}
