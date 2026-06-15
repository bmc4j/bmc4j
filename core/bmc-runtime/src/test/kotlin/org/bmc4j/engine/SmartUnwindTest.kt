package org.bmc4j.engine

import org.bmc4j.StringMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for [SmartUnwind] — the pure per-loop unwinding climb — driven by a stub `runAt` so it
 * needs no engine. Pins the contract: discover every under-bounded loop in ONE run via the unwinding
 * loops the result carries, raise ONLY those loops, never touch a loop that already covers, land on the
 * first conclusive verdict, and hard-cap (per-loop bound + round count) so a symbolic-guard loop fails
 * closed to UNKNOWN instead of looping forever. Also covers the `--unwindset` arg assembly + loop-id
 * parsing the climb feeds the engine.
 */
internal class SmartUnwindTest {

    private fun verified() = JbmcResult(true, emptyList(), null)
    private fun refuted() = JbmcResult(false, emptyList(), null)
    private fun timedOut() = JbmcResult.unknownTimeout("out of time", null)

    /** An UNWINDING_ASSERTION result whose firing loops carry the given engine loop ids. */
    private fun tooSmallAt(vararg loopIds: String): JbmcResult =
            JbmcResult.unknown(UnknownKind.UNWINDING_ASSERTION, "bound too small", null)
                    .withUnwindingLoops(loopIds.map { id ->
                        JbmcResult.UnwindingLoop("pkg.Cls.m", "Cls.java", 7, recursion = false, loopId = id)
                    })

    /** An UNWINDING_ASSERTION result for a RECURSION overrun (no targetable loop id). */
    private fun recursionTooSmall(): JbmcResult =
            JbmcResult.unknown(UnknownKind.UNWINDING_ASSERTION, "recursion bound too small", null)
                    .withUnwindingLoops(listOf(
                            JbmcResult.UnwindingLoop("pkg.R.fib", "R.java", 3, recursion = true, loopId = null)))

    @Test
    fun raises_only_the_under_bounded_loop_and_lands_conclusive() {
        // Two loops; loop A is fine at the base, loop B under-bounds and needs raising. The climb must
        // raise ONLY B (A stays absent from the unwindset = runs at the base), then VERIFY.
        val seen = mutableListOf<Map<String, Int>>()
        val out = SmartUnwind.climb(base = 2, cap = 16) { us ->
            seen.add(us.toSortedMap())
            // Under-bounded until B's own bound reaches 4.
            if ((us["pkg.Cls.m.1"] ?: 2) >= 4) verified() else tooSmallAt("pkg.Cls.m.1")
        }
        assertTrue(out.result.isVerified)
        assertTrue(out.discovered)
        // ONLY loop B was raised; loop A never appears (it covers at the base bound).
        assertEquals(mapOf("pkg.Cls.m.1" to 4), out.unwindSet, "only the firing loop is raised")
        // Round 1 ran with an empty unwindset; round 2 with just B=4. A was never bumped.
        assertEquals(listOf(emptyMap(), mapOf("pkg.Cls.m.1" to 4)), seen)
        assertEquals(2, out.rounds)
    }

    @Test
    fun raises_multiple_firing_loops_independently_in_one_round() {
        // Two loops fire in the SAME run (the whole point of --unwinding-assertions: discover all at once).
        // Both get raised in the next round; a third loop never fires so it is never touched.
        val out = SmartUnwind.climb(base = 1, cap = 16) { us ->
            val a = us["f.0"] ?: 1
            val b = us["f.1"] ?: 1
            when {
                a < 2 || b < 2 -> tooSmallAt("f.0", "f.1") // both under-bounded
                else -> verified()
            }
        }
        assertTrue(out.result.isVerified)
        assertEquals(mapOf("f.0" to 2, "f.1" to 2), out.unwindSet.toSortedMap())
        assertEquals(2, out.rounds, "both loops raised together in one round, then converge")
    }

