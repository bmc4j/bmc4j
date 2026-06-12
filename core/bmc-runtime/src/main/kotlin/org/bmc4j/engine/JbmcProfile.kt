package org.bmc4j.engine

import com.google.gson.JsonParser
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * A per-stage PERFORMANCE BREAKDOWN of one jbmc run, parsed from the verbose `--verbosity 10`
 * STATUS-MESSAGE stream the harness already captures (see [Jbmc] / [JbmcOutputParser]). It is the
 * data behind [org.bmc4j.BmcProfile]: an ADDITIVE diagnostic that never touches the verdict, surfaced
 * only for a proof annotated `@BmcProfile`.
 *
 * The single most useful field is [reachedSat]: a proof that times out purely in symbolic execution
 * NEVER reaches the solver, and that distinction (symex-bound vs solver-bound) is the first thing you
 * need when diagnosing a slow/timing-out proof. The [topUnwoundMethods] "offenders" list pinpoints the
 * hot method, and the formula-size stats ([programSteps], [vccGenerated]/[vccRemaining], [satVariables]
 * /[satClauses]) quantify how large the formula got.
 *
 * Every field is OPTIONAL: a timed-out run is parsed from whatever was captured up to the kill (the
 * most valuable case), so phases/stats that the engine had not yet emitted are simply absent. The
 * message FORMATS are not an engine contract — they are pinned by [JbmcProfileTest] against the
 * bundled engine, whose identity is in the verdict-cache key (a bump re-validates), exactly the same
 * discipline as [JbmcOutputParser]'s opaque-symbol / unwinding markers.
 */
