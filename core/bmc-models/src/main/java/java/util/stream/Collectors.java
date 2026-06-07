package java.util.stream;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcModelTail;

/**
 * Minimal BMC model of {@link java.util.stream.Collectors}. {@code toList}/{@code toSet} are tags;
 * {@code toMap}/{@code groupingBy} additionally carry their mapper/classifier functions. All are
 * interpreted eagerly by {@link ListStream#collect} into the bounded collection models. With the
 * audit tail + loud-body synthesis, reaching an unmodeled collector now fails loudly under JBMC
 * naming the member, rather than silently falling back to a nondet stub.
 */
@BmcModelTail(reason = "the remaining Collectors surface (summing*/averaging*/summarizing* — need double; reducing/filtering/flatMapping/collectingAndThen/teeing/toCollection, the comparator-driven minBy/maxBy, the multi-arg toMap/groupingBy/joining(prefix,suffix) and concurrent variants groupingByConcurrent/toConcurrentMap) is out of scope for the minimal eager model; loud under JBMC")
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
        return new Collector<>(Collector.GROUPING_BY, classifier, null);
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
}
