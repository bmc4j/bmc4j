package kotlin.coroutines.jvm.internal;

/**
 * Clean model of {@code kotlin.coroutines.jvm.internal.SpillingKt}. The real helper nulls out a spilled
 * local in a coroutine state machine's saved frame ONLY when coroutine debug mode is enabled; otherwise
 * it returns the value unchanged (identity). A compiler-generated suspend body with spilled reference
 * locals emits {@code SpillingKt.nullOutSpilledVariable(local)} before storing each {@code L$n} field.
 *
 * <p>Bundled — rather than left to resolve against the real kotlin-stdlib jar — so that EVERY
 * {@code kotlin.coroutines.jvm.internal.*} type a generated continuation references lives in the SAME
 * (bundled) classpath source. With this type in the stdlib jar instead, a continuation class straddles
 * two classpath sources, and JBMC must lazily link the bundled continuation's subtype-&gt;supertype
 * cast edge ({@code checkcast Continuation}) across sources — the order-dependent link that the older-
 * Kotlin legs intermittently drop, havoc'ing the cast (a spurious "Dynamic cast check" REFUTED). Modeling
 * it as the identity (debug mode off) is the sound, behavior-faithful default.
 */
public final class SpillingKt {

    private SpillingKt() {
    }

    public static Object nullOutSpilledVariable(Object value) {
        return value;
    }
}
