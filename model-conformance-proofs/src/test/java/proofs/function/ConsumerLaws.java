package proofs.function;

import java.util.function.Consumer;
import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;

/**
 * Model proofs (axis 2) for the {@link java.util.function.Consumer#andThen} default: it must run
 * both side effects, in order, on the same input. The proofs capture a mutable accumulator and
 * assert the post-state, pinning the sequencing (not just that each consumer runs).
 */
class ConsumerLaws {

    @BmcProof
    void andThen_runs_both_in_order() {
        int[] acc = new int[1];
        Consumer<Integer> add = x -> acc[0] += x;
        Consumer<Integer> doubleAdd = x -> acc[0] += 2 * x;
        // add then doubleAdd, both on 5: 0 + 5 + 10 = 15
        add.andThen(doubleAdd).accept(5);
        Bmc.check(acc[0] == 15);
    }

    @BmcProof(unwind = 1)
    void andThen_order_is_observable() {
        // Record the order: first consumer writes 1 at index 0 iff it ran first.
        int[] log = new int[2];
        int[] n = new int[1];
        Consumer<Integer> first = x -> log[n[0]++] = 1;
        Consumer<Integer> second = x -> log[n[0]++] = 2;
        first.andThen(second).accept(0);
        Bmc.check(log[0] == 1 && log[1] == 2);
    }

    @BmcProof
    void andThen_symbolic_sum() {
        int v = Bmc.anyInt(0, 1000);
        int[] acc = new int[1];
        Consumer<Integer> add = x -> acc[0] += x;
        add.andThen(add).accept(v);
        Bmc.check(acc[0] == 2 * v);
    }
}
