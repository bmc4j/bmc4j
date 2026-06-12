package example.errors;

import java.nio.charset.StandardCharsets;

/**
 * A function that builds an <em>expensive dynamic error message</em> on a reachable-but-unobserved
 * throw branch — the shape bmc4j's exception-message elision exists for, and the in-repo analogue of
 * {@code okio.Buffer.readDecimalLong}'s overflow path (which materializes a byte[]&rarr;String inside
 * the message of the exception it throws).
 *
 * <p>{@link #doubleOrThrow} doubles small inputs and throws for large ones; the throw's message is built
 * by decoding a byte array to a {@code String} ({@link #overflowMessage}) — a byte&rarr;String
 * materialization that bounded model checking finds expensive. A proof that exercises the throw range
 * but never reads the message has no message observer in its cone, so bmc4j's AUTO elision drops the
 * message construction and the proof stays tractable; without elision the byte-decode poisons it
 * (UNKNOWN).
 */
public final class Parser {

    private Parser() {
    }

    /** Doubles {@code n} for {@code n <= 1_000_000}; otherwise throws with an expensively-built
     *  (byte&rarr;String) message. The throw branch is genuinely reachable for large inputs, so it is not
     *  statically pruned — its message construction is encoded by BMC unless elided. */
    public static int doubleOrThrow(int n) {
        if (n > 1_000_000) {
            throw new IllegalArgumentException(overflowMessage(n));
        }
        return n * 2;
    }

    /** An intentionally expensive message build: materialize a byte array whose LENGTH depends on the
     *  (symbolic) input, fill it, and decode it to a String (a byte&rarr;String materialization, exactly
     *  okio's overflow-message trigger). Because the array length and the fill loop's trip count are
     *  input-dependent, bounded model checking must unwind them across the whole input range — far past
     *  the default unwind cap — so the proof is UNKNOWN (under-unwound) unless the message construction
     *  is elided. */
    private static String overflowMessage(int n) {
        int len = (n % 4096) + 1;
        byte[] digits = new byte[len];
        for (int i = 0; i < len; i++) {
            digits[i] = (byte) ('0' + (i % 10));
        }
        String decoded = new String(digits, StandardCharsets.UTF_8);
        return "Number too large: " + decoded;
    }
}
