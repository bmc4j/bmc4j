package java.util.stream;

import java.util.DoubleSummaryStatistics;
import java.util.OptionalDouble;
import java.util.function.BiConsumer;
import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoublePredicate;
import java.util.function.DoubleToIntFunction;
import java.util.function.DoubleToLongFunction;
import java.util.function.DoubleUnaryOperator;
import java.util.function.ObjDoubleConsumer;
import java.util.function.Supplier;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcModelTail;
import org.bmc4j.models.audit.BmcUnmodelable;

import static org.bmc4j.analysis.BmcUnmodelledReached.fail;

/**
 * Minimal BMC model of {@link java.util.stream.DoubleStream}, eager over a bounded {@code double[]}.
 *
 * <p>Mirrors the {@code IntStream}/{@code LongStream} models. The "no-double convention" these were
 * authored under is dead: double <em>arithmetic</em> (addition in {@code sum}/{@code reduce}, the
 * division in {@code average}) and primitive double comparison ({@code <}/{@code >}) are bit-precise
 * sound under JBMC, so the additive/accumulating surface is fully modeled and audited.
 *
 * <p>The one wall that remains is FP TOTAL ORDER. {@link #min()}, {@link #max()} and {@link #sorted()}
 * are specified against {@code Double.compare} (NaN sorts greatest, {@code -0.0 < +0.0}), which routes
 * through {@code doubleToLongBits} — one of the only two unsound double ops under JBMC (the other is
 * dtoa / double-to-string). A primitive {@code <} model would silently diverge from the JDK contract on
 * NaN and signed zero, so those three are loud {@link BmcUnmodelable} rather than a quiet fiction.
 */
@BmcModelTail(reason = "the remaining DoubleStream surface (the infinite generate(supplier)/iterate(seed,next); mapMulti (nested DoubleMapMultiConsumer SAM); builder/iterator/spliterator; sequential/parallel lifecycle no-ops) is out of scope for this minimal eager model; loud under JBMC via the concrete impl")
public interface DoubleStream {

    @BmcModelConforms("@BmcProof (proofs.stream DoubleStreamLaws)")
    DoubleStream map(DoubleUnaryOperator op);

    @BmcModelConforms("@BmcProof (proofs.stream DoubleStreamLaws)")
    <U> Stream<U> mapToObj(DoubleFunction<? extends U> mapper);

    @BmcModelConforms("@BmcProof (proofs.stream DoubleStreamLaws)")
    IntStream mapToInt(DoubleToIntFunction mapper);

    @BmcModelConforms("@BmcProof (proofs.stream DoubleStreamLaws)")
    LongStream mapToLong(DoubleToLongFunction mapper);

    @BmcModelConforms("@BmcProof (proofs.stream DoubleStreamLaws)")
    DoubleStream filter(DoublePredicate predicate);

    @BmcModelConforms("@BmcProof (proofs.stream DoubleStreamLaws)")
    double sum();

    @BmcModelConforms("@BmcProof (proofs.stream DoubleStreamLaws)")
    OptionalDouble average();

    @BmcModelConforms("@BmcProof (proofs.stream DoubleStreamLaws)")
    DoubleSummaryStatistics summaryStatistics();

    @BmcModelConforms("@BmcProof (proofs.stream DoubleStreamLaws)")
    long count();

    @BmcModelConforms("@BmcProof (proofs.stream DoubleStreamLaws)")
    boolean anyMatch(DoublePredicate predicate);

    @BmcModelConforms("@BmcProof (proofs.stream DoubleStreamLaws)")
    boolean allMatch(DoublePredicate predicate);

    @BmcModelConforms("@BmcProof (proofs.stream DoubleStreamLaws)")
    boolean noneMatch(DoublePredicate predicate);

    @BmcModelConforms("@BmcProof (proofs.stream DoubleStreamLaws)")
    DoubleStream limit(long maxSize);

    @BmcModelConforms("@BmcProof (proofs.stream DoubleStreamLaws)")
    DoubleStream skip(long n);

    @BmcModelConforms("@BmcProof (proofs.stream DoubleStreamLaws)")
    DoubleStream takeWhile(DoublePredicate predicate);

    @BmcModelConforms("@BmcProof (proofs.stream DoubleStreamLaws)")
    DoubleStream dropWhile(DoublePredicate predicate);

    @BmcModelConforms("@BmcProof (proofs.stream DoubleStreamLaws)")
    DoubleStream distinct();

    @BmcModelConforms("@BmcProof (proofs.stream DoubleStreamLaws)")
    DoubleStream peek(DoubleConsumer action);

    @BmcModelConforms("@BmcProof (proofs.stream DoubleStreamLaws)")
    void forEach(DoubleConsumer action);

    @BmcModelConforms("@BmcProof (proofs.stream DoubleStreamLaws)")
    void forEachOrdered(DoubleConsumer action);

    @BmcModelConforms("@BmcProof (proofs.stream DoubleStreamLaws)")
    double reduce(double identity, DoubleBinaryOperator op);

    @BmcModelConforms("@BmcProof (proofs.stream DoubleStreamLaws)")
    OptionalDouble reduce(DoubleBinaryOperator op);

