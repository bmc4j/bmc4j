package java.util;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcModelTail;

/** BMC model of {@link java.util.LinkedHashSet} — same array-backed behaviour as {@link HashSet}. */
@BmcModelTail(reason = "SequencedSet surface (addFirst/getLast/reversed/…) and spliterator — out of scope for this array-backed model; all loud under JBMC")
public class LinkedHashSet<E> extends HashSet<E> {

    public LinkedHashSet() {
        super();
    }

    public LinkedHashSet(Collection<? extends E> c) {
        super(c);
    }
}
