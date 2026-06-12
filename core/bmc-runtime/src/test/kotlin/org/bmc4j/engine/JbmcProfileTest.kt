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
    fun pipeline_phases_render_as_a_harness_measured_group_alongside_engine_phases() {
        // An engine-parsed profile (one engine phase + reached SAT) overlaid with bmc4j's OWN
        // harness-measured pipeline pass timings. The render must show BOTH groups and label their
        // provenance so a reader never confuses our prep time with the engine's.
        val engine = JbmcProfile.parse("""
            [
              {"messageText":"Runtime Symex: 0.20s"},
              {"messageText":"Passing problem to propositional reduction"},
              {"messageText":"Runtime Solver: 0.30s"}
            ]""".trimIndent())
        val pipeline = linkedMapOf("desugar" to 0.05, "reachability" to 0.01, "model-slice" to 0.02)
        val p = engine.withHarnessTimings(pipeline, 0.60)

        assertEquals(pipeline, p.pipelineSeconds)
        assertEquals(0.60, p.engineWallSeconds)
        // The engine phases survive the overlay untouched.
        assertEquals(0.20, p.phaseSeconds["Symex"])
        assertTrue(p.reachedSat)

        val rendered = p.render("pkg.Tests.proof", "VERIFIED")
        // Both groups appear, each row carrying its provenance tag.
        assertTrue(rendered.contains("bmc4j pipeline (harness-measured"), "pipeline group header")
        assertTrue(rendered.contains("[bmc4j] desugar"), "a timed pipeline pass, tagged bmc4j-measured")
        assertTrue(rendered.contains("[bmc4j] model-slice"))
        assertTrue(rendered.contains("[engine] Symex"), "an engine phase, tagged engine-reported")
        assertTrue(rendered.contains("[harness] engine wall-clock"), "harness-measured engine wall")
        assertTrue(rendered.contains("reached SAT/SMT solver: YES"))
        // No derived Symex: the engine DID report a phase line, so we never invent one.
        assertNull(p.derivedSymexSeconds())
        // Sub-millisecond timings render as fixed-point, never scientific notation.
        val tiny = engine.withHarnessTimings(mapOf("purity-audit" to 0.0004), 0.6)
        val tinyRendered = tiny.render("pkg.Tests.proof", "VERIFIED")
        assertTrue(tinyRendered.contains("purity-audit"))
        assertFalse(tinyRendered.contains("E-"), "no scientific notation in the rendered timings")
        assertTrue(tinyRendered.contains("<0.001s"), "a sub-ms pass reads as <0.001s")
    }

    @Test
    fun a_symex_timeout_with_no_phase_line_derives_symex_from_the_engine_wall_clock() {
        // The symex-timeout case: jbmc was killed entirely inside symbolic execution, so it emitted
        // unwinding firings but NO `Runtime <Phase>:` line. The harness attributes the whole engine
        // launch->kill wall-clock to a DERIVED `Symex (incomplete)` entry — symex IS the unwinding phase.
        val engine = JbmcProfile.parse("""
            [
              {"messageText":"Unwinding loop java::okio.Buffer.writeUtf8:(Ljava/lang/String;)V.0 iteration 1 file B.java line 7"},
              {"messageText":"Unwinding loop java::okio.Buffer.writeUtf8:(Ljava/lang/String;)V.0 iteration 2 file B.java line 7"}
            ]""".trimIndent())
        assertTrue(engine.phaseSeconds.isEmpty(), "precondition: no engine phase line was emitted")

        val p = engine.withHarnessTimings(mapOf("desugar" to 0.04), 3.90)

        // The derivation is unambiguous: no completed phase + an engine wall-clock => full wall is symex.
        assertEquals(3.90, p.derivedSymexSeconds())
        assertFalse(p.reachedSat)

        val rendered = p.render("okio.Tests.heavy", "TIMEOUT")
        assertTrue(rendered.contains("[harness] Symex (incomplete)"),
                "the derived, harness-measured symex entry replaces the empty phase list")
        assertTrue(rendered.contains("killed inside symbolic execution"), "explains the derivation")
        assertTrue(rendered.contains("reached SAT/SMT solver: NO"))
        // The hot method still surfaces (unwinding was captured up to the kill).
        assertTrue(rendered.contains("okio.Buffer.writeUtf8  x2"))
    }

    @Test
    fun some_phase_lines_but_no_solver_does_not_invent_a_symex_split() {
        // The other timeout shape: the engine reported SOME completed phase(s) but never reached the
        // solver. Those phase times are REAL (jbmc-reported); the missing time is a named missing phase,
        // not a fabricated symex split — so derivedSymexSeconds is null even though an engine wall exists.
        val engine = JbmcProfile.parse("""
            [
              {"messageText":"Runtime Symex: 1.00s"},
              {"messageText":"Runtime Convert SSA: 0.50s"}
            ]""".trimIndent())
        val p = engine.withHarnessTimings(emptyMap(), 4.00)

        assertNull(p.derivedSymexSeconds(), "real phase lines are present => never derive a symex split")
        val rendered = p.render("pkg.Tests.partial", "TIMEOUT")
        assertTrue(rendered.contains("[engine] Symex"))
        assertTrue(rendered.contains("[engine] Convert SSA"))
        assertFalse(rendered.contains("Symex (incomplete)"), "no derived entry when phases were reported")
        assertTrue(rendered.contains("[harness] engine wall-clock"))
    }

    @Test
    fun harness_timings_overlay_is_a_no_op_when_empty_and_does_not_make_a_pipeline_only_profile_empty() {
        val engine = JbmcProfile.parse("[]")
        assertTrue(engine.isEmpty())
        // A no-op overlay returns an equally-empty profile.
        assertTrue(engine.withHarnessTimings(null, null).isEmpty())
        // But a profile carrying ONLY harness timings (no engine data — e.g. the engine streamed nothing
        // before the kill) is NOT empty: the pipeline breakdown is still useful diagnostic output.
        assertFalse(engine.withHarnessTimings(mapOf("desugar" to 0.1), null).isEmpty())
        assertFalse(engine.withHarnessTimings(null, 2.0).isEmpty())
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
