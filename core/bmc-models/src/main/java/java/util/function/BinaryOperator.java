package java.util.function;

import static org.bmc4j.analysis.BmcUnmodelledReached.fail;

import java.util.Comparator;

import org.bmc4j.models.audit.BmcUnmodelable;

/**
 * BMC model of {@link java.util.function.BinaryOperator}: a {@link BiFunction} whose two arguments
 * and result share a type. Modeled so that types referencing {@code BinaryOperator} (e.g.
 * {@code Stream.reduce(T, BinaryOperator)}) resolve {@code apply} to the modeled {@link BiFunction}
 * SAM rather than the JDK's unmodeled one. It adds no own abstract member; the {@code minBy}/
 * {@code maxBy} statics take a {@link java.util.Comparator} (unmodeled) and stay loud per-member.
 *
 * <p><b>Not relocated</b> (see {@link Function}). Conformance via model proofs (proofs.stream, which
 * exercise reduce over a BinaryOperator lambda).
 */
@FunctionalInterface
public interface BinaryOperator<T> extends BiFunction<T, T, T> {

    /**
     * Returns the lesser of two elements per the supplied {@code comparator} — loud: a comparator over
     * the bounded operands has no modeled total order here.
     */
    @BmcUnmodelable(reason = "minBy folds the operands through a Comparator (unmodeled total order) — loud under JBMC")
    static <T> BinaryOperator<T> minBy(Comparator<? super T> comparator) {
        throw fail("bmc4j: unmodelled member java.util.function.BinaryOperator.minBy(java.util.Comparator) — folds the operands through a Comparator (unmodeled total order)");
    }

    /**
     * Returns the greater of two elements per the supplied {@code comparator} — loud: a comparator over
     * the bounded operands has no modeled total order here.
     */
    @BmcUnmodelable(reason = "maxBy folds the operands through a Comparator (unmodeled total order) — loud under JBMC")
    static <T> BinaryOperator<T> maxBy(Comparator<? super T> comparator) {
        throw fail("bmc4j: unmodelled member java.util.function.BinaryOperator.maxBy(java.util.Comparator) — folds the operands through a Comparator (unmodeled total order)");
    }
}
