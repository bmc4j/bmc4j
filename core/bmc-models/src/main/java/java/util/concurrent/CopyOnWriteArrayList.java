package java.util.concurrent;

import java.util.ArrayList;

/** Sequential BMC model of {@link java.util.concurrent.CopyOnWriteArrayList} — functionally the
 *  bmc4j bounded ArrayList model. */
public class CopyOnWriteArrayList<E> extends ArrayList<E> {

    public CopyOnWriteArrayList() {
        super();
    }
}
