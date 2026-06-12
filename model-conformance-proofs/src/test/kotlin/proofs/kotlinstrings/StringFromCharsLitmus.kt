package proofs.kotlinstrings

import org.bmc4j.Bmc
import org.bmc4j.BmcProof
import org.bmc4j.Verdict

/**
 * Materializing a `java.lang.String` from char data — `Char.toString()` / `String.valueOf(char)`,
 * `String(CharArray)`, `CharArray.concatToString()` — and reading the result back with `length` +
 * `s[i]`. The soundness witness is that the read-back chars EQUAL the source chars for all symbolic
 * inputs (the `s[i] == c` checks): a nondet-constructed String could not satisfy them.
 *
 * JBMC links its native from-chars construction (`new String(char[])` / `String.valueOf(char[])`,
 * which lower to `org.cprover.CProverString.ofCharArray`) to an UNCONSTRAINED string, so these proofs
 * were UNKNOWN. `StringBytecode` now redirects those construction call sites to the sound
 * `BmcStrings.ofChar(s)` rebuild (StringBuilder.append(char) + toString — the one construction
 * primitive JBMC models soundly, the same machinery `CharArray.concatToString()` already uses), so
 * the materialized String's `length`/`charAt` agree with the source chars.
 */
class StringFromCharsLitmus {

    /** Char.toString() — emits `String.valueOf(char)`. The headline gap. */
    @BmcProof(maxStringLength = 1, unwind = 2)
    fun char_to_string_is_sound() {
        val c = Bmc.anyInt(0x41, 0x5A).toChar()   // a symbolic ASCII letter A..Z
        val s = c.toString()                       // String-from-char construction (the gap)
        Bmc.check(s.length == 1)
        Bmc.check(s[0] == c)                        // charAt of the materialized string is sound
    }

    /** new String(charArrayOf(a, b)) — emits the `String([C)V` constructor. */
    @BmcProof(maxStringLength = 2, unwind = 3)
    fun string_from_char_array_is_sound() {
        val a = Bmc.anyInt(0x41, 0x5A).toChar()
        val b = Bmc.anyInt(0x41, 0x5A).toChar()
        val arr = charArrayOf(a, b)
        val s = String(arr)
        Bmc.check(s.length == 2)
        Bmc.check(s[0] == a)
        Bmc.check(s[1] == b)
    }

    /** CharArray.concatToString() — already sound via the StringsKt model; pin it stays sound. */
    @BmcProof(maxStringLength = 2, unwind = 3)
    fun concat_to_string_is_sound() {
        val a = Bmc.anyInt(0x41, 0x5A).toChar()
        val b = Bmc.anyInt(0x41, 0x5A).toChar()
        val s = charArrayOf(a, b).concatToString()
        Bmc.check(s.length == 2)
        Bmc.check(s[0] == a)
        Bmc.check(s[1] == b)
    }

    /** A from-chars String then composed with a sound content op (equals) — proves the rebuild
     *  composes with the existing BmcStrings shims, not just with raw charAt. */
    @BmcProof(maxStringLength = 1, unwind = 2)
    fun materialized_string_composes_with_equals() {
        val c = Bmc.anyInt(0x41, 0x5A).toChar()
        val s = c.toString()
        if (c == 'A') {
            Bmc.check(s == "A")     // String.equals -> BmcStrings.equals over the materialized content
        }
    }

    /**
     * Negative control (soundness witness): the materialized char must be EXACTLY the source char,
     * never an over-constrained / wrong value that could false-VERIFY. This deliberately-false claim
     * (`s[0]` always equals a single fixed letter, though the source is symbolic) MUST be REFUTED with
     * a concrete counterexample — proving the rebuild faithfully carries the symbolic content rather
     * than collapsing it to a constant.
     */
    @BmcProof(maxStringLength = 1, unwind = 2, expect = Verdict.REFUTED)
    fun materialized_char_is_not_over_constrained() {
        val c = Bmc.anyInt(0x41, 0x5A).toChar()
        val s = c.toString()
        Bmc.check(s[0] == 'A')   // false for c != 'A' -> must refute
    }
}
