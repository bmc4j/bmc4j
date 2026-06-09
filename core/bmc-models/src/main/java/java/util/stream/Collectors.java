package java.util.stream;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcUnmodelable;

/**
 * Minimal BMC model of {@link java.util.stream.Collectors}. {@code toList}/{@code toSet} are tags;
 * {@code toMap}/{@code groupingBy} additionally carry their mapper/classifier functions. All are
 * interpreted eagerly by {@link ListStream#collect} into the bounded collection models. With the
 * audit tail + loud-body synthesis, reaching an unmodeled collector now fails loudly under JBMC
 * naming the member, rather than silently falling back to a nondet stub.
 *
 * <p>The composite collectors ({@code collectingAndThen}/{@code teeing}/{@code filtering}/{@code
 * flatMapping}, downstream-driven {@code groupingBy}/{@code partitioningBy}) nest other collectors;
 * {@link ListStream#collect} recurses into the downstream over a fresh bounded sub-stream. The
 * comparator-driven {@code minBy}/{@code maxBy} carry an explicit {@link Comparator} (NOT
 * {@code naturalOrder()}, whose boxed {@code Comparable} dispatch is unsound under JBMC — proofs pass
 * a desugared int-lambda comparator). The {@code summing*}/{@code averaging*}/{@code summarizing*}
 * collectors stay in the tail (no double in the stream models, by convention).
 */
// The Collectors tail is fully enumerated below. The FP-result collectors (averaging*, summingDouble,
// summarizingDouble) hit the same Double total-order / FP-arithmetic wall the rest of the stream models
// avoid by convention. The map-Supplier-driven and concurrent collectors take an arbitrary user-supplied
// Map factory (groupingBy/toMap/toConcurrentMap with a Supplier) or a ConcurrentMap-typed result whose
// dynamic dispatch over the unbounded supplied container is out of scope for the bounded eager model — a
// fiction over a fixed HashMap would diverge from the user's container. Each is loud-if-reached under JBMC.
@BmcUnmodelable(member = "averagingInt(java.util.function.ToIntFunction)", reason = "averaging yields a double (sum/count as floating point) — the FP wall the stream models avoid by convention")
@BmcUnmodelable(member = "averagingLong(java.util.function.ToLongFunction)", reason = "averaging yields a double (sum/count as floating point) — the FP wall the stream models avoid by convention")
@BmcUnmodelable(member = "averagingDouble(java.util.function.ToDoubleFunction)", reason = "double extractor + double average — FP arithmetic the stream models avoid by convention")
@BmcUnmodelable(member = "summingDouble(java.util.function.ToDoubleFunction)", reason = "double extractor + double (compensated) summation — FP arithmetic the stream models avoid by convention")
@BmcUnmodelable(member = "summarizingDouble(java.util.function.ToDoubleFunction)", reason = "double extractor + DoubleSummaryStatistics (its getMin/getMax are the FP total-order wall) — FP out of scope")
@BmcUnmodelable(member = "groupingBy(java.util.function.Function,java.util.function.Supplier,java.util.stream.Collector)", reason = "map-Supplier-driven grouping: collects into an arbitrary user-supplied Map factory; the bounded eager model only targets a fixed HashMap, so a fiction over the supplied container would diverge")
@BmcUnmodelable(member = "toMap(java.util.function.Function,java.util.function.Function,java.util.function.BinaryOperator,java.util.function.Supplier)", reason = "map-Supplier-driven toMap: collects into an arbitrary user-supplied Map factory; out of scope for the bounded fixed-HashMap model")
@BmcUnmodelable(member = "toConcurrentMap(java.util.function.Function,java.util.function.Function,java.util.function.BinaryOperator,java.util.function.Supplier)", reason = "map-Supplier-driven toConcurrentMap: arbitrary user-supplied ConcurrentMap factory; out of scope for the bounded fixed-container model")
@BmcUnmodelable(member = "groupingByConcurrent(java.util.function.Function)", reason = "ConcurrentMap-result grouping: result is typed ConcurrentMap (the model's ConcurrentHashMap does not implement ConcurrentMap; a checkcast would trip JBMC) — out of scope; use groupingBy")
@BmcUnmodelable(member = "groupingByConcurrent(java.util.function.Function,java.util.stream.Collector)", reason = "ConcurrentMap-result downstream grouping — out of scope for the bounded model; use groupingBy(classifier, downstream)")
@BmcUnmodelable(member = "groupingByConcurrent(java.util.function.Function,java.util.function.Supplier,java.util.stream.Collector)", reason = "map-Supplier-driven ConcurrentMap grouping — arbitrary supplied container; out of scope for the bounded model")
public final class Collectors {

