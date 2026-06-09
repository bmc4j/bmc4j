package kotlin.math;

import static org.bmc4j.analysis.BmcUnmodelledReached.fail;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcUnmodelable;

/**
 * Clean model of Kotlin's {@code MathKt} facade ({@code kotlin.math.*}) — the NON-INLINE residue of the
 * math functions.
 *
 * <p>Almost the entire {@code kotlin.math} surface a proof reaches — {@code abs}/{@code min}/{@code
 * max}/{@code sqrt}/{@code pow}/{@code ceil}/{@code floor}/{@code sign(Double)}/{@code round}/… — is
 * {@code @InlineOnly}: it inlines into the caller and the inlined body calls {@link java.lang.Math}
 * directly, which JBMC already analyzes soundly. Those have NO JVM method on {@code MathKt} and are
 * therefore never reached on this facade (and need no model). What remains as a real {@code MathKt} JVM
 * member is this small non-inline residue.
 *
 * <p>Modeled (sound, integer-precise or {@link java.lang.Math}-delegating): {@code getSign(Int/Long)}
 * (the {@code Int.sign}/{@code Long.sign} property getters → {@link Integer#signum}/{@link
 * Long#signum}); {@code roundToInt}/{@code roundToLong} (round-half-up via {@link Math#round}, with
 * Kotlin's NaN-throws contract); {@code truncate} (round toward zero); {@code log}/{@code log2}
 * (delegating to {@link Math#log}). These delegate to {@code java.lang.Math}, which JBMC handles over
 * concrete inputs.
 *
 * <p>Waived loud ({@code @BmcUnmodelable}): the inverse hyperbolic transcendentals {@code acosh}/{@code
 * asinh}/{@code atanh} have no {@code java.lang.Math} primitive (the stdlib computes them from
 * {@code ln}/{@code sqrt} via series-equivalent closed forms); a faithful bounded model earns nothing
 * and the no-symbolic-double policy means they're concrete-only anyway, so they stay loud-if-reached
 * rather than proceed on a fiction.
 */
public final class MathKt {

    private MathKt() {
    }

    // ---- Int.sign / Long.sign property getters: -1/0/+1. Integer/Long.signum is bit-precise and sound.
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int getSign(int value) {
        return Integer.signum(value);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int getSign(long value) {
        return Long.signum(value);
    }

    // ---- roundToInt / roundToLong: round half-up to the nearest integer, Kotlin's contract throws
    //   IllegalArgumentException on NaN and clamps +/-Infinity to Int/Long MIN/MAX. Math.round gives
    //   round-half-up and already saturates infinities to MAX/MIN; we add the explicit NaN guard.
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int roundToInt(double value) {
        if (Double.isNaN(value)) {
            throw new IllegalArgumentException("Cannot round NaN value.");
        }
        long r = Math.round(value);
        if (r > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (r < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        return (int) r;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int roundToInt(float value) {
        if (Float.isNaN(value)) {
            throw new IllegalArgumentException("Cannot round NaN value.");
        }
        return Math.round(value);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static long roundToLong(double value) {
        if (Double.isNaN(value)) {
            throw new IllegalArgumentException("Cannot round NaN value.");
        }
        return Math.round(value);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static long roundToLong(float value) {
        if (Float.isNaN(value)) {
            throw new IllegalArgumentException("Cannot round NaN value.");
        }
        return Math.round((double) value);
    }

    // ---- truncate: round TOWARD ZERO to a whole number, returned as the same FP type. Delegates to the
    //   sound rounding primitives (ceil for negatives, floor for non-negatives) in java.lang.Math.
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static double truncate(double value) {
        return value < 0.0 ? Math.ceil(value) : Math.floor(value);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static float truncate(float value) {
        return (float) (value < 0.0f ? Math.ceil(value) : Math.floor(value));
    }

    // ---- log(x, base) = ln(x) / ln(base); log2(x) = log(x) / ln(2). Delegates to Math.log. (Float
    //   overloads return float, matching the stdlib.)
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static double log(double value, double base) {
        return Math.log(value) / Math.log(base);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static float log(float value, float base) {
        return (float) (Math.log(value) / Math.log(base));
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static double log2(double value) {
        return Math.log(value) / Math.log(2.0);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static float log2(float value) {
        return (float) (Math.log(value) / Math.log(2.0));
    }

    // ---- acosh / asinh / atanh: inverse hyperbolic transcendentals. No java.lang.Math primitive — the
    //   stdlib computes them from ln/sqrt closed forms; a bounded model earns nothing and they're
    //   concrete-only under the no-symbolic-double policy. Loud-if-reached.
    @BmcUnmodelable(reason = "inverse hyperbolic acosh has no java.lang.Math primitive (the stdlib computes "
            + "it from a ln/sqrt closed form); concrete-only double transcendental under the no-symbolic-double "
            + "policy — a bounded model earns nothing; loud-if-reached")
    public static double acosh(double value) {
        throw fail("bmc4j: unmodelled member kotlin.math.MathKt.acosh(double) — inverse hyperbolic transcendental with no java.lang.Math primitive");
    }

    @BmcUnmodelable(reason = "inverse hyperbolic asinh has no java.lang.Math primitive (the stdlib computes "
            + "it from a ln/sqrt closed form); concrete-only double transcendental under the no-symbolic-double "
            + "policy — a bounded model earns nothing; loud-if-reached")
    public static double asinh(double value) {
        throw fail("bmc4j: unmodelled member kotlin.math.MathKt.asinh(double) — inverse hyperbolic transcendental with no java.lang.Math primitive");
    }

    @BmcUnmodelable(reason = "inverse hyperbolic atanh has no java.lang.Math primitive (the stdlib computes "
            + "it from a ln closed form); concrete-only double transcendental under the no-symbolic-double "
            + "policy — a bounded model earns nothing; loud-if-reached")
    public static double atanh(double value) {
        throw fail("bmc4j: unmodelled member kotlin.math.MathKt.atanh(double) — inverse hyperbolic transcendental with no java.lang.Math primitive");
    }
}
