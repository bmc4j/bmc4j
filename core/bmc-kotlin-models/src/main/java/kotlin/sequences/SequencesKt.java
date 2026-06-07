package kotlin.sequences;

import static org.bmc4j.analysis.BmcUnmodelledReached.fail;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcModelTail;
import org.bmc4j.models.audit.BmcNotNeeded;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import kotlin.jvm.functions.Function1;

/**
 * Clean model of Kotlin's {@code kotlin.sequences.SequencesKt} facade. Kotlin sequence ops
 * ({@code sequenceOf}, {@code asSequence}, {@code map}/{@code filter}, terminal {@code toList}/
 * {@code sumOfInt}/{@code count}) route through this facade plus kotlin-stdlib internals that JBMC
 * stubs to nondet (silently unsound). This replacement evaluates eagerly over bmc4j's
 * bounded {@link ListSequence}/{@code ArrayList} models, so a sequence pipeline analyses soundly.
 *
 * <p>The exact descriptors modeled (discovered empirically via {@code javap} on compiled proofs):
 * <pre>
 *   sequenceOf([Ljava/lang/Object;)Lkotlin/sequences/Sequence;
 *   map(Lkotlin/sequences/Sequence;Lkotlin/jvm/functions/Function1;)Lkotlin/sequences/Sequence;
 *   filter(Lkotlin/sequences/Sequence;Lkotlin/jvm/functions/Function1;)Lkotlin/sequences/Sequence;
 *   toList(Lkotlin/sequences/Sequence;)Ljava/util/List;
 *   sumOfInt(Lkotlin/sequences/Sequence;)I
 *   count(Lkotlin/sequences/Sequence;)I
 * </pre>
 * ({@code asSequence(Iterable)} lives on {@code CollectionsKt}.) Intermediate ops call the
 * {@code Function1} argument, which bmc4j desugars from the lambda, so the user predicate/mapper is
 * actually applied — not stubbed.
 */
@BmcModelTail(reason = "exotic SequencesKt facade remainder — the bulk of kotlin-stdlib's lazy-sequence "
        + "operators/generators the bounded proofs do not exercise; loud under JBMC if reached")
public final class SequencesKt {

    private SequencesKt() {
    }

    @SafeVarargs
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> Sequence<T> sequenceOf(T... elements) {
        return new ListSequence<>(elements);
    }

