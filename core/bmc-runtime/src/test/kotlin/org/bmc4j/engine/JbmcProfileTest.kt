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
        // A genuine clean completion: the engine wall ~= the sum of the completed phases (Symex 0.20 +
        // Solver 0.30 = 0.50), so there is no unaccounted remainder and no derived in-progress tail.
        val p = engine.withHarnessTimings(pipeline, 0.50)

        assertEquals(pipeline, p.pipelineSeconds)
        assertEquals(0.50, p.engineWallSeconds)
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
        // No derived in-progress phase: the engine reached + reported a completed Solver phase whose time
        // accounts for the wall, so the run is fully accounted - we never invent an incomplete tail.
        assertNull(p.derivedInProgressPhase())
        // Sub-millisecond timings render as fixed-point, never scientific notation.
        val tiny = engine.withHarnessTimings(mapOf("purity-audit" to 0.0004), 0.50)
        val tinyRendered = tiny.render("pkg.Tests.proof", "VERIFIED")
        assertTrue(tinyRendered.contains("purity-audit"))
        assertFalse(tinyRendered.contains("E-"), "no scientific notation in the rendered timings")
        assertTrue(tinyRendered.contains("<0.001s"), "a sub-ms pass reads as <0.001s")
    }

    @Test
    fun a_symex_timeout_with_no_phase_line_derives_symex_from_the_engine_wall_clock() {
        // The symex-bound timeout: jbmc was killed entirely inside symbolic execution. It announced symex
        // (`Starting Bounded Model Checking`) and emitted unwinding firings, but NO `Runtime <Phase>:`
        // line - symex never completed. The harness attributes the whole launch-to-kill wall-clock to a
        // DERIVED `Symex (incomplete)` entry, keyed off the symex entry marker.
        val engine = JbmcProfile.parse("""
            [
              {"messageText":"Starting Bounded Model Checking"},
              {"messageText":"Unwinding loop java::okio.Buffer.writeUtf8:(Ljava/lang/String;)V.0 iteration 1 file B.java line 7"},
              {"messageText":"Unwinding loop java::okio.Buffer.writeUtf8:(Ljava/lang/String;)V.0 iteration 2 file B.java line 7"}
            ]""".trimIndent())
        assertTrue(engine.phaseSeconds.isEmpty(), "precondition: no engine phase line was emitted")
        assertEquals(JbmcProfile.Phase.SYMEX, engine.lastPhaseEntered, "symex was the furthest phase entered")

        val p = engine.withHarnessTimings(mapOf("desugar" to 0.04), 3.90)

        // No completed phase + an engine wall-clock => the full wall is the in-progress symex.
        assertEquals(JbmcProfile.Phase.SYMEX to 3.90, p.derivedInProgressPhase())
        assertFalse(p.reachedSat)

        val rendered = p.render("okio.Tests.heavy", "TIMEOUT")
        assertTrue(rendered.contains("[harness] Symex (incomplete)"),
                "the derived, harness-measured symex entry replaces the empty phase list")
        assertTrue(rendered.contains("killed inside Symex"), "explains the derivation")
        assertTrue(rendered.contains("reached SAT/SMT solver: NO"))
        // The hot method still surfaces (unwinding was captured up to the kill).
        assertTrue(rendered.contains("okio.Buffer.writeUtf8  x2"))
    }

    @Test
    fun a_convert_ssa_bound_timeout_attributes_the_remainder_to_convert_ssa_not_symex() {
        // THE BUG-FIX CASE (a captured-from-real-jbmc shape): symex COMPLETED (`Runtime Symex` emitted),
        // the engine entered Convert SSA (`converting SSA`), and was then killed bit-blasting the equation
        // with no `Runtime Convert SSA` line, never reached the solver. The unaccounted wall-clock
        // (engineWall - Symex) must be attributed to "Convert SSA (incomplete)", NOT dumped onto
        // "Symex (incomplete)". This is the exact misattribution the fix corrects.
        val engine = JbmcProfile.parse("""
            [
              {"messageText":"Starting Bounded Model Checking"},
              {"messageText":"Unwinding loop java::example.timeout.Heavy.quadraticMix:(II)J.0 iteration 1 file Heavy.java line 20"},
              {"messageText":"Runtime Symex: 9.20197s"},
              {"messageText":"size of program expression: 29375 steps"},
              {"messageText":"Generated 69 VCC(s), 66 remaining after simplification"},
              {"messageText":"Runtime Postprocess Equation: 0.0024134s"},
              {"messageText":"converting SSA"}
            ]""".trimIndent())
        assertEquals(JbmcProfile.Phase.CONVERT_SSA, engine.lastPhaseEntered,
                "the last phase entered was Convert SSA (the converting-SSA marker)")
        assertFalse(engine.reachedSat, "no propositional-reduction / SAT line => never reached the solver")
        assertEquals(9.20197, engine.phaseSeconds["Symex"])

        // Engine ran 12s wall; symex (9.20197s) + postprocess (0.0024134s) completed. The remainder
        // (~2.795s) was spent in Convert SSA, where it was killed.
        val p = engine.withHarnessTimings(emptyMap(), 12.0)
        val derived = p.derivedInProgressPhase()
        assertEquals(JbmcProfile.Phase.CONVERT_SSA, derived?.first,
                "the in-progress remainder is attributed to Convert SSA, not Symex")
        val completed = 9.20197 + 0.0024134
        assertEquals(12.0 - completed, derived!!.second, 1e-6, "remainder = wall - completed phases")

        val rendered = p.render("proofs.profiling.heavy", "TIMEOUT")
        assertTrue(rendered.contains("[harness] Convert SSA (incomplete)"),
                "the breakdown shows Convert SSA (incomplete), the phase it was really stuck in")
        assertFalse(rendered.contains("Symex (incomplete)"),
                "it must NOT mislabel the time as Symex (incomplete) - the bug")
        assertTrue(rendered.contains("[engine] Symex"), "the real, completed Symex phase is still shown")
        assertTrue(rendered.contains("killed inside Convert SSA"), "names where the time went")
        assertTrue(rendered.contains("reached SAT/SMT solver: NO"))
    }

    @Test
    fun some_phase_lines_but_no_solver_attributes_the_remainder_to_the_last_phase_entered() {
        // The engine reported a completed phase but never reached the solver, and a later phase entry
        // marker shows where it then went. The completed phase times are REAL; the unaccounted remainder
        // is the in-progress tail (here Convert SSA), derived from the entry marker, not a fabricated
        // split across the completed phases.
        val engine = JbmcProfile.parse("""
            [
              {"messageText":"Starting Bounded Model Checking"},
              {"messageText":"Runtime Symex: 1.00s"},
              {"messageText":"converting SSA"}
            ]""".trimIndent())
        val p = engine.withHarnessTimings(emptyMap(), 4.00)

        val derived = p.derivedInProgressPhase()
        assertEquals(JbmcProfile.Phase.CONVERT_SSA, derived?.first)
        assertEquals(3.00, derived!!.second, 1e-9, "remainder = 4.00 wall - 1.00 completed Symex")
        val rendered = p.render("pkg.Tests.partial", "TIMEOUT")
        assertTrue(rendered.contains("[engine] Symex"), "the real completed Symex phase")
        assertTrue(rendered.contains("[harness] Convert SSA (incomplete)"), "the derived in-progress tail")
        assertTrue(rendered.contains("[harness] engine wall-clock"))
    }

    @Test
    fun a_solver_bound_kill_mid_solve_attributes_the_remainder_to_solver_despite_a_partial_solver_line() {
        // THE BUG-FIX CASE: the engine reached the solver, jbmc printed a PARTIAL `Runtime Solver:` figure
        // (a stale earlier sub-measurement), then was killed STILL inside the solver while kissat churned at
        // 99.9% CPU. jbmc only emits a `Runtime <Phase>:` line when the phase COMPLETES, so the long real
        // solve is NEVER reported. The old guard ("a Solver line was seen => fully accounted") dropped the
        // ~595s of real solving; the remainder-based rule must attribute it to "Solver (incomplete)".
        val engine = JbmcProfile.parse("""
            [
              {"messageText":"Starting Bounded Model Checking"},
              {"messageText":"Runtime Symex: 2.50s"},
              {"messageText":"converting SSA"},
              {"messageText":"Runtime Convert SSA: 2.00s"},
              {"messageText":"Passing problem to propositional reduction"},
              {"messageText":"Runtime Solver: 0.20s"}
            ]""".trimIndent())
        assertTrue(engine.reachedSat, "the propositional-reduction handoff => the solver was reached")
        assertEquals(JbmcProfile.Phase.SOLVER, engine.lastPhaseEntered, "Solver was the furthest phase entered")
        assertEquals(0.20, engine.phaseSeconds["Solver"], "only a PARTIAL Solver figure was reported")

        // Engine ran 600s wall; Symex (2.5) + Convert SSA (2.0) + the partial Solver (0.2) = 4.7s completed.
        // The remaining ~595.3s was real solving the engine was killed inside.
        val p = engine.withHarnessTimings(emptyMap(), 600.0)
        val derived = p.derivedInProgressPhase()
        assertEquals(JbmcProfile.Phase.SOLVER, derived?.first,
                "the unaccounted remainder is attributed to Solver, not dropped on the floor")
        assertEquals(600.0 - (2.50 + 2.00 + 0.20), derived!!.second, 1e-6,
                "remainder = wall - (Symex + Convert SSA + partial Solver)")

        val rendered = p.render("proofs.profiling.solverBound", "TIMEOUT")
        assertTrue(rendered.contains("[harness] Solver (incomplete)"),
                "the ~595s of real solving surfaces as Solver (incomplete), not vanished")
        assertTrue(rendered.contains("[engine] Solver"), "the partial engine Solver figure is still shown")
        assertTrue(rendered.contains("STILL inside Solver"),
                "the note explains a partial Solver line + a kill still inside the solver")
        assertTrue(rendered.contains("reached SAT/SMT solver: YES"))
    }

    @Test
    fun a_cleanly_solved_run_derives_no_in_progress_tail_even_with_an_engine_wall_clock() {
        // The clean-completion guard: the solver genuinely FINISHED, so its `Runtime Solver:` line carries
        // the FULL solve time and the phases sum to ~the wall. The remainder is below the floor, so there is
        // NO spurious "Solver (incomplete)" tail - the fix must not regress this into a double-count.
        val engine = JbmcProfile.parse("""
            [
              {"messageText":"Runtime Symex: 0.20s"},
              {"messageText":"Passing problem to propositional reduction"},
              {"messageText":"Runtime Convert SSA: 0.10s"},
              {"messageText":"Runtime Solver: 1.50s"}
            ]""".trimIndent())
        // wall ~= sum of phases (0.20 + 0.10 + 1.50 = 1.80), only 0.02s of frame overhead, below the floor.
        val p = engine.withHarnessTimings(emptyMap(), 1.82)
        assertNull(p.derivedInProgressPhase(),
                "a finished solver reports its full time => phases ~= wall => no derived tail")
        val rendered = p.render("pkg.Tests.solved", "VERIFIED")
        assertFalse(rendered.contains("(incomplete)"), "no phantom in-progress tail on a clean solve")
    }

    @Test
    fun a_remainder_at_or_below_the_floor_is_not_attributed_to_an_in_progress_phase() {
        // When the completed phases account for ~all the wall-clock (the kill landed at a phase boundary,
        // or the run finished), there is no meaningful in-progress tail to derive: derivedInProgressPhase
        // is null even though an engine wall and a phase marker exist.
        val engine = JbmcProfile.parse("""
            [
              {"messageText":"Starting Bounded Model Checking"},
              {"messageText":"Runtime Symex: 3.98s"}
            ]""".trimIndent())
        val p = engine.withHarnessTimings(emptyMap(), 4.00) // only 0.02s unaccounted, below the floor
        assertNull(p.derivedInProgressPhase(), "a sub-floor remainder is noise, not an in-progress phase")
        val rendered = p.render("pkg.Tests.tight", "TIMEOUT")
        assertFalse(rendered.contains("(incomplete)"), "no derived entry for a negligible remainder")
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
    fun domain_split_renders_a_labeled_block_per_run_plus_a_concurrent_critical_path_aggregate() {
        // A domainSplit fan-out with @BmcProfile: 1 cover + 2 slices, each its own engine run with its own
        // parsed profile. The per-run render must emit one self-labeled block per derived run; the
        // aggregate must tally verdicts and name the PARALLEL critical path (MAX engine wall-clock, NOT the
        // sum) with the long-pole slice + its dominant phase.
        fun solved(symex: Double, solver: Double, wall: Double): JbmcProfile =
                JbmcProfile.parse("""
                    [
                      {"messageText":"Runtime Symex: ${symex}s"},
                      {"messageText":"Passing problem to propositional reduction"},
                      {"messageText":"Runtime Solver: ${solver}s"}
                    ]""".trimIndent()).withHarnessTimings(null, wall)

        val cover = JbmcProfile.LabeledRun("cover", "VERIFIED", solved(0.10, 0.20, 0.4))
        val slice1 = JbmcProfile.LabeledRun("slice 1/2", "VERIFIED", solved(0.50, 0.30, 1.0))
        // slice 2/2 is the long pole: biggest engine wall-clock (5.0s), dominated by Solver (4.0s).
        val slice2 = JbmcProfile.LabeledRun("slice 2/2", "VERIFIED", solved(0.80, 4.00, 5.0))
        val runs = listOf(cover, slice1, slice2)

        val perRun = JbmcProfile.renderRunProfiles("pkg.Tests.split", runs)
        // One self-labeled profile block per derived run.
        assertTrue(perRun.contains("cover: pkg.Tests.split -> VERIFIED - performance breakdown"))
        assertTrue(perRun.contains("slice 1/2: pkg.Tests.split -> VERIFIED - performance breakdown"))
        assertTrue(perRun.contains("slice 2/2: pkg.Tests.split -> VERIFIED - performance breakdown"))
        // The blocks carry the same per-run content a normal @BmcProfile emits.
        assertTrue(perRun.contains("reached SAT/SMT solver: YES"))

        val aggregate = JbmcProfile.renderAggregate("pkg.Tests.split", runs)
        // Per-slice verdict tally.
        assertTrue(aggregate.contains("verdict tally: 3x VERIFIED"))
        // Parallel critical path: MAX wall (5.0s) is the long pole = slice 2/2, NOT the 6.4s sum.
        assertTrue(aggregate.contains("MAX engine wall-clock"))
        assertTrue(aggregate.contains("long pole: slice 2/2"))
        assertTrue(aggregate.contains("5.000s"))
        assertFalse(aggregate.contains("6.4"), "the critical path is the MAX wall, never the sum")
        // The phase that dominated the long pole (Solver 4.0s beats its Symex 0.8s).
        assertTrue(aggregate.contains("dominated by phase: Solver"))
        assertTrue(aggregate.contains("4.000s"))
    }

    @Test
    fun domain_split_aggregate_with_no_engine_wall_clock_says_so_and_an_unprofiled_run_gets_a_note() {
        // A run that produced no profilable output (cancelled by early-exit, or unprofiled): its per-run
        // block degrades to a one-line note, and if NO run recorded an engine wall-clock the aggregate
        // says the critical path is unavailable rather than inventing one.
        val runs = listOf(
                JbmcProfile.LabeledRun("cover", "UNKNOWN", null),
                JbmcProfile.LabeledRun("slice 1/1", "REFUTED", null))

        val perRun = JbmcProfile.renderRunProfiles("pkg.Tests.split", runs)
        assertTrue(perRun.contains("cover: pkg.Tests.split -> UNKNOWN - no engine"))
        assertTrue(perRun.contains("slice 1/1: pkg.Tests.split -> REFUTED - no engine"))

        val aggregate = JbmcProfile.renderAggregate("pkg.Tests.split", runs)
        assertTrue(aggregate.contains("verdict tally: 1x UNKNOWN, 1x REFUTED"))
        assertTrue(aggregate.contains("no engine wall-clock recorded"))
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

    @Test
    fun loop_unwind_bound_is_the_deepest_single_unwind_not_the_firing_total() {
        // The SAME loop reached in TWO encounters: first to depth 3 (iterations 1,2,3), then to depth 2
        // (iterations 1,2). That's 5 firings but the loop only needs bound 3 per encounter. The old code
        // suggested `bound = firings` (5) -> pinning it over-unwinds every encounter and bloats the formula.
        val json = """
            [
              {"messageText":"Unwinding loop java::pkg.C.scan:()V.0 iteration 1 file C.java line 9"},
              {"messageText":"Unwinding loop java::pkg.C.scan:()V.0 iteration 2 file C.java line 9"},
              {"messageText":"Unwinding loop java::pkg.C.scan:()V.0 iteration 3 file C.java line 9"},
              {"messageText":"Unwinding loop java::pkg.C.scan:()V.0 iteration 1 file C.java line 9"},
              {"messageText":"Unwinding loop java::pkg.C.scan:()V.0 iteration 2 file C.java line 9"}
            ]""".trimIndent()
        val loop = JbmcProfile.parse(json).unwindingLoops.single()
        assertEquals(5, loop.iterations, "firings is the total across encounters")
        assertEquals(3, loop.maxDepth, "maxDepth is the deepest single unwind")
        assertEquals("@LoopUnwind(loop = \"java::pkg.C.scan:()V.0\", bound = 3)", loop.suggestion(),
                "the pin bound is the per-encounter depth (3), NOT the firing total (5)")
        val rendered = JbmcProfile.parse(json).render("pkg.Tests.scan", "TIMEOUT")
        assertTrue(rendered.contains("x5 firings, deepest 3"),
                "the display shows firings and the deepest depth: $rendered")
    }

    @Test
    fun reconstructs_the_call_path_to_a_recursive_method() {
        // The live BMC-at stream drives the shadow stack (outer -> mid -> fib); the recursion firing then
        // snapshots it, so a recursive method shows where it was driven from, not just a count.
        val json = """
            [
              {"messageText":"BMC at file R.java line 2 function java::pkg.R.outer:(I)I (depth 5)"},
              {"messageText":"BMC at file R.java line 7 function java::pkg.R.mid:(I)I bytecode-index 1 (depth 9)"},
              {"messageText":"BMC at file R.java line 12 function java::pkg.R.fib:(I)I bytecode-index 1 (depth 14)"},
              {"messageText":"Unwinding recursion java::pkg.R.fib:(I)I iteration 1 ..."}
            ]""".trimIndent()
        val p = JbmcProfile.parse(json)
        val fib = p.recursionByMethod.single()
        assertEquals(listOf("pkg.R.outer", "pkg.R.mid", "pkg.R.fib"), fib.callPath,
                "the call path to the recursive method is reconstructed, outermost first")
        val rendered = p.render("pkg.Tests.r", "TIMEOUT")
        assertTrue(rendered.contains("reached via pkg.R.outer > pkg.R.mid > pkg.R.fib"),
                "the recursion call path is rendered under the entry: $rendered")
    }

    // --- Targetable loop ids + @LoopUnwind suggestions (the "annotation output") ------------------

    @Test
    fun captures_the_full_targetable_loop_id_with_file_line_and_iterations() {
        // Two distinct loops in the SAME method (`.0` and `.1`) must surface as two separate targetable
        // ids — the trailing `.N` is part of the id @LoopUnwind takes, not stripped to the method.
        val json = """
            [
              {"messageText":"Unwinding loop java::okio.Buffer.readDecimalLong:()J.0 iteration 1 file Buffer.java line 41"},
              {"messageText":"Unwinding loop java::okio.Buffer.readDecimalLong:()J.0 iteration 2 file Buffer.java line 41"},
              {"messageText":"Unwinding loop java::okio.Buffer.readDecimalLong:()J.1 iteration 1 file Buffer.java line 55"}
            ]""".trimIndent()
        val p = JbmcProfile.parse(json)

        // Highest-iteration first: .0 (2x) before .1 (1x). The id keeps its `.N`, file/line captured.
        assertEquals(2, p.unwindingLoops.size)
        val hot = p.unwindingLoops[0]
        assertEquals("java::okio.Buffer.readDecimalLong:()J.0", hot.loopId,
                "the FULL --unwindset-form id (with trailing .N) is preserved")
        assertEquals("Buffer.java", hot.file)
        assertEquals(41, hot.line)
        assertEquals(2, hot.iterations)
        assertEquals("java::okio.Buffer.readDecimalLong:()J.1", p.unwindingLoops[1].loopId)
        assertEquals(55, p.unwindingLoops[1].line)
    }

    @Test
    fun the_loop_id_matches_the_parsers_unwindset_form() {
        // The id @LoopUnwind takes MUST equal what JbmcOutputParser recovers from an unwinding property,
        // so a pin authored from the profile output targets the exact loop the engine reports/accepts.
        val func = "java::pkg.Tests.proof:()V"
        val profileJson =
                """[{"messageText":"Unwinding loop $func.3 iteration 1 file T.java line 9"}]"""
        val profileId = JbmcProfile.parse(profileJson).unwindingLoops.single().loopId

        val parserJson = """
            [
              {"result":[
                {"name":"u","status":"FAILURE","property":"$func.unwind.3",
                 "description":"unwinding assertion loop 3",
                 "sourceLocation":{"file":"T.java","line":"9","function":"$func"}},
                {"name":"m","status":"FAILURE","description":"assertion ...",
                 "sourceLocation":{"file":"V.java","line":"${BmcReachability.SENTINEL_LINE}","function":"$func"}}
              ]},
              {"cProverStatus":"failure"}
            ]""".trimIndent()
        val parserId = JbmcOutputParser.parse(parserJson, "pkg.Tests.proof").unwindingLoops.single().loopId

        assertEquals(parserId, profileId,
                "the profile's targetable id must equal the parser's --unwindset id form")
    }

    @Test
    fun renders_a_copy_pasteable_loop_unwind_suggestion_per_loop() {
        // Two distinct loops so we can assert the pins come out as ONE contiguous block.
        val json = """
            [
              {"messageText":"Unwinding loop java::okio.Buffer.readDecimalLong:()J.0 iteration 1 file Buffer.java line 41"},
              {"messageText":"Unwinding loop java::okio.Buffer.readDecimalLong:()J.0 iteration 2 file Buffer.java line 41"},
              {"messageText":"Unwinding loop java::okio.Buffer.writeUtf8:(Ljava/lang/String;)V.0 iteration 1 file Buffer.java line 88"}
            ]""".trimIndent()
        val p = JbmcProfile.parse(json)

        // The per-loop object renders the exact annotation line.
        val decimalPin = "@LoopUnwind(loop = \"java::okio.Buffer.readDecimalLong:()J.0\", bound = 2)"
        val utf8Pin = "@LoopUnwind(loop = \"java::okio.Buffer.writeUtf8:(Ljava/lang/String;)V.0\", bound = 1)"

        val rendered = p.render("okio.Tests.decimal", "TIMEOUT")
        assertTrue(rendered.contains("targetable loops:"),
                "the targetable-loops section is present: $rendered")
        assertTrue(rendered.contains("java::okio.Buffer.readDecimalLong:()J.0  x2 firings  (Buffer.java:41)"),
                "the full id + firings + location is shown: $rendered")
        assertTrue(rendered.contains("only loops that unwound in THIS run"),
                "the partial-list caveat is noted: $rendered")

        // The pins must be FLUSH-LEFT and UNTAGGED — whole render lines equal to the bare annotation,
        // no `  bmc4j[profile]:` prefix or indentation — so a terminal copy pastes straight into source.
        val lines = rendered.lines()
        assertTrue(lines.contains(decimalPin), "decimal pin is a flush-left, untagged line: $rendered")
        assertTrue(lines.contains(utf8Pin), "utf8 pin is a flush-left, untagged line: $rendered")
        // ...and they form ONE CONTIGUOUS block: every pin sits in a single run of untagged lines, so
        // the user can select the whole block at once. The pins are the only untagged (non-prefixed,
        // non-blank) lines, and they must be adjacent.
        val pinIdx = lines.indices.filter { lines[it] == decimalPin || lines[it] == utf8Pin }
        assertEquals(2, pinIdx.size, "both pins present once: $rendered")
        assertEquals(pinIdx[0] + 1, pinIdx[1],
                "the @LoopUnwind pins are emitted as one contiguous block: $rendered")
    }

    @Test
    fun reconstructs_the_call_path_to_each_unwound_loop() {
        // Live symex stream: outer -> mid -> inner (loop fires), return to mid, then mid -> other (loop
        // fires). The `BMC at ... function ...` lines drive the shadow call stack; the unwind lines snapshot
        // it. Reconstruction is heuristic but exact for this non-recursive shape.
        val json = """
            [
              {"messageText":"BMC at file Foo.java line 2 function java::Foo.outer:(I)I (depth 5)"},
              {"messageText":"BMC at file Foo.java line 3 function java::Foo.mid:(I)I bytecode-index 1 (depth 12)"},
              {"messageText":"BMC at file Foo.java line 4 function java::Foo.inner:(I)I bytecode-index 1 (depth 15)"},
              {"messageText":"Unwinding loop java::Foo.inner:(I)I.0 iteration 1 file Foo.java line 4 function java::Foo.inner:(I)I bytecode-index 12 thread 0"},
              {"messageText":"BMC at file Foo.java line 3 function java::Foo.mid:(I)I bytecode-index 4 (depth 30)"},
              {"messageText":"BMC at file Foo.java line 9 function java::Foo.other:(I)I bytecode-index 1 (depth 33)"},
              {"messageText":"Unwinding loop java::Foo.other:(I)I.0 iteration 1 file Foo.java line 9 function java::Foo.other:(I)I bytecode-index 12 thread 0"}
            ]""".trimIndent()
        val p = JbmcProfile.parse(json)

        val inner = p.unwindingLoops.single { it.loopId == "java::Foo.inner:(I)I.0" }
        val other = p.unwindingLoops.single { it.loopId == "java::Foo.other:(I)I.0" }
        assertEquals(listOf("Foo.outer", "Foo.mid", "Foo.inner"), inner.callPath,
                "the call path to the inner loop is reconstructed, outermost first")
        // After inner returns (the function field reverts to mid), the inner frame is popped, so the later
        // 'other' call is NOT nested under inner.
        assertEquals(listOf("Foo.outer", "Foo.mid", "Foo.other"), other.callPath,
                "reverting to an ancestor pops the returned frame before the next call")

        val rendered = p.render("Foo.proof", "TIMEOUT")
        assertTrue(rendered.contains("reached via Foo.outer > Foo.mid > Foo.inner"),
                "the reconstructed call path is rendered under the loop: $rendered")
    }

    @Test
    fun no_loops_means_no_targetable_loops_section() {
        val json = """[{"messageText":"Runtime Solver: 1.0s"}]"""
        val rendered = JbmcProfile.parse(json).render("pkg.Tests.proof", "VERIFIED")
        assertFalse(rendered.contains("targetable loops"),
                "a run with no observed loops emits no targetable-loops section: $rendered")
    }
}
