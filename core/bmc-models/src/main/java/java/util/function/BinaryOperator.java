package java.util.function;

import org.bmc4j.models.audit.BmcModelTail;

/**
 * BMC model of {@link java.util.function.BinaryOperator}: a {@link BiFunction} whose two arguments
 * and result share a type. Modeled so that types referencing {@code BinaryOperator} (e.g.
 * {@code Stream.reduce(T, BinaryOperator)}) resolve {@code apply} to the modeled {@link BiFunction}
 * SAM rather than the JDK's unmodeled one. It adds no own abstract member; the {@code minBy}/
 * {@code maxBy} statics take a {@link java.util.Comparator} (unmodeled) and stay in the loud tail.
 *
 * <p><b>Not relocated</b> (see {@link Function}). Conformance via model proofs (proofs.stream, which
 * exercise reduce over a BinaryOperator lambda).
 */
@BmcModelTail(reason = "BinaryOperator's apply is the inherited BiFunction SAM; its only own surface is the minBy/maxBy statics, which take a Comparator (unmodeled) — loud under JBMC")
@FunctionalInterface
public interface BinaryOperator<T> extends BiFunction<T, T, T> {
}
