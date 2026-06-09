package java.util;

import static org.bmc4j.analysis.BmcUnmodelledReached.fail;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcUnmodelable;

/**
 * BMC model of {@link java.util.EnumSet} — a set of enum constants, modeled over the same
 * fixed-capacity dedup array as the {@link HashSet} model (which it extends to inherit the audited
 * add/remove/contains/containsAll/addAll/removeAll/retainAll/removeIf/forEach/size/isEmpty/clear/
 * iterator surface). Sound and bounded — membership/iteration unwind to the current size.
 *
 * <h2>What is modeled (the EXPLICIT-element surface — needs no enum universe)</h2>
 * The factories that take the elements directly — {@code of(e)}, {@code of(e1,e2)} … {@code of(e1..e5)},
 * {@code of(E, E...)}, and {@code copyOf(Collection)} / {@code copyOf(EnumSet)} — are modeled: they
 * just {@code add} the given elements into the bounded array (dedup via the inherited HashSet add).
 * Iteration ({@code iterator}/{@code forEach}, inherited) is in element-insertion order; the real
 * EnumSet iterates in {@code ordinal()} order. Insertion order is used here (a deviation noted on the
 * relevant proofs); for typical {@code of(...)} usage the elements are inserted in ordinal order anyway.
 *
 * <h2>What stays LOUD (the Class-UNIVERSE factories — need reflective enum enumeration)</h2>
 * {@code allOf(Class)}, {@code noneOf(Class)}, {@code range(e1,e2)}, and {@code complementOf(set)} all
 * need the enum's full constant set. {@code allOf}/{@code noneOf} obtain it via
 * {@code Class.getEnumConstants()} — a reflective read on a symbolic {@code Class} object that JBMC
 * cannot soundly enumerate (the constant universe of an arbitrary, only-known-at-analysis-time enum
 * type is not statically available). {@code range}/{@code complementOf} likewise need every constant
 * between two bounds / the complement against the full universe. These are kept as loud
 * {@code @BmcUnmodelable} stubs: reaching one is an honest member-named UNKNOWN, never a silent nondet.
 *
 * <p>(Determination, as the task asked: JBMC does NOT soundly enumerate the constant universe of a
 * symbolic enum {@code Class} at analysis time, so the universe factories cannot be modeled. The
 * per-instance {@code ordinal()} read IS sound — that is what the EnumMap model uses — but that does
 * not recover the full constant set from a {@code Class} token.)
 *
 * <p>Every real EnumSet member is classified per-member: the explicit-element factories carry
 * {@code @BmcModelConforms}, the Class-universe factories below are loud {@code @BmcUnmodelable}
 * stubs, and the remaining surface ({@code clone()}, {@code toArray(T[])},
 * {@code toArray(IntFunction)}, {@code spliterator()}, plus the modeled
 * {@code stream()}/{@code parallelStream()}/{@code forEach}/etc.) resolves up the modeled
 * {@link HashSet} chain to HashSet's per-member decisions. There is no class-level catch-all.
 */
public abstract class EnumSet<E extends Enum<E>> extends HashSet<E> {

    EnumSet() {
        super();
    }

    /**
     * A concrete element-backed EnumSet the explicit-element factories instantiate (the real EnumSet
     * is abstract with Regular/Jumbo subclasses; this single bounded array stands in for both).
     */
    private static final class ArrayEnumSet<E extends Enum<E>> extends EnumSet<E> {
        ArrayEnumSet() {
            super();
        }
    }

    private static <E extends Enum<E>> EnumSet<E> fresh() {
        return new ArrayEnumSet<>();
    }

    // --- explicit-element factories (modeled — no enum universe needed) -------------------------

    /** Singleton set {@code {e}}. */
    @BmcModelConforms("differential (JavaUtilTailConformanceTest) + @BmcProof (proofs.enumset)")
    public static <E extends Enum<E>> EnumSet<E> of(E e) {
        EnumSet<E> s = fresh();
        s.add(e);
        return s;
    }

    /** Set of the two given elements (dedup if equal). */
    @BmcModelConforms("differential (JavaUtilTailConformanceTest) + @BmcProof (proofs.enumset)")
    public static <E extends Enum<E>> EnumSet<E> of(E e1, E e2) {
        EnumSet<E> s = fresh();
        s.add(e1);
        s.add(e2);
        return s;
    }

