package java.util;

import java.util.function.LongConsumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.stream.LongStream;

import org.bmc4j.models.audit.BmcModelConforms;

/**
 * Clean BMC model of {@link java.util.OptionalLong} — a {@code long} plus a present flag. Replaces the
 * real one on JBMC's analysis classpath only (the real JVM ignores {@code java.*} models). Sound and
 * trivially bounded: there is no collection to unwind. Mirrors {@link Optional} but for the primitive
 * {@code long}, so the {@code LongStream} terminal ops (min/max/reduce/findFirst/findAny) have a sound
 * audited return type without reintroducing {@code double} (the repo's no-double convention).
 *
 * <p>{@code equals}/{@code hashCode}/{@code toString} are the universal Object members the per-member
 * audit gate excludes from a model's real surface, so the full {@link java.util.OptionalLong} surface
 * is modeled here with no tail.
 */
public final class OptionalLong {

    private final long value;
    private final boolean present;

    private OptionalLong(long value, boolean present) {
        this.value = value;
        this.present = present;
    }

    private static final OptionalLong EMPTY = new OptionalLong(0L, false);

    @BmcModelConforms("differential (OptionalPrimitivesConformanceTest) + @BmcProof (proofs.optional OptionalLongLaws)")
    public static OptionalLong empty() {
        return EMPTY;
    }

    @BmcModelConforms("differential (OptionalPrimitivesConformanceTest) + @BmcProof (proofs.optional OptionalLongLaws)")
    public static OptionalLong of(long value) {
        return new OptionalLong(value, true);
    }

    @BmcModelConforms("differential (OptionalPrimitivesConformanceTest) + @BmcProof (proofs.optional OptionalLongLaws)")
    public long getAsLong() {
        if (!present) {
            throw new NoSuchElementException();
        }
        return value;
    }

    @BmcModelConforms("differential (OptionalPrimitivesConformanceTest) + @BmcProof (proofs.optional OptionalLongLaws)")
    public boolean isPresent() {
        return present;
    }

    @BmcModelConforms("differential (OptionalPrimitivesConformanceTest) + @BmcProof (proofs.optional OptionalLongLaws)")
    public boolean isEmpty() {
        return !present;
    }

    @BmcModelConforms("differential (OptionalPrimitivesConformanceTest) + @BmcProof (proofs.optional OptionalLongLaws)")
    public void ifPresent(LongConsumer action) {
        if (present) {
            action.accept(value);
        }
    }

    @BmcModelConforms("differential (OptionalPrimitivesConformanceTest) + @BmcProof (proofs.optional OptionalLongLaws)")
    public void ifPresentOrElse(LongConsumer action, Runnable emptyAction) {
        if (present) {
            action.accept(value);
        } else {
            emptyAction.run();
        }
    }

    /**
     * A stream of zero or one element, like the JDK. Sound: built eagerly from {@link LongStream}'s
     * bounded model ({@code empty()} when absent, {@code of(value)} when present), so it analyses with
     * the same intrinsics the stream models already provide — no extra hole.
     */
    @BmcModelConforms("differential (OptionalPrimitivesConformanceTest) + @BmcProof (proofs.optional OptionalLongLaws)")
    public LongStream stream() {
        return present ? LongStream.of(value) : LongStream.empty();
    }

    @BmcModelConforms("differential (OptionalPrimitivesConformanceTest) + @BmcProof (proofs.optional OptionalLongLaws)")
    public long orElse(long other) {
        return present ? value : other;
    }

    @BmcModelConforms("differential (OptionalPrimitivesConformanceTest) + @BmcProof (proofs.optional OptionalLongLaws)")
    public long orElseGet(LongSupplier supplier) {
        return present ? value : supplier.getAsLong();
    }

    @BmcModelConforms("differential (OptionalPrimitivesConformanceTest) + @BmcProof (proofs.optional OptionalLongLaws)")
    public long orElseThrow() {
        return getAsLong();
    }

    @BmcModelConforms("differential (OptionalPrimitivesConformanceTest) + @BmcProof (proofs.optional OptionalLongLaws)")
    public <X extends Throwable> long orElseThrow(Supplier<? extends X> exceptionSupplier) throws X {
        if (present) {
            return value;
        }
        throw exceptionSupplier.get();
    }
}
