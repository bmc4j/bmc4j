package java.util.stream;

import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Minimal BMC model of {@link java.util.stream.Collector}. The real interface is a
 * supplier/accumulator/combiner/finisher bundle; here it is just a tag identifying the target
 * container (plus, for the map-producing collectors, the key/value/classifier functions), which
 * {@link ListStream#collect} interprets. {@link Collectors} produces these.
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

    final int kind;

    /** For {@link #TO_MAP}: key mapper. For {@link #GROUPING_BY}: the classifier. For {@link #MAPPING}: the per-element mapper. */
    final Function<?, ?> keyFn;

    /** For {@link #TO_MAP}: value mapper. Unused by {@link #GROUPING_BY}. */
    final Function<?, ?> valueFn;

    /** For {@link #JOINING}: the delimiter inserted between elements (empty for {@code joining()}). */
    final CharSequence delimiter;

    /** For {@link #PARTITIONING_BY}: the predicate that splits elements into the true/false buckets. */
    final Predicate<?> predicate;

    /** For {@link #MAPPING}: the downstream collector the mapped elements are fed into. */
    final Collector<?, ?, ?> downstream;

    Collector(int kind) {
        this(kind, (Function<?, ?>) null, (Function<?, ?>) null);
    }

    Collector(int kind, Function<?, ?> keyFn, Function<?, ?> valueFn) {
        this.kind = kind;
        this.keyFn = keyFn;
        this.valueFn = valueFn;
        this.delimiter = "";
        this.predicate = null;
        this.downstream = null;
    }

    Collector(int kind, CharSequence delimiter) {
        this.kind = kind;
        this.keyFn = null;
        this.valueFn = null;
        this.delimiter = delimiter;
        this.predicate = null;
        this.downstream = null;
    }

    Collector(int kind, Predicate<?> predicate) {
        this.kind = kind;
        this.keyFn = null;
        this.valueFn = null;
        this.delimiter = "";
        this.predicate = predicate;
        this.downstream = null;
    }

    /**
     * For {@link #MAPPING}: the downstream collector plus the per-element mapper it feeds. The
     * downstream param comes FIRST so this signature is unambiguous against the
     * {@code (int, Function, Function)} TO_MAP/GROUPING_BY constructor under {@code null} args.
     */
    Collector(int kind, Collector<?, ?, ?> downstream, Function<?, ?> mapper) {
        this.kind = kind;
        this.keyFn = mapper;
        this.valueFn = null;
        this.delimiter = "";
        this.predicate = null;
        this.downstream = downstream;
    }
}
