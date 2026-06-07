package kotlin.sequences;

import org.bmc4j.models.audit.BmcModelConforms;

import java.util.Iterator;

/**
 * Clean BMC model of Kotlin's {@code kotlin.sequences.Sequence} interface. The real interface is
 * lazy ({@code iterator()} produces elements on demand); for bounded model checking we evaluate
 * eagerly over a bounded backing list ({@link ListSequence}), which is sound for the bounded inputs
 * JBMC unwinds over. The single abstract method mirrors the real one so the compiler-emitted
 * {@code Lkotlin/sequences/Sequence;} types in {@link SequencesKt} resolve against this model.
 */
public interface Sequence<T> {
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    Iterator<T> iterator();
}
