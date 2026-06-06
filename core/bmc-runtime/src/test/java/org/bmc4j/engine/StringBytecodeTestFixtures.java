package org.bmc4j.engine;

/**
 * Java record fixtures for {@link StringBytecodeTest}. These MUST stay Java: the test exercises
 * javac's actual {@code java.lang.runtime.ObjectMethods} bootstrap {@code invokedynamic} for record
 * {@code equals}/{@code hashCode}/{@code toString} (and the canonical {@code Name[c=v, ...]} toString
 * form). A Kotlin {@code @JvmRecord data class} would instead emit Kotlin-generated members
 * ({@code Name(c=v, ...)} toString, no ObjectMethods indy), so the desugar pass would have nothing to
 * desugar — keeping these as Java records preserves the exact bytecode shape the pass targets.
 */
final class StringBytecodeTestFixtures {

    private StringBytecodeTestFixtures() {
    }

    /** A real record, so the test exercises javac's actual ObjectMethods bootstrap. */
    record Pt(int x, int y) {
    }

    /**
     * A record with every primitive component, so the JVM-level test exercises each
     * {@code emitComponentHash} arithmetic branch (int/long/boolean/double/float). String/reference
     * components are NOT included here because their sound hash routes through
     * {@code CProverString.charAt}, which only has meaning inside JBMC (it returns '\0' on a real
     * JVM); the String-component dependency is checked in the BMC conformance proofs instead.
     */
    record Prims(int i, long l, boolean b, double d, float f) {
    }

    /** A record whose component is a non-String reference, so toString stays un-desugared. */
    record WithRef(int n, Object ref) {
    }
}
