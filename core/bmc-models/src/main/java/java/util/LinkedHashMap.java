package java.util;

/** BMC model of {@link java.util.LinkedHashMap} — same array-backed behaviour as {@link HashMap}
 *  (insertion order is preserved by the backing arrays). */
public class LinkedHashMap<K, V> extends HashMap<K, V> {

    public LinkedHashMap() {
        super();
    }

    public LinkedHashMap(int initialCapacity) {
        super(initialCapacity);
    }

    public LinkedHashMap(Map<? extends K, ? extends V> m) {
        super(m);
    }
}
