package org.bmc4j.engine

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * Hoists the environment-INDEPENDENT half of [JbmcBackend.prepareClasspath]'s rewrite chain out of the
 * test JVM and into a cacheable Gradle unit of work.
 *
 * The runtime extension runs twelve rewrite passes before every proof; six of them are pure functions of
 * the input bytes alone — they read no environment, no system property, no per-proof state:
 *
 *  - [CoroutineBytecode] (LVT strip), [StringBytecode], [LambdaBytecode], [SwitchBytecode],
 *    [ResidualIndyBytecode], [MathBytecode].
 *
 * These six are the expensive ones on a real consumer classpath (bmc4j + Spring + Kotlin jars), and
 * being input-only they can be computed ONCE — by the Gradle plugin's mirror task — and reused across
 * cold JVMs and CI runs via Gradle's own build cache. The other six stay in the test JVM because they
 * depend on the run environment or the specific proof: [ConfigBytecode] bakes this run's real
 * env/property values, [KotlinParamBytecode] depends on `-Dbmc.kotlinNullableParams`, and the contract /
 * domain-split / reachability / purity / slice passes are per-proof.
 *
 * ## Two entry points
 *
 *  - [mirror] runs in the plugin's classloader-isolated Gradle worker. It mirrors every classpath entry
 *    (dir -> dir, jar -> jar) through the six passes into a Gradle-owned output directory and writes a
 *    `manifest.txt` mapping each ORIGINAL entry to its mirrored counterpart. The work lands under the
 *    task's `@OutputDirectory`, so Gradle (not `~/.cache/bmc4j/`) owns and caches it.
 *  - [substitute] runs in the test JVM. Given the directory the plugin produced, it swaps each entry of
 *    the real analysis classpath for its pre-mirrored counterpart, so [JbmcBackend.prepareClasspath]
 *    starts from already-rewritten bytecode and SKIPS the six passes. The remaining (environment /
 *    per-proof) passes run on the substituted classpath exactly as before.
 *
 * ## Soundness
 *
 * The mirrored bytecode is byte-for-byte what the six in-JVM passes would have produced — this class
 * literally calls the same pass implementations, only redirecting where their content-hashed mirrors
 * land (via [ClasspathMirror.withCacheRoot]). The per-entry cache key is unchanged: it still folds in
 * [Bmc4jVersion.IDENTITY] (see [ClasspathMirror.effectiveExtraKey]), so a stale mirror or a
 * version/semantics mismatch can never be reused. The Gradle task additionally records [Bmc4jVersion.IDENTITY]
 * as a task input, and [substitute] re-checks it against the manifest before trusting the mirror — a
 * mismatch falls back to mirroring in-JVM, never serves a stale rewrite. The verdict-cache key and the
 * witness renderer both continue to key on the ORIGINAL (unsubstituted) classpath, so this is purely a
 * relocation of WHERE the rewrite happens, not WHAT verdict it yields.
 */
object GradleClasspathMirror {

    /** First line of the manifest: the runtime semantics identity the mirror was produced under. */
    private const val IDENTITY_PREFIX = "bmc4j-mirror-identity "
    private const val MANIFEST_NAME = "manifest.txt"
    private const val MIRRORS_DIR = "mirrors"

    /**
     * Mirror [classpath] through the six environment-independent rewrite passes into [outputDir],
     * writing a manifest that [substitute] reads. Called from the Gradle worker.
     *
     * The mirrors land in content-hashed subdirectories of `outputDir/mirrors/` (the same atomic,
     * `.done`-marked, content-keyed scheme [ClasspathMirror] uses for `~/.cache`), and the manifest maps
     * each original entry to its final mirrored path RELATIVE to [outputDir] — so the output is
     * relocatable and Gradle can cache it across runners with a path-independent key.
     */
    @JvmStatic
    fun mirror(classpath: String, outputDir: Path) {
        Files.createDirectories(outputDir)
        val mirrorRoot = outputDir.resolve(MIRRORS_DIR)
        Files.createDirectories(mirrorRoot)

        // Run the six passes in the SAME order JbmcBackend.prepareClasspath runs them, each chaining over
        // the previous pass's output, with every mirror redirected under the Gradle-owned root.
        val finalClasspath = ClasspathMirror.withCacheRoot(mirrorRoot) {
            var cp = CoroutineBytecode.strip(classpath)
            cp = StringBytecode.rewrite(cp)
            cp = LambdaBytecode.rewrite(cp)
            cp = SwitchBytecode.rewrite(cp)
            cp = ResidualIndyBytecode.rewrite(cp)
            cp = MathBytecode.rewrite(cp)
            cp
        }

        val originals = classpath.split(File.pathSeparator).filter { it.isNotEmpty() }
        val finals = finalClasspath.split(File.pathSeparator).filter { it.isNotEmpty() }
        // mirror() is 1:1 and order-preserving over non-empty entries; a count mismatch means an entry was
        // dropped/added, which would silently desync the mapping — fail loud rather than serve a wrong one.
        if (originals.size != finals.size) {
            throw IllegalStateException(
                    "bmc4j mirror produced ${finals.size} entries for ${originals.size} inputs; " +
                            "refusing to write a desynchronised manifest.")
        }

        val sb = StringBuilder()
        sb.append(IDENTITY_PREFIX).append(Bmc4jVersion.IDENTITY).append('\n')
        for (i in originals.indices) {
            val rel = outputDir.relativize(Path.of(finals[i])).toString().replace('\\', '/')
            // "<original>\t<mirrored-relative-to-outputDir>" — TAB-separated; classpath entries never
            // contain a tab, and the relative path keeps the manifest portable across runners.
            sb.append(originals[i]).append('\t').append(rel).append('\n')
        }
        Files.write(outputDir.resolve(MANIFEST_NAME), sb.toString().toByteArray(StandardCharsets.UTF_8))
    }

