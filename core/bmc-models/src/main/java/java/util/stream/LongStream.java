package java.util.stream;

import java.util.OptionalLong;
import java.util.function.BiConsumer;
import java.util.function.LongBinaryOperator;
import java.util.function.LongConsumer;
import java.util.function.LongFunction;
import java.util.function.LongPredicate;
import java.util.function.LongToIntFunction;
import java.util.function.LongUnaryOperator;
import java.util.function.ObjLongConsumer;
import java.util.function.Supplier;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcModelTail;

/** Minimal BMC model of {@link java.util.stream.LongStream}, eager over a bounded {@code long[]}. */
@BmcModelTail(reason = "the remaining LongStream surface (average/summaryStatistics — need the unmodeled OptionalDouble/LongSummaryStatistics + double; asDoubleStream/mapToDouble; the infinite iterate(seed,next)/generate; mapMulti (nested LongMapMultiConsumer SAM); builder/iterator/spliterator; lifecycle no-ops) is out of scope for this minimal eager model; loud under JBMC via the concrete impl")
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

    @BmcModelConforms("@BmcProof (proofs.stream LongStreamTailLaws)")
    boolean allMatch(LongPredicate predicate);

    @BmcModelConforms("@BmcProof (proofs.stream LongStreamTailLaws)")
    boolean noneMatch(LongPredicate predicate);

    @BmcModelConforms("@BmcProof (proofs.stream LongStreamTailLaws)")
    LongStream limit(long maxSize);

    @BmcModelConforms("@BmcProof (proofs.stream LongStreamTailLaws)")
    LongStream skip(long n);

    @BmcModelConforms("@BmcProof (proofs.stream LongStreamTailLaws)")
    LongStream takeWhile(LongPredicate predicate);

    @BmcModelConforms("@BmcProof (proofs.stream LongStreamTailLaws)")
    LongStream dropWhile(LongPredicate predicate);

    @BmcModelConforms("@BmcProof (proofs.stream LongStreamTailLaws)")
    LongStream distinct();

    @BmcModelConforms("@BmcProof (proofs.stream LongStreamTailLaws)")
    LongStream sorted();

    @BmcModelConforms("@BmcProof (proofs.stream LongStreamTailLaws)")
    LongStream peek(LongConsumer action);

    @BmcModelConforms("@BmcProof (proofs.stream LongStreamTailLaws)")
    void forEach(LongConsumer action);

    @BmcModelConforms("@BmcProof (proofs.stream LongStreamTailLaws)")
    long reduce(long identity, LongBinaryOperator op);

    @BmcModelConforms("@BmcProof (proofs.stream LongStreamOptionalLaws)")
    OptionalLong reduce(LongBinaryOperator op);

    @BmcModelConforms("@BmcProof (proofs.stream LongStreamOptionalLaws)")
    OptionalLong min();

    @BmcModelConforms("@BmcProof (proofs.stream LongStreamOptionalLaws)")
    OptionalLong max();

    @BmcModelConforms("@BmcProof (proofs.stream LongStreamOptionalLaws)")
    OptionalLong findFirst();

    @BmcModelConforms("@BmcProof (proofs.stream LongStreamOptionalLaws)")
    OptionalLong findAny();

    @BmcModelConforms("@BmcProof (proofs.stream LongStreamTailLaws)")
    long[] toArray();

    @BmcModelConforms("@BmcProof (proofs.stream LongStreamTailLaws)")
    IntStream mapToInt(LongToIntFunction mapper);

    @BmcModelConforms("@BmcProof (proofs.stream)")
    Stream<Long> boxed();

    @BmcModelConforms("@BmcProof (proofs.stream LongStreamTail2Laws)")
    LongStream flatMap(LongFunction<? extends LongStream> mapper);

    @BmcModelConforms("@BmcProof (proofs.stream LongStreamTail2Laws)")
    void forEachOrdered(LongConsumer action);

    @BmcModelConforms("@BmcProof (proofs.stream LongStreamTail2Laws)")
    <R> R collect(Supplier<R> supplier, ObjLongConsumer<R> accumulator, BiConsumer<R, R> combiner);

    @BmcModelConforms("@BmcProof (proofs.stream LongStreamTailLaws)")
    static LongStream empty() {
        return new LongArrayStream();
    }

    @BmcModelConforms("@BmcProof (proofs.stream LongStreamTailLaws)")
    static LongStream of(long value) {
        LongArrayStream s = new LongArrayStream();
        s.add(value);
        return s;
    }

    @BmcModelConforms("@BmcProof (proofs.stream LongStreamTailLaws)")
    static LongStream concat(LongStream a, LongStream b) {
        LongArrayStream s = new LongArrayStream();
        // Cast to the sole final implementor before draining: an invokevirtual on the concrete
        // LongArrayStream, not an invokeinterface on LongStream. The interface dispatch is the
        // kotlinc-version-fragile devirtualization behind the #169 false-REFUTED family.
        long[] aa = ((LongArrayStream) a).toArray();
        for (int i = 0; i < aa.length; i++) {
            s.add(aa[i]);
        }
        long[] bb = ((LongArrayStream) b).toArray();
        for (int i = 0; i < bb.length; i++) {
            s.add(bb[i]);
        }
        return s;
    }

    /**
     * The FINITE 3-arg iterate (seed + {@code hasNext} predicate + {@code next}). Bounded and sound;
     * the infinite 2-arg {@code iterate}/{@code generate} stay in the tail (would never terminate).
     */
    @BmcModelConforms("@BmcProof (proofs.stream LongStreamTailLaws)")
    static LongStream iterate(long seed, LongPredicate hasNext, LongUnaryOperator next) {
        LongArrayStream s = new LongArrayStream();
        long cur = seed;
        while (hasNext.test(cur)) {
            s.add(cur);
            cur = next.applyAsLong(cur);
        }
        return s;
    }

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
