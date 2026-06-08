package kotlin.sequences;

import static org.bmc4j.analysis.BmcUnmodelledReached.fail;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcModelTail;
import org.bmc4j.models.audit.BmcNotNeeded;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import kotlin.Pair;
import kotlin.collections.IndexedValue;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.functions.Function1;
import kotlin.jvm.functions.Function2;
import kotlin.jvm.functions.Function3;
import org.cprover.CProver;

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

    // ---- mapIndexed / filterIndexed / withIndex: index-aware variants. The Kotlin compiler emits a
    // facade call (these are NOT inline funs) carrying a Function2 (index, element). bmc4j desugars the
    // user lambda into the Function2, so the index-aware mapper/predicate is genuinely applied — not
    // stubbed. We evaluate eagerly over the bounded source, indexing from 0 in iteration order.

    @SuppressWarnings("unchecked")
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T, R> Sequence<R> mapIndexed(Sequence<T> source, Function2<? super Integer, ? super T, ? extends R> transform) {
        ArrayList<R> out = new ArrayList<>();
        int index = 0;
        Iterator<T> it = source.iterator();
        while (it.hasNext()) {
            out.add((R) transform.invoke(index, it.next()));
            index++;
        }
        return new ListSequence<>(out);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> Sequence<T> filterIndexed(Sequence<T> source, Function2<? super Integer, ? super T, Boolean> predicate) {
        ArrayList<T> out = new ArrayList<>();
        int index = 0;
        Iterator<T> it = source.iterator();
        while (it.hasNext()) {
            T v = it.next();
            if (predicate.invoke(index, v)) {
                out.add(v);
            }
            index++;
        }
        return new ListSequence<>(out);
    }

    // withIndex(): each element wrapped in an IndexedValue(index, element), index from 0 in order.
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> Sequence<IndexedValue<T>> withIndex(Sequence<T> source) {
        ArrayList<IndexedValue<T>> out = new ArrayList<>();
        int index = 0;
        Iterator<T> it = source.iterator();
        while (it.hasNext()) {
            out.add(new IndexedValue<>(index, it.next()));
            index++;
        }
        return new ListSequence<>(out);
    }

    // ---- onEach / onEachIndexed: perform the action on each element as it passes through, yielding the
    // SAME elements unchanged (Kotlin contract). Eager, so the side-effecting order is the iteration
    // order over the bounded source. The action is the desugared user lambda, genuinely invoked.

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> Sequence<T> onEach(Sequence<T> source, Function1<? super T, ? extends Object> action) {
        ArrayList<T> out = new ArrayList<>();
        Iterator<T> it = source.iterator();
        while (it.hasNext()) {
            T v = it.next();
            action.invoke(v);
            out.add(v);
        }
        return new ListSequence<>(out);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> Sequence<T> onEachIndexed(Sequence<T> source, Function2<? super Integer, ? super T, ? extends Object> action) {
        ArrayList<T> out = new ArrayList<>();
        int index = 0;
        Iterator<T> it = source.iterator();
        while (it.hasNext()) {
            T v = it.next();
            action.invoke(index, v);
            out.add(v);
            index++;
        }
        return new ListSequence<>(out);
    }

    // ---- firstOrNull / lastOrNull (no-arg) / elementAtOrNull: terminal element accessors that return
    // null instead of throwing when out of range. (The predicate overloads are INLINE funs — desugared
    // directly over iterator() — so only the no-arg/index forms are facade methods needing a model.)

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> T firstOrNull(Sequence<T> source) {
        Iterator<T> it = source.iterator();
        if (!it.hasNext()) {
            return null;
        }
        return it.next();
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> T lastOrNull(Sequence<T> source) {
        Iterator<T> it = source.iterator();
        if (!it.hasNext()) {
            return null;
        }
        T last = it.next();
        while (it.hasNext()) {
            last = it.next();
        }
        return last;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> T elementAtOrNull(Sequence<T> source, int index) {
        if (index < 0) {
            return null;
        }
        int count = 0;
        Iterator<T> it = source.iterator();
        while (it.hasNext()) {
            T element = it.next();
            if (index == count) {
                return element;
            }
            count++;
        }
        return null;
    }

    // ---- chunked(size) / chunked(size, transform): split into lists each of at most `size` (the last
    // may be shorter). Kotlin's contract: size must be positive else IllegalArgumentException. Modeled
    // as windowed(size, size, partialWindows=true) per the stdlib definition.

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> Sequence<List<T>> chunked(Sequence<T> source, int size) {
        return windowed(source, size, size, true);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T, R> Sequence<R> chunked(Sequence<T> source, int size, Function1<? super List<T>, ? extends R> transform) {
        return windowed(source, size, size, true, transform);
    }

    // ---- windowed(size, step, partialWindows[, transform]): a sliding window of `size` advancing by
    // `step`. Kotlin's contract: both size and step must be positive (else IllegalArgumentException). If
    // partialWindows is false, only full windows of exactly `size` are kept; if true, trailing partial
    // windows are kept too. Eager over the bounded source into fresh bounded lists.

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> Sequence<List<T>> windowed(Sequence<T> source, int size, int step, boolean partialWindows) {
        if (size <= 0 || step <= 0) {
            throw new IllegalArgumentException(
                    "Both size " + size + " and step " + step + " must be greater than zero.");
        }
        ArrayList<T> all = new ArrayList<>();
        Iterator<T> it = source.iterator();
        while (it.hasNext()) {
            all.add(it.next());
        }
        int n = all.size();
        ArrayList<List<T>> windows = new ArrayList<>();
        for (int start = 0; start < n; start += step) {
            int end = start + size;
            if (end > n) {
                if (!partialWindows) {
                    break;
                }
                end = n;
            }
            ArrayList<T> window = new ArrayList<>();
            for (int i = start; i < end; i++) {
                window.add(all.get(i));
            }
            windows.add(window);
        }
        return new ListSequence<>(windows);
    }

    @SuppressWarnings("unchecked")
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T, R> Sequence<R> windowed(
            Sequence<T> source, int size, int step, boolean partialWindows,
            Function1<? super List<T>, ? extends R> transform) {
        Sequence<List<T>> windows = windowed(source, size, step, partialWindows);
        ArrayList<R> out = new ArrayList<>();
        Iterator<List<T>> it = windows.iterator();
        while (it.hasNext()) {
            out.add((R) transform.invoke(it.next()));
        }
        return new ListSequence<>(out);
    }

    // ---- zipWithNext() / zipWithNext(transform): pairs (or transformed pairs) of each two ADJACENT
    // elements. Empty if the sequence has fewer than two elements. Eager over the bounded source.

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> Sequence<Pair<T, T>> zipWithNext(Sequence<T> source) {
        ArrayList<Pair<T, T>> out = new ArrayList<>();
        Iterator<T> it = source.iterator();
        if (it.hasNext()) {
            T current = it.next();
            while (it.hasNext()) {
                T next = it.next();
                out.add(new Pair<>(current, next));
                current = next;
            }
        }
        return new ListSequence<>(out);
    }

    @SuppressWarnings("unchecked")
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T, R> Sequence<R> zipWithNext(Sequence<T> source, Function2<? super T, ? super T, ? extends R> transform) {
        ArrayList<R> out = new ArrayList<>();
        Iterator<T> it = source.iterator();
        if (it.hasNext()) {
            T current = it.next();
            while (it.hasNext()) {
                T next = it.next();
                out.add((R) transform.invoke(current, next));
                current = next;
            }
        }
        return new ListSequence<>(out);
    }

    // ---- zip(other) / zip(other, transform): pair up elements at the same index from two sequences;
    // the result ends as soon as the shorter input ends. Eager over both bounded sources.

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T, R> Sequence<Pair<T, R>> zip(Sequence<T> source, Sequence<R> other) {
        ArrayList<Pair<T, R>> out = new ArrayList<>();
        Iterator<T> a = source.iterator();
        Iterator<R> b = other.iterator();
        while (a.hasNext() && b.hasNext()) {
            out.add(new Pair<>(a.next(), b.next()));
        }
        return new ListSequence<>(out);
    }

    @SuppressWarnings("unchecked")
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T, R, V> Sequence<V> zip(Sequence<T> source, Sequence<R> other, Function2<? super T, ? super R, ? extends V> transform) {
        ArrayList<V> out = new ArrayList<>();
        Iterator<T> a = source.iterator();
        Iterator<R> b = other.iterator();
        while (a.hasNext() && b.hasNext()) {
            out.add((V) transform.invoke(a.next(), b.next()));
        }
        return new ListSequence<>(out);
    }

    // ---- plus(...) / minus(...): concatenation and element removal. plus appends; minus(element)
    // removes the FIRST occurrence (equals); minus(elements) removes ALL contained (equals). Eager over
    // the bounded source(s), returning a fresh bounded sequence and leaving the source untouched.

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> Sequence<T> plus(Sequence<T> source, T element) {
        ArrayList<T> out = new ArrayList<>();
        for (Iterator<T> it = source.iterator(); it.hasNext(); ) {
            out.add(it.next());
        }
        out.add(element);
        return new ListSequence<>(out);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> Sequence<T> plus(Sequence<T> source, T[] elements) {
        ArrayList<T> out = new ArrayList<>();
        for (Iterator<T> it = source.iterator(); it.hasNext(); ) {
            out.add(it.next());
        }
        for (T e : elements) {
            out.add(e);
        }
        return new ListSequence<>(out);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> Sequence<T> plus(Sequence<T> source, Iterable<T> elements) {
        ArrayList<T> out = new ArrayList<>();
        for (Iterator<T> it = source.iterator(); it.hasNext(); ) {
            out.add(it.next());
        }
        for (Iterator<T> it = elements.iterator(); it.hasNext(); ) {
            out.add(it.next());
        }
        return new ListSequence<>(out);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> Sequence<T> plus(Sequence<T> source, Sequence<T> elements) {
        ArrayList<T> out = new ArrayList<>();
        for (Iterator<T> it = source.iterator(); it.hasNext(); ) {
            out.add(it.next());
        }
        for (Iterator<T> it = elements.iterator(); it.hasNext(); ) {
            out.add(it.next());
        }
        return new ListSequence<>(out);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> Sequence<T> minus(Sequence<T> source, T element) {
        ArrayList<T> out = new ArrayList<>();
        boolean removed = false;
        for (Iterator<T> it = source.iterator(); it.hasNext(); ) {
            T v = it.next();
            if (!removed && objEquals(v, element)) {
                removed = true;
            } else {
                out.add(v);
            }
        }
        return new ListSequence<>(out);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> Sequence<T> minus(Sequence<T> source, T[] elements) {
        ArrayList<T> removeFrom = new ArrayList<>();
        for (T e : elements) {
            removeFrom.add(e);
        }
        return minusAll(source, removeFrom);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> Sequence<T> minus(Sequence<T> source, Iterable<T> elements) {
        ArrayList<T> removeFrom = new ArrayList<>();
        for (Iterator<T> it = elements.iterator(); it.hasNext(); ) {
            removeFrom.add(it.next());
        }
        return minusAll(source, removeFrom);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> Sequence<T> minus(Sequence<T> source, Sequence<T> elements) {
        ArrayList<T> removeFrom = new ArrayList<>();
        for (Iterator<T> it = elements.iterator(); it.hasNext(); ) {
            removeFrom.add(it.next());
        }
        return minusAll(source, removeFrom);
    }

    private static <T> Sequence<T> minusAll(Sequence<T> source, ArrayList<T> removeFrom) {
        ArrayList<T> out = new ArrayList<>();
        for (Iterator<T> it = source.iterator(); it.hasNext(); ) {
            T v = it.next();
            if (!contains(removeFrom, v)) {
                out.add(v);
            }
        }
        return new ListSequence<>(out);
    }

    private static <T> boolean contains(ArrayList<T> list, T value) {
        for (int i = 0; i < list.size(); i++) {
            if (objEquals(list.get(i), value)) {
                return true;
            }
        }
        return false;
    }

    private static boolean objEquals(Object a, Object b) {
        if (a == null) {
            return b == null;
        }
        return a.equals(b);
    }

    // ---- sorted() / sortedDescending() / sortedWith(comparator): a NEW sequence yielding the source's
    // elements sorted. Reuses the same proven insertion-sort-over-a-bounded-ArrayList pattern as
    // CollectionsKt.sorted/sortedWith (stable; bounded by size, within the proof's unwind). sorted uses
    // natural ordering, sortedDescending its reverse, sortedWith the supplied Comparator. Source
    // untouched, matching the Kotlin contract.

    @SuppressWarnings({"rawtypes", "unchecked"})
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T extends Comparable<? super T>> Sequence<T> sorted(Sequence<T> source) {
        ArrayList<T> out = drain(source);
        insertionSort(out, null);
        return new ListSequence<>(out);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T extends Comparable<? super T>> Sequence<T> sortedDescending(Sequence<T> source) {
        // Sort ascending by natural order, then reverse — avoids depending on an unmodeled
        // Collections.reverseOrder() comparator (which JBMC would nondet-stub into a wrong sort).
        ArrayList<T> out = drain(source);
        insertionSort(out, null);
        ArrayList<T> rev = new ArrayList<>();
        for (int i = out.size() - 1; i >= 0; i--) {
            rev.add(out.get(i));
        }
        return new ListSequence<>(rev);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> Sequence<T> sortedWith(Sequence<T> source, Comparator<? super T> comparator) {
        ArrayList<T> out = drain(source);
        insertionSort(out, comparator);
        return new ListSequence<>(out);
    }

    private static <T> ArrayList<T> drain(Sequence<T> source) {
        ArrayList<T> out = new ArrayList<>();
        for (Iterator<T> it = source.iterator(); it.hasNext(); ) {
            out.add(it.next());
        }
        return out;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static <T> void insertionSort(ArrayList<T> a, Comparator<? super T> cmp) {
        int n = a.size();
        for (int i = 1; i < n; i++) {
            T key = a.get(i);
            int j = i - 1;
            // stable: shift down only strictly-greater elements (> 0, not >= 0)
            while (j >= 0 && compare(a.get(j), key, cmp) > 0) {
                a.set(j + 1, a.get(j));
                j--;
            }
            a.set(j + 1, key);
        }
    }

    /**
     * The concrete backing list of a model {@link Sequence}. Every {@code SequencesKt} op (and
     * {@code sequenceOf}) constructs a {@link ListSequence}, the sole, {@code final} implementor, so
     * the cast is sound. Reading the backing {@link ArrayList} directly — then iterating it by index —
     * avoids dispatching the virtual {@code Sequence.iterator()}, which JBMC must devirtualize on the
     * interface-typed parameter. That devirtualization is fragile across kotlinc versions: on the
     * kotlin-2.0.21 leg it intermittently failed to bind ("no body for callee
     * {@code kotlin.sequences.Sequence.iterator()}"), leaving the iterator nondet/null and producing a
     * false REFUTED for a symbolic-input proof that verifies on 2.4.0. A {@code checkcast} to the
     * final concrete type is resolved by JBMC where the interface method dispatch is not.
     */
    @SuppressWarnings("unchecked")
    private static <T> ArrayList<T> backing(Sequence<? extends T> source) {
        return (ArrayList<T>) ((ListSequence<? extends T>) source).data;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static <T> int compare(T x, T y, Comparator<? super T> cmp) {
        if (cmp != null) {
            return cmp.compare(x, y);
        }
        return ((Comparable) x).compareTo(y);
    }

    // ---- generateSequence(seed, nextFunction) / generateSequence(seedFunction, nextFunction): build a
    // sequence by repeatedly applying nextFunction, stopping when it (or the seed) yields null. The
    // pure-supplier infinite form generateSequence(nextFunction) is laziness-observable (only sensible
    // with a downstream take(n)) and stays in the loud tail. These seeded forms are sound for
    // TERMINATING generators: the eager unwind is bounded with CProver.assume, which prunes the
    // (infinite/over-long) paths JBMC cannot finish rather than silently truncating to a wrong shorter
    // sequence. A generator that terminates within GENERATE_BOUND is analysed exactly.

    private static final int GENERATE_BOUND = 16;

    @SuppressWarnings("unchecked")
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> Sequence<T> generateSequence(T seed, Function1<? super T, ? extends T> nextFunction) {
        ArrayList<T> out = new ArrayList<>();
        T current = seed;
        int guard = 0;
        while (current != null) {
            out.add(current);
            guard++;
            CProver.assume(guard <= GENERATE_BOUND);
            current = (T) nextFunction.invoke(current);
        }
        return new ListSequence<>(out);
    }

    @SuppressWarnings("unchecked")
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> Sequence<T> generateSequence(
            Function0<? extends T> seedFunction, Function1<? super T, ? extends T> nextFunction) {
        return generateSequence((T) seedFunction.invoke(), nextFunction);
    }

    // ---- distinctBy(selector): distinct elements keyed by selector(element), keeping the FIRST element
    // per distinct key, in first-occurrence order. Real chain is a lazy DistinctSequence backed by a
    // HashSet of keys; we evaluate eagerly with a bounded LinkedHashSet of seen keys. The selector is the
    // desugared user lambda, genuinely applied. (distinct() — no selector — is already modeled above.)
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T, K> Sequence<T> distinctBy(Sequence<T> source, Function1<? super T, ? extends K> selector) {
        ArrayList<T> in = backing(source);
        LinkedHashSet<K> seenKeys = new LinkedHashSet<>();
        ArrayList<T> out = new ArrayList<>();
        for (int i = 0; i < in.size(); i++) {
            T v = in.get(i);
            K key = selector.invoke(v);
            if (seenKeys.add(key)) {
                out.add(v);
            }
        }
        return new ListSequence<>(out);
    }

    // ---- filterNot(predicate): the complement of filter — keep elements for which the predicate is
    // FALSE. Real chain is a lazy FilteringSequence(sendWhen=false); we evaluate eagerly. The predicate is
    // the desugared user lambda, genuinely applied.
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> Sequence<T> filterNot(Sequence<T> source, Function1<? super T, Boolean> predicate) {
        ArrayList<T> out = new ArrayList<>();
        for (Iterator<T> it = source.iterator(); it.hasNext(); ) {
            T v = it.next();
            if (!predicate.invoke(v)) {
                out.add(v);
            }
        }
        return new ListSequence<>(out);
    }

    // ---- filterNotNull(): drop null elements, keeping order. stdlib defines it as filterNot { it==null };
    // we model it directly (and soundly) as a null-dropping pass over the bounded source.
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> Sequence<T> filterNotNull(Sequence<T> source) {
        ArrayList<T> out = new ArrayList<>();
        for (Iterator<T> it = source.iterator(); it.hasNext(); ) {
            T v = it.next();
            if (v != null) {
                out.add(v);
            }
        }
        return new ListSequence<>(out);
    }

    // ---- mapNotNull(transform): map each element then drop null results, keeping order. stdlib defines
    // it as TransformingSequence(transform).filterNotNull(); we fuse the two passes eagerly. The transform
    // is the desugared user lambda, genuinely applied.
    @SuppressWarnings("unchecked")
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T, R> Sequence<R> mapNotNull(Sequence<T> source, Function1<? super T, ? extends R> transform) {
        ArrayList<R> out = new ArrayList<>();
        for (Iterator<T> it = source.iterator(); it.hasNext(); ) {
            R r = (R) transform.invoke(it.next());
            if (r != null) {
                out.add(r);
            }
        }
        return new ListSequence<>(out);
    }

    // ---- mapIndexedNotNull(transform): index-aware mapNotNull. stdlib defines it as
    // TransformingIndexedSequence(transform).filterNotNull(); we fuse the two passes eagerly, indexing from
    // 0 in iteration order. The transform is the desugared user lambda (Function2), genuinely applied.
    @SuppressWarnings("unchecked")
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T, R> Sequence<R> mapIndexedNotNull(
            Sequence<T> source, Function2<? super Integer, ? super T, ? extends R> transform) {
        ArrayList<R> out = new ArrayList<>();
        int index = 0;
        for (Iterator<T> it = source.iterator(); it.hasNext(); ) {
            R r = (R) transform.invoke(index, it.next());
            if (r != null) {
                out.add(r);
            }
            index++;
        }
        return new ListSequence<>(out);
    }

    // ---- flatMapIndexedIterable / flatMapIndexedSequence: index-aware flatMap. The Kotlin compiler picks
    // the JVM name by the transform's return type — @JvmName("flatMapIndexedIterable") for a transform
    // yielding Iterable<R>, @JvmName("flatMapIndexedSequence") for one yielding Sequence<R> (the bare
    // `flatMapIndexed(Sequence,Function2,Function1)` is the shared private iterator-extractor helper, never
    // a Kotlin call site → stays loud in the tail). We concatenate each element's expansion in order,
    // indexing from 0. The transform is the desugared user lambda (Function2), genuinely applied.

    @SuppressWarnings("unchecked")
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T, R> Sequence<R> flatMapIndexedIterable(
            Sequence<T> source, Function2<? super Integer, ? super T, ? extends Iterable<? extends R>> transform) {
        ArrayList<R> out = new ArrayList<>();
        int index = 0;
        for (Iterator<T> it = source.iterator(); it.hasNext(); ) {
            Iterable<? extends R> inner = transform.invoke(index, it.next());
            for (Iterator<? extends R> in = inner.iterator(); in.hasNext(); ) {
                out.add(in.next());
            }
            index++;
        }
        return new ListSequence<>(out);
    }

    @SuppressWarnings("unchecked")
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T, R> Sequence<R> flatMapIndexedSequence(
            Sequence<T> source, Function2<? super Integer, ? super T, ? extends Sequence<? extends R>> transform) {
        ArrayList<R> out = new ArrayList<>();
        int index = 0;
        for (Iterator<T> it = source.iterator(); it.hasNext(); ) {
            Sequence<? extends R> inner = transform.invoke(index, it.next());
            for (Iterator<? extends R> in = inner.iterator(); in.hasNext(); ) {
                out.add(in.next());
            }
            index++;
        }
        return new ListSequence<>(out);
    }

    // ---- runningFold / scan (+ Indexed forms): successive accumulation values. The result begins with the
    // INITIAL value, then each step's accumulator after applying operation left-to-right — so a source of
    // length n yields n+1 elements. scan/scanIndexed are aliases for runningFold/runningFoldIndexed in
    // stdlib; we mirror that. These are finite, eager-sound intermediate ops (NOT laziness-observable: the
    // n+1 prefix is fully determined by the bounded source). The operation is the desugared user lambda.

    @SuppressWarnings("unchecked")
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T, R> Sequence<R> runningFold(
            Sequence<T> source, R initial, Function2<? super R, ? super T, ? extends R> operation) {
        ArrayList<R> out = new ArrayList<>();
        R acc = initial;
        out.add(acc);
        for (Iterator<T> it = source.iterator(); it.hasNext(); ) {
            acc = (R) operation.invoke(acc, it.next());
            out.add(acc);
        }
        return new ListSequence<>(out);
    }

    @SuppressWarnings("unchecked")
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T, R> Sequence<R> runningFoldIndexed(
            Sequence<T> source, R initial, Function3<? super Integer, ? super R, ? super T, ? extends R> operation) {
        ArrayList<R> out = new ArrayList<>();
        R acc = initial;
        out.add(acc);
        int index = 0;
        for (Iterator<T> it = source.iterator(); it.hasNext(); ) {
            acc = (R) operation.invoke(index, acc, it.next());
            out.add(acc);
            index++;
        }
        return new ListSequence<>(out);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T, R> Sequence<R> scan(
            Sequence<T> source, R initial, Function2<? super R, ? super T, ? extends R> operation) {
        return runningFold(source, initial, operation);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T, R> Sequence<R> scanIndexed(
            Sequence<T> source, R initial, Function3<? super Integer, ? super R, ? super T, ? extends R> operation) {
        return runningFoldIndexed(source, initial, operation);
    }

    // ---- requireNoNulls(): identity over the elements, but throws IllegalArgumentException on the FIRST
    // null. stdlib defines it as map { it ?: throw IAE }. Eager over the bounded source; a fully non-null
    // source passes through unchanged.
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> Sequence<T> requireNoNulls(Sequence<T> source) {
        ArrayList<T> out = new ArrayList<>();
        for (Iterator<T> it = source.iterator(); it.hasNext(); ) {
            T v = it.next();
            if (v == null) {
                throw new IllegalArgumentException("null element found in " + source + ".");
            }
            out.add(v);
        }
        return new ListSequence<>(out);
    }

    // ---- asIterable(): expose the sequence's elements as an Iterable. The real impl wraps the SAME lazy
    // sequence's iterator(); we drain into a fresh bounded ArrayList (an Iterable) — observationally
    // identical for a TERMINATING sequence under the bounded models. (asSequence() the inverse is InlineOnly
    // and stays not-needed.)
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> Iterable<T> asIterable(Sequence<T> source) {
        ArrayList<T> out = new ArrayList<>();
        for (Iterator<T> it = source.iterator(); it.hasNext(); ) {
            out.add(it.next());
        }
        return out;
    }

    // ---- ifEmpty(defaultValue): yield the source's elements if non-empty, else yield defaultValue()'s.
    // Kotlin's contract: defaultValue is invoked ONLY when the source is empty. We honour that by draining
    // the source first and calling defaultValue() exclusively on the empty branch — eager-sound for
    // terminating sequences. (constrainOnce stays loud: its once-only-iteration semantics are
    // laziness/iteration-count observable and cannot be modeled eagerly.)
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> Sequence<T> ifEmpty(Sequence<T> source, Function0<? extends Sequence<? extends T>> defaultValue) {
        ArrayList<T> out = new ArrayList<>();
        Iterator<T> it = source.iterator();
        if (it.hasNext()) {
            while (it.hasNext()) {
                out.add(it.next());
            }
        } else {
            for (Iterator<? extends T> d = defaultValue.invoke().iterator(); d.hasNext(); ) {
                out.add(d.next());
            }
        }
        return new ListSequence<>(out);
    }
}
