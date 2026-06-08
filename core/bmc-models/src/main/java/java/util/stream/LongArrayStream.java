package java.util.stream;

import java.util.ArrayList;
import java.util.LongSummaryStatistics;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.function.BiConsumer;
import java.util.function.LongBinaryOperator;
import java.util.function.LongConsumer;
import java.util.function.LongFunction;
import java.util.function.LongPredicate;
import java.util.function.LongToDoubleFunction;
import java.util.function.LongToIntFunction;
import java.util.function.LongUnaryOperator;
import java.util.function.ObjLongConsumer;
import java.util.function.Supplier;

import org.bmc4j.models.audit.BmcModelConforms;

/** Eager, {@code long[]}-backed {@link LongStream} model (fixed capacity, like the collection models). */
final class LongArrayStream implements LongStream {

    private static final int CAPACITY = 64;

    private final long[] data = new long[CAPACITY];
    private int size;

    void add(long v) {
        data[size++] = v;
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream)")
    public LongStream map(LongUnaryOperator op) {
        LongArrayStream s = new LongArrayStream();
        for (int i = 0; i < size; i++) {
            s.add(op.applyAsLong(data[i]));
        }
        return s;
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream)")
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
    @BmcModelConforms("@BmcProof (proofs.stream)")
    public long sum() {
        long t = 0;
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
    public boolean anyMatch(LongPredicate predicate) {
        for (int i = 0; i < size; i++) {
            if (predicate.test(data[i])) {
                return true;
            }
        }
        return false;
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream)")
    public boolean allMatch(LongPredicate predicate) {
        for (int i = 0; i < size; i++) {
            if (!predicate.test(data[i])) {
                return false;
            }
        }
        return true;
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream)")
    public boolean noneMatch(LongPredicate predicate) {
        for (int i = 0; i < size; i++) {
            if (predicate.test(data[i])) {
                return false;
            }
        }
        return true;
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream)")
    public LongStream limit(long maxSize) {
        LongArrayStream s = new LongArrayStream();
        for (int i = 0; i < size; i++) {
            if (i < maxSize) {
                s.add(data[i]);
            }
        }
        return s;
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream)")
    public LongStream skip(long n) {
        LongArrayStream s = new LongArrayStream();
        for (int i = 0; i < size; i++) {
            if (i >= n) {
                s.add(data[i]);
            }
        }
        return s;
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream)")
    public LongStream takeWhile(LongPredicate predicate) {
        LongArrayStream s = new LongArrayStream();
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
    public LongStream dropWhile(LongPredicate predicate) {
        LongArrayStream s = new LongArrayStream();
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
    public LongStream distinct() {
        LongArrayStream s = new LongArrayStream();
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
    public LongStream sorted() {
        // Natural-order stable insertion sort over the bounded array. Tiny lengths only.
        LongArrayStream s = new LongArrayStream();
        long[] out = new long[size];
        int n = 0;
        for (int i = 0; i < size; i++) {
            long v = data[i];
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
    public LongStream peek(LongConsumer action) {
        LongArrayStream s = new LongArrayStream();
        for (int i = 0; i < size; i++) {
            action.accept(data[i]);
            s.add(data[i]);
        }
        return s;
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream)")
    public void forEach(LongConsumer action) {
        for (int i = 0; i < size; i++) {
            action.accept(data[i]);
        }
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream)")
    public long reduce(long identity, LongBinaryOperator op) {
        long result = identity;
        for (int i = 0; i < size; i++) {
            result = op.applyAsLong(result, data[i]);
        }
        return result;
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream LongStreamOptionalLaws)")
    public OptionalLong reduce(LongBinaryOperator op) {
        if (size == 0) {
            return OptionalLong.empty();
        }
        long result = data[0];
        for (int i = 1; i < size; i++) {
            result = op.applyAsLong(result, data[i]);
        }
        return OptionalLong.of(result);
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream LongStreamOptionalLaws)")
    public OptionalLong min() {
        if (size == 0) {
            return OptionalLong.empty();
        }
        long m = data[0];
        for (int i = 1; i < size; i++) {
            if (data[i] < m) {
                m = data[i];
            }
        }
        return OptionalLong.of(m);
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream LongStreamOptionalLaws)")
    public OptionalLong max() {
        if (size == 0) {
            return OptionalLong.empty();
        }
        long m = data[0];
        for (int i = 1; i < size; i++) {
            if (data[i] > m) {
                m = data[i];
            }
        }
        return OptionalLong.of(m);
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream LongStreamOptionalLaws)")
    public OptionalLong findFirst() {
        return size == 0 ? OptionalLong.empty() : OptionalLong.of(data[0]);
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream LongStreamOptionalLaws)")
    public OptionalLong findAny() {
        // Eager bounded model is deterministic: findAny returns the first element, a valid choice
        // under the JDK's "any element" contract (it permits, but does not require, the first).
        return findFirst();
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream)")
    public long[] toArray() {
        long[] out = new long[size];
        for (int i = 0; i < size; i++) {
            out[i] = data[i];
        }
        return out;
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream)")
    public IntStream mapToInt(LongToIntFunction mapper) {
        IntArrayStream s = new IntArrayStream();
        for (int i = 0; i < size; i++) {
            s.add(mapper.applyAsInt(data[i]));
        }
        return s;
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream LongStreamDoubleBridgeLaws)")
    public DoubleStream asDoubleStream() {
        // long -> double widening (exact for the small bounded element values proofs use); sound +/÷.
        DoubleArrayStream s = new DoubleArrayStream();
        for (int i = 0; i < size; i++) {
            s.add(data[i]);
        }
        return s;
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream LongStreamDoubleBridgeLaws)")
    public DoubleStream mapToDouble(LongToDoubleFunction mapper) {
        DoubleArrayStream s = new DoubleArrayStream();
        for (int i = 0; i < size; i++) {
            s.add(mapper.applyAsDouble(data[i]));
        }
        return s;
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream LongStreamDoubleBridgeLaws)")
    public OptionalDouble average() {
        if (size == 0) {
            return OptionalDouble.empty();
        }
        // Sum in a long (no overflow for bounded inputs), then ONE sound double division.
        long t = 0;
        for (int i = 0; i < size; i++) {
            t += data[i];
        }
        return OptionalDouble.of((double) t / size);
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream LongStreamDoubleBridgeLaws)")
    public LongSummaryStatistics summaryStatistics() {
        LongSummaryStatistics stats = new LongSummaryStatistics();
        for (int i = 0; i < size; i++) {
            stats.accept(data[i]);
        }
        return stats;
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream)")
    public <U> Stream<U> mapToObj(LongFunction<? extends U> mapper) {
        ArrayList<U> out = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            out.add(mapper.apply(data[i]));
        }
        return new ListStream<>(out);
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream)")
    public Stream<Long> boxed() {
        ArrayList<Long> l = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            l.add(data[i]);
        }
        return new ListStream<>(l);
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream)")
    public LongStream flatMap(LongFunction<? extends LongStream> mapper) {
        LongArrayStream s = new LongArrayStream();
        for (int i = 0; i < size; i++) {
            LongStream inner = mapper.apply(data[i]);
            // Drain via the sole final implementor (invokevirtual), not the LongStream interface —
            // the interface dispatch is the kotlinc-version-fragile devirtualization behind the #169
            // false-REFUTED family.
            long[] arr = ((LongArrayStream) inner).toArray();
            for (int j = 0; j < arr.length; j++) {
                s.add(arr[j]);
            }
        }
        return s;
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream)")
    public void forEachOrdered(LongConsumer action) {
        // The eager model is already ordered; forEachOrdered == forEach over the encounter order.
        for (int i = 0; i < size; i++) {
            action.accept(data[i]);
        }
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream)")
    public <R> R collect(Supplier<R> supplier, ObjLongConsumer<R> accumulator, BiConsumer<R, R> combiner) {
        // Sequential mutable reduction; the combiner only joins parallel partials (never here).
        R container = supplier.get();
        for (int i = 0; i < size; i++) {
            accumulator.accept(container, data[i]);
        }
        return container;
    }
}
