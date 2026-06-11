package proofs.stream;

import java.util.ArrayList;
import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.stream.Collectors;
import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;

/**
 * Model proofs (axis 2) for the integer-valued summation/summary collectors and the prefix/suffix
 * joining drained off the {@code Collectors} tail: {@code summingInt}, {@code summingLong},
 * {@code summarizingInt}, {@code summarizingLong}, and {@code joining(delimiter, prefix, suffix)}.
 * Sound under JBMC — pure integer accumulation (no floating point) and explicit-StringBuilder concat
 * (no invokedynamic). Shapes mirror what JBMC devirtualizes: a concrete bounded source list and simple
 * non-capturing lambdas folded through {@link java.util.stream.ListStream#collect}.
 *
 * <p>Range-reduced: tiny element counts, small int/long values, so the symbolic legs stay tractable.
 */
class CollectorsSummingLaws {

    private static List<Integer> ints(int a, int b, int c) {
        ArrayList<Integer> xs = new ArrayList<>();
        xs.add(a);
        xs.add(b);
        xs.add(c);
        return xs;
    }

    @BmcProof(unwind = 4)
    void summingInt_concrete() {
        int sum = ints(1, 2, 3).stream().collect(Collectors.summingInt(x -> x));
        Bmc.check(sum == 6);
    }

    @BmcProof(unwind = 4)
    void summingInt_with_mapper() {
        int sum = ints(1, 2, 3).stream().collect(Collectors.summingInt(x -> x * 2));
        Bmc.check(sum == 12);
    }

    @BmcProof(unwind = 4)
    void summingInt_symbolic_is_sum() {
        // Two symbolic ints in a tight range; the collector sum must equal the direct int sum.
        int a = Bmc.anyInt(0, 100);
        int b = Bmc.anyInt(0, 100);
        ArrayList<Integer> xs = new ArrayList<>();
        xs.add(a);
        xs.add(b);
        int sum = xs.stream().collect(Collectors.summingInt(x -> x));
        Bmc.check(sum == a + b);
    }

    @BmcProof
    void summingInt_empty_is_zero() {
        ArrayList<Integer> xs = new ArrayList<>();
        int sum = xs.stream().collect(Collectors.summingInt(x -> x));
        Bmc.check(sum == 0);
    }

    @BmcProof(unwind = 4)
    void summingLong_concrete() {
        long sum = ints(10, 20, 30).stream().collect(Collectors.summingLong(x -> (long) x));
        Bmc.check(sum == 60L);
    }

    @BmcProof(unwind = 4)
    void summarizingInt_count_sum_min_max() {
        IntSummaryStatistics stats = ints(3, 1, 2).stream().collect(Collectors.summarizingInt(x -> x));
        Bmc.check(stats.getCount() == 3L && stats.getSum() == 6L
                && stats.getMin() == 1 && stats.getMax() == 3);
    }

    @BmcProof(unwind = 4)
    void summarizingLong_count_sum_min_max() {
        LongSummaryStatistics stats =
                ints(5, 9, 7).stream().collect(Collectors.summarizingLong(x -> (long) x));
        Bmc.check(stats.getCount() == 3L && stats.getSum() == 21L
                && stats.getMin() == 5L && stats.getMax() == 9L);
    }

    private static List<CharSequence> strs(String a, String b, String c) {
        ArrayList<CharSequence> xs = new ArrayList<>();
        xs.add(a);
        xs.add(b);
        xs.add(c);
        return xs;
    }

    @BmcProof(unwind = 8)
    void joining_prefix_suffix_wraps() {
        String s = strs("a", "b", "c").stream().collect(Collectors.joining(",", "[", "]"));
        Bmc.check(s.equals("[a,b,c]"));
    }

    @BmcProof
    void joining_prefix_suffix_empty_is_just_wrapper() {
        ArrayList<CharSequence> xs = new ArrayList<>();
        String s = xs.stream().collect(Collectors.joining(",", "[", "]"));
        Bmc.check(s.equals("[]"));
    }
}
