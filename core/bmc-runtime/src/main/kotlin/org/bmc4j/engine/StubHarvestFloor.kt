package org.bmc4j.engine

import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Positive floor for nondet-stub detection: prove the [JbmcOutputParser] stub harvest
 * actually *works* against the engine in use before trusting an **empty** harvest.
 *
 * The harvest keys on a literal engine string (`"new opaque symbol: method '"` in the
 * `--verbosity 10 --json-ui` stream). Against the bundled engine that format is pinned by the
 * parser tests, but a consumer pointing `-Dbmc.jbmc` at another build has no such guarantee:
 * a format drift **silently empties** the harvest — greens lose their honesty footnotes and
 * `strictStubs` stops gating, with nothing visible anywhere. That silent-open failure is the
 * hole this floor closes.
 *
 * **Mechanism.** The first time a VERIFIED result with an empty harvest would be trusted
 * (a non-empty harvest needs no floor — the signal's presence proves the parse), run a canary:
 * a generated class whose entry calls a method of a class that does not exist — the one situation
 * that MUST produce a stub — through the same executable, flag shape and parser as real proofs.
 *
 * **Three outcomes, not two.** The canary's *bytecode + engine + parser* are deterministic, but the
 * *run* can fail to complete under CI load (timeout, a transient engine abort, truncated output).
 * A run that did not complete observed NOTHING about stub detection, so it must not be confused with
 * a run that completed and genuinely surfaced no stub (the format-drift case the floor exists for).
 * [classify] therefore maps the run to one of:
 *  - [CanaryOutcome.PROVEN] — the harvest surfaced the canary stub: stub detection works.
 *  - [CanaryOutcome.BROKEN] — the run produced trustworthy verdict output but NO canary stub: the
 *    engine's stub reporting does not match the parser (format drift). This is the genuine failure;
 *    the green is reported as a loud engine-infrastructure UNKNOWN, never passed.
 *  - [CanaryOutcome.INCONCLUSIVE] — the run did not complete (timeout / engine abort / unparseable /
 *    could-not-launch). The canary observed nothing, so this is NOT evidence the engine is broken;
 *    it is retried (the canary is trivial), and a residual inconclusive surfaces a DISTINCT, honest
 *    infrastructure UNKNOWN ("the canary could not complete") that is NOT memoized — the next proof
 *    or JVM re-probes — so a transient hiccup never poisons a whole proof leg as "format drift".
 *
 * **Cost & single-flight.** A PROVEN outcome is a pure function of the engine binary and the bmc4j
 * parser, so it is memoized in-process per engine identity and as a disk marker under
 * `~/.cache/bmc4j/stub-floor/` keyed by (engine identity, [Bmc4jVersion.IDENTITY]). The canary run
 * itself is serialized across processes by a sibling `.lock` file AND draws a [JbmcConcurrency]
 * permit, so a fresh runner whose first wave of proof workers all hit the empty-harvest floor at once
 * fires ONE canary (the rest wait on the lock, then see the marker), never N unpermitted canaries
 * piled onto an already CPU-saturated proof leg — the contention that turned the trivial canary into
 * a timeout in the first place. Marker IO fails open to re-running the canary; the floor itself never
 * fails open on a BROKEN engine.
 */
internal object StubHarvestFloor {

    /**
     * The stub the canary MUST harvest: a method of a deliberately-missing class. The package is
     * chosen to be plainly user-shaped — outside every [StubFilter] noise rule (pinned by
     * `StubHarvestFloorTest`) — so the floor exercises the full pipeline the real harvest
     * uses: opaque-marker parse, FQN extraction, signal filter.
     */
    const val CANARY_STUB_FQN = "bmc.canary.Missing.gone"

    /** Wall-clock budget for the canary run — trivial work; a healthy engine takes seconds. */
    private const val CANARY_TIMEOUT_SECONDS = 120

    /**
     * Floor-level re-runs of an INCONCLUSIVE canary before giving up. The canary is a trivial probe,
     * so an inconclusive run (timeout under load / transient abort / truncated output) is a flake that
     * a fresh, permit-gated, serialized attempt can clear — distinct from a BROKEN engine, which is
     * deterministic and must fail straight through. [Jbmc.exec] already retries crash/parse-failure
     * once internally; these are the floor's own additional, serialized attempts on top.
     */
    private const val INCONCLUSIVE_RETRIES = 2

    /** The three things a canary run can tell us about the engine's stub detection. */
    internal enum class CanaryOutcome {
        /** The harvest surfaced the canary stub — stub detection provably works against this engine. */
        PROVEN,

        /** The run produced trustworthy verdict output but no canary stub — format drift (engine broken). */
        BROKEN,

        /** The run did not complete (timeout / abort / unparseable / could-not-launch) — observed nothing. */
        INCONCLUSIVE
    }

    /** Canary runs actually executed by this JVM — test hook pinning the memo/marker behavior. */
    @JvmField
    val CANARY_RUNS = AtomicInteger()

    /** Floor verdict per engine identity (in-process memo; the disk marker spans JVMs). Only ever
     *  holds `true` (PROVEN) or `false` (BROKEN) — an INCONCLUSIVE result is never memoized. */
    private val RESULTS = ConcurrentHashMap<String, Boolean>()

    /**
     * The single-run canary, behind a swappable reference so a test can drive the full ensure() /
     * lock / retry / memoization path with a chosen outcome (e.g. a synthetic INCONCLUSIVE) without a
     * real engine. Production always uses [canaryHarvests]; tests set and reset this around a case.
     * Every assignment counts a [CANARY_RUNS] tick via the real runner, so the test hook preserves the
     * memo/retry invariants the existing tests assert.
     */
    @JvmField
    var canaryRunner: (String) -> CanaryOutcome = ::canaryHarvests

    /**
     * Throws an engine-infrastructure [BmcUndecidedError] unless stub detection provably works
     * against [jbmcPath]. Call only when an empty harvest is about to be trusted on a green.
     *
     * A BROKEN engine (format drift) fails LOUD and is memoized. An INCONCLUSIVE canary (the run could
     * not complete) also fails this proof — soundness: an unverified floor cannot trust an empty
     * harvest — but with a DISTINCT message and WITHOUT memoizing, so the next proof/JVM re-probes
     * instead of every proof sharing a sticky false "format drift" verdict.
     */
    @JvmStatic
    fun ensure(jbmcPath: String, engineIdentity: String?) {
        val key = (engineIdentity ?: "") + '|' + Bmc4jVersion.IDENTITY
        // Memoized PROVEN/BROKEN short-circuits before any locking; an absent entry means we must probe.
        RESULTS[key]?.let { proven ->
            if (proven) return else throw brokenError(jbmcPath)
        }
        when (resolveUnderLock(jbmcPath, key)) {
            CanaryOutcome.PROVEN -> return
            CanaryOutcome.BROKEN -> throw brokenError(jbmcPath)
            CanaryOutcome.INCONCLUSIVE -> throw inconclusiveError(jbmcPath)
        }
    }

    /** The loud format-drift UNKNOWN: a completed canary that harvested no stub — the engine's stub
     *  reporting does not match the parser. This is the genuine failure the floor exists to catch. */
    private fun brokenError(jbmcPath: String): BmcUndecidedError = BmcUndecidedError(
            "stub detection could not be verified against this engine ($jbmcPath): a" +
            " canary proof calling a deliberately-missing method harvested no nondet stub," +
            " so an empty harvest cannot be trusted — a green might silently rest on" +
            " unmodeled methods. Use the bundled engine, or an engine whose" +
            " --verbosity 10 --json-ui output reports stubbed methods with the" +
            " \"new opaque symbol\" message.", true)

    /** The infrastructure UNKNOWN for a canary that never COMPLETED (timeout / abort / unparseable).
     *  Distinct from [brokenError]: this says nothing about the engine's stub FORMAT — the canary
     *  simply could not run to a trustworthy result under load — so it is not memoized and re-probes. */
    private fun inconclusiveError(jbmcPath: String): BmcUndecidedError = BmcUndecidedError(
            "stub detection could not be verified against this engine ($jbmcPath): the canary" +
            " proof did not complete (it timed out, the engine aborted, or its output was" +
            " unparseable) even after retries, so an empty harvest cannot be trusted on this run." +
            " This is an infrastructure hiccup (likely CPU starvation under load), not a format" +
            " drift — it is not cached, so a subsequent run re-verifies the floor.", true)

    /**
     * Single-flight the canary across processes with a `.lock` file beside the marker, then probe.
     * Holding the lock: re-check the in-process memo and the disk marker (a peer may have just proved
     * the floor), and only if still unproven run the canary. A PROVEN outcome writes the marker and
     * memoizes `true`; a BROKEN outcome memoizes `false`; an INCONCLUSIVE outcome memoizes nothing.
     * If the lock itself can't be taken (IO error), fall back to probing unserialized — correctness is
     * unaffected; only the de-duplication of concurrent first-wave canaries is lost.
     */
    private fun resolveUnderLock(jbmcPath: String, key: String): CanaryOutcome {
        val marker = markerPath(key)
        try {
            Files.createDirectories(marker.parent)
        } catch (ignored: IOException) {
            // best effort — the canary still runs; only the marker write may later fail open
        } catch (ignored: RuntimeException) {
        }
        val lock = marker.resolveSibling(marker.fileName.toString() + ".lock")
        val channel: FileChannel
        val held: FileLock
        try {
            channel = FileChannel.open(lock, StandardOpenOption.CREATE, StandardOpenOption.WRITE)
            held = channel.lock() // blocks until the first-wave canary (in any process) finishes
        } catch (ignored: IOException) {
            // Couldn't lock (e.g. a filesystem without OS locks): probe without cross-process serialization.
            return resolve(jbmcPath, key, marker)
        } catch (ignored: RuntimeException) {
            return resolve(jbmcPath, key, marker)
        }
        try {
            return resolve(jbmcPath, key, marker)
        } finally {
            try {
                held.release()
            } catch (ignored: IOException) {
            }
            try {
                channel.close()
            } catch (ignored: IOException) {
            }
        }
    }

    /**
     * The PROVEN-fast-path + probe, run with the cross-process lock already held (or, on a lock
     * failure, unserialized). Re-checks the in-process memo and the disk marker first — under the lock
     * a peer may have just proved the floor — so the actual canary fires at most once per (engine,
     * runtime) across the whole runner when starting fresh.
     */
    private fun resolve(jbmcPath: String, key: String, marker: Path): CanaryOutcome {
        RESULTS[key]?.let { return if (it) CanaryOutcome.PROVEN else CanaryOutcome.BROKEN }
        try {
            if (Files.isRegularFile(marker)) {
                RESULTS[key] = true
                return CanaryOutcome.PROVEN
            }
        } catch (ignored: RuntimeException) {
            // unreadable marker -> just run the canary
        }
        val outcome = canaryWithRetries(jbmcPath)
        when (outcome) {
            CanaryOutcome.PROVEN -> {
                RESULTS[key] = true
                try {
                    Files.writeString(marker, "ok\n", StandardCharsets.UTF_8)
                } catch (ignored: IOException) {
                    // best effort: next JVM re-proves the floor
                } catch (ignored: RuntimeException) {
                }
            }
            CanaryOutcome.BROKEN -> RESULTS[key] = false
            // INCONCLUSIVE: never memoized, never marked — the next probe re-verifies.
            CanaryOutcome.INCONCLUSIVE -> {}
        }
        return outcome
    }

    /**
     * Run the canary, retrying ONLY an [CanaryOutcome.INCONCLUSIVE] outcome up to [INCONCLUSIVE_RETRIES]
     * extra times. A PROVEN or BROKEN result is a real, deterministic answer and returns immediately;
     * an inconclusive run (the trivial probe got starved/aborted/truncated under load) is a flake worth
     * one more serialized, permit-gated attempt. Stays loud on a genuinely BROKEN engine.
     */
    private fun canaryWithRetries(jbmcPath: String): CanaryOutcome {
        var outcome = canaryRunner(jbmcPath)
        var tries = 0
        while (outcome == CanaryOutcome.INCONCLUSIVE && tries < INCONCLUSIVE_RETRIES) {
            tries++
            println("bmc4j: stub-detection canary did not complete (attempt ${tries + 1}) - re-running")
            outcome = canaryRunner(jbmcPath)
        }
        return outcome
    }

    /** SHA-256 hex of [key] — the disk marker's file name. */
    private fun digest(key: String): String {
        val md = MessageDigest.getInstance("SHA-256") // present on every JVM
        return md.digest(key.toByteArray(StandardCharsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
    }

    /** `~/.cache/bmc4j/stub-floor/<digest>` — sibling of the engine extraction cache.
     *  Exposed so the test can plant/inspect markers under a redirected `user.home`. */
    @JvmStatic
    fun markerPath(key: String): Path =
            Path.of(System.getProperty("user.home"), ".cache", "bmc4j", "stub-floor", digest(key))

    /**
     * Classify a canary [result] into one of the three [CanaryOutcome]s. Pure (no IO) and exposed for
     * the test so the timeout / crash / parse-failure / format-drift mapping is pinned directly:
     *  - the canary stub harvested            -> PROVEN (stub detection works; verdict is irrelevant —
     *    the probe carries no reachability markers, so a healthy run is UNKNOWN[SOLVER_GAVE_UP] yet
     *    still surfaces the stub via the parallel harvest);
     *  - a NON-completion UNKNOWN             -> INCONCLUSIVE. TIMEOUT, ENGINE_CRASH and PARSE_FAILURE
     *    mean the run produced no trustworthy verdict output, so its empty harvest is meaningless;
     *  - any other completed result w/o stub  -> BROKEN. The run reached a trustworthy verdict
     *    (VERIFIED / REFUTED / the markerless SOLVER_GAVE_UP the probe normally yields / an unwinding
     *    firing) but the harvest is empty — the engine's stub reporting genuinely doesn't match the
     *    parser (format drift). This is the loud failure the floor exists for.
     */
    internal fun classify(result: JbmcResult): CanaryOutcome {
        if (result.stubbedMethods.contains(CANARY_STUB_FQN)) {
            return CanaryOutcome.PROVEN
        }
        return when (result.undecidedKind) {
            UnknownKind.TIMEOUT, UnknownKind.ENGINE_CRASH, UnknownKind.PARSE_FAILURE ->
                CanaryOutcome.INCONCLUSIVE
            else -> CanaryOutcome.BROKEN
        }
    }

    /**
     * Run the canary once: write the generated probe class to a temp dir, verify it with the SAME
     * executable, flag shape and parser as a real proof, and [classify] the harvest. The run draws a
     * [JbmcConcurrency] permit so it counts against the same JVM-wide jbmc budget as real proofs
     * (never an extra unbounded process on top of a saturated proof leg).
     *
     * Two distinct could-not-run failures: a binary that won't even START (a bad/missing `-Dbmc.jbmc`
     * path — [Jbmc.run] throws [IllegalStateException] wrapping the launch [IOException]) is a
     * DETERMINISTIC infrastructure error — retrying is pointless and an engine that can't run can't
     * vouch — so it is [CanaryOutcome.BROKEN] (loud + memoized). A failure to prepare the temp probe,
     * or any other unexpected runtime fault, is conservatively [CanaryOutcome.INCONCLUSIVE] (re-probe).
     */
    private fun canaryHarvests(jbmcPath: String): CanaryOutcome {
        CANARY_RUNS.incrementAndGet()
        // One-time per machine/engine/version: explain the pause instead of stalling silently.
        println("bmc4j: verifying stub detection against this engine (one-time canary)")
        var dir: Path? = null
        try {
            dir = Files.createTempDirectory("bmc4j-stub-canary")
            val probe = dir.resolve("bmc").resolve("canary").resolve("Probe.class")
            Files.createDirectories(probe.parent)
            Files.write(probe, probeClass())
            val canaryDir = dir
            val result = JbmcConcurrency.withPermit {
                Jbmc(jbmcPath).run(
                        "bmc.canary.Probe", "bmc.canary.Probe.probe", canaryDir.toString(),
                        2, false, 0, "", CANARY_TIMEOUT_SECONDS)
            }
            return classify(result)
        } catch (e: IOException) {
            // Couldn't write the probe / temp dir -> observed nothing about the engine -> re-probe later.
            return CanaryOutcome.INCONCLUSIVE
        } catch (e: IllegalStateException) {
            // [Jbmc.run] wraps a process-launch IOException as IllegalStateException: a binary that can't
            // even start is a deterministic infrastructure failure (bad path / not executable), not a
            // transient — retrying won't help, so fail loud and memoize.
            return if (isLaunchFailure(e)) CanaryOutcome.BROKEN else CanaryOutcome.INCONCLUSIVE
        } catch (e: RuntimeException) {
            return CanaryOutcome.INCONCLUSIVE
        } finally {
            deleteQuietly(dir)
        }
    }

    /** True when [e] is [Jbmc.run]'s "could not start the process" wrapper (a launch [IOException]),
     *  as opposed to e.g. an interruption — keyed off the cause type so it survives message changes. */
    private fun isLaunchFailure(e: IllegalStateException): Boolean = e.cause is IOException

    /**
     * Bytecode for `public class bmc.canary.Probe { public static void probe() {
     * bmc.canary.Missing.gone(); } }` — `bmc.canary.Missing` deliberately does not exist, so
     * the engine MUST stub the call and report it. (Shape pinned by `StubHarvestFloorTest`.)
     */
    @JvmStatic
    fun probeClass(): ByteArray {
        val cw = ClassWriter(0)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "bmc/canary/Probe", null, "java/lang/Object", null)
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "probe", "()V", null, null)
        mv.visitCode()
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "bmc/canary/Missing", "gone", "()V", false)
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(0, 0)
        mv.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }

    private fun deleteQuietly(dir: Path?) {
        if (dir == null) {
            return
        }
        try {
            Files.walk(dir).use { walk ->
                walk.sorted(Comparator.reverseOrder()).forEach { p ->
                    try {
                        Files.deleteIfExists(p)
                    } catch (ignored: IOException) {
                        // best effort
                    }
                }
            }
        } catch (ignored: IOException) {
            // best effort
        } catch (ignored: RuntimeException) {
            // best effort
        }
    }
}
