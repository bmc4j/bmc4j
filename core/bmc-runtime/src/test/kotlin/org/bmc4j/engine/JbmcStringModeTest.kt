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
 * [StringMode.CHAR_ARRAY_MODEL] the two are mutually exclusive - `--no-refine-strings` is emitted and
 * `--max-nondet-string-length` is omitted. Under [StringMode.REFINEMENT] (the default) neither
 * `--no-refine-strings` appears nor is the existing max-nondet behaviour changed.
 */
internal class JbmcStringModeTest {

    private fun args(stringMode: StringMode, maxStringLength: Int = 16): List<String> =
            Jbmc.args("pkg.T", "pkg.T.proof", "/cp",
                    1, true, maxStringLength, null, "", stringMode)

    @Test
    fun none_emitsNoRefineStrings_andOmitsMaxNondetStringLength() {
        val a = args(StringMode.CHAR_ARRAY_MODEL, maxStringLength = 16)
        assertTrue(a.contains("--no-refine-strings"),
                "StringMode.CHAR_ARRAY_MODEL must emit --no-refine-strings: $a")
        assertFalse(a.contains("--max-nondet-string-length"),
                "StringMode.CHAR_ARRAY_MODEL must OMIT --max-nondet-string-length (JBMC rejects the two together): $a")
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
        // maxStringLength <= 0 already means "do not pass the flag"; CHAR_ARRAY_MODEL behaviour is independent of it.
        val a = args(StringMode.REFINEMENT, maxStringLength = 0)
        assertFalse(a.contains("--no-refine-strings"), a.toString())
        assertFalse(a.contains("--max-nondet-string-length"), a.toString())
    }

    @Test
    fun verdictRelevantFlags_differBetweenModes() {
        val refinement = BmcRequest("pkg.C", "pkg.C.proof", "/cp",
                16, true, 16, "", 0, stringMode = StringMode.REFINEMENT)
        val none = BmcRequest("pkg.C", "pkg.C.proof", "/cp",
                16, true, 16, "", 0, stringMode = StringMode.CHAR_ARRAY_MODEL)
        assertNotEquals(Jbmc.verdictRelevantFlags(refinement), Jbmc.verdictRelevantFlags(none),
                "the verdict-relevant flag signature must differ between StringMode.REFINEMENT and CHAR_ARRAY_MODEL " +
                        "so a cached verdict for one mode is never reused for the other")
    }

    @Test
    fun verdictRelevantFlags_underNone_dependOnMaxStringLength() {
        // Under CHAR_ARRAY_MODEL the length bound is enforced by the bytecode transform (StringLengthBytecode), not a
        // jbmc flag, so two CHAR_ARRAY_MODEL proofs differing only in maxStringLength produce DIFFERENT analysed
        // bytecode. The verdict-cache signature must therefore differ too, or one's verdict would be
        // served for the other (a stale-cache soundness hole).
        val none2 = BmcRequest("pkg.C", "pkg.C.proof", "/cp",
                16, true, 2, "", 0, stringMode = StringMode.CHAR_ARRAY_MODEL)
        val none8 = BmcRequest("pkg.C", "pkg.C.proof", "/cp",
                16, true, 8, "", 0, stringMode = StringMode.CHAR_ARRAY_MODEL)
        assertNotEquals(Jbmc.verdictRelevantFlags(none2), Jbmc.verdictRelevantFlags(none8),
                "under CHAR_ARRAY_MODEL the verdict signature must fold in maxStringLength (it changes the bytecode)")
    }

    @Test
    fun none_realCommand_neverCarriesTheCacheOnlyMarker() {
        // The cache-only length marker must NEVER reach the engine (jbmc rejects a string-length flag
        // with --no-refine-strings); args() builds the real command, so it must be absent there.
        val a = args(StringMode.CHAR_ARRAY_MODEL, maxStringLength = 8)
        assertFalse(a.contains("--bmc-norefine-string-length"),
                "the CHAR_ARRAY_MODEL cache-only length marker must not be passed to jbmc: $a")
    }
}
