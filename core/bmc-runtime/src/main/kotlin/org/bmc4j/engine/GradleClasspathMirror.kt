package org.bmc4j.engine

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * Hoists the RUN-WIDE half of [JbmcBackend.prepareClasspath]'s rewrite chain — every pass whose result
 * is a function of the bytecode plus at most run-wide state (not the specific proof) — out of the test
 * JVM and into a cacheable Gradle unit of work.
 *
 * The runtime extension runs a chain of rewrite passes before every proof. Every pass whose result is a
 * function of the bytecode plus at most RUN-WIDE state (not the specific proof) is computed ONCE — by the
 * Gradle plugin's mirror task — and reused across cold JVMs and CI runs via Gradle's own build cache,
 * instead of every cold test JVM re-paying a full ASM walk of every dependency jar:
 *
 *  - the six pure desugars [CoroutineBytecode] (LVT strip), [StringBytecode], [LambdaBytecode],
 *    [SwitchBytecode], [ResidualIndyBytecode], [MathBytecode], fused into ONE walk
 *    ([ClasspathMirror.mirrorAll]);
 *  - [ConfigBytecode] — bakes `Bmc.*From*("KEY")` to this run's real env/property value. The forked test
 *    JVM's config properties don't reach the worker JVM, so the plugin forwards them; the worker records
 *    the resolved config in the manifest and the runtime re-validates it ([configMatches]) — a config
 *    flip since the mirror was built falls back to a full in-JVM rewrite, never serves a stale bake;
 *  - [KotlinParamBytecode] — relaxes the non-null parameter prologue inside `@BmcProof` methods; its
 *    only input beyond the bytecode is the run-wide `bmc.kotlinNullableParams` flag, which the task
 *    records as an `@Input` (so flipping it re-mirrors) and folds into the mirror cache key;
 *  - [ReachabilityBytecode] — injects the vacuity marker into `@BmcProof` returns; a pure function of
 *    the bytecode.
 *
 * These are the expensive ones on a real consumer classpath (bmc4j + Spring + Kotlin jars): each is a
 * full ASM walk of every entry, so under cold parallel proof lanes they contend on the per-user
 * `~/.cache` and balloon. The remaining passes stay in the test JVM because they depend on the specific
 * proof with no single shared artifact: the contract / domain-split / purity / slice passes are per-proof.
 *
 * ## Two entry points
 *
 *  - [mirror] runs in the plugin's classloader-isolated Gradle worker. It mirrors every classpath entry
 *    (dir -> dir, jar -> jar) through the fused desugars then Config then KotlinParam then Reachability
 *    into a Gradle-owned output directory and writes a `manifest.txt` mapping each ORIGINAL entry to its
 *    mirrored counterpart (plus the identity + resolved-config header lines). The work lands under the
 *    task's `@OutputDirectory`, so Gradle (not `~/.cache/bmc4j/`) owns and caches it.
 *  - [substitute] runs in the test JVM. Given the directory the plugin produced, it swaps each entry of
 *    the real analysis classpath for its pre-mirrored counterpart, so [JbmcBackend.prepareClasspath]
 *    starts from already-rewritten bytecode and SKIPS the hoisted passes. The remaining (per-proof)
 *    passes run on the substituted classpath exactly as before. Entries are matched by CANONICAL path
 *    ([canonicalKey]): the test worker can spell a `java.class.path` entry differently than the task's
 *    file path (notably doubled backslashes on Windows), so a raw compare would silently miss every entry.
 *
 * ## Ordering / commutativity
 *
 * The canonical in-JVM order is `6-desugar -> Config -> KotlinParam -> (DomainSplit) -> Reachability`,
 * with the per-proof contract rewrite on the already-desugared classpath and DomainSplit between
 * KotlinParam and Reachability. A covered entry reaches the test JVM with `6-desugar + Config +
 * KotlinParam + Reachability` already applied, and the remaining in-JVM passes (contracts, then — for a
 * domainSplit cover run only — DomainSplit + a Reachability re-run for the injected return) run on top.
 * Contracts thus run AFTER Config/KotlinParam/Reachability rather than before. This is byte-identical
 * because the passes touch DISJOINT instruction sites: Config rewrites `Bmc.*From*` call sites, KotlinParam
 * the `checkNotNullParameter` prologue intrinsic (and a `@NotNull` parameter annotation), Reachability the
 * `return` opcodes, and contracts other invoke sites — no pass adds, removes, or moves an instruction
 * another keys on, so they commute. The byte-identity test (`GradleClasspathMirrorTest`) pins this against
 * the in-JVM pipeline over a representative classpath including project class dirs.
 *
 * ## Soundness
 *
 * The mirrored bytecode is byte-for-byte what the hoisted in-JVM passes would have produced — this class
 * literally calls the same pass implementations, only redirecting where their content-hashed mirrors
 * land (via [ClasspathMirror.withCacheRoot]). The per-entry cache key folds in [Bmc4jVersion.IDENTITY]
 * (see [ClasspathMirror.effectiveExtraKey]), so a stale mirror or a version/semantics mismatch can never
 * be reused. The Gradle task additionally records the identity (+ Kotlin-param flag) and the resolved
 * config as manifest header lines and as task inputs; [substitute]/[coveredEntries] re-check the identity
 * and [configMatches] re-checks the config before the mirror is trusted — any mismatch falls back to a
 * full in-JVM rewrite, never serves a stale rewrite. The verdict-cache key and the witness renderer both
 * continue to key on the ORIGINAL (unsubstituted) classpath, so this is purely a relocation of WHERE the
 * rewrite happens, not WHAT verdict it yields.
 */
