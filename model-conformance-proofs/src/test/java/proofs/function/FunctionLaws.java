package proofs.function;

import java.util.function.Function;
import java.util.function.UnaryOperator;
import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;

/**
 * Model proofs (axis 2) for the {@link java.util.function.Function} / {@link UnaryOperator}
 * composition defaults. These pin the actual lambda-chaining pattern — {@code f.andThen(g)},
 * {@code f.compose(g)}, {@code Function.identity()} — not the members in isolation: each proof
 * builds a real composition chain and checks the concrete (and, where cheap, symbolic) result.
 * Lambdas are desugared by bmc4j's own layer; the modeled defaults supply the composition order.
 */
class FunctionLaws {

    @BmcProof
    void andThen_applies_in_order() {
        Function<Integer, Integer> plus1 = x -> x + 1;
        Function<Integer, Integer> times2 = x -> x * 2;
        // andThen: plus1 first, then times2 -> (3+1)*2 = 8
        Bmc.check(plus1.andThen(times2).apply(3) == 8);
    }

    @BmcProof(unwind = 1)
    void compose_applies_in_order() {
        Function<Integer, Integer> plus1 = x -> x + 1;
        Function<Integer, Integer> times2 = x -> x * 2;
        // compose: times2 first, then plus1 -> (3*2)+1 = 7
        Bmc.check(plus1.compose(times2).apply(3) == 7);
    }

    @BmcProof(unwind = 1)
    void andThen_compose_are_mirror() {
        Function<Integer, Integer> plus1 = x -> x + 1;
        Function<Integer, Integer> times2 = x -> x * 2;
        int x = Bmc.anyInt(0, 1000);
        // f.andThen(g) == g.compose(f), for all x in range (compare by value — unbox).
        Bmc.check(plus1.andThen(times2).apply(x).intValue() == times2.compose(plus1).apply(x).intValue());
    }

    @BmcProof
    void andThen_chain_three() {
        Function<Integer, Integer> plus1 = x -> x + 1;
        Function<Integer, Integer> times2 = x -> x * 2;
        Function<Integer, Integer> minus3 = x -> x - 3;
        // ((2+1)*2)-3 = 3
        Bmc.check(plus1.andThen(times2).andThen(minus3).apply(2) == 3);
    }

    @BmcProof
    void identity_is_neutral() {
        int x = Bmc.anyInt(0, 1000);
        Bmc.check(Function.identity().apply(x).equals(x));
    }

    @BmcProof
    void identity_left_right_neutral_under_andThen() {
        Function<Integer, Integer> plus1 = x -> x + 1;
        int x = Bmc.anyInt(0, 1000);
        // f.andThen(identity) == f == identity.andThen(f) — compare by value (unbox; == on boxed
        // Integers is reference equality and each apply() returns a fresh box).
        Bmc.check(plus1.andThen(Function.<Integer>identity()).apply(x).intValue() == plus1.apply(x).intValue());
        Bmc.check(Function.<Integer>identity().andThen(plus1).apply(x).intValue() == plus1.apply(x).intValue());
    }

    @BmcProof
    void unaryOperator_identity() {
        int x = Bmc.anyInt(0, 1000);
        UnaryOperator<Integer> id = UnaryOperator.identity();
        Bmc.check(id.apply(x).equals(x));
    }
}
