package kotlin.enums;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

/**
 * Clean model of the stdlib-internal {@code kotlin.enums.EnumEntriesList} — the immutable {@code List}
 * an enum's {@code entries} property returns. The real class is an {@code AbstractList} wrapper over
 * the enum's {@code values()} array whose member calls reach kotlin-stdlib internals JBMC stubs to
 * nondet, so {@code entries.size}/{@code entries[i]}/iteration spuriously refute.
 *
 * <p>This model wraps bmc4j's bounded {@link ArrayList} model by COMPOSITION (not inheritance): the
 * {@code List} members are concrete bodies on THIS class that delegate to the backing list. Subclassing
 * the model left the inherited {@code List} members unresolved on the concrete type — JBMC dispatched
 * {@code entries.size()} to the bodyless {@code java.util.List.size()} interface method ("no body for
 * callee java.util.List.size()") instead of the model — so the members proofs reach are forwarded
 * explicitly here. The backing list is filled from {@code values()} in declaration order at
 * construction, so the ordinal/index correspondence the real {@code EnumEntries} guarantees
 * ({@code entries[i].ordinal == i}, {@code entries[ordinal] === value}) holds by construction.
 *
 * @param <T> the enum type
 */
public final class EnumEntriesList<T extends Enum<T>> implements EnumEntries<T> {

    private final ArrayList<T> backing;

    public EnumEntriesList(T[] entries) {
        backing = new ArrayList<>();
        for (T e : entries) {
            backing.add(e);
        }
    }

    @Override
    public int size() {
        return backing.size();
    }

    @Override
    public boolean isEmpty() {
        return backing.isEmpty();
    }

    @Override
    public T get(int index) {
        return backing.get(index);
    }

    @Override
    public boolean contains(Object o) {
        return backing.contains(o);
    }

    @Override
    public int indexOf(Object o) {
        return backing.indexOf(o);
    }

    @Override
    public int lastIndexOf(Object o) {
        // EnumEntries holds distinct constants, so last == first occurrence.
        return backing.indexOf(o);
    }

    @Override
    public Iterator<T> iterator() {
        return backing.iterator();
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        for (Object o : c) {
            if (!backing.contains(o)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public Object[] toArray() {
        Object[] out = new Object[backing.size()];
        for (int i = 0; i < backing.size(); i++) {
            out[i] = backing.get(i);
        }
        return out;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <E> E[] toArray(E[] a) {
        for (int i = 0; i < backing.size(); i++) {
            a[i] = (E) backing.get(i);
        }
        return a;
    }

    // ---- immutable: mutators throw, matching the real EnumEntries (an immutable List) ----

    @Override
    public boolean add(T t) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean remove(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean addAll(Collection<? extends T> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean addAll(int index, Collection<? extends T> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }

    @Override
    public T set(int index, T element) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void add(int index, T element) {
        throw new UnsupportedOperationException();
    }

    @Override
    public T remove(int index) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ListIterator<T> listIterator() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ListIterator<T> listIterator(int index) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<T> subList(int fromIndex, int toIndex) {
        throw new UnsupportedOperationException();
    }
}
