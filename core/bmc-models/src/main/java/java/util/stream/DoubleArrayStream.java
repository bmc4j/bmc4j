package java.util.stream;

import java.util.ArrayList;
import java.util.DoubleSummaryStatistics;
import java.util.OptionalDouble;
import java.util.function.BiConsumer;
import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoublePredicate;
import java.util.function.DoubleToIntFunction;
import java.util.function.DoubleToLongFunction;
import java.util.function.DoubleUnaryOperator;
import java.util.function.ObjDoubleConsumer;
import java.util.function.Supplier;

import org.bmc4j.models.audit.BmcModelConforms;

/**
 * Eager, {@code double[]}-backed {@link DoubleStream} model (fixed capacity, like the collection models).
 *
 * <p>{@code min}/{@code max}/{@code sorted} are deliberately NOT implemented here: they are loud
 * {@link org.bmc4j.models.audit.BmcUnmodelable} default methods on the {@link DoubleStream} interface
 * (FP total-order via {@code Double.compare} is unsound under JBMC). Everything here is sound double
 * arithmetic/comparison.
 */
final class DoubleArrayStream implements DoubleStream {

    private static final int CAPACITY = 64;

    private final double[] data = new double[CAPACITY];
    private int size;

    void add(double v) {
        data[size++] = v;
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream DoubleStreamLaws)")
    public DoubleStream map(DoubleUnaryOperator op) {
        DoubleArrayStream s = new DoubleArrayStream();
        for (int i = 0; i < size; i++) {
            s.add(op.applyAsDouble(data[i]));
        }
        return s;
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream DoubleStreamLaws)")
    public DoubleStream filter(DoublePredicate predicate) {
        DoubleArrayStream s = new DoubleArrayStream();
        for (int i = 0; i < size; i++) {
            if (predicate.test(data[i])) {
                s.add(data[i]);
            }
        }
        return s;
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream DoubleStreamLaws)")
    public double sum() {
        double t = 0.0;
        for (int i = 0; i < size; i++) {
            t += data[i];
        }
        return t;
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream DoubleStreamLaws)")
    public OptionalDouble average() {
        if (size == 0) {
            return OptionalDouble.empty();
        }
        double t = 0.0;
        for (int i = 0; i < size; i++) {
            t += data[i];
        }
        return OptionalDouble.of(t / size);
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream DoubleStreamLaws)")
    public DoubleSummaryStatistics summaryStatistics() {
        DoubleSummaryStatistics stats = new DoubleSummaryStatistics();
        for (int i = 0; i < size; i++) {
            stats.accept(data[i]);
        }
        return stats;
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream DoubleStreamLaws)")
    public long count() {
        return size;
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream DoubleStreamLaws)")
    public boolean anyMatch(DoublePredicate predicate) {
        for (int i = 0; i < size; i++) {
            if (predicate.test(data[i])) {
                return true;
            }
        }
        return false;
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream DoubleStreamLaws)")
    public boolean allMatch(DoublePredicate predicate) {
        for (int i = 0; i < size; i++) {
            if (!predicate.test(data[i])) {
                return false;
            }
        }
        return true;
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream DoubleStreamLaws)")
    public boolean noneMatch(DoublePredicate predicate) {
        for (int i = 0; i < size; i++) {
            if (predicate.test(data[i])) {
                return false;
            }
        }
        return true;
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream DoubleStreamLaws)")
    public DoubleStream limit(long maxSize) {
        DoubleArrayStream s = new DoubleArrayStream();
        for (int i = 0; i < size; i++) {
            if (i < maxSize) {
                s.add(data[i]);
            }
        }
        return s;
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream DoubleStreamLaws)")
    public DoubleStream skip(long n) {
        DoubleArrayStream s = new DoubleArrayStream();
        for (int i = 0; i < size; i++) {
            if (i >= n) {
                s.add(data[i]);
            }
        }
        return s;
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream DoubleStreamLaws)")
    public DoubleStream takeWhile(DoublePredicate predicate) {
        DoubleArrayStream s = new DoubleArrayStream();
        for (int i = 0; i < size; i++) {
            if (!predicate.test(data[i])) {
                break;
            }
            s.add(data[i]);
        }
        return s;
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream DoubleStreamLaws)")
    public DoubleStream dropWhile(DoublePredicate predicate) {
        DoubleArrayStream s = new DoubleArrayStream();
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
    @BmcModelConforms("@BmcProof (proofs.stream DoubleStreamLaws)")
    public DoubleStream distinct() {
        DoubleArrayStream s = new DoubleArrayStream();
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
    @BmcModelConforms("@BmcProof (proofs.stream DoubleStreamLaws)")
    public DoubleStream peek(DoubleConsumer action) {
        DoubleArrayStream s = new DoubleArrayStream();
        for (int i = 0; i < size; i++) {
            action.accept(data[i]);
            s.add(data[i]);
        }
        return s;
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream DoubleStreamLaws)")
    public void forEach(DoubleConsumer action) {
        for (int i = 0; i < size; i++) {
            action.accept(data[i]);
        }
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream DoubleStreamLaws)")
    public void forEachOrdered(DoubleConsumer action) {
        // The eager model is already ordered; forEachOrdered == forEach over the encounter order.
        for (int i = 0; i < size; i++) {
            action.accept(data[i]);
        }
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream DoubleStreamLaws)")
    public double reduce(double identity, DoubleBinaryOperator op) {
        double result = identity;
        for (int i = 0; i < size; i++) {
            result = op.applyAsDouble(result, data[i]);
        }
        return result;
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream DoubleStreamLaws)")
    public OptionalDouble reduce(DoubleBinaryOperator op) {
        if (size == 0) {
            return OptionalDouble.empty();
        }
        double result = data[0];
        for (int i = 1; i < size; i++) {
            result = op.applyAsDouble(result, data[i]);
        }
        return OptionalDouble.of(result);
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream DoubleStreamLaws)")
    public OptionalDouble findFirst() {
        return size == 0 ? OptionalDouble.empty() : OptionalDouble.of(data[0]);
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream DoubleStreamLaws)")
    public OptionalDouble findAny() {
        // Eager bounded model is deterministic: findAny returns the first element, a valid choice
        // under the JDK's "any element" contract (it permits, but does not require, the first).
        return findFirst();
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream DoubleStreamLaws)")
    public double[] toArray() {
        double[] out = new double[size];
        for (int i = 0; i < size; i++) {
            out[i] = data[i];
        }
        return out;
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream DoubleStreamLaws)")
    public IntStream mapToInt(DoubleToIntFunction mapper) {
        IntArrayStream s = new IntArrayStream();
        for (int i = 0; i < size; i++) {
            s.add(mapper.applyAsInt(data[i]));
        }
        return s;
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream DoubleStreamLaws)")
    public LongStream mapToLong(DoubleToLongFunction mapper) {
        LongArrayStream s = new LongArrayStream();
        for (int i = 0; i < size; i++) {
            s.add(mapper.applyAsLong(data[i]));
        }
        return s;
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream DoubleStreamLaws)")
    public <U> Stream<U> mapToObj(DoubleFunction<? extends U> mapper) {
        ArrayList<U> out = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            out.add(mapper.apply(data[i]));
        }
        return new ListStream<>(out);
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream DoubleStreamLaws)")
    public Stream<Double> boxed() {
        ArrayList<Double> l = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            l.add(data[i]);
        }
        return new ListStream<>(l);
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream DoubleStreamLaws)")
    public DoubleStream flatMap(DoubleFunction<? extends DoubleStream> mapper) {
        DoubleArrayStream s = new DoubleArrayStream();
        for (int i = 0; i < size; i++) {
            DoubleStream inner = mapper.apply(data[i]);
            // Drain via the sole final implementor (invokevirtual), not the DoubleStream interface —
            // the interface dispatch is the kotlinc-version-fragile devirtualization behind the #169
            // false-REFUTED family.
            double[] arr = ((DoubleArrayStream) inner).toArray();
            for (int j = 0; j < arr.length; j++) {
                s.add(arr[j]);
            }
        }
        return s;
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream DoubleStreamLaws)")
    public <R> R collect(Supplier<R> supplier, ObjDoubleConsumer<R> accumulator, BiConsumer<R, R> combiner) {
        // Sequential mutable reduction; the combiner only joins parallel partials (never here).
        R container = supplier.get();
        for (int i = 0; i < size; i++) {
            accumulator.accept(container, data[i]);
        }
        return container;
    }
}
