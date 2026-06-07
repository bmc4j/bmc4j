package kotlin.sequences;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcModelTail;

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
}
