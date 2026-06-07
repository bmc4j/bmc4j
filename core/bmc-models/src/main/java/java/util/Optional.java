package java.util;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.bmc4j.models.audit.BmcModelConforms;

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
    @BmcModelConforms("differential (OptionalArraysConformanceTest) + @BmcProof (proofs.optional)")
    public static <T> Optional<T> empty() {
        return (Optional<T>) EMPTY;
    }

    private static final Optional<?> EMPTY = new Optional<>(null, false);

    @BmcModelConforms("differential (OptionalArraysConformanceTest) + @BmcProof (proofs.optional)")
    public static <T> Optional<T> of(T value) {
        if (value == null) {
            throw new NullPointerException();
        }
        return new Optional<>(value, true);
    }

    @BmcModelConforms("differential (OptionalArraysConformanceTest) + @BmcProof (proofs.optional)")
    public static <T> Optional<T> ofNullable(T value) {
        return value == null ? empty() : of(value);
    }

    @BmcModelConforms("differential (OptionalArraysConformanceTest) + @BmcProof (proofs.optional)")
    public boolean isPresent() {
        return present;
    }

    @BmcModelConforms("differential (OptionalArraysConformanceTest) + @BmcProof (proofs.optional)")
    public boolean isEmpty() {
        return !present;
    }

    @BmcModelConforms("differential (OptionalArraysConformanceTest) + @BmcProof (proofs.optional)")
    public T get() {
        if (!present) {
            throw new NoSuchElementException();
        }
        return value;
    }

    @BmcModelConforms("differential (OptionalArraysConformanceTest) + @BmcProof (proofs.optional)")
    public T orElse(T other) {
        return present ? value : other;
    }

    @BmcModelConforms("differential (OptionalArraysConformanceTest) + @BmcProof (proofs.optional)")
    public T orElseGet(Supplier<? extends T> supplier) {
        return present ? value : supplier.get();
    }

    @BmcModelConforms("differential (OptionalArraysConformanceTest) + @BmcProof (proofs.optional)")
    public T orElseThrow() {
        return get();
    }

    @BmcModelConforms("differential (OptionalArraysConformanceTest) + @BmcProof (proofs.optional)")
    public <X extends Throwable> T orElseThrow(Supplier<? extends X> exceptionSupplier) throws X {
        if (present) {
            return value;
        }
        throw exceptionSupplier.get();
    }

    @BmcModelConforms("differential (OptionalArraysConformanceTest) + @BmcProof (proofs.optional)")
    public void ifPresent(Consumer<? super T> action) {
        if (present) {
            action.accept(value);
        }
    }

    @BmcModelConforms("differential (OptionalArraysConformanceTest) + @BmcProof (proofs.optional)")
    public void ifPresentOrElse(Consumer<? super T> action, Runnable emptyAction) {
        if (present) {
            action.accept(value);
        } else {
            emptyAction.run();
        }
    }

    @BmcModelConforms("differential (OptionalArraysConformanceTest) + @BmcProof (proofs.optional)")
    public <U> Optional<U> map(Function<? super T, ? extends U> mapper) {
        return present ? Optional.ofNullable(mapper.apply(value)) : Optional.empty();
    }

    @SuppressWarnings("unchecked")
    @BmcModelConforms("differential (OptionalArraysConformanceTest) + @BmcProof (proofs.optional)")
    public <U> Optional<U> flatMap(Function<? super T, ? extends Optional<? extends U>> mapper) {
        if (!present) {
            return empty();
        }
        // The JDK rejects a null Optional from the mapper (NPE) — mirror it, never silently empty.
        Optional<U> r = (Optional<U>) mapper.apply(value);
        if (r == null) {
            throw new NullPointerException();
        }
        return r;
    }

    @BmcModelConforms("differential (OptionalArraysConformanceTest) + @BmcProof (proofs.optional)")
    public Optional<T> filter(Predicate<? super T> predicate) {
        if (!present) {
            return this;
        }
        return predicate.test(value) ? this : empty();
    }

    @SuppressWarnings("unchecked")
    @BmcModelConforms("differential (OptionalArraysConformanceTest) + @BmcProof (proofs.optional)")
    public Optional<T> or(Supplier<? extends Optional<? extends T>> supplier) {
        if (present) {
            return this;
        }
        Optional<T> r = (Optional<T>) supplier.get();
        if (r == null) {
            throw new NullPointerException();
        }
        return r;
    }

    /**
     * A stream of zero or one element, like the JDK. Sound: the bounded {@link Stream} model is
     * built eagerly from {@code Stream.of(...)} (an empty stream when absent, a singleton when
     * present), so {@code optional.stream().filter(...).count()} analyses with the same intrinsics
     * the stream models already provide — no extra hole.
     */
    @SuppressWarnings("unchecked")
    @BmcModelConforms("differential (OptionalArraysConformanceTest) + @BmcProof (proofs.optional)")
    public Stream<T> stream() {
        return present ? Stream.of(value) : (Stream<T>) Stream.of();
    }
}
