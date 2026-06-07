package proofs.strings;

import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;

/**
 * Conformance pins for the {@code java.lang.StringBuilder} mutator/accessor ops consumer code uses
 * directly (as opposed to the {@code append(...)} overloads on the concat desugar path, which are
 * pinned in {@link StringBuilderAppendLaws}): {@code reverse}, {@code insert(int,String)},
 * {@code deleteCharAt}, {@code length}, {@code charAt}. JBMC models these natively (no shim), so this
 * suite is the conformance record that they are sound.
 *
 * <p>Each proof asserts the result via {@code length()} + {@code charAt} (the sound primitives) and
 * pins an EXACT result — which a nondet StringBuilder model could not satisfy — concretely, plus a
 * symbolic law for {@code reverse}.
 */
class StringBuilderLaws {

    // ---- reverse() --------------------------------------------------------

    @BmcProof
    void reverse_concrete() {
        String r = new StringBuilder("abc").reverse().toString();
        Bmc.check(r.length() == 3);
        Bmc.check(r.charAt(0) == 'c' && r.charAt(1) == 'b' && r.charAt(2) == 'a');
    }

    @BmcProof
    void reverse_symbolic_length_preserved_and_ends_swap() {
        // Length-preserving, and the first char of the reverse is the last of the source — a nondet
        // reverse could satisfy neither.
        String s = Bmc.anyString(3);
        String r = new StringBuilder(s).reverse().toString();
        Bmc.check(r.length() == s.length());
        if (s.length() > 0) {
            Bmc.check(r.charAt(0) == s.charAt(s.length() - 1));
        }
    }

    // ---- insert(int, String) ----------------------------------------------

    @BmcProof
    void insert_concrete() {
        String r = new StringBuilder("ac").insert(1, "b").toString();
        Bmc.check(r.length() == 3);
        Bmc.check(r.charAt(0) == 'a' && r.charAt(1) == 'b' && r.charAt(2) == 'c');
    }

    // ---- deleteCharAt(int) ------------------------------------------------

    @BmcProof
    void deleteCharAt_concrete() {
        String r = new StringBuilder("abc").deleteCharAt(1).toString();
        Bmc.check(r.length() == 2);
        Bmc.check(r.charAt(0) == 'a' && r.charAt(1) == 'c');
    }

    // ---- length() / charAt(int) -------------------------------------------

    @BmcProof
    void length_and_charAt_concrete() {
        StringBuilder sb = new StringBuilder("abc");
        Bmc.check(sb.length() == 3);
        Bmc.check(sb.charAt(0) == 'a' && sb.charAt(2) == 'c');
    }
}
