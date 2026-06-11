package proofs.stream;

import java.util.ArrayList;
import java.util.stream.LongStream;
import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;

/**
 * Model proofs (axis 2) for the {@code LongStream} tail-2 ops added over the bounded backing —
 * {@code flatMap(LongFunction)}, {@code forEachOrdered}, and the 3-arg mutable
 * {@code collect(supplier, ObjLongConsumer, combiner)}. Mirrors {@link IntStreamTail2Laws}. The
 * primitive {@code mapMulti(LongMapMultiConsumer)} stays in the tail (nested SAM); {@code average}/
 * {@code mapToDouble} stay in the tail (double).
 */
class LongStreamTail2Laws {

    @BmcProof(unwind = 8)
    void flatMap_flattens_and_sums() {
        long sum = LongStream.of(1, 2, 3).flatMap(x -> LongStream.of(x, x * 10)).sum();
        Bmc.check(sum == 66L);
    }

    @BmcProof
    void flatMap_can_empty() {
        long n = LongStream.of(1, 2, 3).flatMap(x -> LongStream.empty()).count();
        Bmc.check(n == 0L);
    }

    @BmcProof
    void forEachOrdered_visits_all_in_order() {
        long[] sum = {0};
        long[] first = {-1};
        LongStream.of(4, 5, 6).forEachOrdered(x -> {
            if (first[0] < 0) {
                first[0] = x;
            }
            sum[0] += x;
        });
        Bmc.check(sum[0] == 15L && first[0] == 4L);
    }

    @BmcProof(unwind = 4)
    void collect_mutable_into_list() {
        ArrayList<Long> out = LongStream.of(1, 2, 3).collect(
                ArrayList::new,
                (list, x) -> list.add(x + 100),
                ArrayList::addAll);
        Bmc.check(out.size() == 3 && out.get(0) == 101L && out.get(2) == 103L);
    }

    /** Symbolic: flatMap with a singleton mapper is identity on the sum, for all inputs. */
    @BmcProof
    void symbolic_flatMap_singleton_is_identity() {
        long a = Bmc.anyInt(0, 1000);
        long b = Bmc.anyInt(0, 1000);
        long sum = LongStream.of(a, b).flatMap(LongStream::of).sum();
        Bmc.check(sum == a + b);
    }
}
