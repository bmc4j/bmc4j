package java.util.stream;

import java.util.function.LongPredicate;
import java.util.function.LongUnaryOperator;

/** Minimal BMC model of {@link java.util.stream.LongStream}, eager over a bounded {@code long[]}. */
public interface LongStream {

    LongStream map(LongUnaryOperator op);

    LongStream filter(LongPredicate predicate);

    long sum();

    long count();

    boolean anyMatch(LongPredicate predicate);

    Stream<Long> boxed();

    static LongStream range(long startInclusive, long endExclusive) {
        LongArrayStream s = new LongArrayStream();
        for (long i = startInclusive; i < endExclusive; i++) {
            s.add(i);
        }
        return s;
    }

    static LongStream rangeClosed(long startInclusive, long endInclusive) {
        LongArrayStream s = new LongArrayStream();
        for (long i = startInclusive; i <= endInclusive; i++) {
            s.add(i);
        }
        return s;
    }

    static LongStream of(long... values) {
        LongArrayStream s = new LongArrayStream();
        for (long v : values) {
            s.add(v);
        }
        return s;
    }
}
