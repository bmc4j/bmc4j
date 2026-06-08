package java.util;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;
import java.util.stream.DoubleStream;

import org.bmc4j.models.audit.BmcModelConforms;

/**
 * Clean BMC model of {@link java.util.OptionalDouble} — a {@code double} plus a present flag. Replaces
 * the real one on JBMC's analysis classpath only (the real JVM ignores {@code java.*} models). Sound and
 * trivially bounded: there is no collection to unwind. Mirrors {@link OptionalInt}/{@link OptionalLong}
 * for the primitive {@code double}, so the {@code DoubleStream} terminal ops (average/reduce/findFirst/
 * findAny) have a sound audited return type.
 *
 * <p>The "no-double convention" these primitive Optionals were authored under is dead: a {@code double}
 * value plus a flag, primitive {@code double} comparison, and {@code ==}-on-present-flag are all
 * bit-precise sound under JBMC. There is no {@code Double.compare} / {@code doubleToLongBits} anywhere in
 * this model, so no FP total-order hole.
 *
 * <p>{@code equals}/{@code hashCode}/{@code toString} are the universal Object members the per-member
 * audit gate excludes from a model's real surface, so the full {@link java.util.OptionalDouble} surface
 * is modeled here with no tail.
 */
public final class OptionalDouble {

    private final double value;
    private final boolean present;

    private OptionalDouble(double value, boolean present) {
        this.value = value;
        this.present = present;
    }

    private static final OptionalDouble EMPTY = new OptionalDouble(0.0, false);

    @BmcModelConforms("differential (OptionalPrimitivesConformanceTest) + @BmcProof (proofs.optional OptionalDoubleLaws)")
    public static OptionalDouble empty() {
        return EMPTY;
    }

    @BmcModelConforms("differential (OptionalPrimitivesConformanceTest) + @BmcProof (proofs.optional OptionalDoubleLaws)")
    public static OptionalDouble of(double value) {
        return new OptionalDouble(value, true);
    }

    @BmcModelConforms("differential (OptionalPrimitivesConformanceTest) + @BmcProof (proofs.optional OptionalDoubleLaws)")
    public double getAsDouble() {
        if (!present) {
            throw new NoSuchElementException();
        }
        return value;
    }

    @BmcModelConforms("differential (OptionalPrimitivesConformanceTest) + @BmcProof (proofs.optional OptionalDoubleLaws)")
    public boolean isPresent() {
        return present;
    }

    @BmcModelConforms("differential (OptionalPrimitivesConformanceTest) + @BmcProof (proofs.optional OptionalDoubleLaws)")
    public boolean isEmpty() {
        return !present;
    }

    @BmcModelConforms("differential (OptionalPrimitivesConformanceTest) + @BmcProof (proofs.optional OptionalDoubleLaws)")
    public void ifPresent(DoubleConsumer action) {
        if (present) {
            action.accept(value);
        }
    }

    @BmcModelConforms("differential (OptionalPrimitivesConformanceTest) + @BmcProof (proofs.optional OptionalDoubleLaws)")
    public void ifPresentOrElse(DoubleConsumer action, Runnable emptyAction) {
        if (present) {
            action.accept(value);
        } else {
            emptyAction.run();
        }
    }

    /**
     * A stream of zero or one element, like the JDK. Sound: built eagerly from {@link DoubleStream}'s
     * bounded model ({@code empty()} when absent, {@code of(value)} when present), so it analyses with
     * the same intrinsics the stream models already provide — no extra hole.
     */
    @BmcModelConforms("differential (OptionalPrimitivesConformanceTest) + @BmcProof (proofs.optional OptionalDoubleLaws)")
    public DoubleStream stream() {
        return present ? DoubleStream.of(value) : DoubleStream.empty();
    }

    @BmcModelConforms("differential (OptionalPrimitivesConformanceTest) + @BmcProof (proofs.optional OptionalDoubleLaws)")
    public double orElse(double other) {
        return present ? value : other;
    }

    @BmcModelConforms("differential (OptionalPrimitivesConformanceTest) + @BmcProof (proofs.optional OptionalDoubleLaws)")
    public double orElseGet(DoubleSupplier supplier) {
        return present ? value : supplier.getAsDouble();
    }

    @BmcModelConforms("differential (OptionalPrimitivesConformanceTest) + @BmcProof (proofs.optional OptionalDoubleLaws)")
    public double orElseThrow() {
        return getAsDouble();
    }

    @BmcModelConforms("differential (OptionalPrimitivesConformanceTest) + @BmcProof (proofs.optional OptionalDoubleLaws)")
    public <X extends Throwable> double orElseThrow(Supplier<? extends X> exceptionSupplier) throws X {
        if (present) {
            return value;
        }
        throw exceptionSupplier.get();
    }
}
