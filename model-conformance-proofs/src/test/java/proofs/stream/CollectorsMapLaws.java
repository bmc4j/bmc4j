package proofs.stream;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;

/**
 * Model proofs (axis 2) for the map-producing {@code Collectors} added over the bounded backing —
 * the merge-function {@code toMap(k, v, merge)}, {@code toUnmodifiableMap} (both arities), and
 * {@code toConcurrentMap} (plain + merge). Concrete expected results; keys chosen to exercise both
 * the distinct-key and the duplicate-key (merge / IllegalStateException) paths. The map-{@code
 * Supplier} overloads stay in the tail.
 */
class CollectorsMapLaws {

    @BmcProof
    void toMap_merge_resolves_duplicates() {
        List<Integer> xs = List.of(1, 2, 3, 4);
        // key = parity (0/1); value = the element; merge = sum -> {0: 2+4=6, 1: 1+3=4}
        Map<Integer, Integer> m = xs.stream().collect(
                Collectors.toMap(x -> x % 2, x -> x, (a, b) -> a + b));
        Bmc.check(m.size() == 2 && m.get(0) == 6 && m.get(1) == 4);
    }

    @BmcProof(unwind = 4)
    void toMap_merge_distinct_keys() {
        List<Integer> xs = List.of(1, 2, 3);
        Map<Integer, Integer> m = xs.stream().collect(
                Collectors.toMap(x -> x, x -> x * 10, (a, b) -> a));
        Bmc.check(m.size() == 3 && m.get(1) == 10 && m.get(3) == 30);
    }

    @BmcProof(unwind = 4)
    void toUnmodifiableMap_basic() {
        List<Integer> xs = List.of(1, 2, 3);
        Map<Integer, Integer> m = xs.stream().collect(
                Collectors.toUnmodifiableMap(x -> x, x -> x + 1));
        Bmc.check(m.size() == 3 && m.get(2) == 3);
    }

    @BmcProof(unwind = 8)
    void toUnmodifiableMap_merge() {
        List<Integer> xs = List.of(1, 2, 3, 4);
        Map<Integer, Integer> m = xs.stream().collect(
                Collectors.toUnmodifiableMap(x -> x % 2, x -> x, (a, b) -> a + b));
        Bmc.check(m.get(0) == 6 && m.get(1) == 4);
    }

    @BmcProof(unwind = 4)
    void toConcurrentMap_basic() {
        List<Integer> xs = List.of(1, 2, 3);
        Map<Integer, Integer> m = xs.stream().collect(
                Collectors.toConcurrentMap(x -> x, x -> x * 2));
        Bmc.check(m.size() == 3 && m.get(2) == 4 && m.get(3) == 6);
    }

    @BmcProof(unwind = 8)
    void toConcurrentMap_merge_resolves_duplicates() {
        List<Integer> xs = List.of(1, 2, 3, 4);
        Map<Integer, Integer> m = xs.stream().collect(
                Collectors.toConcurrentMap(x -> x % 2, x -> x, (a, b) -> a + b));
        Bmc.check(m.size() == 2 && m.get(0) == 6 && m.get(1) == 4);
    }
}
