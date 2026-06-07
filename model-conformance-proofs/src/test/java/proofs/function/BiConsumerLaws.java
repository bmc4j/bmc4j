package proofs.function;

import java.util.function.BiConsumer;
import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;

/**
 * Model proofs (axis 2) for the {@link java.util.function.BiConsumer#andThen} default: run both
 * bi-consumers, in order, on the same (a, b) pair. Captures a mutable accumulator and asserts the
 * post-state to pin the sequencing.
 */
class BiConsumerLaws {

    @BmcProof
    void andThen_runs_both_in_order() {
        int[] acc = new int[1];
        BiConsumer<Integer, Integer> addSum = (a, b) -> acc[0] += a + b;
        BiConsumer<Integer, Integer> addProd = (a, b) -> acc[0] += a * b;
        // addSum then addProd on (2,3): 0 + (2+3) + (2*3) = 11
        addSum.andThen(addProd).accept(2, 3);
        Bmc.check(acc[0] == 11);
    }

    @BmcProof
    void andThen_symbolic() {
        int a = Bmc.anyInt(0, 1000);
        int b = Bmc.anyInt(0, 1000);
        int[] acc = new int[1];
        BiConsumer<Integer, Integer> addA = (x, y) -> acc[0] += x;
        BiConsumer<Integer, Integer> addB = (x, y) -> acc[0] += y;
        addA.andThen(addB).accept(a, b);
        Bmc.check(acc[0] == a + b);
    }
}
