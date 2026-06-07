package kotlin.jvm.internal;

import kotlin.UninitializedPropertyAccessException;
import static org.bmc4j.analysis.BmcUnmodelledReached.fail;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcModelTail;
import org.bmc4j.models.audit.BmcNotNeeded;

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
 */
@BmcModelTail(reason = "exotic Intrinsics surface — reflective/spread/typed-checkNotNull/array overloads "
        + "Kotlin's null-safety lowering does not emit on the bounded analysis path; loud under JBMC if reached")
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

    // --- not-needed members (loud stubs; reaching one demotes to a member-named UNKNOWN) ---
    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void areEqual(double a0, java.lang.Double a1) {
        throw fail("bmc4j: unmodelled member kotlin.jvm.internal.Intrinsics.areEqual(double,java.lang.Double) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void areEqual(float a0, java.lang.Float a1) {
        throw fail("bmc4j: unmodelled member kotlin.jvm.internal.Intrinsics.areEqual(float,java.lang.Float) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void areEqual(java.lang.Double a0, double a1) {
        throw fail("bmc4j: unmodelled member kotlin.jvm.internal.Intrinsics.areEqual(java.lang.Double,double) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void areEqual(java.lang.Double a0, java.lang.Double a1) {
        throw fail("bmc4j: unmodelled member kotlin.jvm.internal.Intrinsics.areEqual(java.lang.Double,java.lang.Double) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void areEqual(java.lang.Float a0, float a1) {
        throw fail("bmc4j: unmodelled member kotlin.jvm.internal.Intrinsics.areEqual(java.lang.Float,float) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void areEqual(java.lang.Float a0, java.lang.Float a1) {
        throw fail("bmc4j: unmodelled member kotlin.jvm.internal.Intrinsics.areEqual(java.lang.Float,java.lang.Float) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void checkFieldIsNotNull(java.lang.Object a0, java.lang.String a1, java.lang.String a2) {
        throw fail("bmc4j: unmodelled member kotlin.jvm.internal.Intrinsics.checkFieldIsNotNull(java.lang.Object,java.lang.String,java.lang.String) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void checkHasClass(java.lang.String a0) {
        throw fail("bmc4j: unmodelled member kotlin.jvm.internal.Intrinsics.checkHasClass(java.lang.String) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void checkHasClass(java.lang.String a0, java.lang.String a1) {
        throw fail("bmc4j: unmodelled member kotlin.jvm.internal.Intrinsics.checkHasClass(java.lang.String,java.lang.String) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void checkReturnedValueIsNotNull(java.lang.Object a0, java.lang.String a1, java.lang.String a2) {
        throw fail("bmc4j: unmodelled member kotlin.jvm.internal.Intrinsics.checkReturnedValueIsNotNull(java.lang.Object,java.lang.String,java.lang.String) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void needClassReification() {
        throw fail("bmc4j: unmodelled member kotlin.jvm.internal.Intrinsics.needClassReification() — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void needClassReification(java.lang.String a0) {
        throw fail("bmc4j: unmodelled member kotlin.jvm.internal.Intrinsics.needClassReification(java.lang.String) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void reifiedOperationMarker(int a0, java.lang.String a1) {
        throw fail("bmc4j: unmodelled member kotlin.jvm.internal.Intrinsics.reifiedOperationMarker(int,java.lang.String) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void reifiedOperationMarker(int a0, java.lang.String a1, java.lang.String a2) {
        throw fail("bmc4j: unmodelled member kotlin.jvm.internal.Intrinsics.reifiedOperationMarker(int,java.lang.String,java.lang.String) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void throwAssert() {
        throw fail("bmc4j: unmodelled member kotlin.jvm.internal.Intrinsics.throwAssert() — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void throwAssert(java.lang.String a0) {
        throw fail("bmc4j: unmodelled member kotlin.jvm.internal.Intrinsics.throwAssert(java.lang.String) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void throwIllegalArgument() {
        throw fail("bmc4j: unmodelled member kotlin.jvm.internal.Intrinsics.throwIllegalArgument() — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void throwIllegalArgument(java.lang.String a0) {
        throw fail("bmc4j: unmodelled member kotlin.jvm.internal.Intrinsics.throwIllegalArgument(java.lang.String) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void throwIllegalState() {
        throw fail("bmc4j: unmodelled member kotlin.jvm.internal.Intrinsics.throwIllegalState() — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void throwIllegalState(java.lang.String a0) {
        throw fail("bmc4j: unmodelled member kotlin.jvm.internal.Intrinsics.throwIllegalState(java.lang.String) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void throwJavaNpe(java.lang.String a0) {
        throw fail("bmc4j: unmodelled member kotlin.jvm.internal.Intrinsics.throwJavaNpe(java.lang.String) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void throwUndefinedForReified() {
        throw fail("bmc4j: unmodelled member kotlin.jvm.internal.Intrinsics.throwUndefinedForReified() — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void throwUndefinedForReified(java.lang.String a0) {
        throw fail("bmc4j: unmodelled member kotlin.jvm.internal.Intrinsics.throwUndefinedForReified(java.lang.String) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

    @BmcNotNeeded(reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed")
    public static void throwUninitializedProperty(java.lang.String a0) {
        throw fail("bmc4j: unmodelled member kotlin.jvm.internal.Intrinsics.throwUninitializedProperty(java.lang.String) — real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed");
    }

}