object GradleClasspathMirror {

    /** First line of the manifest: the runtime semantics identity the mirror was produced under. */
    private const val IDENTITY_PREFIX = "bmc4j-mirror-identity "

    /** Second line of the manifest: the resolved config the mirror's Config bake used (newline-encoded),
     *  re-validated by the runtime against the current environment so a STALE config bake is never served. */
    private const val CONFIG_PREFIX = "bmc4j-mirror-config "
    private const val MANIFEST_NAME = "manifest.txt"
    private const val MIRRORS_DIR = "mirrors"

    /**
     * The manifest identity for a mirror produced with the given [kotlinNullableParams] flag:
     * [Bmc4jVersion.IDENTITY] (the rewrite-code/version semantics) joined with the only run-wide flag any
     * hoisted pass reads. Folding the flag in means a mirror baked with one Kotlin-parameter semantics is
     * never trusted by a run with the other — even if a relocated/hand-copied mirror dir somehow escaped
     * the Gradle task's `@Input` invalidation. [substitute] / [coveredEntries] compute this from the
     * RUNTIME's current flag and require an exact match before trusting any entry.
     */
    private fun mirrorIdentity(kotlinNullableParams: Boolean): String =
            Bmc4jVersion.IDENTITY + "|knp=" + kotlinNullableParams

    /** The current runtime's Kotlin-parameter flag, read from the system property the test JVM is launched
     *  with (the same property [KotlinParamBytecode.rewrite] reads). */
    private fun runtimeKotlinNullableParams(): Boolean =
            java.lang.Boolean.getBoolean("bmc.kotlinNullableParams")

    /**
     * Mirror [classpath] through the run-wide rewrite passes into [outputDir], writing a manifest that
     * [substitute] reads. Called from the Gradle worker.
     *
     * The single-argument overload mirrors with the DEFAULT Kotlin-parameter semantics (auto-assume
     * non-null, i.e. `bmc.kotlinNullableParams=false`); the worker uses the [kotlinNullableParams]
     * overload to honour the run's actual flag. The two produce distinct cache entries (the flag is
     * folded into the mirror key), so an honest-JVM mirror never aliases the default one.
     *
     * The mirrors land in content-hashed subdirectories of `outputDir/mirrors/` (the same atomic,
     * `.done`-marked, content-keyed scheme [ClasspathMirror] uses for `~/.cache`), and the manifest maps
     * each original entry to its final mirrored path RELATIVE to [outputDir] — so the output is
     * relocatable and Gradle can cache it across runners with a path-independent key.
     */
    @JvmStatic
    fun mirror(classpath: String, outputDir: Path) = mirror(classpath, outputDir, false, mapOf())

