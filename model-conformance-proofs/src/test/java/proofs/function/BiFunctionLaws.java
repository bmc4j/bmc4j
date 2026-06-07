package proofs.function;

import java.util.function.BiFunction;
import java.util.function.Function;
import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;

/**
 * Model proofs (axis 2) for {@link java.util.function.BiFunction#andThen}: apply the two-arg
 * function, then feed its result through a follow-on {@link Function}. Pins the chain shape
 * {@code bf.andThen(f).apply(a, b)} with concrete and symbolic inputs.
 */
class BiFunctionLaws {

    @BmcProof
    void andThen_pipes_result() {
        BiFunction<Integer, Integer, Integer> add = (a, b) -> a + b;
        Function<Integer, Integer> times10 = x -> x * 10;
        // (2 + 3) * 10 = 50
        Bmc.check(add.andThen(times10).apply(2, 3) == 50);
    }

    @BmcProof
    void andThen_symbolic() {
        BiFunction<Integer, Integer, Integer> add = (a, b) -> a + b;
        Function<Integer, Integer> plus1 = x -> x + 1;
        int a = Bmc.anyInt(0, 1000);
        int b = Bmc.anyInt(0, 1000);
        Bmc.check(add.andThen(plus1).apply(a, b) == a + b + 1);
    }

    @BmcProof
    void andThen_chain_two_functions() {
        BiFunction<Integer, Integer, Integer> mul = (a, b) -> a * b;
        Function<Integer, Integer> plus1 = x -> x + 1;
        Function<Integer, Integer> times2 = x -> x * 2;
        // ((3 * 4) + 1) * 2 = 26
        Bmc.check(mul.andThen(plus1).andThen(times2).apply(3, 4) == 26);
    }
}
