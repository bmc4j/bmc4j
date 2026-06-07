package java.util.stream;

import java.util.function.IntPredicate;
import java.util.function.IntUnaryOperator;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcModelTail;

/** Minimal BMC model of {@link java.util.stream.IntStream}, eager over a bounded {@code int[]}. */
@BmcModelConforms("eager bounded int stream — @BmcProof (proofs.stream): of/range/map/filter/sum/count")
@BmcModelTail(reason = "the broad lazy IntStream surface (rangeClosed/sorted/distinct/limit/skip/peek/min/max/average/reduce-overloads/mapToObj/mapToLong/asLongStream/boxed/toArray/collect/summaryStatistics/iterate/generate/concat/…) is out of scope for this minimal eager model; loud under JBMC via the concrete impl")
public interface IntStream {

    IntStream map(IntUnaryOperator op);

    IntStream filter(IntPredicate predicate);

    int sum();

    long count();

    boolean anyMatch(IntPredicate predicate);

    Stream<Integer> boxed();

    static IntStream range(int startInclusive, int endExclusive) {
        IntArrayStream s = new IntArrayStream();
        for (int i = startInclusive; i < endExclusive; i++) {
            s.add(i);
        }
        return s;
    }

    static IntStream rangeClosed(int startInclusive, int endInclusive) {
        IntArrayStream s = new IntArrayStream();
        for (int i = startInclusive; i <= endInclusive; i++) {
            s.add(i);
        }
        return s;
    }

    static IntStream of(int... values) {
        IntArrayStream s = new IntArrayStream();
        for (int v : values) {
            s.add(v);
        }
        return s;
    }
}