    private Collectors() {
    }

    @BmcModelConforms("@BmcProof (proofs.stream CollectorsLaws)")
    public static <T> Collector<T, ?, List<T>> toList() {
        return new Collector<>(Collector.TO_LIST);
    }

    @BmcModelConforms("@BmcProof (proofs.stream CollectorsLaws)")
    public static <T> Collector<T, ?, List<T>> toUnmodifiableList() {
        return new Collector<>(Collector.TO_LIST);
    }

    @BmcModelConforms("@BmcProof (proofs.stream CollectorsLaws)")
    public static <T> Collector<T, ?, Set<T>> toSet() {
        return new Collector<>(Collector.TO_SET);
    }

    @BmcModelConforms("@BmcProof (proofs.stream CollectorsTailLaws)")
    public static <T> Collector<T, ?, Set<T>> toUnmodifiableSet() {
        return new Collector<>(Collector.TO_SET);
    }

    /**
     * Counts the elements. Mirrors {@link java.util.stream.Collectors#counting()}: yields a
     * {@link Long}. {@link ListStream#collect} interprets the {@code COUNTING} tag.
     */
    @BmcModelConforms("@BmcProof (proofs.stream CollectorsTailLaws)")
    public static <T> Collector<T, ?, Long> counting() {
        return new Collector<>(Collector.COUNTING);
    }

    /**
     * Adapts {@code downstream} by applying {@code mapper} to each element first. Mirrors
     * {@link java.util.stream.Collectors#mapping(Function, Collector)}. {@link ListStream#collect}
     * maps each element through {@code mapper}, then collects the mapped stream with {@code downstream}
     * (which the model supports for {@code toList}/{@code toSet}/{@code counting}/{@code joining}).
     */
    @BmcModelConforms("@BmcProof (proofs.stream CollectorsTailLaws)")
    public static <T, U, A, R> Collector<T, ?, R> mapping(
            Function<? super T, ? extends U> mapper,
            Collector<? super U, A, R> downstream) {
        return new Collector<>(Collector.MAPPING, downstream, mapper);
    }

    /**
     * Collects into a bounded {@link java.util.HashMap} keyed by {@code keyMapper} with values from
     * {@code valueMapper}. Mirrors {@link java.util.stream.Collectors#toMap}: a duplicate key throws
     * {@link IllegalStateException} (the model's {@code collect} enforces this). Bounded by the
     * HashMap model's capacity.
     */
    @BmcModelConforms("@BmcProof (proofs.stream CollectorsLaws)")
    public static <T, K, V> Collector<T, ?, Map<K, V>> toMap(
            Function<? super T, ? extends K> keyMapper,
            Function<? super T, ? extends V> valueMapper) {
        return new Collector<>(Collector.TO_MAP, keyMapper, valueMapper);
    }

    /**
     * Groups elements into a bounded {@link java.util.HashMap} from each {@code classifier} result
     * to the {@link java.util.ArrayList} of elements mapping to it, in encounter order. Mirrors
     * {@link java.util.stream.Collectors#groupingBy(Function)}. Bounded by the collection models'
     * capacity.
     */
    @BmcModelConforms("@BmcProof (proofs.stream CollectorsLaws)")
    public static <T, K> Collector<T, ?, Map<K, List<T>>> groupingBy(
            Function<? super T, ? extends K> classifier) {
        return new Collector<>(Collector.GROUPING_BY, classifier, (Function<?, ?>) null);
    }

