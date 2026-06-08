package java.util;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcModelTail;

/**
 * BMC model of {@link java.util.Collections} — only the {@code enumeration(Collection)} producer is
 * modeled, the bridge that turns a modeled collection into a bounded {@link Enumeration}. The vast
 * remainder (sort/binarySearch, the unmodifiable/synchronized/singleton wrappers, and the many
 * shuffle/rotate/fill utilities) is the exotic tail, build-synthesized loud — never a silent stub.
 *
 * <p>{@code enumeration} snapshots the collection's elements into a fixed-capacity array (bounded by
 * the source's size — keep it within the proof's {@code unwind}) and walks them by index. This is the
 * concrete-backing iteration pattern: no virtual dispatch through the Enumeration interface, the
 * cursor advances over a real array.
 */
@BmcModelTail(reason = "exotic remainder: sort/binarySearch/unmodifiable*/synchronized*/singleton*/shuffle/etc. static utilities — out of scope for the bounded model; all loud under JBMC")
public final class Collections {

    private Collections() {
    }

    /**
     * A bounded {@link Enumeration} over {@code c}'s elements, like {@code Collections.enumeration(c)}.
     * Iterates by index over a snapshot of the source — concrete backing, no interface dispatch.
     */
    @BmcModelConforms("differential (EnumerationConformanceTest) + @BmcProof (proofs.enumeration)")
    public static <T> Enumeration<T> enumeration(Collection<T> c) {
        return new ArrayEnumeration<>(c);
    }

    /**
     * Concrete, fixed-capacity {@link Enumeration} backed by an element array filled from the source
     * collection's iterator at construction time. {@code nextElement} advances a cursor over that array
     * and throws {@link NoSuchElementException} past the end (JDK semantics).
     */
    private static final class ArrayEnumeration<T> implements Enumeration<T> {

        private static final int CAPACITY = 64;

        private final Object[] elements = new Object[CAPACITY];
        private final int count;
        private int cursor;

        ArrayEnumeration(Collection<T> c) {
            int n = 0;
            for (T e : c) {
                elements[n++] = e;
            }
            this.count = n;
        }

        @Override
        public boolean hasMoreElements() {
            return cursor < count;
        }

        @Override
        @SuppressWarnings("unchecked")
        public T nextElement() {
            if (cursor >= count) {
                throw new NoSuchElementException();
            }
            return (T) elements[cursor++];
        }
    }
}
