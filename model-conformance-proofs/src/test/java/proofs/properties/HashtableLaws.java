package proofs.properties;

import java.util.Enumeration;
import java.util.Hashtable;

import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;

/**
 * Model proofs (axis 2) for the {@link java.util.Hashtable} model — the legacy bounded map the
 * {@link java.util.Properties} model builds on. The core property is SHARED BACKING STATE: a value
 * {@code put} into the table is the same value a later {@code get} returns (so an unmodeled Hashtable's
 * put/get havoc is fixed). JBMC verifies these algebraic laws over symbolic inputs in tight ranges,
 * under its own semantics, exactly as a real proof relies on.
 */
class HashtableLaws {

    /** put(k, v) then get(k) returns the very value stored — the shared-state core fix. */
    @BmcProof(unwind = 4)
    void put_then_get_returns_stored_value() {
        int k = Bmc.anyInt(0, 100);
        int v = Bmc.anyInt(-100, 100);
        Hashtable<Integer, Integer> t = new Hashtable<>();
        t.put(k, v);
        Bmc.check(t.get(k) == v);                 // get sees the put (not a nondet)
        Bmc.check(t.containsKey(k));
        Bmc.check(t.contains(v) && t.containsValue(v));
    }

    /** A second put to the same key overwrites and returns the prior value; size is unchanged. */
    @BmcProof(unwind = 4)
    void put_overwrites_same_key() {
        int k = Bmc.anyInt(0, 100);
        int v1 = Bmc.anyInt(-100, 100);
        int v2 = Bmc.anyInt(-100, 100);
        Hashtable<Integer, Integer> t = new Hashtable<>();
        t.put(k, v1);
        Object old = t.put(k, v2);
        Bmc.check(old != null && (Integer) old == v1);  // prior value returned
        Bmc.check(t.get(k) == v2);                       // new value stored
        Bmc.check(t.size() == 1);                        // overwrite, not a second entry
    }

    /** get on an absent key is null; getOrDefault returns the default; size/isEmpty stay consistent. */
    @BmcProof(unwind = 4)
    void absent_key_is_null_and_default() {
        int present = Bmc.anyInt(0, 50);
        int absent = Bmc.anyInt(51, 100);          // disjoint from present
        int v = Bmc.anyInt(-100, 100);
        Hashtable<Integer, Integer> t = new Hashtable<>();
        Bmc.check(t.isEmpty() && t.size() == 0);
        t.put(present, v);
        Bmc.check(!t.isEmpty() && t.size() == 1);
        Bmc.check(t.get(absent) == null);
        Bmc.check(t.getOrDefault(absent, -7) == -7);
        Bmc.check(t.getOrDefault(present, -7) == v);
        Bmc.check(!t.containsKey(absent));
    }

    /** remove deletes the mapping and returns the prior value; the table shrinks and forgets the key. */
    @BmcProof(unwind = 4)
    void remove_deletes_mapping() {
        int k = Bmc.anyInt(0, 100);
        int v = Bmc.anyInt(-100, 100);
        Hashtable<Integer, Integer> t = new Hashtable<>();
        t.put(k, v);
        Object old = t.remove(k);
        Bmc.check(old != null && (Integer) old == v);
        Bmc.check(t.get(k) == null);
        Bmc.check(!t.containsKey(k));
        Bmc.check(t.isEmpty());
    }

    /** keys()/elements() enumerate exactly the stored entries (concrete bounded enumeration). */
    @BmcProof(unwind = 4)
    void keys_and_elements_enumerate_entries() {
        int v = Bmc.anyInt(-100, 100);
        Hashtable<Integer, Integer> t = new Hashtable<>();
        t.put(7, v);
        Enumeration<Integer> ks = t.keys();
        Bmc.check(ks.hasMoreElements());
        Bmc.check(ks.nextElement() == 7);
        Bmc.check(!ks.hasMoreElements());
        Enumeration<Integer> es = t.elements();
        Bmc.check(es.hasMoreElements());
        Bmc.check(es.nextElement() == v);
        Bmc.check(!es.hasMoreElements());
    }
}
