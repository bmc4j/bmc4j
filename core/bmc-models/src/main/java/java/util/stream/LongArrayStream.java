package java.util.stream;

import java.util.ArrayList;
import java.util.function.LongFunction;
import java.util.function.LongPredicate;
import java.util.function.LongUnaryOperator;

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
}
