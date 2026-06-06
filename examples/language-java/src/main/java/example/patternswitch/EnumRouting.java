package example.patternswitch;

/**
 * A pattern-matching switch over a plain enum. The {@code case null} arm makes javac emit the
 * {@code SwitchBootstraps.enumSwitch} invokedynamic (a classic enum switch without it compiles to
 * the indy-free {@code $SwitchMap} form, which analyzes soundly). bmc4j deliberately does NOT
 * desugar {@code enumSwitch} — instead the residual-indy pass surfaces the site through the
 * nondet-stub policy, so the trust is visible (footnote / {@code strictStubs}) rather than silent.
 */
public final class EnumRouting {

    public enum Status { OK, RETRY, FAIL }

    private EnumRouting() {
    }

    /** Route a status to a code; {@code null} routes to -1. Compiles to an enumSwitch indy. */
    public static int code(Status s) {
        return switch (s) {
            case null -> -1;
            case OK -> 0;
            case RETRY -> 1;
            case FAIL -> 2;
        };
    }
}
