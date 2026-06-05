package example.patternswitch;

/**
 * A sealed hierarchy whose subtypes are dispatched with a pattern-matching {@code switch}. The
 * switch over a {@code Shape} compiles to a {@code SwitchBootstraps.typeSwitch} invokedynamic, which
 * bmc4j desugars (in its bytecode layer) into a sound {@code instanceof} chain — so JBMC can prove
 * the selected arm matches the subject's real type even when the subject is symbolic.
 *
 * <p>Components are {@code int} on purpose: the property under test is type dispatch, not floating
 * point, and integer arithmetic keeps the BMC cheap.
 */
public sealed interface Shape permits Circle, Square, Rectangle {

    /**
     * A "size" measure via a pattern {@code switch}. With the typeSwitch desugar this is a sound,
     * total function of the subject's runtime type; without it JBMC would link the dispatch to a
     * nondet branch decoupled from the subject's type.
     */
    static int size(Shape s) {
        return switch (s) {
            case Circle c -> c.radius();
            case Square sq -> sq.side() * sq.side();
            case Rectangle r -> r.width() * r.height();
        };
    }

    /** A small integer tag per concrete type — used by proofs to pin which arm was taken. */
    static int tag(Shape s) {
        return switch (s) {
            case Circle c -> 1;
            case Square sq -> 2;
            case Rectangle r -> 3;
        };
    }
}
