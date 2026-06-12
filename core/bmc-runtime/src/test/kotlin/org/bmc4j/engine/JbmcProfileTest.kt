package org.bmc4j.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files

/**
 * Pins [JbmcProfile]'s parsing of the verbose `--verbosity 10` STATUS-MESSAGE stream — the per-stage
 * performance breakdown behind `@BmcProfile`. The message FORMATS are not an engine contract; this
 * test is what pins them against the bundled engine, the same discipline [JbmcOutputParserTest] uses
 * for the opaque-symbol / unwinding markers. Covers a fully-solved run, a symex-bound timeout (the
 * "never reached SAT" case — the headline signal), and a truncated stream (a timeout kill mid-write).
 */
internal class JbmcProfileTest {

    @Test
    fun parses_phases_unwinding_formula_and_reached_sat_on_a_solved_run() {
        val json = """
            [
              {"messageType":"STATUS-MESSAGE","messageText":"Unwinding loop java::pkg.Buffer.writeUtf8:(Ljava/lang/String;)V.0 iteration 1 file Buffer.java line 10"},
              {"messageType":"STATUS-MESSAGE","messageText":"Unwinding loop java::pkg.Buffer.writeUtf8:(Ljava/lang/String;)V.0 iteration 2 file Buffer.java line 10"},
              {"messageType":"STATUS-MESSAGE","messageText":"Unwinding loop java::pkg.Buffer.writeUtf8:(Ljava/lang/String;)V.0 iteration 3 file Buffer.java line 10"},
              {"messageType":"STATUS-MESSAGE","messageText":"Unwinding loop java::pkg.Other.scan:()V.0 iteration 1 file Other.java line 5"},
              {"messageType":"STATUS-MESSAGE","messageText":"size of program expression: 12345 steps"},
              {"messageType":"STATUS-MESSAGE","messageText":"Runtime Symex: 0.42s"},
              {"messageType":"STATUS-MESSAGE","messageText":"Generated 88 VCC(s), 17 remaining after simplification"},
              {"messageType":"STATUS-MESSAGE","messageText":"Passing problem to propositional reduction"},
              {"messageType":"STATUS-MESSAGE","messageText":"Runtime Convert SSA: 0.10s"},
              {"messageType":"STATUS-MESSAGE","messageText":"23456 variables, 78901 clauses"},
              {"messageType":"STATUS-MESSAGE","messageText":"Runtime Solver: 1.50s"}
            ]""".trimIndent()

        val p = JbmcProfile.parse(json)

        assertTrue(p.reachedSat, "the propositional-reduction marker + SAT size line => reached SAT")
        assertEquals(12345L, p.programSteps)
        assertEquals(88L, p.vccGenerated)
        assertEquals(17L, p.vccRemaining)
        assertEquals(23456L, p.satVariables)
        assertEquals(78901L, p.satClauses)
        // Phases captured in order.
        assertEquals(0.42, p.phaseSeconds["Symex"])
        assertEquals(0.10, p.phaseSeconds["Convert SSA"])
        assertEquals(1.50, p.phaseSeconds["Solver"])
        // Top offender first: writeUtf8 unwound 3x beats Other.scan 1x; methods rendered dot-form.
        assertEquals("pkg.Buffer.writeUtf8", p.unwindingByMethod[0].method)
        assertEquals(3, p.unwindingByMethod[0].count)
        assertEquals("pkg.Other.scan", p.unwindingByMethod[1].method)
        assertEquals(1, p.unwindingByMethod[1].count)

        val rendered = p.render("pkg.Tests.proof", "VERIFIED")
        assertTrue(rendered.contains("reached SAT/SMT solver: YES"))
        assertTrue(rendered.contains("pkg.Buffer.writeUtf8  x3"))
        assertTrue(rendered.contains("23456 variables, 78901 clauses"))
    }