    /** Mirror with an explicit [kotlinNullableParams] flag but no forwarded config (the worker reads its
     *  own env/properties for any `Bmc.*From*` bake). Convenience for tests / non-Gradle callers. */
    @JvmStatic
    fun mirror(classpath: String, outputDir: Path, kotlinNullableParams: Boolean) =
            mirror(classpath, outputDir, kotlinNullableParams, mapOf())

    /**
     * Mirror [classpath] through the run-wide passes, honouring the run's
     * [kotlinNullableParams] flag and baking the run's config from [configProperties] (the system
     * properties the test task will set, forwarded by the plugin so the worker — a different JVM than the
     * forked test JVM — bakes the SAME `Bmc.*FromProperty` constants). `Bmc.*FromEnv` reads the worker's
     * inherited process environment. See the single-arg overload for the manifest/layout contract.
     */
    @JvmStatic
    fun mirror(classpath: String, outputDir: Path, kotlinNullableParams: Boolean,
               configProperties: Map<String, String>) {
        Files.createDirectories(outputDir)
        val mirrorRoot = outputDir.resolve(MIRRORS_DIR)
        Files.createDirectories(mirrorRoot)

        // Run the same hoisted passes the in-JVM pipeline runs, in the same order, with every mirror
        // redirected under the Gradle-owned root:
        //  1. the six pure desugars as ONE fused walk (inflate once -> all six in order -> deflate once),
        //     byte-for-byte what the sequential per-pass chain produced — this is what collapses the cold
        //     mirror cost (six inflate/deflate round-trips per class become one);
        //  2. Config (bake Bmc.*From*("KEY") to this run's real value);
        //  3. KotlinParam (relax @BmcProof non-null prologues, unless the honest-JVM flag is set);
        //  4. Reachability (inject the vacuity marker into @BmcProof returns).
        // Each pass uses the SAME entry point the test JVM calls, so the mirrored bytecode is byte-for-byte
        // what the in-JVM passes would have produced (modulo the per-proof tail that stays in-JVM).
        val resolvedConfig: String
        // Bound the mirror walk's parallelism inside the Gradle worker. The worker runs in (a classloader
        // of) the Gradle daemon JVM, whose heap is typically smaller than a forked test JVM's; the
        // per-entry mirror holds a jar's inflated classes resident, so the default fan-out (up to 8) over
        // large dependency jars can exhaust the daemon heap (observed: OOM in the worker). This task is a
        // one-time cacheable unit, not latency-critical, so a low fan-out trades irrelevant throughput for
        // a safe peak. An explicit -Dbmc.mirrorParallelism still wins (the helper honours it).
        val finalClasspath = withMirrorParallelismDefault(WORKER_MIRROR_PARALLELISM) {
            withConfigProperties(configProperties) {
                ClasspathMirror.withCacheRoot(mirrorRoot) {
                    var cp = ClasspathMirror.mirrorAll(classpath)
                    // Config bake — uses the forwarded properties (set on this worker for the scope of the
                    // call) and the worker's inherited env. The runtime re-validates the baked config below.
                    cp = ConfigBytecode.rewrite(cp)
                    // The flag lives in the runtime as a system property (KotlinParamBytecode.rewrite reads
                    // it); the worker is a separate JVM, so set it from the explicit argument for this walk
                    // only. Folded into the mirror cache key below regardless.
                    cp = withKotlinNullableParams(kotlinNullableParams) { KotlinParamBytecode.rewrite(cp) }
                    cp = ReachabilityBytecode.rewrite(cp)
                    cp
                }
            }
        }
        // The resolved config the bake just used — recorded in the manifest so the runtime can re-validate
        // it against the current environment and never trust a STALE config bake (see readMap).
        resolvedConfig = withConfigProperties(configProperties) {
            ConfigBytecode.resolvedConfig(classpath)
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
        sb.append(IDENTITY_PREFIX).append(mirrorIdentity(kotlinNullableParams)).append('\n')
        // The resolved config (sorted, deterministic) the Config bake used, base64-free single line —
        // resolvedConfig is newline-joined, so encode the newlines so the manifest stays line-oriented.
        sb.append(CONFIG_PREFIX).append(encodeConfig(resolvedConfig)).append('\n')
        for (i in originals.indices) {
            val rel = outputDir.relativize(Path.of(finals[i])).toString().replace('\\', '/')
            // "<original>\t<mirrored-relative-to-outputDir>" — TAB-separated; classpath entries never
            // contain a tab, and the relative path keeps the manifest portable across runners.
            sb.append(originals[i]).append('\t').append(rel).append('\n')
        }
        Files.write(outputDir.resolve(MANIFEST_NAME), sb.toString().toByteArray(StandardCharsets.UTF_8))
    }

    /** Conservative mirror fan-out for the Gradle worker (see [withMirrorParallelismDefault]). Low enough
     *  that a few large inflated dependency jars resident at once stay within the Gradle daemon's heap. */
    private const val WORKER_MIRROR_PARALLELISM = 2

    /** Run [body] with `bmc.mirrorParallelism` defaulted to [value] when the user has NOT set it, so the
     *  worker's mirror walk uses a low, heap-safe fan-out; an explicit `-Dbmc.mirrorParallelism` is left
     *  untouched (it still wins). Restores the prior state afterwards. */
    private inline fun <T> withMirrorParallelismDefault(value: Int, body: () -> T): T {
        val key = "bmc.mirrorParallelism"
        val prev = System.getProperty(key)
        if (prev.isNullOrBlank()) {
            System.setProperty(key, value.toString())
        }
        try {
            return body()
        } finally {
            if (prev.isNullOrBlank()) System.clearProperty(key)
        }
    }

    /** Run [body] with `bmc.kotlinNullableParams` forced to [value], restoring the prior property after.
     *  [KotlinParamBytecode.rewrite] reads the property ONCE at entry (before dispatching the parallel
     *  walk), so scoping it around the whole call is race-free; the worker runs one [mirror] at a time. */
    private inline fun <T> withKotlinNullableParams(value: Boolean, body: () -> T): T {
        val key = "bmc.kotlinNullableParams"
        val prev = System.getProperty(key)
        System.setProperty(key, value.toString())
        try {
            return body()
        } finally {
            if (prev == null) System.clearProperty(key) else System.setProperty(key, prev)
        }
    }

    /** Run [body] with [properties] set as system properties (restoring each prior value after), so the
     *  worker JVM — which does not inherit the forked test JVM's `-D` flags — bakes the SAME
     *  `Bmc.*FromProperty` constants the test run will use. `Bmc.*FromEnv` reads the inherited env, which
     *  the worker shares with the daemon process tree. The worker runs one [mirror] at a time, so this is
     *  race-free; [ConfigBytecode.rewrite]/[ConfigBytecode.resolvedConfig] read the values within. */
    private inline fun <T> withConfigProperties(properties: Map<String, String>, body: () -> T): T {
        if (properties.isEmpty()) {
            return body()
        }
        val prior = HashMap<String, String?>(properties.size)
        for ((k, v) in properties) {
            prior[k] = System.getProperty(k)
            System.setProperty(k, v)
        }
        try {
            return body()
        } finally {
            for ((k, old) in prior) {
                if (old == null) System.clearProperty(k) else System.setProperty(k, old)
            }
        }
    }

    /** Encode the (possibly multi-line) resolved-config string to a single manifest line: the only
     *  characters are the reader/KEY/value text plus '\n' separators, so escape '\' then '\n'. */
    private fun encodeConfig(resolved: String): String =
            resolved.replace("\\", "\\\\").replace("\n", "\\n")

    /** Inverse of [encodeConfig]. */
    private fun decodeConfig(line: String): String {
        val sb = StringBuilder(line.length)
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (c == '\\' && i + 1 < line.length) {
                val n = line[i + 1]
                when (n) {
                    'n' -> { sb.append('\n'); i += 2 }
                    '\\' -> { sb.append('\\'); i += 2 }
                    else -> { sb.append(c); i++ }
                }
            } else {
                sb.append(c); i++
            }
        }
        return sb.toString()
    }

