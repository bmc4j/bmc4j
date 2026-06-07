package java.util.stream;

import java.util.function.LongFunction;
import java.util.function.LongPredicate;
import java.util.function.LongUnaryOperator;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcModelTail;

/** Minimal BMC model of {@link java.util.stream.LongStream}, eager over a bounded {@code long[]}. */
@BmcModelTail(reason = "the broad lazy LongStream surface (sorted/distinct/limit/skip/peek/min/max/average/reduce-overloads/asDoubleStream/toArray/collect/summaryStatistics/iterate/generate/concat/…) is out of scope for this minimal eager model; loud under JBMC via the concrete impl")
public interface LongStream {

    @BmcModelConforms("@BmcProof (proofs.stream)")
    LongStream map(LongUnaryOperator op);

    @BmcModelConforms("@BmcProof (proofs.stream StreamChainLaws)")
    <U> Stream<U> mapToObj(LongFunction<? extends U> mapper);

    @BmcModelConforms("@BmcProof (proofs.stream)")
    LongStream filter(LongPredicate predicate);

    @BmcModelConforms("@BmcProof (proofs.stream)")
    long sum();

    @BmcModelConforms("@BmcProof (proofs.stream)")
    long count();

    @BmcModelConforms("@BmcProof (proofs.stream)")
    boolean anyMatch(LongPredicate predicate);

    @BmcModelConforms("@BmcProof (proofs.stream)")
    Stream<Long> boxed();

    @BmcModelConforms("@BmcProof (proofs.stream)")
    static LongStream range(long startInclusive, long endExclusive) {
        LongArrayStream s = new LongArrayStream();
        for (long i = startInclusive; i < endExclusive; i++) {
            s.add(i);
        }
        return s;
    }

    @BmcModelConforms("@BmcProof (proofs.stream)")
    static LongStream rangeClosed(long startInclusive, long endInclusive) {
        LongArrayStream s = new LongArrayStream();
        for (long i = startInclusive; i <= endInclusive; i++) {
            s.add(i);
        }
        return s;
    }

    @BmcModelConforms("@BmcProof (proofs.stream)")
    static LongStream of(long... values) {
        LongArrayStream s = new LongArrayStream();
        for (long v : values) {
            s.add(v);
        }
        return s;
    }
}