    @Test
    fun a_symex_bound_run_reports_never_reached_sat_and_the_hot_method() {
        // No "Passing problem to propositional reduction", no SAT size line, no Solver phase: the
        // engine never got past symbolic execution. This is the headline diagnostic for a timeout.
        val sb = StringBuilder("[\n")
        sb.append("""{"messageText":"Runtime Symex: 5.00s"},""").append('\n')
        // 465 unwinding firings of one hot method.
        for (i in 1..465) {
            sb.append("""{"messageText":"Unwinding loop java::okio.Buffer.writeUtf8:""")
                    .append("""(Ljava/lang/String;)V.0 iteration """).append(i)
                    .append(" file Buffer.java line 7\"}")
            sb.append(if (i == 465) "\n" else ",\n")
        }
        sb.append("]")

        val p = JbmcProfile.parse(sb.toString())

        assertFalse(p.reachedSat, "no propositional-reduction marker / SAT line => never reached SAT")
        assertNull(p.satVariables)
        assertEquals("okio.Buffer.writeUtf8", p.unwindingByMethod.single().method)
        assertEquals(465, p.unwindingByMethod.single().count)

        val rendered = p.render("okio.Tests.heavy", "TIMEOUT")
        assertTrue(rendered.contains("reached SAT/SMT solver: NO"))
        assertTrue(rendered.contains("okio.Buffer.writeUtf8  x465"))
        assertTrue(rendered.contains("BEFORE solving"), "names where the time went")
    }

    @Test
    fun streaming_file_parse_tolerates_a_truncated_stream_from_a_timeout_kill() {
        // A stream cut off mid-write (the engine was force-killed): the array is never closed and the
        // last object is incomplete. The profiler must use whatever well-formed elements it read.
        val truncated = """
            [
              {"messageText":"Runtime Symex: 2.00s"},
              {"messageText":"Unwinding loop java::pkg.Hot.loop:()V.0 iteration 1 file H.java line 3"},
              {"messageText":"Unwinding loop java::pkg.Hot.loop:()V.0 iteration 2 file H.java line 3"},
              {"messageText":"size of program expression: 999 steps"},
              {"messageText":"Unwinding loop java::pkg.Hot.loop:()V.0 iteration 3 file H.ja"""
                .trimIndent()
        val file = Files.createTempFile("bmc4j-profile", ".json").toFile()
        try {
            file.writeText(truncated, StandardCharsets.UTF_8)
            val p = JbmcProfile.parse(file)
            // The well-formed prefix is used; the truncated trailing object is ignored, never thrown.
            assertEquals(2.00, p.phaseSeconds["Symex"])
            assertEquals(999L, p.programSteps)
            assertFalse(p.reachedSat)
            assertEquals("pkg.Hot.loop", p.unwindingByMethod.single().method)
            // The 3rd (truncated) firing didn't complete as a JSON object, so 2 well-formed ones count.
            assertEquals(2, p.unwindingByMethod.single().count)
        } finally {
            file.delete()
        }
    }

    @Test
    fun an_empty_or_garbage_stream_yields_an_empty_profile_never_throws() {
        assertTrue(JbmcProfile.parse("not the json-ui array {{{").isEmpty())
        assertTrue(JbmcProfile.parse("[]").isEmpty())
        val empty = Files.createTempFile("bmc4j-profile", ".txt").toFile()
        try {
            assertTrue(JbmcProfile.parse(empty).isEmpty())
        } finally {
            empty.delete()
        }
    }

    @Test
    fun recursion_unwinding_is_tallied_separately() {
        val json = """
            [
              {"messageText":"Unwinding recursion java::pkg.R.fib:(I)I iteration 1 ..."},
              {"messageText":"Unwinding recursion java::pkg.R.fib:(I)I iteration 2 ..."}
            ]""".trimIndent()
        val p = JbmcProfile.parse(json)
        assertTrue(p.unwindingByMethod.isEmpty())
        assertEquals("pkg.R.fib", p.recursionByMethod.single().method)
        assertEquals(2, p.recursionByMethod.single().count)
    }
}
