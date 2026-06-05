package java.util.stream;

import java.util.function.IntPredicate;
import java.util.function.IntUnaryOperator;

/** Minimal BMC model of {@link java.util.stream.IntStream}, eager over a bounded {@code int[]}. */
public interface IntStream {

    IntStream map(IntUnaryOperator op);

    IntStream filter(IntPredicate predicate);

    int sum();

    long count();

    boolean anyMatch(IntPredicate predicate);

    Stream<Integer> boxed();

    static IntStream range(int startInclusive, int endExclusive) {
        IntArrayStream s = new IntArrayStream();
        for (int i = startInclusive; i < endExclusive; i++) {
            s.add(i);
        }
        return s;
    }

    static IntStream rangeClosed(int startInclusive, int endInclusive) {
        IntArrayStream s = new IntArrayStream();
        for (int i = startInclusive; i <= endInclusive; i++) {
            s.add(i);
        }
        return s;
    }

    static IntStream of(int... values) {
        IntArrayStream s = new IntArrayStream();
        for (int v : values) {
            s.add(v);
        }
        return s;
    }
}
