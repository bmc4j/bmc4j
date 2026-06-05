package java.util;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Clean BMC model of {@link java.util.Optional} — a value plus a present flag. Replaces the real
 * one on JBMC's analysis classpath only (the real JVM ignores {@code java.*} models). Sound and
 * trivially bounded: there is no collection to unwind.
 */
public final class Optional<T> {

    private final T value;
    private final boolean present;

    private Optional(T value, boolean present) {
        this.value = value;
        this.present = present;
    }

    @SuppressWarnings("unchecked")
    public static <T> Optional<T> empty() {
        return (Optional<T>) EMPTY;
    }

    private static final Optional<?> EMPTY = new Optional<>(null, false);

    public static <T> Optional<T> of(T value) {
        if (value == null) {
            throw new NullPointerException();
        }
        return new Optional<>(value, true);
    }

    public static <T> Optional<T> ofNullable(T value) {
        return value == null ? empty() : of(value);
    }

    public boolean isPresent() {
        return present;
    }

    public boolean isEmpty() {
        return !present;
    }

    public T get() {
        if (!present) {
            throw new NoSuchElementException();
        }
        return value;
    }

    public T orElse(T other) {
        return present ? value : other;
    }

    public T orElseGet(Supplier<? extends T> supplier) {
        return present ? value : supplier.get();
    }

    public T orElseThrow() {
        return get();
    }

    public void ifPresent(Consumer<? super T> action) {
        if (present) {
            action.accept(value);
        }
    }

    public <U> Optional<U> map(Function<? super T, ? extends U> mapper) {
        return present ? Optional.ofNullable(mapper.apply(value)) : Optional.empty();
    }

    public Optional<T> filter(Predicate<? super T> predicate) {
        if (!present) {
            return this;
        }
        return predicate.test(value) ? this : empty();
    }
}
