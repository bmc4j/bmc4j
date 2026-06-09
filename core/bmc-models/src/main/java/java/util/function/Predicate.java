package java.util.function;

import org.bmc4j.models.audit.BmcModelConforms;

/**
 * BMC model of {@link java.util.function.Predicate}. The SAM {@code test} is abstract (a desugared
 * lambda); the boolean-combinator defaults ({@code and}/{@code or}/{@code negate}) and the
 * {@code isEqual}/{@code not} statics are modeled faithfully so predicate-composition chains like
 * {@code p.and(q).negate().test(x)} analyse soundly under JBMC.
 *
 * <p><b>Not relocated</b> (see {@link Function} — {@code java.util.function} is excluded from the
 * differential relocation). Conformance is validated by model proofs (proofs.function), not the
 * differential axis. {@code and}/{@code or} short-circuit exactly as the JDK's defaults do. The JDK's
 * defaults {@code Objects.requireNonNull} the other predicate first; that null-guard is elided here
 * (no Objects model) — the modeled result for the non-null predicates a proof composes is identical.
 */
@FunctionalInterface
public interface Predicate<T> {

    @BmcModelConforms("@BmcProof (proofs.function PredicateLaws)")
    boolean test(T t);

    /** Short-circuiting logical AND of this predicate and {@code other}. */
    @BmcModelConforms("@BmcProof (proofs.function PredicateLaws)")
    default Predicate<T> and(Predicate<? super T> other) {
        return (T t) -> test(t) && other.test(t);
    }

    /** Logical negation of this predicate. */
    @BmcModelConforms("@BmcProof (proofs.function PredicateLaws)")
    default Predicate<T> negate() {
        return (T t) -> !test(t);
    }

    /** Short-circuiting logical OR of this predicate and {@code other}. */
    @BmcModelConforms("@BmcProof (proofs.function PredicateLaws)")
    default Predicate<T> or(Predicate<? super T> other) {
        return (T t) -> test(t) || other.test(t);
    }

    /**
     * A predicate testing equality against {@code targetRef}: null-safe value equality, mirroring the
     * JDK's {@code Objects.equals(targetRef, t)} (inlined here — there is no Objects model).
     */
    @BmcModelConforms("@BmcProof (proofs.function PredicateLaws)")
    static <T> Predicate<T> isEqual(Object targetRef) {
        return (T t) -> (targetRef == null) ? (t == null) : targetRef.equals(t);
    }

    /** The negation of {@code target}. */
    @BmcModelConforms("@BmcProof (proofs.function PredicateLaws)")
    @SuppressWarnings("unchecked")
    static <T> Predicate<T> not(Predicate<? super T> target) {
        return (Predicate<T>) target.negate();
    }
}
