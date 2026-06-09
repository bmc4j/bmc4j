package java.util.stream;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;
import java.util.function.UnaryOperator;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcUnmodelable;

import static org.bmc4j.analysis.BmcUnmodelledReached.fail;

/**
 * Minimal BMC model of {@link java.util.stream.Stream}, evaluated <em>eagerly</em> over a bounded
 * backing list ({@link ListStream}). JBMC otherwise stubs the stream framework to nondet. Pipelines
 * unwind to the element count, so keep streams within the proof's {@code unwind}. Intermediate ops
 * ({@code map}/{@code filter}) call their functional-interface arguments, which bmc4j desugars from
 * lambdas — so {@code stream.filter(p).map(f).count()} analyses soundly.
 */
// The Stream tail is fully enumerated. The infinite producers (generate/iterate(seed,next)) never
// terminate, so a bounded eager producer would diverge from the JDK observable. The lazy builder() and
// the virtual iterator()/spliterator() dispatch are out of scope for the eager array model. The
// BaseStream lifecycle members (onClose/close/isParallel/parallel/sequential/unordered) carry no model
// on this sequential eager interface; reaching any is loud-if-reached under JBMC.
@BmcUnmodelable(member = "generate(java.util.function.Supplier)", reason = "infinite producer — never terminates; a bounded eager model would diverge from the JDK observable")
@BmcUnmodelable(member = "iterate(java.lang.Object,java.util.function.UnaryOperator)", reason = "the 2-arg infinite iterate(seed, next) — never terminates; use the bounded 3-arg iterate(seed, hasNext, next), which IS modeled")
@BmcUnmodelable(member = "builder()", reason = "lazy Stream.Builder accumulation is out of scope for the eager array-backed model")
@BmcUnmodelable(member = "iterator()", reason = "virtual Iterator dispatch over the stream is out of scope for the eager array model")
@BmcUnmodelable(member = "spliterator()", reason = "Spliterator (parallel-decomposition) dispatch is out of scope for the sequential eager model")
@BmcUnmodelable(member = "isParallel()", reason = "BaseStream lifecycle: parallelism flag — no model on the sequential eager interface; loud if reached")
@BmcUnmodelable(member = "parallel()", reason = "true-parallel execution is out of scope for the sequential eager model")
@BmcUnmodelable(member = "sequential()", reason = "BaseStream lifecycle no-op — no model on the eager interface; loud if reached")
@BmcUnmodelable(member = "unordered()", reason = "BaseStream lifecycle no-op (ordering hint) — no model on the eager interface; loud if reached")
@BmcUnmodelable(member = "onClose(java.lang.Runnable)", reason = "BaseStream close-handler registration — no model on the eager interface; loud if reached")
@BmcUnmodelable(member = "close()", reason = "BaseStream/AutoCloseable lifecycle no-op — no model on the eager interface; loud if reached")
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

    @BmcModelConforms("@BmcProof (proofs.stream StreamDoubleBridgeLaws)")
    DoubleStream mapToDouble(ToDoubleFunction<? super T> mapper);

    @BmcModelConforms("@BmcProof (proofs.stream StreamTailLaws)")
    IntStream flatMapToInt(Function<? super T, ? extends IntStream> mapper);

    @BmcModelConforms("@BmcProof (proofs.stream StreamTailLaws)")
    LongStream flatMapToLong(Function<? super T, ? extends LongStream> mapper);

    @BmcModelConforms("@BmcProof (proofs.stream StreamDoubleBridgeLaws)")
    DoubleStream flatMapToDouble(Function<? super T, ? extends DoubleStream> mapper);

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

    @BmcModelConforms("@BmcProof (proofs.stream StreamTail2Laws)")
    Object[] toArray();

    @BmcModelConforms("@BmcProof (proofs.stream StreamTail2Laws)")
    <A> A[] toArray(IntFunction<A[]> generator);

    @BmcModelConforms("@BmcProof (proofs.stream StreamTail2Laws)")
    void forEachOrdered(Consumer<? super T> action);

    @BmcModelConforms("@BmcProof (proofs.stream StreamTail2Laws)")
    <R> R collect(Supplier<R> supplier, BiConsumer<R, ? super T> accumulator, BiConsumer<R, R> combiner);

    @BmcModelConforms("@BmcProof (proofs.stream StreamTail2Laws)")
    <U> U reduce(U identity, BiFunction<U, ? super T, U> accumulator, BinaryOperator<U> combiner);

    @BmcModelConforms("@BmcProof (proofs.stream StreamTail2Laws)")
    <R> Stream<R> mapMulti(BiConsumer<? super T, ? super Consumer<R>> mapper);

    @BmcModelConforms("@BmcProof (proofs.stream StreamTail2Laws)")
    IntStream mapMultiToInt(BiConsumer<? super T, ? super java.util.function.IntConsumer> mapper);

    @BmcModelConforms("@BmcProof (proofs.stream StreamTail2Laws)")
    LongStream mapMultiToLong(BiConsumer<? super T, ? super java.util.function.LongConsumer> mapper);

    @BmcModelConforms("@BmcProof (proofs.stream StreamDoubleBridgeLaws)")
    DoubleStream mapMultiToDouble(BiConsumer<? super T, ? super java.util.function.DoubleConsumer> mapper);

    // The NATURAL-ORDER sorted() (no comparator) routes through the elements' Comparable.compareTo via a
    // boxed/dynamic dispatch JBMC cannot resolve soundly (the #169 devirt family for the unconstrained T),
    // so it is a loud @BmcUnmodelable rather than a quiet fiction. The COMPARATOR sorted(Comparator) above
    // IS modeled — the comparator is an explicit, desugared SAM the model can drive by index.
    @BmcUnmodelable(reason = "Stream.sorted() (natural order) dispatches through the elements' Comparable.compareTo on the unconstrained T — a boxed/dynamic comparison JBMC cannot devirtualize soundly (#169 family); a fiction would diverge from the JDK ordering. Use sorted(Comparator), which IS modeled.")
    default Stream<T> sorted() {
        throw fail("bmc4j: unmodelled member java.util.stream.Stream.sorted() — natural-order Comparable.compareTo dispatch on the unconstrained element type is unsound under JBMC; use sorted(Comparator)");
    }

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
        // Cast each interface-typed param to the sole final implementor before reading it, so the
        // drain is an invokevirtual on the concrete ListStream rather than an invokeinterface on
        // Stream — the latter is the kotlinc-version-fragile devirtualization that produced the #169
        // family of false REFUTEDs ("no body for callee") under symbolic inputs / old-kotlin legs.
        List<? extends T> la = ((ListStream<? extends T>) a).toList();
        for (int i = 0; i < la.size(); i++) {
            l.add(la.get(i));
        }
        List<? extends T> lb = ((ListStream<? extends T>) b).toList();
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
