package java.util.stream;

import java.util.Comparator;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;

/**
 * Minimal BMC model of {@link java.util.stream.Collector}. The real interface is a
 * supplier/accumulator/combiner/finisher bundle; here it is just a {@link #kind} tag identifying the
 * target reduction (plus the functions/comparators/downstreams the relevant collectors carry), which
 * {@link ListStream#collect} interprets eagerly. {@link Collectors} produces these.
 *
 * <p>Rather than one constructor per shape, this carries a flexible set of optional fields and a single
 * canonical constructor; each {@link Collectors} factory fills only the fields its {@link #kind} reads.
 * Unused fields are {@code null}. The field set is wide enough for the composite collectors
 * ({@code collectingAndThen}/{@code teeing}/{@code filtering}/{@code flatMapping}/downstream-driven
 * {@code groupingBy}/{@code partitioningBy}) which nest other collectors and finishers.
 */
public final class Collector<T, A, R> {

    static final int TO_LIST = 0;
    static final int TO_SET = 1;
    static final int TO_MAP = 2;
    static final int GROUPING_BY = 3;
    static final int JOINING = 4;
    static final int PARTITIONING_BY = 5;
    static final int COUNTING = 6;
    static final int MAPPING = 7;
    // tail-2 additions:
    static final int TO_MAP_MERGE = 8;          // keyFn, valueFn, mergeFn
    static final int TO_CONCURRENT_MAP = 9;     // keyFn, valueFn
    static final int TO_CONCURRENT_MAP_MERGE = 10; // keyFn, valueFn, mergeFn
    static final int GROUPING_BY_DOWNSTREAM = 11;  // keyFn (classifier), downstream
    static final int PARTITIONING_BY_DOWNSTREAM = 12; // predicate, downstream
    static final int REDUCING = 13;             // mergeFn (op); identityPresent? identity; keyFn (optional mapper)
    static final int COLLECTING_AND_THEN = 14;  // downstream, finisher
    static final int FILTERING = 15;            // predicate, downstream
    static final int FLAT_MAPPING = 16;         // keyFn (element -> Stream), downstream
    static final int MIN_BY = 17;               // comparator
    static final int MAX_BY = 18;               // comparator
    static final int TEEING = 19;               // downstream, downstream2, merger
    static final int TO_COLLECTION = 20;        // supplier
    static final int SUMMING_INT = 21;          // toIntFn
    static final int SUMMING_LONG = 22;         // toLongFn
    static final int SUMMARIZING_INT = 23;      // toIntFn
    static final int SUMMARIZING_LONG = 24;     // toLongFn
    static final int JOINING_PREFIX_SUFFIX = 25; // delimiter + prefix + suffix

    final int kind;

    /** TO_MAP/concurrent: key mapper. GROUPING_BY*: classifier. MAPPING/FLAT_MAPPING: per-element fn. REDUCING: optional element mapper. */
    final Function<?, ?> keyFn;

    /** TO_MAP/concurrent: value mapper. */
    final Function<?, ?> valueFn;

    /** TO_MAP_MERGE/concurrent-merge: merge function for duplicate keys. REDUCING: the reduction operator. */
    final BinaryOperator<?> mergeFn;

    /** REDUCING(identity, …): whether an identity/initial value is supplied (distinguishes the Optional-returning overload). */
    final boolean identityPresent;

    /** REDUCING(identity, …): the identity / initial value. */
    final Object identity;

    /** JOINING: delimiter inserted between elements (empty for {@code joining()}). */
    final CharSequence delimiter;

    /** PARTITIONING_BY / PARTITIONING_BY_DOWNSTREAM / FILTERING: the predicate splitting / selecting elements. */
    final Predicate<?> predicate;

    /** MIN_BY/MAX_BY: the comparator. */
    final Comparator<?> comparator;

    /** TO_COLLECTION: the container supplier. */
    final Supplier<?> supplier;

    /** COLLECTING_AND_THEN: the finisher applied to the downstream result. */
    final Function<?, ?> finisher;

    /** TEEING: the BiFunction merging the two downstream results. */
    final BiFunction<?, ?, ?> merger;

    /** MAPPING/COLLECTING_AND_THEN/FILTERING/FLAT_MAPPING/groupingBy-downstream/teeing: the nested downstream collector. */
    final Collector<?, ?, ?> downstream;

    /** TEEING: the second downstream collector. */
    final Collector<?, ?, ?> downstream2;

    /** SUMMING_INT/SUMMARIZING_INT: the int-valued extractor. */
    final ToIntFunction<?> toIntFn;

    /** SUMMING_LONG/SUMMARIZING_LONG: the long-valued extractor. */
    final ToLongFunction<?> toLongFn;

    /** JOINING_PREFIX_SUFFIX: the prefix prepended to the result. */
    final CharSequence prefix;

    /** JOINING_PREFIX_SUFFIX: the suffix appended to the result. */
    final CharSequence suffix;

