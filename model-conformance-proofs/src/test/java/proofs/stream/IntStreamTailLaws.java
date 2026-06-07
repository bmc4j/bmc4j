package proofs.stream;

import java.util.stream.IntStream;
import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;

/**
 * Model proofs (axis 2) for the {@code IntStream} tail ops added over the bounded backing —
 * {@code limit}/{@code skip}/{@code takeWhile}/{@code dropWhile}/{@code distinct}/{@code sorted}/
 * {@code peek}/{@code forEach}/{@code allMatch}/{@code noneMatch}/{@code reduce(int,op)}/{@code
 * toArray}/{@code mapToLong}/{@code asLongStream}, plus the static {@code of(int)}/{@code empty}/
 * {@code concat}/{@code iterate(3-arg)}. Concrete tiny inputs; pins each op's result so a wrong
 * pipeline is caught. {@code min/max/reduce(op)/findFirst/findAny} (returning the now-modeled
 * {@code OptionalInt}) are proven in {@link IntStreamOptionalLaws}; {@code average/summaryStatistics}
 * stay in the tail (need OptionalDouble/IntSummaryStatistics + double).
 */
class IntStreamTailLaws {

    @BmcProof
    void limit_truncates() {
        Bmc.check(IntStream.of(1, 2, 3, 4).limit(2).sum() == 3); // 1+2
    }

    @BmcProof
    void skip_drops_prefix() {
        Bmc.check(IntStream.of(1, 2, 3, 4).skip(2).sum() == 7); // 3+4
    }

    @BmcProof
    void takeWhile_stops() {
        Bmc.check(IntStream.of(1, 2, 9, 1).takeWhile(x -> x < 5).sum() == 3); // 1+2
    }

    @BmcProof
    void dropWhile_drops_leading_run() {
        Bmc.check(IntStream.of(1, 2, 9, 1).dropWhile(x -> x < 5).sum() == 10); // 9+1
    }

    @BmcProof
    void distinct_dedups() {
        Bmc.check(IntStream.of(1, 2, 2, 3).distinct().count() == 3L);
    }

    @BmcProof
    void sorted_orders_ascending() {
        int[] a = IntStream.of(3, 1, 2).sorted().toArray();
        Bmc.check(a.length == 3 && a[0] == 1 && a[1] == 2 && a[2] == 3);
    }

    @BmcProof
    void allMatch_true_when_all() {
        Bmc.check(IntStream.of(2, 4, 6).allMatch(x -> x % 2 == 0));
    }

    @BmcProof
    void allMatch_false_when_one_fails() {
        Bmc.check(!IntStream.of(2, 3, 6).allMatch(x -> x % 2 == 0));
    }

    @BmcProof
    void noneMatch_true_when_none() {
        Bmc.check(IntStream.of(1, 3, 5).noneMatch(x -> x % 2 == 0));
    }

    @BmcProof
    void reduce_with_identity_sums() {
        Bmc.check(IntStream.of(1, 2, 3, 4).reduce(0, (a, b) -> a + b) == 10);
    }

    @BmcProof
    void reduce_with_identity_product() {
        Bmc.check(IntStream.of(2, 3, 4).reduce(1, (a, b) -> a * b) == 24);
    }

    @BmcProof
    void toArray_roundtrips() {
        int[] a = IntStream.of(5, 6, 7).toArray();
        Bmc.check(a.length == 3 && a[0] == 5 && a[2] == 7);
    }

    @BmcProof
    void peek_is_identity() {
        Bmc.check(IntStream.of(1, 2, 3).peek(x -> { }).sum() == 6);
    }

    @BmcProof
    void mapToLong_widens_and_sums() {
        Bmc.check(IntStream.of(1, 2, 3).mapToLong(x -> (long) x * 2).sum() == 12L);
    }

    @BmcProof
    void asLongStream_widens() {
        Bmc.check(IntStream.of(1, 2, 3).asLongStream().sum() == 6L);
    }

    @BmcProof
    void of_single() {
        Bmc.check(IntStream.of(42).sum() == 42);
    }

    @BmcProof
    void empty_sum_is_zero() {
        Bmc.check(IntStream.empty().sum() == 0 && IntStream.empty().count() == 0L);
    }

    @BmcProof
    void concat_appends() {
        Bmc.check(IntStream.concat(IntStream.of(1, 2), IntStream.of(3, 4)).sum() == 10);
    }

    @BmcProof
    void iterate_finite_terminates() {
        // seed 1, while <= 4, next *2 -> [1,2,4] -> sum 7
        Bmc.check(IntStream.iterate(1, x -> x <= 4, x -> x * 2).sum() == 7);
    }

    /** Symbolic: skip(1) over a 3-element stream drops exactly the head, for all inputs. */
    @BmcProof
    void symbolic_skip_one() {
        int a = Bmc.anyInt(0, 1000);
        int b = Bmc.anyInt(0, 1000);
        int c = Bmc.anyInt(0, 1000);
        Bmc.check(IntStream.of(a, b, c).skip(1).sum() == b + c);
    }
}
