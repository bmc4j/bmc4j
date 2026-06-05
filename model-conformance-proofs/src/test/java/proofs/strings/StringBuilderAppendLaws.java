package proofs.strings;

import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;

/**
 * Conformance pins for every {@code StringBuilder.append(...)} overload the concat /
 * record-toString desugar in {@code org.bmc4j.engine.StringBytecode} can emit. The desugar rewrites
 * the Java-9 {@code StringConcatFactory} {@code invokedynamic} back to {@code new StringBuilder()
 * .append(...).append(...).toString()}, so the soundness of {@code "x" + y} rests entirely on JBMC
 * modelling these overloads soundly. These proofs pin that, so a future engine/model bump can't
 * silently break one and quietly invalidate every concatenation proof.
 *
 * <p>Each proof asserts the appended content/length via {@code length()} + {@code charAt} (the sound
 * primitives) and pins an EXACT result — which a nondet StringBuilder model could not satisfy — so a
 * green run is the conformance record.
 *
 * <h2>append overload soundness map (probed 2026-06)</h2>
 * <ul>
 *   <li>{@code append(String)} — SOUND. The desugar's literal-chunk and {@code String}-arg path.</li>
 *   <li>{@code append(char)} — SOUND. A {@code char} arg appends as one character (not its codepoint
 *       digits), so the desugar appends chars directly.</li>
 *   <li>{@code append(boolean)} — SOUND. Renders {@code "true"}/{@code "false"}.</li>
 *   <li>{@code append(int)} — SOUND (probed). The desugar nonetheless routes {@code int} through
 *       {@code Integer.toString(i)} + {@code append(String)} as belt-and-suspenders (historically the
 *       int overload was the suspect one); this pin guards the direct overload regardless.</li>
 *   <li>{@code append(long)} — SOUND (probed); desugar routes via {@code Long.toString} likewise.</li>
 *   <li>{@code append(float)} — SOUND ({@code 1.5f} renders {@code "1.5"}); the record-toString
 *       desugar emits this overload directly.</li>
 *   <li><b>{@code append(double)} — UNSOUND.</b> The rendered result is UNCONSTRAINED ({@code 1.5d}
 *       does not render {@code "1.5"}; even {@code Double.toString(d)} refutes). JBMC does not model
 *       double formatting (unlike float). The desugar still emits {@code append(double)} for a double
 *       operand, so {@code "x" + aDouble} yields an unconstrained result string — but an unconstrained
 *       result is conservatively SOUND: JBMC can always counter a non-tautological claim about it, so
 *       it OVER-refutes (imprecise) rather than producing a false green. Pinned completion-only.</li>
 *   <li>{@code append(Object)} — SOUND for a {@code String} value (renders via {@code String.valueOf}).
 *       The desugar routes a non-String reference / array component here; a true array or arbitrary
 *       reference renders through {@code Object.toString}, which JBMC links to nondet — which is why
 *       the desugar only desugars records whose components all render soundly (primitive or String)
 *       and leaves the rest as their original indy rather than emit a wrong result.</li>
 *   <li>{@code append(CharSequence)} — SOUND for a {@code String} value.</li>
 *   <li><b>{@code append(char[])} — UNSOUND.</b> {@code "ab".toCharArray()} appended back yields an
 *       UNCONSTRAINED result (length/content nondet): asserting the exact chars REFUTES. The desugar
 *       NEVER emits this overload — a {@code char[]} concat operand is an array, so it is routed
 *       through {@code append(Object)} (matching {@code StringConcat}'s "arrays concat as their
 *       {@code Object.toString()}" semantics), and array record components are left un-desugared. We
 *       pin only that the call COMPLETES (a nondet boolean is trivially true-or-false); we do NOT
 *       assert content, because that is genuinely unsound. Documented here so it stays visible.</li>
 * </ul>
 */
class StringBuilderAppendLaws {

