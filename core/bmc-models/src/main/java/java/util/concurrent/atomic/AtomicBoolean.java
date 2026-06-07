package java.util.concurrent.atomic;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcModelTail;

/** Sequential BMC model of {@link java.util.concurrent.atomic.AtomicBoolean} — a mutable boolean. */
@BmcModelTail(reason = "VarHandle memory-ordering variants (getAcquire/getOpaque/getPlain/setOpaque/setPlain/setRelease/compareAndExchange*/weakCompareAndSet{Acquire,Release,Volatile}) collapse to the plain op under sequential analysis. All loud under JBMC")
public class AtomicBoolean {

    private boolean value;

    public AtomicBoolean() {
    }

    public AtomicBoolean(boolean initialValue) {
        this.value = initialValue;
    }

    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public final boolean get() {
        return value;
    }

    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public final void set(boolean newValue) {
        value = newValue;
    }

    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public final void lazySet(boolean newValue) {
        value = newValue;
    }

    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public final boolean getAndSet(boolean newValue) {
        boolean old = value;
        value = newValue;
        return old;
    }

    @BmcModelConforms("differential (ConcurrencyConformanceTest) + @BmcProof (proofs.concurrent)")
    public final boolean compareAndSet(boolean expect, boolean update) {
        if (value == expect) {
            value = update;
            return true;
        }
        return false;
    }
}
