package java.lang;

/**
 * Message-free BMC model of {@link java.lang.ArrayIndexOutOfBoundsException}.
 *
 * <p>The real JDK {@code (int)} constructor builds {@code super("Array index out of range: " + index)}.
 * As with {@link StringIndexOutOfBoundsException}, a symbolic {@code index} on an out-of-bounds branch
 * turns that concat into an unbounded {@code new String(char[], 0, count)} blowup. This model drops the
 * detail message from the index constructor so nothing symbolic is built, while preserving the TYPE and
 * control flow exactly. See {@link IndexOutOfBoundsException} for the full rationale and the
 * message-handling contract.
 */
public class ArrayIndexOutOfBoundsException extends IndexOutOfBoundsException {

    public ArrayIndexOutOfBoundsException() {
        super();
    }

    /** Message-free: NO {@code "Array index out of range: " + index} concat (the symbolic-message blowup). */
    public ArrayIndexOutOfBoundsException(int index) {
        super();
    }

    /** Pass-through: the (already concrete) message reference is merely stored, never built. */
    public ArrayIndexOutOfBoundsException(String s) {
        super(s);
    }
}
