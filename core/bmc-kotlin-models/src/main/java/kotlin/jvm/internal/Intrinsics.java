package kotlin.jvm.internal;

import kotlin.UninitializedPropertyAccessException;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcUnmodelable;

/**
 * Clean JBMC model of Kotlin's {@code Intrinsics} runtime helpers.
 *
 * <p>The real implementation builds exceptions via stack-trace sanitization (array
 * and reflection-ish operations) that, once fully resolvable on the classpath, make
 * JBMC generate a cascade of spurious null/array/cast checks inside Intrinsics. This
 * model keeps only the observable semantics — a null check that throws, the
 * uninitialized-{@code lateinit} read that throws, equality, comparison — so
 * idiomatic null-safe Kotlin analyzes cleanly.
 *
 * <p>Compiled into a separate source set and bundled as a resource (not on any
 * runtime classpath); the JUnit extension extracts it onto JBMC's analysis classpath.
 *
 * <p>No class-level {@code @BmcModelTail}: the entire real {@code Intrinsics} surface is enumerated
 * per-member — the null-safety / equality / comparison helpers are modeled and
 * {@code @BmcModelConforms}-pinned, and the reflective / reified / stacktrace-sanitizing / boxed-FP
 * overloads are class-level {@code @BmcUnmodelable} loud walls (their real stdlib bytecode does NOT
 * analyze soundly when reached — boxed-FP equality, runtime reification/reflection, and stacktrace
 * sanitization route through machinery JBMC nondet-stubs, so a reach must demote loudly rather than
 * verify on a fiction) — so there is no undeclared remainder for a tail to absorb.
 */
@BmcUnmodelable(member = "areEqual(double, java.lang.Double)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "areEqual(float, java.lang.Float)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "areEqual(java.lang.Double, double)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "areEqual(java.lang.Double, java.lang.Double)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "areEqual(java.lang.Float, float)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "areEqual(java.lang.Float, java.lang.Float)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "checkFieldIsNotNull(java.lang.Object, java.lang.String, java.lang.String)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "checkHasClass(java.lang.String)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "checkHasClass(java.lang.String, java.lang.String)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "checkReturnedValueIsNotNull(java.lang.Object, java.lang.String, java.lang.String)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "needClassReification()", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "needClassReification(java.lang.String)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "reifiedOperationMarker(int, java.lang.String)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "reifiedOperationMarker(int, java.lang.String, java.lang.String)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "throwAssert()", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "throwAssert(java.lang.String)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "throwIllegalArgument()", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "throwIllegalArgument(java.lang.String)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "throwIllegalState()", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "throwIllegalState(java.lang.String)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "throwJavaNpe(java.lang.String)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "throwUndefinedForReified()", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "throwUndefinedForReified(java.lang.String)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "throwUninitializedProperty(java.lang.String)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
public class Intrinsics {

    private Intrinsics() {
    }

    @BmcModelConforms("fundamentals-kotlin null-safety proofs")
    public static void checkNotNullParameter(Object value, String name) {
        if (value == null) {
            throw new NullPointerException(name);
        }
    }

    @BmcModelConforms("fundamentals-kotlin null-safety proofs")
    public static void checkNotNull(Object value) {
        if (value == null) {
            throw new NullPointerException();
        }
    }

    @BmcModelConforms("fundamentals-kotlin null-safety proofs")
    public static void checkNotNull(Object value, String message) {
        if (value == null) {
            throw new NullPointerException(message);
        }
    }

    @BmcModelConforms("fundamentals-kotlin null-safety proofs")
    public static void checkNotNullExpressionValue(Object value, String expression) {
        if (value == null) {
            throw new NullPointerException(expression);
        }
    }

    @BmcModelConforms("fundamentals-kotlin null-safety proofs")
    public static void checkExpressionValueIsNotNull(Object value, String expression) {
        if (value == null) {
            throw new NullPointerException(expression);
        }
    }

    @BmcModelConforms("fundamentals-kotlin null-safety proofs")
    public static void checkParameterIsNotNull(Object value, String name) {
        if (value == null) {
            throw new NullPointerException(name);
        }
    }

    @BmcModelConforms("fundamentals-kotlin null-safety proofs")
    public static void checkReturnedValueIsNotNull(Object value, String message) {
        if (value == null) {
            throw new NullPointerException(message);
        }
    }

    @BmcModelConforms("fundamentals-kotlin null-safety proofs")
    public static void checkFieldIsNotNull(Object value, String message) {
        if (value == null) {
            throw new NullPointerException(message);
        }
    }

    @BmcModelConforms("fundamentals-kotlin null-safety proofs")
    public static void throwNpe() {
        throw new NullPointerException();
    }

    @BmcModelConforms("fundamentals-kotlin null-safety proofs")
    public static void throwNpe(String message) {
        throw new NullPointerException(message);
    }

    @BmcModelConforms("fundamentals-kotlin null-safety proofs")
    public static void throwJavaNpe() {
        throw new NullPointerException();
    }

    /**
     * Reading a {@code lateinit} property before initialization lowers (kotlinc 2.x) to this call,
     * passing the property name. The real helper builds the message and delegates to {@code
     * throwUninitializedProperty}, which constructs a {@link UninitializedPropertyAccessException}
     * through {@code sanitizeStackTrace} (the reflective stack-trace machinery this model avoids).
     * Here we throw the real exception directly with the same documented message, so an uninitialized
     * read refutes through the genuine {@code UninitializedPropertyAccessException} path — not an
     * incidental null deref. The exception type's constructors are trivial (plain {@code
     * RuntimeException} delegation) and link cleanly under JBMC, so no model of it is needed.
     */
    @BmcModelConforms("fundamentals-kotlin lateinit proofs (proofs.lateinitprops)")
    public static void throwUninitializedPropertyAccessException(String name) {
        throw new UninitializedPropertyAccessException("lateinit property " + name + " has not been initialized");
    }

    @BmcModelConforms("fundamentals-kotlin null-safety proofs")
    public static boolean areEqual(Object first, Object second) {
        if (first == second) {
            return true;
        }
        if (first == null || second == null) {
            return false;
        }
        // Kotlin `==` and string `when` lower to areEqual; JBMC's native String.equals is unsound, so
        // compare strings character-wise (length + charAt are sound) — the same fix as BmcStrings.
        if (first instanceof String && second instanceof String) {
            String a = (String) first;
            String b = (String) second;
            int n = a.length();
            if (n != b.length()) {
                return false;
            }
            for (int i = 0; i < n; i++) {
                if (a.charAt(i) != b.charAt(i)) {
                    return false;
                }
            }
            return true;
        }
        return first.equals(second);
    }

    @BmcModelConforms("fundamentals-kotlin null-safety proofs")
    public static int compare(int first, int second) {
        return first < second ? -1 : (first == second ? 0 : 1);
    }

    @BmcModelConforms("fundamentals-kotlin null-safety proofs")
    public static int compare(long first, long second) {
        return first < second ? -1 : (first == second ? 0 : 1);
    }

    @BmcModelConforms("fundamentals-kotlin null-safety proofs")
    public static String stringPlus(String self, Object other) {
        return self + other;
    }

    // --- remaining facade surface: declared class-level via @BmcUnmodelable above (loud-if-reached:
    //     boxed-FP / reflective / reified / stacktrace-sanitizing walls). ---
}
