package java.util.stream;

import java.util.List;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcModelTail;

/**
 * Minimal BMC model of {@link java.util.stream.Stream}, evaluated <em>eagerly</em> over a bounded
 * backing list ({@link ListStream}). JBMC otherwise stubs the stream framework to nondet. Pipelines
 * unwind to the element count, so keep streams within the proof's {@code unwind}. Intermediate ops
 * ({@code map}/{@code filter}) call their functional-interface arguments, which bmc4j desugars from
 * lambdas — so {@code stream.filter(p).map(f).count()} analyses soundly.
 */
@BmcModelConforms("eager bounded stream — @BmcProof (proofs.stream StreamLaws): filter/map/mapToInt/mapToLong/count/anyMatch/allMatch/forEach/reduce/collect/toList/of")
@BmcModelTail(reason = "the broad lazy Stream surface (sorted/distinct/limit/skip/peek/flatMap/findFirst/findAny/min/max/noneMatch/takeWhile/dropWhile/iterate/generate/concat/mapToObj/toArray/reduce-overloads/collect(supplier,accumulator,combiner)/…) is out of scope for this minimal eager model; loud under JBMC (via the concrete ListStream impl)")
public interface Stream<T> {

    Stream<T> filter(Predicate<? super T> predicate);

    <R> Stream<R> map(Function<? super T, ? extends R> mapper);

    IntStream mapToInt(ToIntFunction<? super T> mapper);

    LongStream mapToLong(ToLongFunction<? super T> mapper);

    long count();

    boolean anyMatch(Predicate<? super T> predicate);

    boolean allMatch(Predicate<? super T> predicate);

    void forEach(Consumer<? super T> action);

    T reduce(T identity, BinaryOperator<T> accumulator);

    <R, A> R collect(Collector<? super T, A, R> collector);

    List<T> toList();

    @SafeVarargs
    static <T> Stream<T> of(T... values) {
        return new ListStream<>(values);
    }
}