    /**
     * In the test JVM: swap each entry of [originalClasspath] for its pre-mirrored counterpart produced
     * by the plugin in [outputDir]. Entries the plugin didn't mirror (e.g. a non-class container, or one
     * absent from the manifest) pass through unchanged — the remaining in-JVM passes still rewrite them,
     * so soundness is preserved, only the speed-up is forfeited for those entries.
     *
     * Returns the original classpath unchanged (so the caller mirrors in-JVM as before) when the mirror
     * is missing, unreadable, or was produced under a DIFFERENT [Bmc4jVersion.IDENTITY] — a version /
     * semantics mismatch must never serve a stale rewrite.
     */
    @JvmStatic
    fun substitute(originalClasspath: String, outputDir: Path): String {
        val map = readMap(outputDir)
        if (map.isEmpty()) {
            return originalClasspath
        }
        val out = StringBuilder()
        var first = true
        for (entry in originalClasspath.split(File.pathSeparator)) {
            if (entry.isEmpty()) {
                continue
            }
            if (!first) {
                out.append(File.pathSeparator)
            }
            first = false
            out.append(map[entry]?.toString() ?: entry)
        }
        return out.toString()
    }

    /**
     * The set of ORIGINAL classpath entries the plugin mirror in [outputDir] actually covers (a usable,
     * identity-matched mirror exists on disk). The runtime uses this to run the six in-JVM passes over
     * exactly the entries the plugin did NOT pre-mirror (e.g. the consumer's own freshly-compiled class
     * dirs, which aren't on the resolved dependency classpath the task takes as input) — so every entry
     * is desugared exactly once, by the plugin or in-JVM, never zero times.
     */
    @JvmStatic
    fun coveredEntries(outputDir: Path): Set<String> = readMap(outputDir).keys

    /** Read the manifest's (original entry -> mirrored path) map, or an empty map if the mirror is
     *  missing, unreadable, produced under a different [Bmc4jVersion.IDENTITY], or has no on-disk
     *  targets. An empty map means "trust nothing here" — the caller desugars in-JVM. */
    private fun readMap(outputDir: Path): Map<String, Path> {
        val manifest = outputDir.resolve(MANIFEST_NAME)
        if (!Files.isRegularFile(manifest)) {
            return emptyMap()
        }
        val lines = try {
            Files.readAllLines(manifest, StandardCharsets.UTF_8)
        } catch (e: java.io.IOException) {
            return emptyMap()
        }
        if (lines.isEmpty() || lines[0] != IDENTITY_PREFIX + Bmc4jVersion.IDENTITY) {
            // No identity header, or a mirror from a different runtime semantics — don't trust it.
            return emptyMap()
        }
        val map = HashMap<String, Path>()
        for (i in 1 until lines.size) {
            val line = lines[i]
            val tab = line.indexOf('\t')
            if (tab <= 0) {
                continue
            }
            val original = line.substring(0, tab)
            val mirrored = outputDir.resolve(line.substring(tab + 1)).normalize()
            // Only trust a mirrored entry that actually exists on disk; a missing one is dropped so the
            // caller desugars the original in-JVM, never points the engine at a phantom path.
            if (Files.exists(mirrored)) {
                map[original] = mirrored
            }
        }
        return map
    }
}
