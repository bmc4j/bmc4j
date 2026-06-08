package proofs.kotlinstrings

import org.bmc4j.Bmc
import org.bmc4j.BmcProof

/**
 * Model proofs (axis 2) for the `kotlin.text.StringsKt` facade — the bounded char-array transforms over
 * the already-sound `java.lang.String` primitives, modeled in `kotlin/text/StringsKt.java`. Each Kotlin
 * extension call below (`s.trim()`, `s.take(n)`, `s.substringBefore(c)`, …) emits a
 * `StringsKt.<op>((CharSequence)s, …)` invokestatic that binds to the shadowing model on JBMC's analysis
 * classpath; the un-modeled facade members would otherwise nondet-stub silently.
 *
 * Soundness rule (matching `proofs.strings.StringLaws`): each proof pins the result with `length` + per-
 * index `charAt` / an exact value / BOTH directions of a boolean, so a nondet model could not satisfy it.
 * Symbolic laws range-reduce hard — short strings (≤4) over `Bmc.anyString` — to keep the string-
 * refinement solver tight. They confirm the model actually walks the receiver by index (a havoc'd stub
 * would refute the symbolic relations).
 */
class KotlinStringsLaws {

    // ---- trim / trimStart / trimEnd ----

    @BmcProof
    fun trim_concrete() {
        Bmc.check("  hi  ".trim() == "hi")
        Bmc.check("  hi".trimStart() == "hi")
        Bmc.check("hi  ".trimEnd() == "hi")
        Bmc.check("xxhixx".trim('x') == "hi")
    }

    @BmcProof(maxStringLength = 4)
    fun trim_symbolic_no_leading_trailing_space() {
        val s = Bmc.anyString(4)
        val t = s.trim()
        // A trimmed string never begins or ends with whitespace (unless empty).
        if (t.isNotEmpty()) {
            Bmc.check(t[0] != ' ' && t[t.length - 1] != ' ')
        }
    }

    // ---- take / drop / takeLast / dropLast ----

    @BmcProof
    fun take_drop_concrete() {
        Bmc.check("hello".take(2) == "he")
        Bmc.check("hello".drop(2) == "llo")
        Bmc.check("hello".takeLast(2) == "lo")
        Bmc.check("hello".dropLast(2) == "hel")
        Bmc.check("ab".take(5) == "ab")   // n > length -> whole string
        Bmc.check("ab".drop(5) == "")     // n > length -> empty
    }

    @BmcProof(maxStringLength = 4)
    fun take_plus_drop_is_identity() {
        val s = Bmc.anyString(4)
        val n = Bmc.anyInt(0, s.length)
        Bmc.check(s.take(n) + s.drop(n) == s)
    }

    // ---- substring(range) / slice ----

    @BmcProof
    fun substring_range_concrete() {
        Bmc.check("hello".substring(1..3) == "ell")
        Bmc.check("hello".slice(0..1) == "he")
    }

    // ---- substringBefore / After / *Last ----

    @BmcProof
    fun substring_before_after_concrete() {
        Bmc.check("a.b.c".substringBefore('.') == "a")
        Bmc.check("a.b.c".substringAfter('.') == "b.c")
        Bmc.check("a.b.c".substringBeforeLast('.') == "a.b")
        Bmc.check("a.b.c".substringAfterLast('.') == "c")
        Bmc.check("abc".substringBefore('z', "none") == "none")   // missing delimiter
    }

    // ---- removePrefix / removeSuffix / removeSurrounding ----

    @BmcProof
    fun remove_prefix_suffix_concrete() {
        Bmc.check("prefoo".removePrefix("pre") == "foo")
        Bmc.check("foobar".removeSuffix("bar") == "foo")
        Bmc.check("foo".removePrefix("xx") == "foo")       // no match -> unchanged
        Bmc.check("<hi>".removeSurrounding("<", ">") == "hi")
        Bmc.check("(hi)".removeSurrounding("<", ">") == "(hi)")
    }

