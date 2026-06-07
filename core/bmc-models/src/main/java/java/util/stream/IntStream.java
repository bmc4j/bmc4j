package java.util.stream;

import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import java.util.function.IntUnaryOperator;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcModelTail;

/** Minimal BMC model of {@link java.util.stream.IntStream}, eager over a bounded {@code int[]}. */
@BmcModelTail(reason = "the broad lazy IntStream surface (sorted/distinct/limit/skip/peek/min/max/average/reduce-overloads/mapToLong/asLongStream/toArray/collect/summaryStatistics/iterate/generate/concat/…) is out of scope for this minimal eager model; loud under JBMC via the concrete impl")
public interface IntStream {

    @BmcModelConforms("@BmcProof (proofs.stream)")
    IntStream map(IntUnaryOperator op);

    @BmcModelConforms("@BmcProof (proofs.stream StreamChainLaws)")
    <U> Stream<U> mapToObj(IntFunction<? extends U> mapper);

    @BmcModelConforms("@BmcProof (proofs.stream)")
    IntStream filter(IntPredicate predicate);

    @BmcModelConforms("@BmcProof (proofs.stream)")
    int sum();

    @BmcModelConforms("@BmcProof (proofs.stream)")
    long count();

    @BmcModelConforms("@BmcProof (proofs.stream)")
    boolean anyMatch(IntPredicate predicate);

    @BmcModelConforms("@BmcProof (proofs.stream)")
    Stream<Integer> boxed();

    @BmcModelConforms("@BmcProof (proofs.stream)")
    static IntStream range(int startInclusive, int endExclusive) {
        IntArrayStream s = new IntArrayStream();
        for (int i = startInclusive; i < endExclusive; i++) {
            s.add(i);
        }
        return s;
    }

    @BmcModelConforms("@BmcProof (proofs.stream)")
    static IntStream rangeClosed(int startInclusive, int endInclusive) {
        IntArrayStream s = new IntArrayStream();
        for (int i = startInclusive; i <= endInclusive; i++) {
            s.add(i);
        }
        return s;
    }

    @BmcModelConforms("@BmcProof (proofs.stream)")
    static IntStream of(int... values) {
        IntArrayStream s = new IntArrayStream();
        for (int v : values) {
            s.add(v);
        }
        return s;
    }
}
