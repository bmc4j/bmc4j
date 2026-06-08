package proofs.stream;

import java.util.stream.DoubleStream;
import java.util.stream.Stream;
import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;

/**
 * Model proofs (axis 2) for the {@code Stream<T>} -> double bridge ops pulled off the tail:
 * {@code mapToDouble(ToDoubleFunction)}, {@code flatMapToDouble(Function)}, and
 * {@code mapMultiToDouble(BiConsumer)}. Each drains the object stream by index into the bounded
 * {@code DoubleArrayStream} (concrete iteration, never a virtual dispatch through the DoubleStream
 * interface), so the sound double-arithmetic surface (sum/count) verifies.
 *
 * <p>NOT here: {@code Stream.sorted()} (natural order) — it is a loud {@code @BmcUnmodelable}
 * (Comparable.compareTo dispatch on the unconstrained element type is unsound under JBMC), so reaching
 * it is an honest UNKNOWN, not a law to assert. {@code sorted(Comparator)} is covered by StreamTailLaws.
 */
class StreamDoubleBridgeLaws {

    @BmcProof
    void mapToDouble_then_sum() {
        double s = Stream.of(1, 2, 3).mapToDouble(x -> x * 2.0).sum();
        Bmc.check(s == 12.0);
    }

    @BmcProof
    void mapToDouble_then_count() {
        long n = Stream.of("a", "bb", "ccc").mapToDouble(x -> x.length()).count();
        Bmc.check(n == 3L);
    }

    @BmcProof
    void flatMapToDouble_flattens_and_sums() {
        // each x -> [x, x*10] -> [1,10,2,20] -> sum 33
        double s = Stream.of(1, 2).flatMapToDouble(x -> DoubleStream.of(x, x * 10.0)).sum();
        Bmc.check(s == 33.0);
    }

    @BmcProof
    void flatMapToDouble_can_empty() {
        long n = Stream.of(1, 2, 3).flatMapToDouble(x -> DoubleStream.empty()).count();
        Bmc.check(n == 0L);
    }

    @BmcProof
    void mapMultiToDouble_emits_per_element() {
        // each x emits x and x+0.5 -> [1,1.5,2,2.5] -> sum 7.0
        double s = Stream.of(1, 2).mapMultiToDouble((x, sink) -> {
            sink.accept(x);
            sink.accept(x + 0.5);
        }).sum();
        Bmc.check(s == 7.0);
    }

    /** Symbolic: mapToDouble identity-widen sum equals the int sum, for all small inputs. */
    @BmcProof
    void symbolic_mapToDouble_sum_matches_int_sum() {
        int a = Bmc.anyInt(-100, 100);
        int b = Bmc.anyInt(-100, 100);
        int c = Bmc.anyInt(-100, 100);
        double s = Stream.of(a, b, c).mapToDouble(x -> (double) x).sum();
        Bmc.check(s == (double) (a + b + c));
    }
}
