package example.errors;

/**
 * The <em>dead-local</em> analogue of {@link Parser}: the expensive message material is built in a
 * PRIOR statement, stored to a local, and only ever read by the thrown exception's message — the exact
 * shape of okio {@code Buffer.readDecimalLong}'s overflow path
 * ({@code val buffer = Buffer().writeDecimalLong(v).writeByte(b); throw NFE("…${buffer.readUtf8()}")}).
 *
 * <p>{@link #renderOrThrow} doubles small inputs; for large ones it builds a {@link Materializer} via a
 * fresh-object builder chain ({@code new Materializer().grow(n).grow(n)} — each {@code grow} runs an
 * input-bounded append loop the engine must unwind), stores it in a LOCAL, then throws an
 * {@link IllegalArgumentException} whose message reads {@code materializer.render()}. The builder chain
 * is a separate statement, so it lands in a local; the only reader of that local is the (elided) message.
 *
 * <p>This is what the backward dead-code slice in bmc4j's exception-message elision exists for: eliding
 * the message removes {@code render()}, leaving the {@code materializer} local dead — but its
 * construction (the expensive {@code grow} loops) lives one statement earlier, so the message-only
 * elision of {@link Parser} would still encode it. The slice drops the dead fresh-object chain too, so
 * the engine never unwinds {@code grow}, and the proof stays tractable.
 */
public final class SlowMessage {

    private SlowMessage() {
    }

    /** Doubles {@code n} for {@code n <= 1_000_000}; otherwise throws with a message built from a
     *  fresh-object builder chain stored in a local — the dead-local slice's motivating shape. */
    public static int renderOrThrow(int n) {
        if (n > 1_000_000) {
            // A SEPARATE statement: the expensive builder chain lands in a local (not inline in the
            // throw), so eliding only the message expression would leave this construction behind.
            Materializer materializer = new Materializer().grow(n).grow(n);
            throw new IllegalArgumentException("overflow: " + materializer.render());
        }
        return n * 2;
    }

    /**
     * A fresh object whose builder methods run input-bounded append loops — the per-call work the engine
     * unwinds. Allocated fresh and escaping only into the (elided) exception message, so once the message
     * is gone the whole object is dead and its construction is safe to slice away.
     */
    static final class Materializer {
        private final StringBuilder sb = new StringBuilder();

        Materializer grow(int n) {
            int len = (n % 4096) + 1;
            for (int i = 0; i < len; i++) {
                sb.append((char) ('0' + (i % 10)));
            }
            return this;
        }

        String render() {
            return sb.toString();
        }
    }
}
