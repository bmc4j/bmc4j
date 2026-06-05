package proofs.lambdas;

import example.lambdas.Rules;
import java.util.function.BiFunction;
import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;
import org.bmc4j.Verdict;

/**
 * Proving code that uses lambdas and method references. A lambda compiles to an {@code
 * invokedynamic} JBMC can't construct; bmc4j desugars each lambda site to an ordinary class
 * implementing the functional interface, so the proof analyses your code unchanged.
 */
class LambdaProofs {

    // PASS: a lambda passed into a higher-order function — applied twice, +1 each time, adds 2.
    @BmcProof
    void increment_twice_adds_two() {
        int x = Bmc.anyInt(-1000, 1000);
        Bmc.check(Rules.applyTwice(v -> v + 1, x) == x + 2);
    }

    // PASS: a method reference behaves the same — doubled twice is ×4.
    @BmcProof
    void method_reference_doubles_twice() {
        int x = Bmc.anyInt(-1000, 1000);
        Bmc.check(Rules.applyTwice(Rules::doubled, x) == 4 * x);
    }

    // PASS over every pair: the integer average never drops below the smaller input.
    @BmcProof
    void average_is_at_least_min() {
        int a = Bmc.anyInt(0, 1000);
        int b = Bmc.anyInt(0, 1000);
        BiFunction<Integer, Integer, Integer> avg = (x, y) -> (x + y) / 2;
        Bmc.check(avg.apply(a, b) >= Math.min(a, b));
    }

    // FAIL (the bug): "the average is strictly above the smaller" is false for adjacent values —
    // (0 + 1) / 2 == 0 by integer truncation. BMC finds the counterexample a=0, b=1.
    @BmcProof(expect = Verdict.REFUTED)
    void average_strictly_above_min() {
        int a = Bmc.anyInt(0, 1000);
        int b = Bmc.anyInt(0, 1000);
        Bmc.assume(a != b);
        BiFunction<Integer, Integer, Integer> avg = (x, y) -> (x + y) / 2;
        Bmc.check(avg.apply(a, b) > Math.min(a, b));
    }
}