    /**
     * In the test JVM: swap each entry of [originalClasspath] for its pre-mirrored counterpart produced
     * by the plugin in [outputDir]. Entries the plugin didn't mirror (e.g. a non-class container, or one
     * absent from the manifest) pass through unchanged — the remaining in-JVM passes still rewrite them,
     * so soundness is preserved, only the speed-up is forfeited for those entries.
     *
     * Returns the original classpath unchanged (so the caller mirrors in-JVM as before) when the mirror
     * is missing, unreadable, or was produced under a DIFFERENT identity ([Bmc4jVersion.IDENTITY] or the
     * Kotlin-parameter flag). The CONFIG match is a separate gate ([configMatches]) the caller checks once
     * over the full union classpath, because the per-substitution sub-classpath does not carry every
     * config call site.
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
            // Look up by CANONICAL path: the worker keyed the manifest by each entry's canonical path,
            // because the test JVM's java.class.path and the task's resolved file paths can spell the same
            // location differently (notably Gradle's test worker doubles every backslash on Windows). A raw
            // string compare would miss every entry and silently disable the mirror; canonicalising both
            // sides makes them match.
            out.append(map[canonicalKey(entry)]?.toString() ?: entry)
        }
        return out.toString()
    }

    /**
     * The set of ORIGINAL classpath entries the plugin mirror in [outputDir] actually covers, each as a
     * CANONICAL path key (a usable, identity-matched mirror exists on disk). The runtime uses this to run
     * the hoistable passes in-JVM over exactly the entries the plugin did NOT pre-mirror — so every entry
     * is rewritten exactly once, by the plugin or in-JVM, never zero times. Callers must compare their own
     * entries via [canonicalKey] (the keys are canonical, not raw). On a normal Gradle run the task covers
     * the whole classpath (deps + the consumer's own output + bmcModel), so this set is the full classpath
     * and nothing is rewritten in-JVM.
     *
     * Like [substitute], this does NOT gate on the config match — the caller checks [configMatches] once
     * over the full union classpath and treats a mismatch as "no mirror" (full in-JVM rewrite).
     */
    @JvmStatic
    fun coveredEntries(outputDir: Path): Set<String> = readMap(outputDir).keys

