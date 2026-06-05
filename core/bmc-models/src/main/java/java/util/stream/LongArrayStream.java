package java.util.stream;

import java.util.ArrayList;
import java.util.function.LongPredicate;
import java.util.function.LongUnaryOperator;

/** Eager, {@code long[]}-backed {@link LongStream} model (fixed capacity, like the collection models). */
final class LongArrayStream implements LongStream {

    private static final int CAPACITY = 64;

    private final long[] data = new long[CAPACITY];
    private int size;

    void add(long v) {
        data[size++] = v;
    }

    @Override
    public LongStream map(LongUnaryOperator op) {
        LongArrayStream s = new LongArrayStream();
        for (int i = 0; i < size; i++) {
            s.add(op.applyAsLong(data[i]));
        }
        return s;
    }

    @Override
    public LongStream filter(LongPredicate predicate) {
        LongArrayStream s = new LongArrayStream();
        for (int i = 0; i < size; i++) {
            if (predicate.test(data[i])) {
                s.add(data[i]);
            }
        }
        return s;
    }

    @Override
    public long sum() {
        long t = 0;
        for (int i = 0; i < size; i++) {
            t += data[i];
        }
        return t;
    }

    @Override
    public long count() {
        return size;
    }

    @Override
    public boolean anyMatch(LongPredicate predicate) {
        for (int i = 0; i < size; i++) {
            if (predicate.test(data[i])) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Stream<Long> boxed() {
        ArrayList<Long> l = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            l.add(data[i]);
        }
        return new ListStream<>(l);
    }
}
