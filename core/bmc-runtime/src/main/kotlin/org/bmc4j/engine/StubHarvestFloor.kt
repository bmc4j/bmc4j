package org.bmc4j.engine

import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
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
 * If the canary's harvest does not surface that stub, stub detection demonstrably does not work
 * against this engine, and the green is reported as an engine-infrastructure UNKNOWN (loud,
 * actionable), never passed.
 *
 * **Cost.** The outcome is a pure function of the engine binary and the bmc4j parser, so it
 * is memoized in-process per engine identity and as a disk marker under
 * `~/.cache/bmc4j/stub-floor/` keyed by (engine identity, [Bmc4jVersion.IDENTITY]) —
 * one small engine run per machine per engine+runtime version. Marker IO fails open to re-running
 * the canary (toward re-verification); the floor itself never fails open.
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

    /** Canary runs actually executed by this JVM — test hook pinning the memo/marker behavior. */
    @JvmField
    val CANARY_RUNS = AtomicInteger()

    /** Floor verdict per engine identity (in-process memo; the disk marker spans JVMs). */
    private val RESULTS = ConcurrentHashMap<String, Boolean>()

    /**
     * Throws an engine-infrastructure [BmcUndecidedError] unless stub detection provably works
     * against [jbmcPath]. Call only when an empty harvest is about to be trusted on a green.
     */
    @JvmStatic
    fun ensure(jbmcPath: String, engineIdentity: String?) {
        val key = (engineIdentity ?: "") + '|' + Bmc4jVersion.IDENTITY
        val ok = RESULTS.computeIfAbsent(key) { k -> holds(jbmcPath, k) }
        if (!ok) {
            // Engine-infrastructure UNKNOWN: never a pass, and never satisfies expect = UNKNOWN.
            throw BmcUndecidedError(
                    "stub detection could not be verified against this engine ($jbmcPath): a" +
                    " canary proof calling a deliberately-missing method harvested no nondet stub," +
                    " so an empty harvest cannot be trusted — a green might silently rest on" +
                    " unmodeled methods. Use the bundled engine, or an engine whose" +
                    " --verbosity 10 --json-ui output reports stubbed methods with the" +
                    " \"new opaque symbol\" message.", true)
        }
    }

    /**
     * Disk-marker fast path around [canaryHarvests]: a marker for this (engine, runtime)
     * key means a prior JVM already proved the floor — same binary + same parser, same outcome.
     * Writes the marker on a fresh success; never writes a negative (a broken setup may be fixed
     * between JVMs, so failure re-probes next run). Marker IO fails open to re-running the canary.
     */
    private fun holds(jbmcPath: String, key: String): Boolean {
        val marker = markerPath(key)
        try {
            if (Files.isRegularFile(marker)) {
                return true
            }
        } catch (ignored: RuntimeException) {
            // unreadable marker -> just re-run the canary
        }
        val ok = canaryHarvests(jbmcPath)
        if (ok) {
            try {
                Files.createDirectories(marker.parent)
                Files.writeString(marker, "ok\n", StandardCharsets.UTF_8)
            } catch (ignored: IOException) {
                // best effort: next JVM re-proves the floor
            } catch (ignored: RuntimeException) {
                // best effort: next JVM re-proves the floor
            }
        }
        return ok
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
     * Run the canary: write the generated probe class to a temp dir, verify it with the SAME
     * executable, flag shape and parser as a real proof, and check the harvest surfaced
     * [CANARY_STUB_FQN]. Any failure to run is `false` — an engine that cannot run a
     * trivial probe cannot vouch for its own stub reporting (loud, never silent).
     */
    private fun canaryHarvests(jbmcPath: String): Boolean {
        CANARY_RUNS.incrementAndGet()
        // One-time per machine/engine/version: explain the pause instead of stalling silently.
        println("bmc4j: verifying stub detection against this engine (one-time canary)")
        var dir: Path? = null
        try {
            dir = Files.createTempDirectory("bmc4j-stub-canary")
            val probe = dir.resolve("bmc").resolve("canary").resolve("Probe.class")
            Files.createDirectories(probe.parent)
            Files.write(probe, probeClass())
            val result = Jbmc(jbmcPath).run(
                    "bmc.canary.Probe", "bmc.canary.Probe.probe", dir.toString(),
                    2, false, 0, false, "", CANARY_TIMEOUT_SECONDS)
            return result.stubbedMethods.contains(CANARY_STUB_FQN)
        } catch (e: IOException) {
            return false // can't run the canary -> can't trust an empty harvest
        } catch (e: RuntimeException) {
            return false
        } finally {
            deleteQuietly(dir)
        }
    }

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
