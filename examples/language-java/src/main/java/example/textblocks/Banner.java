package example.textblocks;

/**
 * Text blocks (Java 15+). A {@code """ ... """} text block is just a compile-time {@code String}
 * constant — no {@code invokedynamic}, no special runtime machinery — so JBMC analyses it like any
 * other String literal. These methods expose a text block's content for proofs to assert against
 * using the sound, JBMC-modeled String operations ({@code length}/{@code charAt}/{@code equals}/
 * {@code startsWith}/{@code contains}).
 */
public final class Banner {

    private Banner() {
    }

    /** A multi-line text block. Compiles to the constant {@code "line1\nline2\nline3"}. */
    public static String text() {
        return """
                line1
                line2
                line3""";
    }

    /** A single-line text block — content is the bare word, no surrounding quotes or newline. */
    public static String word() {
        return """
                prod""";
    }

    /**
     * A text block whose trailing space is preserved with the {@code \s} escape (without it, text
     * blocks strip trailing whitespace per line), then concatenated with symbolic input. The prefix
     * {@code "hi "} is therefore exactly 3 chars.
     */
    public static String greeting(String name) {
        return """
                hi\s""" + name;
    }
}