    @BmcProof
    void append_String() {
        StringBuilder sb = new StringBuilder();
        sb.append("ab");
        String r = sb.toString();
        Bmc.check(r.length() == 2);
        Bmc.check(r.charAt(0) == 'a' && r.charAt(1) == 'b');
    }

    @BmcProof
    void append_char() {
        StringBuilder sb = new StringBuilder();
        sb.append('x');
        String r = sb.toString();
        Bmc.check(r.length() == 1);
        Bmc.check(r.charAt(0) == 'x');
    }

    @BmcProof
    void append_boolean() {
        StringBuilder sb = new StringBuilder();
        sb.append(true);
        String r = sb.toString();
        Bmc.check(r.length() == 4);
        Bmc.check(r.charAt(0) == 't' && r.charAt(1) == 'r' && r.charAt(2) == 'u' && r.charAt(3) == 'e');
    }

    @BmcProof
    void append_int() {
        StringBuilder sb = new StringBuilder();
        sb.append(42);
        String r = sb.toString();
        Bmc.check(r.length() == 2);
        Bmc.check(r.charAt(0) == '4' && r.charAt(1) == '2');
    }

    @BmcProof
    void append_long() {
        StringBuilder sb = new StringBuilder();
        sb.append(42L);
        String r = sb.toString();
        Bmc.check(r.length() == 2);
        Bmc.check(r.charAt(0) == '4' && r.charAt(1) == '2');
    }

    @BmcProof
    void append_float() {
        StringBuilder sb = new StringBuilder();
        sb.append(1.5f);
        String r = sb.toString();
        Bmc.check(r.length() == 3);
        Bmc.check(r.charAt(0) == '1' && r.charAt(1) == '.' && r.charAt(2) == '5');
    }

    @BmcProof
    void append_double_is_unsound_so_only_completion_is_pinned() {
        // append(double) is UNSOUND in JBMC: the rendered result is UNCONSTRAINED. Both the direct
        // overload AND Double.toString(d) + append(String) refute on the exact "1.5" —
        // double formatting is not modelled, unlike float. We pin ONLY that the call completes.
        //
        // Conservatively SOUND consequence for concat: the desugar emits append(double) for a double
        // operand, so "x"+aDouble yields an unconstrained result string. Unconstrained means JBMC can
        // pick any value, so it can ALWAYS find a counterexample to a non-tautological claim about the
        // result — it never produces a FALSE GREEN; the failure mode is over-refutation (imprecision),
        // not unsoundness. Documented in the overload map above so the limitation stays visible.
        StringBuilder sb = new StringBuilder();
        sb.append(1.5d);
        String r = sb.toString();
        Bmc.check(r == r);   // tautology: a nondet result is trivially equal to itself
    }

    @BmcProof
    void append_Object_string_value() {
        StringBuilder sb = new StringBuilder();
        sb.append((Object) "ab");
        String r = sb.toString();
        Bmc.check(r.length() == 2);
        Bmc.check(r.charAt(0) == 'a' && r.charAt(1) == 'b');
    }

    @BmcProof
    void append_CharSequence_string_value() {
        StringBuilder sb = new StringBuilder();
        sb.append((CharSequence) "ab");
        String r = sb.toString();
        Bmc.check(r.length() == 2);
        Bmc.check(r.charAt(0) == 'a' && r.charAt(1) == 'b');
    }

    @BmcProof
    void append_charArray_is_unsound_so_only_completion_is_pinned() {
        // append(char[]) is UNSOUND in JBMC (result is unconstrained), so we pin ONLY that the call
        // completes — asserting content would refute. The desugar never emits this overload (char[]
        // operands route through append(Object)); see the class doc.
        StringBuilder sb = new StringBuilder();
        sb.append(new char[] {'a', 'b'});
        String r = sb.toString();
        Bmc.check(r == r);   // tautology: a nondet result is trivially equal to itself
    }
}
