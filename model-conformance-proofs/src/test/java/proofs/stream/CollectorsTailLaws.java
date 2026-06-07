package proofs.stream;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;

/**
 * Model proofs (axis 2) for the {@code Collectors} tail collectors added over the bounded backing —
 * {@code counting}, {@code mapping(mapper, downstream)}, and {@code toUnmodifiableSet}. Chain-shaped
 * over the existing {@code collect} interpreter; concrete expected results. The double-valued
 * collectors (summing / averaging / summarizing) deliberately stay in the tail (no double in the
 * stream models, by convention).
 */
class CollectorsTailLaws {

    @BmcProof
    void counting_counts() {
        List<Integer> xs = List.of(1, 2, 3, 4);
        long n = xs.stream().collect(Collectors.counting());
        Bmc.check(n == 4L);
    }

    @BmcProof
    void counting_after_filter() {
        List<Integer> xs = List.of(1, 2, 3, 4);
        long n = xs.stream().filter(x -> x % 2 == 0).collect(Collectors.counting());
        Bmc.check(n == 2L);
    }

    @BmcProof
    void mapping_to_list() {
        List<Integer> xs = List.of(1, 2, 3);
        // map each *10, collect to list
        List<Integer> out = xs.stream().collect(Collectors.mapping(x -> x * 10, Collectors.toList()));
        Bmc.check(out.size() == 3 && out.get(0) == 10 && out.get(2) == 30);
    }

    @BmcProof
    void mapping_to_counting() {
        List<Integer> xs = List.of(1, 2, 3);
        long n = xs.stream().collect(Collectors.mapping(x -> x + 1, Collectors.counting()));
        Bmc.check(n == 3L);
    }

    @BmcProof
    void toUnmodifiableSet_dedups() {
        List<Integer> xs = List.of(1, 2, 2, 3);
        Set<Integer> s = xs.stream().collect(Collectors.toUnmodifiableSet());
        Bmc.check(s.size() == 3 && s.contains(1) && s.contains(2) && s.contains(3));
    }
}
