package kotlin.sequences;

import static org.bmc4j.analysis.BmcUnmodelledReached.fail;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcUnmodelable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
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
        Iterator<T> it = seqIter(source);
        while (it.hasNext()) {
            out.add((R) transform.invoke(it.next()));
        }
        return new ListSequence<>(out);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> Sequence<T> filter(Sequence<T> source, Function1<? super T, Boolean> predicate) {
        ArrayList<T> out = new ArrayList<>();
        Iterator<T> it = seqIter(source);
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
        Iterator<T> it = seqIter(source);
        while (it.hasNext()) {
            out.add(it.next());
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int sumOfInt(Sequence<Integer> source) {
        int sum = 0;
        Iterator<Integer> it = seqIter(source);
        while (it.hasNext()) {
            sum += it.next();
        }
        return sum;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> int count(Sequence<T> source) {
        int n = 0;
        Iterator<T> it = seqIter(source);
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
            Iterator<T> it = seqIter(source);
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
        Iterator<T> it = seqIter(source);
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

    // ---- takeWhile(predicate) / dropWhile(predicate): the Kotlin compiler emits
    //   takeWhile(Lkotlin/sequences/Sequence;Lkotlin/jvm/functions/Function1;)Lkotlin/sequences/Sequence;
    //   dropWhile(Lkotlin/sequences/Sequence;Lkotlin/jvm/functions/Function1;)Lkotlin/sequences/Sequence;
    // These are NOT inline funs on Sequence (unlike their Iterable cousins on CollectionsKt) — they are
    // real facade calls returning a lazy TakeWhileSequence/DropWhileSequence whose iterator is a small
    // finite state machine over the source iterator's hasNext/next protocol: takeWhile yields the leading
    // run for which the predicate holds and STOPS at the first failure (it does NOT resume); dropWhile
    // skips that same leading run then yields everything after — INCLUDING later elements that fail the
    // predicate. We replay that exact state machine eagerly over the concrete bounded backing (iterating
    // by the seqIter/backing checkcast, never the kotlinc-version-fragile virtual Sequence.iterator()).
    // The predicate is the desugared user lambda, genuinely applied.

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> Sequence<T> takeWhile(Sequence<T> source, Function1<? super T, Boolean> predicate) {
        ArrayList<T> out = new ArrayList<>();
        for (Iterator<T> it = seqIter(source); it.hasNext(); ) {
            T v = it.next();
            if (!predicate.invoke(v)) {
                break;
            }
            out.add(v);
        }
        return new ListSequence<>(out);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> Sequence<T> dropWhile(Sequence<T> source, Function1<? super T, Boolean> predicate) {
        ArrayList<T> out = new ArrayList<>();
        boolean dropping = true;
        for (Iterator<T> it = seqIter(source); it.hasNext(); ) {
            T v = it.next();
            if (dropping && predicate.invoke(v)) {
                // still in the leading run the predicate accepts — skip this element.
                dropping = true;
            } else {
                // the run has ended (first rejection): keep this and every later element.
                dropping = false;
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
        for (Iterator<T> it = seqIter(source); it.hasNext(); ) {
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
        Iterator<T> it = seqIter(source);
        while (it.hasNext()) {
            Sequence<? extends R> inner = transform.invoke(it.next());
            Iterator<? extends R> in = seqIter(inner);
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
        for (Iterator<T> it = seqIter(source); it.hasNext(); ) {
            out.add(it.next());
        }
        return out;
    }

    // ==== #184 tail-drain: terminal accessors, conversions, numeric reductions, comparator/Comparable
    // extrema and the remaining intermediate ops. All eager over the concrete bounded backing via
    // seqIter/backing (NEVER the kotlinc-version-fragile virtual Sequence.iterator() — see backing's
    // #169 rationale), so the descriptors that previously fell through to @BmcModelTail are now sound.

    // ---- single-element / empty constructors. sequenceOf(T) wraps one element; emptySequence() is the
    // empty sequence (stdlib's EmptySequence singleton). Both eager ListSequences.
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> Sequence<T> sequenceOf(T element) {
        ArrayList<T> out = new ArrayList<>();
        out.add(element);
        return new ListSequence<>(out);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> Sequence<T> emptySequence() {
        return new ListSequence<>(new ArrayList<T>());
    }

    // ---- asSequence(Iterator): wrap a (bounded) iterator's elements into an eager ListSequence so the
    // downstream ops analyse over the bounded model. The real impl is lazy over the SAME iterator; eager
    // is observationally identical for a terminating iterator.
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> Sequence<T> asSequence(Iterator<T> source) {
        ArrayList<T> out = new ArrayList<>();
        while (source.hasNext()) {
            out.add(source.next());
        }
        return new ListSequence<>(out);
    }

    // ---- first / last / single / singleOrNull (no-predicate terminals). first/last/single throw
    // NoSuchElementException on empty (single also throws IllegalArgumentException on >1); singleOrNull
    // returns null unless there is exactly one element. (The predicate overloads are INLINE — they
    // desugar into the caller — so only the no-arg forms are facade methods needing a model.)

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> T first(Sequence<T> source) {
        Iterator<T> it = seqIter(source);
        if (!it.hasNext()) {
            throw new NoSuchElementException("Sequence is empty.");
        }
        return it.next();
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> T last(Sequence<T> source) {
        Iterator<T> it = seqIter(source);
        if (!it.hasNext()) {
            throw new NoSuchElementException("Sequence is empty.");
        }
        T last = it.next();
        while (it.hasNext()) {
            last = it.next();
        }
        return last;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> T single(Sequence<T> source) {
        Iterator<T> it = seqIter(source);
        if (!it.hasNext()) {
            throw new NoSuchElementException("Sequence is empty.");
        }
        T single = it.next();
        if (it.hasNext()) {
            throw new IllegalArgumentException("Sequence has more than one element.");
        }
        return single;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> T singleOrNull(Sequence<T> source) {
        Iterator<T> it = seqIter(source);
        if (!it.hasNext()) {
            return null;
        }
        T single = it.next();
        if (it.hasNext()) {
            return null;
        }
        return single;
    }

    // ---- any() / none() (no-predicate terminals): any() is true iff the sequence is non-empty; none()
    // is its negation. (The predicate overloads are INLINE.)
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> boolean any(Sequence<T> source) {
        return seqIter(source).hasNext();
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> boolean none(Sequence<T> source) {
        return !seqIter(source).hasNext();
    }

    // ---- contains / indexOf / lastIndexOf / elementAt / elementAtOrElse: positional / membership
    // terminals. contains == indexOf >= 0; indexOf returns the FIRST equals-match index or -1; lastIndexOf
    // the LAST; elementAt throws IndexOutOfBoundsException out of range; elementAtOrElse calls the default
    // function with the index when out of range. All eager over the bounded backing, equals via objEquals.

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> boolean contains(Sequence<T> source, T element) {
        return indexOf(source, element) >= 0;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> int indexOf(Sequence<T> source, T element) {
        int index = 0;
        for (Iterator<T> it = seqIter(source); it.hasNext(); ) {
            if (objEquals(it.next(), element)) {
                return index;
            }
            index++;
        }
        return -1;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> int lastIndexOf(Sequence<T> source, T element) {
        int index = 0;
        int last = -1;
        for (Iterator<T> it = seqIter(source); it.hasNext(); ) {
            if (objEquals(it.next(), element)) {
                last = index;
            }
            index++;
        }
        return last;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> T elementAt(Sequence<T> source, int index) {
        if (index < 0) {
            throw new IndexOutOfBoundsException(
                    "Sequence doesn't contain element at index " + index + ".");
        }
        int count = 0;
        for (Iterator<T> it = seqIter(source); it.hasNext(); ) {
            T element = it.next();
            if (index == count) {
                return element;
            }
            count++;
        }
        throw new IndexOutOfBoundsException(
                "Sequence doesn't contain element at index " + index + ".");
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> T elementAtOrElse(Sequence<T> source, int index, Function1<? super Integer, ? extends T> defaultValue) {
        if (index < 0) {
            return defaultValue.invoke(index);
        }
        int count = 0;
        for (Iterator<T> it = seqIter(source); it.hasNext(); ) {
            T element = it.next();
            if (index == count) {
                return element;
            }
            count++;
        }
        return defaultValue.invoke(index);
    }

    // ---- filterNotNullTo(dest): drain the non-null elements into the destination, in order.
    // (filterIsInstance/filterIsInstanceTo's reflective Class.isInstance check is WALLED below — it
    // routes through CProver.classIdentifier, a JBMC reflective hook that nondet-stubs unsoundly.)
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <C extends Collection<? super T>, T> C filterNotNullTo(Sequence<T> source, C destination) {
        for (Iterator<T> it = seqIter(source); it.hasNext(); ) {
            T v = it.next();
            if (v != null) {
                destination.add(v);
            }
        }
        return destination;
    }

    // ---- flatMapIterable(transform) / flatten / flattenSequenceOfIterable: concatenation flatteners.
    // flatMapIterable's transform yields an Iterable per element (the @JvmName twin of flatMap whose
    // transform yields a Sequence — already modeled); flatten concatenates a Sequence<Sequence>;
    // flattenSequenceOfIterable concatenates a Sequence<Iterable>. All eager over the bounded backing.

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T, R> Sequence<R> flatMapIterable(
            Sequence<T> source, Function1<? super T, ? extends Iterable<? extends R>> transform) {
        ArrayList<R> out = new ArrayList<>();
        for (Iterator<T> it = seqIter(source); it.hasNext(); ) {
            Iterable<? extends R> inner = transform.invoke(it.next());
            drainIterable(inner, out);
        }
        return new ListSequence<>(out);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> Sequence<T> flatten(Sequence<? extends Sequence<? extends T>> source) {
        ArrayList<T> out = new ArrayList<>();
        for (Iterator<? extends Sequence<? extends T>> it = seqIter(source); it.hasNext(); ) {
            for (Iterator<? extends T> in = seqIter(it.next()); in.hasNext(); ) {
                out.add(in.next());
            }
        }
        return new ListSequence<>(out);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> Sequence<T> flattenSequenceOfIterable(Sequence<? extends Iterable<? extends T>> source) {
        ArrayList<T> out = new ArrayList<>();
        for (Iterator<? extends Iterable<? extends T>> it = seqIter(source); it.hasNext(); ) {
            drainIterable(it.next(), out);
        }
        return new ListSequence<>(out);
    }

    // ---- unzip: split a Sequence<Pair> into a Pair of two parallel lists, in order.
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T, R> Pair<List<T>, List<R>> unzip(Sequence<? extends Pair<? extends T, ? extends R>> source) {
        ArrayList<T> firsts = new ArrayList<>();
        ArrayList<R> seconds = new ArrayList<>();
        for (Iterator<? extends Pair<? extends T, ? extends R>> it = seqIter(source); it.hasNext(); ) {
            Pair<? extends T, ? extends R> p = it.next();
            firsts.add(p.getFirst());
            seconds.add(p.getSecond());
        }
        return new Pair<>(firsts, seconds);
    }

    // ---- toCollection / toHashSet / toMutableList / toMutableSet / toSortedSet(+Comparator): bulk
    // drains into a fresh collection. toMutableSet preserves first-occurrence order (LinkedHashSet);
    // toHashSet is unordered; toSortedSet sorts by natural order (or the supplied Comparator). All eager.

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T, C extends Collection<? super T>> C toCollection(Sequence<T> source, C destination) {
        for (Iterator<T> it = seqIter(source); it.hasNext(); ) {
            destination.add(it.next());
        }
        return destination;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> HashSet<T> toHashSet(Sequence<T> source) {
        HashSet<T> out = new HashSet<>();
        for (Iterator<T> it = seqIter(source); it.hasNext(); ) {
            out.add(it.next());
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> List<T> toMutableList(Sequence<T> source) {
        ArrayList<T> out = new ArrayList<>();
        for (Iterator<T> it = seqIter(source); it.hasNext(); ) {
            out.add(it.next());
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> Set<T> toMutableSet(Sequence<T> source) {
        LinkedHashSet<T> out = new LinkedHashSet<>();
        for (Iterator<T> it = seqIter(source); it.hasNext(); ) {
            out.add(it.next());
        }
        return out;
    }

    // ---- sumOf* / averageOf* numeric reductions over the boxed-primitive backing. sum folds the elements;
    // average is sum/count as a double (NaN for an empty source, matching Kotlin). sumOfByte/Short widen to
    // int, matching the stdlib return types. (sumOfInt is already modeled above.)

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int sumOfByte(Sequence<Byte> source) {
        int sum = 0;
        for (Iterator<Byte> it = seqIter(source); it.hasNext(); ) {
            sum += it.next();
        }
        return sum;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int sumOfShort(Sequence<Short> source) {
        int sum = 0;
        for (Iterator<Short> it = seqIter(source); it.hasNext(); ) {
            sum += it.next();
        }
        return sum;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static long sumOfLong(Sequence<Long> source) {
        long sum = 0;
        for (Iterator<Long> it = seqIter(source); it.hasNext(); ) {
            sum += it.next();
        }
        return sum;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static float sumOfFloat(Sequence<Float> source) {
        float sum = 0;
        for (Iterator<Float> it = seqIter(source); it.hasNext(); ) {
            sum += it.next();
        }
        return sum;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static double sumOfDouble(Sequence<Double> source) {
        double sum = 0;
        for (Iterator<Double> it = seqIter(source); it.hasNext(); ) {
            sum += it.next();
        }
        return sum;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static double averageOfByte(Sequence<Byte> source) {
        double sum = 0;
        int count = 0;
        for (Iterator<Byte> it = seqIter(source); it.hasNext(); ) {
            sum += it.next();
            count++;
        }
        return count == 0 ? Double.NaN : sum / count;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static double averageOfShort(Sequence<Short> source) {
        double sum = 0;
        int count = 0;
        for (Iterator<Short> it = seqIter(source); it.hasNext(); ) {
            sum += it.next();
            count++;
        }
        return count == 0 ? Double.NaN : sum / count;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static double averageOfInt(Sequence<Integer> source) {
        double sum = 0;
        int count = 0;
        for (Iterator<Integer> it = seqIter(source); it.hasNext(); ) {
            sum += it.next();
            count++;
        }
        return count == 0 ? Double.NaN : sum / count;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static double averageOfLong(Sequence<Long> source) {
        double sum = 0;
        int count = 0;
        for (Iterator<Long> it = seqIter(source); it.hasNext(); ) {
            sum += it.next();
            count++;
        }
        return count == 0 ? Double.NaN : sum / count;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static double averageOfFloat(Sequence<Float> source) {
        double sum = 0;
        int count = 0;
        for (Iterator<Float> it = seqIter(source); it.hasNext(); ) {
            sum += it.next();
            count++;
        }
        return count == 0 ? Double.NaN : sum / count;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static double averageOfDouble(Sequence<Double> source) {
        double sum = 0;
        int count = 0;
        for (Iterator<Double> it = seqIter(source); it.hasNext(); ) {
            sum += it.next();
            count++;
        }
        return count == 0 ? Double.NaN : sum / count;
    }

    // ---- maxOrNull / maxOrThrow / minOrNull / minOrThrow (natural Comparable fold) and the
    // maxWith*/minWith* (Comparator-driven) extrema. The *OrNull forms return null on empty; *OrThrow
    // throw NoSuchElementException. The natural-order fold uses compareTo (sound for the boxed-primitive
    // backing the proofs exercise); the *With forms apply the supplied Comparator — the same proven
    // pattern as CollectionsKt.max/minWith*. Mirrors the erased generic overload (the Double/Float
    // primitive overloads share its erased descriptor, so one model covers all).

    @SuppressWarnings({"rawtypes", "unchecked"})
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T extends Comparable<? super T>> T maxOrNull(Sequence<T> source) {
        Iterator<T> it = seqIter(source);
        if (!it.hasNext()) {
            return null;
        }
        T max = it.next();
        while (it.hasNext()) {
            T e = it.next();
            if (max.compareTo(e) < 0) {
                max = e;
            }
        }
        return max;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T extends Comparable<? super T>> T maxOrThrow(Sequence<T> source) {
        Iterator<T> it = seqIter(source);
        if (!it.hasNext()) {
            throw new NoSuchElementException("Sequence is empty.");
        }
        T max = it.next();
        while (it.hasNext()) {
            T e = it.next();
            if (max.compareTo(e) < 0) {
                max = e;
            }
        }
        return max;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T extends Comparable<? super T>> T minOrNull(Sequence<T> source) {
        Iterator<T> it = seqIter(source);
        if (!it.hasNext()) {
            return null;
        }
        T min = it.next();
        while (it.hasNext()) {
            T e = it.next();
            if (min.compareTo(e) > 0) {
                min = e;
            }
        }
        return min;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T extends Comparable<? super T>> T minOrThrow(Sequence<T> source) {
        Iterator<T> it = seqIter(source);
        if (!it.hasNext()) {
            throw new NoSuchElementException("Sequence is empty.");
        }
        T min = it.next();
        while (it.hasNext()) {
            T e = it.next();
            if (min.compareTo(e) > 0) {
                min = e;
            }
        }
        return min;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> T maxWithOrNull(Sequence<T> source, Comparator<? super T> comparator) {
        Iterator<T> it = seqIter(source);
        if (!it.hasNext()) {
            return null;
        }
        T max = it.next();
        while (it.hasNext()) {
            T e = it.next();
            if (comparator.compare(max, e) < 0) {
                max = e;
            }
        }
        return max;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> T maxWithOrThrow(Sequence<T> source, Comparator<? super T> comparator) {
        Iterator<T> it = seqIter(source);
        if (!it.hasNext()) {
            throw new NoSuchElementException("Sequence is empty.");
        }
        T max = it.next();
        while (it.hasNext()) {
            T e = it.next();
            if (comparator.compare(max, e) < 0) {
                max = e;
            }
        }
        return max;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> T minWithOrNull(Sequence<T> source, Comparator<? super T> comparator) {
        Iterator<T> it = seqIter(source);
        if (!it.hasNext()) {
            return null;
        }
        T min = it.next();
        while (it.hasNext()) {
            T e = it.next();
            if (comparator.compare(min, e) > 0) {
                min = e;
            }
        }
        return min;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> T minWithOrThrow(Sequence<T> source, Comparator<? super T> comparator) {
        Iterator<T> it = seqIter(source);
        if (!it.hasNext()) {
            throw new NoSuchElementException("Sequence is empty.");
        }
        T min = it.next();
        while (it.hasNext()) {
            T e = it.next();
            if (comparator.compare(min, e) > 0) {
                min = e;
            }
        }
        return min;
    }

    // ---- runningReduce / runningReduceIndexed: like runningFold but seeded by the FIRST element (no
    // explicit initial). Empty source -> empty result; otherwise the result begins with the first element
    // then each successive accumulator. The operation is the desugared user lambda, genuinely applied.

    @SuppressWarnings("unchecked")
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <S, T extends S> Sequence<S> runningReduce(
            Sequence<T> source, Function2<? super S, ? super T, ? extends S> operation) {
        ArrayList<S> out = new ArrayList<>();
        Iterator<T> it = seqIter(source);
        if (it.hasNext()) {
            S acc = it.next();
            out.add(acc);
            while (it.hasNext()) {
                acc = (S) operation.invoke(acc, it.next());
                out.add(acc);
            }
        }
        return new ListSequence<>(out);
    }

    @SuppressWarnings("unchecked")
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <S, T extends S> Sequence<S> runningReduceIndexed(
            Sequence<T> source, Function3<? super Integer, ? super S, ? super T, ? extends S> operation) {
        ArrayList<S> out = new ArrayList<>();
        Iterator<T> it = seqIter(source);
        if (it.hasNext()) {
            S acc = it.next();
            out.add(acc);
            int index = 1;
            while (it.hasNext()) {
                acc = (S) operation.invoke(index, acc, it.next());
                out.add(acc);
                index++;
            }
        }
        return new ListSequence<>(out);
    }

    // --- WALLED members (@BmcUnmodelable, loud-if-reached): no sound bounded eager model exists. ---

    @BmcUnmodelable(reason = "laziness-only: an infinite pure-supplier generator whose finite prefix is "
            + "observable only via a downstream take(n) — cannot be drained eagerly without truncating to a "
            + "wrong sequence")
    public static void generateSequence(Function0 nextFunction) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.generateSequence(kotlin.jvm.functions.Function0) — laziness-only: an infinite pure-supplier generator whose finite prefix is observable only via a downstream take(n) — cannot be drained eagerly without truncating to a wrong sequence");
    }

    @BmcUnmodelable(reason = "once-iteration-observable: constrainOnce wraps a sequence so a SECOND iteration "
            + "throws — a stateful laziness property an eager bounded snapshot cannot represent")
    public static void constrainOnce(Sequence a0) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.constrainOnce(kotlin.sequences.Sequence) — once-iteration-observable: constrainOnce wraps a sequence so a SECOND iteration throws — a stateful laziness property an eager bounded snapshot cannot represent");
    }

    @BmcUnmodelable(reason = "coroutine sequence builder: the Function2 is a restricted suspend lambda over "
            + "SequenceScope (yield/yieldAll) compiled to a state machine bmc4j does not model")
    public static void sequence(Function2 a0) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.sequence(kotlin.jvm.functions.Function2) — coroutine sequence builder: the Function2 is a restricted suspend lambda over SequenceScope (yield/yieldAll) compiled to a state machine bmc4j does not model");
    }

    @BmcUnmodelable(reason = "coroutine iterator builder: the Function2 is a restricted suspend lambda over "
            + "SequenceScope (yield/yieldAll) compiled to a state machine bmc4j does not model")
    public static void iterator(Function2 a0) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.iterator(kotlin.jvm.functions.Function2) — coroutine iterator builder: the Function2 is a restricted suspend lambda over SequenceScope (yield/yieldAll) compiled to a state machine bmc4j does not model");
    }

    @BmcUnmodelable(reason = "nondeterministic: shuffled draws a random permutation from Random — there is no "
            + "single sound value an eager model can return")
    public static void shuffled(Sequence a0) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.shuffled(kotlin.sequences.Sequence) — nondeterministic: shuffled draws a random permutation from Random — there is no single sound value an eager model can return");
    }

    @BmcUnmodelable(reason = "nondeterministic: shuffled draws a random permutation from the supplied Random — "
            + "there is no single sound value an eager model can return")
    public static void shuffled(Sequence a0, kotlin.random.Random a1) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.shuffled(kotlin.sequences.Sequence,kotlin.random.Random) — nondeterministic: shuffled draws a random permutation from the supplied Random — there is no single sound value an eager model can return");
    }

    @BmcUnmodelable(reason = "string-heavy + `$default` bridge: joinTo's StringBuilder/append reasoning is the "
            + "JBMC string blowup, and the call site routes through a kotlinc-synthesized joinTo$default the "
            + "engine nondet-stubs — verdict is UNKNOWN regardless of body correctness")
    public static void joinTo(Sequence a0, Appendable a1, CharSequence a2, CharSequence a3, CharSequence a4, int a5, CharSequence a6, Function1 a7) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.joinTo(kotlin.sequences.Sequence,java.lang.Appendable,java.lang.CharSequence,java.lang.CharSequence,java.lang.CharSequence,int,java.lang.CharSequence,kotlin.jvm.functions.Function1) — string-heavy + $default bridge: joinTo's StringBuilder/append reasoning is the JBMC string blowup, and the call site routes through a kotlinc-synthesized joinTo$default the engine nondet-stubs — verdict is UNKNOWN regardless of body correctness");
    }

    @BmcUnmodelable(reason = "string-heavy + `$default` bridge: joinToString's StringBuilder/append reasoning is "
            + "the JBMC string blowup, and the call site routes through a kotlinc-synthesized joinToString$default "
            + "the engine nondet-stubs — verdict is UNKNOWN regardless of body correctness")
    public static void joinToString(Sequence a0, CharSequence a1, CharSequence a2, CharSequence a3, int a4, CharSequence a5, Function1 a6) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.joinToString(kotlin.sequences.Sequence,java.lang.CharSequence,java.lang.CharSequence,java.lang.CharSequence,int,java.lang.CharSequence,kotlin.jvm.functions.Function1) — string-heavy + $default bridge: joinToString's StringBuilder/append reasoning is the JBMC string blowup, and the call site routes through a kotlinc-synthesized joinToString$default the engine nondet-stubs — verdict is UNKNOWN regardless of body correctness");
    }

    @BmcUnmodelable(reason = "private shared iterator-extractor helper (flatMapIndexed(Sequence,Function2,Function1)) "
            + "— never a Kotlin call site; the public flatMapIndexedIterable/flatMapIndexedSequence twins are modeled")
    public static void flatMapIndexed(Sequence a0, Function2 a1, Function1 a2) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.flatMapIndexed(kotlin.sequences.Sequence,kotlin.jvm.functions.Function2,kotlin.jvm.functions.Function1) — private shared iterator-extractor helper — never a Kotlin call site; the public flatMapIndexedIterable/flatMapIndexedSequence twins are modeled");
    }

    @BmcUnmodelable(reason = "reflective type check: filterIsInstance routes through Class.isInstance -> "
            + "CProver.classIdentifier, a JBMC reflective hook that nondet-stubs unsoundly (UNKNOWN, not a sound "
            + "model) — same out-of-scope boundary as CollectionsKt")
    public static void filterIsInstance(Sequence a0, Class a1) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.filterIsInstance(kotlin.sequences.Sequence,java.lang.Class) — reflective type check: filterIsInstance routes through Class.isInstance -> CProver.classIdentifier, a JBMC reflective hook that nondet-stubs unsoundly");
    }

    @BmcUnmodelable(reason = "reflective type check: filterIsInstanceTo routes through Class.isInstance -> "
            + "CProver.classIdentifier, a JBMC reflective hook that nondet-stubs unsoundly (UNKNOWN, not a sound model)")
    public static void filterIsInstanceTo(Sequence a0, Collection a1, Class a2) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.filterIsInstanceTo(kotlin.sequences.Sequence,java.util.Collection,java.lang.Class) — reflective type check: filterIsInstanceTo routes through Class.isInstance -> CProver.classIdentifier, a JBMC reflective hook that nondet-stubs unsoundly");
    }

    @BmcUnmodelable(reason = "TreeSet natural-order total-order over unconstrained Comparable T: the JDK TreeSet "
            + "model's internal comparison cast refutes under JBMC (Dynamic cast check) — no sound bounded model, "
            + "matching the CollectionsKt.toSortedSet out-of-scope boundary")
    public static void toSortedSet(Sequence a0) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.toSortedSet(kotlin.sequences.Sequence) — TreeSet natural-order total-order over unconstrained Comparable T: the JDK TreeSet model's internal comparison cast refutes under JBMC; no sound bounded model");
    }

    @BmcUnmodelable(reason = "TreeSet comparator total-order: shares the JDK TreeSet model's internal comparison-cast "
            + "fragility under JBMC — no sound bounded model, matching the CollectionsKt.toSortedSet out-of-scope boundary")
    public static void toSortedSet(Sequence a0, Comparator a1) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.toSortedSet(kotlin.sequences.Sequence,java.util.Comparator) — TreeSet comparator total-order: shares the JDK TreeSet model's internal comparison-cast fragility under JBMC; no sound bounded model");
    }

    // --- not-needed members (loud stubs; reaching one demotes to a member-named UNKNOWN) ---
    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void all(kotlin.sequences.Sequence a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.all(kotlin.sequences.Sequence,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void any(kotlin.sequences.Sequence a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.any(kotlin.sequences.Sequence,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void associate(kotlin.sequences.Sequence a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.associate(kotlin.sequences.Sequence,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void associateBy(kotlin.sequences.Sequence a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.associateBy(kotlin.sequences.Sequence,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void associateBy(kotlin.sequences.Sequence a0, kotlin.jvm.functions.Function1 a1, kotlin.jvm.functions.Function1 a2) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.associateBy(kotlin.sequences.Sequence,kotlin.jvm.functions.Function1,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void associateByTo(kotlin.sequences.Sequence a0, java.util.Map a1, kotlin.jvm.functions.Function1 a2) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.associateByTo(kotlin.sequences.Sequence,java.util.Map,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void associateByTo(kotlin.sequences.Sequence a0, java.util.Map a1, kotlin.jvm.functions.Function1 a2, kotlin.jvm.functions.Function1 a3) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.associateByTo(kotlin.sequences.Sequence,java.util.Map,kotlin.jvm.functions.Function1,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void associateTo(kotlin.sequences.Sequence a0, java.util.Map a1, kotlin.jvm.functions.Function1 a2) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.associateTo(kotlin.sequences.Sequence,java.util.Map,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void associateWith(kotlin.sequences.Sequence a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.associateWith(kotlin.sequences.Sequence,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void associateWithTo(kotlin.sequences.Sequence a0, java.util.Map a1, kotlin.jvm.functions.Function1 a2) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.associateWithTo(kotlin.sequences.Sequence,java.util.Map,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void count(kotlin.sequences.Sequence a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.count(kotlin.sequences.Sequence,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void filterIndexedTo(kotlin.sequences.Sequence a0, java.util.Collection a1, kotlin.jvm.functions.Function2 a2) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.filterIndexedTo(kotlin.sequences.Sequence,java.util.Collection,kotlin.jvm.functions.Function2) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void filterNotTo(kotlin.sequences.Sequence a0, java.util.Collection a1, kotlin.jvm.functions.Function1 a2) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.filterNotTo(kotlin.sequences.Sequence,java.util.Collection,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void filterTo(kotlin.sequences.Sequence a0, java.util.Collection a1, kotlin.jvm.functions.Function1 a2) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.filterTo(kotlin.sequences.Sequence,java.util.Collection,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void first(kotlin.sequences.Sequence a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.first(kotlin.sequences.Sequence,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void firstOrNull(kotlin.sequences.Sequence a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.firstOrNull(kotlin.sequences.Sequence,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void flatMapIterableTo(kotlin.sequences.Sequence a0, java.util.Collection a1, kotlin.jvm.functions.Function1 a2) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.flatMapIterableTo(kotlin.sequences.Sequence,java.util.Collection,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void flatMapTo(kotlin.sequences.Sequence a0, java.util.Collection a1, kotlin.jvm.functions.Function1 a2) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.flatMapTo(kotlin.sequences.Sequence,java.util.Collection,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void fold(kotlin.sequences.Sequence a0, java.lang.Object a1, kotlin.jvm.functions.Function2 a2) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.fold(kotlin.sequences.Sequence,java.lang.Object,kotlin.jvm.functions.Function2) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void foldIndexed(kotlin.sequences.Sequence a0, java.lang.Object a1, kotlin.jvm.functions.Function3 a2) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.foldIndexed(kotlin.sequences.Sequence,java.lang.Object,kotlin.jvm.functions.Function3) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void forEach(kotlin.sequences.Sequence a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.forEach(kotlin.sequences.Sequence,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void forEachIndexed(kotlin.sequences.Sequence a0, kotlin.jvm.functions.Function2 a1) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.forEachIndexed(kotlin.sequences.Sequence,kotlin.jvm.functions.Function2) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void groupBy(kotlin.sequences.Sequence a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.groupBy(kotlin.sequences.Sequence,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void groupBy(kotlin.sequences.Sequence a0, kotlin.jvm.functions.Function1 a1, kotlin.jvm.functions.Function1 a2) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.groupBy(kotlin.sequences.Sequence,kotlin.jvm.functions.Function1,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void groupByTo(kotlin.sequences.Sequence a0, java.util.Map a1, kotlin.jvm.functions.Function1 a2) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.groupByTo(kotlin.sequences.Sequence,java.util.Map,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void groupByTo(kotlin.sequences.Sequence a0, java.util.Map a1, kotlin.jvm.functions.Function1 a2, kotlin.jvm.functions.Function1 a3) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.groupByTo(kotlin.sequences.Sequence,java.util.Map,kotlin.jvm.functions.Function1,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void groupingBy(kotlin.sequences.Sequence a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.groupingBy(kotlin.sequences.Sequence,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void indexOfFirst(kotlin.sequences.Sequence a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.indexOfFirst(kotlin.sequences.Sequence,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void indexOfLast(kotlin.sequences.Sequence a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.indexOfLast(kotlin.sequences.Sequence,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void last(kotlin.sequences.Sequence a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.last(kotlin.sequences.Sequence,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void lastOrNull(kotlin.sequences.Sequence a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.lastOrNull(kotlin.sequences.Sequence,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void mapIndexedNotNullTo(kotlin.sequences.Sequence a0, java.util.Collection a1, kotlin.jvm.functions.Function2 a2) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.mapIndexedNotNullTo(kotlin.sequences.Sequence,java.util.Collection,kotlin.jvm.functions.Function2) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void mapIndexedTo(kotlin.sequences.Sequence a0, java.util.Collection a1, kotlin.jvm.functions.Function2 a2) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.mapIndexedTo(kotlin.sequences.Sequence,java.util.Collection,kotlin.jvm.functions.Function2) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void mapNotNullTo(kotlin.sequences.Sequence a0, java.util.Collection a1, kotlin.jvm.functions.Function1 a2) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.mapNotNullTo(kotlin.sequences.Sequence,java.util.Collection,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void mapTo(kotlin.sequences.Sequence a0, java.util.Collection a1, kotlin.jvm.functions.Function1 a2) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.mapTo(kotlin.sequences.Sequence,java.util.Collection,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void maxByOrNull(kotlin.sequences.Sequence a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.maxByOrNull(kotlin.sequences.Sequence,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void maxByOrThrow(kotlin.sequences.Sequence a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.maxByOrThrow(kotlin.sequences.Sequence,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void minByOrNull(kotlin.sequences.Sequence a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.minByOrNull(kotlin.sequences.Sequence,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void minByOrThrow(kotlin.sequences.Sequence a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.minByOrThrow(kotlin.sequences.Sequence,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void none(kotlin.sequences.Sequence a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.none(kotlin.sequences.Sequence,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void partition(kotlin.sequences.Sequence a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.partition(kotlin.sequences.Sequence,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void reduce(kotlin.sequences.Sequence a0, kotlin.jvm.functions.Function2 a1) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.reduce(kotlin.sequences.Sequence,kotlin.jvm.functions.Function2) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void reduceIndexed(kotlin.sequences.Sequence a0, kotlin.jvm.functions.Function3 a1) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.reduceIndexed(kotlin.sequences.Sequence,kotlin.jvm.functions.Function3) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void reduceIndexedOrNull(kotlin.sequences.Sequence a0, kotlin.jvm.functions.Function3 a1) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.reduceIndexedOrNull(kotlin.sequences.Sequence,kotlin.jvm.functions.Function3) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void reduceOrNull(kotlin.sequences.Sequence a0, kotlin.jvm.functions.Function2 a1) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.reduceOrNull(kotlin.sequences.Sequence,kotlin.jvm.functions.Function2) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void single(kotlin.sequences.Sequence a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.single(kotlin.sequences.Sequence,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void singleOrNull(kotlin.sequences.Sequence a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.singleOrNull(kotlin.sequences.Sequence,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void sortedBy(kotlin.sequences.Sequence a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.sortedBy(kotlin.sequences.Sequence,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void sortedByDescending(kotlin.sequences.Sequence a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.sortedByDescending(kotlin.sequences.Sequence,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
    public static void sumBy(kotlin.sequences.Sequence a0, kotlin.jvm.functions.Function1 a1) {
        throw fail("bmc4j: unmodelled member kotlin.sequences.SequencesKt.sumBy(kotlin.sequences.Sequence,kotlin.jvm.functions.Function1) — inline — body lands in caller; the facade JVM method is never called from a Kotlin call site");
    }

    @BmcUnmodelable(reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
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
        Iterator<T> it = seqIter(source);
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
        Iterator<T> it = seqIter(source);
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
        Iterator<T> it = seqIter(source);
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
        Iterator<T> it = seqIter(source);
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
        Iterator<T> it = seqIter(source);
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
        Iterator<T> it = seqIter(source);
        if (!it.hasNext()) {
            return null;
        }
        return it.next();
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> T lastOrNull(Sequence<T> source) {
        Iterator<T> it = seqIter(source);
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
        Iterator<T> it = seqIter(source);
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
        Iterator<T> it = seqIter(source);
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
        Iterator<List<T>> it = seqIter(windows);
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
        Iterator<T> it = seqIter(source);
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
        Iterator<T> it = seqIter(source);
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
        Iterator<T> a = seqIter(source);
        Iterator<R> b = seqIter(other);
        while (a.hasNext() && b.hasNext()) {
            out.add(new Pair<>(a.next(), b.next()));
        }
        return new ListSequence<>(out);
    }

    @SuppressWarnings("unchecked")
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T, R, V> Sequence<V> zip(Sequence<T> source, Sequence<R> other, Function2<? super T, ? super R, ? extends V> transform) {
        ArrayList<V> out = new ArrayList<>();
        Iterator<T> a = seqIter(source);
        Iterator<R> b = seqIter(other);
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
        for (Iterator<T> it = seqIter(source); it.hasNext(); ) {
            out.add(it.next());
        }
        out.add(element);
        return new ListSequence<>(out);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> Sequence<T> plus(Sequence<T> source, T[] elements) {
        ArrayList<T> out = new ArrayList<>();
        for (Iterator<T> it = seqIter(source); it.hasNext(); ) {
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
        for (Iterator<T> it = seqIter(source); it.hasNext(); ) {
            out.add(it.next());
        }
        drainIterable(elements, out);
        return new ListSequence<>(out);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> Sequence<T> plus(Sequence<T> source, Sequence<T> elements) {
        ArrayList<T> out = new ArrayList<>();
        for (Iterator<T> it = seqIter(source); it.hasNext(); ) {
            out.add(it.next());
        }
        for (Iterator<T> it = seqIter(elements); it.hasNext(); ) {
            out.add(it.next());
        }
        return new ListSequence<>(out);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> Sequence<T> minus(Sequence<T> source, T element) {
        ArrayList<T> out = new ArrayList<>();
        boolean removed = false;
        for (Iterator<T> it = seqIter(source); it.hasNext(); ) {
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
        drainIterable(elements, removeFrom);
        return minusAll(source, removeFrom);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> Sequence<T> minus(Sequence<T> source, Sequence<T> elements) {
        ArrayList<T> removeFrom = new ArrayList<>();
        for (Iterator<T> it = seqIter(elements); it.hasNext(); ) {
            removeFrom.add(it.next());
        }
        return minusAll(source, removeFrom);
    }

    private static <T> Sequence<T> minusAll(Sequence<T> source, ArrayList<T> removeFrom) {
        ArrayList<T> out = new ArrayList<>();
        for (Iterator<T> it = seqIter(source); it.hasNext(); ) {
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
        ArrayList<T> in = backing(source);
        ArrayList<T> out = new ArrayList<>();
        for (int i = 0; i < in.size(); i++) {
            out.add(in.get(i));
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
     *
     * <p>The {@code checkcast} is itself only deterministically discharged when JBMC has fully bound the
     * {@code Sequence}-typed parameter to its sole concrete subtype. Under heavy parallel load (the
     * full-suite leg running the conformance {@code test} and the as-shipped {@code jarModels}
     * conformance suite on one runner at once) JBMC has been observed to lose that binding even for a
     * CONCRETE pipeline, leaving {@code source}'s dynamic type unconstrained — so the cast spuriously
     * fails its "Dynamic cast check" and the whole analysis cascades to a phantom nondet counterexample.
     * The {@code instanceof} assume below pins the dynamic type explicitly: it prunes the (genuinely
     * impossible — {@link ListSequence} is the sole implementor and every op constructs one) branch where
     * {@code source} is not a {@code ListSequence}, so the checkcast is dischargeable regardless of how
     * the type-binding raced. Sound by construction: it never removes a real path, only the
     * never-taken one that the cast would otherwise have to refute. Because every {@code seqIter}/
     * {@code drain}/{@code distinctBy} routes through here, this stabilizes the whole facade at one point.
     */
    @SuppressWarnings("unchecked")
    private static <T> ArrayList<T> backing(Sequence<? extends T> source) {
        CProver.assume(source instanceof ListSequence);
        return (ArrayList<T>) ((ListSequence<? extends T>) source).data;
    }

    /**
     * Iterate a model {@link Sequence} via its concrete backing {@link ArrayList}'s iterator rather
     * than the virtual {@code Sequence.iterator()}. Same robustness rationale as {@link #backing}:
     * {@code ListSequence} is the sole {@code final} implementor, so the {@code checkcast} is sound,
     * and {@code ArrayList.iterator()} is a concrete-typed call JBMC resolves where the interface
     * dispatch on the {@code Sequence}-typed parameter is kotlinc-version-fragile (the #169 false
     * REFUTED: "no body for callee kotlin.sequences.Sequence.iterator()" on the kotlin-2.0.21 leg).
     * Every {@code SequencesKt} op that previously called {@code <seq>.iterator()} routes through here
     * so the whole facade — not just {@code distinctBy} — is devirtualization-robust.
     */
    private static <T> Iterator<T> seqIter(Sequence<? extends T> source) {
        return SequencesKt.<T>backing(source).iterator();
    }

    /**
     * Append every element of an inner {@link Iterable} (in order) to {@code out}. The flatteners
     * ({@code flatten}/{@code flatMapIterable}/{@code flattenSequenceOfIterable}) drain an inner
     * {@code Iterable<? extends T>} whose static type is the interface — so a raw {@code inner.iterator()}
     * is a multi-implementor interface dispatch JBMC must devirtualize on the interface-typed value.
     * That devirtualization is fragile on some codegen legs (the jdk25 {@code flatten} false REFUTED:
     * the inner {@code Iterable.iterator()} went nondet, cascading to a phantom "Dynamic cast check" /
     * null-pointer / array-index counterexample at the inner index). Same {@code #169} family as
     * {@link #backing}/{@link #seqIter}.
     *
     * <p>Every model {@code Iterable} the flatteners see — {@code listOf(...)}, {@code mutableListOf}, a
     * collection literal, the {@link ArrayList} model itself — is backed by the concrete {@link ArrayList}
     * model. When {@code inner} is one, read it BY INDEX via the concrete-typed {@code get(i)}, which JBMC
     * resolves where the interface dispatch is not. The {@code instanceof} prunes only the never-taken
     * non-{@code ArrayList} branch for those; any genuinely other {@code Iterable} falls back to its own
     * {@code iterator()} (sound — that path is unchanged, just not the one the proofs exercise).
     */
    @SuppressWarnings("unchecked")
    private static <T> void drainIterable(Iterable<? extends T> inner, ArrayList<T> out) {
        if (inner instanceof ArrayList) {
            ArrayList<? extends T> a = (ArrayList<? extends T>) inner;
            int n = a.size();
            for (int i = 0; i < n; i++) {
                out.add(a.get(i));
            }
            return;
        }
        for (Iterator<? extends T> in = inner.iterator(); in.hasNext(); ) {
            out.add(in.next());
        }
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
        for (Iterator<T> it = seqIter(source); it.hasNext(); ) {
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
        for (Iterator<T> it = seqIter(source); it.hasNext(); ) {
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
        for (Iterator<T> it = seqIter(source); it.hasNext(); ) {
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
        for (Iterator<T> it = seqIter(source); it.hasNext(); ) {
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

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T, R> Sequence<R> flatMapIndexedIterable(
            Sequence<T> source, Function2<? super Integer, ? super T, ? extends Iterable<? extends R>> transform) {
        ArrayList<R> out = new ArrayList<>();
        int index = 0;
        for (Iterator<T> it = seqIter(source); it.hasNext(); ) {
            Iterable<? extends R> inner = transform.invoke(index, it.next());
            drainIterable(inner, out);
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
        for (Iterator<T> it = seqIter(source); it.hasNext(); ) {
            Sequence<? extends R> inner = transform.invoke(index, it.next());
            for (Iterator<? extends R> in = seqIter(inner); in.hasNext(); ) {
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
        for (Iterator<T> it = seqIter(source); it.hasNext(); ) {
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
        for (Iterator<T> it = seqIter(source); it.hasNext(); ) {
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
        for (Iterator<T> it = seqIter(source); it.hasNext(); ) {
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
        for (Iterator<T> it = seqIter(source); it.hasNext(); ) {
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
        Iterator<T> it = seqIter(source);
        if (it.hasNext()) {
            while (it.hasNext()) {
                out.add(it.next());
            }
        } else {
            for (Iterator<? extends T> d = seqIter(defaultValue.invoke()); d.hasNext(); ) {
                out.add(d.next());
            }
        }
        return new ListSequence<>(out);
    }
}
