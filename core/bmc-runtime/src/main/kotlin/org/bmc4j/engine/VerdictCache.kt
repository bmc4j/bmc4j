package org.bmc4j.engine

import org.bmc4j.Verdict
import java.io.File
import java.io.IOException
import java.io.UncheckedIOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.TreeMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipInputStream

/**
 * Per-proof verdict cache: skip re-verifying a proof whose inputs haven't changed.
 *
 * A proof's verdict is a pure function of its inputs — the reachable bytecode, the request flags,
 * and the engine + bmc4j-runtime semantics. Re-running a green proof whose inputs are unchanged buys
 * nothing, and BMC is the expensive kind of test. This cache turns "nothing changed" into "proofs are
 * free", which is what lets `@BmcProof` stay in the default `test` task.
 *
 * ## Soundness
 * A **stale pass is a soundness bug** for this tool, so the cache is deliberately biased toward
 * over-invalidation (re-running) and against under-invalidation (a wrong skip):
 * - **Only deterministic, expectation-matching PASSES are ever cached.** `VERIFIED` for a
 *   normal proof; `REFUTED` / `VACUOUS` for a fail-on-purpose demo whose
 *   `@BmcProof(expect = ...)` declares exactly that verdict — a refutation is as much a pure
 *   function of the inputs as a verification, and the demo's *pass* is the refutation.
 *   **Failures are never cached**: any expectation mismatch (the dangerous drift) always comes
 *   from a live engine run, so the counterexample is fresh and a flaky environment can't pin a
 *   stale failure.
 * - **`TIMEOUT` and `UNKNOWN` are never cached, even when expected.** A timeout is a
 *   function of machine speed, not of the inputs — serving a cached "TIMEOUT, as expected" on a
 *   faster runner would hide the drift to VERIFIED that the expectation exists to catch. (It would
 *   also save almost nothing: a timeout costs exactly its budget.)
 * - The key composes every input that can change a verdict (see [computeKey]): the analysis
 *   classpath *content*, the effective request, the engine identity, and the bmc4j runtime
 *   semantics identity ([Bmc4jVersion.IDENTITY]). Coarse on purpose — any application-class
 *   change invalidates that module's whole cache. (The `expect` attribute lives in the
 *   compiled test class, so changing it invalidates via classpath content too.)
 * - **Fail-open.** Any error reading or writing the cache is swallowed and treated as a miss, so
 *   the cache can never cause a wrong or varying verdict — at worst it runs the engine. A hit whose
 *   stored verdict does not satisfy the proof's expectation is ignored the same way: live run.
 */
object VerdictCache {

    /** Bypass the cache entirely (always run the engine, never read or write). */
    private const val NO_CACHE_PROP = "bmc.noCache"

    /**
     * Cache directory: `<module>/build/bmc4j/verdict-cache/`. Resolved against `user.dir`
     * (the test JVM's working directory is the module dir) on every access rather than cached in a
     * static, so it tracks the working directory — keeps the cache per-module under `build/` (so
     * `gradlew clean` removes it) and lets tests redirect it.
     */
    private fun cacheDir(): Path =
            Path.of(System.getProperty("user.dir", "."), "build", "bmc4j", "verdict-cache")

    /** True when caching is disabled via `-Dbmc.noCache=true` (or the `bmc { cache=false }` flag). */
    @JvmStatic
    fun disabled(): Boolean = System.getProperty(NO_CACHE_PROP, "false").toBoolean()

    /**
     * Look up a previously cached **verified** verdict for [request] under [engineIdentity].
     * Returns `true` only on a hit whose key matches exactly; `false` on a miss, on a disabled
     * cache, or on ANY error (fail-open). A `true` here means the proof was verified and the engine
     * run can be skipped.
     */
    @JvmStatic
    fun isVerified(request: BmcRequest, engineIdentity: String?): Boolean =
            lookupVerified(request, engineIdentity) != null

    /** [lookup] narrowed to `VERIFIED`: a hit with any other stored verdict is a miss. */
    @JvmStatic
    fun lookupVerified(request: BmcRequest, engineIdentity: String?): Hit? {
        val hit = lookup(request, engineIdentity)
        return if (hit != null && hit.verdict == Verdict.VERIFIED) hit else null
    }

