package java.util.function;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcModelTail;

/**
 * BMC model of {@link java.util.function.Consumer}. The SAM {@code accept} is abstract (a desugared
 * lambda); the {@code andThen} default sequences two consumers and is modeled faithfully so
 * {@code c1.andThen(c2).accept(x)} runs both side effects in order under JBMC.
 *
 * <p><b>Not relocated</b> (see {@link Function}). Conformance via model proofs (proofs.function). The
 * JDK default {@code Objects.requireNonNull}s {@code after} first; elided here (no Objects model) —
 * behavior for the non-null consumers a proof composes is identical.
 */
@BmcModelTail(reason = "java.util.function.Consumer has no surface beyond the SAM + the modeled andThen default; nothing else exists to be loud about")
@FunctionalInterface
public interface Consumer<T> {

    @BmcModelConforms("@BmcProof (proofs.function ConsumerLaws)")
    void accept(T t);

    /** Run this consumer, then {@code after}, on the same input (both side effects, in order). */
    @BmcModelConforms("@BmcProof (proofs.function ConsumerLaws)")
    default Consumer<T> andThen(Consumer<? super T> after) {
        return (T t) -> {
            accept(t);
            after.accept(t);
        };
    }
}