    /**
     * Concatenates the (CharSequence) elements in encounter order, with no delimiter. Mirrors
     * {@link java.util.stream.Collectors#joining()}. Sound under JBMC: {@link ListStream#collect}
     * builds the result with an explicit {@link StringBuilder} (JBMC models StringBuilder.append/
     * toString soundly) — no {@code invokedynamic} string concat, no regex/format machinery.
     */
    @BmcModelConforms("@BmcProof (proofs.stream CollectorsLaws)")
    public static Collector<CharSequence, ?, String> joining() {
        return new Collector<>(Collector.JOINING, "");
    }

    /**
     * Concatenates the (CharSequence) elements in encounter order, separated by {@code delimiter}.
     * Mirrors {@link java.util.stream.Collectors#joining(CharSequence)}. Sound under JBMC via the
     * explicit-StringBuilder path in {@link ListStream#collect} (no indy concat / regex).
     */
    @BmcModelConforms("@BmcProof (proofs.stream CollectorsLaws)")
    public static Collector<CharSequence, ?, String> joining(CharSequence delimiter) {
        return new Collector<>(Collector.JOINING, delimiter);
    }

    /**
     * Partitions elements into a bounded {@link java.util.HashMap} with exactly two keys —
     * {@code Boolean.TRUE} and {@code Boolean.FALSE} — each mapping to the {@link java.util.ArrayList}
     * of elements for which {@code predicate} is/ isn't satisfied, in encounter order. Mirrors
     * {@link java.util.stream.Collectors#partitioningBy(Predicate)}: BOTH keys are always present (the
     * partition is total), even when a bucket is empty. {@link ListStream#collect} interprets this.
     */
    @BmcModelConforms("@BmcProof (proofs.stream StreamChainLaws)")
    public static <T> Collector<T, ?, Map<Boolean, List<T>>> partitioningBy(
            Predicate<? super T> predicate) {
        return new Collector<>(Collector.PARTITIONING_BY, predicate);
    }

    // ---- tail-2 additions -----------------------------------------------------------------------

    /**
     * {@code toMap} with a {@code mergeFunction} resolving duplicate keys (no {@link
     * IllegalStateException}). Mirrors {@link java.util.stream.Collectors#toMap(Function, Function,
     * BinaryOperator)}: on a collision the existing and new values are merged. {@link
     * ListStream#collect} applies the merge in encounter order.
     */
    @BmcModelConforms("@BmcProof (proofs.stream CollectorsMapLaws)")
    public static <T, K, V> Collector<T, ?, Map<K, V>> toMap(
            Function<? super T, ? extends K> keyMapper,
            Function<? super T, ? extends V> valueMapper,
            BinaryOperator<V> mergeFunction) {
        return new Collector<>(Collector.TO_MAP_MERGE, keyMapper, valueMapper, mergeFunction);
    }

    /** Unmodifiable view of {@link #toMap(Function, Function)} (same eager bounded HashMap). */
    @BmcModelConforms("@BmcProof (proofs.stream CollectorsMapLaws)")
    public static <T, K, V> Collector<T, ?, Map<K, V>> toUnmodifiableMap(
            Function<? super T, ? extends K> keyMapper,
            Function<? super T, ? extends V> valueMapper) {
        return new Collector<>(Collector.TO_MAP, keyMapper, valueMapper);
    }

    /** Unmodifiable view of {@link #toMap(Function, Function, BinaryOperator)}. */
    @BmcModelConforms("@BmcProof (proofs.stream CollectorsMapLaws)")
    public static <T, K, V> Collector<T, ?, Map<K, V>> toUnmodifiableMap(
            Function<? super T, ? extends K> keyMapper,
            Function<? super T, ? extends V> valueMapper,
            BinaryOperator<V> mergeFunction) {
        return new Collector<>(Collector.TO_MAP_MERGE, keyMapper, valueMapper, mergeFunction);
    }

