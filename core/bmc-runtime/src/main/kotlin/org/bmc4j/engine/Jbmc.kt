package org.bmc4j.engine

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
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
            solver: String?, timeoutSeconds: Int = 0): JbmcResult {
        preflightSolver(solver) // fail clearly now if a requested external solver isn't available
        val command = mutableListOf(executable)
        command.addAll(args(entryClass, entryFunction, classpath, unwind, unwindingAssertions,
                maxStringLength, solver))
        return exec(command, entryFunction, timeoutSeconds)
    }

    /** Drains a process stream to a buffer on its own thread (so reads can't deadlock or block waitFor). */
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
        internal fun args(entryClass: String, entryFunction: String, classpath: String,
                          unwind: Int, unwindingAssertions: Boolean, maxStringLength: Int,
                          solver: String?): List<String> {
            val cmd = mutableListOf<String>()
            cmd.add(entryClass)
            cmd.add("--classpath")
            cmd.add(classpath)
            cmd.add("--function")
            cmd.add(entryFunction)
            cmd.add("--unwind")
            cmd.add(unwind.toString())
            if (unwindingAssertions) {
                cmd.add("--unwinding-assertions")
            }
            if (maxStringLength > 0) {
                cmd.add("--max-nondet-string-length")
                cmd.add(maxStringLength.toString())
            }
            addSolver(cmd, solver)
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
         * Select the SAT/SMT backend. A per-proof [override] (from `@BmcProof(solver=…)`)
         * wins; otherwise `-Dbmc.solver` (default = JBMC's built-in MiniSat). SMT backends
         * (z3/boolector/cvc4/cvc5) can be much faster on array/bitvector-heavy formulas; any other value
         * is passed to `--sat-solver` (e.g. cadical, glucose). `-Dbmc.solverCmd` points at an
         * external SMT2 solver binary (used with `--smt2`).
         */
        private fun addSolver(cmd: MutableList<String>, override: String?) {
            // External SAT solver (e.g. CryptoMiniSat) via DIMACS — the ONE solver swap that actually
            // works on jbmc: it bit-blasts to CNF and hands it to a modern SAT solver, bypassing string
            // refinement (so numeric/boolean proofs only). jbmc's --z3/--smt2 path can't be used (it
            // crashes converting Java string types to SMT2). Configured globally via bmc.externalSat.
            val externalSat = System.getProperty("bmc.externalSat")
            if (!externalSat.isNullOrBlank()) {
                cmd.add("--external-sat-solver")
                cmd.add(externalSat.trim())
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
        internal fun exec(command: List<String>, entryFunction: String, timeoutSeconds: Int = 0): JbmcResult {
            val first = execOnce(command, entryFunction, timeoutSeconds)
            val kind = first.undecidedKind
            if (kind == null || !kind.retryable) {
                return first // a real verdict, or a deterministic (non-retryable) UNKNOWN
            }
            println("  bmc4j: $entryFunction came back UNKNOWN[$kind] (retryable)" +
                    " - re-running the engine once")
            val second = execOnce(command, entryFunction, timeoutSeconds)
            // Keep the better outcome. The retry recovering a real verdict (VERIFIED/REFUTED) wins; a
            // still-undecided retry stays UNKNOWN (never promoted to a pass), annotated when the SAME
            // retryable kind recurred so a persisted flake is named as such.
            if (second.undecidedKind == kind) {
                return JbmcResult.unknown(kind,
                        second.undecidedReason + "\n    (the $kind persisted across a retry)",
                        second.rawOutput)
            }
            return second
        }

        private fun execOnce(command: List<String>, entryFunction: String, timeoutSeconds: Int): JbmcResult {
            INVOCATIONS.incrementAndGet() // ground-truth engine-launch counter for the verdict cache
            val pb = ProcessBuilder(command)
            pb.redirectErrorStream(false)
            applySolverPath(pb) // so jbmc's --z3 (etc.) finds the solver even if it's not on global PATH
            var p: Process? = null
            try {
                p = pb.start()
                RUNNING.add(p)
                // Drain both streams on background threads so a full pipe buffer can't deadlock the
                // process while we're blocked in waitFor(timeout) (a single-threaded readAllBytes would
                // also ignore the timeout entirely — it blocks until EOF, i.e. until the process exits).
                val out = StreamGobbler(p.inputStream)
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
                    return JbmcResult.unknownTimeout("timed out after ${timeoutSeconds}s", out.text())
                }
                out.join()
                err.join()
                val exit = p.exitValue()
                if (exit != 0 && exit != 10) {
                    // Engine error (solver gave up / crashed / bad invocation). Undecided, not refuted:
                    // there's no counterexample, so report UNKNOWN rather than a refutation. The detail
                    // (exit code + stderr) is folded into the message for actionable diagnosis. Flagged
                    // as a CRASH structurally so exec() retries it once.
                    return JbmcResult.unknownEngineCrash(
                            engineErrorReason(command, exit, err.text(), out.text()), out.text())
                }
                return JbmcOutputParser.parse(out.text(), entryFunction)
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
            }
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
            if (name == "minisat" || name == "minisat2" || solverBinaryOnPath(name)) {
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
