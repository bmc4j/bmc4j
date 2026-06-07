package kotlin.enums;

import org.bmc4j.models.audit.BmcModelConforms;

import java.util.List;

/**
 * Clean model of Kotlin's {@code kotlin.enums.EnumEntries} marker interface (the type of an enum's
 * {@code entries} property, Kotlin 1.9+). The real interface is a sealed {@code List} sub-type
 * implemented by the stdlib-internal {@code EnumEntriesList}; analysis-facing code only ever sees it
 * as a {@code List}, so modeling it as a plain {@code List} sub-interface is sufficient. The concrete
 * {@link EnumEntriesList} model supplies the behaviour.
 *
 * @param <E> the enum type
 */
@BmcModelConforms("Kotlin stdlib model — @BmcProof (model-conformance-proofs); facade/value model, audited at class level")
public interface EnumEntries<E extends Enum<E>> extends List<E> {
}
