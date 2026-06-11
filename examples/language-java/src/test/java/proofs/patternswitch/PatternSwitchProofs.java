package proofs.patternswitch;

import example.patternswitch.Circle;
import example.patternswitch.Classifier;
import example.patternswitch.Rectangle;
import example.patternswitch.Shape;
import example.patternswitch.Square;
import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;

/**
 * Soundness of pattern-matching {@code switch} (the {@code SwitchBootstraps.typeSwitch}
 * invokedynamic) over a SYMBOLIC-typed subject — the case that was unsound before bmc4j's typeSwitch
 * desugar. JBMC links an indy result to an unconstrained value, so without the desugar the proofs
 * below (selected arm == actual runtime type, for ALL possible subjects) would NOT hold: the dispatch
 * would be decoupled from the subject's type. With the desugar the switch is an ordinary
 * {@code instanceof}/{@code equals} chain, and these become real theorems.
 */
class PatternSwitchProofs {

    /**
     * THE marquee law. The subject is chosen nondeterministically among the three concrete
     * {@link Shape} types, so JBMC explores every possibility. For each, the arm the pattern switch
     * selects ({@code Shape.tag}) must equal the subject's true runtime type. If typeSwitch were
     * still nondet, JBMC could pick a {@code tag} inconsistent with the {@code instanceof} below, and
     * one of these checks would be refutable.
     */
    @BmcProof
    void sealed_switch_selects_the_real_runtime_type() {
        Shape s = symbolicShape();
        int tag = Shape.tag(s);
        if (s instanceof Circle) {
            Bmc.check(tag == 1);
        } else if (s instanceof Square) {
            Bmc.check(tag == 2);
        } else if (s instanceof Rectangle) {
            Bmc.check(tag == 3);
        } else {
            Bmc.check(false); // a sealed Shape is always one of the three
        }
    }

    /** The switch is total over the sealed type: every symbolic Shape yields a defined tag in 1..3
     *  (never the no-match sentinel), i.e. no arm is skipped for some subtype. */
    @BmcProof
    void sealed_switch_is_total() {
        Shape s = symbolicShape();
        int tag = Shape.tag(s);
        Bmc.check(tag >= 1 && tag <= 3);
    }

    /** Size dispatches to the arm matching the runtime type: for a symbolic Square the switch must
     *  have taken the Square arm, so size == side*side. Ties the computed value to the real type. */
    @BmcProof(unwind = 1)
    void size_matches_dispatched_arm_for_symbolic_square() {
        int side = Bmc.anyInt(0, 10_000);
        Shape s = new Square(side);
        Bmc.check(Shape.size(s) == side * side);
    }

    /**
     * Object switch with a type label, a constant (String) label, and a default, over a symbolic
     * subject chosen among an Integer, a String, and a Double. The selected arm must match the real
     * type for every possibility — exactly the symbolic-typed-subject soundness that was missing.
     */
    @BmcProof
    void object_switch_selects_real_type() {
        Object o = symbolicObject();
        int k = Classifier.kind(o);
        if (o instanceof Integer) {
            Bmc.check(k == 1);
        } else if (o instanceof String) {
            Bmc.check(k == 2);
        } else {
            Bmc.check(k == 0); // the Double falls to default
        }
    }

    /** Guarded switch: the sign classifier is sound for a symbolic int. The {@code when} guards force
     *  {@code restartIndex} re-entry; the desugar honours it, so the result equals the true sign. */
    @BmcProof(unwind = 2)
    void guarded_switch_computes_true_sign() {
        int n = Bmc.anyInt(-1000, 1000);
        Object o = n;
        int sign = Classifier.sign(o);
        if (n > 0) {
            Bmc.check(sign == 1);
        } else if (n < 0) {
            Bmc.check(sign == -1);
        } else {
            Bmc.check(sign == 0);
        }
    }

    /** A non-Integer subject hits the guarded switch's default arm — the guards never spuriously
     *  match a different type. */
    @BmcProof
    void guarded_switch_default_for_non_integer() {
        Object o = "not an int";
        Bmc.check(Classifier.sign(o) == 2);
    }

    /** Explicit {@code case null} is reachable and selected for a null subject (the typeSwitch
     *  null path), not folded into the default. */
    @BmcProof
    void explicit_null_case_is_selected() {
        Object o = null;
        Bmc.check(Classifier.withNull(o) == -100);
    }

    // ---- nondeterministic symbolic subjects --------------------------------------------------

    /** A symbolic Shape: JBMC picks any of the three concrete subtypes. */
    private static Shape symbolicShape() {
        int which = Bmc.anyInt(0, 2);
        return switch (which) {
            case 0 -> new Circle(Bmc.anyInt());
            case 1 -> new Square(Bmc.anyInt());
            default -> new Rectangle(Bmc.anyInt(), Bmc.anyInt());
        };
    }

    /** A symbolic Object of one of three distinct runtime types. */
    private static Object symbolicObject() {
        int which = Bmc.anyInt(0, 2);
        return switch (which) {
            case 0 -> Integer.valueOf(Bmc.anyInt());
            case 1 -> "symbolic";
            default -> Double.valueOf(Bmc.anyDouble());
        };
    }
}
