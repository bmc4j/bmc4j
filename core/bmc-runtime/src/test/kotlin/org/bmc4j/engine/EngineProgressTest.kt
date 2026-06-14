package org.bmc4j.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets

/**
 * Pins [EngineProgress]: the LIVE phase-progress streaming surfaced under `@BmcProfile` /
 * `-Dbmc.streamEngine`. It must emit one console line per phase TRANSITION (symex -> Convert SSA ->
 * solver) as the markers arrive, a periodic symex heartbeat naming the hot function, and nothing for the
 * verbosity flood, and it must match a marker even when it is split across two reads.
 */
internal class EngineProgressTest {

    private fun feedString(progress: EngineProgress, s: String) {
        val bytes = s.toByteArray(StandardCharsets.UTF_8)
        progress.feed(bytes, bytes.size)
    }

    @Test
    fun emits_one_line_per_phase_transition_in_order() {
        val out = mutableListOf<String>()
        val progress = EngineProgress(out::add)
        feedString(progress, """{"messageText":"Starting Bounded Model Checking"},""")
        feedString(progress, """{"messageText":"Unwinding loop java::pkg.A.f:()V.0 iteration 1 ..."},""")
        feedString(progress, """{"messageText":"converting SSA"},""")
        feedString(progress, """{"messageText":"Passing problem to propositional reduction"},""")
        progress.finish()

        // A line per transition, each tagged and naming the phase, in engine order.
        assertTrue(out.any { it.contains("bmc4j[engine]") && it.contains("Symex") }, "symex start announced")
        assertTrue(out.any { it.contains("Convert SSA") }, "convert-ssa transition announced")
        assertTrue(out.any { it.contains("solver") }, "solver handoff announced")
        // Each transition fires exactly once - no repeats from later chunks re-containing the carry.
        assertEquals(1, out.count { it.contains("symbolic execution / loop unwinding has begun") })
        assertEquals(1, out.count { it.contains("converting the program to a bit-vector formula") })
        assertEquals(1, out.count { it.contains("propositional reduction") })
    }

    @Test
    fun matches_a_marker_split_across_two_reads() {
        val out = mutableListOf<String>()
        val progress = EngineProgress(out::add)
        // The "converting SSA" marker straddles the read boundary; the carry must stitch it back together.
        feedString(progress, """...some flood... conver""")
        feedString(progress, """ting SSA ...more flood...""")
        progress.finish()
        assertTrue(out.any { it.contains("Convert SSA") }, "a split marker is still matched via the carry")
    }

    @Test
    fun beats_a_symex_heartbeat_periodically_and_names_the_function() {
        val out = mutableListOf<String>()
        val progress = EngineProgress(out::add)
        // Far more unwinding lines than the heartbeat stride => at least one heartbeat, but not one per line.
        repeat(5000) {
            // jbmc emits each --json-ui object on its own line; the newline is the safe commit boundary.
            feedString(progress,
                    "{\"messageText\":\"Unwinding loop java::okio.Buffer.writeUtf8:(Ljava/lang/String;)V.0 iteration $it ...\"},\n")
        }
        progress.finish()
        val beats = out.filter { it.contains("Symex heartbeat") }
        assertFalse(beats.isEmpty(), "a long symex emits at least one heartbeat")
        assertTrue(beats.size < 50, "the heartbeat is sparse, never one-per-step (no flood)")
        assertTrue(beats.any { it.contains("okio.Buffer.writeUtf8") }, "the heartbeat names the hot function")
    }

    @Test
    fun is_enabled_under_profile_and_overridable_by_property() {
        val prev = System.getProperty("bmc.streamEngine")
        try {
            System.clearProperty("bmc.streamEngine")
            assertTrue(EngineProgress.isEnabled(profile = true), "on by default under @BmcProfile")
            assertFalse(EngineProgress.isEnabled(profile = false), "off by default without profiling")
            System.setProperty("bmc.streamEngine", "true")
            assertTrue(EngineProgress.isEnabled(profile = false), "forced on by -Dbmc.streamEngine=true")
            System.setProperty("bmc.streamEngine", "false")
            assertFalse(EngineProgress.isEnabled(profile = true), "forced off by -Dbmc.streamEngine=false")
        } finally {
            if (prev == null) System.clearProperty("bmc.streamEngine") else System.setProperty("bmc.streamEngine", prev)
        }
    }
}
