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

    @BmcProof
    fun pad_start_length_and_suffix() {
        // padStart pads on the LEFT to the target length and keeps the original as the suffix, pinned per
        // index. (Concrete: the StringBuilder pad over a symbolic receiver is budget-fragile under the
        // parallel suite; pad_concrete pins the exact result.)
        val s = "ab"
        val p = s.padStart(4, '0')
        Bmc.check(p.length == 4 && p[0] == '0' && p[1] == '0')
        for (i in s.indices) {
            Bmc.check(p[2 + i] == s[i])
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

    @BmcProof(maxStringLength = 3)
    fun take_plus_drop_lengths_and_indices() {
        // take(k) is the first k chars and drop(k) the rest, pinned per index over a symbolic receiver at
        // a CONCRETE split point (a symbolic split blows the string-refinement budget; the symbolic
        // receiver already exercises the by-index walk). Lengths sum back to the original.
        val s = Bmc.anyString(3)
        val k = 1
        val t = s.take(k)
        val d = s.drop(k)
        if (s.length >= k) {
            Bmc.check(t.length == k && d.length == s.length - k)
            Bmc.check(t[0] == s[0])
            for (i in 0 until d.length) {
                Bmc.check(d[i] == s[k + i])
            }
        }
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

    @BmcProof(maxStringLength = 3, unwind = 4)
    fun contains_self_symbolic() {
        // A string contains / starts with / ends with itself — a tautology a havoc'd model would refute.
        // Length 3 + unwind 4 (just past the bound) keeps the heavy symbolic self-containment circuit
        // small enough to solve quickly.
        val s = Bmc.anyString(3)
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

    @BmcProof
    fun reversed_mirrors_by_index() {
        // reversed()[i] == s[len-1-i], pinned per index over a concrete receiver — the model walks the
        // receiver backwards. (Concrete, not symbolic: a symbolic StringBuilder reverse is budget-fragile
        // under the parallel suite — see reversed_repeat_concrete for the value check.)
        val s = "abcd"
        val r = s.reversed()
        Bmc.check(r.length == 4)
        for (i in s.indices) {
            Bmc.check(r[i] == s[s.length - 1 - i])
        }
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

    // ---- any / none (no-predicate emptiness) ----

    @BmcProof
    fun any_none_concrete() {
        Bmc.check("a".any())
        Bmc.check(!"".any())
        Bmc.check("".none())
        Bmc.check(!"a".none())
    }

    @BmcProof(maxStringLength = 4)
    fun any_is_not_empty_symbolic() {
        val s = Bmc.anyString(4)
        Bmc.check(s.any() == s.isNotEmpty())
        Bmc.check(s.none() == s.isEmpty())
    }

    // ---- max / min char ----

    @BmcProof
    fun max_min_concrete() {
        Bmc.check("bca".maxOrNull() == 'c')
        Bmc.check("bca".minOrNull() == 'a')
        Bmc.check("".maxOrNull() == null)
    }

    @BmcProof
    fun max_min_is_a_bound() {
        // maxOrNull/minOrNull bound every char of the receiver, pinned per index over a concrete string.
        // (Concrete: a symbolic char-scan is budget-fragile under the parallel suite; max_min_concrete
        // pins the exact extrema and this pins the bound relation.)
        val s = "dbeca"
        val mx = s.maxOrNull()
        val mn = s.minOrNull()
        Bmc.check(mx != null && mn != null)
        for (i in s.indices) {
            Bmc.check(s[i] <= mx!! && s[i] >= mn!!)
        }
    }

    // ---- indexOfAny / lastIndexOfAny / findAnyOf ----

    @BmcProof
    fun index_of_any_concrete() {
        Bmc.check("hello".indexOfAny(charArrayOf('l', 'z')) == 2)
        Bmc.check("hello".lastIndexOfAny(charArrayOf('l', 'z')) == 3)
        Bmc.check("hello".indexOfAny(charArrayOf('x')) == -1)
        Bmc.check("a.b:c".indexOfAny(listOf(":", ".")) == 1)
    }

    // ---- slice(Iterable) / toCollection / toSortedSet / withIndex ----

    @BmcProof
    fun slice_collection_concrete() {
        // slice(Iterable) returns a CharSequence; pin it per index (CharSequence == String would route
        // through String.equals -> CProverString.equals, which JBMC nondet-stubs).
        val sl = "hello".slice(listOf(1, 3, 4))
        Bmc.check(sl.length == 3 && sl[0] == 'e' && sl[1] == 'l' && sl[2] == 'o')
        val wi = "ab".withIndex().toList()
        Bmc.check(wi.size == 2 && wi[0].index == 0 && wi[0].value == 'a' && wi[1].value == 'b')
    }

    // ---- zip / zipWithNext ----

    @BmcProof
    fun zip_concrete() {
        val z = "abc".zip("xy")
        Bmc.check(z.size == 2 && z[0].first == 'a' && z[0].second == 'x' && z[1].second == 'y')
        val zn = "abc".zipWithNext()
        Bmc.check(zn.size == 2 && zn[0].first == 'a' && zn[0].second == 'b' && zn[1].first == 'b')
    }

    // ---- chunked / windowed / lines ----

    @BmcProof
    fun chunked_windowed_lines_concrete() {
        val c = "abcde".chunked(2)
        Bmc.check(c.size == 3 && c[0] == "ab" && c[1] == "cd" && c[2] == "e")
        val w = "abcd".windowed(2, 1, false)
        Bmc.check(w.size == 3 && w[0] == "ab" && w[2] == "cd")
        val ls = "a\nbb\nc".lines()
        Bmc.check(ls.size == 3 && ls[0] == "a" && ls[1] == "bb" && ls[2] == "c")
    }

    // ---- split (char / string delimiter, not regex) ----

    @BmcProof
    fun split_concrete() {
        val a = "a,b,c".split(",")
        Bmc.check(a.size == 3 && a[0] == "a" && a[2] == "c")
        val b = "a.b.c".split('.')
        Bmc.check(b.size == 3 && b[1] == "b")
        val lim = "a,b,c".split(",", limit = 2)
        Bmc.check(lim.size == 2 && lim[0] == "a" && lim[1] == "b,c")
    }

    // ---- integer parses / toBooleanStrict (no dtoa, no locale) ----

    @BmcProof
    fun parse_int_concrete() {
        Bmc.check("123".toIntOrNull() == 123)
        Bmc.check("-7".toIntOrNull() == -7)
        Bmc.check("ff".toIntOrNull(16) == 255)
        Bmc.check("12x".toIntOrNull() == null)
        Bmc.check("".toIntOrNull() == null)
        Bmc.check("9".toLongOrNull() == 9L)
    }

    @BmcProof
    fun parse_boolean_concrete() {
        Bmc.check("true".toBooleanStrictOrNull() == true)
        Bmc.check("false".toBooleanStrictOrNull() == false)
        Bmc.check("yes".toBooleanStrictOrNull() == null)
    }

    // ---- asSequence / asIterable / iterator (concrete backing, never virtual CharIterator) ----

    @BmcProof
    fun as_sequence_iterable_iterator_concrete() {
        Bmc.check("abc".asSequence().toList().size == 3)
        Bmc.check("abc".asIterable().count() == 3)
        val it = "ab".iterator()
        Bmc.check(it.hasNext() && it.nextChar() == 'a' && it.nextChar() == 'b' && !it.hasNext())
    }

    // ---- indent ops ----

    @BmcProof
    fun indent_concrete() {
        Bmc.check("a\nb".prependIndent(">") == ">a\n>b")
        Bmc.check("  a\n  b".trimIndent() == "a\nb")
        Bmc.check("|x\n|y".trimMargin() == "x\ny")
    }

    // ---- random: every draw is an in-bounds char of the receiver ----

    @BmcProof
    fun random_in_bounds_concrete() {
        val c = "abc".random()
        Bmc.check(c == 'a' || c == 'b' || c == 'c')
    }
}