    @Test
    fun symbolic_loop_caps_per_loop_bound_and_stops_not_forever() {
        // The crux: a single loop fires at EVERY finite bound (symbolic trip count). The per-loop bound
        // must cap and the climb must STOP with the last UNKNOWN, never loop forever.
        var calls = 0
        val out = SmartUnwind.climb(base = 1, cap = 8, step = 2, maxRounds = 100) { _ ->
            calls++
            tooSmallAt("f.0") // never converges
        }
        assertTrue(out.result.isUnknown)
        assertFalse(out.discovered)
        assertEquals(UnknownKind.UNWINDING_ASSERTION, out.result.undecidedKind)
        // 1 -> 2 -> 4 -> 8 (cap), then one more round confirms no raise is possible and stops. Bounded.
        assertEquals(8, out.unwindSet["f.0"], "the loop's bound is capped at the hard ceiling")
        assertTrue(calls <= 6, "the climb terminates in a bounded number of rounds, got $calls")
    }

    @Test
    fun round_budget_terminates_the_climb() {
        // Even before the per-loop cap, the hard round budget must stop the climb (belt-and-suspenders
        // against a never-converging loop). With maxRounds=3 the engine runs at most 3 times.
        var calls = 0
        val out = SmartUnwind.climb(base = 1, cap = 1024, step = 2, maxRounds = 3) { _ ->
            calls++
            tooSmallAt("f.0")
        }
        assertTrue(out.result.isUnknown)
        assertFalse(out.discovered)
        assertEquals(3, calls, "no more than maxRounds engine runs")
        assertEquals(3, out.rounds)
    }

    @Test
    fun a_recursion_overrun_has_no_loop_id_so_the_climb_stops_immediately() {
        // A recursion overrun carries no targetable loop id; --unwindset cannot raise it, so the climb
        // makes no progress and STOPS on the first UNKNOWN rather than spinning.
        var calls = 0
        val out = SmartUnwind.climb(base = 1, cap = 16) { _ -> calls++; recursionTooSmall() }
        assertTrue(out.result.isUnknown)
        assertFalse(out.discovered)
        assertEquals(1, calls, "no targetable loop -> no progress -> stop after one run")
        assertTrue(out.unwindSet.isEmpty(), "nothing was raised")
    }

    @Test
    fun a_rung_that_falls_over_stops_with_that_unknown() {
        // A non-unwinding UNKNOWN (timeout) won't be fixed by a bigger bound: surface it, don't climb.
        var calls = 0
        val out = SmartUnwind.climb(base = 1, cap = 16) { us ->
            calls++
            if (us.isEmpty()) tooSmallAt("f.0") else timedOut()
        }
        assertTrue(out.result.isUnknown)
        assertEquals(UnknownKind.TIMEOUT, out.result.undecidedKind)
        assertFalse(out.discovered)
        assertEquals(2, calls)
    }

    @Test
    fun a_conclusive_first_round_needs_no_unwindset() {
        // Every loop already covers at the base bound: the very first (empty-unwindset) run is conclusive.
        val seen = mutableListOf<Map<String, Int>>()
        val out = SmartUnwind.climb(base = 4, cap = 16) { us -> seen.add(us); verified() }
        assertTrue(out.result.isVerified)
        assertTrue(out.discovered)
        assertTrue(out.unwindSet.isEmpty(), "no loop needed raising")
        assertEquals(listOf(emptyMap<String, Int>()), seen, "one run, empty unwindset")
    }

    @Test
    fun refuted_within_the_bound_is_surfaced_not_climbed() {
        // A real counterexample within the per-loop bounds is a refutation; the climb lands on it.
        val out = SmartUnwind.climb(base = 1, cap = 16) { us ->
            if (us.isEmpty()) tooSmallAt("f.0") else refuted()
        }
        assertFalse(out.result.isVerified)
        assertFalse(out.result.isUnknown)
        assertTrue(out.discovered)
    }

    // --- --unwindset arg assembly + loop-id parsing -------------------------------------------------