    /**
     * Collects into a bounded {@link java.util.concurrent.ConcurrentHashMap}. Mirrors {@link
     * java.util.stream.Collectors#toConcurrentMap(Function, Function)}: a duplicate key throws
     * {@link IllegalStateException}. Under the model's sequential semantics this is observably the
     * same as {@code toMap} but with a concurrent target.
     *
     * <p><b>Return type is {@code Map}, not {@code ConcurrentMap}</b> (the real signature's type): the
     * model's {@link java.util.concurrent.ConcurrentHashMap} {@code extends HashMap} but does not
     * {@code implement ConcurrentMap}, so a {@code checkcast → ConcurrentMap} on the collected result
     * would trip JBMC's dynamic-cast check (a false REFUTE, the documented interface-signature
     * artifact). Declaring {@code Map} avoids the spurious cast; the runtime container is still a
     * {@code ConcurrentHashMap}, observably identical. The audit gate matches by name+params
     * (return-agnostic), so this still pins the real {@code toConcurrentMap} member.
     */
    @BmcModelConforms("@BmcProof (proofs.stream CollectorsMapLaws)")
    public static <T, K, V> Collector<T, ?, Map<K, V>> toConcurrentMap(
            Function<? super T, ? extends K> keyMapper,
            Function<? super T, ? extends V> valueMapper) {
        return new Collector<>(Collector.TO_CONCURRENT_MAP, keyMapper, valueMapper);
    }

    /** {@code toConcurrentMap} with a {@code mergeFunction} resolving duplicate keys (return type {@code Map}; see the no-merge overload). */
    @BmcModelConforms("@BmcProof (proofs.stream CollectorsMapLaws)")
    public static <T, K, V> Collector<T, ?, Map<K, V>> toConcurrentMap(
            Function<? super T, ? extends K> keyMapper,
            Function<? super T, ? extends V> valueMapper,
            BinaryOperator<V> mergeFunction) {
        return new Collector<>(Collector.TO_CONCURRENT_MAP_MERGE, keyMapper, valueMapper, mergeFunction);
    }

    /**
     * Groups by {@code classifier}, then collects each group with {@code downstream}. Mirrors {@link
     * java.util.stream.Collectors#groupingBy(Function, Collector)}. {@link ListStream#collect} builds
     * the per-key element lists in encounter order, then collects each with the downstream.
     */
    @BmcModelConforms("@BmcProof (proofs.stream CollectorsDownstreamLaws)")
    public static <T, K, A, D> Collector<T, ?, Map<K, D>> groupingBy(
            Function<? super T, ? extends K> classifier,
            Collector<? super T, A, D> downstream) {
        return new Collector<>(Collector.GROUPING_BY_DOWNSTREAM, (Function<?, ?>) classifier, downstream);
    }

    /**
     * Partitions by {@code predicate}, then collects each (TRUE/FALSE) bucket with {@code downstream}.
     * Mirrors {@link java.util.stream.Collectors#partitioningBy(Predicate, Collector)}: BOTH keys are
     * always present (total partition).
     */
    @BmcModelConforms("@BmcProof (proofs.stream CollectorsDownstreamLaws)")
    public static <T, A, D> Collector<T, ?, Map<Boolean, D>> partitioningBy(
            Predicate<? super T> predicate,
            Collector<? super T, A, D> downstream) {
        return new Collector<>(Collector.PARTITIONING_BY_DOWNSTREAM, (Predicate<?>) predicate, downstream);
    }

    /**
     * Reduces the elements with {@code op}, no identity. Mirrors {@link
     * java.util.stream.Collectors#reducing(BinaryOperator)}: yields an {@link Optional} (empty for the
     * empty stream).
     */
    @BmcModelConforms("@BmcProof (proofs.stream CollectorsReducingLaws)")
    public static <T> Collector<T, ?, Optional<T>> reducing(BinaryOperator<T> op) {
        return new Collector<>(Collector.REDUCING, (BinaryOperator<?>) op);
    }

    /**
     * Reduces with {@code identity} + {@code op}. Mirrors {@link
     * java.util.stream.Collectors#reducing(Object, BinaryOperator)}: yields the (non-Optional) reduced
     * value, {@code identity} for the empty stream.
     */
    @BmcModelConforms("@BmcProof (proofs.stream CollectorsReducingLaws)")
    public static <T> Collector<T, ?, T> reducing(T identity, BinaryOperator<T> op) {
        return new Collector<>(Collector.REDUCING, (Object) identity, (Function<?, ?>) null, (BinaryOperator<?>) op);
    }