    /**
     * A cache hit's stored verdict *fact* (verdict + stub facts): the entry's verdict marker
     * (`VERIFIED`, `REFUTED` or `VACUOUS` — the only verdicts ever stored) plus the
     * nondet-stub list that was harvested when the proof verified. `null` on a miss, a disabled
     * cache, an unrecognized marker, or any error (fail-open → run the engine). Whether the stored
     * verdict *satisfies* the proof's expectation is the caller's judgement, made fresh at read
     * time — the cache stores the fact, never the pass. Likewise the stored stub list lets the stub
     * *policy* be re-judged at read time — flipping `strictStubs` or editing
     * `allowStubs` re-decides from the stored fact *without* an engine re-run, because
     * neither is part of the cache key.
     */
    @JvmStatic
    fun lookup(request: BmcRequest, engineIdentity: String?): Hit? {
        if (disabled()) {
            return null
        }
        try {
            val key = computeKey(request, engineIdentity)
            val entry = cacheDir().resolve(key)
            if (!Files.isRegularFile(entry)) {
                return null
            }
            // The entry's first line is "<VERDICT> <entryFunction>"; only the deterministic markers are
            // recognized (a truncated/scribbled file is a miss, fail-open). Remaining "STUB <fqn>" lines
            // carry the harvested stub fact for re-judgement.
            val lines = Files.readAllLines(entry, StandardCharsets.UTF_8)
            if (lines.isEmpty()) {
                return null
            }
            val verdict = parseMarker(lines[0].trim()) ?: return null
            val stubs = lines.asSequence().drop(1)
                    .map { it.trim() }
                    .filter { it.startsWith("STUB ") }
                    .map { it.removePrefix("STUB ").trim() }
                    .toList()
            return Hit(verdict, stubs)
        } catch (e: RuntimeException) {
            return null // fail-open: any trouble reading the cache -> miss -> run the engine
        } catch (e: IOException) {
            return null
        }
    }

    /** The deterministic verdict named by an entry's first line, or `null` if unrecognized. */
    private fun parseMarker(firstLine: String): Verdict? {
        val token = firstLine.substringBefore(' ')
        return DETERMINISTIC.firstOrNull { it.name == token }
    }

    /**
     * The verdicts that are a pure function of the proof's inputs and may therefore be cached.
     * `TIMEOUT`/`UNKNOWN` are deliberately absent: a timeout is a function of machine speed.
     */
    private val DETERMINISTIC = arrayOf(Verdict.VERIFIED, Verdict.REFUTED, Verdict.VACUOUS)

    /** A cache hit: the stored verdict fact, plus the stub list harvested when the proof verified. */
    class Hit internal constructor(
            /** The stored verdict: `VERIFIED`, `REFUTED` or `VACUOUS` — never a failure. */
            @get:JvmName("verdict") val verdict: Verdict,
            stubbedMethods: List<String>) {

        /** The nondet stubs (filtered signal) recorded when this proof verified; empty for non-VERIFIED. */
        @get:JvmName("stubbedMethods")
        val stubbedMethods: List<String> = stubbedMethods.toList()
    }

    /**
     * Record that [request] (under [engineIdentity]) verified. Equivalent to
     * [storeIfExpectedMatch] with a `VERIFIED` expectation: stores iff the result verified.
     */
    @JvmStatic
    fun storeIfVerified(request: BmcRequest, engineIdentity: String?, result: JbmcResult?) {
        storeIfExpectedMatch(request, engineIdentity, result, Verdict.VERIFIED)
    }

