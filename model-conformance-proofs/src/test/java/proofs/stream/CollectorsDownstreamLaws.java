package proofs.stream;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;

/**
 * Model proofs (axis 2) for the composite, downstream-nesting {@code Collectors} added over the
 * bounded backing — {@code groupingBy(classifier, downstream)}, {@code partitioningBy(pred,
 * downstream)}, {@code collectingAndThen}, {@code filtering}, {@code flatMapping}, {@code teeing},
 * and {@code toCollection}. Each pins a concrete result so a wrong nesting is caught; the downstream
 * is interpreted by {@code ListStream.collect} over a fresh bounded sub-stream.
 */
class CollectorsDownstreamLaws {

    @BmcProof
    void groupingBy_with_counting_downstream() {
        List<Integer> xs = List.of(1, 2, 3, 4);
        // group by parity, count each: {1(odd): 1,3 -> 2 ; 0(even): 2,4 -> 2}
        Map<Integer, Long> m = xs.stream().collect(
                Collectors.groupingBy(x -> x % 2, Collectors.counting()));
        Bmc.check(m.get(1) == 2L && m.get(0) == 2L);
    }

    @BmcProof
    void groupingBy_with_mapping_downstream() {
        List<Integer> xs = List.of(1, 2, 3, 4);
        // group by parity, map each *10 into a list: {0: [20,40], 1: [10,30]}
        Map<Integer, List<Integer>> m = xs.stream().collect(
                Collectors.groupingBy(x -> x % 2, Collectors.mapping(x -> x * 10, Collectors.toList())));
        Bmc.check(m.get(0).size() == 2 && m.get(0).get(0) == 20 && m.get(0).get(1) == 40);
        Bmc.check(m.get(1).get(0) == 10 && m.get(1).get(1) == 30);
    }

    @BmcProof
    void partitioningBy_with_counting_downstream() {
        List<Integer> xs = List.of(1, 2, 3, 4);
        Map<Boolean, Long> m = xs.stream().collect(
                Collectors.partitioningBy(x -> x % 2 == 0, Collectors.counting()));
        Bmc.check(m.get(Boolean.TRUE) == 2L && m.get(Boolean.FALSE) == 2L);
    }

    @BmcProof
    void collectingAndThen_finishes() {
        List<Integer> xs = List.of(1, 2, 3);
        // collect to list, then take its size
        int size = xs.stream().collect(
                Collectors.collectingAndThen(Collectors.toList(), List::size));
        Bmc.check(size == 3);
    }

    @BmcProof
    void filtering_keeps_only_matching() {
        List<Integer> xs = List.of(1, 2, 3, 4);
        // keep evens, collect to list -> [2,4]
        List<Integer> out = xs.stream().collect(
                Collectors.filtering(x -> x % 2 == 0, Collectors.toList()));
        Bmc.check(out.size() == 2 && out.get(0) == 2 && out.get(1) == 4);
    }

    @BmcProof
    void filtering_into_grouping_counts_kept_only() {
        List<Integer> xs = List.of(2, 3, 6);
        // group by parity, but only count multiples of 3 within each group:
        // odd: {3} kept -> 1 ; even: {2,6} -> only 6 kept -> 1
        Map<Integer, Long> m = xs.stream().collect(
                Collectors.groupingBy(x -> x % 2,
                        Collectors.filtering(x -> x % 3 == 0, Collectors.counting())));
        Bmc.check(m.get(1) == 1L && m.get(0) == 1L);
    }

    @BmcProof
    void flatMapping_flattens_into_downstream() {
        List<Integer> xs = List.of(1, 2);
        // each x -> [x, x*10]; flatten into a list -> [1,10,2,20]
        List<Integer> out = xs.stream().collect(
                Collectors.flatMapping(x -> Stream.of(x, x * 10), Collectors.toList()));
        Bmc.check(out.size() == 4 && out.get(0) == 1 && out.get(1) == 10 && out.get(3) == 20);
    }

    @BmcProof
    void teeing_merges_two_downstreams() {
        List<Integer> xs = List.of(1, 2, 3, 4);
        // count and sum-via-reducing, then combine into average-ish (sum / count, integer)
        long avg = xs.stream().collect(Collectors.teeing(
                Collectors.counting(),
                Collectors.reducing(0, (a, b) -> a + b),
                (count, sum) -> sum / count));
        Bmc.check(avg == 2L); // (1+2+3+4)/4 = 10/4 = 2
    }

    @BmcProof
    void toCollection_into_arraylist() {
        List<Integer> xs = List.of(3, 1, 2);
        ArrayList<Integer> out = xs.stream().collect(Collectors.toCollection(ArrayList::new));
        Bmc.check(out.size() == 3 && out.get(0) == 3 && out.get(2) == 2);
    }
}
