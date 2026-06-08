package kotlin.text;

import org.bmc4j.models.audit.BmcModelConforms;

/**
 * Concrete {@link kotlin.collections.CharIterator} backing for {@code CharSequence.iterator()}. The real
 * {@code iterator()} returns an anonymous {@code CharIterator} whose {@code nextChar()} reads the next
 * char of the receiver; modeling it requires a CONCRETE subclass so JBMC has a real {@code nextChar()}
 * body to analyze rather than a virtual abstract method it would nondet-stub. This walks a concrete
 * {@code String} BY INDEX (the #169-robust pattern — never a havoc'd virtual CharIterator).
 */
public final class StringCharIterator extends kotlin.collections.CharIterator {

    private final String s;
    private int index;

    public StringCharIterator(String s) {
        this.s = s;
        this.index = 0;
    }

    @Override
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public boolean hasNext() {
        return index < s.length();
    }

    @Override
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public char nextChar() {
        return s.charAt(index++);
    }
}
