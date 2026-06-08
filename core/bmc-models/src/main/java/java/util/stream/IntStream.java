package java.util.stream;

import java.util.IntSummaryStatistics;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.BiConsumer;
import java.util.function.IntBinaryOperator;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import java.util.function.IntToDoubleFunction;
import java.util.function.IntToLongFunction;
import java.util.function.IntUnaryOperator;
import java.util.function.ObjIntConsumer;
import java.util.function.Supplier;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcModelTail;

/** Minimal BMC model of {@link java.util.stream.IntStream}, eager over a bounded {@code int[]}. */
@BmcModelTail(reason = "the remaining IntStream surface (the infinite iterate(seed,next)/generate; mapMulti (nested IntMapMultiConsumer SAM); builder/iterator/spliterator; lifecycle no-ops onClose/close/isParallel/parallel/sequential/unordered) is out of scope for this minimal eager model; loud under JBMC via the concrete impl")
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

    @BmcModelConforms("@BmcProof (proofs.stream IntStreamTailLaws)")
    boolean allMatch(IntPredicate predicate);

    @BmcModelConforms("@BmcProof (proofs.stream IntStreamTailLaws)")
    boolean noneMatch(IntPredicate predicate);

    @BmcModelConforms("@BmcProof (proofs.stream IntStreamTailLaws)")
    IntStream limit(long maxSize);

    @BmcModelConforms("@BmcProof (proofs.stream IntStreamTailLaws)")
    IntStream skip(long n);

    @BmcModelConforms("@BmcProof (proofs.stream IntStreamTailLaws)")
    IntStream takeWhile(IntPredicate predicate);

    @BmcModelConforms("@BmcProof (proofs.stream IntStreamTailLaws)")
    IntStream dropWhile(IntPredicate predicate);

    @BmcModelConforms("@BmcProof (proofs.stream IntStreamTailLaws)")
    IntStream distinct();

    @BmcModelConforms("@BmcProof (proofs.stream IntStreamTailLaws)")
    IntStream sorted();

    @BmcModelConforms("@BmcProof (proofs.stream IntStreamTailLaws)")
    IntStream peek(IntConsumer action);

    @BmcModelConforms("@BmcProof (proofs.stream IntStreamTailLaws)")
    void forEach(IntConsumer action);

    @BmcModelConforms("@BmcProof (proofs.stream IntStreamTailLaws)")
    int reduce(int identity, IntBinaryOperator op);

    @BmcModelConforms("@BmcProof (proofs.stream IntStreamOptionalLaws)")
    OptionalInt reduce(IntBinaryOperator op);

    @BmcModelConforms("@BmcProof (proofs.stream IntStreamOptionalLaws)")
    OptionalInt min();

    @BmcModelConforms("@BmcProof (proofs.stream IntStreamOptionalLaws)")
    OptionalInt max();

    @BmcModelConforms("@BmcProof (proofs.stream IntStreamOptionalLaws)")
    OptionalInt findFirst();

    @BmcModelConforms("@BmcProof (proofs.stream IntStreamOptionalLaws)")
    OptionalInt findAny();

    @BmcModelConforms("@BmcProof (proofs.stream IntStreamTailLaws)")
    int[] toArray();

    @BmcModelConforms("@BmcProof (proofs.stream IntStreamTailLaws)")
    LongStream mapToLong(IntToLongFunction mapper);

    @BmcModelConforms("@BmcProof (proofs.stream IntStreamTailLaws)")
    LongStream asLongStream();

    @BmcModelConforms("@BmcProof (proofs.stream IntStreamDoubleBridgeLaws)")
    DoubleStream asDoubleStream();

    @BmcModelConforms("@BmcProof (proofs.stream IntStreamDoubleBridgeLaws)")
    DoubleStream mapToDouble(IntToDoubleFunction mapper);

    @BmcModelConforms("@BmcProof (proofs.stream IntStreamDoubleBridgeLaws)")
    OptionalDouble average();

    @BmcModelConforms("@BmcProof (proofs.stream IntStreamDoubleBridgeLaws)")
    IntSummaryStatistics summaryStatistics();

    @BmcModelConforms("@BmcProof (proofs.stream)")
    Stream<Integer> boxed();

    @BmcModelConforms("@BmcProof (proofs.stream IntStreamTail2Laws)")
    IntStream flatMap(IntFunction<? extends IntStream> mapper);

    @BmcModelConforms("@BmcProof (proofs.stream IntStreamTail2Laws)")
    void forEachOrdered(IntConsumer action);

    @BmcModelConforms("@BmcProof (proofs.stream IntStreamTail2Laws)")
    <R> R collect(Supplier<R> supplier, ObjIntConsumer<R> accumulator, BiConsumer<R, R> combiner);

    @BmcModelConforms("@BmcProof (proofs.stream IntStreamTailLaws)")
    static IntStream empty() {
        return new IntArrayStream();
    }

    @BmcModelConforms("@BmcProof (proofs.stream IntStreamTailLaws)")
    static IntStream of(int value) {
        IntArrayStream s = new IntArrayStream();
        s.add(value);
        return s;
    }

    @BmcModelConforms("@BmcProof (proofs.stream IntStreamTailLaws)")
    static IntStream concat(IntStream a, IntStream b) {
        IntArrayStream s = new IntArrayStream();
        // Cast to the sole final implementor before draining: an invokevirtual on the concrete
        // IntArrayStream, not an invokeinterface on IntStream. The interface dispatch is the
        // kotlinc-version-fragile devirtualization behind the #169 false-REFUTED family.
        int[] aa = ((IntArrayStream) a).toArray();
        for (int i = 0; i < aa.length; i++) {
            s.add(aa[i]);
        }
        int[] bb = ((IntArrayStream) b).toArray();
        for (int i = 0; i < bb.length; i++) {
            s.add(bb[i]);
        }
        return s;
    }

    /**
     * The FINITE 3-arg iterate (seed + {@code hasNext} predicate + {@code next}). Bounded and sound;
     * the infinite 2-arg {@code iterate}/{@code generate} stay in the tail (would never terminate).
     */
    @BmcModelConforms("@BmcProof (proofs.stream IntStreamTailLaws)")
    static IntStream iterate(int seed, IntPredicate hasNext, IntUnaryOperator next) {
        IntArrayStream s = new IntArrayStream();
        int cur = seed;
        while (hasNext.test(cur)) {
            s.add(cur);
            cur = next.applyAsInt(cur);
        }
        return s;
    }

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
