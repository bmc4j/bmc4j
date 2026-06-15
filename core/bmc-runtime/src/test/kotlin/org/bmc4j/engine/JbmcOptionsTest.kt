package org.bmc4j.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for the raw `@JbmcOptions` passthrough: its whitespace-tokenized arguments are appended
 * verbatim to the jbmc command ([Jbmc.args]), and the raw options string is part of the verdict-cache
 * identity ([VerdictCache.computeKey]) so setting or changing it forces a fresh engine run while the
 * empty default keys identically to a proof with no `@JbmcOptions`.
 */
internal class JbmcOptionsTest {

    private fun request(jbmcOptions: String): BmcRequest =
            BmcRequest("pkg.C", "pkg.C.proof", "/cp", 16, true, 16, "", 0,
                    jbmcOptions = jbmcOptions)

    @Test
    fun args_appendOptionTokens_inOrder() {
        val a = Jbmc.args("pkg.T", "pkg.T.proof", "/cp", 1, true, 16, null, "",
                org.bmc4j.StringMode.REFINEMENT, "--object-bits 12 --no-built-in-assertions")
        assertTrue(a.containsAll(listOf("--object-bits", "12", "--no-built-in-assertions")),
                "every @JbmcOptions token must appear in the argv: $a")
        // tokens ride together at the end, in source order
        val i = a.indexOf("--object-bits")
        assertTrue(i >= 0 && a[i + 1] == "12" && a[i + 2] == "--no-built-in-assertions",
                "@JbmcOptions tokens must be appended in order: $a")
    }

    @Test
    fun args_collapseRunsOfWhitespace_andIgnoreEmpty() {
        val a = Jbmc.args("pkg.T", "pkg.T.proof", "/cp", 1, true, 16, null, "",
                org.bmc4j.StringMode.REFINEMENT, "  --a   --b  ")
        assertTrue(a.contains("--a") && a.contains("--b"), a.toString())
        assertFalse(a.contains(""), "no empty token may be produced from runs of whitespace: $a")
    }

    @Test
    fun args_emptyOptions_addNothing() {
        val withOpts = Jbmc.args("pkg.T", "pkg.T.proof", "/cp", 1, true, 16, null, "",
                org.bmc4j.StringMode.REFINEMENT, "")
        val noOpts = Jbmc.args("pkg.T", "pkg.T.proof", "/cp", 1, true, 16, null, "",
                org.bmc4j.StringMode.REFINEMENT)
        assertEquals(noOpts, withOpts, "empty @JbmcOptions must not change the command")
    }

    @Test
    fun cacheKey_absentOptions_keysIdenticallyToNoAnnotation() {
        // The empty default must produce the byte-identical key a proof with no @JbmcOptions would.
        assertEquals(VerdictCache.computeKey(request(""), "engine-id"),
                VerdictCache.computeKey(request(""), "engine-id"),
                "an absent @JbmcOptions must not perturb the verdict-cache key")
    }

    @Test
    fun cacheKey_changesWhenOptionsSet() {
        assertNotEquals(VerdictCache.computeKey(request(""), "engine-id"),
                VerdictCache.computeKey(request("--object-bits 12"), "engine-id"),
                "setting @JbmcOptions must bust the verdict cache (force a fresh engine run)")
    }

    @Test
    fun cacheKey_changesWhenOptionsChange() {
        assertNotEquals(VerdictCache.computeKey(request("--object-bits 12"), "engine-id"),
                VerdictCache.computeKey(request("--object-bits 16"), "engine-id"),
                "changing @JbmcOptions must bust the verdict cache")
    }
}
