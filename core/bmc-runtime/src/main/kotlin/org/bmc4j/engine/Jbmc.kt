package org.bmc4j.engine

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** Builds and runs a JBMC process against a single proof entry point. */
class Jbmc(private val executable: String) {

    /**
     * Run JBMC with the given entry function and classpath.
     *
     * @param entryClass    fully qualified class containing the proof method
     * @param entryFunction `Class.method` entry point
     * @param classpath     classpath JBMC loads the bytecode from
     * @param unwind        loop unwinding bound
     * @param unwindingAssertions add --unwinding-assertions to flag insufficient bounds
     * @param maxStringLength bound on nondeterministic (input) string length; ignored if `<= 0`
     */
    @JvmOverloads
    fun run(entryClass: String, entryFunction: String, classpath: String,
            unwind: Int, unwindingAssertions: Boolean, maxStringLength: Int,
            solver: String?, timeoutSeconds: Int = 0, externalSatPath: String = "",
            userClasspath: String? = null, profile: Boolean = false,
            pipelineSeconds: Map<String, Double>? = null): JbmcResult {
        preflightSolver(solver) // fail clearly now if a requested external solver isn't available
        val command = mutableListOf(executable)
        command.addAll(args(entryClass, entryFunction, classpath, unwind, unwindingAssertions,
                maxStringLength, solver, externalSatPath))
        return exec(command, entryFunction, timeoutSeconds, userClasspath, profile, pipelineSeconds)
    }

    /** Drains a process stream to a buffer on its own thread (so reads can't deadlock or block waitFor).
     *  Used for STDERR only, which is tiny; STDOUT (which can reach hundreds of MB on a heavy
     *  `--verbosity 10 --trace` proof) is spilled to a temp file by [FileGobbler] instead, never
     *  buffered in heap. */
    private class StreamGobbler(private val input: InputStream) : Thread("bmc-jbmc-gobbler") {

        @Volatile
        private var text = ""

        init {
            isDaemon = true
        }

        override fun run() {
            try {
                text = String(input.readAllBytes(), StandardCharsets.UTF_8)
            } catch (e: IOException) {
                // Stream closed by a kill mid-read — keep whatever was captured (possibly empty).
            }
        }

        fun text(): String = text
    }

    /**
     * Drains jbmc's STDOUT to a temp [file] on its own thread, STREAMING it through a bounded buffer
     * instead of buffering the whole (possibly hundreds-of-MB `--verbosity 10 --trace`) output in heap
     * as one `String` — the latent OOM this fixes. The parser ([JbmcOutputParser.parse] taking a
     * `File`) then reads the spill incrementally, materializing only what it needs; the timeout / crash
     * paths read a bounded head+tail for their error message (see [headTail]). The file is deleted by
     * the caller (`execOnce`'s `finally`).
     */
    private class FileGobbler(private val input: InputStream, val file: File) :
            Thread("bmc-jbmc-gobbler") {

        init {
            isDaemon = true
        }

        override fun run() {
            try {
                file.outputStream().buffered().use { out -> input.copyTo(out, COPY_BUFFER) }
            } catch (e: IOException) {
                // Stream closed by a kill mid-read — keep whatever was spilled so far (possibly empty).
            }
        }

        /** A bounded head+tail of the spilled output for a timeout / engine-crash diagnostic message —
         *  read with a random-access seek so we never pull the whole (possibly huge) file into heap. */
        fun headTail(): String = headTail(file)

        companion object {
            private const val COPY_BUFFER = 1 shl 16 // 64 KiB streaming buffer; heap stays bounded

            /** Bounded head+tail of [file] (UTF-8, best-effort) for an error message. Empty if missing. */
            fun headTail(file: File): String {
                val len = try {
                    if (file.isFile) file.length() else return ""
                } catch (e: IOException) {
                    return ""
                }
                if (len == 0L) {
                    return ""
                }
                val side = DIAG_SIDE
                return try {
                    if (len <= 2L * side) {
                        String(file.readBytes(), StandardCharsets.UTF_8)
                    } else {
                        val head = file.inputStream().use { ins ->
                            val b = ByteArray(side); val n = ins.read(b)
                            if (n <= 0) "" else String(b, 0, n, StandardCharsets.UTF_8)
                        }
                        val tail = RandomAccessFile(file, "r").use { raf ->
                            raf.seek(len - side)
                            val b = ByteArray(side); raf.readFully(b)
                            String(b, StandardCharsets.UTF_8)
                        }
                        "$head\n...\n$tail"
                    }
                } catch (e: IOException) {
                    ""
                }
            }

            private const val DIAG_SIDE = 800
        }
    }

