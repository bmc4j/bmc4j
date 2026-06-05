package example.patternswitch;

/** Pattern switches over a general {@code Object} subject — type labels, a constant label, guards,
 *  null and default — each compiling to a {@code SwitchBootstraps.typeSwitch} invokedynamic. */
public final class Classifier {

    private Classifier() {
    }

    /** Type-pattern dispatch with a default. Returns a tag identifying the matched arm. */
    public static int kind(Object o) {
        return switch (o) {
            case Integer i -> 1;
            case String s -> 2;
            default -> 0;
        };
    }

    /** A guarded switch: the {@code when} clause forces a {@code restartIndex} re-entry when it
     *  fails, so the same subject resumes matching at the next case. Exercises the desugar's
     *  honouring of {@code restartIndex}. */
    public static int sign(Object o) {
        return switch (o) {
            case Integer i when i > 0 -> 1;   // positive
            case Integer i when i < 0 -> -1;  // negative
            case Integer i -> 0;              // zero (guards fell through via restartIndex)
            default -> 2;                      // not an Integer at all
        };
    }

    /** Includes an explicit {@code case null} so the null arm is reachable (no implicit
     *  {@code requireNonNull}); returns the typeSwitch null sentinel's downstream value. */
    public static int withNull(Object o) {
        return switch (o) {
            case null -> -100;
            case String s -> 7;
            default -> 0;
        };
    }
}
