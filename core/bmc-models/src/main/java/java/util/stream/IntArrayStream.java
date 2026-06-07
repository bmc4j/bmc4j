package java.util.stream;

import java.util.ArrayList;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
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
