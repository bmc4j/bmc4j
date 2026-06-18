package java.lang;

/**
 * Message-free BMC model of {@link java.lang.IndexOutOfBoundsException}.
 *
 * <p><b>Why this model exists.</b> The real JDK {@code (int)} constructor builds its detail message by
 * concatenating {@code "Index out of range: " + index}. When {@code index} is SYMBOLIC (the common case
 * on an out-of-bounds bounds-check branch — e.g. the char-array {@code String} model's
 * {@link String#charAt(int)} throwing {@code new StringIndexOutOfBoundsException(index)} with a symbolic
 * {@code index}), that concatenation builds a {@code new String(char[], 0, count)} with a symbolic
 * {@code count}, whose per-char copy loop unwinds without bound — a giant blowup on a branch that is
 * usually INFEASIBLE but explored before pruning. The {@code removeExceptionMessages} rewrite cannot
 * catch it: the message is built INSIDE this JDK constructor body, not at the {@code T.<init>(String)}
 * call site.
 *
 * <p>This model keeps the exception TYPE and control flow exactly (it still constructs and throws the
 * right type, so {@code instanceof} / {@code catch} / "is it thrown" are unchanged) and ONLY removes the
 * detail-message string: the {@code (int)} constructor stores NO message ({@code super()} with a null
 * message, no concat), so nothing symbolic is built. The {@code (String)} constructor passes its (already
 * concrete) message reference straight through to {@code RuntimeException} — which merely stores the
 * reference, building nothing — so an explicitly-messaged throw is unaffected. Reading the dropped message
 * back ({@code getMessage()} after the {@code (int)} ctor) returns {@code null}, which is sound: an
 * exception's detail message is not verdict-relevant in BMC.
 */
public class IndexOutOfBoundsException extends RuntimeException {

    public IndexOutOfBoundsException() {
        super();
    }

    /** Message-free: NO {@code "Index out of range: " + index} concat (the symbolic-message blowup). */
    public IndexOutOfBoundsException(int index) {
        super();
    }

    /** Message-free: NO {@code "Index out of range: " + index} concat (the symbolic-message blowup). */
    public IndexOutOfBoundsException(long index) {
        super();
    }

    /** Pass-through: the (already concrete) message reference is merely stored, never built. */
    public IndexOutOfBoundsException(String s) {
        super(s);
    }
}