    /** Mirror dirs already warned about (per JVM), so the guard logs at most ONCE per mirror rather than
     *  per proof. The string is the mirror dir's normalised path; the set is small (one per Gradle run). */
    private val warnedMirrors = java.util.Collections.newSetFromMap(
            java.util.concurrent.ConcurrentHashMap<String, Boolean>())

    /**
     * Detect the SILENT path-format mismatch that originally disabled the mirror on Windows: the manifest
     * declares N>0 covered entries (an identity-matched mirror with on-disk targets), yet NONE of the live
     * [classpath] entries match any covered key. On a normal Gradle run the task covers the WHOLE analysis
     * classpath, so a total miss is almost never a legitimate "nothing to substitute" -- it is overwhelmingly
     * a spelling mismatch between the worker's manifest keys and the test JVM's `java.class.path` (the exact
     * bug [canonicalKey] fixes). Left silent, it degrades to a full in-JVM rewrite that LOOKS like success
     * (0 hits == "nothing to do"), which is how the Windows regression hid for several releases.
     *
     * This is purely a DIAGNOSTIC: the fallback is sound either way (every uncovered entry is rewritten
     * in-JVM, see [JbmcBackend.hoistableWithGradleMirror]), so the guard only WARNS -- it never fails a run,
     * so a genuinely-disjoint classpath (the rare legitimate total miss) over-warns at worst and never turns
     * into a false failure. Warns at most once per mirror dir per JVM. Call once per run over the full union
     * classpath the worker baked (deps + project + bmcModel); a blank classpath or empty cover is a no-op.
     *
     * @return the number of live entries that matched a covered key (0 means the warning fired).
     */
    @JvmStatic
    fun warnIfMirrorMatchedNothing(classpath: String, outputDir: Path): Int {
        val covered = coveredEntries(outputDir)
        if (covered.isEmpty()) {
            // No trusted mirror here (missing / identity- or flag-mismatched): a 0-match is the honest,
            // expected "full in-JVM rewrite" path, not a path-format bug -- say nothing.
            return 0
        }
        var matched = 0
        for (entry in classpath.split(File.pathSeparator)) {
            if (entry.isNotEmpty() && canonicalKey(entry) in covered) {
                matched++
            }
        }
        if (matched == 0 && warnedMirrors.add(outputDir.toAbsolutePath().normalize().toString())) {
            System.err.println(
                    "[bmc4j] WARNING: the Gradle classpath mirror at " + outputDir +
                            " declares " + covered.size + " covered entries but matched 0 of the " +
                            "analysis classpath -- almost certainly a path-format mismatch (e.g. Windows " +
                            "backslash spelling) between the plugin's manifest and the test JVM's " +
                            "java.class.path, NOT a legitimately-uncovered classpath. The run stays SOUND " +
                            "(every entry is rewritten in-JVM) but forfeits the mirror's speed-up. Please " +
                            "report this with your OS + Gradle version.")
        }
        return matched
    }

