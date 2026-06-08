package proofs.stream;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;

/**
 * Model proofs (axis 2) for the reduction {@code Collectors} added over the bounded backing —
 * {@code reducing} (all three arities) and the comparator-driven {@code minBy}/{@code maxBy}. The
 * comparators are explicit desugared int-lambdas (NOT {@code Comparator.naturalOrder()}, whose boxed
 * {@code Comparable} dispatch is unsound under JBMC — the same pattern {@link StreamTailLaws} uses).
 */
class CollectorsReducingLaws {

    // explicit desugared comparator over the unboxed int — never Comparator.naturalOrder()
    private static final Comparator<Integer> ASC = (a, b) -> a.intValue() - b.intValue();

    @BmcProof
    void reducing_no_identity_present() {
        List<Integer> xs = List.of(2, 3, 4);
        Optional<Integer> p = xs.stream().collect(Collectors.reducing((a, b) -> a * b));
        Bmc.check(p.isPresent() && p.get() == 24);
    }

    @BmcProof
    void reducing_no_identity_empty() {
        List<Integer> xs = List.of(1, 2, 3);
        Optional<Integer> p = xs.stream().filter(x -> x > 100).collect(Collectors.reducing((a, b) -> a + b));
        Bmc.check(p.isEmpty());
    }

    @BmcProof
    void reducing_with_identity() {
        List<Integer> xs = List.of(1, 2, 3, 4);
        int sum = xs.stream().collect(Collectors.reducing(0, (a, b) -> a + b));
        Bmc.check(sum == 10);
    }

    @BmcProof
    void reducing_with_identity_empty_returns_identity() {
        List<Integer> xs = List.of(1, 2, 3);
        int r = xs.stream().filter(x -> x > 100).collect(Collectors.reducing(7, (a, b) -> a + b));
        Bmc.check(r == 7);
    }

    @BmcProof
    void reducing_with_mapper_and_identity() {
        List<Integer> xs = List.of(1, 2, 3);
        // map each *10, then sum -> 60
        int sum = xs.stream().collect(Collectors.reducing(0, x -> x * 10, (a, b) -> a + b));
        Bmc.check(sum == 60);
    }

    @BmcProof
    void minBy_picks_smallest() {
        List<Integer> xs = List.of(3, 1, 2);
        Optional<Integer> m = xs.stream().collect(Collectors.minBy(ASC));
        Bmc.check(m.isPresent() && m.get() == 1);
    }

    @BmcProof
    void maxBy_picks_largest() {
        List<Integer> xs = List.of(3, 1, 2);
        Optional<Integer> m = xs.stream().collect(Collectors.maxBy(ASC));
        Bmc.check(m.isPresent() && m.get() == 3);
    }

    @BmcProof
    void minBy_empty_is_empty() {
        List<Integer> xs = List.of(1, 2);
        Optional<Integer> m = xs.stream().filter(x -> x > 100).collect(Collectors.minBy(ASC));
        Bmc.check(m.isEmpty());
    }
}