    private Collector(int kind, Function<?, ?> keyFn, Function<?, ?> valueFn, BinaryOperator<?> mergeFn,
            boolean identityPresent, Object identity, CharSequence delimiter, Predicate<?> predicate,
            Comparator<?> comparator, Supplier<?> supplier, Function<?, ?> finisher,
            BiFunction<?, ?, ?> merger, Collector<?, ?, ?> downstream, Collector<?, ?, ?> downstream2,
            ToIntFunction<?> toIntFn, ToLongFunction<?> toLongFn, CharSequence prefix, CharSequence suffix) {
        this.kind = kind;
        this.keyFn = keyFn;
        this.valueFn = valueFn;
        this.mergeFn = mergeFn;
        this.identityPresent = identityPresent;
        this.identity = identity;
        this.delimiter = delimiter;
        this.predicate = predicate;
        this.comparator = comparator;
        this.supplier = supplier;
        this.finisher = finisher;
        this.merger = merger;
        this.downstream = downstream;
        this.downstream2 = downstream2;
        this.toIntFn = toIntFn;
        this.toLongFn = toLongFn;
        this.prefix = prefix;
        this.suffix = suffix;
    }

    Collector(int kind) {
        this(kind, null, null, null, false, null, "", null, null, null, null, null, null, null, null, null, "", "");
    }

    /** SUMMING_INT / SUMMARIZING_INT: an int-valued extractor. */
    Collector(int kind, ToIntFunction<?> toIntFn) {
        this(kind, null, null, null, false, null, "", null, null, null, null, null, null, null, toIntFn, null, "", "");
    }

    /** SUMMING_LONG / SUMMARIZING_LONG: a long-valued extractor. */
    Collector(int kind, ToLongFunction<?> toLongFn) {
        this(kind, null, null, null, false, null, "", null, null, null, null, null, null, null, null, toLongFn, "", "");
    }

    /** JOINING_PREFIX_SUFFIX: delimiter + prefix + suffix. */
    Collector(int kind, CharSequence delimiter, CharSequence prefix, CharSequence suffix) {
        this(kind, null, null, null, false, null, delimiter, null, null, null, null, null, null, null, null, null, prefix, suffix);
    }

    Collector(int kind, Function<?, ?> keyFn, Function<?, ?> valueFn) {
        this(kind, keyFn, valueFn, null, false, null, "", null, null, null, null, null, null, null, null, null, "", "");
    }

    Collector(int kind, CharSequence delimiter) {
        this(kind, null, null, null, false, null, delimiter, null, null, null, null, null, null, null, null, null, "", "");
    }

    Collector(int kind, Predicate<?> predicate) {
        this(kind, null, null, null, false, null, "", predicate, null, null, null, null, null, null, null, null, "", "");
    }

    /**
     * MAPPING: downstream first so this signature is unambiguous against the {@code (int, Function,
     * Function)} TO_MAP/GROUPING_BY constructor under {@code null} args.
     */
    Collector(int kind, Collector<?, ?, ?> downstream, Function<?, ?> mapper) {
        this(kind, mapper, null, null, false, null, "", null, null, null, null, null, downstream, null, null, null, "", "");
    }

    // ---- tail-2 shape constructors --------------------------------------------------------------

    /** TO_MAP_MERGE / TO_CONCURRENT_MAP_MERGE: key + value + merge function. */
    Collector(int kind, Function<?, ?> keyFn, Function<?, ?> valueFn, BinaryOperator<?> mergeFn) {
        this(kind, keyFn, valueFn, mergeFn, false, null, "", null, null, null, null, null, null, null, null, null, "", "");
    }

    /** GROUPING_BY_DOWNSTREAM / FILTERING / FLAT_MAPPING: a classifier/mapper or predicate + downstream. */
    Collector(int kind, Function<?, ?> classifierOrMapper, Collector<?, ?, ?> downstream) {
        this(kind, classifierOrMapper, null, null, false, null, "", null, null, null, null, null, downstream, null, null, null, "", "");
    }

    /** PARTITIONING_BY_DOWNSTREAM / FILTERING: predicate + downstream. */
    Collector(int kind, Predicate<?> predicate, Collector<?, ?, ?> downstream) {
        this(kind, null, null, null, false, null, "", predicate, null, null, null, null, downstream, null, null, null, "", "");
    }

    /** REDUCING(op) — no identity; Optional-returning. */
    Collector(int kind, BinaryOperator<?> op) {
        this(kind, null, null, op, false, null, "", null, null, null, null, null, null, null, null, null, "", "");
    }

    /** REDUCING(identity, op) and REDUCING(identity, mapper, op). */
    Collector(int kind, Object identity, Function<?, ?> mapper, BinaryOperator<?> op) {
        this(kind, mapper, null, op, true, identity, "", null, null, null, null, null, null, null, null, null, "", "");
    }

    /** COLLECTING_AND_THEN: downstream + finisher. */
    Collector(int kind, Collector<?, ?, ?> downstream, Function<?, ?> finisher, boolean isFinisher) {
        this(kind, null, null, null, false, null, "", null, null, null, finisher, null, downstream, null, null, null, "", "");
    }

    /** MIN_BY / MAX_BY: comparator. */
    Collector(int kind, Comparator<?> comparator) {
        this(kind, null, null, null, false, null, "", null, comparator, null, null, null, null, null, null, null, "", "");
    }

    /** TO_COLLECTION: container supplier. */
    Collector(int kind, Supplier<?> supplier) {
        this(kind, null, null, null, false, null, "", null, null, supplier, null, null, null, null, null, null, "", "");
    }

    /** TEEING: two downstreams + merger. */
    Collector(int kind, Collector<?, ?, ?> downstream, Collector<?, ?, ?> downstream2, BiFunction<?, ?, ?> merger) {
        this(kind, null, null, null, false, null, "", null, null, null, null, merger, downstream, downstream2, null, null, "", "");
    }
}
