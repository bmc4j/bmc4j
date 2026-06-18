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
        /** bmc4j's OWN pre-engine pipeline pass wall-times (label → seconds), in first-seen order:
         *  `mirror`, `desugar`, `reachability`, `domain-split`, … . These are HARNESS-MEASURED (a
         *  wall-clock around bmc4j's classpath/bytecode prep, see [PipelineTiming]), kept distinct from
         *  the engine-REPORTED [phaseSeconds] so a slow proof shows whether the cost is OUR prep or the
         *  engine's symex/solver. Empty when not profiled at the backend, or fully pre-mirrored. */
        val pipelineSeconds: Map<String, Double>,
        /** The engine subprocess wall-clock (launch → exit/kill) the harness timed, in seconds, or null
         *  when not measured. On a TIMEOUT, this minus the completed `Runtime <Phase>:` times is the
         *  UNACCOUNTED remainder, attributed to the phase the engine was killed inside (see
         *  [derivedInProgressPhase] / [lastPhaseEntered]): Solver for a solver-bound proof killed mid-solve,
         *  Convert SSA for a bit-blasting-bound one, Symex for a symex-bound one. Harness-measured, never an
         *  engine-reported number. */
        val engineWallSeconds: Double?,
        /** True if the engine reached the SAT/SMT solver (it logged "Passing problem to propositional
         *  reduction" or a `<N> variables, <M> clauses` line). On a timeout this distinguishes a
         *  symex-bound proof (never reached SAT) from a solver-bound one. */
        val reachedSat: Boolean,
        /** The LAST engine phase the run was observed to ENTER, derived from in-progress markers (not
         *  completion lines): [Phase.SYMEX] once `Starting Bounded Model Checking` is seen, [Phase.CONVERT_SSA]
         *  once `converting SSA` is seen, [Phase.SOLVER] once the solver is reached. Null if no phase marker
         *  was captured (the engine was killed before symex even started). This is what lets a TIMEOUT
         *  attribute its wall-clock to the phase the engine was actually IN, e.g. a heavy proof whose symex
         *  finished (`Runtime Symex` emitted) but was then killed bit-blasting in Convert SSA is attributed
         *  to Convert SSA, NOT mislabelled "Symex (incomplete)". Survives a SIGKILL because the marker is a
         *  STATUS-MESSAGE the engine emits at phase entry, captured incrementally before the kill. */
        val lastPhaseEntered: Phase?,
        /** Per-method loop-unwinding counts (method FQN → number of `Unwinding loop` iterations seen),
         *  highest-first then by name. The "top offenders" list — the hot method is at the top. */
        val unwindingByMethod: List<MethodCount>,
        /** Per-LOOP unwinding detail (one entry per distinct engine loop id observed), highest-iteration
         *  first then by id. Each carries the FULL `--unwindset`-form loop id, its source file:line, and
         *  the iterations seen — the data behind the copy-pasteable `@org.bmc4j.LoopUnwind` suggestions
         *  the render emits. NB: this lists only loops that ACTUALLY unwound in this run; a loop the
         *  global bound never reached does not appear (the list is the observed loops, possibly partial). */
        val unwindingLoops: List<LoopInfo>,
        /** Per-method recursion-unwinding counts (`Unwinding recursion` lines), highest-first. */
        val recursionByMethod: List<RecursionInfo>,
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

    /** One method the engine RECURSIVELY unwound: the `Unwinding recursion` firing [count] and the
     *  best-effort [callPath] that reached it, reconstructed from the live symex stream exactly like a
     *  [LoopInfo]'s (empty when no call context was captured; heuristic, so same-method recursion past the
     *  first frame isn't distinguished). [method]/[count] mirror [MethodCount] so existing readers still work. */
    class RecursionInfo(@get:JvmName("method") val method: String,
                        @get:JvmName("count") val count: Int,
                        @get:JvmName("callPath") val callPath: List<String> = emptyList()) {
        /** The [callPath] as `outer > mid > inner` (outermost first), or empty when none captured. */
        fun callPathString(): String = callPath.joinToString(" > ")
    }

    /**
     * One derived run of a domainSplit fan-out, paired with the label/verdict it should self-report under
     * and its parsed engine [profile] (null when the run produced nothing profilable). Buffered by the
     * coordinator and rendered AFTER the fan-out finishes via [renderRunProfiles] / [renderAggregate],
     * never streamed live (concurrent slices would interleave their lines into noise).
     */
    class LabeledRun(@get:JvmName("label") val label: String,
                     @get:JvmName("verdict") val verdict: String,
                     @get:JvmName("profile") val profile: JbmcProfile?)

    /**
     * One observed loop: its FULL `--unwindset`-form engine [loopId] (the exact id
     * `@org.bmc4j.LoopUnwind(loop = ...)` takes, e.g. `java::okio.Buffer.writeUtf8:(...)V.0`), the
     * source [file]/[line] it sits at, and the [iterations] it unwound this run. The id form matches
     * [JbmcOutputParser]'s `loopIdFromProperty` so a pin authored from this output targets the same loop
     * the engine reports. [suggestion] renders the ready-to-paste annotation line.
     */
    class LoopInfo(@get:JvmName("loopId") val loopId: String,
                   @get:JvmName("file") val file: String?,
                   @get:JvmName("line") val line: Int,
                   /** TOTAL `Unwinding loop` firings for this id, summed across every time the loop was
                    *  encountered — a cost signal, NOT the @LoopUnwind bound (see [maxDepth]). */
                   @get:JvmName("iterations") val iterations: Int,
                   /** Best-effort call path (OUTERMOST first, ending in this loop's own method) that the
                    *  symex took to reach this loop, reconstructed from the live `BMC at ... function ...`
                    *  stream. Empty when no call context was captured. HEURISTIC, not a true engine call
                    *  stack: it is inferred from source-frame transitions, so deep recursion / re-entry to
                    *  the same method can mislabel a frame. Tells "real path vs dead-but-explored branch". */
                   @get:JvmName("callPath") val callPath: List<String> = emptyList(),
                   /** DEEPEST single unwind (max `iteration N`) — the correct per-encounter @LoopUnwind
                    *  bound. Defaults to [iterations] for callers/tests that don't distinguish (a
                    *  single-encounter loop has firings == maxDepth). */
                   @get:JvmName("maxDepth") val maxDepth: Int = iterations) {

        /** A ready-to-paste pin for THIS loop: `@LoopUnwind(loop = "<id>", bound = <maxDepth>)`. The bound
         *  is the DEEPEST single unwind observed (max `iteration N`) — the per-encounter depth `--unwindset`
         *  actually wants. It is NOT the total firing count: a loop hit K times to depth D logs K*D firings
         *  but only needs bound D, so pinning the firing count would over-unwind every encounter and bloat
         *  the formula. Raise it if the loop is still under-bounded, lower it to cap cost. */
        fun suggestion(): String =
                "@LoopUnwind(loop = \"$loopId\", bound = $maxDepth)"

        /** The [callPath] as `outer > mid > inner` (outermost caller first), or empty when none captured. */
        fun callPathString(): String = callPath.joinToString(" > ")
    }

    /**
     * The ORDERED engine phases, used to attribute a timeout's unaccounted wall-clock to the phase the
     * engine was actually IN. jbmc emits a STATUS-MESSAGE on ENTERING each (not just on completing it):
     * `Starting Bounded Model Checking` marks [SYMEX] (symbolic execution / loop unwinding), `converting
     * SSA` marks [CONVERT_SSA] (lowering the program equation to a bit-vector formula - the bit-blasting
     * that dominates a heavy timeout), and the propositional-reduction handoff marks [SOLVER]. `ordinal`
     * is the
     * progression order, so "the furthest phase entered" is just the max. The display label is what a
     * derived `<label> (incomplete)` row reads as. */
    enum class Phase(val label: String) {
        SYMEX("Symex"),
        CONVERT_SSA("Convert SSA"),
        SOLVER("Solver");
    }

    /** True when the run yielded nothing we could turn into a breakdown — neither engine-reported data
     *  (phases, unwinding, formula stats) NOR any harness-measured timing (bmc4j pipeline passes, or an
     *  engine wall-clock we could attribute to a derived Symex). A profiled run almost always has at least
     *  a pipeline timing or an engine wall-clock, so this is rarely true once profiling is on. */
    fun isEmpty(): Boolean =
            phaseSeconds.isEmpty() && unwindingByMethod.isEmpty() && recursionByMethod.isEmpty()
                    && programSteps == null && vccGenerated == null && satVariables == null
                    && pipelineSeconds.isEmpty() && engineWallSeconds == null && lastPhaseEntered == null

    /**
     * The UNACCOUNTED engine wall-clock attributed to an in-progress phase the engine was killed inside,
     * paired with that phase, or null when no derivation applies. This is the timeout-attribution fix:
     * instead of always dumping the whole wall-clock onto "Symex (incomplete)", we attribute it to the
     * phase the markers say the engine was actually IN.
     *
     * The rule, honest to the captured markers:
     *  - The wall-clock to attribute is the engine wall-clock MINUS the time of the phases that DID
     *    complete (their `Runtime <Phase>:` lines were captured). On a clean symex-bound kill nothing
     *    completed, so it is the full wall-clock; on a Convert-SSA-bound kill symex's ~10s is subtracted
     *    and the REMAINDER (the bit-blasting time) is what we surface.
     *  - The phase it is attributed to is [lastPhaseEntered] - the furthest in-progress marker seen
     *    (the solver handoff means Solver, `converting SSA` means Convert SSA, else `Starting Bounded
     *    Model Checking` means Symex). If no phase marker was captured at all we fall back to Symex (the
     *    engine was killed before even symex announced itself, so symbolic execution is the only phase it
     *    could have been in).
     *
     * The decision to derive a tail rests SOLELY on the remainder, NOT on whether a `Runtime Solver:` line
     * is present. jbmc emits a `Runtime <Phase>:` line only when that phase COMPLETES, so on a kill
     * MID-SOLVE the only Solver figure present is a stale/partial earlier sub-measurement and the long real
     * solve is never reported - leaving a large remainder that must be attributed to the Solver phase the
     * engine was killed inside. On a clean completion the `Runtime Solver:` line carries the FULL solve
     * time, so the completed phases sum to ~the wall, the remainder falls below the floor, and we return
     * null - no double-count, no phantom tail.
     *
     * Returns null only when there is no engine wall-clock to attribute, or when the remainder is not a
     * positive amount of time worth showing (the named completed phases already account for the wall). We
     * never invent a split across completed phases - only the single in-progress tail is derived. */
    fun derivedInProgressPhase(): Pair<Phase, Double>? {
        val wall = engineWallSeconds ?: return null
        val completed = phaseSeconds.values.sum()
        val remainder = wall - completed
        if (remainder <= IN_PROGRESS_FLOOR_SECONDS) {
            // The completed phases already account for ~all the wall-clock: nothing meaningful was spent
            // in an in-progress phase (the kill landed right at a phase boundary, or the run finished - a
            // clean solve reports its FULL Solver time, so phases ~= wall and we derive no tail here).
            return null
        }
        val phase = lastPhaseEntered
                // No phase marker captured at all: the engine was killed before symex announced itself,
                // so the only phase it could have been in is symbolic execution.
                ?: Phase.SYMEX
        return phase to remainder
    }

    /**
     * The phase that dominated this run's engine wall-clock, as a `(label, seconds)` pair, or null when
     * there is nothing to attribute. Used by the domainSplit aggregate to name "which phase dominated the
     * long-pole slice". The candidates are the engine-REPORTED completed phases ([phaseSeconds]) plus, for
     * an incomplete/killed run, the DERIVED in-progress tail ([derivedInProgressPhase]) - so a symex-bound
     * timeout that reported no completed phase still names "Symex (incomplete)" as its dominant cost. The
     * largest-by-seconds candidate wins.
     */
    fun dominantPhase(): Pair<String, Double>? {
        val candidates = buildList {
            phaseSeconds.forEach { (phase, secs) -> add(phase to secs) }
            derivedInProgressPhase()?.let { (phase, secs) -> add("${phase.label} (incomplete)" to secs) }
        }
        return candidates.maxByOrNull { it.second }
    }

    /**
     * Return a copy of this engine-parsed profile carrying the HARNESS-measured timings: bmc4j's own
     * [pipeline] pass wall-times and the engine subprocess [engineWall] (launch → exit/kill) in seconds.
     * The engine-reported fields ([phaseSeconds], unwinding, formula stats, [reachedSat]) are unchanged —
     * these are a parallel, additive overlay that never touches a verdict. [parse] produces the
     * engine-side data; the backend / driver attaches its own measurements through here. A null/empty
     * [pipeline] and null [engineWall] leave the profile untouched.
     */
    fun withHarnessTimings(pipeline: Map<String, Double>?, engineWall: Double?): JbmcProfile {
        if (pipeline.isNullOrEmpty() && engineWall == null) {
            return this
        }
        return JbmcProfile(
                phaseSeconds = phaseSeconds,
                pipelineSeconds = if (pipeline.isNullOrEmpty()) pipelineSeconds else LinkedHashMap(pipeline),
                engineWallSeconds = engineWall ?: engineWallSeconds,
                reachedSat = reachedSat,
                lastPhaseEntered = lastPhaseEntered,
                unwindingByMethod = unwindingByMethod,
                unwindingLoops = unwindingLoops,
                recursionByMethod = recursionByMethod,
                programSteps = programSteps,
                vccGenerated = vccGenerated,
                vccRemaining = vccRemaining,
                satVariables = satVariables,
                satClauses = satClauses)
    }

    /**
     * Render the breakdown as a readable, human-first table for the proof's stdout/report. Lines are
     * prefixed `  bmc4j[profile]:` so they sit alongside the existing `  bmc4j:` progress lines and are
     * easy to grep. [entryFunction] names the proof; [verdict] is its resolved verdict (so the profile
     * self-labels what run it describes).
     */
    fun render(entryFunction: String, verdict: String): String = render(entryFunction, verdict, null)

    /**
     * Render the breakdown, optionally tagged with a derived-run [runLabel] (e.g. `slice 3/8`, `cover`).
     * When [runLabel] is non-null the header reads `<runLabel>: <entryFunction> -> <verdict> - ...`, so
     * the per-run blocks of a domainSplit fan-out are each self-identifying. The single-proof path passes
     * null for the unlabeled header. Everything else (phases, offenders, formula size) is identical.
     */
    fun render(entryFunction: String, verdict: String, runLabel: String?): String {
        val headerPrefix = if (runLabel == null) "" else "$runLabel: "
        // Build the breakdown one line at a time — a row per list entry, no '\n' or string concatenation.
        // Each timed row is "<label> <name>  <secs>", padded so the seconds column lines up.
        fun MutableList<String>.timed(label: String, name: String, secs: Double) =
                add("       $label ${name.padEnd(24)}  ${formatSeconds(secs)}")
        fun MutableList<String>.methods(header: String, counts: List<MethodCount>, showMore: Boolean) {
            if (counts.isEmpty()) return
            add("   $header")
            counts.take(TOP_N).forEach { add("       ${it.method}  x${it.count}") }
            if (showMore && counts.size > TOP_N) add("       (+ ${counts.size - TOP_N} more)")
        }

        val lines = buildList {
            add(" $headerPrefix$entryFunction -> $verdict - performance breakdown (bmc4j prep + engine)")
            add("   legend: $LBL_BMC4J/$LBL_HARNESS = bmc4j-measured wall-clock, $LBL_ENGINE = jbmc-reported")

            // bmc4j's OWN pipeline (HARNESS-measured): classpath mirroring + the bytecode rewrites we run
            // before jbmc launches. Shown FIRST so the breakdown reads as (our prep) + (engine).
            add("   bmc4j pipeline (harness-measured - our prep before the engine):")
            if (pipelineSeconds.isEmpty()) {
                add("       (no pipeline passes timed - fully pre-mirrored, or not measured)")
            } else {
                pipelineSeconds.forEach { (pass, secs) -> timed(LBL_BMC4J, pass, secs) }
                add("       ${"total".padEnd(24 + LBL_BMC4J.length + 1)}  ${formatSeconds(pipelineSeconds.values.sum())}")
            }

            // Where the ENGINE spent wall-time. First the phases jbmc REPORTED a completed `Runtime <Phase>:`
            // line for (engine-measured). Then, on a TIMEOUT, the unaccounted remainder is attributed to the
            // phase the engine was actually killed INSIDE - derived from the in-progress markers it streamed
            // (the solver handoff means Solver; `converting SSA` means Convert SSA; else symex). This is the
            // attribution fix: a heavy proof killed bit-blasting after symex finished reads "Convert SSA
            // (incomplete)", and one killed MID-SOLVE (only a partial engine Solver line present) reads
            // "Solver (incomplete)" - never mislabelled, never silently dropped.
            add("   engine phases (where jbmc spent wall-time):")
            phaseSeconds.forEach { (phase, secs) -> timed(LBL_ENGINE, phase, secs) }
            val inProgress = derivedInProgressPhase()
            when {
                inProgress != null -> {
                    val (phase, secs) = inProgress
                    timed(LBL_HARNESS, "${phase.label} (incomplete)", secs)
                    val explain = when {
                        phaseSeconds.isEmpty() ->
                            "the engine reported no completed phase; it was killed inside ${phase.label}, so the" +
                                    " whole engine wall-clock above is ${phase.label}"
                        // A partial engine Solver figure is present but the run was killed STILL inside the
                        // solver: jbmc only reports a `Runtime <Phase>:` time when the phase completes, so the
                        // figure above is a partial sub-measurement and the long real solve is this remainder.
                        phaseSeconds.containsKey(phase.label) ->
                            "the engine reported a PARTIAL ${phase.label} time above, then was killed STILL inside" +
                                    " ${phase.label}; this is the UNACCOUNTED remainder of the engine wall-clock"
                        else ->
                            "the engine completed the phase(s) above (engine-reported), then was killed inside" +
                                    " ${phase.label}; this is the UNACCOUNTED remainder of the engine wall-clock"
                    }
                    add("       ($explain - DERIVED by the harness from its phase markers, not a" +
                            " jbmc-reported time)")
                }
                phaseSeconds.isNotEmpty() -> { /* fully accounted by the engine-reported phases above */ }
                reachedSat -> add("       (no engine phase timings captured)")
                else -> add("       (no engine phase timings captured - did not reach the solver)")
            }
            // The engine's total wall-clock (harness-measured) frames the engine-reported phase times (which
            // can sum to less than the wall-clock: load/teardown the phases don't name).
            engineWallSeconds?.let { timed(LBL_HARNESS, "engine wall-clock", it) }
            if (reachedSat) {
                add("       reached SAT/SMT solver: YES")
            } else {
                add("       reached SAT/SMT solver: NO  (time was spent BEFORE solving - i.e. in" +
                        " symbolic execution / formula construction, not the solver)")
            }

            methods("top unwound loops (method x iterations):", unwindingByMethod, showMore = true)
            // Recursion unwinding, with the reconstructed call path under each (the loop-list gets this via
            // LoopInfo; recursion uses its own RecursionInfo so a recursive <clinit>/method shows where it
            // was driven from, not just a count).
            if (recursionByMethod.isNotEmpty()) {
                add("   recursion unwinding (method x firings):")
                recursionByMethod.take(TOP_N).forEach { r ->
                    add("       ${r.method}  x${r.count}")
                    if (r.callPath.size > 1) {
                        add("           reached via ${r.callPathString()}")
                    }
                }
            }

            // Targetable loop ids + ready-to-paste @LoopUnwind suggestions. Each observed loop shows its
            // FULL --unwindset-form id (what @LoopUnwind takes) and a copy-pasteable pin line, so a user
            // can fix a loop's bound without having to discover the engine id by hand. The list is the
            // loops that ACTUALLY unwound this run (a loop the global bound never reached won't appear),
            // so it is noted as possibly partial.
            if (unwindingLoops.isNotEmpty()) {
                val shown = unwindingLoops.take(TOP_N)
                // First the readable list: each observed loop's FULL --unwindset-form id, iterations, and
                // source location, tagged like the rest of the breakdown.
                add("   targetable loops:")
                shown.forEach { loop ->
                    val where = if (loop.file != null) "  (${loop.file}:${loop.line})" else ""
                    // Show total firings AND the deepest single unwind. The pin bound is the deepest (what
                    // --unwindset wants per encounter); the firing total is the cost signal. They differ when
                    // a loop is reached many times (high firings, small depth) - the case the old "bound =
                    // firings" suggestion got wrong.
                    val depth = if (loop.maxDepth != loop.iterations) ", deepest ${loop.maxDepth}" else ""
                    add("       ${loop.loopId}  x${loop.iterations} firings$depth$where")
                    // The reconstructed call path that reached this loop (outermost first). Shown only when
                    // there is caller context beyond the loop's own method, so a reader can tell a loop on a
                    // real path from one reached via a dead-but-explored branch. Heuristic - see LoopInfo.
                    if (loop.callPath.size > 1) {
                        add("           reached via ${loop.callPathString()}")
                    }
                }
                if (unwindingLoops.size > TOP_N) {
                    add("       (+ ${unwindingLoops.size - TOP_N} more)")
                }
                add("       NOTE: only loops that unwound in THIS run are listed (a loop the bound never" +
                        " reached won't appear); the suggested bound is the DEEPEST single unwind (the" +
                        " per-encounter depth --unwindset wants, not the firing total) - raise it if a loop" +
                        " is still under-bounded.")
                // Then ALL the pins as ONE contiguous, flush-left, UNTAGGED block (see RAW_LINE_MARKER):
                // select the whole block in one go and paste it straight onto the proof's @BmcProof —
                // a multi-line terminal copy carries no `bmc4j[profile]:` prefix to hand-strip.
                add("   @LoopUnwind pins (copy the whole block below onto your @BmcProof method):")
                shown.forEach { loop -> add("$RAW_LINE_MARKER${loop.suggestion()}") }
            }

            // Formula size.
            val stats = buildList {
                programSteps?.let { add("program expression: $it steps") }
                if (vccGenerated != null || vccRemaining != null)
                    add("VCCs: ${vccGenerated ?: "?"} generated, ${vccRemaining ?: "?"} remaining")
                if (satVariables != null || satClauses != null)
                    add("SAT: ${satVariables ?: "?"} variables, ${satClauses ?: "?"} clauses")
            }
            if (stats.isNotEmpty()) {
                add("   formula size:")
                stats.forEach { add("       $it") }
            }
        }

        // Stamp every line with the grep-able tag, EXCEPT lines flagged RAW_LINE_MARKER (the
        // @LoopUnwind pins): those are emitted flush-left and untagged so a line-copy in the terminal
        // hands back exactly the annotation, with nothing to hand-strip before pasting into source.
        return lines.joinToString("\n") { line ->
            if (line.startsWith(RAW_LINE_MARKER)) line.removePrefix(RAW_LINE_MARKER)
            else "  bmc4j[profile]:$line"
        }
    }

    private fun formatSeconds(secs: Double): String = when {
        // Always fixed-point, never scientific notation: a sub-millisecond pass (e.g. a no-op
        // purity-audit) reads as "<0.001s" rather than an unhelpful "7.4E-4s".
        secs >= 0.001 -> String.format("%.3fs", secs)
        secs > 0.0 -> "<0.001s"
        else -> "0.000s"
    }

    companion object {

        /**
         * Render the grouped PER-RUN profile blocks for a domainSplit fan-out: one labeled
         * [JbmcProfile.render] block per derived [runs] entry (`cover`, `slice i/N`), plus a one-line note
         * for any run that produced no profilable output. [entryFunction] names the proof. Returns a
         * single newline-joined string (empty when there is nothing to show), emitted by the coordinator
         * after the fan-out completes.
         */
        @JvmStatic
        fun renderRunProfiles(entryFunction: String, runs: List<LabeledRun>): String {
            val blocks = buildList {
                runs.forEach { run ->
                    val p = run.profile
                    if (p == null || p.isEmpty()) {
                        add("  bmc4j[profile]: ${run.label}: $entryFunction -> ${run.verdict} - no engine" +
                                " performance breakdown was captured (no profilable verbose output).")
                    } else {
                        add(p.render(entryFunction, run.verdict, run.label))
                    }
                }
            }
            return blocks.joinToString("\n")
        }

        /**
         * Render the AGGREGATE summary across a domainSplit fan-out's derived [runs]. Two parts:
         *  - a per-verdict TALLY (how many runs VERIFIED / REFUTED / etc.), and
         *  - the PARALLEL CRITICAL PATH: because the runs execute CONCURRENTLY, the wall-clock is the MAX
         *    engine wall-clock across runs (the long pole), NOT their sum. We name that slowest run and
         *    the phase that dominated IT (so a reader sees "slice 5/8 was the long pole, dominated by
         *    Convert SSA"). Runs that never recorded an engine wall-clock (cancelled by early-exit, or
         *    unprofiled) simply don't contribute a critical-path candidate.
         *
         * [entryFunction] names the proof. Lines carry the same `  bmc4j[profile]:` tag as the per-run
         * blocks. Purely additive diagnostic output - it never touches the verdict.
         */
        @JvmStatic
        fun renderAggregate(entryFunction: String, runs: List<LabeledRun>): String {
            // Per-verdict tally, in first-seen order so the printed order is stable.
            val tally = LinkedHashMap<String, Int>()
            runs.forEach { tally[it.verdict] = (tally[it.verdict] ?: 0) + 1 }

            // Parallel critical path: the run with the MAX engine wall-clock is the long pole.
            val slowest = runs
                    .mapNotNull { run -> run.profile?.engineWallSeconds?.let { run to it } }
                    .maxByOrNull { it.second }

            val lines = buildList {
                add(" $entryFunction -> domainSplit aggregate (${runs.size} run(s), executed concurrently)")
                add("   verdict tally: " + tally.entries.joinToString(", ") { "${it.value}x ${it.key}" })
                if (slowest == null) {
                    add("   critical path: (no engine wall-clock recorded on any run - cancelled by" +
                            " early-exit, or unprofiled)")
                } else {
                    val (run, wall) = slowest
                    add("   critical path (MAX engine wall-clock across the concurrent runs, NOT the sum):")
                    add("       long pole: ${run.label}  ${fmtSeconds(wall)}")
                    val dominant = run.profile?.dominantPhase()
                    if (dominant != null) {
                        add("       dominated by phase: ${dominant.first}  ${fmtSeconds(dominant.second)}")
                    }
                }
            }
            return lines.joinToString("\n") { "  bmc4j[profile]:$it" }
        }

        /** Companion-level seconds formatter, identical to the instance [render] one (fixed-point, never
         *  scientific notation), used by the aggregate which has no per-run instance to call. */
        private fun fmtSeconds(secs: Double): String = when {
            secs >= 0.001 -> String.format("%.3fs", secs)
            secs > 0.0 -> "<0.001s"
            else -> "0.000s"
        }

        /** Top-N offenders shown in the rendered table (the full list is in the parsed object). */
        private const val TOP_N = 8

        /** Internal flag prepended to a built render line that must be emitted FLUSH-LEFT and UNTAGGED
         *  (no `  bmc4j[profile]:` prefix) — used for the @LoopUnwind pins so a terminal line-copy yields
         *  exactly the paste-able annotation. Stripped in the final join; never reaches output, and no
         *  real line begins with it. */
        private const val RAW_LINE_MARKER = "@@RAW@@"

        /** Below this many seconds, an unaccounted in-progress remainder is treated as noise (a kill landing
         *  right at a phase boundary, clock jitter) rather than a phase the engine was meaningfully stuck in,
         *  so [derivedInProgressPhase] returns null. Small enough that a real Convert-SSA-bound timeout (where
         *  the remainder is seconds-to-minutes) always surfaces. */
        private const val IN_PROGRESS_FLOOR_SECONDS = 0.05

        /** Row tags that label each timing's PROVENANCE so a reader never confuses a bmc4j-measured
         *  wall-clock with an engine-reported phase time. `[bmc4j]` = a timed bmc4j pipeline pass;
         *  `[harness]` = another harness-measured wall-clock (engine wall-clock, derived Symex);
         *  `[engine]` = a number jbmc itself reported in its `Runtime <Phase>:` stream. */
        private const val LBL_BMC4J = "[bmc4j]"
        private const val LBL_HARNESS = "[harness]"
        private const val LBL_ENGINE = "[engine]"

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

        /** `Unwinding loop java::pkg.Class.method:(sig)ret.<n> iteration <N> file <f> line <l>` — the
         *  per-loop firing the engine logs at verbosity 10. Group 1 is the FULL `--unwindset`-form loop id
         *  (the trailing `.<n>` KEPT — it is part of the id @org.bmc4j.LoopUnwind targets); group 2/3 are
         *  the source file/line when present. We count one firing per line, attribute it to the loop id
         *  (and, via [renderMethod], to its method for the by-method offenders list). */
        private val UNWIND_LOOP_RE =
                Regex("""^Unwinding loop (\S+) iteration (\d+)(?: file (.+?) line (\d+))?""")
        /** `Unwinding recursion java::pkg.Class.method ...`. */
        private val UNWIND_RECURSION_RE =
                Regex("""^Unwinding recursion (\S+)""")
        /** `BMC at file <f> line <l> function java::pkg.Class.method:(sig)ret bytecode-index <n> (depth <d>)`
         *  — emitted per symex STEP in the live (verbosity 10) stream, naming the function currently being
         *  symbolically executed. Group 1 is its FULL symbol. We track this field's TRANSITIONS to
         *  reconstruct a best-effort call path to each unwound loop (see [Accumulator.frames]); the `depth`
         *  is a monotonic symex-STEP counter, NOT call depth, so it can't drive the stack and we ignore it. */
        private val BMC_AT_RE =
                Regex("""^BMC at .*?\bfunction (java::\S+)""")
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

        /** Phase-ENTRY markers jbmc streams when it BEGINS a phase (distinct from the `Runtime <Phase>:`
         *  COMPLETION lines). They survive a SIGKILL - emitted as soon as the phase starts - so they tell
         *  us which phase a killed run was IN, which the completion lines (never emitted for the killed
         *  phase) cannot. Pinned against the bundled cbmc 6.9.0 by [JbmcProfileTest], same discipline as
         *  the other markers; the engine identity is in the verdict-cache key so a bump re-validates.
         *
         *  - `Starting Bounded Model Checking`: symbolic execution (loop unwinding) has begun.
         *  - `converting SSA`: symex finished; the program equation is being lowered to a bit-vector
         *    formula (the bit-blasting that dominates a heavy timeout). */
        private const val ENTER_SYMEX = "Starting Bounded Model Checking"
        private const val ENTER_CONVERT_SSA = "converting SSA"

        /** Mutable tally that absorbs message lines and emits an immutable [JbmcProfile]. */
        private class Accumulator {
            private val phases = LinkedHashMap<String, Double>()
            private var reachedSat = false
            private val loopCounts = LinkedHashMap<String, Int>()
            // Per-LOOP detail keyed by the FULL --unwindset-form loop id: iterations seen + the first
            // file/line observed for it. Drives the targetable-loops / @LoopUnwind suggestion output.
            private val loopInfos = LinkedHashMap<String, LoopAcc>()
            private val recursionInfos = LinkedHashMap<String, RecursionAcc>()
            private var programSteps: Long? = null
            private var vccGenerated: Long? = null
            private var vccRemaining: Long? = null
            private var satVariables: Long? = null
            private var satClauses: Long? = null
            private var lastPhaseEntered: Phase? = null
            // Best-effort SHADOW CALL STACK (outermost first), driven by the live `BMC at ... function ...`
            // transitions, so each unwound loop can be stamped with the call path that reached it. Push on
            // entering a function not currently on the stack (a call); pop back to it when the active
            // function reverts to one already on the stack (a return). Heuristic: same-method recursion /
            // re-entry is indistinguishable by name, so a deeply recursive path can under-count frames.
            private val frames = ArrayList<String>()

            /** Advance the furthest-entered phase monotonically (never regress to an earlier phase). */
            private fun enter(phase: Phase) {
                val cur = lastPhaseEntered
                if (cur == null || phase.ordinal > cur.ordinal) {
                    lastPhaseEntered = phase
                }
            }

            /** Fold a `BMC at`/unwind `function` field into the shadow stack: a function not currently on
             *  the stack is a CALL (push); a function already below the top is a RETURN to that ancestor
             *  (pop everything above it). The top is always the function currently being symex'd. */
            private fun enterFunction(symbol: String) {
                val m = renderMethod(symbol)
                val idx = frames.lastIndexOf(m)
                if (idx < 0) {
                    frames.add(m)
                } else {
                    while (frames.size - 1 > idx) {
                        frames.removeAt(frames.size - 1)
                    }
                }
            }

            fun consume(text: String) {
                val line = text.trim()
                if (line.isEmpty()) {
                    return
                }
                // Phase-ENTRY markers (cheap substring checks, kept ahead of the heavier regexes). They
                // record which phase the engine was IN so a killed run attributes its wall-clock correctly.
                // The Convert-SSA entry marker is what a heavy timeout streams just before the bit-blasting
                // flood that gets it killed - the signal the old code discarded.
                if (line.contains(ENTER_CONVERT_SSA)) {
                    enter(Phase.CONVERT_SSA)
                    return
                }
                if (line.contains(ENTER_SYMEX)) {
                    enter(Phase.SYMEX)
                    return
                }
                // Per-step symex location: just updates the shadow call stack, then done. Gated on the cheap
                // prefix because these lines are the most frequent in a verbose run - short-circuiting here
                // also keeps them from falling through the heavier regexes below.
                if (line.startsWith("BMC at")) {
                    BMC_AT_RE.find(line)?.let { enterFunction(it.groupValues[1]) }
                    return
                }
                UNWIND_LOOP_RE.find(line)?.let {
                    val loopId = it.groupValues[1]
                    val m = renderMethod(loopId)
                    loopCounts[m] = (loopCounts[m] ?: 0) + 1
                    // Ensure the loop's own method tops the shadow stack (the preceding `BMC at` lines should
                    // have pushed it; this is a backstop if one was missed), then snapshot the call path.
                    enterFunction(loopId)
                    // Per-loop detail: source location + the call path, captured the FIRST time we see it.
                    // file/line are groups 3/4 now that the iteration number is captured as group 2.
                    val acc = loopInfos.getOrPut(loopId) {
                        LoopAcc(it.groupValues[3].ifEmpty { null }, it.groupValues[4].toIntOrNull() ?: 0,
                                ArrayList(frames))
                    }
                    acc.firings++
                    // The @LoopUnwind bound is the DEEPEST single unwind (max `iteration N`), NOT the total
                    // firing count: --unwindset unwinds a loop N times PER ENCOUNTER, so a loop reached K
                    // times to depth D logs K*D firings but only needs bound D. Tracking the max keeps the
                    // suggested pin from over-unwinding every encounter.
                    val iterN = it.groupValues[2].toIntOrNull() ?: 0
                    if (iterN > acc.maxDepth) acc.maxDepth = iterN
                    return
                }
                UNWIND_RECURSION_RE.find(line)?.let {
                    val sym = it.groupValues[1]
                    val m = renderMethod(sym)
                    // Ensure the recursed method tops the shadow stack, then snapshot the call path the FIRST
                    // time we see it (later firings repeat, and the stack may have moved by then).
                    enterFunction(sym)
                    val acc = recursionInfos.getOrPut(m) { RecursionAcc(ArrayList(frames)) }
                    acc.count++
                    return
                }
                RUNTIME_RE.matchEntire(line)?.let {
                    val phase = it.groupValues[1].trim()
                    val secs = it.groupValues[2].toDoubleOrNull()
                    if (secs != null && phase.isNotEmpty()) {
                        phases[phase] = secs
                        // A COMPLETED phase line also tells us the engine reached at least that phase (a
                        // belt-and-braces backstop should an entry marker be missing/renamed in a build).
                        if (phase.equals(Phase.CONVERT_SSA.label, ignoreCase = true)) {
                            enter(Phase.CONVERT_SSA)
                        } else if (phase.startsWith("Symex", ignoreCase = true)) {
                            enter(Phase.SYMEX)
                        }
                        if (phase.startsWith("Solver", ignoreCase = true)
                                || phase.contains("decision procedure", ignoreCase = true)) {
                            reachedSat = true // a solver phase ran -> SAT was reached
                            enter(Phase.SOLVER)
                        }
                    }
                    return
                }
                if (line.contains(PASSING_TO_SAT)) {
                    reachedSat = true
                    enter(Phase.SOLVER)
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
                    enter(Phase.SOLVER)
                    return
                }
            }

            fun build(): JbmcProfile = JbmcProfile(
                    phaseSeconds = LinkedHashMap(phases),
                    pipelineSeconds = emptyMap(),
                    engineWallSeconds = null,
                    reachedSat = reachedSat,
                    lastPhaseEntered = lastPhaseEntered,
                    unwindingByMethod = sorted(loopCounts),
                    unwindingLoops = sortedLoops(loopInfos),
                    recursionByMethod = sortedRecursion(recursionInfos),
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

            /** The per-loop detail, highest-iteration first then by id (a stable, deterministic order). */
            private fun sortedLoops(infos: Map<String, LoopAcc>): List<LoopInfo> =
                    infos.entries
                            .sortedWith(compareByDescending<Map.Entry<String, LoopAcc>> { it.value.firings }
                                    .thenBy { it.key })
                            .map { LoopInfo(it.key, it.value.file, it.value.line, it.value.firings,
                                    it.value.callPath, it.value.maxDepth) }

            /** Per-method recursion detail, highest-firings first then by method (stable, deterministic). */
            private fun sortedRecursion(infos: Map<String, RecursionAcc>): List<RecursionInfo> =
                    infos.entries
                            .sortedWith(compareByDescending<Map.Entry<String, RecursionAcc>> { it.value.count }
                                    .thenBy { it.key })
                            .map { RecursionInfo(it.key, it.value.count, it.value.callPath) }
        }

        /** Mutable per-loop tally: the iterations seen plus the (first-observed) source location and the
         *  call path that reached the loop. */
        private class LoopAcc(@JvmField val file: String?, @JvmField val line: Int,
                              @JvmField val callPath: List<String>) {
            /** Total `Unwinding loop` firings for this id (a cost signal — summed across every encounter). */
            @JvmField var firings: Int = 0
            /** Deepest single unwind (max `iteration N`) — the correct per-encounter @LoopUnwind bound. */
            @JvmField var maxDepth: Int = 0
        }

        /** Mutable per-method recursion tally: firings seen plus the (first-observed) call path. */
        private class RecursionAcc(@JvmField val callPath: List<String>) {
            @JvmField var count: Int = 0
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
