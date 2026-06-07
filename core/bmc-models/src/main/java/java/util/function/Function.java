package java.util.function;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcModelTail;

/**
 * BMC model of {@link java.util.function.Function}. The SAM {@code apply} is abstract (supplied by a
 * desugared lambda / method reference); the {@code andThen}/{@code compose} <em>default</em> methods
 * are tiny pure compositions modeled faithfully here so {@code f.andThen(g).apply(x)} analyses
 * soundly under JBMC — it desugars to the two SAM calls in order, no nondet stub.
 *
 * <p><b>Not relocated.</b> Unlike the other java.* models, {@code java.util.function} is excluded from
 * the differential relocation (see {@code bmc-models-conformance} {@code isModel}): functional
 * interfaces are shared between the real JDK collection models and their lambdas, so relocating them
 * would break passing a real-JDK lambda to a model method. These models exist only to give JBMC sound
 * default-method bodies on the PROOF analysis classpath; conformance is validated by model proofs
 * (proofs.function), not the differential axis.
 *
 * <p>The JDK's defaults {@code Objects.requireNonNull} their argument first; that null-guard is
 * elided here (there is no Objects model, and dragging one in would put a nondet stub on the
 * composition path) — the modeled result for the non-null functions a proof composes is identical.
 * {@code identity()} returns {@code t -> t}, desugared like any lambda.
 */
@BmcModelTail(reason = "java.util.function.Function has no surface beyond the SAM + the two modeled composition defaults (andThen/compose) + identity; nothing else exists to be loud about")
@FunctionalInterface
public interface Function<T, R> {

    @BmcModelConforms("@BmcProof (proofs.function FunctionLaws)")
    R apply(T t);

    /** {@code (this) then (after)}: apply this, feed the result to {@code after}. */
    @BmcModelConforms("@BmcProof (proofs.function FunctionLaws)")
    default <V> Function<T, V> andThen(Function<? super R, ? extends V> after) {
        return (T t) -> after.apply(apply(t));
    }

    /** {@code (before) then (this)}: apply {@code before}, feed its result to this. */
    @BmcModelConforms("@BmcProof (proofs.function FunctionLaws)")
    default <V> Function<V, R> compose(Function<? super V, ? extends T> before) {
        return (V v) -> apply(before.apply(v));
    }

    /** The identity function {@code t -> t}. */
    @BmcModelConforms("@BmcProof (proofs.function FunctionLaws)")
    static <T> Function<T, T> identity() {
        return t -> t;
    }
}
