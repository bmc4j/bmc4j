package java.util.concurrent.atomic;

import java.util.function.BinaryOperator;
import java.util.function.UnaryOperator;

/** Sequential BMC model of {@link java.util.concurrent.atomic.AtomicReference} — a mutable holder. */
public class AtomicReference<V> {

    private V value;

    public AtomicReference() {
    }

    public AtomicReference(V initialValue) {
        this.value = initialValue;
    }

    public final V get() {
        return value;
    }

    public final void set(V newValue) {
        value = newValue;
    }

    public final void lazySet(V newValue) {
        value = newValue;
    }

    public final V getAndSet(V newValue) {
        V old = value;
        value = newValue;
        return old;
    }

    public final boolean compareAndSet(V expect, V update) {
        if (value == expect) {
            value = update;
            return true;
        }
        return false;
    }

    public final V updateAndGet(UnaryOperator<V> updateFunction) {
        value = updateFunction.apply(value);
        return value;
    }

    public final V getAndUpdate(UnaryOperator<V> updateFunction) {
        V old = value;
        value = updateFunction.apply(value);
        return old;
    }

    public final V accumulateAndGet(V x, BinaryOperator<V> f) {
        value = f.apply(value, x);
        return value;
    }
}
