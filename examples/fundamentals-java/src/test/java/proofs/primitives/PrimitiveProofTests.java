package proofs.primitives;

import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;
import org.bmc4j.Verdict;

/**
 * Smoke proofs for the symbolic-primitive helpers:
 * {@code anyShort}/{@code anyByte}/{@code anyChar}/{@code anyFloat}, and the bounded
 * {@code anyDouble(lo,hi)}/{@code anyFloat(lo,hi)}.
 *
 * <p>Each PASSING proof asserts a fact true for the whole symbolic domain (so JBMC must really range
 * over it); the FAILS-on-purpose siblings assert a knowingly-false bound, so JBMC refutes them — the
 * paired pattern proves the helper actually introduces an unconstrained / correctly-bounded input
 * rather than a fixed value. See {@code examples/README.md} on fail-on-purpose proofs.
 */
class PrimitiveProofTests {

    // ---- integral primitives: range bounds are the JLS type ranges ----------

    /** PASSES: a symbolic short is within the short range. */
    @BmcProof
    void short_is_within_its_type_range() {
        short s = Bmc.anyShort();
        Bmc.check(s >= Short.MIN_VALUE && s <= Short.MAX_VALUE);
    }

    /** FAILS: a symbolic short can be negative, so a "non-negative" claim is refuted. */
    // Expected verdict: REFUTED - anyShort() covers negative values - the false claim is refuted.
    @BmcProof(expect = Verdict.REFUTED)
    void short_is_not_always_non_negative() {
        short s = Bmc.anyShort();
        Bmc.check(s >= 0);
    }

    /** PASSES: a symbolic byte is within the byte range. */
    @BmcProof
    void byte_is_within_its_type_range() {
        byte b = Bmc.anyByte();
        Bmc.check(b >= Byte.MIN_VALUE && b <= Byte.MAX_VALUE);
    }

    /** PASSES: a symbolic char is within the unsigned 16-bit char range. */
    @BmcProof
    void char_is_within_its_type_range() {
        char c = Bmc.anyChar();
        Bmc.check(c >= Character.MIN_VALUE && c <= Character.MAX_VALUE);
    }

    /** FAILS: a symbolic char is not always the NUL character. */
    // Expected verdict: REFUTED - anyChar() covers non-NUL values - the false claim is refuted.
    @BmcProof(expect = Verdict.REFUTED)
    void char_is_not_always_nul() {
        char c = Bmc.anyChar();
        Bmc.check(c == '\0');
    }

    // ---- bounded floating point: the bound actually constrains, NaN excluded -

    /** PASSES: a bounded double stays inside [lo, hi] for every allowed input. */
    @BmcProof
    void bounded_double_stays_in_range() {
        double x = Bmc.anyDouble(-5.0, 5.0);
        Bmc.check(x >= -5.0 && x <= 5.0);
    }

    /** FAILS: the bounded double can exceed any sub-range — here, it is not always <= 1.0. */
    // Expected verdict: REFUTED - anyDouble(lo,hi) ranges over the whole interval.
    @BmcProof(expect = Verdict.REFUTED)
    void bounded_double_is_not_pinned_to_a_subrange() {
        double x = Bmc.anyDouble(-5.0, 5.0);
        Bmc.check(x <= 1.0);
    }

    /** PASSES: a bounded float stays inside [lo, hi] for every allowed input. */
    @BmcProof
    void bounded_float_stays_in_range() {
        float x = Bmc.anyFloat(0.0f, 100.0f);
        Bmc.check(x >= 0.0f && x <= 100.0f);
    }

    /**
     * PASSES: NaN semantics. A bounded float EXCLUDES NaN by construction, so {@code x == x} (which is
     * false only for NaN) holds for every allowed input — the proof would be refuted if NaN leaked in.
     */
    @BmcProof
    void bounded_float_excludes_nan() {
        float x = Bmc.anyFloat(0.0f, 1.0f);
        Bmc.check(x == x); // true for all reals, false only for NaN
    }

    /**
     * FAILS on purpose: NaN semantics of the UNBOUNDED helper. {@code anyDouble()} ranges over the
     * whole IEEE-754 domain INCLUDING NaN, and {@code x == x} is false for NaN — so JBMC finds NaN as
     * a counterexample. This documents that unbounded floating point includes NaN (use the bounded
     * form when you need an ordered, NaN-free value).
     */
    // Expected verdict: REFUTED - anyDouble() includes NaN - the false claim is refuted.
    @BmcProof(expect = Verdict.REFUTED)
    void unbounded_double_includes_nan() {
        double x = Bmc.anyDouble();
        Bmc.check(x == x); // refuted: NaN != NaN
    }
}
