package java.util;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import org.bmc4j.models.audit.BmcModelConforms;

/**
 * Clean BMC model of {@link java.util.OptionalInt} — an {@code int} plus a present flag. Replaces the
 * real one on JBMC's analysis classpath only (the real JVM ignores {@code java.*} models). Sound and
 * trivially bounded: there is no collection to unwind. Mirrors {@link Optional} but for the primitive
 * {@code int}, so the {@code IntStream} terminal ops (min/max/reduce/findFirst/findAny) have a sound
 * audited return type without reintroducing {@code double} (the repo's no-double convention).
 *
 * <p>{@code equals}/{@code hashCode}/{@code toString} are the universal Object members the per-member
 * audit gate excludes from a model's real surface, so the full {@link java.util.OptionalInt} surface
 * is modeled here with no tail.
 */
public final class OptionalInt {

    private final int value;
    private final boolean present;

    private OptionalInt(int value, boolean present) {
        this.value = value;
        this.present = present;
    }

    private static final OptionalInt EMPTY = new OptionalInt(0, false);

    @BmcModelConforms("differential (OptionalPrimitivesConformanceTest) + @BmcProof (proofs.optional OptionalIntLaws)")
    public static OptionalInt empty() {
        return EMPTY;
    }

    @BmcModelConforms("differential (OptionalPrimitivesConformanceTest) + @BmcProof (proofs.optional OptionalIntLaws)")
    public static OptionalInt of(int value) {
        return new OptionalInt(value, true);
    }

    @BmcModelConforms("differential (OptionalPrimitivesConformanceTest) + @BmcProof (proofs.optional OptionalIntLaws)")
    public int getAsInt() {
        if (!present) {
            throw new NoSuchElementException();
        }
        return value;
    }

    @BmcModelConforms("differential (OptionalPrimitivesConformanceTest) + @BmcProof (proofs.optional OptionalIntLaws)")
    public boolean isPresent() {
        return present;
    }

    @BmcModelConforms("differential (OptionalPrimitivesConformanceTest) + @BmcProof (proofs.optional OptionalIntLaws)")
    public boolean isEmpty() {
        return !present;
    }

    @BmcModelConforms("differential (OptionalPrimitivesConformanceTest) + @BmcProof (proofs.optional OptionalIntLaws)")
    public void ifPresent(IntConsumer action) {
        if (present) {
            action.accept(value);
        }
    }

    @BmcModelConforms("differential (OptionalPrimitivesConformanceTest) + @BmcProof (proofs.optional OptionalIntLaws)")
    public void ifPresentOrElse(IntConsumer action, Runnable emptyAction) {
        if (present) {
            action.accept(value);
        } else {
            emptyAction.run();
        }
    }

    /**
     * A stream of zero or one element, like the JDK. Sound: built eagerly from {@link IntStream}'s
     * bounded model ({@code empty()} when absent, {@code of(value)} when present), so it analyses with
     * the same intrinsics the stream models already provide — no extra hole.
     */
    @BmcModelConforms("differential (OptionalPrimitivesConformanceTest) + @BmcProof (proofs.optional OptionalIntLaws)")
    public IntStream stream() {
        return present ? IntStream.of(value) : IntStream.empty();
    }

    @BmcModelConforms("differential (OptionalPrimitivesConformanceTest) + @BmcProof (proofs.optional OptionalIntLaws)")
    public int orElse(int other) {
        return present ? value : other;
    }

    @BmcModelConforms("differential (OptionalPrimitivesConformanceTest) + @BmcProof (proofs.optional OptionalIntLaws)")
    public int orElseGet(IntSupplier supplier) {
        return present ? value : supplier.getAsInt();
    }

    @BmcModelConforms("differential (OptionalPrimitivesConformanceTest) + @BmcProof (proofs.optional OptionalIntLaws)")
    public int orElseThrow() {
        return getAsInt();
    }

    @BmcModelConforms("differential (OptionalPrimitivesConformanceTest) + @BmcProof (proofs.optional OptionalIntLaws)")
    public <X extends Throwable> int orElseThrow(Supplier<? extends X> exceptionSupplier) throws X {
        if (present) {
            return value;
        }
        throw exceptionSupplier.get();
    }
}
