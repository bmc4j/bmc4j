package proofs.stream;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;

/**
 * Model proofs (axis 2) for the Stream tail chain ops added over the bounded backing —
 * {@code flatMap}, {@code reduce(BinaryOperator)}, {@code distinct}, {@code skip}, {@code mapToObj}
 * — plus the {@code partitioningBy} collector. These pin the actual chain-shaped patterns (e.g.
 * {@code stream().map().filter().flatMap().reduce()}), not the members in isolation, with concrete
 * expected results so a wrong pipeline result is caught. Lambdas are desugared by bmc4j's own layer;
 * streams unwind to the element count, so the lists are kept tiny to stay tractable.
 */
class StreamChainLaws {

    @BmcProof(unwind = 8)
    void flatMap_flattens() {
        List<Integer> xs = List.of(1, 2, 3);
        // each x -> [x, x*10]; flatten -> [1,10,2,20,3,30], count 6
        List<Integer> out = xs.stream()
                .flatMap(x -> Stream.of(x, x * 10))
                .toList();
        Bmc.check(out.size() == 6 && out.get(0) == 1 && out.get(1) == 10 && out.get(5) == 30);
    }

    @BmcProof(unwind = 8)
    void map_filter_flatMap_reduce_chain() {
        List<Integer> xs = List.of(1, 2, 3, 4);
        // *2 -> 2,4,6,8 ; keep >4 -> 6,8 ; flatMap each -> [n,n] ; reduce + -> 28
        Optional<Integer> sum = xs.stream()
                .map(x -> x * 2)
                .filter(x -> x > 4)
                .flatMap(x -> Stream.of(x, x))
                .reduce((a, b) -> a + b);
        // each kept element (6, 8) duplicated by flatMap -> 6,6,8,8 -> sum 28
        Bmc.check(sum.isPresent() && sum.get().intValue() == 28);
    }

    @BmcProof(unwind = 4)
    void reduce_no_identity_empty_is_empty() {
        // filter everything out -> empty -> reduce returns Optional.empty()
        List<Integer> xs = List.of(1, 2, 3);
        Optional<Integer> r = xs.stream().filter(x -> x > 100).reduce((a, b) -> a + b);
        Bmc.check(r.isEmpty());
    }

    @BmcProof(unwind = 4)
    void reduce_no_identity_product() {
        List<Integer> xs = List.of(2, 3, 4);
        Optional<Integer> p = xs.stream().reduce((a, b) -> a * b);
        Bmc.check(p.isPresent() && p.get() == 24);
    }

    @BmcProof(unwind = 8)
    void distinct_dedups() {
        // <=4-arg List.of (explicit overload) so JBMC keeps the concrete ArrayList type and
        // devirtualizes .stream(); 5+ args route to varargs (a loud "no body for callee").
        List<Integer> xs = List.of(1, 2, 2, 3);
        List<Integer> out = xs.stream().distinct().toList();
        // encounter order, first occurrence kept: [1,2,3]
        Bmc.check(out.size() == 3 && out.get(0) == 1 && out.get(1) == 2 && out.get(2) == 3);
    }

    @BmcProof(unwind = 8)
    void skip_drops_prefix() {
        List<Integer> xs = List.of(10, 20, 30, 40);
        List<Integer> out = xs.stream().skip(2).toList();
        Bmc.check(out.size() == 2 && out.get(0) == 30 && out.get(1) == 40);
    }

    @BmcProof(unwind = 16)
    void skip_then_count() {
        Bmc.check(IntStream.range(0, 10).boxed().skip(7).count() == 3L);
    }

    @BmcProof
    void mapToObj_boxes_via_function() {
        // IntStream 1..3 -> map each to (n+1) as Integer -> list [2,3,4]
        List<Integer> out = IntStream.of(1, 2, 3)
                .mapToObj(n -> Integer.valueOf(n + 1))
                .toList();
        Bmc.check(out.size() == 3 && out.get(0) == 2 && out.get(2) == 4);
    }

    @BmcProof(unwind = 8)
    void partitioningBy_splits_total() {
        List<Integer> xs = List.of(1, 2, 3, 4);
        Map<Boolean, List<Integer>> parts = xs.stream()
                .collect(Collectors.partitioningBy(x -> x % 2 == 0));
        // both keys present; evens {2,4}, odds {1,3}
        Bmc.check(parts.get(Boolean.TRUE).size() == 2);
        Bmc.check(parts.get(Boolean.FALSE).size() == 2);
        Bmc.check(parts.get(Boolean.TRUE).get(0) == 2 && parts.get(Boolean.TRUE).get(1) == 4);
        Bmc.check(parts.get(Boolean.FALSE).get(0) == 1 && parts.get(Boolean.FALSE).get(1) == 3);
    }

    @BmcProof(unwind = 4)
    void partitioningBy_empty_bucket_present() {
        List<Integer> xs = List.of(1, 3, 5);
        Map<Boolean, List<Integer>> parts = xs.stream()
                .collect(Collectors.partitioningBy(x -> x % 2 == 0));
        // no evens, but the TRUE key is still present with an empty list (total partition)
        Bmc.check(parts.get(Boolean.TRUE).isEmpty());
        Bmc.check(parts.get(Boolean.FALSE).size() == 3);
    }
}
