package proofs.abstractcollections;

import java.util.AbstractCollection;
import java.util.AbstractList;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;

/**
 * Regression proofs for the devirtualization soundness fix: a user collection that {@code extends}
 * one of the {@code java.util.Abstract*} skeletal bases, overrides only the abstract primitives, is
 * held through the JDK collection INTERFACE, and is proven through that interface.
 *
 * <p>Before the {@code AbstractCollection}/{@code AbstractList}/{@code AbstractSet}/{@code AbstractMap}
 * models existed, the inherited interface methods ({@code size}/{@code get}/{@code isEmpty}/…) had no
 * resolvable body for an UNMODELLED user subclass, so JBMC nondet-stubbed them and these TRUE
 * properties came back FALSELY REFUTED. With the abstract bases modeled (their derived ops written over
 * the user's overridden primitives), the user subclass devirtualizes and each property VERIFIES.
 *
 * <p>Each case is a single-element collection so {@code size==1}, the lone element is known, and the
 * collection is non-empty — all read through the interface-typed reference, never the concrete type.
 */
class AbstractCollectionSubclassLaws {

    // ---- AbstractList: user overrides get(int) + size(); held as java.util.List ---------------------

    static final class SingletonList extends AbstractList<Integer> {
        private final int v;
        SingletonList(int v) { this.v = v; }
        @Override public Integer get(int index) {
            if (index != 0) { throw new IndexOutOfBoundsException(); }
            return v;
        }
        @Override public int size() { return 1; }
    }

    @BmcProof
    void abstractList_subclass_size_via_interface() {
        int x = Bmc.anyInt();
        List<Integer> l = new SingletonList(x);
        Bmc.check(l.size() == 1);
    }

    @BmcProof
    void abstractList_subclass_get_via_interface() {
        int x = Bmc.anyInt();
        List<Integer> l = new SingletonList(x);
        Bmc.check(l.get(0) == x);
    }

    @BmcProof(unwind = 1)
    void abstractList_subclass_not_empty_via_interface() {
        int x = Bmc.anyInt();
        List<Integer> l = new SingletonList(x);
        Bmc.check(!l.isEmpty());
    }

    @BmcProof
    void abstractList_subclass_contains_indexOf_via_interface() {
        int x = Bmc.anyInt();
        List<Integer> l = new SingletonList(x);
        Bmc.check(l.contains(x) && l.indexOf(x) == 0);
    }

    // ---- AbstractCollection: user overrides iterator() + size(); held as java.util.Collection -------

    static final class SingletonCollection extends AbstractCollection<Integer> {
        private final int v;
        SingletonCollection(int v) { this.v = v; }
        @Override public int size() { return 1; }
        @Override public Iterator<Integer> iterator() {
            return new Iterator<Integer>() {
                private boolean done;
                @Override public boolean hasNext() { return !done; }
                @Override public Integer next() {
                    if (done) { throw new NoSuchElementException(); }
                    done = true;
                    return v;
                }
            };
        }
    }

    @BmcProof
    void abstractCollection_subclass_size_via_interface() {
        int x = Bmc.anyInt();
        Collection<Integer> c = new SingletonCollection(x);
        Bmc.check(c.size() == 1 && !c.isEmpty());
    }

    @BmcProof
    void abstractCollection_subclass_contains_via_interface() {
        int x = Bmc.anyInt();
        Collection<Integer> c = new SingletonCollection(x);
        Bmc.check(c.contains(x));
    }

    // ---- AbstractSet: user overrides iterator() + size(); held as java.util.Set ---------------------

    static final class SingletonSet extends AbstractSet<Integer> {
        private final int v;
        SingletonSet(int v) { this.v = v; }
        @Override public int size() { return 1; }
        @Override public Iterator<Integer> iterator() {
            return new Iterator<Integer>() {
                private boolean done;
                @Override public boolean hasNext() { return !done; }
                @Override public Integer next() {
                    if (done) { throw new NoSuchElementException(); }
                    done = true;
                    return v;
                }
            };
        }
    }

    @BmcProof
    void abstractSet_subclass_size_via_interface() {
        int x = Bmc.anyInt();
        Set<Integer> s = new SingletonSet(x);
        Bmc.check(s.size() == 1 && !s.isEmpty());
    }

    @BmcProof
    void abstractSet_subclass_contains_via_interface() {
        int x = Bmc.anyInt();
        Set<Integer> s = new SingletonSet(x);
        Bmc.check(s.contains(x));
    }

    // ---- AbstractMap: user overrides entrySet(); held as java.util.Map ------------------------------

    static final class Pair implements Map.Entry<Integer, Integer> {
        private final int k;
        private final int val;
        Pair(int k, int val) { this.k = k; this.val = val; }
        @Override public Integer getKey() { return k; }
        @Override public Integer getValue() { return val; }
        @Override public Integer setValue(Integer value) { throw new UnsupportedOperationException(); }
    }

    static final class SingletonMap extends AbstractMap<Integer, Integer> {
        private final int k;
        private final int val;
        SingletonMap(int k, int val) { this.k = k; this.val = val; }
        @Override public Set<Map.Entry<Integer, Integer>> entrySet() {
            HashSet<Map.Entry<Integer, Integer>> es = new HashSet<>();
            es.add(new Pair(k, val));
            return es;
        }
    }

    @BmcProof
    void abstractMap_subclass_size_via_interface() {
        int k = Bmc.anyInt();
        int v = Bmc.anyInt();
        Map<Integer, Integer> m = new SingletonMap(k, v);
        Bmc.check(m.size() == 1 && !m.isEmpty());
    }

    @BmcProof(unwind = 1)
    void abstractMap_subclass_get_via_interface() {
        int k = Bmc.anyInt();
        int v = Bmc.anyInt();
        Map<Integer, Integer> m = new SingletonMap(k, v);
        Bmc.check(m.get(k) == v && m.containsKey(k));
    }
}
