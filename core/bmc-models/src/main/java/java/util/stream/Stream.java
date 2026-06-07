package java.util.stream;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;
import java.util.function.UnaryOperator;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcModelTail;

/**
 * Minimal BMC model of {@link java.util.stream.Stream}, evaluated <em>eagerly</em> over a bounded
 * backing list ({@link ListStream}). JBMC otherwise stubs the stream framework to nondet. Pipelines
 * unwind to the element count, so keep streams within the proof's {@code unwind}. Intermediate ops
 * ({@code map}/{@code filter}) call their functional-interface arguments, which bmc4j desugars from
 * lambdas — so {@code stream.filter(p).map(f).count()} analyses soundly.
 */
@BmcModelTail(reason = "the remaining lazy Stream surface (unordered sorted(), the infinite iterate(seed,next)/generate, mapToDouble/flatMapToDouble, mapMulti*, toArray/reduce(identity,accumulator,combiner)/collect(supplier,accumulator,combiner), and the lifecycle no-ops onClose/close/parallel/sequential) is out of scope for this minimal eager model; loud under JBMC (via the concrete ListStream impl)")
public interface Stream<T> {

    @BmcModelConforms("@BmcProof (proofs.stream StreamLaws)")
    Stream<T> filter(Predicate<? super T> predicate);

    @BmcModelConforms("@BmcProof (proofs.stream StreamLaws)")
    <R> Stream<R> map(Function<? super T, ? extends R> mapper);

    @BmcModelConforms("@BmcProof (proofs.stream StreamChainLaws)")
    <R> Stream<R> flatMap(Function<? super T, ? extends Stream<? extends R>> mapper);

    @BmcModelConforms("@BmcProof (proofs.stream StreamChainLaws)")
    Stream<T> distinct();

    @BmcModelConforms("@BmcProof (proofs.stream StreamChainLaws)")
    Stream<T> skip(long n);

    @BmcModelConforms("@BmcProof (proofs.stream StreamTailLaws)")
    Stream<T> limit(long maxSize);

    @BmcModelConforms("@BmcProof (proofs.stream StreamTailLaws)")
    Stream<T> takeWhile(Predicate<? super T> predicate);

    @BmcModelConforms("@BmcProof (proofs.stream StreamTailLaws)")
    Stream<T> dropWhile(Predicate<? super T> predicate);

    @BmcModelConforms("@BmcProof (proofs.stream StreamTailLaws)")
    Stream<T> peek(Consumer<? super T> action);

    @BmcModelConforms("@BmcProof (proofs.stream StreamTailLaws)")
    Stream<T> sorted(Comparator<? super T> comparator);

    @BmcModelConforms("@BmcProof (proofs.stream StreamLaws)")
    IntStream mapToInt(ToIntFunction<? super T> mapper);

    @BmcModelConforms("@BmcProof (proofs.stream StreamLaws)")
    LongStream mapToLong(ToLongFunction<? super T> mapper);

    @BmcModelConforms("@BmcProof (proofs.stream StreamTailLaws)")
    IntStream flatMapToInt(Function<? super T, ? extends IntStream> mapper);

    @BmcModelConforms("@BmcProof (proofs.stream StreamTailLaws)")
    LongStream flatMapToLong(Function<? super T, ? extends LongStream> mapper);

    @BmcModelConforms("@BmcProof (proofs.stream StreamLaws)")
    long count();

    @BmcModelConforms("@BmcProof (proofs.stream StreamLaws)")
    boolean anyMatch(Predicate<? super T> predicate);

    @BmcModelConforms("@BmcProof (proofs.stream StreamLaws)")
    boolean allMatch(Predicate<? super T> predicate);

    @BmcModelConforms("@BmcProof (proofs.stream StreamTailLaws)")
    boolean noneMatch(Predicate<? super T> predicate);

    @BmcModelConforms("@BmcProof (proofs.stream StreamTailLaws)")
    Optional<T> findFirst();

    @BmcModelConforms("@BmcProof (proofs.stream StreamTailLaws)")
    Optional<T> findAny();

    @BmcModelConforms("@BmcProof (proofs.stream StreamTailLaws)")
    Optional<T> min(Comparator<? super T> comparator);

    @BmcModelConforms("@BmcProof (proofs.stream StreamTailLaws)")
    Optional<T> max(Comparator<? super T> comparator);

    @BmcModelConforms("@BmcProof (proofs.stream StreamLaws)")
    void forEach(Consumer<? super T> action);

    @BmcModelConforms("@BmcProof (proofs.stream StreamLaws)")
    T reduce(T identity, BinaryOperator<T> accumulator);

    @BmcModelConforms("@BmcProof (proofs.stream StreamChainLaws)")
    Optional<T> reduce(BinaryOperator<T> accumulator);

    @BmcModelConforms("@BmcProof (proofs.stream StreamLaws)")
    <R, A> R collect(Collector<? super T, A, R> collector);

    @BmcModelConforms("@BmcProof (proofs.stream StreamLaws)")
    List<T> toList();

    @SafeVarargs
    @BmcModelConforms("@BmcProof (proofs.stream StreamLaws)")
    static <T> Stream<T> of(T... values) {
        return new ListStream<>(values);
    }

    @BmcModelConforms("@BmcProof (proofs.stream StreamTailLaws)")
    static <T> Stream<T> of(T t) {
        java.util.ArrayList<T> l = new java.util.ArrayList<>();
        l.add(t);
        return new ListStream<>(l);
    }

    @BmcModelConforms("@BmcProof (proofs.stream StreamTailLaws)")
    static <T> Stream<T> empty() {
        return new ListStream<>(new java.util.ArrayList<T>());
    }

    @BmcModelConforms("@BmcProof (proofs.stream StreamTailLaws)")
    static <T> Stream<T> ofNullable(T t) {
        java.util.ArrayList<T> l = new java.util.ArrayList<>();
        if (t != null) {
            l.add(t);
        }
        return new ListStream<>(l);
    }

    @BmcModelConforms("@BmcProof (proofs.stream StreamTailLaws)")
    static <T> Stream<T> concat(Stream<? extends T> a, Stream<? extends T> b) {
        java.util.ArrayList<T> l = new java.util.ArrayList<>();
        List<? extends T> la = a.toList();
        for (int i = 0; i < la.size(); i++) {
            l.add(la.get(i));
        }
        List<? extends T> lb = b.toList();
        for (int i = 0; i < lb.size(); i++) {
            l.add(lb.get(i));
        }
        return new ListStream<>(l);
    }

    /**
     * The FINITE 3-arg iterate (seed + {@code hasNext} predicate + {@code next}). Bounded and sound:
     * it terminates when {@code hasNext} fails, exactly like the JDK. The infinite 2-arg
     * {@code iterate(seed, next)} and {@code generate(supplier)} stay in the tail — they never
     * terminate, so a bounded eager producer would diverge from the JDK observable.
     */
    @BmcModelConforms("@BmcProof (proofs.stream StreamTailLaws)")
    static <T> Stream<T> iterate(T seed, Predicate<? super T> hasNext, UnaryOperator<T> next) {
        java.util.ArrayList<T> l = new java.util.ArrayList<>();
        T cur = seed;
        while (hasNext.test(cur)) {
            l.add(cur);
            cur = next.apply(cur);
        }
        return new ListStream<>(l);
    }
}