    /**
     * Maps each element with {@code mapper} then reduces with {@code identity} + {@code op}. Mirrors
     * {@link java.util.stream.Collectors#reducing(Object, Function, BinaryOperator)}.
     */
    @BmcModelConforms("@BmcProof (proofs.stream CollectorsReducingLaws)")
    public static <T, U> Collector<T, ?, U> reducing(
            U identity, Function<? super T, ? extends U> mapper, BinaryOperator<U> op) {
        return new Collector<>(Collector.REDUCING, (Object) identity, (Function<?, ?>) mapper, (BinaryOperator<?>) op);
    }

    /**
     * Collects with {@code downstream}, then applies {@code finisher} to the result. Mirrors {@link
     * java.util.stream.Collectors#collectingAndThen(Collector, Function)}.
     */
    @BmcModelConforms("@BmcProof (proofs.stream CollectorsDownstreamLaws)")
    public static <T, A, R, RR> Collector<T, ?, RR> collectingAndThen(
            Collector<T, A, R> downstream, Function<R, RR> finisher) {
        return new Collector<>(Collector.COLLECTING_AND_THEN, downstream, (Function<?, ?>) finisher, true);
    }

    /**
     * Keeps only elements satisfying {@code predicate}, feeding them to {@code downstream}. Mirrors
     * {@link java.util.stream.Collectors#filtering(Predicate, Collector)} — distinct from {@code
     * Stream.filter} in that excluded elements still reach the collector's account (e.g. a counting
     * downstream counts only the kept ones, but the grouping key is computed first).
     */
    @BmcModelConforms("@BmcProof (proofs.stream CollectorsDownstreamLaws)")
    public static <T, A, R> Collector<T, ?, R> filtering(
            Predicate<? super T> predicate, Collector<? super T, A, R> downstream) {
        return new Collector<>(Collector.FILTERING, (Predicate<?>) predicate, downstream);
    }

    /**
     * Maps each element to a {@link Stream} via {@code mapper} and flattens, feeding the elements to
     * {@code downstream}. Mirrors {@link java.util.stream.Collectors#flatMapping(Function, Collector)}.
     */
    @BmcModelConforms("@BmcProof (proofs.stream CollectorsDownstreamLaws)")
    public static <T, U, A, R> Collector<T, ?, R> flatMapping(
            Function<? super T, ? extends Stream<? extends U>> mapper,
            Collector<? super U, A, R> downstream) {
        return new Collector<>(Collector.FLAT_MAPPING, (Function<?, ?>) mapper, downstream);
    }

    /**
     * The minimum element by {@code comparator}. Mirrors {@link
     * java.util.stream.Collectors#minBy(Comparator)}: yields an {@link Optional} (empty for the empty
     * stream). Pass an explicit comparator (NOT {@code naturalOrder()} — boxed {@code Comparable}
     * dispatch is unsound under JBMC).
     */
    @BmcModelConforms("@BmcProof (proofs.stream CollectorsReducingLaws)")
    public static <T> Collector<T, ?, Optional<T>> minBy(Comparator<? super T> comparator) {
        return new Collector<>(Collector.MIN_BY, (Comparator<?>) comparator);
    }

    /** The maximum element by {@code comparator}; mirrors {@link java.util.stream.Collectors#maxBy(Comparator)}. */
    @BmcModelConforms("@BmcProof (proofs.stream CollectorsReducingLaws)")
    public static <T> Collector<T, ?, Optional<T>> maxBy(Comparator<? super T> comparator) {
        return new Collector<>(Collector.MAX_BY, (Comparator<?>) comparator);
    }

    /**
     * Collects with both {@code downstream1} and {@code downstream2}, then merges the two results with
     * {@code merger}. Mirrors {@link java.util.stream.Collectors#teeing(Collector, Collector,
     * BiFunction)}.
     */
    @BmcModelConforms("@BmcProof (proofs.stream CollectorsDownstreamLaws)")
    public static <T, R1, R2, R> Collector<T, ?, R> teeing(
            Collector<? super T, ?, R1> downstream1,
            Collector<? super T, ?, R2> downstream2,
            BiFunction<? super R1, ? super R2, R> merger) {
        return new Collector<>(Collector.TEEING, downstream1, downstream2, (BiFunction<?, ?, ?>) merger);
    }

