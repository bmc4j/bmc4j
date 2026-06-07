package proofs.strings;

import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;

/**
 * Conformance pins for the {@code java.lang.String} ops that JBMC's own model gets wrong and that
 * bmc4j therefore <em>shims</em>: {@code equals}, {@code startsWith}, {@code endsWith},
 * {@code contains}. {@code org.bmc4j.engine.StringBytecode} redirects these call sites to the sound
 * {@code BmcStrings} rebuilds (over {@code length()} + {@code CProverString.charAt}) during analysis,
 * so ordinary code that calls {@code s.equals(t)} / {@code s.contains(x)} becomes provable.
 *
 * <p>These shims are trust-critical — every collection/Optional/Map proof that compares String
 * elements rests on the redirect being sound — yet they previously had NO {@code @BmcProof} law
 * suite (only the {@code examples/stdlib} example, which is not a green gate). This suite is that
 * record: each proof pins BOTH directions (a true-positive AND a true-negative, or an exact value
 * over the sound {@code charAt} primitive), so a nondet/unconstrained model could not satisfy it and
 * a future engine/redirect bump that silently breaks one turns this suite red.
 *
 * <p>Written in Java so each call binds to the real {@code java.lang.String} method the redirect
 * matches (Kotlin's {@code startsWith}/{@code contains} route through kotlin-stdlib and would not
 * exercise the redirected JDK method).
 */
class StringShimLaws {

    // ---- equals (shim) -----------------------------------------------------

    @BmcProof
    void equals_concrete_true_and_false() {
        Bmc.check("abc".equals("abc"));     // true: a nondet result could be false
        Bmc.check(!"abc".equals("abd"));    // false: a nondet result could be true
        Bmc.check(!"abc".equals("ab"));     // different length -> false
        Bmc.check(!"abc".equals((Object) Integer.valueOf(1)));  // non-String -> false
    }

    @BmcProof(maxStringLength = 4)
    void equals_symbolic_reflexive() {
        String s = Bmc.anyString(4);
        Bmc.check(s.equals(s));             // reflexive over every bounded string
    }

    @BmcProof(maxStringLength = 4)
    void equals_symbolic_agrees_with_charAt_scan() {
        // equals(t) must agree, in BOTH directions, with "same length AND every charAt matches" —
        // the exact relation BmcStrings.equals rebuilds. A nondet shim could refute this. Both
        // operands are symbolic by necessity; each is bounded to 3.
        String a = Bmc.anyString(3);
        String b = Bmc.anyString(3);
        boolean same = a.length() == b.length();
        if (same) {
            for (int i = 0; i < a.length(); i++) {
                if (a.charAt(i) != b.charAt(i)) {
                    same = false;
                }
            }
        }
        Bmc.check(a.equals(b) == same);
    }

    // ---- startsWith (shim) -------------------------------------------------

    @BmcProof
    void startsWith_concrete_true_and_false() {
        Bmc.check("hello".startsWith("he"));    // true
        Bmc.check(!"hello".startsWith("lo"));   // false (wrong position)
        Bmc.check(!"hi".startsWith("hello"));   // false (prefix longer than receiver)
    }

    @BmcProof(maxStringLength = 4)
    void startsWith_symbolic_self_and_empty() {
        // Every string starts with itself and with the empty prefix: a nondet shim could refute.
        String s = Bmc.anyString(4);
        Bmc.check(s.startsWith(s));
        Bmc.check(s.startsWith(""));
    }

    // ---- endsWith (shim) ---------------------------------------------------

    @BmcProof
    void endsWith_concrete_true_and_false() {
        Bmc.check("hello".endsWith("lo"));      // true
        Bmc.check(!"hello".endsWith("he"));     // false
        Bmc.check(!"hi".endsWith("hello"));     // false (suffix longer than receiver)
    }

    @BmcProof(maxStringLength = 4)
    void endsWith_symbolic_self_and_empty() {
        String s = Bmc.anyString(4);
        Bmc.check(s.endsWith(s));
        Bmc.check(s.endsWith(""));
    }

    // ---- contains (shim) ---------------------------------------------------

    @BmcProof
    void contains_concrete_true_and_false() {
        Bmc.check("hello".contains("ell"));     // true: substring present
        Bmc.check("hello".contains("hello"));   // true: whole string
        Bmc.check(!"hello".contains("z"));      // false: absent
        Bmc.check(!"hi".contains("hello"));     // false: needle longer than receiver
    }

    @BmcProof(maxStringLength = 4)
    void contains_symbolic_self_and_empty() {
        // Every string contains itself and the empty needle: a nondet shim could refute.
        String s = Bmc.anyString(4);
        Bmc.check(s.contains(s));
        Bmc.check(s.contains(""));
    }
}
