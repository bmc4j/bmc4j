package example.errors;

/**
 * A function whose thrown exception's MESSAGE is part of the contract a caller checks — the case where
 * eliding the message would be UNSOUND. {@link #requirePositive} throws
 * {@code IllegalArgumentException("not positive")} for a non-positive input; a caller that catches it
 * and reads {@code getMessage()} observes the message.
 *
 * <p>Because a proof over this code has a {@code getMessage()} observer in its reachable cone, bmc4j's
 * AUTO elision gate must NOT elide here — otherwise the message would become {@code null} and a proof
 * asserting its content would be silently wrong. This is the soundness litmus: the observer's presence
 * suppresses elision, so the verdict is unchanged.
 */
public final class Validator {

    private Validator() {
    }

    /** Returns {@code n} when positive; otherwise throws with the exact message {@code "not positive"}. */
    public static int requirePositive(int n) {
        if (n <= 0) {
            throw new IllegalArgumentException("not positive");
        }
        return n;
    }
}