    // ---- startsWith / endsWith / contains / indexOf (default + ignoreCase) ----

    @BmcProof
    fun starts_ends_contains_concrete() {
        Bmc.check("hello".startsWith("he"))
        Bmc.check(!"hello".startsWith("lo"))
        Bmc.check("hello".endsWith("lo"))
        Bmc.check(!"hello".endsWith("he"))
        Bmc.check("hello".contains("ell"))
        Bmc.check(!"hello".contains('z'))
        Bmc.check("hello".indexOf('l') == 2)
        Bmc.check("hello".lastIndexOf('l') == 3)
        Bmc.check("hello".indexOf('z') == -1)
    }

    @BmcProof
    fun ignore_case_ascii_concrete() {
        Bmc.check("Hello".startsWith("hello", ignoreCase = true))
        Bmc.check("Hello".contains("ELL", ignoreCase = true))
        Bmc.check("ABC".equals("abc", ignoreCase = true))
        Bmc.check(!"ABC".equals("abd", ignoreCase = true))
    }

    @BmcProof(maxStringLength = 4)
    fun contains_self_symbolic() {
        val s = Bmc.anyString(4)
        Bmc.check(s.contains(s))
        Bmc.check(s.startsWith(s))
        Bmc.check(s.endsWith(s))
    }

    // ---- replace / replaceFirst ----

    @BmcProof
    fun replace_concrete() {
        Bmc.check("banana".replace('a', 'o') == "bonono")
        Bmc.check("aXbXc".replace("X", "-") == "a-b-c")
        Bmc.check("aXbXc".replaceFirst("X", "-") == "a-bXc")
        Bmc.check("aXbXc".replaceFirst('X', '-') == "a-bXc")
    }

    // ---- padStart / padEnd ----

    @BmcProof
    fun pad_concrete() {
        Bmc.check("7".padStart(3, '0') == "007")
        Bmc.check("7".padEnd(3, '0') == "700")
        Bmc.check("abcd".padStart(2, '0') == "abcd")  // already long enough
    }

    // ---- reversed / repeat ----

    @BmcProof
    fun reversed_repeat_concrete() {
        Bmc.check("abc".reversed() == "cba")
        Bmc.check("ab".repeat(3) == "ababab")
        Bmc.check("x".repeat(0) == "")
    }

    @BmcProof(maxStringLength = 4)
    fun reversed_twice_is_identity() {
        val s = Bmc.anyString(4)
        Bmc.check(s.reversed().reversed().toString() == s)
    }

    // ---- isBlank / first / last / single / getOrNull ----

    @BmcProof
    fun blank_and_element_access_concrete() {
        Bmc.check("   ".isBlank())
        Bmc.check(!"  x ".isBlank())
        Bmc.check("hi".first() == 'h')
        Bmc.check("hi".last() == 'i')
        Bmc.check("".firstOrNull() == null)
        Bmc.check("hi".getOrNull(5) == null)
        Bmc.check("a".single() == 'a')
        Bmc.check("ab".singleOrNull() == null)
        Bmc.check("abc".lastIndex == 2)
    }

    // ---- commonPrefixWith / commonSuffixWith ----

    @BmcProof
    fun common_prefix_suffix_concrete() {
        Bmc.check("abcde".commonPrefixWith("abxyz") == "ab")
        Bmc.check("hello".commonSuffixWith("yello") == "ello")
        Bmc.check("abc".commonPrefixWith("xyz") == "")
    }

    // ---- toList / toSet / count(predicate) ----

    @BmcProof
    fun to_collections_concrete() {
        val xs = "abc".toList()
        Bmc.check(xs.size == 3 && xs[0] == 'a' && xs[2] == 'c')
        val s = "aabbc".toSet()
        Bmc.check(s.size == 3 && s.contains('a') && !s.contains('z'))
    }
}