    /**
     * Collects into the {@link java.util.Collection} produced by {@code collectionFactory}. Mirrors
     * {@link java.util.stream.Collectors#toCollection(Supplier)}. The factory supplies a bounded
     * collection model (e.g. {@code ArrayList::new}); {@link ListStream#collect} adds each element.
     */
    @BmcModelConforms("@BmcProof (proofs.stream CollectorsDownstreamLaws)")
    public static <T, C extends java.util.Collection<T>> Collector<T, ?, C> toCollection(
            Supplier<C> collectionFactory) {
        return new Collector<>(Collector.TO_COLLECTION, (Supplier<?>) collectionFactory);
    }

    // ---- integer-valued summation / summary (sound: integer accumulation, no FP) ----------------

    /**
     * Sums the {@code int} values produced by {@code mapper}. Mirrors {@link
     * java.util.stream.Collectors#summingInt(ToIntFunction)}: yields a boxed {@link Integer}, {@code 0}
     * for the empty stream. Sound under JBMC — pure {@code int} accumulation, no floating point.
     */
    @BmcModelConforms("@BmcProof (proofs.stream CollectorsSummingLaws)")
    public static <T> Collector<T, ?, Integer> summingInt(ToIntFunction<? super T> mapper) {
        return new Collector<>(Collector.SUMMING_INT, (ToIntFunction<?>) mapper);
    }

    /**
     * Sums the {@code long} values produced by {@code mapper}. Mirrors {@link
     * java.util.stream.Collectors#summingLong(ToLongFunction)}: yields a boxed {@link Long}, {@code 0L}
     * for the empty stream. Sound under JBMC — pure {@code long} accumulation, no floating point.
     */
    @BmcModelConforms("@BmcProof (proofs.stream CollectorsSummingLaws)")
    public static <T> Collector<T, ?, Long> summingLong(ToLongFunction<? super T> mapper) {
        return new Collector<>(Collector.SUMMING_LONG, (ToLongFunction<?>) mapper);
    }

    /**
     * Accumulates count/sum/min/max of the {@code int} values produced by {@code mapper} into a bounded
     * {@link java.util.IntSummaryStatistics}. Mirrors {@link
     * java.util.stream.Collectors#summarizingInt(ToIntFunction)}. Sound — the int summary accumulator is
     * fully modeled (integer min/max; {@code getAverage}'s {@code sum/count} is the one sound division).
     */
    @BmcModelConforms("@BmcProof (proofs.stream CollectorsSummingLaws)")
    public static <T> Collector<T, ?, java.util.IntSummaryStatistics> summarizingInt(
            ToIntFunction<? super T> mapper) {
        return new Collector<>(Collector.SUMMARIZING_INT, (ToIntFunction<?>) mapper);
    }

    /**
     * Accumulates count/sum/min/max of the {@code long} values produced by {@code mapper} into a bounded
     * {@link java.util.LongSummaryStatistics}. Mirrors {@link
     * java.util.stream.Collectors#summarizingLong(ToLongFunction)}. Sound — integer accumulation.
     */
    @BmcModelConforms("@BmcProof (proofs.stream CollectorsSummingLaws)")
    public static <T> Collector<T, ?, java.util.LongSummaryStatistics> summarizingLong(
            ToLongFunction<? super T> mapper) {
        return new Collector<>(Collector.SUMMARIZING_LONG, (ToLongFunction<?>) mapper);
    }

    /**
     * Concatenates the (CharSequence) elements in encounter order with {@code delimiter} between them,
     * wrapped in {@code prefix} … {@code suffix}. Mirrors {@link
     * java.util.stream.Collectors#joining(CharSequence, CharSequence, CharSequence)}. Sound under JBMC
     * via the explicit-StringBuilder path in {@link ListStream#collect} (no invokedynamic concat).
     */
    @BmcModelConforms("@BmcProof (proofs.stream CollectorsSummingLaws)")
    public static Collector<CharSequence, ?, String> joining(
            CharSequence delimiter, CharSequence prefix, CharSequence suffix) {
        return new Collector<>(Collector.JOINING_PREFIX_SUFFIX, delimiter, prefix, suffix);
    }
}