    /** Set of the three given elements (dedup). */
    @BmcModelConforms("differential (JavaUtilTailConformanceTest) + @BmcProof (proofs.enumset)")
    public static <E extends Enum<E>> EnumSet<E> of(E e1, E e2, E e3) {
        EnumSet<E> s = fresh();
        s.add(e1);
        s.add(e2);
        s.add(e3);
        return s;
    }

    /** Set of the four given elements (dedup). */
    @BmcModelConforms("differential (JavaUtilTailConformanceTest) + @BmcProof (proofs.enumset)")
    public static <E extends Enum<E>> EnumSet<E> of(E e1, E e2, E e3, E e4) {
        EnumSet<E> s = fresh();
        s.add(e1);
        s.add(e2);
        s.add(e3);
        s.add(e4);
        return s;
    }

    /** Set of the five given elements (dedup). */
    @BmcModelConforms("differential (JavaUtilTailConformanceTest) + @BmcProof (proofs.enumset)")
    public static <E extends Enum<E>> EnumSet<E> of(E e1, E e2, E e3, E e4, E e5) {
        EnumSet<E> s = fresh();
        s.add(e1);
        s.add(e2);
        s.add(e3);
        s.add(e4);
        s.add(e5);
        return s;
    }

    /** Set of {@code first} plus the varargs {@code rest} (dedup). */
    @SafeVarargs
    @BmcModelConforms("differential (JavaUtilTailConformanceTest) + @BmcProof (proofs.enumset)")
    public static <E extends Enum<E>> EnumSet<E> of(E first, E... rest) {
        EnumSet<E> s = fresh();
        s.add(first);
        for (E e : rest) {
            s.add(e);
        }
        return s;
    }

    /** A new EnumSet holding {@code c}'s distinct elements (dedup via the inherited add). */
    @BmcModelConforms("differential (JavaUtilTailConformanceTest) + @BmcProof (proofs.enumset)")
    public static <E extends Enum<E>> EnumSet<E> copyOf(Collection<E> c) {
        EnumSet<E> s = fresh();
        for (E e : c) {
            s.add(e);
        }
        return s;
    }

    /** A new EnumSet that is a copy of the given EnumSet (same elements). */
    @BmcModelConforms("differential (JavaUtilTailConformanceTest) + @BmcProof (proofs.enumset)")
    public static <E extends Enum<E>> EnumSet<E> copyOf(EnumSet<E> s) {
        EnumSet<E> out = fresh();
        for (E e : s) {
            out.add(e);
        }
        return out;
    }

    // --- Class-universe factories (LOUD — need the enum constant universe via reflection) -------

    @BmcUnmodelable(reason = "needs the enum constant universe via reflective Class.getEnumConstants() — JBMC cannot soundly enumerate the constants of a symbolic enum Class")
    public static <E extends Enum<E>> EnumSet<E> noneOf(Class<E> elementType) {
        throw fail("bmc4j: unmodelled member java.util.EnumSet.noneOf(java.lang.Class) — needs the enum constant universe via reflective Class.getEnumConstants()");
    }

    @BmcUnmodelable(reason = "needs the enum constant universe via reflective Class.getEnumConstants() — JBMC cannot soundly enumerate the constants of a symbolic enum Class")
    public static <E extends Enum<E>> EnumSet<E> allOf(Class<E> elementType) {
        throw fail("bmc4j: unmodelled member java.util.EnumSet.allOf(java.lang.Class) — needs the enum constant universe via reflective Class.getEnumConstants()");
    }

    @BmcUnmodelable(reason = "needs every constant between the two bounds — requires the enum constant universe via reflective Class.getEnumConstants()")
    public static <E extends Enum<E>> EnumSet<E> range(E from, E to) {
        throw fail("bmc4j: unmodelled member java.util.EnumSet.range(java.lang.Enum,java.lang.Enum) — needs the enum constant universe via reflective Class.getEnumConstants()");
    }

    @BmcUnmodelable(reason = "the complement against the enum's full universe — requires the enum constant universe via reflective Class.getEnumConstants()")
    public static <E extends Enum<E>> EnumSet<E> complementOf(EnumSet<E> s) {
        throw fail("bmc4j: unmodelled member java.util.EnumSet.complementOf(java.util.EnumSet) — needs the enum constant universe via reflective Class.getEnumConstants()");
    }
}
