package java.util.concurrent.atomic;

import java.util.function.BinaryOperator;
import java.util.function.UnaryOperator;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcModelTail;

/** Sequential BMC model of {@link java.util.concurrent.atomic.AtomicReference} — a mutable holder. */
@BmcModelTail(reason = "VarHandle memory-ordering variants (getAcquire/getOpaque/getPlain/setOpaque/setPlain/setRelease/compareAndExchange*/weakCompareAndSet{Acquire,Release,Volatile}) collapse to the plain op under sequential analysis. All loud under JBMC")
public class AtomicReference<V> {

    private V value;

    public AtomicReference() {
    }

    public AtomicReference(V initialValue) {
        this.value = initialValue;
    }

    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public final V get() {
        return value;
    }

    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public final void set(V newValue) {
        value = newValue;
    }

    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public final void lazySet(V newValue) {
        value = newValue;
    }

    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public final V getAndSet(V newValue) {
        V old = value;
        value = newValue;
        return old;
    }

    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public final boolean compareAndSet(V expect, V update) {
        if (value == expect) {
            value = update;
            return true;
        }
        return false;
    }

    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public final V updateAndGet(UnaryOperator<V> updateFunction) {
        value = updateFunction.apply(value);
        return value;
    }

    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public final V getAndUpdate(UnaryOperator<V> updateFunction) {
        V old = value;
        value = updateFunction.apply(value);
        return old;
    }

    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public final V accumulateAndGet(V x, BinaryOperator<V> f) {
        value = f.apply(value, x);
        return value;
    }
}
