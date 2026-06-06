package org.bmc4j.engine;

import org.cprover.CProver;

/**
 * Sound stand-ins for Kotlin {@code Intrinsics} call sites the rewrite layer redirects
 * (see {@link KotlinParamBytecode}). Lives in bmc-runtime so it is always on the analysis
 * classpath, like {@link BmcMath} / {@link BmcStrings}.
 */
public final class BmcKotlin {

    private BmcKotlin() {
    }

    /**
     * The auto-assume form of {@code Intrinsics.checkNotNullParameter}: instead of throwing on a
     * null parameter (the honest-JVM semantics), prune the path — the proof then ranges exactly
     * over the inputs the Kotlin type system admits for a non-null-typed parameter. Identical
     * descriptor to the intrinsic, so the redirect is a pure owner swap.
     */
    public static void assumeNotNullParameter(Object value, String name) {
        CProver.assume(value != null);
    }
}
