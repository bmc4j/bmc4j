package kotlin;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcModelTail;

/**
 * Clean model of Kotlin's {@code kotlin.Result<T>} {@code @JvmInline value class}. Like
 * {@link kotlin.time.Duration}, {@code Result} is a value class — it is erased at the JVM ABI to its single
 * backing field (here an {@code Object value}) and its members become {@code static} methods over that
 * erased value with kotlinc-mangled names ({@code constructor-impl}, {@code isFailure-impl}, …). A
 * <em>success</em> flows as the raw value itself; a <em>failure</em> is carried by the {@link Failure}
 * wrapper holding the throwable. The mangled {@code -impl} names aren't legal Java identifiers, so — exactly
 * as for {@code Duration} — this model is authored with legal placeholder names that the module build's
 * {@code renameDurationAbi} ASM pass rewrites to the real dashed ABI names (carrying the
 * {@link BmcModelConforms} annotation along), so a consumer's
 * {@code invokestatic kotlin/Result."isFailure-impl":(Ljava/lang/Object;)Z} resolves to a modeled body
 * instead of a JBMC nondet stub.
 *
 * <p><b>Modeled (non-inline ABI surface):</b> {@code constructor-impl} (identity — the value is already the
 * erased carrier), {@code isFailure-impl}/{@code isSuccess-impl} (failure iff the carrier is a
 * {@link Failure}), and {@code exceptionOrNull-impl} (the carried throwable, or {@code null} on success).
 *
 * <p><b>Documented holes / why others need nothing:</b> {@code getOrNull}, {@code getOrThrow},
 * {@code fold}/{@code map}/{@code mapCatching}/{@code recover}/{@code onSuccess}/{@code onFailure} and the
 * {@code Companion.success}/{@code Companion.failure} constructors are all {@code @InlineOnly} (the
 * {@code getOrNull-impl} and {@code Companion.success}/{@code .failure} ABI methods are {@code private}):
 * the compiler inlines them straight into the caller, so there is no ABI method to model — verified by
 * reflection over kotlin-stdlib (they are absent from the public surface). {@code toString-impl},
 * {@code equals-impl}/{@code equals-impl0}, {@code hashCode-impl}, {@code box-impl}/{@code unbox-impl} and
 * the {@code getValue} accessor are the value-class boxing/identity remainder absorbed by the tail below;
 * loud under JBMC if reached. The {@link Failure} carrier + {@link ResultKt#throwOnFailure} remain modeled
 * for the coroutine resume plumbing (the original reason this class existed).
 */
@BmcModelTail(reason = "kotlin.Result value-class boxing/identity remainder under the mangled JVM ABI — "
        + "toString-impl formatting, equals/hashCode/box/unbox value-class identity, getValue accessor; "
        + "the getOrNull/fold/map/recover and Companion.success/failure surface is @InlineOnly (no ABI "
        + "method to model); loud under JBMC if reached")
public final class Result {

    private Result() {
    }

    /**
     * Failure carrier: a success value flows as the raw object, a failure is wrapped here. Mirrors the real
     * {@code kotlin.Result$Failure} (public final {@code Throwable exception}, single-arg constructor); used
     * by generated coroutine resume code and {@link ResultKt#throwOnFailure}.
     */
    public static final class Failure {
        public final Throwable exception;

        public Failure(Throwable exception) {
            this.exception = exception;
        }
    }

    // ---- value-class ABI (static over the erased Object carrier; renamed to the mangled -impl names) ----

    /** Identity: the supplied value IS the erased carrier (success raw, or a {@link Failure}). */
    @BmcModelConforms("@BmcProof (proofs.kotlinresult)")
    public static Object constructorImpl(Object value) {
        return value;
    }

    @BmcModelConforms("@BmcProof (proofs.kotlinresult)")
    public static boolean isFailure(Object value) {
        return value instanceof Failure;
    }

    @BmcModelConforms("@BmcProof (proofs.kotlinresult)")
    public static boolean isSuccess(Object value) {
        return !(value instanceof Failure);
    }

    @BmcModelConforms("@BmcProof (proofs.kotlinresult)")
    public static Throwable exceptionOrNull(Object value) {
        if (value instanceof Failure) {
            return ((Failure) value).exception;
        }
        return null;
    }
}
