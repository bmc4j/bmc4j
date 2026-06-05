package java.util;

/**
 * Partial BMC model of {@link java.util.Arrays}: just {@code asList}, which Kotlin's {@code listOf}
 * and Java's {@code Arrays.asList} route through. Other {@code Arrays} methods remain JBMC stubs
 * exactly as before this class existed (a missing method is havoc'd, not an error), so adding this
 * model is regression-safe.
 */
public class Arrays {

    private Arrays() {
    }

    @SafeVarargs
    public static <T> List<T> asList(T... a) {
        ArrayList<T> l = new ArrayList<>();
        for (T t : a) {
            l.add(t);
        }
        return l;
    }
}
