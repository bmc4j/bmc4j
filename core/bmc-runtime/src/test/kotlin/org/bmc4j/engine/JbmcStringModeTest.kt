package org.bmc4j.engine

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.bmc4j.StringMode

/**
 * Unit tests for the per-proof [StringMode] to JBMC flag mapping in [Jbmc.args] /
 * [Jbmc.appendVerdictRelevantFlags], and that the mode is part of the verdict-relevant signature.
 *
 * The mapping has one HARD constraint: JBMC rejects `--max-nondet-string-length` together with
 * `--no-refine-strings` ("cannot use --max-nondet-string-length with --no-refine-strings"), so under
 * [StringMode.NONE] the two are mutually exclusive - `--no-refine-strings` is emitted and
 * `--max-nondet-string-length` is omitted. Under [StringMode.REFINEMENT] (the default) neither
 * `--no-refine-strings` appears nor is the existing max-nondet behaviour changed.
 */
internal class JbmcStringModeTest {

    private fun args(stringMode: StringMode, maxStringLength: Int = 16): List<String> =
            Jbmc.args("pkg.T", "pkg.T.proof", "/cp",
                    1, true, maxStringLength, null, "", stringMode)

    @Test
    fun none_emitsNoRefineStrings_andOmitsMaxNondetStringLength() {
        val a = args(StringMode.NONE, maxStringLength = 16)
        assertTrue(a.contains("--no-refine-strings"),
                "StringMode.NONE must emit --no-refine-strings: $a")
        assertFalse(a.contains("--max-nondet-string-length"),
                "StringMode.NONE must OMIT --max-nondet-string-length (JBMC rejects the two together): $a")
    }

    @Test
    fun refinement_omitsNoRefineStrings_andKeepsMaxNondetStringLength() {
        val a = args(StringMode.REFINEMENT, maxStringLength = 16)
        assertFalse(a.contains("--no-refine-strings"),
                "StringMode.REFINEMENT must NOT emit --no-refine-strings: $a")
        assertTrue(a.contains("--max-nondet-string-length"),
                "StringMode.REFINEMENT keeps the existing --max-nondet-string-length behaviour: $a")
        // and the value rides right after the flag, unchanged from the pre-feature path
        val i = a.indexOf("--max-nondet-string-length")
        assertTrue(i >= 0 && i + 1 < a.size && a[i + 1] == "16",
                "--max-nondet-string-length must carry its bound: $a")
    }

    @Test
    fun refinement_withZeroMaxStringLength_emitsNeither() {
        // maxStringLength <= 0 already means "do not pass the flag"; NONE behaviour is independent of it.
        val a = args(StringMode.REFINEMENT, maxStringLength = 0)
        assertFalse(a.contains("--no-refine-strings"), a.toString())
        assertFalse(a.contains("--max-nondet-string-length"), a.toString())
    }

    @Test
    fun verdictRelevantFlags_differBetweenModes() {
        val refinement = BmcRequest("pkg.C", "pkg.C.proof", "/cp",
                16, true, 16, "", 0, stringMode = StringMode.REFINEMENT)
        val none = BmcRequest("pkg.C", "pkg.C.proof", "/cp",
                16, true, 16, "", 0, stringMode = StringMode.NONE)
        assertNotEquals(Jbmc.verdictRelevantFlags(refinement), Jbmc.verdictRelevantFlags(none),
                "the verdict-relevant flag signature must differ between StringMode.REFINEMENT and NONE " +
                        "so a cached verdict for one mode is never reused for the other")
    }
}
