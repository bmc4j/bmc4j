package proofs.stream;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;

/**
 * Model proofs (axis 2) for the {@code Stream<T>} tail-2 ops added over the bounded {@link
 * java.util.stream.Stream} backing — {@code toArray()}/{@code toArray(IntFunction)}, {@code
 * forEachOrdered}, the 3-arg mutable {@code collect(supplier, accumulator, combiner)}, the 3-arg
 * {@code reduce(identity, accumulator, combiner)}, and the {@code mapMulti}/{@code mapMultiToInt}/
 * {@code mapMultiToLong} one-to-many flatteners. Chain-shaped, concrete expected results so a wrong
 * pipeline is caught; lists kept tiny (streams unwind to element count). The double-typed
 * {@code mapToDouble}/{@code mapMultiToDouble} stay in the tail (no double, by convention).
 */
class StreamTail2Laws {

    @BmcProof
    void toArray_roundtrips() {
        List<Integer> xs = List.of(5, 6, 7);
        Object[] a = xs.stream().toArray();
        Bmc.check(a.length == 3 && (Integer) a[0] == 5 && (Integer) a[2] == 7);
    }

    @BmcProof
    void toArray_generator_typed() {
        List<Integer> xs = List.of(1, 2, 3);
        Integer[] a = xs.stream().toArray(Integer[]::new);
        Bmc.check(a.length == 3 && a[0] == 1 && a[2] == 3);
    }

    @BmcProof
    void forEachOrdered_visits_all_in_order() {
        List<Integer> xs = List.of(1, 2, 3);
        int[] sum = {0};
        int[] first = {-1};
        xs.stream().forEachOrdered(x -> {
            if (first[0] < 0) {
                first[0] = x;
            }
            sum[0] += x;
        });
        Bmc.check(sum[0] == 6 && first[0] == 1);
    }

    @BmcProof
    void collect_mutable_into_list() {
        List<Integer> xs = List.of(1, 2, 3);
        ArrayList<Integer> out = xs.stream().collect(
                ArrayList::new,
                (list, x) -> list.add(x * 10),
                ArrayList::addAll);
        Bmc.check(out.size() == 3 && out.get(0) == 10 && out.get(2) == 30);
    }

    @BmcProof
    void reduce_three_arg_sums() {
        List<Integer> xs = List.of(1, 2, 3, 4);
        // identity 0, accumulate adding, combiner unused in sequential
        int sum = xs.stream().reduce(0, (acc, x) -> acc + x, (a, b) -> a + b);
        Bmc.check(sum == 10);
    }

    @BmcProof
    void reduce_three_arg_counts_via_accumulator() {
        List<Integer> xs = List.of(7, 8, 9);
        // map-then-fold shape: accumulate +1 per element regardless of value
        int n = xs.stream().reduce(0, (acc, x) -> acc + 1, (a, b) -> a + b);
        Bmc.check(n == 3);
    }

    @BmcProof
    void mapMulti_duplicates_each() {
        List<Integer> xs = List.of(1, 2, 3);
        // each x -> emit x and x*10
        List<Integer> out = xs.stream().<Integer>mapMulti((x, sink) -> {
            sink.accept(x);
            sink.accept(x * 10);
        }).toList();
        Bmc.check(out.size() == 6 && out.get(0) == 1 && out.get(1) == 10 && out.get(5) == 30);
    }

    @BmcProof
    void mapMulti_can_drop() {
        List<Integer> xs = List.of(1, 2, 3, 4);
        // emit only even elements
        long n = xs.stream().<Integer>mapMulti((x, sink) -> {
            if (x % 2 == 0) {
                sink.accept(x);
            }
        }).count();
        Bmc.check(n == 2L);
    }

    @BmcProof
    void mapMultiToInt_flattens_and_sums() {
        List<Integer> xs = List.of(1, 2, 3);
        IntStream s = xs.stream().mapMultiToInt((x, sink) -> {
            sink.accept(x);
            sink.accept(x);
        });
        Bmc.check(s.sum() == 12); // (1+1)+(2+2)+(3+3)
    }

    @BmcProof
    void mapMultiToLong_flattens_and_sums() {
        List<Integer> xs = List.of(1, 2, 3);
        LongStream s = xs.stream().mapMultiToLong((x, sink) -> {
            sink.accept((long) x);
            sink.accept((long) x);
        });
        Bmc.check(s.sum() == 12L);
    }

    /** Symbolic: a 3-arg sequential reduce equals the plain sum, for all inputs. */
    @BmcProof
    void symbolic_reduce_three_arg_equals_sum() {
        int a = Bmc.anyInt(0, 1000);
        int b = Bmc.anyInt(0, 1000);
        List<Integer> xs = List.of(a, b);
        int sum = xs.stream().reduce(0, (acc, x) -> acc + x, (p, q) -> p + q);
        Bmc.check(sum == a + b);
    }
}
