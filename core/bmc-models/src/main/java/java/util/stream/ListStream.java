package java.util.stream;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;

import org.bmc4j.models.audit.BmcModelConforms;

/** Eager, array-backed {@link Stream} model. Each op produces a fresh stream over a bounded list. */
public final class ListStream<T> implements Stream<T> {

    private final ArrayList<T> data;

    public ListStream(ArrayList<T> data) {
        this.data = data;
    }

    public ListStream(T[] values) {
        this.data = new ArrayList<>();
        for (T v : values) {
            data.add(v);
        }
    }

    public ListStream(List<T> source) {
        this.data = new ArrayList<>();
        for (int i = 0; i < source.size(); i++) {
            data.add(source.get(i));
        }
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream)")
    public Stream<T> filter(Predicate<? super T> predicate) {
        ArrayList<T> out = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            T v = data.get(i);
            if (predicate.test(v)) {
                out.add(v);
            }
        }
        return new ListStream<>(out);
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream)")
    public <R> Stream<R> map(Function<? super T, ? extends R> mapper) {
        ArrayList<R> out = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            out.add(mapper.apply(data.get(i)));
        }
        return new ListStream<>(out);
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream)")
    public IntStream mapToInt(ToIntFunction<? super T> mapper) {
        IntArrayStream s = new IntArrayStream();
        for (int i = 0; i < data.size(); i++) {
            s.add(mapper.applyAsInt(data.get(i)));
        }
        return s;
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream)")
    public LongStream mapToLong(ToLongFunction<? super T> mapper) {
        LongArrayStream s = new LongArrayStream();
        for (int i = 0; i < data.size(); i++) {
            s.add(mapper.applyAsLong(data.get(i)));
        }
        return s;
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream)")
    public long count() {
        return data.size();
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream)")
    public boolean anyMatch(Predicate<? super T> predicate) {
        for (int i = 0; i < data.size(); i++) {
            if (predicate.test(data.get(i))) {
                return true;
            }
        }
        return false;
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream)")
    public boolean allMatch(Predicate<? super T> predicate) {
        for (int i = 0; i < data.size(); i++) {
            if (!predicate.test(data.get(i))) {
                return false;
            }
        }
        return true;
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream)")
    public void forEach(Consumer<? super T> action) {
        for (int i = 0; i < data.size(); i++) {
            action.accept(data.get(i));
        }
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream)")
    public T reduce(T identity, BinaryOperator<T> accumulator) {
        T result = identity;
        for (int i = 0; i < data.size(); i++) {
            result = accumulator.apply(result, data.get(i));
        }
        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    @BmcModelConforms("@BmcProof (proofs.stream)")
    public <R, A> R collect(Collector<? super T, A, R> collector) {
        if (collector.kind == Collector.TO_SET) {
            java.util.HashSet<T> s = new java.util.HashSet<>();
            for (int i = 0; i < data.size(); i++) {
                s.add(data.get(i));
            }
            return (R) s;
        }
        if (collector.kind == Collector.TO_MAP) {
            Function<? super T, ?> keyFn = (Function<? super T, ?>) collector.keyFn;
            Function<? super T, ?> valFn = (Function<? super T, ?>) collector.valueFn;
            java.util.HashMap<Object, Object> m = new java.util.HashMap<>();
            for (int i = 0; i < data.size(); i++) {
                T v = data.get(i);
                Object key = keyFn.apply(v);
                if (m.containsKey(key)) {
                    throw new IllegalStateException("Duplicate key");
                }
                m.put(key, valFn.apply(v));
            }
            return (R) m;
        }
        if (collector.kind == Collector.JOINING) {
            // Explicit StringBuilder (JBMC models append/toString soundly) — no invokedynamic
            // string concat, so this is sound. Elements are CharSequence (joining's contract).
            StringBuilder sb = new StringBuilder();
            String sep = collector.delimiter.toString();
            for (int i = 0; i < data.size(); i++) {
                if (i > 0) {
                    sb.append(sep);
                }
                sb.append((CharSequence) data.get(i));
            }
            return (R) sb.toString();
        }
        if (collector.kind == Collector.GROUPING_BY) {
            Function<? super T, ?> classifier = (Function<? super T, ?>) collector.keyFn;
            java.util.HashMap<Object, List<T>> m = new java.util.HashMap<>();
            for (int i = 0; i < data.size(); i++) {
                T v = data.get(i);
                Object key = classifier.apply(v);
                ArrayList<T> bucket = (ArrayList<T>) m.get(key);
                if (bucket == null) {
                    bucket = new ArrayList<>();
                    m.put(key, bucket);
                }
                bucket.add(v);
            }
            return (R) m;
        }
        return (R) toList();
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream)")
    public List<T> toList() {
        ArrayList<T> copy = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            copy.add(data.get(i));
        }
        return copy;
    }
}
