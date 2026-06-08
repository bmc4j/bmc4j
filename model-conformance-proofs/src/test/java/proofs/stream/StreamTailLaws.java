package proofs.stream;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;

/**
 * Model proofs (axis 2) for the {@code Stream<T>} tail ops added over the bounded {@link
 * java.util.stream.Stream} backing — {@code limit}/{@code takeWhile}/{@code dropWhile}/{@code peek}/
 * {@code noneMatch}/{@code findFirst}/{@code findAny}, the comparator ops {@code sorted}/{@code min}/
 * {@code max}, the {@code flatMapToInt}/{@code flatMapToLong} primitive flatteners, and the static
 * factories {@code of(T)}/{@code empty}/{@code ofNullable}/{@code concat}/{@code iterate(3-arg)}.
 * Chain-shaped, concrete expected results so a wrong pipeline is caught; lists kept tiny (streams
 * unwind to the element count). Comparators use natural Integer order (the concrete tiny-list pattern
 * the Kotlin sortedWith proofs already validate under JBMC).
 */
class StreamTailLaws {

    @BmcProof
    void limit_truncates() {
        List<Integer> xs = List.of(1, 2, 3, 4);
        List<Integer> out = xs.stream().limit(2).toList();
        Bmc.check(out.size() == 2 && out.get(0) == 1 && out.get(1) == 2);
    }

    @BmcProof
    void limit_past_end_is_identity() {
        List<Integer> xs = List.of(1, 2);
        Bmc.check(xs.stream().limit(10).count() == 2L);
    }

    @BmcProof
    void takeWhile_stops_at_first_false() {
        List<Integer> xs = List.of(1, 2, 3, 1);
        // take while < 3 -> [1,2], stops at 3 even though a later 1 would pass
        List<Integer> out = xs.stream().takeWhile(x -> x < 3).toList();
        Bmc.check(out.size() == 2 && out.get(0) == 1 && out.get(1) == 2);
    }

    @BmcProof
    void dropWhile_drops_leading_run() {
        List<Integer> xs = List.of(1, 2, 3, 1);
        // drop while < 3 -> [3,1] (the trailing 1 is kept)
        List<Integer> out = xs.stream().dropWhile(x -> x < 3).toList();
        Bmc.check(out.size() == 2 && out.get(0) == 3 && out.get(1) == 1);
    }

    @BmcProof
    void peek_is_identity_on_elements() {
        List<Integer> xs = List.of(1, 2, 3);
        Bmc.check(xs.stream().peek(x -> { }).count() == 3L);
    }

    @BmcProof
    void noneMatch_true_when_none() {
        List<Integer> xs = List.of(1, 2, 3);
        Bmc.check(xs.stream().noneMatch(x -> x > 100));
    }

    @BmcProof
    void noneMatch_false_when_some() {
        List<Integer> xs = List.of(1, 2, 3);
        Bmc.check(!xs.stream().noneMatch(x -> x == 2));
    }

    @BmcProof
    void findFirst_returns_head() {
        List<Integer> xs = List.of(7, 8, 9);
        Optional<Integer> f = xs.stream().findFirst();
        Bmc.check(f.isPresent() && f.get() == 7);
    }

    @BmcProof
    void findFirst_empty_is_empty() {
        List<Integer> xs = List.of(1, 2, 3);
        Bmc.check(xs.stream().filter(x -> x > 100).findFirst().isEmpty());
    }

    @BmcProof
    void findAny_present_on_nonempty() {
        List<Integer> xs = List.of(5, 6);
        Bmc.check(xs.stream().findAny().isPresent());
    }

    // Comparators are explicit desugared lambdas over the unboxed int — NOT Comparator.naturalOrder()
    // (whose Comparable.compareTo dispatch on boxed Integers is unsound/refuted under JBMC). This is
    // the same pattern the Kotlin sortedWith proofs use (a desugared keySelector Comparator).
    private static final Comparator<Integer> ASC = (a, b) -> a.intValue() - b.intValue();

    @BmcProof
    void min_picks_smallest() {
        List<Integer> xs = List.of(3, 1, 2);
        Optional<Integer> m = xs.stream().min(ASC);
        Bmc.check(m.isPresent() && m.get() == 1);
    }

