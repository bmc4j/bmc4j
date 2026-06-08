package java.util.stream;

import java.util.ArrayList;
import java.util.OptionalInt;
import java.util.function.IntBinaryOperator;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import java.util.function.IntToLongFunction;
import java.util.function.IntUnaryOperator;

import org.bmc4j.models.audit.BmcModelConforms;

/** Eager, {@code int[]}-backed {@link IntStream} model (fixed capacity, like the collection models). */
final class IntArrayStream implements IntStream {

    private static final int CAPACITY = 64;

    private final int[] data = new int[CAPACITY];
    private int size;

    void add(int v) {
        data[size++] = v;
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream)")
    public IntStream map(IntUnaryOperator op) {
        IntArrayStream s = new IntArrayStream();
        for (int i = 0; i < size; i++) {
            s.add(op.applyAsInt(data[i]));
        }
        return s;
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream)")
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
    @BmcModelConforms("@BmcProof (proofs.stream)")
    public int sum() {
        int t = 0;
        for (int i = 0; i < size; i++) {
            t += data[i];
        }
        return t;
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream)")
    public long count() {
        return size;
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream)")
    public boolean anyMatch(IntPredicate predicate) {
        for (int i = 0; i < size; i++) {
            if (predicate.test(data[i])) {
                return true;
            }
        }
        return false;
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream)")
    public boolean allMatch(IntPredicate predicate) {
        for (int i = 0; i < size; i++) {
            if (!predicate.test(data[i])) {
                return false;
            }
        }
        return true;
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream)")
    public boolean noneMatch(IntPredicate predicate) {
        for (int i = 0; i < size; i++) {
            if (predicate.test(data[i])) {
                return false;
            }
        }
        return true;
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream)")
    public IntStream limit(long maxSize) {
        IntArrayStream s = new IntArrayStream();
        for (int i = 0; i < size; i++) {
            if (i < maxSize) {
                s.add(data[i]);
            }
        }
        return s;
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream)")
    public IntStream skip(long n) {
        IntArrayStream s = new IntArrayStream();
        for (int i = 0; i < size; i++) {
            if (i >= n) {
                s.add(data[i]);
            }
        }
        return s;
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream)")
    public IntStream takeWhile(IntPredicate predicate) {
        IntArrayStream s = new IntArrayStream();
        for (int i = 0; i < size; i++) {
            if (!predicate.test(data[i])) {
                break;
            }
            s.add(data[i]);
        }
        return s;
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream)")
    public IntStream dropWhile(IntPredicate predicate) {
        IntArrayStream s = new IntArrayStream();
        boolean dropping = true;
        for (int i = 0; i < size; i++) {
            if (dropping && predicate.test(data[i])) {
                continue;
            }
            dropping = false;
            s.add(data[i]);
        }
        return s;
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream)")
    public IntStream distinct() {
        IntArrayStream s = new IntArrayStream();
        for (int i = 0; i < size; i++) {
            boolean seen = false;
            for (int j = 0; j < i; j++) {
                if (data[j] == data[i]) {
                    seen = true;
                    break;
                }
            }
            if (!seen) {
                s.add(data[i]);
            }
        }
        return s;
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream)")
    public IntStream sorted() {
        // Natural-order stable insertion sort over the bounded array. Tiny lengths only.
        IntArrayStream s = new IntArrayStream();
        int[] out = new int[size];
        int n = 0;
        for (int i = 0; i < size; i++) {
            int v = data[i];
            int pos = n;
            for (int j = 0; j < n; j++) {
                if (v < out[j]) {
                    pos = j;
                    break;
                }
            }
            for (int k = n; k > pos; k--) {
                out[k] = out[k - 1];
            }
            out[pos] = v;
            n++;
        }
        for (int i = 0; i < n; i++) {
            s.add(out[i]);
        }
        return s;
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream)")
    public IntStream peek(IntConsumer action) {
        IntArrayStream s = new IntArrayStream();
        for (int i = 0; i < size; i++) {
            action.accept(data[i]);
            s.add(data[i]);
        }
        return s;
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream)")
    public void forEach(IntConsumer action) {
        for (int i = 0; i < size; i++) {
            action.accept(data[i]);
        }
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream)")
    public int reduce(int identity, IntBinaryOperator op) {
        int result = identity;
        for (int i = 0; i < size; i++) {
            result = op.applyAsInt(result, data[i]);
        }
        return result;
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream IntStreamOptionalLaws)")
    public OptionalInt reduce(IntBinaryOperator op) {
        if (size == 0) {
            return OptionalInt.empty();
        }
        int result = data[0];
        for (int i = 1; i < size; i++) {
            result = op.applyAsInt(result, data[i]);
        }
        return OptionalInt.of(result);
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream IntStreamOptionalLaws)")
    public OptionalInt min() {
        if (size == 0) {
            return OptionalInt.empty();
        }
        int m = data[0];
        for (int i = 1; i < size; i++) {
            if (data[i] < m) {
                m = data[i];
            }
        }
        return OptionalInt.of(m);
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream IntStreamOptionalLaws)")
    public OptionalInt max() {
        if (size == 0) {
            return OptionalInt.empty();
        }
        int m = data[0];
        for (int i = 1; i < size; i++) {
            if (data[i] > m) {
                m = data[i];
            }
        }
        return OptionalInt.of(m);
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream IntStreamOptionalLaws)")
    public OptionalInt findFirst() {
        return size == 0 ? OptionalInt.empty() : OptionalInt.of(data[0]);
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream IntStreamOptionalLaws)")
    public OptionalInt findAny() {
        // Eager bounded model is deterministic: findAny returns the first element, a valid choice
        // under the JDK's "any element" contract (it permits, but does not require, the first).
        return findFirst();
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream)")
    public int[] toArray() {
        int[] out = new int[size];
        for (int i = 0; i < size; i++) {
            out[i] = data[i];
        }
        return out;
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream)")
    public LongStream mapToLong(IntToLongFunction mapper) {
        LongArrayStream s = new LongArrayStream();
        for (int i = 0; i < size; i++) {
            s.add(mapper.applyAsLong(data[i]));
        }
        return s;
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream)")
    public LongStream asLongStream() {
        LongArrayStream s = new LongArrayStream();
        for (int i = 0; i < size; i++) {
            s.add(data[i]);
        }
        return s;
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream)")
    public <U> Stream<U> mapToObj(IntFunction<? extends U> mapper) {
        ArrayList<U> out = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            out.add(mapper.apply(data[i]));
        }
        return new ListStream<>(out);
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream)")
    public Stream<Integer> boxed() {
        ArrayList<Integer> l = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            l.add(data[i]);
        }
        return new ListStream<>(l);
    }
}
