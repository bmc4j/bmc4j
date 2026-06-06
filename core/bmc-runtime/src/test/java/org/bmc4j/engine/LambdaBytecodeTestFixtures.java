package org.bmc4j.engine;

import java.util.function.Function;
import java.util.function.IntUnaryOperator;

/**
 * Java lambda fixtures for {@link LambdaBytecodeTest}. These MUST stay Java: the test runs the class
 * through {@link LambdaBytecode} and asserts javac's {@code LambdaMetafactory} {@code invokedynamic}
 * sites are desugared into one loadable generated class per site. Kotlin's SAM-conversion /
 * method-reference codegen differs (private synthetic accessors, different bootstrap shape), so a
 * Kotlin fixture would not reproduce the javac bytecode the pass targets.
 */
final class LambdaBytecodeTestFixtures {

    private LambdaBytecodeTestFixtures() {
    }

    /** Fixtures with real javac-emitted LambdaMetafactory sites. */
    static final class Fix {
        static int capturing(int base) {
            IntUnaryOperator f = x -> x + base; // capturing lambda
            return f.applyAsInt(5);
        }

        static int staticRef() {
            IntUnaryOperator f = Fix::triple; // static method reference
            return f.applyAsInt(4);
        }

        static int instanceRef() {
            Function<String, Integer> len = String::length; // unbound instance ref + box/unbox
            return len.apply("abcde");
        }

        static int triple(int z) {
            return z * 3;
        }
    }
}
