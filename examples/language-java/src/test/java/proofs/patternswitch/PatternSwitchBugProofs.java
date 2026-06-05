package proofs.patternswitch;

import example.patternswitch.Classifier;
import example.patternswitch.Shape;
import example.patternswitch.Square;
import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;
import org.bmc4j.Verdict;

/**
 * Fail-on-purpose demos: false claims about a pattern {@code switch} that BMC must REFUTE. These
 * prove the desugar produces a real, type-tied dispatch (a nondet indy result could spuriously
 * satisfy them). Each declares {@code expect = REFUTED}, so the suite PASSES while the desugar is
 * sound — and goes loudly red if a regression ever lets a false claim come back VERIFIED.
 */
class PatternSwitchBugProofs {

    /** FALSE: a Square's size is its side squared, not its side. BMC finds a counterexample. */
    @BmcProof(expect = Verdict.REFUTED)
    void square_size_is_not_its_side() {
        int side = Bmc.anyInt(2, 10_000); // side != side*side for side > 1
        Shape s = new Square(side);
        Bmc.check(Shape.size(s) == side); // refuted: size == side*side
    }

    /** FALSE: an Integer subject is classified as 1, not 2 (2 is the String arm). The dispatch is
     *  genuinely tied to the type, so this is refutable. */
    @BmcProof(expect = Verdict.REFUTED)
    void integer_is_not_classified_as_string() {
        Object o = Integer.valueOf(Bmc.anyInt());
        Bmc.check(Classifier.kind(o) == 2); // refuted: an Integer takes arm 1
    }
}
