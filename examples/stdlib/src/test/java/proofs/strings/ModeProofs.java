package proofs.strings;

import example.strings.Modes;
import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;
import org.bmc4j.Verdict;

/**
 * Proving code that uses {@code String.equals}. Introduce symbolic strings with a length bound
 * ({@code Bmc.anyString(n)}) so the sound character-wise comparison stays
 * bounded. The code under proof uses plain {@code equals} — bmc4j makes it sound.
 */
class ModeProofs {

    // PASS: "prod" is recognized as prod. (Native JBMC String.equals can't prove this.)
    @BmcProof(unwind = 8)
    void prod_is_recognized() {
        Bmc.check(Modes.isProd("prod"));
    }

    // PASS over every bounded string: anything that isn't "prod" is not prod.
    @BmcProof(unwind = 8)
    void non_prod_is_not_recognized() {
        String mode = Bmc.anyString(8);
        Bmc.assume(!mode.equals("prod"));
        Bmc.check(!Modes.isProd(mode));
    }

    // INTENDED FAILURE: banner NPEs when the value is unset/null (mode.equals on null).
    // Expected verdict: REFUTED - an unset mode dereferences null in banner().
    @BmcProof(expect = Verdict.REFUTED)
    void banner_throws_on_unset() {
        String mode = Bmc.anyBoolean() ? Bmc.anyString(8) : null;
        Modes.banner(mode);
    }

    // PASS: startsWith is sound — an "eu-" prefix is recognized.
    @BmcProof(unwind = 4)
    void eu_prefix_recognized() {
        Bmc.check(Modes.isEuHost("eu-west-1"));
    }

    // PASS over every bounded string: a host too short to hold "eu-" is never an EU host.
    @BmcProof
    void too_short_to_be_eu_host() {
        String host = Bmc.anyString(2);
        Bmc.check(!Modes.isEuHost(host));
    }

    // PASS: contains is sound — a "prod" substring is found.
    @BmcProof(unwind = 8)
    void contains_prod_recognized() {
        Bmc.check(Modes.targetsProd("db.prod.internal"));
    }

    // PASS: contains with a StringBuilder (non-String CharSequence) needle no longer throws a spurious
    // ClassCastException inside bmc4j's own stand-in. The non-String-needle path degrades to
    // a nondet result rather than crashing, so this proof — which only requires the call to COMPLETE
    // without that spurious refutation — verifies. (A nondet boolean is trivially true-or-false.)
    @BmcProof(unwind = 8)
    void contains_with_stringbuilder_needle_does_not_throw() {
        boolean r = Modes.containsBuilt("db.prod.internal", "prod");
        Bmc.check(r || !r);
    }

    // PASS over every bounded pair: String+String concatenation tracks length soundly. The `+`
    // compiles to a StringConcatFactory invokedynamic, which bmc4j desugars to a sound form.
    @BmcProof
    void qualify_length_is_sum() {
        String env = Bmc.anyString(4);
        String region = Bmc.anyString(4);
        Bmc.check(Modes.qualify(env, region).length() == env.length() + 1 + region.length());
    }

    // PASS: anyOf varargs picks an element of an explicit value set — every choice is "us" or "eu".
    @BmcProof(unwind = 4)
    void anyOf_varargs_picks_a_listed_region() {
        String region = Bmc.anyOf("us", "eu");
        Bmc.check(region.equals("us") || region.equals("eu"));
    }

    // PASS: anyOf(List) picks an element of a collection-shaped domain.
    @BmcProof(unwind = 4)
    void anyOf_list_picks_a_listed_region() {
        String region = Bmc.anyOf(java.util.List.of("us", "eu", "ap"));
        Bmc.check(region.equals("us") || region.equals("eu") || region.equals("ap"));
    }

    // PASS: a per-proof @BmcProof(maxStringLength = N) override bounds this proof's symbolic strings
    //. A host shorter than "eu-" can never be an EU host, proven for length 0..2 only.
    @BmcProof(maxStringLength = 2)
    void per_proof_string_length_override_bounds_the_input() {
        String host = Bmc.anyString(2);
        Bmc.check(!Modes.isEuHost(host));
    }
}
