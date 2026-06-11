package proofs.stream;

import java.util.ArrayList;
import java.util.stream.IntStream;
import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;

/**
 * Model proofs (axis 2) for the {@code IntStream} tail-2 ops added over the bounded backing —
 * {@code flatMap(IntFunction)}, {@code forEachOrdered}, and the 3-arg mutable
 * {@code collect(supplier, ObjIntConsumer, combiner)}. Concrete tiny inputs; pins each op's result so
 * a wrong pipeline is caught. The primitive {@code mapMulti(IntMapMultiConsumer)} stays in the tail
 * (its nested SAM type isn't modeled); {@code average}/{@code mapToDouble} stay in the tail (double).
 */
class IntStreamTail2Laws {

    @BmcProof(unwind = 8)
    void flatMap_flattens_and_sums() {
        // each x -> IntStream.of(x, x*10) -> [1,10,2,20,3,30] -> sum 66
        int sum = IntStream.of(1, 2, 3).flatMap(x -> IntStream.of(x, x * 10)).sum();
        Bmc.check(sum == 66);
    }

    @BmcProof(unwind = 4)
    void flatMap_can_empty() {
        // each x -> empty -> total empty -> count 0
        long n = IntStream.of(1, 2, 3).flatMap(x -> IntStream.empty()).count();
        Bmc.check(n == 0L);
    }

    @BmcProof(unwind = 4)
    void forEachOrdered_visits_all_in_order() {
        int[] sum = {0};
        int[] first = {-1};
        IntStream.of(4, 5, 6).forEachOrdered(x -> {
            if (first[0] < 0) {
                first[0] = x;
            }
            sum[0] += x;
        });
        Bmc.check(sum[0] == 15 && first[0] == 4);
    }

    @BmcProof(unwind = 4)
    void collect_mutable_into_list() {
        ArrayList<Integer> out = IntStream.of(1, 2, 3).collect(
                ArrayList::new,
                (list, x) -> list.add(x + 100),
                ArrayList::addAll);
        Bmc.check(out.size() == 3 && out.get(0) == 101 && out.get(2) == 103);
    }

    /** Symbolic: flatMap with a singleton mapper is identity on the sum, for all inputs. */
    @BmcProof(unwind = 4)
    void symbolic_flatMap_singleton_is_identity() {
        int a = Bmc.anyInt(0, 1000);
        int b = Bmc.anyInt(0, 1000);
        int c = Bmc.anyInt(0, 1000);
        int sum = IntStream.of(a, b, c).flatMap(IntStream::of).sum();
        Bmc.check(sum == a + b + c);
    }
}
