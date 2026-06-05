package java.util.concurrent.atomic;

/** Sequential BMC model of {@link java.util.concurrent.atomic.AtomicBoolean} — a mutable boolean. */
public class AtomicBoolean {

    private boolean value;

    public AtomicBoolean() {
    }

    public AtomicBoolean(boolean initialValue) {
        this.value = initialValue;
    }

    public final boolean get() {
        return value;
    }

    public final void set(boolean newValue) {
        value = newValue;
    }

    public final void lazySet(boolean newValue) {
        value = newValue;
    }

    public final boolean getAndSet(boolean newValue) {
        boolean old = value;
        value = newValue;
        return old;
    }

    public final boolean compareAndSet(boolean expect, boolean update) {
        if (value == expect) {
            value = update;
            return true;
        }
        return false;
    }
}
