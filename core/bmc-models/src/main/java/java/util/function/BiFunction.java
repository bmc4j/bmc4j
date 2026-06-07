package java.util.function;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcModelTail;

/**
 * BMC model of {@link java.util.function.BiFunction}. The SAM {@code apply(T,U)} is abstract (a
 * desugared lambda); the {@code andThen} default feeds the two-arg result into a follow-on
 * {@link Function}, modeled faithfully so {@code bf.andThen(f).apply(a, b)} analyses soundly.
 *
 * <p><b>Not relocated</b> (see {@link Function}). Conformance via model proofs (proofs.function). The
 * JDK default {@code Objects.requireNonNull}s {@code after} first; elided here (no Objects model) —
 * behavior for the non-null functions a proof composes is identical.
 */
@BmcModelTail(reason = "java.util.function.BiFunction has no surface beyond the SAM + the modeled andThen default; nothing else exists to be loud about")
@FunctionalInterface
public interface BiFunction<T, U, R> {

    @BmcModelConforms("@BmcProof (proofs.function BiFunctionLaws)")
    R apply(T t, U u);

    /** Apply this, then feed the result to {@code after}. */
    @BmcModelConforms("@BmcProof (proofs.function BiFunctionLaws)")
    default <V> BiFunction<T, U, V> andThen(Function<? super R, ? extends V> after) {
        return (T t, U u) -> after.apply(apply(t, u));
    }
}
