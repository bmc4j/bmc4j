package proofs.loopcontract;

import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;
import org.bmc4j.LoopInvariant;
import org.bmc4j.Verdict;

/**
 * The ANNOTATION form of a loop contract: {@link LoopInvariant} attaches an inductive invariant to a REAL
 * {@code for} loop written as-is in the proof, instead of the {@code Bmc.loop*} marker re-author. bmc4j
 * recovers the loop's header/guard/body/back-edge from the bytecode (by the {@code .N} engine loop id) and
 * lowers it to the same sound base/step/summary VCs.
 *
 * <p>The {@code loop()} id is the {@code --unwindset}-form id (run under {@code @BmcProfile} to print it);
 * for these proof methods the loop is the only one, so it is {@code ...:()V.0}. The {@code predicate} is a
 * static boolean whose PARAMETER NAMES bind by name to the loop's locals.
 */
class LoopInvariantAnnotationProof {

    // The counting loop's inductive invariant, as a named predicate. Parameter names (i, s, n) bind by name
    // to the loop's locals.
    static boolean countingInvariant(int i, int s, int n) {
        return i >= 0 && i <= n && s == i * (i - 1) / 2;
    }

    /**
     * VERIFIES: the real counted loop {@code for (i=0; i<n; i++) s+=i;} SUMMARIZED via @LoopInvariant at
     * unwind = 1 -- the loop is recovered from the bytecode and contracted in place, no markers.
     */
    @BmcProof(unwind = 1)
    @LoopInvariant(
            loop = "java::proofs.loopcontract.LoopInvariantAnnotationProof.counting_loop_annotated_summarized:()V.0",
            predicate = "countingInvariant")
    void counting_loop_annotated_summarized() {
        int n = Bmc.anyInt(0, 1000);
        int s = 0;
        for (int i = 0; i < n; i++) {
            s = s + i;
        }
        Bmc.check(s == n * (n - 1) / 2);
    }

    // A WRONG invariant: the accumulator term is off (i*(i+1)/2 instead of i*(i-1)/2).
    static boolean wrongCountingInvariant(int i, int s, int n) {
        return i >= 0 && i <= n && s == i * (i + 1) / 2;
    }

    /**
     * REFUTES: the same recovered loop with a WRONG predicate. The step preservation assert fails -> REFUTED,
     * never a false VERIFIED. Pins that the annotation-recovered contract asserts its base/step VCs exactly
     * like the marker form.
     */
    @BmcProof(unwind = 1, expect = Verdict.REFUTED)
    @LoopInvariant(
            loop = "java::proofs.loopcontract.LoopInvariantAnnotationProof.wrong_annotated_invariant_refutes:()V.0",
            predicate = "wrongCountingInvariant")
    void wrong_annotated_invariant_refutes() {
        int n = Bmc.anyInt(0, 1000);
        int s = 0;
        for (int i = 0; i < n; i++) {
            s = s + i;
        }
        Bmc.check(s == n * (n - 1) / 2);
    }
}
