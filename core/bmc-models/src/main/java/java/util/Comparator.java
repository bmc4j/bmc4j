package java.util;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcModelTail;

/**
 * BMC model of {@link java.util.Comparator}, present ONLY to give JBMC a sound, devirtualizable
 * {@link #naturalOrder()} body on the proof analysis classpath. The SAM {@code compare} is abstract
 * (supplied by a desugared lambda / method reference, which JBMC drives directly).
 *
 * <p><b>Not relocated.</b> Like {@code java.util.function}, this interface is excluded from the
 * differential relocation (see {@code bmc-models-conformance}'s {@code isModel}): a {@code Comparator}
 * is SHARED between user/proof code (which passes a real-JDK comparator — often a desugared lambda — to
 * a model's {@code sort}/{@code sorted}) and the model surface, and the natural-order witness's own
 * {@link BmcNaturalOrder#COMPARATOR} is a {@code Comparator}. Relocating the interface to {@code
 * bmcref.*} would split those into incompatible twins. So this model is validated by model proofs
 * (proofs.sort), never the differential axis, and needs no relocated twin.
 *
 * <p><b>Only {@code naturalOrder()} is modeled.</b> The real JDK's {@code naturalOrder()} returns a
 * comparator that calls the elements' VIRTUAL {@code Comparable.compareTo} — a multi-implementor
 * dispatch JBMC cannot devirtualize soundly. The modeled factory instead returns
 * {@link BmcNaturalOrder#COMPARATOR}, a single concrete comparator backed by the bit-precise
 * {@link BmcNaturalOrder#compare(Object, Object)} (builtin Comparables only; loud for anything else).
 * Every other {@code Comparator} static/default ({@code reverseOrder}, {@code comparing*},
 * {@code thenComparing*}, {@code nullsFirst}/{@code nullsLast}, {@code reversed}) is absorbed by the
 * tail: it composes through dynamic dispatch the model declines to fictionalize, so reaching it fails
 * loudly and named (via the build-time loud-body synthesis) rather than proceeding on a fiction.
 */
@BmcModelTail(reason = "java.util.Comparator's composition surface (reverseOrder/comparing*/thenComparing*/nullsFirst/nullsLast/reversed) dispatches through user comparators/key-extractors JBMC can't soundly devirtualize; only the SAM compare and the devirtualizable naturalOrder() are modeled, the rest is loud-if-reached")
@FunctionalInterface
public interface Comparator<T> {

    @BmcModelConforms("@BmcProof (proofs.sort NaturalOrderSortLaws) — the desugared-SAM comparator JBMC drives by index")
    int compare(T o1, T o2);

    /**
     * Natural-order comparator backed by the single concrete, devirtualizable
     * {@link BmcNaturalOrder#compare(Object, Object)} — NOT the JDK's virtual {@code Comparable.compareTo}
     * dispatch. Covers the builtin Comparables bit-precisely; loud for any other element type.
     */
    @BmcModelConforms("@BmcProof (proofs.sort NaturalOrderSortLaws)")
    @SuppressWarnings({"unchecked", "rawtypes"})
    static <T extends Comparable<? super T>> Comparator<T> naturalOrder() {
        return (Comparator) BmcNaturalOrder.COMPARATOR;
    }
}