    @BmcProof
    void max_picks_largest() {
        List<Integer> xs = List.of(3, 1, 2);
        Optional<Integer> m = xs.stream().max(ASC);
        Bmc.check(m.isPresent() && m.get() == 3);
    }

    @BmcProof
    void min_empty_is_empty() {
        List<Integer> xs = List.of(1, 2);
        Bmc.check(xs.stream().filter(x -> x > 100).min(ASC).isEmpty());
    }

    @BmcProof
    void sorted_orders_ascending() {
        List<Integer> xs = List.of(3, 1, 2);
        List<Integer> out = xs.stream().sorted(ASC).toList();
        Bmc.check(out.size() == 3 && out.get(0) == 1 && out.get(1) == 2 && out.get(2) == 3);
    }

    @BmcProof
    void flatMapToInt_flattens_and_sums() {
        List<Integer> xs = List.of(1, 2, 3);
        // each x -> IntStream.of(x, x) -> [1,1,2,2,3,3] -> sum 12
        int sum = xs.stream().flatMapToInt(x -> IntStream.of(x, x)).sum();
        Bmc.check(sum == 12);
    }

    @BmcProof
    void flatMapToLong_flattens_and_sums() {
        List<Integer> xs = List.of(1, 2, 3);
        long sum = xs.stream().flatMapToLong(x -> LongStream.of(x, x)).sum();
        Bmc.check(sum == 12L);
    }

    @BmcProof
    void of_single_is_one_element() {
        Bmc.check(Stream.of("a").count() == 1L);
    }

    @BmcProof
    void empty_has_no_elements() {
        Bmc.check(Stream.empty().count() == 0L);
    }

    @BmcProof
    void ofNullable_null_is_empty() {
        Bmc.check(Stream.ofNullable(null).count() == 0L);
    }

    @BmcProof
    void ofNullable_value_is_singleton() {
        Bmc.check(Stream.ofNullable("x").count() == 1L);
    }

    @BmcProof
    void concat_appends() {
        Stream<Integer> a = Stream.of(1, 2);
        Stream<Integer> b = Stream.of(3, 4);
        List<Integer> out = Stream.concat(a, b).toList();
        Bmc.check(out.size() == 4 && out.get(0) == 1 && out.get(3) == 4);
    }

    @BmcProof
    void iterate_finite_terminates() {
        // seed 1, while <= 4, next *2 -> [1,2,4] (8 stops the predicate)
        List<Integer> out = Stream.iterate(1, x -> x <= 4, x -> x * 2).toList();
        Bmc.check(out.size() == 3 && out.get(0) == 1 && out.get(1) == 2 && out.get(2) == 4);
    }

    // ---- Devirtualization-robustness regression (interface-dispatch unsoundness family, #150/#157/
    // #164/#169). Stream.concat / Stream.flatMap read their Stream-typed arg/inner via the sole final
    // implementor (ListStream), NOT the Stream interface, so JBMC does not have to devirtualize an
    // invokeinterface that the kotlin-2.0.21 leg fails to bind ("no body for callee"). SYMBOLIC inputs
    // are essential: the pre-fix concrete concat_appends proof constant-folds the chain so the
    // interface dispatch resolves anyway — only symbolic operands keep the dispatch live and would
    // false-REFUTE on the old-kotlin/symbolic leg if concat reverted to the interface call.

    @BmcProof
    void symbolic_concat_appends() {
        int a = Bmc.anyInt(-100, 100);
        int b = Bmc.anyInt(-100, 100);
        int c = Bmc.anyInt(-100, 100);
        List<Integer> out = Stream.concat(Stream.of(a, b), Stream.of(c)).toList();
        Bmc.check(out.size() == 3 && out.get(0) == a && out.get(1) == b && out.get(2) == c);
    }

    @BmcProof
    void symbolic_flatMap_expands() {
        int a = Bmc.anyInt(-100, 100);
        int b = Bmc.anyInt(-100, 100);
        List<Integer> out = Stream.of(a, b).flatMap(x -> Stream.of(x, x + 1)).toList();
        Bmc.check(out.size() == 4 && out.get(0) == a && out.get(1) == a + 1
                && out.get(2) == b && out.get(3) == b + 1);
    }
}
