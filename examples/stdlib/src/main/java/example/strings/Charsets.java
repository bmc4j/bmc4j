package example.strings;

/**
 * Ordinary code that validates/parses text inputs drawn from a restricted character class —
 * identifiers, digit strings, fixed-charset codes. Proofs over these (see the matching
 * {@code proofs.strings.CharsetProofs}) introduce the inputs with the charset-bounded symbolic
 * string helpers ({@code Bmc.anyAsciiString} / {@code Bmc.anyString(n, alphabet)}), so the proof
 * ranges over realistic text instead of all of UTF-16 — both sound and far cheaper per character.
 */
public final class Charsets {

    private Charsets() {
    }

    /** Whether every character is an ASCII digit {@code '0'..'9'}. */
    public static boolean isAllDigits(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    /** Whether the string contains no control characters (every char is printable ASCII or above). */
    public static boolean hasNoAsciiControlChars(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) < 0x20) {
                return false;
            }
        }
        return true;
    }
}
