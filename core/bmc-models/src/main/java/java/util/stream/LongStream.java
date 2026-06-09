package java.util.stream;

import java.util.LongSummaryStatistics;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.function.BiConsumer;
import java.util.function.LongBinaryOperator;
import java.util.function.LongConsumer;
import java.util.function.LongFunction;
import java.util.function.LongPredicate;
import java.util.function.LongToDoubleFunction;
import java.util.function.LongToIntFunction;
import java.util.function.LongUnaryOperator;
import java.util.function.ObjLongConsumer;
import java.util.function.Supplier;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcUnmodelable;

/** Minimal BMC model of {@link java.util.stream.LongStream}, eager over a bounded {@code long[]}. */
// The LongStream tail is fully enumerated. The infinite producers (generate/iterate(seed,next)) never
// terminate; the primitive mapMulti drives a nested LongMapMultiConsumer SAM whose dispatch is out of
// scope; builder()/iterator()/spliterator() are lazy/virtual; the BaseStream lifecycle members carry no
// model on this sequential eager interface. Each is loud-if-reached under JBMC.
@BmcUnmodelable(member = "generate(java.util.function.LongSupplier)", reason = "infinite producer — never terminates; a bounded eager model would diverge from the JDK observable")
@BmcUnmodelable(member = "iterate(long,java.util.function.LongUnaryOperator)", reason = "the 2-arg infinite iterate(seed, next) — never terminates; use the bounded 3-arg iterate(seed, hasNext, next), which IS modeled")
@BmcUnmodelable(member = "mapMulti(java.util.stream.LongStream$LongMapMultiConsumer)", reason = "primitive mapMulti drives a nested LongMapMultiConsumer SAM whose virtual dispatch is out of scope for the eager array model")
@BmcUnmodelable(member = "builder()", reason = "lazy LongStream.Builder accumulation is out of scope for the eager array-backed model")
@BmcUnmodelable(member = "iterator()", reason = "virtual PrimitiveIterator.OfLong dispatch is out of scope for the eager array model")
@BmcUnmodelable(member = "spliterator()", reason = "Spliterator.OfLong (parallel-decomposition) dispatch is out of scope for the sequential eager model")
@BmcUnmodelable(member = "isParallel()", reason = "BaseStream lifecycle: parallelism flag — no model on the sequential eager interface; loud if reached")
@BmcUnmodelable(member = "parallel()", reason = "true-parallel execution is out of scope for the sequential eager model")
@BmcUnmodelable(member = "sequential()", reason = "BaseStream lifecycle no-op — no model on the eager interface; loud if reached")
@BmcUnmodelable(member = "unordered()", reason = "BaseStream lifecycle no-op (ordering hint) — no model on the eager interface; loud if reached")
@BmcUnmodelable(member = "onClose(java.lang.Runnable)", reason = "BaseStream close-handler registration — no model on the eager interface; loud if reached")
@BmcUnmodelable(member = "close()", reason = "BaseStream/AutoCloseable lifecycle no-op — no model on the eager interface; loud if reached")
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

    @BmcModelConforms("@BmcProof (proofs.stream LongStreamDoubleBridgeLaws)")
    DoubleStream asDoubleStream();

    @BmcModelConforms("@BmcProof (proofs.stream LongStreamDoubleBridgeLaws)")
    DoubleStream mapToDouble(LongToDoubleFunction mapper);

    @BmcModelConforms("@BmcProof (proofs.stream LongStreamDoubleBridgeLaws)")
    OptionalDouble average();

    @BmcModelConforms("@BmcProof (proofs.stream LongStreamDoubleBridgeLaws)")
    LongSummaryStatistics summaryStatistics();

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