    @SuppressWarnings("unchecked")
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T, R> Sequence<R> map(Sequence<T> source, Function1<? super T, ? extends R> transform) {
        ArrayList<R> out = new ArrayList<>();
        Iterator<T> it = source.iterator();
        while (it.hasNext()) {
            out.add((R) transform.invoke(it.next()));
        }
        return new ListSequence<>(out);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> Sequence<T> filter(Sequence<T> source, Function1<? super T, Boolean> predicate) {
        ArrayList<T> out = new ArrayList<>();
        Iterator<T> it = source.iterator();
        while (it.hasNext()) {
            T v = it.next();
            if (predicate.invoke(v)) {
                out.add(v);
            }
        }
        return new ListSequence<>(out);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> List<T> toList(Sequence<T> source) {
        ArrayList<T> out = new ArrayList<>();
        Iterator<T> it = source.iterator();
        while (it.hasNext()) {
            out.add(it.next());
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int sumOfInt(Sequence<Integer> source) {
        int sum = 0;
        Iterator<Integer> it = source.iterator();
        while (it.hasNext()) {
            sum += it.next();
        }
        return sum;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> int count(Sequence<T> source) {
        int n = 0;
        Iterator<T> it = source.iterator();
        while (it.hasNext()) {
            it.next();
            n++;
        }
        return n;
    }

    // ---- take(n) / drop(n): the Kotlin compiler emits
    //   take(Lkotlin/sequences/Sequence;I)Lkotlin/sequences/Sequence;
    //   drop(Lkotlin/sequences/Sequence;I)Lkotlin/sequences/Sequence;
    // The real chain is lazy (TakeSequence/DropSequence) backed by kotlin-stdlib internals JBMC
    // stubs. We evaluate eagerly into a fresh bounded ListSequence. Kotlin's contract: a negative n
    // throws IllegalArgumentException; take(n) yields the first min(n, len) elements; drop(n) yields
    // the rest after the first min(n, len) — both in order.

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> Sequence<T> take(Sequence<T> source, int n) {
        if (n < 0) {
            throw new IllegalArgumentException("Requested element count " + n + " is less than zero.");
        }
        ArrayList<T> out = new ArrayList<>();
        if (n > 0) {
            int taken = 0;
            Iterator<T> it = source.iterator();
            while (it.hasNext()) {
                out.add(it.next());
                taken++;
                if (taken == n) {
                    break;
                }
            }
        }
        return new ListSequence<>(out);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> Sequence<T> drop(Sequence<T> source, int n) {
        if (n < 0) {
            throw new IllegalArgumentException("Requested element count " + n + " is less than zero.");
        }
        ArrayList<T> out = new ArrayList<>();
        int dropped = 0;
        Iterator<T> it = source.iterator();
        while (it.hasNext()) {
            T v = it.next();
            if (dropped < n) {
                dropped++;
            } else {
                out.add(v);
            }
        }
        return new ListSequence<>(out);
    }

    // ---- distinct(): the Kotlin compiler emits
    //   distinct(Lkotlin/sequences/Sequence;)Lkotlin/sequences/Sequence;
    // Kotlin's contract: distinct elements in first-occurrence order (dedup via equals). Eager over a
    // bounded LinkedHashSet (dedups via equals, iterates in insertion order in the model).
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> Sequence<T> distinct(Sequence<T> source) {
        LinkedHashSet<T> seen = new LinkedHashSet<>();
        for (Iterator<T> it = source.iterator(); it.hasNext(); ) {
            seen.add(it.next());
        }
        ArrayList<T> out = new ArrayList<>();
        for (Iterator<T> it = seen.iterator(); it.hasNext(); ) {
            out.add(it.next());
        }
        return new ListSequence<>(out);
    }

    // ---- flatMap { }: the Kotlin compiler emits
    //   flatMap(Lkotlin/sequences/Sequence;Lkotlin/jvm/functions/Function1;)Lkotlin/sequences/Sequence;
    // where the transform yields a Sequence per element; we concatenate them in order. The transform
    // is the user lambda (desugared by bmc4j), so it's genuinely applied, not stubbed.
    @SuppressWarnings("unchecked")
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T, R> Sequence<R> flatMap(
            Sequence<T> source, Function1<? super T, ? extends Sequence<? extends R>> transform) {
        ArrayList<R> out = new ArrayList<>();
        Iterator<T> it = source.iterator();
        while (it.hasNext()) {
            Sequence<? extends R> inner = transform.invoke(it.next());
            Iterator<? extends R> in = inner.iterator();
            while (in.hasNext()) {
                out.add(in.next());
            }
        }
        return new ListSequence<>(out);
    }

    // ---- toSet(): the Kotlin compiler emits
    //   toSet(Lkotlin/sequences/Sequence;)Ljava/util/Set;
    // Kotlin's contract: a NEW LinkedHashSet preserving first-occurrence order, dedup via equals.
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> Set<T> toSet(Sequence<T> source) {
        LinkedHashSet<T> out = new LinkedHashSet<>();
        for (Iterator<T> it = source.iterator(); it.hasNext(); ) {
            out.add(it.next());
        }
        return out;
    }

    // --- not-needed members (loud stubs; reaching one demotes to a member-named UNKNOWN) ---
    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void all(kotlin.sequences.Sequence a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.all(kotlin.sequences.Sequence,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void any(kotlin.sequences.Sequence a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.any(kotlin.sequences.Sequence,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void associate(kotlin.sequences.Sequence a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.associate(kotlin.sequences.Sequence,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void associateBy(kotlin.sequences.Sequence a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.associateBy(kotlin.sequences.Sequence,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void associateBy(kotlin.sequences.Sequence a0, kotlin.jvm.functions.Function1 a1, kotlin.jvm.functions.Function1 a2) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.associateBy(kotlin.sequences.Sequence,kotlin.jvm.functions.Function1,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void associateByTo(kotlin.sequences.Sequence a0, java.util.Map a1, kotlin.jvm.functions.Function1 a2) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.associateByTo(kotlin.sequences.Sequence,java.util.Map,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void associateByTo(kotlin.sequences.Sequence a0, java.util.Map a1, kotlin.jvm.functions.Function1 a2, kotlin.jvm.functions.Function1 a3) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.associateByTo(kotlin.sequences.Sequence,java.util.Map,kotlin.jvm.functions.Function1,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void associateTo(kotlin.sequences.Sequence a0, java.util.Map a1, kotlin.jvm.functions.Function1 a2) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.associateTo(kotlin.sequences.Sequence,java.util.Map,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void associateWith(kotlin.sequences.Sequence a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.associateWith(kotlin.sequences.Sequence,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void associateWithTo(kotlin.sequences.Sequence a0, java.util.Map a1, kotlin.jvm.functions.Function1 a2) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.associateWithTo(kotlin.sequences.Sequence,java.util.Map,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void count(kotlin.sequences.Sequence a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.count(kotlin.sequences.Sequence,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void filterIndexedTo(kotlin.sequences.Sequence a0, java.util.Collection a1, kotlin.jvm.functions.Function2 a2) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.filterIndexedTo(kotlin.sequences.Sequence,java.util.Collection,kotlin.jvm.functions.Function2) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void filterNotTo(kotlin.sequences.Sequence a0, java.util.Collection a1, kotlin.jvm.functions.Function1 a2) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.filterNotTo(kotlin.sequences.Sequence,java.util.Collection,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void filterTo(kotlin.sequences.Sequence a0, java.util.Collection a1, kotlin.jvm.functions.Function1 a2) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.filterTo(kotlin.sequences.Sequence,java.util.Collection,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void first(kotlin.sequences.Sequence a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.first(kotlin.sequences.Sequence,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void firstOrNull(kotlin.sequences.Sequence a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.firstOrNull(kotlin.sequences.Sequence,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void flatMapIterableTo(kotlin.sequences.Sequence a0, java.util.Collection a1, kotlin.jvm.functions.Function1 a2) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.flatMapIterableTo(kotlin.sequences.Sequence,java.util.Collection,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void flatMapTo(kotlin.sequences.Sequence a0, java.util.Collection a1, kotlin.jvm.functions.Function1 a2) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.flatMapTo(kotlin.sequences.Sequence,java.util.Collection,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void fold(kotlin.sequences.Sequence a0, java.lang.Object a1, kotlin.jvm.functions.Function2 a2) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.fold(kotlin.sequences.Sequence,java.lang.Object,kotlin.jvm.functions.Function2) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void foldIndexed(kotlin.sequences.Sequence a0, java.lang.Object a1, kotlin.jvm.functions.Function3 a2) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.foldIndexed(kotlin.sequences.Sequence,java.lang.Object,kotlin.jvm.functions.Function3) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void forEach(kotlin.sequences.Sequence a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.forEach(kotlin.sequences.Sequence,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void forEachIndexed(kotlin.sequences.Sequence a0, kotlin.jvm.functions.Function2 a1) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.forEachIndexed(kotlin.sequences.Sequence,kotlin.jvm.functions.Function2) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void groupBy(kotlin.sequences.Sequence a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.groupBy(kotlin.sequences.Sequence,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void groupBy(kotlin.sequences.Sequence a0, kotlin.jvm.functions.Function1 a1, kotlin.jvm.functions.Function1 a2) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.groupBy(kotlin.sequences.Sequence,kotlin.jvm.functions.Function1,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void groupByTo(kotlin.sequences.Sequence a0, java.util.Map a1, kotlin.jvm.functions.Function1 a2) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.groupByTo(kotlin.sequences.Sequence,java.util.Map,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void groupByTo(kotlin.sequences.Sequence a0, java.util.Map a1, kotlin.jvm.functions.Function1 a2, kotlin.jvm.functions.Function1 a3) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.groupByTo(kotlin.sequences.Sequence,java.util.Map,kotlin.jvm.functions.Function1,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void groupingBy(kotlin.sequences.Sequence a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.groupingBy(kotlin.sequences.Sequence,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void indexOfFirst(kotlin.sequences.Sequence a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.indexOfFirst(kotlin.sequences.Sequence,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void indexOfLast(kotlin.sequences.Sequence a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.indexOfLast(kotlin.sequences.Sequence,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void last(kotlin.sequences.Sequence a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.last(kotlin.sequences.Sequence,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void lastOrNull(kotlin.sequences.Sequence a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.lastOrNull(kotlin.sequences.Sequence,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void mapIndexedNotNullTo(kotlin.sequences.Sequence a0, java.util.Collection a1, kotlin.jvm.functions.Function2 a2) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.mapIndexedNotNullTo(kotlin.sequences.Sequence,java.util.Collection,kotlin.jvm.functions.Function2) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void mapIndexedTo(kotlin.sequences.Sequence a0, java.util.Collection a1, kotlin.jvm.functions.Function2 a2) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.mapIndexedTo(kotlin.sequences.Sequence,java.util.Collection,kotlin.jvm.functions.Function2) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void mapNotNullTo(kotlin.sequences.Sequence a0, java.util.Collection a1, kotlin.jvm.functions.Function1 a2) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.mapNotNullTo(kotlin.sequences.Sequence,java.util.Collection,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void mapTo(kotlin.sequences.Sequence a0, java.util.Collection a1, kotlin.jvm.functions.Function1 a2) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.mapTo(kotlin.sequences.Sequence,java.util.Collection,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void maxByOrNull(kotlin.sequences.Sequence a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.maxByOrNull(kotlin.sequences.Sequence,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void maxByOrThrow(kotlin.sequences.Sequence a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.maxByOrThrow(kotlin.sequences.Sequence,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void minByOrNull(kotlin.sequences.Sequence a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.minByOrNull(kotlin.sequences.Sequence,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void minByOrThrow(kotlin.sequences.Sequence a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.minByOrThrow(kotlin.sequences.Sequence,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void none(kotlin.sequences.Sequence a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.none(kotlin.sequences.Sequence,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void partition(kotlin.sequences.Sequence a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.partition(kotlin.sequences.Sequence,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void reduce(kotlin.sequences.Sequence a0, kotlin.jvm.functions.Function2 a1) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.reduce(kotlin.sequences.Sequence,kotlin.jvm.functions.Function2) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void reduceIndexed(kotlin.sequences.Sequence a0, kotlin.jvm.functions.Function3 a1) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.reduceIndexed(kotlin.sequences.Sequence,kotlin.jvm.functions.Function3) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void reduceIndexedOrNull(kotlin.sequences.Sequence a0, kotlin.jvm.functions.Function3 a1) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.reduceIndexedOrNull(kotlin.sequences.Sequence,kotlin.jvm.functions.Function3) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void reduceOrNull(kotlin.sequences.Sequence a0, kotlin.jvm.functions.Function2 a1) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.reduceOrNull(kotlin.sequences.Sequence,kotlin.jvm.functions.Function2) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void single(kotlin.sequences.Sequence a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.single(kotlin.sequences.Sequence,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void singleOrNull(kotlin.sequences.Sequence a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.singleOrNull(kotlin.sequences.Sequence,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void sortedBy(kotlin.sequences.Sequence a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.sortedBy(kotlin.sequences.Sequence,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void sortedByDescending(kotlin.sequences.Sequence a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.sortedByDescending(kotlin.sequences.Sequence,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void sumBy(kotlin.sequences.Sequence a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.sumBy(kotlin.sequences.Sequence,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcNotNeeded(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void sumByDouble(kotlin.sequences.Sequence a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.sumByDouble(kotlin.sequences.Sequence,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

}
