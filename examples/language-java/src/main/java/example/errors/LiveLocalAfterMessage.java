package example.errors;

/**
 * The <em>live-local</em> counterpart to {@link SlowMessage}: a method that guards its argument with a
 * {@code throw new IllegalArgumentException(<dynamic message>)} AND, on the normal path, carries a value
 * through a LOCAL that it returns and the caller goes on to use. The dynamic message makes
 * exception-message elision rewrite this method; the returned local is genuinely LIVE.
 *
 * <p>This is the exact shape of {@code Bmc.anyString(min, max)} (a {@code throw new IAE("...got " + n)}
 * argument check, then {@code String s = ...; return s;}). It pins the soundness boundary that eliding a
 * message must leave the rest of the method - its live locals and their type/non-null tracking - untouched.
 * The value carried is a SYMBOLIC string (the caller passes {@code Bmc.anyAsciiString(...)}): its non-null
 * guarantee comes from the engine's nondet modeling, which reads the method's LocalVariableTable. A rewrite
 * that drops a modified method's LocalVariableTable wholesale makes the engine lose that non-null tracking,
 * so the caller reads the returned string back {@code null} and a correct proof FALSE-REFUTES with a
 * NullPointerException.
 */
public final class LiveLocalAfterMessage {

    private LiveLocalAfterMessage() {
    }

    /**
     * Rejects a {@code null} {@code s} with a dynamic message (the elided construction), otherwise carries
     * {@code s} through a local and returns it. The returned value is LIVE: the caller reads its
     * {@code length()}. Eliding the message must not disturb it.
     */
    public static String checkedLabel(String s, int tag) {
        if (s == null) {
            throw new IllegalArgumentException("label must be non-null, tag=" + tag);
        }
        String label = s;
        return label;
    }
}
