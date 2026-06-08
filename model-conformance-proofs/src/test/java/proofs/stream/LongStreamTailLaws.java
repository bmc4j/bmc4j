package proofs.stream;

import java.util.stream.LongStream;
import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;

/**
 * Model proofs (axis 2) for the {@code LongStream} tail ops added over the bounded backing — mirrors
 * {@link IntStreamTailLaws}: {@code limit}/{@code skip}/{@code takeWhile}/{@code dropWhile}/{@code
 * distinct}/{@code sorted}/{@code peek}/{@code forEach}/{@code allMatch}/{@code noneMatch}/{@code
 * reduce(long,op)}/{@code toArray}/{@code mapToInt}, plus the static {@code of(long)}/{@code empty}/
 * {@code concat}/{@code iterate(3-arg)}. {@code min/max/reduce(op)/findFirst/findAny} (returning the
 * now-modeled {@code OptionalLong}) are proven in {@link LongStreamOptionalLaws}; {@code
 * average/summaryStatistics} stay in the tail (need OptionalDouble/LongSummaryStatistics + double).
 */
class LongStreamTailLaws {

    @BmcProof
    void limit_truncates() {
        Bmc.check(LongStream.of(1, 2, 3, 4).limit(2).sum() == 3L); // 1+2
    }

    @BmcProof
    void skip_drops_prefix() {
        Bmc.check(LongStream.of(1, 2, 3, 4).skip(2).sum() == 7L); // 3+4
    }

    @BmcProof
    void takeWhile_stops() {
        Bmc.check(LongStream.of(1, 2, 9, 1).takeWhile(x -> x < 5).sum() == 3L); // 1+2
    }

    @BmcProof
    void dropWhile_drops_leading_run() {
        Bmc.check(LongStream.of(1, 2, 9, 1).dropWhile(x -> x < 5).sum() == 10L); // 9+1
    }

    @BmcProof
    void distinct_dedups() {
        Bmc.check(LongStream.of(1, 2, 2, 3).distinct().count() == 3L);
    }

    @BmcProof
    void sorted_orders_ascending() {
        long[] a = LongStream.of(3, 1, 2).sorted().toArray();
        Bmc.check(a.length == 3 && a[0] == 1L && a[1] == 2L && a[2] == 3L);
    }

    @BmcProof
    void allMatch_true_when_all() {
        Bmc.check(LongStream.of(2, 4, 6).allMatch(x -> x % 2 == 0));
    }

    @BmcProof
    void noneMatch_true_when_none() {
        Bmc.check(LongStream.of(1, 3, 5).noneMatch(x -> x % 2 == 0));
    }

    @BmcProof
    void reduce_with_identity_sums() {
        Bmc.check(LongStream.of(1, 2, 3, 4).reduce(0L, (a, b) -> a + b) == 10L);
    }

    @BmcProof
    void toArray_roundtrips() {
        long[] a = LongStream.of(5, 6, 7).toArray();
        Bmc.check(a.length == 3 && a[0] == 5L && a[2] == 7L);
    }

    @BmcProof
    void peek_is_identity() {
        Bmc.check(LongStream.of(1, 2, 3).peek(x -> { }).sum() == 6L);
    }

    @BmcProof
    void mapToInt_narrows_and_sums() {
        Bmc.check(LongStream.of(1, 2, 3).mapToInt(x -> (int) (x + 1)).sum() == 9); // 2+3+4
    }

    @BmcProof
    void of_single() {
        Bmc.check(LongStream.of(42).sum() == 42L);
    }

    @BmcProof
    void empty_sum_is_zero() {
        Bmc.check(LongStream.empty().sum() == 0L && LongStream.empty().count() == 0L);
    }

    @BmcProof
    void concat_appends() {
        Bmc.check(LongStream.concat(LongStream.of(1, 2), LongStream.of(3, 4)).sum() == 10L);
    }

    @BmcProof
    void iterate_finite_terminates() {
        Bmc.check(LongStream.iterate(1, x -> x <= 4, x -> x * 2).sum() == 7L); // [1,2,4]
    }
}