    companion object {

        /**
         * Every live JBMC process, so it — and any child solver it spawned (e.g. z3 via `--z3`) —
         * can be force-killed if the test run is cancelled or the JVM shuts down. Without this, stopping
         * a Gradle task leaves orphaned `jbmc.exe`/`z3.exe` processes burning CPU.
         */
        private val RUNNING: MutableSet<Process> = ConcurrentHashMap.newKeySet()

        /**
         * Count of actual jbmc process launches in this JVM: every engine invocation goes
         * through [exec], so this is the ground truth for "did the verdict cache skip the engine?".
         * A cache hit returns before `exec`, so a second unchanged run should add zero here for the
         * cached proofs. Exposed via [invocationCount] for tests/diagnostics.
         */
        private val INVOCATIONS = AtomicLong()

        /** Number of jbmc processes launched in this JVM (a cache hit never increments this). */
        @JvmStatic
        fun invocationCount(): Long = INVOCATIONS.get()

        init {
            Runtime.getRuntime().addShutdownHook(Thread({
                RUNNING.forEach(::killTree)
            }, "bmc-jbmc-reaper"))
        }

        private fun killTree(p: Process) {
            try {
                p.descendants().forEach(ProcessHandle::destroyForcibly) // jbmc's children (e.g. z3) first
            } catch (ignored: RuntimeException) {
                // best effort
            }
            p.destroyForcibly()
        }

        /** The JBMC argument list — everything after the executable. */
        @JvmOverloads
        internal fun args(entryClass: String, entryFunction: String, classpath: String,
                          unwind: Int, unwindingAssertions: Boolean, maxStringLength: Int,
                          solver: String?, externalSatPath: String = ""): List<String> {
            val cmd = mutableListOf<String>()
            cmd.add(entryClass)
            cmd.add("--classpath")
            cmd.add(classpath)
            cmd.add("--function")
            cmd.add(entryFunction)
            // The verdict-relevant flags (unwind, unwinding-assertions, max-nondet-string-length, the
            // solver selection, AND any hard-coded engine flags) come from ONE builder, so the command
            // and the verdict-cache key can never drift apart — see [appendVerdictRelevantFlags] /
            // [verdictRelevantFlags].
            appendVerdictRelevantFlags(cmd, unwind, unwindingAssertions, maxStringLength,
                    solver, externalSatPath)
            // Pure OUTPUT/UI flags below — they change what we can OBSERVE, never the verdict, so they
            // are deliberately NOT part of the verdict-cache signature.
            cmd.add("--json-ui")
            cmd.add("--trace")
            // Bump verbosity so the engine emits its "opaque symbol" messages — the nondet-stub fact we
            // harvest per proof. At the default level those messages are suppressed; level 10
            // surfaces them in the same --json-ui STATUS-MESSAGE stream. The extra messages are ignored by
            // the verdict logic (only the result array + cProverStatus drive the verdict), so this changes
            // what we can OBSERVE, not the verdict.
            cmd.add("--verbosity")
            cmd.add("10")
            return cmd
        }

        /**
         * Append every jbmc flag that can CHANGE a verdict to [cmd], in the exact form jbmc receives
         * them. This is the SINGLE SOURCE of the verdict-relevant flag set: [args] calls it when building
         * the real command, and [verdictRelevantFlags] calls it to derive the verdict-cache key — so the
         * key can never diverge from the flags actually passed.
         *
         * INCLUDES: `--unwind`, `--unwinding-assertions`, `--max-nondet-string-length`, the full solver
         * selection (`--external-sat-solver` / `--smt2`+`--external-smt2-solver` / `--z3` / `--boolector`
         * / `--cvc4` / `--cvc5` / `--sat-solver`), and any future HARD-CODED engine flag added here.
         *
         * EXCLUDES (by construction — added in [args], not here): the executable path, the `--classpath`
         * value (its CONTENT is keyed via the reachable-cone digest; the path varies per machine/shard),
         * the `--function`/entry (keyed separately), and the pure output/UI flags (`--json-ui`,
         * `--trace`, `--verbosity`) which never affect the verdict.
         *
         * NB: `--slice-formula` / `--drop-unused-functions` were tried (~1.8x on non-string proofs) but
         * REVERTED — they break jbmc's STRING REFINEMENT: symbolic string proofs
         * (StringLaws / KotlinStringsLaws / StringBuilderLaws — ~33 of them) falsely REFUTE under
         * slicing while concrete ones pass. The "soundness-neutral" claim held only for the
         * string-free proofs the original benchmark covered. If reintroduced HERE (the only correct
         * place — so the cache key tracks them automatically), GATE to string-free proofs only (like the
         * external-SAT path) and benchmark WITH string proofs in the sample.
         */
        private fun appendVerdictRelevantFlags(cmd: MutableList<String>, unwind: Int,
                                               unwindingAssertions: Boolean, maxStringLength: Int,
                                               solver: String?, externalSatPath: String) {
            cmd.add("--unwind")
            cmd.add(unwind.toString())
            if (unwindingAssertions) {
                cmd.add("--unwinding-assertions")
            }
            if (maxStringLength > 0) {
                cmd.add("--max-nondet-string-length")
                cmd.add(maxStringLength.toString())
            }
            addSolver(cmd, solver, externalSatPath)
        }

        /**
         * The canonical verdict-relevant flag SIGNATURE for a request — the verdict-changing jbmc flags
         * joined by a space, derived from the SAME [appendVerdictRelevantFlags] builder the real command
         * uses. The verdict cache hashes this string so a cached verdict is reused only for a run with
         * the same verdict-relevant flags. It captures flags a per-field key would miss — in particular
         * any flag hard-coded in (or `-Dbmc.*`-driven into) the command — closing the gap where such a
         * flag could silently diverge a cached verdict from reality. See [appendVerdictRelevantFlags] for
         * the exact include/exclude set.
         *
         * NOTE: the resolved solver depends on `-Dbmc.solverCmd`/`-Dbmc.solver` system properties, so
         * this is evaluated against the live properties at key time — exactly when the command is built.
         */
        @JvmStatic
        internal fun verdictRelevantFlags(request: BmcRequest): String {
            val flags = mutableListOf<String>()
            appendVerdictRelevantFlags(flags, request.unwind, request.unwindingAssertions,
                    request.maxStringLength, request.solver, request.externalSatPath)
            return flags.joinToString(" ")
        }

        /**
         * Select the SAT/SMT backend. The RESOLVED external-SAT decision wins first: when
         * [externalSatPath] is non-empty, [SolverPlan] has already decided this proof is safe to run on
         * that fast DIMACS SAT solver (proven text-free, or the expert override) — wire it. Otherwise a
         * per-proof [override] (from `@BmcProof(solver=…)`) wins; otherwise `-Dbmc.solver` (default =
         * JBMC's built-in MiniSat). SMT backends (z3/boolector/cvc4/cvc5) can be much faster on
         * array/bitvector-heavy formulas; any other value is passed to `--sat-solver` (e.g. cadical,
         * glucose). `-Dbmc.solverCmd` points at an external SMT2 solver binary (used with `--smt2`).
         *
         * NOTE: this no longer reads `bmc.externalSat` directly — the global external-SAT property is
         * resolved (with the text/String safety guard) in [SolverPlan] and arrives here as the
         * already-vetted [externalSatPath]. A text-using proof never reaches this method with a non-empty
         * path, so external SAT (String reasoning off) can never engage on a text proof here.
         */
        private fun addSolver(cmd: MutableList<String>, override: String?, externalSatPath: String) {
            // Resolved external SAT solver via DIMACS — the ONE solver swap that actually works on jbmc:
            // it bit-blasts to CNF and hands it to a modern SAT solver, running with string refinement
            // OFF (so numeric/boolean proofs only — the safety guard in SolverPlan guarantees this path
            // is only ever populated for a text-free proof). jbmc's --z3/--smt2 path can't be used (it
            // crashes converting Java string types to SMT2).
            if (externalSatPath.isNotBlank()) {
                cmd.add("--external-sat-solver")
                cmd.add(externalSatPath.trim())
                return
            }
            val external = System.getProperty("bmc.solverCmd")
            if (!external.isNullOrBlank()) {
                cmd.add("--smt2")
                cmd.add("--external-smt2-solver")
                cmd.add(external.trim())
                return
            }
            val solver = if (!override.isNullOrBlank()) override else System.getProperty("bmc.solver")
            if (solver.isNullOrBlank()) {
                return // default: built-in MiniSat
            }
            when (solver.trim().lowercase()) {
                "minisat", "minisat2" -> return
                // The fast-solver names are handled by SolverPlan (the bundled external SAT path) BEFORE
                // this method, gated by the text-use guard. If we reach here with such a name and an
                // EMPTY externalSatPath, SolverPlan deliberately DECLINED (a text proof falling back, or
                // no bundled fast solver on this platform) — so use the engine default, never pass the
                // name to --sat-solver (which would look for an unrelated 'kissat' binary on PATH).
                "kissat", "fast" -> return
                "z3" -> cmd.add("--z3")
                "boolector" -> cmd.add("--boolector")
                "cvc4" -> cmd.add("--cvc4")
                "cvc5" -> cmd.add("--cvc5")
                else -> {
                    cmd.add("--sat-solver")
                    cmd.add(solver.trim())
                }
            }
        }

        /**
         * Run a fully-built `jbmc ...` command and parse its `--json-ui` output. Exit 0 =
         * verified, 10 = violation; any other exit is an engine error → [UNKNOWN][JbmcResult.Verdict.UNKNOWN]
         * (the run was undecided, not a refutation). If `timeoutSeconds > 0` and the process
         * doesn't finish in time, its whole tree is force-killed (the solver is a child of jbmc) and the
         * result is UNKNOWN with a timeout reason.
         *
         * **A RETRYABLE UNKNOWN is re-run ONCE.** The retry is driven by the result's
         * [UnknownKind.retryable] flag, generalizing the old crash-only retry: a non-verdict engine
         * exit (ENGINE_CRASH), unparseable `--json-ui` output (PARSE_FAILURE), and any future retryable
         * kind all self-heal here. jbmc 6.9.0 has rare NONDETERMINISTIC internal aborts (observed in
         * CI: an `Invariant check failed` in `create_parameter_names`, exit 134, on a proof that passes
         * identically before and after), and truncated/interleaved output is likewise transient.
         *
         * **Soundness.** Bounded to EXACTLY one extra run — never a loop. We keep the BETTER outcome:
         * a clean verdict from the retry wins; if the retry is undecided too we keep the retry's
         * result, annotating it "(persisted across a retry)" when it recurs as the SAME retryable kind.
         * Crucially a recurring retryable kind STAYS UNKNOWN — the retry can never turn an UNKNOWN into
         * a VERIFIED, so it never masks a real model hole. A non-retryable kind (TIMEOUT, unwinding,
         * solver-gave-up) fails straight through with no wasted re-solve. The retry is LOUD (printed),
         * never silent, and each attempt counts as a real engine launch.
         */
        internal fun exec(command: List<String>, entryFunction: String, timeoutSeconds: Int = 0,
                          userClasspath: String? = null, profile: Boolean = false,
                          pipelineSeconds: Map<String, Double>? = null): JbmcResult {
            val first = execOnce(command, entryFunction, timeoutSeconds, userClasspath, profile,
                    pipelineSeconds)
            val kind = first.undecidedKind
            if (kind == null || !kind.retryable) {
                return first // a real verdict, or a deterministic (non-retryable) UNKNOWN
            }
            println("  bmc4j: $entryFunction came back UNKNOWN[$kind] (retryable)" +
                    " - re-running the engine once")
            val second = execOnce(command, entryFunction, timeoutSeconds, userClasspath, profile,
                    pipelineSeconds)
            // Keep the better outcome. The retry recovering a real verdict (VERIFIED/REFUTED) wins; a
            // still-undecided retry stays UNKNOWN (never promoted to a pass), annotated when the SAME
            // retryable kind recurred so a persisted flake is named as such.
            if (second.undecidedKind == kind) {
                return JbmcResult.unknown(kind,
                        second.undecidedReason + "\n    (the $kind persisted across a retry)",
                        second.rawOutput)
                        .withProfile(second.profile) // keep the retry's diagnostic breakdown
            }
            return second
        }

        private fun execOnce(command: List<String>, entryFunction: String, timeoutSeconds: Int,
                             userClasspath: String?, profile: Boolean = false,
                             pipelineSeconds: Map<String, Double>? = null): JbmcResult {
            INVOCATIONS.incrementAndGet() // ground-truth engine-launch counter for the verdict cache
            val pb = ProcessBuilder(command)
            pb.redirectErrorStream(false)
            applySolverPath(pb) // so jbmc's --z3 (etc.) finds the solver even if it's not on global PATH
            var p: Process? = null
            // jbmc's stdout is SPILLED to this temp file as it streams, never buffered whole in heap (the
            // latent-OOM fix); the parser reads it incrementally and we delete it in `finally`.
            val outFile = Files.createTempFile("bmc-jbmc-out", ".json").toFile()
            // Engine subprocess wall-clock (launch -> exit/kill), in nanos, for @BmcProfile. On a
            // symex-timeout jbmc emits NO `Runtime` phase line, so this harness-measured wall-clock is the
            // ONLY way to attribute the engine's time to the (incomplete) symex phase. Measured only when
            // profiling, so the normal path is unaffected.
            val engineStartNanos = System.nanoTime()
            try {
                p = pb.start()
                RUNNING.add(p)
                // Drain both streams on background threads so a full pipe buffer can't deadlock the
                // process while we're blocked in waitFor(timeout) (a single-threaded readAllBytes would
                // also ignore the timeout entirely — it blocks until EOF, i.e. until the process exits).
                // STDOUT spills to a temp file (it can be hundreds of MB); STDERR stays in heap (tiny).
                val out = FileGobbler(p.inputStream, outFile)
                val err = StreamGobbler(p.errorStream)
                out.start()
                err.start()

                val finished: Boolean
                if (timeoutSeconds > 0) {
                    finished = p.waitFor(timeoutSeconds.toLong(), TimeUnit.SECONDS)
                } else {
                    p.waitFor()
                    finished = true
                }
                if (!finished) {
                    // Budget exhausted: kill jbmc AND its solver child.
                    killTree(p)
                    p.waitFor() // reap so exitValue/streams settle
                    out.join()
                    err.join()
                    // A bounded head+tail of the spill is enough to diagnose a timeout — never the whole
                    // (possibly huge) output. When @BmcProfile asked for it, parse the breakdown from
                    // whatever the engine streamed up to the kill — the MOST valuable profile case, since
                    // it reveals where a timed-out proof was stuck (symex vs solver) and the hot method.
                    return JbmcResult.unknownTimeout("timed out after ${timeoutSeconds}s", out.headTail())
                            .withProfile(parseProfileIfRequested(profile, outFile, engineStartNanos, pipelineSeconds))
                }
                out.join()
                err.join()
                val exit = p.exitValue()
                if (exit != 0 && exit != 10) {
                    // Engine error (solver gave up / crashed / bad invocation). Undecided, not refuted:
                    // there's no counterexample, so report UNKNOWN rather than a refutation. The detail
                    // (exit code + stderr) is folded into the message for actionable diagnosis. Flagged
                    // as a CRASH structurally so exec() retries it once. The stdout detail/rawOutput is a
                    // bounded head+tail of the spill, not the whole stream.
                    val outHeadTail = out.headTail()
                    return JbmcResult.unknownEngineCrash(
                            engineErrorReason(command, exit, err.text(), outHeadTail), outHeadTail)
                            .withProfile(parseProfileIfRequested(profile, outFile, engineStartNanos, pipelineSeconds))
                }
                // STREAM-parse straight from the spill file: only the verdict element + opaque-symbol
                // STATUS-MESSAGEs are materialized; the flood is read and discarded (heap stays bounded).
                return JbmcOutputParser.parse(outFile, entryFunction, userClasspath)
                        .withProfile(parseProfileIfRequested(profile, outFile, engineStartNanos, pipelineSeconds))
            } catch (e: IOException) {
                throw IllegalStateException(
                        "Could not start JBMC process: " + command.joinToString(" "), e)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                if (p != null) {
                    killTree(p) // cancelled mid-run — don't leave jbmc (and its z3 child) orphaned
                }
                throw IllegalStateException("Interrupted while running JBMC (process terminated)", e)
            } finally {
                if (p != null) {
                    RUNNING.remove(p)
                    if (p.isAlive) {
                        killTree(p)
                    }
                }
                try {
                    Files.deleteIfExists(outFile.toPath()) // never leak the (possibly large) spill file
                } catch (ignored: IOException) {
                    outFile.deleteOnExit() // best effort: clean up at JVM exit if the unlink raced
                }
            }
        }

        /**
         * Parse the per-stage performance breakdown from the spill [outFile] when [profile] is on (the
         * `@BmcProfile` capability), else null. A SEPARATE streaming pass over the same captured stream
         * the verdict comes from — so the normal (non-profiled) path is byte-for-byte unchanged and pays
         * nothing, while a profiled run reads the verbose STATUS-MESSAGEs the verdict parser discards.
         * Best-effort: [JbmcProfile.parse] never throws (the profile is diagnostic, never a verdict),
         * and it tolerates a TRUNCATED file from a timeout kill — exactly the case worth profiling.
         */
        private fun parseProfileIfRequested(profile: Boolean, outFile: File,
                                            engineStartNanos: Long,
                                            pipelineSeconds: Map<String, Double>?): JbmcProfile? {
            if (!profile) {
                return null
            }
            val engineWall = (System.nanoTime() - engineStartNanos) / 1_000_000_000.0
            // Fold in the HARNESS-measured timings: bmc4j's own pre-engine pipeline passes and the engine
            // subprocess wall-clock. The engine wall-clock is what lets the renderer derive a Symex
            // entry on a symex-timeout (no `Runtime` phase line emitted) — symex IS the unwinding phase,
            // so the full engine wall-clock is symex when no phase completed.
            return JbmcProfile.parse(outFile).withHarnessTimings(pipelineSeconds, engineWall)
        }

        /** Build the UNKNOWN reason line for an engine error exit (solver gave up / crashed / bad args). */
        private fun engineErrorReason(command: List<String>, exit: Int,
                                      stderr: String?, stdout: String?): String = buildString {
            append("JBMC exited with engine error code ").append(exit)
                    .append(" (solver gave up or crashed)")
            val solverFlag = solverFlagIn(command)
            if (solverFlag != null) {
                append("; a non-default solver (").append(solverFlag)
                        .append(") was in play — if it isn't installed/visible that's the likely cause")
            }
            var detail = if (!stderr.isNullOrBlank()) stderr else (stdout ?: "")
            detail = detail.trim()
            if (detail.isNotEmpty()) {
                if (detail.length > 600) {
                    detail = detail.substring(0, 600) + " ..."
                }
                append('\n').append(detail)
            }
        }

        private fun solverFlagIn(command: List<String>): String? =
                command.firstOrNull {
                    it == "--z3" || it == "--boolector" || it == "--cvc4" || it == "--cvc5"
                            || it == "--sat-solver" || it == "--external-smt2-solver"
                }

        /**
         * Fail clearly BEFORE launching jbmc if a requested external solver isn't available, instead of
         * letting jbmc fail with a cryptic error. The default (MiniSat, built into jbmc) needs nothing.
         */
        private fun preflightSolver(override: String?) {
            val solverCmd = System.getProperty("bmc.solverCmd")
            if (!solverCmd.isNullOrBlank()) {
                if (!File(solverCmd.trim()).isFile) {
                    throw IllegalStateException(
                            "bmc.solverCmd points at a file that does not exist: " + solverCmd.trim())
                }
                return
            }
            val solver = if (!override.isNullOrBlank()) override else System.getProperty("bmc.solver")
            if (solver.isNullOrBlank()) {
                return // built-in MiniSat
            }
            val name = solver.trim().lowercase()
            // The fast-solver names are resolved by SolverPlan to the bundled binary (or declined) BEFORE
            // jbmc runs — they are never expected on PATH, so don't fail preflight on them.
            if (name == "minisat" || name == "minisat2" || name == "kissat" || name == "fast"
                    || solverBinaryOnPath(name)) {
                return
            }
            val solverPath = System.getProperty("bmc.solverPath", "")
            throw IllegalStateException(
                    "SMT/SAT solver '" + solver.trim() + "' was requested (@BmcProof(solver=...) or" +
                            " -Dbmc.solver) but its binary was not found. Install it and put it on PATH," +
                            " set bmc { solverPath = \"<dir>\" }, or bmc { solverCmd = \"<full path>\" }." +
                            (if (solverPath.isBlank()) "" else "  (bmc.solverPath=$solverPath)"))
        }

        private fun solverBinaryOnPath(name: String): Boolean {
            val dirs = mutableListOf<String>()
            val sp = System.getProperty("bmc.solverPath")
            if (!sp.isNullOrBlank()) {
                dirs.addAll(sp.split(File.pathSeparator))
            }
            val path = System.getenv("PATH") ?: System.getenv("Path") // Windows uses "Path"
            if (path != null) {
                dirs.addAll(path.split(File.pathSeparator))
            }
            val candidates = arrayOf(name, "$name.exe", "$name.bat", "$name.cmd")
            return dirs.asSequence()
                    .filter { it.isNotBlank() }
                    .any { d -> candidates.any { c -> File(d.trim(), c).isFile } }
        }

        /** Prepend `-Dbmc.solverPath` to the subprocess PATH so jbmc can find the solver binary. */
        private fun applySolverPath(pb: ProcessBuilder) {
            val sp = System.getProperty("bmc.solverPath")
            if (sp.isNullOrBlank()) {
                return
            }
            val env = pb.environment()
            // Windows uses "Path"; match case-insensitively and fall back to "PATH".
            val key = env.keys.firstOrNull { it.equals("PATH", ignoreCase = true) } ?: "PATH"
            env[key] = sp.trim() + File.pathSeparator + env.getOrDefault(key, "")
        }
    }
}
