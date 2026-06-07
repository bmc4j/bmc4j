package java.util.stream;

import java.util.List;
import java.util.Optional;
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
@BmcModelTail(reason = "the broad lazy Stream surface (sorted/limit/peek/findFirst/findAny/min/max/noneMatch/takeWhile/dropWhile/iterate/generate/concat/mapToObj/toArray/reduce(identity,accumulator,combiner)/collect(supplier,accumulator,combiner)/…) is out of scope for this minimal eager model; loud under JBMC (via the concrete ListStream impl)")
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

    @BmcModelConforms("@BmcProof (proofs.stream StreamLaws)")
    IntStream mapToInt(ToIntFunction<? super T> mapper);

    @BmcModelConforms("@BmcProof (proofs.stream StreamLaws)")
    LongStream mapToLong(ToLongFunction<? super T> mapper);

    @BmcModelConforms("@BmcProof (proofs.stream StreamLaws)")
    long count();

    @BmcModelConforms("@BmcProof (proofs.stream StreamLaws)")
    boolean anyMatch(Predicate<? super T> predicate);

    @BmcModelConforms("@BmcProof (proofs.stream StreamLaws)")
    boolean allMatch(Predicate<? super T> predicate);

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
}