class JbmcProfile private constructor(
        /** Phase wall-times the engine reported, in first-seen order: `Symex`, `Convert SSA`,
         *  `Postprocess`, `Solver`, … → seconds. Empty if the run emitted none (e.g. killed early). */
        val phaseSeconds: Map<String, Double>,
        /** True if the engine reached the SAT/SMT solver (it logged "Passing problem to propositional
         *  reduction" or a `<N> variables, <M> clauses` line). On a timeout this distinguishes a
         *  symex-bound proof (never reached SAT) from a solver-bound one. */
        val reachedSat: Boolean,
        /** Per-method loop-unwinding counts (method FQN → number of `Unwinding loop` iterations seen),
         *  highest-first then by name. The "top offenders" list — the hot method is at the top. */
        val unwindingByMethod: List<MethodCount>,
        /** Per-method recursion-unwinding counts (`Unwinding recursion` lines), highest-first. */
        val recursionByMethod: List<MethodCount>,
        /** `size of program expression: <N> steps`, or null if not emitted. */
        val programSteps: Long?,
        /** `Generated <N> VCC(s), …` — verification conditions generated, or null. */
        val vccGenerated: Long?,
        /** `… <M> remaining after simplification` — VCCs left after simplification, or null. */
        val vccRemaining: Long?,
        /** SAT `<N> variables, <M> clauses` → variables, or null (only present once SAT is reached). */
        val satVariables: Long?,
        /** SAT clauses, or null. */
        val satClauses: Long?) {

    /** One method and the count of unwinding (loop or recursion) firings attributed to it. */
    class MethodCount(@get:JvmName("method") val method: String,
                      @get:JvmName("count") val count: Int)

    /** True when the run emitted nothing we could turn into a breakdown (no phases, no unwinding, no
     *  formula stats) — e.g. it was killed before the engine logged anything useful. */
    fun isEmpty(): Boolean =
            phaseSeconds.isEmpty() && unwindingByMethod.isEmpty() && recursionByMethod.isEmpty()
                    && programSteps == null && vccGenerated == null && satVariables == null

    /**
     * Render the breakdown as a readable, human-first table for the proof's stdout/report. Lines are
     * prefixed `  bmc4j[profile]:` so they sit alongside the existing `  bmc4j:` progress lines and are
     * easy to grep. [entryFunction] names the proof; [verdict] is its resolved verdict (so the profile
     * self-labels what run it describes).
     */
    fun render(entryFunction: String, verdict: String): String {
        val sb = StringBuilder()
        val tag = "  bmc4j[profile]:"
        sb.append(tag).append(' ').append(entryFunction).append(" -> ").append(verdict)
                .append(" - engine performance breakdown\n")

        // Where time went + whether the solver was reached (the headline signal).
        sb.append(tag).append("   phases (where the engine spent wall-time):\n")
        if (phaseSeconds.isEmpty()) {
            sb.append(tag).append("       (no phase timings captured")
                    .append(if (reachedSat) ")\n" else " - did not reach the solver)\n")
        } else {
            for ((phase, secs) in phaseSeconds) {
                sb.append(tag).append("       ").append(phase.padEnd(20)).append("  ")
                        .append(formatSeconds(secs)).append('\n')
            }
        }
        sb.append(tag).append("       reached SAT/SMT solver: ").append(if (reachedSat) "YES" else "NO")
        if (!reachedSat) {
            sb.append("  (time was spent BEFORE solving - i.e. in symbolic execution /")
                    .append(" formula construction, not the solver)")
        }
        sb.append('\n')

        // Top unwound methods (the hot method).
        if (unwindingByMethod.isNotEmpty()) {
            sb.append(tag).append("   top unwound loops (method x iterations):\n")
            for (mc in unwindingByMethod.take(TOP_N)) {
                sb.append(tag).append("       ").append(mc.method).append("  x").append(mc.count)
                        .append('\n')
            }
            if (unwindingByMethod.size > TOP_N) {
                sb.append(tag).append("       (+ ").append(unwindingByMethod.size - TOP_N)
                        .append(" more)\n")
            }
        }
        if (recursionByMethod.isNotEmpty()) {
            sb.append(tag).append("   recursion unwinding (method x firings):\n")
            for (mc in recursionByMethod.take(TOP_N)) {
                sb.append(tag).append("       ").append(mc.method).append("  x").append(mc.count)
                        .append('\n')
            }
        }

        // Formula size.
        val stats = mutableListOf<String>()
        programSteps?.let { stats.add("program expression: $it steps") }
        if (vccGenerated != null || vccRemaining != null) {
            stats.add("VCCs: ${vccGenerated ?: "?"} generated, ${vccRemaining ?: "?"} remaining")
        }
        if (satVariables != null || satClauses != null) {
            stats.add("SAT: ${satVariables ?: "?"} variables, ${satClauses ?: "?"} clauses")
        }
        if (stats.isNotEmpty()) {
            sb.append(tag).append("   formula size:\n")
            for (s in stats) {
                sb.append(tag).append("       ").append(s).append('\n')
            }
        }
        return sb.toString().trimEnd('\n')
    }

    private fun formatSeconds(secs: Double): String =
            if (secs >= 0.001) String.format("%.3fs", secs) else "${secs}s"

    companion object {

        /** Top-N offenders shown in the rendered table (the full list is in the parsed object). */
        private const val TOP_N = 8

        /**
         * Parse the breakdown from the jbmc spill [file] (the `--json-ui` array of message objects),
         * streaming it like [JbmcOutputParser.parse] so heap stays bounded regardless of output size:
         * only small counters and the (bounded) per-method tallies are retained, never the message
         * flood. Best-effort and NEVER throws — a parse problem yields an empty/partial profile, since
         * the profile is purely diagnostic and must never affect a verdict. Robust to a TRUNCATED stream
         * (a timeout kill mid-write): whatever well-formed elements were read are used.
         */
        @JvmStatic
        fun parse(file: File): JbmcProfile {
            val acc = Accumulator()
            try {
                file.inputStream().buffered().use { raw ->
                    JsonReader(raw.reader(StandardCharsets.UTF_8)).use { reader ->
                        reader.isLenient = true
                        if (reader.peek() != JsonToken.BEGIN_ARRAY) {
                            return acc.build()
                        }
                        reader.beginArray()
                        while (reader.hasNext()) {
                            val element = JsonParser.parseReader(reader)
                            if (!element.isJsonObject) {
                                continue
                            }
                            val obj = element.asJsonObject
                            val textEl = obj.get("messageText") ?: continue
                            if (!textEl.isJsonPrimitive) {
                                continue
                            }
                            acc.consume(textEl.asString)
                        }
                    }
                }
            } catch (e: Exception) {
                // Truncated/garbage/unreadable: keep whatever was accumulated. Diagnostic only.
            }
            return acc.build()
        }

        /**
         * Parse the breakdown from an in-heap [json] string (the `--json-ui` array). Used by the unit
         * tests that pin the message formats; the production path uses the [File] overload. Best-effort,
         * never throws.
         */
        @JvmStatic
        fun parse(json: String): JbmcProfile {
            val acc = Accumulator()
            try {
                val root = JsonParser.parseString(json).asJsonArray
                for (e in root) {
                    if (!e.isJsonObject) {
                        continue
                    }
                    val textEl = e.asJsonObject.get("messageText") ?: continue
                    if (textEl.isJsonPrimitive) {
                        acc.consume(textEl.asString)
                    }
                }
            } catch (e: Exception) {
                // Diagnostic only — never throw out of the profiler.
            }
            return acc.build()
        }

        /** `Unwinding loop java::pkg.Class.method.<n> iteration <N> file ... line ...` — the per-loop
         *  firing the engine logs at verbosity 10. We count one per line and attribute it to the method. */
        private val UNWIND_LOOP_RE =
                Regex("""^Unwinding loop (\S+?)(?:\.\d+)? iteration \d+""")
        /** `Unwinding recursion java::pkg.Class.method ...`. */
        private val UNWIND_RECURSION_RE =
                Regex("""^Unwinding recursion (\S+)""")
        /** `size of program expression: <N> steps`. */
        private val PROGRAM_STEPS_RE =
                Regex("""size of program expression:\s*(\d+)\s*steps""")
        /** `Generated <N> VCC(s), <M> remaining after simplification`. */
        private val VCC_RE =
                Regex("""Generated (\d+) VCC\(s\),\s*(\d+) remaining""")
        /** SAT `<N> variables, <M> clauses` (jbmc logs it just before/after handing the CNF to the solver). */
        private val SAT_SIZE_RE =
                Regex("""(\d+) variables,\s*(\d+) clauses""")
        /** `Runtime <Phase>: <seconds>s` — the phase wall-times (Symex / Convert SSA / Postprocess /
         *  Solver / decision procedure). The trailing unit may be `s` or absent depending on the build. */
        private val RUNTIME_RE =
                Regex("""^Runtime (.+?):\s*([0-9.eE+\-]+)\s*s?$""")
        /** The symex->SAT transition marker: its presence proves the solver was reached. */
        private const val PASSING_TO_SAT = "Passing problem to propositional reduction"

        /** Mutable tally that absorbs message lines and emits an immutable [JbmcProfile]. */
        private class Accumulator {
            private val phases = LinkedHashMap<String, Double>()
            private var reachedSat = false
            private val loopCounts = LinkedHashMap<String, Int>()
            private val recursionCounts = LinkedHashMap<String, Int>()
            private var programSteps: Long? = null
            private var vccGenerated: Long? = null
            private var vccRemaining: Long? = null
            private var satVariables: Long? = null
            private var satClauses: Long? = null

            fun consume(text: String) {
                val line = text.trim()
                if (line.isEmpty()) {
                    return
                }
                UNWIND_LOOP_RE.find(line)?.let {
                    val m = renderMethod(it.groupValues[1])
                    loopCounts[m] = (loopCounts[m] ?: 0) + 1
                    return
                }
                UNWIND_RECURSION_RE.find(line)?.let {
                    val m = renderMethod(it.groupValues[1])
                    recursionCounts[m] = (recursionCounts[m] ?: 0) + 1
                    return
                }
                RUNTIME_RE.matchEntire(line)?.let {
                    val phase = it.groupValues[1].trim()
                    val secs = it.groupValues[2].toDoubleOrNull()
                    if (secs != null && phase.isNotEmpty()) {
                        phases[phase] = secs
                        if (phase.startsWith("Solver", ignoreCase = true)
                                || phase.contains("decision procedure", ignoreCase = true)) {
                            reachedSat = true // a solver phase ran -> SAT was reached
                        }
                    }
                    return
                }
                if (line.contains(PASSING_TO_SAT)) {
                    reachedSat = true
                    return
                }
                PROGRAM_STEPS_RE.find(line)?.let {
                    programSteps = it.groupValues[1].toLongOrNull() ?: programSteps
                    return
                }
                VCC_RE.find(line)?.let {
                    vccGenerated = it.groupValues[1].toLongOrNull() ?: vccGenerated
                    vccRemaining = it.groupValues[2].toLongOrNull() ?: vccRemaining
                    return
                }
                SAT_SIZE_RE.find(line)?.let {
                    satVariables = it.groupValues[1].toLongOrNull() ?: satVariables
                    satClauses = it.groupValues[2].toLongOrNull() ?: satClauses
                    reachedSat = true // a CNF size line is only emitted once SAT is reached
                    return
                }
            }

            fun build(): JbmcProfile = JbmcProfile(
                    phaseSeconds = LinkedHashMap(phases),
                    reachedSat = reachedSat,
                    unwindingByMethod = sorted(loopCounts),
                    recursionByMethod = sorted(recursionCounts),
                    programSteps = programSteps,
                    vccGenerated = vccGenerated,
                    vccRemaining = vccRemaining,
                    satVariables = satVariables,
                    satClauses = satClauses)

            private fun sorted(counts: Map<String, Int>): List<MethodCount> =
                    counts.entries
                            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }
                                    .thenBy { it.key })
                            .map { MethodCount(it.key, it.value) }
        }

        /** `java::pkg.Class.method:(sig)ret` (or a bare `java::pkg.Class.method`) -> `pkg.Class.method`.
         *  Mirrors [JbmcOutputParser]'s method-FQN normalization so the offenders list reads like the
         *  nondet-stub footnotes. A name without the `java::` prefix is returned trimmed. */
        private fun renderMethod(symbol: String): String {
            var s = symbol.removePrefix("java::")
            val sig = s.indexOf(":(")
            if (sig >= 0) {
                s = s.substring(0, sig)
            }
            return s.ifBlank { symbol }
        }
    }
}