    @Test
    fun args_emit_one_unwindset_flag_per_loop_in_stable_order() {
        // The per-loop overrides become `--unwindset <loopId>:<bound>` args, sorted by loop id so the
        // command (and the verdict-cache signature derived from the same builder) is deterministic.
        val a = Jbmc.args("pkg.T", "pkg.T.proof", "/cp",
                /*unwind=*/2, /*unwindingAssertions=*/true, /*maxStringLength=*/0, /*solver=*/null,
                /*externalSatPath=*/"", StringMode.REFINEMENT,
                unwindSet = mapOf("java::pkg.T.proof:()V.1" to 8, "java::pkg.T.proof:()V.0" to 4))
        // global bound still present
        val gi = a.indexOf("--unwind")
        assertTrue(gi >= 0 && a[gi + 1] == "2", "global --unwind 2 still emitted: $a")
        // both per-loop overrides present, sorted by loop id (.0 before .1)
        val flags = a.withIndex().filter { it.value == "--unwindset" }.map { a[it.index + 1] }
        assertEquals(listOf("java::pkg.T.proof:()V.0:4", "java::pkg.T.proof:()V.1:8"), flags,
                "one --unwindset per loop, value form <id>:<bound>, stably ordered: $a")
    }

    @Test
    fun no_unwindset_args_when_the_map_is_empty() {
        val a = Jbmc.args("pkg.T", "pkg.T.proof", "/cp",
                2, true, 0, null, "", StringMode.REFINEMENT, unwindSet = emptyMap())
        assertFalse(a.contains("--unwindset"), "no per-loop flags for an ordinary single-bound run: $a")
    }

    @Test
    fun parser_recovers_the_unwindset_loop_id_from_a_loop_firing() {
        // A loop unwinding-assertion property `<func>.unwind.<n>` yields the --unwindset id `<func>.<n>`.
        val json = """
            [
              {"result":[
                {"name":"u","status":"FAILURE","property":"java::pkg.Tests.proof:()V.unwind.3",
                 "description":"unwinding assertion loop 3",
                 "sourceLocation":{"file":"T.java","line":"9","function":"java::pkg.Tests.proof:()V"}},
                {"name":"m","status":"FAILURE","description":"assertion ...",
                 "sourceLocation":{"file":"V.java","line":"${BmcReachability.SENTINEL_LINE}",
                  "function":"java::pkg.Tests.proof:()V"}}
              ]},
              {"cProverStatus":"failure"}
            ]""".trimIndent()
        val loops = JbmcOutputParser.parse(json, "pkg.Tests.proof").unwindingLoops
        assertEquals(1, loops.size)
        assertEquals("java::pkg.Tests.proof:()V.3", loops[0].loopId,
                "the unwindset loop id is the property with .unwind. collapsed to .")
        assertFalse(loops[0].recursion)
    }

    @Test
    fun parser_leaves_loop_id_null_for_a_recursion_overrun() {
        // A recursion overrun has no per-site unwindset handle, so loopId is null (the smart climb then
        // falls back rather than fabricating an id).
        val json = """
            [
              {"result":[
                {"name":"u","status":"FAILURE","property":"java::pkg.Deep.down:(I)I.recursion",
                 "description":"recursion unwinding assertion",
                 "sourceLocation":{"file":"D.java","line":"4","function":"java::pkg.Deep.down:(I)I"}},
                {"name":"m","status":"FAILURE","description":"assertion ...",
                 "sourceLocation":{"file":"V.java","line":"${BmcReachability.SENTINEL_LINE}",
                  "function":"java::pkg.Tests.proof:()V"}}
              ]},
              {"cProverStatus":"failure"}
            ]""".trimIndent()
        val loops = JbmcOutputParser.parse(json, "pkg.Tests.proof").unwindingLoops
        assertEquals(1, loops.size)
        assertTrue(loops[0].recursion)
        assertEquals(null, loops[0].loopId, "a recursion overrun is not targetable per-loop")
    }
}
