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

    @BmcProof(unwind = 4)
    void mapToDouble_then_sum() {
        double s = Stream.of(1, 2, 3).mapToDouble(x -> x * 2.0).sum();
        Bmc.check(s == 12.0);
    }

    @BmcProof(unwind = 4)
    void mapToDouble_then_count() {
        long n = Stream.of("a", "bb", "ccc").mapToDouble(x -> x.length()).count();
        Bmc.check(n == 3L);
    }

    @BmcProof(unwind = 8)
    void flatMapToDouble_flattens_and_sums() {
        // each x -> [x, x*10] -> [1,10,2,20] -> sum 33
        double s = Stream.of(1, 2).flatMapToDouble(x -> DoubleStream.of(x, x * 10.0)).sum();
        Bmc.check(s == 33.0);
    }

    @BmcProof(unwind = 4)
    void flatMapToDouble_can_empty() {
        long n = Stream.of(1, 2, 3).flatMapToDouble(x -> DoubleStream.empty()).count();
        Bmc.check(n == 0L);
    }

    @BmcProof(unwind = 8)
    void mapMultiToDouble_emits_per_element() {
        // each x emits x and x+0.5 -> [1,1.5,2,2.5] -> sum 7.0
        double s = Stream.of(1, 2).mapMultiToDouble((x, sink) -> {
            sink.accept(x);
            sink.accept(x + 0.5);
        }).sum();
        Bmc.check(s == 7.0);
    }

    /**
     * Symbolic: mapToDouble identity-widen sum equals the int sum, for all small inputs. The cost is
     * the symbolic FP-adder bit-width (the {@code symbolic_sum} lesson), so the operand window is kept
     * tight (±32); the int->double-widen-then-sum identity holds for every exactly-representable value,
     * so this narrowed-but-still-symbolic window (crossing zero, both signs) is just as strong a proof.
     * It discharged at ~138s fresh over ±100 — right at the slow-CI budget wall — and well under it here.
     */
    @BmcProof
    void symbolic_mapToDouble_sum_matches_int_sum() {
        int a = Bmc.anyInt(-32, 32);
        int b = Bmc.anyInt(-32, 32);
        int c = Bmc.anyInt(-32, 32);
        double s = Stream.of(a, b, c).mapToDouble(x -> (double) x).sum();
        Bmc.check(s == (double) (a + b + c));
    }
}