    /**
     * A path entry's canonical key for covered-entry matching: the canonical (symlink/relativity-resolved,
     * case-normalised on Windows) absolute path, or — if that can't be computed — the absolute path, or the
     * raw string as a last resort. Collapses spelling differences (e.g. the doubled backslashes Gradle's
     * test worker puts in `java.class.path` on Windows) so the test JVM's classpath entries match the
     * manifest keys the task wrote.
     */
    @JvmStatic
    fun canonicalKey(entry: String): String {
        return try {
            java.io.File(entry).canonicalPath
        } catch (e: java.io.IOException) {
            try {
                java.io.File(entry).absolutePath
            } catch (e2: RuntimeException) {
                entry
            }
        }
    }

    /**
     * True if the mirror in [outputDir] baked its Config constants under the SAME config this run resolves
     * over [configClasspath] — the FULL union of every entry the mirror saw (deps + the consumer's own
     * output + bmcModel), so every `Bmc.*From*` call site is in scope, exactly the union the worker baked
     * over. A mismatch (a flipped env var / property since the mirror was built) means the baked constants
     * are stale, so the caller must NOT trust the mirror and rewrites in-JVM instead. [ConfigBytecode.resolvedConfig]
     * is the same deterministic resolution the verdict-cache key uses. A missing/old-format manifest, or a
     * resolution error, returns false (fail toward in-JVM, never toward a stale bake). The Gradle @Input on
     * the config also re-runs the task on a change, so this is the soundness backstop, not the only guard.
     */
    @JvmStatic
    fun configMatches(configClasspath: String, outputDir: Path): Boolean {
        val manifest = outputDir.resolve(MANIFEST_NAME)
        if (!Files.isRegularFile(manifest)) {
            return false
        }
        val lines = try {
            Files.readAllLines(manifest, StandardCharsets.UTF_8)
        } catch (e: java.io.IOException) {
            return false
        }
        if (lines.size < 2 || !lines[1].startsWith(CONFIG_PREFIX)) {
            return false
        }
        val bakedConfig = decodeConfig(lines[1].substring(CONFIG_PREFIX.length))
        val currentConfig = try {
            ConfigBytecode.resolvedConfig(configClasspath)
        } catch (e: RuntimeException) {
            return false
        }
        return bakedConfig == currentConfig
    }

    /** Read the manifest's (original entry -> mirrored path) map, or an empty map if the mirror is
     *  missing, unreadable, produced under a different identity / Kotlin-param flag, or has no on-disk
     *  targets. The config match is checked separately ([configMatches]). An empty map means "trust
     *  nothing here" — the caller rewrites in-JVM. */
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
        // Line 0 = identity (semantics + Kotlin-param flag); line 1 = the config the bake used (gated by
        // configMatches, not here). A wrong/old-format header means the mirror's bytecode is stale.
        if (lines.size < 2
                || lines[0] != IDENTITY_PREFIX + mirrorIdentity(runtimeKotlinNullableParams())
                || !lines[1].startsWith(CONFIG_PREFIX)) {
            return emptyMap()
        }
        val map = HashMap<String, Path>()
        for (i in 2 until lines.size) {
            val line = lines[i]
            val tab = line.indexOf('\t')
            if (tab <= 0) {
                continue
            }
            val original = line.substring(0, tab)
            val mirrored = outputDir.resolve(line.substring(tab + 1)).normalize()
            // Only trust a mirrored entry that actually exists on disk; a missing one is dropped so the
            // caller rewrites the original in-JVM, never points the engine at a phantom path. Key by the
            // CANONICAL path so the runtime's java.class.path entries (which Gradle's test worker can spell
            // differently — e.g. doubled backslashes on Windows) match what the task wrote.
            if (Files.exists(mirrored)) {
                map[canonicalKey(original)] = mirrored
            }
        }
        return map
    }
}
