package java.util.concurrent;

import java.util.ArrayList;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcModelTail;

/** Sequential BMC model of {@link java.util.concurrent.CopyOnWriteArrayList} — functionally the
 *  bmc4j bounded ArrayList model. */
@BmcModelConforms("inherits the ArrayList model surface; differential (sequential semantics)")
@BmcModelTail(reason = "copy-on-write/atomic extras (addIfAbsent/addAllAbsent/getArray/…), Deque surface, listIterator/subList/spliterator — out of scope for this sequential array-backed model; all loud under JBMC")
public class CopyOnWriteArrayList<E> extends ArrayList<E> {

    public CopyOnWriteArrayList() {
        super();
    }
}
