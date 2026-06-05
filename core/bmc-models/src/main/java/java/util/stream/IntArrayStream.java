package java.util.stream;

import java.util.ArrayList;
import java.util.function.IntPredicate;
import java.util.function.IntUnaryOperator;

/** Eager, {@code int[]}-backed {@link IntStream} model (fixed capacity, like the collection models). */
final class IntArrayStream implements IntStream {

    private static final int CAPACITY = 64;

    private final int[] data = new int[CAPACITY];
    private int size;

    void add(int v) {
        data[size++] = v;
    }

    @Override
    public IntStream map(IntUnaryOperator op) {
        IntArrayStream s = new IntArrayStream();
        for (int i = 0; i < size; i++) {
            s.add(op.applyAsInt(data[i]));
        }
        return s;
    }

    @Override
    public IntStream filter(IntPredicate predicate) {
        IntArrayStream s = new IntArrayStream();
        for (int i = 0; i < size; i++) {
            if (predicate.test(data[i])) {
                s.add(data[i]);
            }
        }
        return s;
    }

    @Override
    public int sum() {
        int t = 0;
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
    public boolean anyMatch(IntPredicate predicate) {
        for (int i = 0; i < size; i++) {
            if (predicate.test(data[i])) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Stream<Integer> boxed() {
        ArrayList<Integer> l = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            l.add(data[i]);
        }
        return new ListStream<>(l);
    }
}
