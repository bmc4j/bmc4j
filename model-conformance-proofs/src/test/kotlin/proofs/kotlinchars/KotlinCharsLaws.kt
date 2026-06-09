package proofs.kotlinchars

import org.bmc4j.Bmc
import org.bmc4j.BmcProof

/**
 * Model proofs (axis 2) for the `kotlin.text.CharsKt` non-inline residue. The everyday predicates
 * (isDigit/isLetter/isLetterOrDigit/isUpperCase/…) are @InlineOnly — they inline to java.lang.Character
 * directly and need no model — so these laws pin the genuinely non-inline JVM members the model supplies:
 * `digitToInt`/`digitToChar` (with/without radix), `isWhitespace`, and `equals(Char, Char, ignoreCase)`.
 */
class KotlinCharsLaws {

    /** digitToInt: '7' is 7 in radix 10. */
    @BmcProof
    fun digitToInt_decimal() {
        Bmc.check('7'.digitToInt() == 7)
    }

    /** digitToInt with a radix: 'f' is 15 in radix 16. */
    @BmcProof
    fun digitToInt_hex_radix() {
        Bmc.check('f'.digitToInt(16) == 15)
    }

    /** digitToIntOrNull returns null for a non-digit instead of throwing. */
    @BmcProof
    fun digitToIntOrNull_non_digit_is_null() {
        Bmc.check('z'.digitToIntOrNull() == null && '3'.digitToIntOrNull() == 3)
    }

    /** digitToChar: 7 -> '7', and 15 -> 'F' in radix 16 (uppercased). */
    @BmcProof
    fun digitToChar_round_trip() {
        Bmc.check(7.digitToChar() == '7' && 15.digitToChar(16) == 'F')
    }

    /** isWhitespace recognizes the space and a non-space. */
    @BmcProof
    fun isWhitespace_space_and_letter() {
        Bmc.check(' '.isWhitespace() && !'a'.isWhitespace())
    }

    /** equals(ignoreCase): 'A' equals 'a' case-insensitively but not case-sensitively. */
    @BmcProof
    fun equals_ignore_case() {
        Bmc.check('A'.equals('a', ignoreCase = true) && !'A'.equals('a', ignoreCase = false))
    }
}
