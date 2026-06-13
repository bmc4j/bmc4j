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
     * The FAITHFUL okio {@code Buffer.readDecimalLong} overflow shape: a fresh object in a local with a
     * <b>discarded side-effect-only self-call OUTSIDE the message</b> as well as a read inside it. okio:
     * <pre>{@code
     * val buffer = Buffer().writeDecimalLong(value).writeByte(b)   // fresh object, a local
     * if (!negative) buffer.readByte()                             // discarded result, side-effect-only
     * throw NumberFormatException("Number too large: ${buffer.readUtf8()}")
     * }</pre>
     * The out-of-message {@code probe()} (the {@code readByte()} analogue) reads the local but discards its
     * result — a side-effect-only self-call. Under the prior in-region-reads-only rule that out-of-region
     * read kept the {@code materializer} live, so the expensive {@code grow} chain stayed in the cone and
     * the proof timed out. The object is in fact FULLY dead (fresh, escapes nowhere, the probe result
     * discarded), so dead-allocation elimination drops its whole lifetime — chain AND probe — and the
     * proof verifies.
     */
    public static int renderOrThrowWithProbe(int n) {
        if (n > 1_000_000) {
            Materializer materializer = new Materializer().grow(n).grow(n);
            // A DISCARDED side-effect-only self-call OUTSIDE the message (the okio readByte() analogue):
            // its result is thrown away, so the only thing keeping `materializer` live is the message.
            materializer.probe();
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

        /** A side-effect-only self-call returning a value typically DISCARDED at the call site — the
         *  {@code readByte()} analogue. Mutates the object and returns its length; the caller throws the
         *  result away, so once the object is dead this call is dead too. */
        int probe() {
            int len = sb.length();
            if (len > 0) {
                sb.setLength(len - 1);
            }
            return len;
        }

        String render() {
            return sb.toString();
        }
    }
}
