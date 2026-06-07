package kotlin.enums;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcModelTail;

/**
 * Clean model of Kotlin's {@code kotlin.enums.EnumEntriesKt} facade. An {@code enum class}'s
 * {@code <clinit>} initialises its synthetic {@code $ENTRIES} field via
 * {@code EnumEntriesKt.enumEntries($VALUES)} (the {@code Enum[]} overload), and the {@code entries}
 * property accessor returns it. The real facade hands back a stdlib-internal {@code EnumEntriesList}
 * whose members JBMC stubs to nondet, so {@code Enum.entries} is unusable in proofs today.
 *
 * <p>This replacement returns bmc4j's bounded {@link EnumEntriesList} (an {@code ArrayList} model
 * populated from {@code values()}) directly, so {@code entries.size}/{@code entries[i]}/iteration
 * analyse over the bounded list model. Because this class REPLACES the stdlib facade on the analysis
 * path, every overload an enum's bytecode or consumer code can reach is modeled; the two unused
 * overloads fail loudly rather than nondet-stub.
 */
@BmcModelTail(reason = "EnumEntriesKt has no remainder beyond the two modeled enumEntries overloads; "
        + "tail present for ratchet completeness, loud under JBMC if any future member is reached")
public final class EnumEntriesKt {

    private EnumEntriesKt() {
    }

    /**
     * The overload an {@code enum class}'s {@code <clinit>} actually calls:
     * {@code EnumEntriesKt.enumEntries($VALUES)} with the synthetic {@code $VALUES} array.
     */
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <E extends Enum<E>> EnumEntries<E> enumEntries(E[] entries) {
        return new EnumEntriesList<>(entries);
    }

    /**
     * Pre-1.9 / lambda-provider overload {@code enumEntries(() -> values())}. Modeled by invoking the
     * provider and wrapping the array, so it stays sound if a consumer reaches it.
     */
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <E extends Enum<E>> EnumEntries<E> enumEntries(kotlin.jvm.functions.Function0<E[]> entriesProvider) {
        return new EnumEntriesList<>(entriesProvider.invoke());
    }

    /**
     * The reified {@code enumEntries<T>()} intrinsic. The Kotlin compiler inlines its callers to the
     * {@code Enum[]} overload above, so this body is unreachable on the analysis path; it fails loudly
     * (never a silent nondet stub) if some path somehow reaches it.
     */
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <E extends Enum<E>> EnumEntries<E> enumEntries() {
        throw new UnsupportedOperationException(
                "EnumEntriesKt.enumEntries() (reified intrinsic) is inlined by kotlinc; model not reachable");
    }
}
