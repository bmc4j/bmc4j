package java.lang;

/**
 * Message-free BMC model of {@link java.lang.StringIndexOutOfBoundsException}.
 *
 * <p>The real JDK {@code (int)} constructor builds {@code super("String index out of range: " + index)}.
 * The char-array {@code String} model's bounds checks throw {@code new StringIndexOutOfBoundsException(index)}
 * with a SYMBOLIC {@code index} (see {@link String#charAt(int)}), so that concat builds a
 * {@code new String(char[], 0, count)} with a symbolic {@code count} whose copy loop unwinds without
 * bound — a blowup on the (usually infeasible) out-of-bounds branch. This model drops the detail message
 * from the index constructors so nothing symbolic is built, while preserving the TYPE and control flow
 * exactly. See {@link IndexOutOfBoundsException} for the full rationale and the message-handling contract.
 */
public class StringIndexOutOfBoundsException extends IndexOutOfBoundsException {

    public StringIndexOutOfBoundsException() {
        super();
    }

    /** Message-free: NO {@code "String index out of range: " + index} concat (the symbolic-message blowup). */
    public StringIndexOutOfBoundsException(int index) {
        super();
    }

    /** Pass-through: the (already concrete) message reference is merely stored, never built. */
    public StringIndexOutOfBoundsException(String s) {
        super(s);
    }
}
