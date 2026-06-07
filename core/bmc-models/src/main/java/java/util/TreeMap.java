package java.util;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcModelTail;

/** BMC model of {@link java.util.TreeMap} — array-backed; functional results (get/put/containsKey)
 *  match, though the model does not impose key ordering. */
@BmcModelConforms("inherits the HashMap model surface; functional results match (no key ordering imposed)")
@BmcModelTail(reason = "NavigableMap/SortedMap ordering surface (firstKey/lastKey/ceilingEntry/floorKey/headMap/tailMap/subMap/descendingMap/pollFirstEntry/…) — this array-backed model imposes no ordering, so the ordered navigation API is out of scope; all loud under JBMC")
public class TreeMap<K, V> extends HashMap<K, V> {

    public TreeMap() {
        super();
    }

    public TreeMap(Map<? extends K, ? extends V> m) {
        super(m);
    }
}
