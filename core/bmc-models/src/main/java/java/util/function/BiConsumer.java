package java.util.function;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcModelTail;

/**
 * BMC model of {@link java.util.function.BiConsumer}. The SAM {@code accept(T,U)} is abstract (a
 * desugared lambda); the {@code andThen} default sequences two bi-consumers, modeled faithfully so
 * {@code bc1.andThen(bc2).accept(a, b)} runs both side effects in order under JBMC.
 *
 * <p><b>Not relocated</b> (see {@link Function}). Conformance via model proofs (proofs.function). The
 * JDK default {@code Objects.requireNonNull}s {@code after} first; elided here (no Objects model) —
 * behavior for the non-null consumers a proof composes is identical.
 */
@BmcModelTail(reason = "java.util.function.BiConsumer has no surface beyond the SAM + the modeled andThen default; nothing else exists to be loud about")
@FunctionalInterface
public interface BiConsumer<T, U> {

    @BmcModelConforms("@BmcProof (proofs.function BiConsumerLaws)")
    void accept(T t, U u);

    /** Run this bi-consumer, then {@code after}, on the same pair (both side effects, in order). */
    @BmcModelConforms("@BmcProof (proofs.function BiConsumerLaws)")
    default BiConsumer<T, U> andThen(BiConsumer<? super T, ? super U> after) {
        return (T t, U u) -> {
            accept(t, u);
            after.accept(t, u);
        };
    }
}
