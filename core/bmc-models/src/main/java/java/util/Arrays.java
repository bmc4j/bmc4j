package java.util;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcModelTail;

/**
 * Partial BMC model of {@link java.util.Arrays}: just {@code asList}, which Kotlin's {@code listOf}
 * and Java's {@code Arrays.asList} route through. The rest of the (large) {@code Arrays} surface is
 * deliberately unmodeled; with the audit tail + loud-body synthesis, reaching any of those members
 * now fails LOUDLY under JBMC naming the member, rather than silently havocking to a nondet result.
 */
@BmcModelConforms("models asList (the listOf / Arrays.asList route); the rest is the tail")
@BmcModelTail(reason = "the broad Arrays utility surface (sort/binarySearch/fill/copyOf/copyOfRange/equals/hashCode/stream/setAll/parallel*/toString/deep*/… across every primitive + Object overload) is out of scope for a bounded model; only asList is modeled. All loud under JBMC")
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
