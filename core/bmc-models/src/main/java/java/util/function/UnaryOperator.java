package java.util.function;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcModelTail;

/**
 * BMC model of {@link java.util.function.UnaryOperator}: a {@link Function} whose argument and
 * result are the same type. It inherits the modeled {@code apply}/{@code andThen}/{@code compose}
 * from {@link Function}; its only own member is the {@code identity()} static, modeled faithfully
 * ({@code t -> t}) so {@code UnaryOperator.identity().apply(x) == x} under JBMC.
 *
 * <p><b>Not relocated</b> (see {@link Function}). Conformance via model proofs (proofs.function).
 */
@BmcModelTail(reason = "java.util.function.UnaryOperator has no surface beyond the inherited Function members + its own identity() static; nothing else exists to be loud about")
@FunctionalInterface
public interface UnaryOperator<T> extends Function<T, T> {

    /** The identity unary operator {@code t -> t}. */
    @BmcModelConforms("@BmcProof (proofs.function FunctionLaws)")
    static <T> UnaryOperator<T> identity() {
        return t -> t;
    }
}