    /**
     * Record [result]'s verdict iff it is an expectation-matching *pass* with a
     * deterministic verdict: `VERIFIED` for a normal proof, `REFUTED`/`VACUOUS` for a
     * fail-on-purpose demo whose `@BmcProof(expect = ...)` declares exactly that verdict. No-ops
     * when the cache is disabled, when the actual verdict does not equal [expected] (failures are
     * never cached — a mismatch must always come from a live run), when the verdict is
     * `TIMEOUT`/`UNKNOWN` (machine-dependent, never cached even when expected), or on ANY
     * write error (fail-open). The marker is written atomically (temp file + move) so a concurrent
     * reader never sees a half-written entry.
     */
    @JvmStatic
    fun storeIfExpectedMatch(request: BmcRequest, engineIdentity: String?, result: JbmcResult?,
                             expected: Verdict) {
        if (disabled() || result == null) {
            return
        }
        val actual = deterministicVerdictOf(result)
        if (actual == null || actual != expected) {
            return // never cache UNKNOWN/TIMEOUT, never cache a failure — always re-run those
        }
        try {
            val key = computeKey(request, engineIdentity)
            Files.createDirectories(cacheDir())
            val entry = cacheDir().resolve(key)
            val tmp = cacheDir().resolve(key + ".tmp." + java.lang.Long.toHexString(System.nanoTime()))
            // Store the verdict marker plus (for VERIFIED) the harvested nondet-stub fact, one
            // "STUB <fqn>" per line. The stub policy is judged at READ time, so the cache key is
            // unchanged — only this payload grows — and flipping strictStubs / allowStubs re-judges
            // without an engine re-run. (The stub policy only applies to greens, so non-VERIFIED
            // entries carry no STUB lines.)
            val body = buildString {
                append(actual.name).append(' ').append(request.entryFunction).append('\n')
                if (actual == Verdict.VERIFIED) {
                    for (stub in result.stubbedMethods) {
                        append("STUB ").append(stub).append('\n')
                    }
                }
            }
            Files.writeString(tmp, body, StandardCharsets.UTF_8)
            try {
                Files.move(tmp, entry, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE)
            } catch (atomicUnsupported: IOException) {
                Files.move(tmp, entry, StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (e: RuntimeException) {
            // fail-open: a write failure must never break the build or vary a verdict.
            cleanupTemp()
        } catch (e: IOException) {
            cleanupTemp()
        }
    }

    /**
     * The result's verdict if it is deterministic (cacheable), else `null`. Mirrors the
     * user-facing verdict mapping but collapses the machine-dependent verdicts (`TIMEOUT` and
     * other `UNKNOWN`s) to `null` — they are never cacheable regardless of expectation.
     */
    private fun deterministicVerdictOf(result: JbmcResult): Verdict? = when {
        result.isVerified -> Verdict.VERIFIED
        result.isVacuous -> Verdict.VACUOUS
        result.isUnknown -> null // TIMEOUT / UNKNOWN: a function of machine speed, not of the inputs
        else -> Verdict.REFUTED
    }

    /**
     * The cache key: a hex digest over everything that can change this proof's verdict —
     * 1. the bmc4j runtime semantics identity ([Bmc4jVersion.IDENTITY]) — the rewrite layer
     *    defines the proof's semantics, so a layer change must bust the cache;
     * 2. the engine identity (bundled engine version, or a hash of an explicit `jbmcPath` binary);
     * 3. the effective request: entry function, unwind, unwinding-assertions, solver,
     *    maxStringLength, timeoutSeconds, concurrent;
     * 4. the analysis-classpath *content*: every application `.class` file and every
     *    model jar on the classpath, by path + content (coarse v1 — any app-class change invalidates
     *    the whole module's cache);
     * 5. the user-model directory content (`bmc.userModels`): user `src/bmcModel` classes
     *    are spliced onto the analysis classpath inside `JbmcBackend.prepareClasspath`, AFTER
     *    this key is built, so they aren't in `request.classpath` — fold them in explicitly or
     *    editing a user model serves a stale green;
     * 6. the Kotlin parameter semantics (`bmc.kotlinNullableParams`):
     *    [KotlinParamBytecode] rewrites proof prologues at analysis time, AFTER this key is
     *    built — fold the mode in or flipping it would serve verdicts proven under the other
     *    semantics;
     * 7. the resolved config inputs: [ConfigBytecode] bakes the real
     *    `System.getenv`/`getProperty("KEY")` value into the analysed bytecode at analysis
     *    time (AFTER this key is built), and the app `.class` files don't change when an env var
     *    or property changes — so scan the reachable classpath for literal-keyed `Bmc.*From*`
     *    call sites, resolve each `KEY=value` the same way the bake does, and fold them in, or a
     *    config-pinned proof keeps its cached green after its config flips to a violating value.
     *
     * Visible for unit testing (each input must perturb the digest).
     */
    @JvmStatic
    fun computeKey(request: BmcRequest, engineIdentity: String?): String {
        val md = sha256()
        // 1) runtime semantics identity
        update(md, "runtime", Bmc4jVersion.IDENTITY)
        // 2) engine identity
        update(md, "engine", engineIdentity ?: "")
        // 3) effective request
        update(md, "entry", request.entryFunction)
        update(md, "unwind", request.unwind.toString())
        update(md, "ua", request.unwindingAssertions.toString())
        update(md, "solver", request.solver)
        update(md, "msl", request.maxStringLength.toString())
        update(md, "timeout", request.timeoutSeconds.toString())
        update(md, "concurrent", request.concurrent.toString())
        // 4) analysis-classpath content — memoized per classpath behind a (path, size, mtime)
        //    fingerprint; computeKey runs for EVERY proof and re-hashing the whole classpath
        //    dominated the cost of a cache hit (see memoized()).
        update(md, "classpath", memoized(DIGEST_MEMO, request.classpath, ::classpathContentDigest))
        // 5) user-model content (bmc.userModels) — spliced onto the classpath after this key is built,
        //    so editing a user model must invalidate here or a stale green is served.
        val userModels = System.getProperty("bmc.userModels", "")
        update(md, "userModels", memoized(DIGEST_MEMO, userModels, ::classpathContentDigest))
        // 6) Kotlin parameter semantics (bmc.kotlinNullableParams) — KotlinParamBytecode rewrites
        //    proof prologues at analysis time, AFTER this key is built, and the app .class files
        //    don't change when the flag flips — so fold the mode in, or flipping to honest-JVM
        //    nullable-parameter semantics would serve greens proven under the auto-assume.
        update(md, "kotlinNullableParams", java.lang.Boolean.getBoolean("bmc.kotlinNullableParams").toString())
        // 7) resolved config inputs — ConfigBytecode bakes the REAL System.getenv/getProperty("KEY")
        //    value into the analysed bytecode at analysis time, AFTER this key is built, and the app
        //    .class files don't change when an env var / property changes. So fold the resolved KEY=value
        //    pairs (scanned from the same reachable classpath, incl. user models) in here, or a
        //    config-pinned proof keeps its cached green after its config flips to a violating value.
        update(md, "config", resolvedConfig(request.classpath, userModels))
        return toHex(md.digest())
    }

    /**
     * The resolved config inputs (`KEY=value` pairs) for every literal-keyed `Bmc.*From*`
     * call site reachable on the analysis classpath and the user-model dir — the values
     * [ConfigBytecode] bakes at analysis time. Folded into [computeKey] so a config-pinned
     * proof's cached green is invalidated when its config flips to a violating value (the app
     * `.class` files are unchanged, so [classpathContentDigest] alone can't catch it).
     * Fail-open: any error scanning yields the empty string (a miss-toward-re-run, never a wrong hit).
     */
    @JvmStatic
    @JvmName("resolvedConfig") // internal functions are name-mangled in bytecode; Java tests call it
    internal fun resolvedConfig(classpath: String?, userModels: String?): String {
        return try {
            val cp = classpath ?: ""
            val um = userModels ?: ""
            val combined = when {
                um.isBlank() -> cp
                cp.isBlank() -> um
                else -> cp + File.pathSeparator + um
            }
            // Only the bytecode SCAN for call sites is memoized (a pure function of the classpath's
            // content, fingerprint-guarded like the digests). The VALUES are re-resolved on every key
            // (one getenv/getProperty per site — cheap), so a config flip between calls still perturbs
            // the key for unchanged bytecode.
            val sites = memoized(SITES_MEMO, combined, ConfigBytecode::scanCallSites)
            ConfigBytecode.resolveSites(sites)
        } catch (e: RuntimeException) {
            "" // fail-open: scan trouble -> empty -> cache fails open to a re-run
        }
    }

    // --- Per-JVM memo of the expensive computeKey inputs --------------------------------------------

    /** Count of expensive recomputes (full digest or scan) — test hook pinning that the memo hits. */
    @JvmField
    val MEMO_RECOMPUTES = AtomicInteger()

    /** A memoized value plus the (path, size, mtime) fingerprint of the files it was computed from. */
    private class Memo<V>(val fingerprint: String, val value: V)

    private val DIGEST_MEMO = ConcurrentHashMap<String, Memo<String>>()
    private val SITES_MEMO = ConcurrentHashMap<String, Memo<List<Array<String>>>>()

    /**
     * Memoize [compute] per classpath string, guarded by a cheap (path, size, mtime)
     * fingerprint of every file the computation reads. [computeKey] runs for **every** proof
     * (the lookup; live runs pay it again on store), but its expensive inputs — the classpath content
     * digest and the config call-site scan — hash every class file and decompress + parse every jar on
     * the analysis classpath, which made even a cache HIT cost hundreds of milliseconds per proof.
     * Both are pure functions of the classpath's file contents, which are stable for a test JVM's
     * lifetime in any real run (Gradle compiles before the test task starts) — but stability is
     * *verified, not assumed*: any file appearing, disappearing, or changing size/mtime changes
     * the fingerprint and forces a fresh compute, so a mid-JVM edit (the soundness tests do exactly
     * that) still invalidates. Fail-open: a fingerprint error yields a never-matching value, forcing
     * the fresh compute.
     *
     * Computes under a per-key lock: proofs run in parallel (the plugin defaults to one executor
     * per core), so without it the FIRST wave of proofs all miss the empty memo simultaneously and
     * each redundantly hashes the whole classpath — the herd pays the full cost as many times over as
     * there are executors. With the lock, one thread computes and the rest wait briefly and reuse it.
     */
    private fun <V> memoized(memo: ConcurrentHashMap<String, Memo<V>>,
                             classpath: String?, compute: (String) -> V): V {
        val key = classpath ?: ""
        val fp = fingerprint(key)
        var m = memo[key]
        if (m != null && m.fingerprint == fp) {
            return m.value
        }
        synchronized(LOCKS.computeIfAbsent(key) { Any() }) {
            m = memo[key] // re-check: another thread may have computed while we waited
            m?.let {
                if (it.fingerprint == fp) {
                    return it.value
                }
            }
            MEMO_RECOMPUTES.incrementAndGet()
            val value = compute(key)
            memo[key] = Memo(fp, value)
            return value
        }
    }

    /** Per-classpath compute locks for [memoized] (never removed; a test JVM sees few keys). */
    private val LOCKS = ConcurrentHashMap<String, Any>()

    /**
     * A cheap identity of every file [classpathContentDigest] and the config scan read: each
     * directory entry's `.class` files (relative path, size, mtime — the same filter the digest
     * walks) plus each regular-file entry (path, size, mtime), sorted. Reads attributes only, never
     * content; mtime carries its full filesystem precision. Distinct absent/error markers so a path
     * flipping between states never aliases a clean fingerprint.
     */
    private fun fingerprint(classpath: String): String {
        try {
            if (classpath.isBlank()) {
                return ""
            }
            val sb = StringBuilder()
            for (e in classpath.split(File.pathSeparator).filter { it.isNotBlank() }.sorted()) {
                val p = Path.of(e)
                when {
                    Files.isDirectory(p) -> {
                        val classes = mutableListOf<Path>()
                        Files.walk(p).use { walk ->
                            walk.filter { Files.isRegularFile(it) }
                                    .filter { it.fileName.toString().endsWith(".class") }
                                    .forEach { classes.add(it) }
                        }
                        classes.sortBy { it.toString() }
                        for (c in classes) {
                            appendFileId(sb, e + "!" + p.relativize(c).toString().replace('\\', '/'), c)
                        }
                    }
                    Files.isRegularFile(p) -> appendFileId(sb, e, p)
                    else -> sb.append(e).append("|absent\n")
                }
            }
            return sb.toString()
        } catch (ex: IOException) {
            // fail-open: an unfingerprintable classpath never matches a memo entry -> fresh compute
            return "unfingerprintable:" + System.nanoTime()
        } catch (ex: RuntimeException) {
            return "unfingerprintable:" + System.nanoTime()
        }
    }

    private fun appendFileId(sb: StringBuilder, label: String, file: Path) {
        sb.append(label).append('|').append(Files.size(file)).append('|')
                .append(Files.getLastModifiedTime(file).to(TimeUnit.NANOSECONDS))
                .append('\n')
    }

    /**
     * A digest of the verdict-relevant *content* of the analysis classpath: every application
     * `.class` file (directory entries, recursed) and every jar, by relative path + bytes.
     * Library jars are folded in too — a consumer proof can reach into an unmodeled/un-stubbed jar's
     * actual bytecode, so upgrading that jar (without recompiling app `.class` files) must
     * invalidate, or a stale VERIFIED is served across the dependency change. [jarContentDigest]
     * ignores zip timestamps, so a non-reproducible rebuild of the same classes doesn't spuriously
     * invalidate. Coarse but correct: any content change on the classpath invalidates.
     */
    @JvmStatic
    @JvmName("classpathContentDigest") // internal functions are name-mangled; Java tests call it
    internal fun classpathContentDigest(classpath: String?): String {
        val md = sha256()
        if (classpath.isNullOrBlank()) {
            return toHex(md.digest())
        }
        // Sort so classpath ordering doesn't change the digest (the reachable set is order-independent).
        for (e in classpath.split(File.pathSeparator).filter { it.isNotBlank() }.sorted()) {
            val p = Path.of(e)
            try {
                if (Files.isDirectory(p)) {
                    digestClassDir(md, p)
                } else if (Files.isRegularFile(p) && isJar(p)) {
                    update(md, "jar:" + p.fileName, jarContentDigest(p))
                }
            } catch (ex: RuntimeException) {
                // Fail-open per-entry: if one entry can't be read, fold a marker so a later read failure
                // can't silently equal a clean run, and keep going. The overall lookup still fails open.
                update(md, "unreadable:$e", ex.javaClass.simpleName)
            } catch (ex: IOException) {
                update(md, "unreadable:$e", ex.javaClass.simpleName)
            }
        }
        return toHex(md.digest())
    }

    /** Hash every `.class` file under [dir], by path-relative-to-dir + content, sorted. */
    private fun digestClassDir(md: MessageDigest, dir: Path) {
        val classes = mutableListOf<Path>()
        Files.walk(dir).use { walk ->
            walk.filter { Files.isRegularFile(it) }
                    .filter { it.fileName.toString().endsWith(".class") }
                    .forEach { classes.add(it) }
        }
        classes.sortBy { it.toString() }
        for (c in classes) {
            val rel = dir.relativize(c).toString().replace('\\', '/')
            update(md, "class:$rel", fileDigest(c))
        }
    }

    /** A classpath entry is a jar (verdict-relevant content) if its name ends in `.jar`. */
    private fun isJar(jar: Path): Boolean =
            jar.fileName.toString().lowercase().endsWith(".jar")

    /**
     * Digest of a jar's *logical content*: each entry's name + uncompressed bytes, sorted by
     * name. Deliberately ignores zip metadata (timestamps, ordering, compression) so a non-reproducible
     * rebuild of the same classes — Gradle stamps jars with build timestamps, so the raw bytes differ
     * every build — does NOT spuriously invalidate the cache. The model jars' actual class content is
     * what affects a verdict, and that's exactly what this hashes.
     */
    @JvmStatic
    @JvmName("jarContentDigest") // internal functions are name-mangled; Java tests call it
    internal fun jarContentDigest(jar: Path): String {
        val md = sha256()
        val entries = TreeMap<String, ByteArray>()
        try {
            ZipInputStream(Files.newInputStream(jar)).use { zin ->
                var ze = zin.nextEntry
                while (ze != null) {
                    if (!ze.isDirectory) {
                        entries[ze.name] = zin.readAllBytes()
                    }
                    ze = zin.nextEntry
                }
            }
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }
        for ((name, bytes) in entries) {
            md.update(name.toByteArray(StandardCharsets.UTF_8))
            md.update(0.toByte())
            md.update(bytes)
            md.update(0.toByte())
        }
        return toHex(md.digest())
    }

    /** SHA-256 of a file's bytes, hex-encoded. */
    @JvmStatic
    @JvmName("fileDigest") // internal functions are name-mangled; Java tests call it
    internal fun fileDigest(file: Path): String {
        try {
            val md = sha256()
            md.update(Files.readAllBytes(file))
            return toHex(md.digest())
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }
    }

    private fun update(md: MessageDigest, label: String, value: String?) {
        md.update(label.toByteArray(StandardCharsets.UTF_8))
        md.update(0.toByte())
        md.update((value ?: "").toByteArray(StandardCharsets.UTF_8))
        md.update(0.toByte())
    }

    private fun sha256(): MessageDigest =
            MessageDigest.getInstance("SHA-256") // present on every JVM

    private fun toHex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            sb.append(Character.forDigit((b.toInt() shr 4) and 0xF, 16))
            sb.append(Character.forDigit(b.toInt() and 0xF, 16))
        }
        return sb.toString()
    }

    /** Best-effort sweep of any leftover temp files from a failed write (fail-open housekeeping). */
    private fun cleanupTemp() {
        try {
            Files.list(cacheDir()).use { s ->
                s.filter { it.fileName.toString().contains(".tmp.") }
                        .forEach {
                            try {
                                Files.deleteIfExists(it)
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
