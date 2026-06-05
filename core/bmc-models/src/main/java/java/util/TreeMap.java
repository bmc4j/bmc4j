package java.util;

/** BMC model of {@link java.util.TreeMap} — array-backed; functional results (get/put/containsKey)
 *  match, though the model does not impose key ordering. */
public class TreeMap<K, V> extends HashMap<K, V> {

    public TreeMap() {
        super();
    }

    public TreeMap(Map<? extends K, ? extends V> m) {
        super(m);
    }
}
