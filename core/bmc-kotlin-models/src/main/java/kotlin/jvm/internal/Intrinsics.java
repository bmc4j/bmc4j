package kotlin.jvm.internal;

/**
 * Clean JBMC model of Kotlin's {@code Intrinsics} runtime helpers.
 *
 * <p>The real implementation builds exceptions via stack-trace sanitization (array
 * and reflection-ish operations) that, once fully resolvable on the classpath, make
 * JBMC generate a cascade of spurious null/array/cast checks inside Intrinsics. This
 * model keeps only the observable semantics — a null check that throws, equality,
 * comparison — so idiomatic null-safe Kotlin analyzes cleanly.
 *
 * <p>Compiled into a separate source set and bundled as a resource (not on any
 * runtime classpath); the JUnit extension extracts it onto JBMC's analysis classpath.
 */
public class Intrinsics {

    private Intrinsics() {
    }

    public static void checkNotNullParameter(Object value, String name) {
        if (value == null) {
            throw new NullPointerException(name);
        }
    }

    public static void checkNotNull(Object value) {
        if (value == null) {
            throw new NullPointerException();
        }
    }

    public static void checkNotNull(Object value, String message) {
        if (value == null) {
            throw new NullPointerException(message);
        }
    }

    public static void checkNotNullExpressionValue(Object value, String expression) {
        if (value == null) {
            throw new NullPointerException(expression);
        }
    }

    public static void checkExpressionValueIsNotNull(Object value, String expression) {
        if (value == null) {
            throw new NullPointerException(expression);
        }
    }

    public static void checkParameterIsNotNull(Object value, String name) {
        if (value == null) {
            throw new NullPointerException(name);
        }
    }

    public static void checkReturnedValueIsNotNull(Object value, String message) {
        if (value == null) {
            throw new NullPointerException(message);
        }
    }

    public static void checkFieldIsNotNull(Object value, String message) {
        if (value == null) {
            throw new NullPointerException(message);
        }
    }

    public static void throwNpe() {
        throw new NullPointerException();
    }

    public static void throwNpe(String message) {
        throw new NullPointerException(message);
    }

    public static void throwJavaNpe() {
        throw new NullPointerException();
    }

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

    public static int compare(int first, int second) {
        return first < second ? -1 : (first == second ? 0 : 1);
    }

    public static int compare(long first, long second) {
        return first < second ? -1 : (first == second ? 0 : 1);
    }

    public static String stringPlus(String self, Object other) {
        return self + other;
    }
}
