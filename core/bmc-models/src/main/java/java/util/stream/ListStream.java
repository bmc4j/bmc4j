package java.util.stream;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
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
    public <R> Stream<R> flatMap(Function<? super T, ? extends Stream<? extends R>> mapper) {
        ArrayList<R> out = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            Stream<? extends R> inner = mapper.apply(data.get(i));
            // Drain via the sole final implementor (invokevirtual on ListStream), not via the
            // Stream interface — the interface dispatch is the kotlinc-version-fragile
            // devirtualization behind the #169 false-REFUTED family.
            List<? extends R> innerList = ((ListStream<? extends R>) inner).toList();
            for (int j = 0; j < innerList.size(); j++) {
                out.add(innerList.get(j));
            }
        }
        return new ListStream<>(out);
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream)")
    public Stream<T> distinct() {
        ArrayList<T> out = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            T v = data.get(i);
            if (!out.contains(v)) {
                out.add(v);
            }
        }
        return new ListStream<>(out);
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream)")
    public Stream<T> skip(long n) {
        ArrayList<T> out = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            if (i >= n) {
                out.add(data.get(i));
            }
        }
        return new ListStream<>(out);
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream)")
    public Stream<T> limit(long maxSize) {
        ArrayList<T> out = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            if (i < maxSize) {
                out.add(data.get(i));
            }
        }
        return new ListStream<>(out);
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream)")
    public Stream<T> takeWhile(Predicate<? super T> predicate) {
        ArrayList<T> out = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            T v = data.get(i);
            if (!predicate.test(v)) {
                break;
            }
            out.add(v);
        }
        return new ListStream<>(out);
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream)")
    public Stream<T> dropWhile(Predicate<? super T> predicate) {
        ArrayList<T> out = new ArrayList<>();
        boolean dropping = true;
        for (int i = 0; i < data.size(); i++) {
            T v = data.get(i);
            if (dropping && predicate.test(v)) {
                continue;
            }
            dropping = false;
            out.add(v);
        }
        return new ListStream<>(out);
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream)")
    public Stream<T> peek(Consumer<? super T> action) {
        ArrayList<T> out = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            T v = data.get(i);
            action.accept(v);
            out.add(v);
        }
        return new ListStream<>(out);
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream)")
    public Stream<T> sorted(Comparator<? super T> comparator) {
        // Nondet sorted-permutation witness (java.util.BmcSortWitness): havoc an output of the same
        // length, assume it is a bijective permutation of the input AND non-decreasing under the
        // comparator. Covers any sort implementation at once and avoids the n^2 data-dependent
        // comparisons a real selection sort would emit. Tiny lists only.
        return new ListStream<>(java.util.BmcSortWitness.sorted(data, comparator));
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream)")
    public IntStream flatMapToInt(Function<? super T, ? extends IntStream> mapper) {
        IntArrayStream s = new IntArrayStream();
        for (int i = 0; i < data.size(); i++) {
            IntStream inner = mapper.apply(data.get(i));
            int[] arr = ((IntArrayStream) inner).toArray();
            for (int j = 0; j < arr.length; j++) {
                s.add(arr[j]);
            }
        }
        return s;
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream)")
    public LongStream flatMapToLong(Function<? super T, ? extends LongStream> mapper) {
        LongArrayStream s = new LongArrayStream();
        for (int i = 0; i < data.size(); i++) {
            LongStream inner = mapper.apply(data.get(i));
            long[] arr = ((LongArrayStream) inner).toArray();
            for (int j = 0; j < arr.length; j++) {
                s.add(arr[j]);
            }
        }
        return s;
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream)")
    public DoubleStream flatMapToDouble(Function<? super T, ? extends DoubleStream> mapper) {
        DoubleArrayStream s = new DoubleArrayStream();
        for (int i = 0; i < data.size(); i++) {
            DoubleStream inner = mapper.apply(data.get(i));
            // Drain via the sole final implementor (invokevirtual on DoubleArrayStream), not the
            // DoubleStream interface — the interface dispatch is the kotlinc-version-fragile
            // devirtualization behind the #169 false-REFUTED family.
            double[] arr = ((DoubleArrayStream) inner).toArray();
            for (int j = 0; j < arr.length; j++) {
                s.add(arr[j]);
            }
        }
        return s;
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream)")
    public boolean noneMatch(Predicate<? super T> predicate) {
        for (int i = 0; i < data.size(); i++) {
            if (predicate.test(data.get(i))) {
                return false;
            }
        }
        return true;
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream)")
    public Optional<T> findFirst() {
        if (data.size() == 0) {
            return Optional.empty();
        }
        return Optional.of(data.get(0));
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream)")
    public Optional<T> findAny() {
        // For an ordered eager model, findAny is allowed to (and does) return the first element.
        if (data.size() == 0) {
            return Optional.empty();
        }
        return Optional.of(data.get(0));
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream)")
    public Optional<T> min(Comparator<? super T> comparator) {
        if (data.size() == 0) {
            return Optional.empty();
        }
        T best = data.get(0);
        for (int i = 1; i < data.size(); i++) {
            T v = data.get(i);
            if (comparator.compare(v, best) < 0) {
                best = v;
            }
        }
        return Optional.of(best);
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream)")
    public Optional<T> max(Comparator<? super T> comparator) {
        if (data.size() == 0) {
            return Optional.empty();
        }
        T best = data.get(0);
        for (int i = 1; i < data.size(); i++) {
            T v = data.get(i);
            if (comparator.compare(v, best) > 0) {
                best = v;
            }
        }
        return Optional.of(best);
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
    public DoubleStream mapToDouble(ToDoubleFunction<? super T> mapper) {
        DoubleArrayStream s = new DoubleArrayStream();
        for (int i = 0; i < data.size(); i++) {
            s.add(mapper.applyAsDouble(data.get(i)));
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
    @BmcModelConforms("@BmcProof (proofs.stream)")
    public Optional<T> reduce(BinaryOperator<T> accumulator) {
        if (data.size() == 0) {
            return Optional.empty();
        }
        T result = data.get(0);
        for (int i = 1; i < data.size(); i++) {
            result = accumulator.apply(result, data.get(i));
        }
        return Optional.of(result);
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
        if (collector.kind == Collector.PARTITIONING_BY) {
            Predicate<? super T> predicate = (Predicate<? super T>) collector.predicate;
            ArrayList<T> trues = new ArrayList<>();
            ArrayList<T> falses = new ArrayList<>();
            for (int i = 0; i < data.size(); i++) {
                T v = data.get(i);
                if (predicate.test(v)) {
                    trues.add(v);
                } else {
                    falses.add(v);
                }
            }
            // partitioningBy always has BOTH keys present (total partition), even when empty.
            java.util.HashMap<Object, List<T>> m = new java.util.HashMap<>();
            m.put(Boolean.FALSE, falses);
            m.put(Boolean.TRUE, trues);
            return (R) m;
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
        if (collector.kind == Collector.COUNTING) {
            return (R) Long.valueOf(data.size());
        }
        if (collector.kind == Collector.MAPPING) {
            // Apply the per-element mapper, then collect the mapped elements with the downstream
            // collector over a fresh bounded stream (sound for toList/toSet/counting/joining).
            Function<? super T, ?> mapper = (Function<? super T, ?>) collector.keyFn;
            ArrayList<Object> mapped = new ArrayList<>();
            for (int i = 0; i < data.size(); i++) {
                mapped.add(mapper.apply(data.get(i)));
            }
            Collector<Object, Object, R> down = (Collector<Object, Object, R>) collector.downstream;
            return new ListStream<>(mapped).collect(down);
        }
        if (collector.kind == Collector.TO_MAP_MERGE || collector.kind == Collector.TO_CONCURRENT_MAP_MERGE) {
            Function<? super T, ?> keyFn = (Function<? super T, ?>) collector.keyFn;
            Function<? super T, ?> valFn = (Function<? super T, ?>) collector.valueFn;
            java.util.function.BinaryOperator<Object> merge =
                    (java.util.function.BinaryOperator<Object>) collector.mergeFn;
            java.util.Map<Object, Object> m = collector.kind == Collector.TO_CONCURRENT_MAP_MERGE
                    ? new java.util.concurrent.ConcurrentHashMap<>() : new java.util.HashMap<>();
            for (int i = 0; i < data.size(); i++) {
                T v = data.get(i);
                Object key = keyFn.apply(v);
                Object val = valFn.apply(v);
                if (m.containsKey(key)) {
                    m.put(key, merge.apply(m.get(key), val));
                } else {
                    m.put(key, val);
                }
            }
            return (R) m;
        }
        if (collector.kind == Collector.TO_CONCURRENT_MAP) {
            Function<? super T, ?> keyFn = (Function<? super T, ?>) collector.keyFn;
            Function<? super T, ?> valFn = (Function<? super T, ?>) collector.valueFn;
            java.util.concurrent.ConcurrentHashMap<Object, Object> m = new java.util.concurrent.ConcurrentHashMap<>();
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
        if (collector.kind == Collector.GROUPING_BY_DOWNSTREAM) {
            Function<? super T, ?> classifier = (Function<? super T, ?>) collector.keyFn;
            Collector<Object, Object, Object> down = (Collector<Object, Object, Object>) collector.downstream;
            // First bucket the elements in encounter order (keys discovered in order), then collect
            // each bucket with the downstream over a fresh bounded sub-stream.
            ArrayList<Object> keys = new ArrayList<>();
            ArrayList<ArrayList<T>> buckets = new ArrayList<>();
            for (int i = 0; i < data.size(); i++) {
                T v = data.get(i);
                Object key = classifier.apply(v);
                int idx = keys.indexOf(key);
                if (idx < 0) {
                    keys.add(key);
                    ArrayList<T> b = new ArrayList<>();
                    b.add(v);
                    buckets.add(b);
                } else {
                    buckets.get(idx).add(v);
                }
            }
            java.util.HashMap<Object, Object> m = new java.util.HashMap<>();
            for (int i = 0; i < keys.size(); i++) {
                m.put(keys.get(i), new ListStream<>(buckets.get(i)).collect(down));
            }
            return (R) m;
        }
        if (collector.kind == Collector.PARTITIONING_BY_DOWNSTREAM) {
            Predicate<? super T> predicate = (Predicate<? super T>) collector.predicate;
            Collector<Object, Object, Object> down = (Collector<Object, Object, Object>) collector.downstream;
            ArrayList<T> trues = new ArrayList<>();
            ArrayList<T> falses = new ArrayList<>();
            for (int i = 0; i < data.size(); i++) {
                T v = data.get(i);
                if (predicate.test(v)) {
                    trues.add(v);
                } else {
                    falses.add(v);
                }
            }
            java.util.HashMap<Object, Object> m = new java.util.HashMap<>();
            m.put(Boolean.FALSE, new ListStream<>(falses).collect(down));
            m.put(Boolean.TRUE, new ListStream<>(trues).collect(down));
            return (R) m;
        }
        if (collector.kind == Collector.REDUCING) {
            java.util.function.BinaryOperator<Object> op =
                    (java.util.function.BinaryOperator<Object>) collector.mergeFn;
            Function<? super T, ?> mapper = (Function<? super T, ?>) collector.keyFn;
            if (collector.identityPresent) {
                Object result = collector.identity;
                for (int i = 0; i < data.size(); i++) {
                    Object e = mapper == null ? data.get(i) : mapper.apply(data.get(i));
                    result = op.apply(result, e);
                }
                return (R) result;
            }
            // No identity: Optional-returning.
            if (data.size() == 0) {
                return (R) Optional.empty();
            }
            Object result = data.get(0);
            for (int i = 1; i < data.size(); i++) {
                result = op.apply(result, data.get(i));
            }
            return (R) Optional.of(result);
        }
        if (collector.kind == Collector.COLLECTING_AND_THEN) {
            Collector<? super T, Object, Object> down = (Collector<? super T, Object, Object>) collector.downstream;
            Function<Object, Object> finisher = (Function<Object, Object>) collector.finisher;
            Object collected = this.collect(down);
            return (R) finisher.apply(collected);
        }
        if (collector.kind == Collector.FILTERING) {
            Predicate<? super T> predicate = (Predicate<? super T>) collector.predicate;
            Collector<Object, Object, R> down = (Collector<Object, Object, R>) collector.downstream;
            ArrayList<Object> kept = new ArrayList<>();
            for (int i = 0; i < data.size(); i++) {
                T v = data.get(i);
                if (predicate.test(v)) {
                    kept.add(v);
                }
            }
            return new ListStream<>(kept).collect(down);
        }
        if (collector.kind == Collector.FLAT_MAPPING) {
            Function<? super T, ? extends Stream<?>> mapper =
                    (Function<? super T, ? extends Stream<?>>) collector.keyFn;
            Collector<Object, Object, R> down = (Collector<Object, Object, R>) collector.downstream;
            ArrayList<Object> flat = new ArrayList<>();
            for (int i = 0; i < data.size(); i++) {
                Stream<?> inner = mapper.apply(data.get(i));
                List<?> innerList = ((ListStream<?>) inner).toList();
                for (int j = 0; j < innerList.size(); j++) {
                    flat.add(innerList.get(j));
                }
            }
            return new ListStream<>(flat).collect(down);
        }
        if (collector.kind == Collector.MIN_BY || collector.kind == Collector.MAX_BY) {
            Comparator<? super T> comparator = (Comparator<? super T>) collector.comparator;
            if (data.size() == 0) {
                return (R) Optional.empty();
            }
            T best = data.get(0);
            for (int i = 1; i < data.size(); i++) {
                T v = data.get(i);
                int c = comparator.compare(v, best);
                if ((collector.kind == Collector.MIN_BY && c < 0)
                        || (collector.kind == Collector.MAX_BY && c > 0)) {
                    best = v;
                }
            }
            return (R) Optional.of(best);
        }
        if (collector.kind == Collector.TEEING) {
            Collector<? super T, Object, Object> d1 = (Collector<? super T, Object, Object>) collector.downstream;
            Collector<? super T, Object, Object> d2 = (Collector<? super T, Object, Object>) collector.downstream2;
            java.util.function.BiFunction<Object, Object, Object> merger =
                    (java.util.function.BiFunction<Object, Object, Object>) collector.merger;
            Object r1 = this.collect(d1);
            Object r2 = this.collect(d2);
            return (R) merger.apply(r1, r2);
        }
        if (collector.kind == Collector.SUMMING_INT) {
            java.util.function.ToIntFunction<? super T> fn =
                    (java.util.function.ToIntFunction<? super T>) collector.toIntFn;
            int sum = 0;
            for (int i = 0; i < data.size(); i++) {
                sum += fn.applyAsInt(data.get(i));
            }
            return (R) Integer.valueOf(sum);
        }
        if (collector.kind == Collector.SUMMING_LONG) {
            java.util.function.ToLongFunction<? super T> fn =
                    (java.util.function.ToLongFunction<? super T>) collector.toLongFn;
            long sum = 0L;
            for (int i = 0; i < data.size(); i++) {
                sum += fn.applyAsLong(data.get(i));
            }
            return (R) Long.valueOf(sum);
        }
        if (collector.kind == Collector.SUMMARIZING_INT) {
            java.util.function.ToIntFunction<? super T> fn =
                    (java.util.function.ToIntFunction<? super T>) collector.toIntFn;
            java.util.IntSummaryStatistics stats = new java.util.IntSummaryStatistics();
            for (int i = 0; i < data.size(); i++) {
                stats.accept(fn.applyAsInt(data.get(i)));
            }
            return (R) stats;
        }
        if (collector.kind == Collector.SUMMARIZING_LONG) {
            java.util.function.ToLongFunction<? super T> fn =
                    (java.util.function.ToLongFunction<? super T>) collector.toLongFn;
            java.util.LongSummaryStatistics stats = new java.util.LongSummaryStatistics();
            for (int i = 0; i < data.size(); i++) {
                stats.accept(fn.applyAsLong(data.get(i)));
            }
            return (R) stats;
        }
        if (collector.kind == Collector.JOINING_PREFIX_SUFFIX) {
            // Explicit StringBuilder (JBMC models append/toString soundly) — no invokedynamic concat.
            StringBuilder sb = new StringBuilder();
            sb.append(collector.prefix.toString());
            String sep = collector.delimiter.toString();
            for (int i = 0; i < data.size(); i++) {
                if (i > 0) {
                    sb.append(sep);
                }
                sb.append((CharSequence) data.get(i));
            }
            sb.append(collector.suffix.toString());
            return (R) sb.toString();
        }
        if (collector.kind == Collector.TO_COLLECTION) {
            java.util.function.Supplier<java.util.Collection<Object>> supplier =
                    (java.util.function.Supplier<java.util.Collection<Object>>) collector.supplier;
            java.util.Collection<Object> c = supplier.get();
            for (int i = 0; i < data.size(); i++) {
                c.add(data.get(i));
            }
            return (R) c;
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

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream)")
    public Object[] toArray() {
        Object[] out = new Object[data.size()];
        for (int i = 0; i < data.size(); i++) {
            out[i] = data.get(i);
        }
        return out;
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream)")
    public <A> A[] toArray(IntFunction<A[]> generator) {
        A[] out = generator.apply(data.size());
        for (int i = 0; i < data.size(); i++) {
            @SuppressWarnings("unchecked")
            A e = (A) data.get(i);
            out[i] = e;
        }
        return out;
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream)")
    public void forEachOrdered(Consumer<? super T> action) {
        // The eager model is already ordered; forEachOrdered == forEach over the encounter order.
        for (int i = 0; i < data.size(); i++) {
            action.accept(data.get(i));
        }
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream)")
    public <R> R collect(Supplier<R> supplier, BiConsumer<R, ? super T> accumulator, BiConsumer<R, R> combiner) {
        // Sequential mutable reduction: one container, accumulate each element. The combiner is only
        // exercised under parallel splitting (never here), exactly as the JDK leaves it for a
        // sequential pipeline.
        R container = supplier.get();
        for (int i = 0; i < data.size(); i++) {
            accumulator.accept(container, data.get(i));
        }
        return container;
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream)")
    public <U> U reduce(U identity, BiFunction<U, ? super T, U> accumulator, BinaryOperator<U> combiner) {
        // Sequential fold; the combiner is unused (only joins parallel partial results in the JDK).
        U result = identity;
        for (int i = 0; i < data.size(); i++) {
            result = accumulator.apply(result, data.get(i));
        }
        return result;
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream)")
    public <R> Stream<R> mapMulti(BiConsumer<? super T, ? super Consumer<R>> mapper) {
        ArrayList<R> out = new ArrayList<>();
        Consumer<R> sink = new Consumer<R>() {
            @Override
            public void accept(R r) {
                out.add(r);
            }
        };
        for (int i = 0; i < data.size(); i++) {
            mapper.accept(data.get(i), sink);
        }
        return new ListStream<>(out);
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream)")
    public IntStream mapMultiToInt(BiConsumer<? super T, ? super java.util.function.IntConsumer> mapper) {
        IntArrayStream s = new IntArrayStream();
        java.util.function.IntConsumer sink = new java.util.function.IntConsumer() {
            @Override
            public void accept(int v) {
                s.add(v);
            }
        };
        for (int i = 0; i < data.size(); i++) {
            mapper.accept(data.get(i), sink);
        }
        return s;
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream)")
    public LongStream mapMultiToLong(BiConsumer<? super T, ? super java.util.function.LongConsumer> mapper) {
        LongArrayStream s = new LongArrayStream();
        java.util.function.LongConsumer sink = new java.util.function.LongConsumer() {
            @Override
            public void accept(long v) {
                s.add(v);
            }
        };
        for (int i = 0; i < data.size(); i++) {
            mapper.accept(data.get(i), sink);
        }
        return s;
    }

    @Override
    @BmcModelConforms("@BmcProof (proofs.stream)")
    public DoubleStream mapMultiToDouble(BiConsumer<? super T, ? super java.util.function.DoubleConsumer> mapper) {
        DoubleArrayStream s = new DoubleArrayStream();
        java.util.function.DoubleConsumer sink = new java.util.function.DoubleConsumer() {
            @Override
            public void accept(double v) {
                s.add(v);
            }
        };
        for (int i = 0; i < data.size(); i++) {
            mapper.accept(data.get(i), sink);
        }
        return s;
    }
}
