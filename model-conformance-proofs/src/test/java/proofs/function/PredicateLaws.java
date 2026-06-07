package proofs.function;

import java.util.function.Predicate;
import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;

/**
 * Model proofs (axis 2) for the {@link java.util.function.Predicate} boolean-combinator defaults
 * ({@code and}/{@code or}/{@code negate}) and the {@code isEqual}/{@code not} statics. Each proof
 * builds a real predicate-composition chain and checks it against the boolean truth — including a
 * De Morgan law and a chained {@code and().or().negate()} pattern — over a symbolic input where cheap.
 */
class PredicateLaws {

    @BmcProof
    void and_is_conjunction() {
        Predicate<Integer> positive = x -> x > 0;
        Predicate<Integer> even = x -> x % 2 == 0;
        int x = Bmc.anyInt(-100, 100);
        Bmc.check(positive.and(even).test(x) == (x > 0 && x % 2 == 0));
    }

    @BmcProof
    void or_is_disjunction() {
        Predicate<Integer> positive = x -> x > 0;
        Predicate<Integer> even = x -> x % 2 == 0;
        int x = Bmc.anyInt(-100, 100);
        Bmc.check(positive.or(even).test(x) == (x > 0 || x % 2 == 0));
    }

    @BmcProof
    void negate_is_complement() {
        Predicate<Integer> positive = x -> x > 0;
        int x = Bmc.anyInt(-100, 100);
        Bmc.check(positive.negate().test(x) == !(x > 0));
    }

    @BmcProof
    void de_morgan() {
        Predicate<Integer> positive = x -> x > 0;
        Predicate<Integer> even = x -> x % 2 == 0;
        int x = Bmc.anyInt(-100, 100);
        // !(a && b) == (!a || !b)
        Bmc.check(positive.and(even).negate().test(x)
                == positive.negate().or(even.negate()).test(x));
    }

    @BmcProof
    void chained_and_or_negate() {
        Predicate<Integer> positive = x -> x > 0;
        Predicate<Integer> even = x -> x % 2 == 0;
        Predicate<Integer> big = x -> x > 50;
        int x = Bmc.anyInt(-100, 100);
        // (positive AND even) OR big, then negate
        boolean expected = !((x > 0 && x % 2 == 0) || x > 50);
        Bmc.check(positive.and(even).or(big).negate().test(x) == expected);
    }

    @BmcProof
    void not_static_is_negate() {
        Predicate<Integer> even = x -> x % 2 == 0;
        int x = Bmc.anyInt(-100, 100);
        Bmc.check(Predicate.not(even).test(x) == !(x % 2 == 0));
    }

    @BmcProof
    void isEqual_value_equality() {
        Predicate<Integer> isFive = Predicate.isEqual(5);
        Bmc.check(isFive.test(5));
        Bmc.check(!isFive.test(6));
    }
}