    @BmcModelConforms("@BmcProof (proofs.stream DoubleStreamLaws)")
    OptionalDouble findFirst();

    @BmcModelConforms("@BmcProof (proofs.stream DoubleStreamLaws)")
    OptionalDouble findAny();

    @BmcModelConforms("@BmcProof (proofs.stream DoubleStreamLaws)")
    double[] toArray();

    @BmcModelConforms("@BmcProof (proofs.stream DoubleStreamLaws)")
    Stream<Double> boxed();

    @BmcModelConforms("@BmcProof (proofs.stream DoubleStreamLaws)")
    DoubleStream flatMap(DoubleFunction<? extends DoubleStream> mapper);

    @BmcModelConforms("@BmcProof (proofs.stream DoubleStreamLaws)")
    <R> R collect(Supplier<R> supplier, ObjDoubleConsumer<R> accumulator, BiConsumer<R, R> combiner);

    // --- FP TOTAL-ORDER WALL: loud-if-reached, NOT a no-double leftover -------------------------------
    // min/max/sorted are defined against Double.compare's TOTAL order (NaN greatest, -0.0 < +0.0), which
    // goes through doubleToLongBits — an unsound double op under JBMC. A primitive `<` model would
    // silently disagree with the JDK on NaN / signed zero (the differential suite would catch it), so
    // these are loud @BmcUnmodelable instead of a quiet fiction. (sum/reduce/average above ARE sound:
    // plain double +/÷ is bit-precise under JBMC.)

    @BmcUnmodelable(reason = "DoubleStream.min is Double.compare TOTAL order (NaN greatest, -0.0<+0.0) via doubleToLongBits — the FP total-order wall, unsound under JBMC; a primitive < model would diverge from the JDK on NaN/signed zero")
    default OptionalDouble min() {
        throw fail("bmc4j: unmodelled member java.util.stream.DoubleStream.min() — Double.compare total order (NaN/-0.0) via doubleToLongBits is unsound under JBMC");
    }

    @BmcUnmodelable(reason = "DoubleStream.max is Double.compare TOTAL order (NaN greatest, -0.0<+0.0) via doubleToLongBits — the FP total-order wall, unsound under JBMC; a primitive > model would diverge from the JDK on NaN/signed zero")
    default OptionalDouble max() {
        throw fail("bmc4j: unmodelled member java.util.stream.DoubleStream.max() — Double.compare total order (NaN/-0.0) via doubleToLongBits is unsound under JBMC");
    }

    @BmcUnmodelable(reason = "DoubleStream.sorted is Double.compare TOTAL order (NaN greatest, -0.0<+0.0) via doubleToLongBits — the FP total-order wall, unsound under JBMC; a primitive-< sort would diverge from the JDK on NaN/signed zero")
    default DoubleStream sorted() {
        throw fail("bmc4j: unmodelled member java.util.stream.DoubleStream.sorted() — Double.compare total order (NaN/-0.0) via doubleToLongBits is unsound under JBMC");
    }

    @BmcModelConforms("@BmcProof (proofs.stream DoubleStreamLaws)")
    static DoubleStream empty() {
        return new DoubleArrayStream();
    }

    @BmcModelConforms("@BmcProof (proofs.stream DoubleStreamLaws)")
    static DoubleStream of(double value) {
        DoubleArrayStream s = new DoubleArrayStream();
        s.add(value);
        return s;
    }

    @BmcModelConforms("@BmcProof (proofs.stream DoubleStreamLaws)")
    static DoubleStream of(double... values) {
        DoubleArrayStream s = new DoubleArrayStream();
        for (double v : values) {
            s.add(v);
        }
        return s;
    }

    @BmcModelConforms("@BmcProof (proofs.stream DoubleStreamLaws)")
    static DoubleStream concat(DoubleStream a, DoubleStream b) {
        DoubleArrayStream s = new DoubleArrayStream();
        // Cast to the sole final implementor before draining: an invokevirtual on the concrete
        // DoubleArrayStream, not an invokeinterface on DoubleStream. The interface dispatch is the
        // kotlinc-version-fragile devirtualization behind the #169 false-REFUTED family.
        double[] aa = ((DoubleArrayStream) a).toArray();
        for (int i = 0; i < aa.length; i++) {
            s.add(aa[i]);
        }
        double[] bb = ((DoubleArrayStream) b).toArray();
        for (int i = 0; i < bb.length; i++) {
            s.add(bb[i]);
        }
        return s;
    }

    /**
     * The FINITE 3-arg iterate (seed + {@code hasNext} predicate + {@code next}). Bounded and sound;
     * the infinite 2-arg {@code iterate}/{@code generate} stay in the tail (would never terminate).
     */
    @BmcModelConforms("@BmcProof (proofs.stream DoubleStreamLaws)")
    static DoubleStream iterate(double seed, DoublePredicate hasNext, DoubleUnaryOperator next) {
        DoubleArrayStream s = new DoubleArrayStream();
        double cur = seed;
        while (hasNext.test(cur)) {
            s.add(cur);
            cur = next.applyAsDouble(cur);
        }
        return s;
    }
}
