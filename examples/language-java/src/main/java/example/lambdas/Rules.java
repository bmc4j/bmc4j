package example.lambdas;

import java.util.function.IntUnaryOperator;

/** A higher-order function under proof — it takes a rule (a lambda or method reference). */
public final class Rules {

    private Rules() {
    }

    /** Apply a rule twice: {@code rule(rule(x))}. */
    public static int applyTwice(IntUnaryOperator rule, int x) {
        return rule.applyAsInt(rule.applyAsInt(x));
    }

    /** A named rule, usable as a method reference. */
    public static int doubled(int x) {
        return x * 2;
    }
}
