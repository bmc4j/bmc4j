package org.cprover;

/**
 * COMPILE-ONLY source-compatible stub of bmc-runtime's {@code org.cprover.CProver} primitive, present
 * here only so this module's models can bound their eager unwinds with {@code CProver.assume(...)} —
 * the same primitive the JDK blocking j.u.c models in bmc-models use. This module cannot depend on
 * bmc-runtime (bmc-runtime already consumes this module's compiled classes, so the dependency would be
 * a cycle), and this stub is NOT bundled into the model jar (only the {@code main} source set is). At
 * verification time JBMC recognises {@code org.cprover.CProver} by FQN and substitutes its assume
 * semantics from the analysis classpath; this stub never reaches it.
 *
 * <p>The real class is the authority — see {@code org.cprover.CProver} in bmc-runtime. Keep this
 * signature in lockstep with it.
 */
public final class CProver {

    private CProver() {
    }

    /** Constrains the analysis to paths where {@code assumption} holds; mirrors the real signature. */
    public static void assume(boolean assumption) {
        // No-op stub: never executed by the model jar (compile-only), JBMC supplies the real primitive.
    }

    // Nondeterministic-value primitives — JBMC introduces a fresh symbolic value of the type at each
    // call site (it explores every value at once). Used by the kotlin.random.Random bounded-draw model
    // to model each draw as nondet-in-range. Compile-only stub bodies; JBMC supplies the real semantics.

    /** A fresh symbolic {@code int}; mirrors the real signature. */
    public static int nondetInt() {
        return 0;
    }

    /** A fresh symbolic {@code long}; mirrors the real signature. */
    public static long nondetLong() {
        return 0L;
    }

    /** A fresh symbolic {@code boolean}; mirrors the real signature. */
    public static boolean nondetBoolean() {
        return false;
    }
}
