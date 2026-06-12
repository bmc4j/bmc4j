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
     * 3. the effective request: entry function, the verdict-relevant jbmc FLAG SIGNATURE
     *    ([Jbmc.verdictRelevantFlags] — the actual command flags, so a hard-coded/`-Dbmc.*`-driven
     *    flag is keyed automatically and can never diverge a cached verdict from the run), plus the
     *    non-flag inputs (string-refinement mode, per-proof timeout);
     * 4. the proof's **reachable-cone content**: only the `.class` files transitively reachable from
     *    this proof's entry class (a constant-pool / call-graph walk, [ReachableCone]) — so touching a
     *    class outside the cone no longer invalidates this proof. When the cone can't be bounded
     *    soundly (reflection / method handles, an un-attributable `invokedynamic`, the entry class not
     *    on the classpath, or any walk error) it falls back to the whole-classpath content digest — the
     *    old coarse-but-always-sound behaviour. Over-invalidation is always acceptable; a stale green
     *    never is, so the unbounded edge folds in the whole classpath rather than dropping it;
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
    @JvmOverloads
    fun computeKey(
            request: BmcRequest,
            engineIdentity: String?,
            slicePolicy: String = ModelSlice.KEEP_POLICY_VERSION,
    ): String {
        val md = sha256()
        // 1) runtime semantics identity
        update(md, "runtime", Bmc4jVersion.IDENTITY)
        // 2) engine identity
        update(md, "engine", engineIdentity ?: "")
        // 3) effective request
        update(md, "entry", request.entryFunction)
        // The verdict-relevant jbmc FLAG SIGNATURE — the actual flags the engine receives, derived from
        // the SAME builder that assembles the command ([Jbmc.verdictRelevantFlags]). This is the single
        // source of truth for "which flags can change the verdict", so a flag HARD-CODED in (or
        // -Dbmc.*-driven into) the command — e.g. a future --slice-formula — is captured here
        // automatically and can never silently diverge a cached verdict from the run that produced it.
        // It includes --unwind, --unwinding-assertions, --max-nondet-string-length and the full solver
        // selection; it excludes the executable path, the --classpath VALUE (its content is keyed via
        // the cone digest below; the path is machine/shard-volatile), the --function/entry (keyed
        // separately above), and the pure output/UI flags (--json-ui/--trace/--verbosity).
        update(md, "flags", Jbmc.verdictRelevantFlags(request))
        // The per-field updates below are kept as belt-and-suspenders alongside the signature: redundant
        // for the flags the signature already covers, but they additionally fold in inputs that are NOT
        // jbmc command flags (so the signature can't capture them) — the string-refinement mode and the
        // per-proof timeout. Over-keying is always sound.
        update(md, "unwind", request.unwind.toString())
        update(md, "ua", request.unwindingAssertions.toString())
        update(md, "solver", request.solver)
        // The RESOLVED external-SAT solver identity AND the string-refinement mode (on/off). A verdict
        // proven on the fast external SAT solver (text reasoning OFF) is NOT interchangeable with one
        // proven on the default solver (text reasoning ON), so both must bust the cache: under-keying
        // here would serve a text-reasoning-off verdict for a text-reasoning-on request (or vice versa),
        // a soundness bug. The path (not just the requested name) is folded in, so swapping the bundled
        // fast binary for a different external solver invalidates too. (stringRefinementOff is NOT a jbmc
        // command flag — it rides on the external-SAT path selection — so the signature can't carry it;
        // it must stay an explicit per-field update.) Over-keying is always sound.
        update(md, "externalSat", request.externalSatPath)
        update(md, "stringRefinementOff", request.stringRefinementOff.toString())
        update(md, "msl", request.maxStringLength.toString())
        // The per-proof timeout is a budget, not a jbmc verdict flag (it bounds wall-clock, not the
        // formula), so it isn't in the flag signature — fold it in explicitly.
        update(md, "timeout", request.timeoutSeconds.toString())
        // Exception-message elision mode (see ExceptionMessageElision): AUTO/ON/OFF select DIFFERENT
        // analysed bytecode (a thrown exception's message construction is dropped or kept), so a verdict
        // proven under one mode is not interchangeable with another. The AUTO gate's outcome additionally
        // depends on the cone (folded in just below), so AUTO-with-observer and AUTO-without resolve to
        // distinct cones and thus distinct keys already; this field distinguishes the explicit ON/OFF
        // overrides. Over-keying is sound; under-keying could serve an elided verdict for a non-eliding
        // request.
        update(md, "elideMessages", request.elideMessages.name)
        // 4) reachable-cone content — only the classes this proof transitively reaches, so a change to
        //    an unrelated class no longer busts this proof's cache. Falls back to the whole-classpath
        //    digest when the cone can't be bounded soundly (see coneContentDigest / ReachableCone).
        //    Memoized behind a (path, size, mtime) fingerprint keyed by classpath AND entry class: the
        //    cone is per-entry, so two proofs in the same module get distinct memo entries, while an
        //    unchanged classpath still reuses the digest across re-keys of the same proof.
        update(md, "cone", memoizedCone(request.classpath, request.entryClass))
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
        // 8) model-slicing policy — ModelSlice prunes the analysis classpath AFTER this key is
        //    built (same shape as 6/7: the inputs the key hashed don't change when the slicing
        //    policy does). Fold the policy identity in, or a verdict computed under a different
        //    slicing rule — including a pre-slicing run — would satisfy this proof's lookup.
        //    (The parameter exists as a test seam; production callers use the real policy.)
        update(md, "slicePolicy", slicePolicy)
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
    private val CONE_MEMO = ConcurrentHashMap<String, Memo<String>>()

    /**
     * The cone-scoped content digest for ([classpath], [entryClass]), memoized behind the classpath's
     * (path, size, mtime) fingerprint. The memo key folds in the entry class so two proofs sharing a
     * classpath get distinct cones; the *fingerprint* still tracks only the classpath files, so any
     * file change forces a fresh compute for every entry (over-invalidation stays sound). Computed
     * under a per-key lock for the same herd-avoidance reason as [memoized].
     */
    private fun memoizedCone(classpath: String?, entryClass: String): String {
        val cp = classpath ?: ""
        val memoKey = entryClass + " " + cp
        val fp = fingerprint(cp)
        var m = CONE_MEMO[memoKey]
        if (m != null && m.fingerprint == fp) {
            return m.value
        }
        synchronized(LOCKS.computeIfAbsent(memoKey) { Any() }) {
            m = CONE_MEMO[memoKey]
            m?.let {
                if (it.fingerprint == fp) {
                    return it.value
                }
            }
            MEMO_RECOMPUTES.incrementAndGet()
            val value = coneContentDigest(cp, entryClass)
            CONE_MEMO[memoKey] = Memo(fp, value)
            return value
        }
    }

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
     * The reachable-cone content digest for a proof: a hex digest over the bytes of exactly the
     * classes [ReachableCone] reaches from [entryClass] on [classpath], by internal name + content,
     * sorted (so classpath order and jar-vs-dir placement don't perturb it). When the cone can't be
     * bounded soundly the walk returns the whole-classpath signal and this delegates to
     * [classpathContentDigest] — the coarse, always-correct fallback — tagged so a resolved cone that
     * happens to reach every class can never collide with a deliberate fallback.
     *
     * Soundness: the cone is a sound over-approximation of the classes a proof depends on, so a class
     * the proof actually reaches is always in the digest; touching a class *outside* the cone leaves
     * this digest (hence the proof's whole cache key) unchanged, which is the intended cache hit. A
     * class the cone reaches but that lives off the classpath (a real-JDK type with no `.class` here)
     * contributes nothing to this digest — its modelled content rides on the model jars, which are
     * folded in via the engine identity and the user-model / config inputs; an off-classpath JDK class
     * has no per-module bytes to change. Visible for unit testing.
     */
    internal fun coneContentDigest(classpath: String?, entryClass: String): String {
        val cone = ReachableCone.compute(entryClass, classpath)
        if (cone.whole || cone.classes == null) {
            // Conservative fallback: hash the whole classpath, tagged distinctly so it can never alias
            // a resolved cone digest.
            val md = sha256()
            update(md, "cone-mode", "whole")
            update(md, "cone-whole", classpathContentDigest(classpath))
            return toHex(md.digest())
        }
        val md = sha256()
        update(md, "cone-mode", "scoped")
        val bytesByName = coneClassBytes(classpath, cone.classes)
        // Sort by internal name so order is stable regardless of where each class was found.
        for (name in bytesByName.keys.sorted()) {
            update(md, "cone-class:$name", toHex(sha256().also { it.update(bytesByName[name]) }.digest()))
        }
        return toHex(md.digest())
    }

    /**
     * Read the raw bytes of each internal class name in [wanted] from [classpath] (first on the
     * classpath wins, mirroring JVM resolution), across both directory and jar entries. Names not
     * present on the classpath (real-JDK / off-classpath types the cone reached) are simply absent from
     * the result — they have no per-module content to hash.
     */
    private fun coneClassBytes(classpath: String?, wanted: Set<String>): Map<String, ByteArray> {
        val out = HashMap<String, ByteArray>()
        if (classpath.isNullOrBlank()) {
            return out
        }
        for (e in classpath.split(File.pathSeparator).filter { it.isNotBlank() }.sorted()) {
            val p = Path.of(e)
            try {
                when {
                    Files.isDirectory(p) -> coneBytesFromDir(p, wanted, out)
                    Files.isRegularFile(p) && isJar(p) -> coneBytesFromJar(p, wanted, out)
                    else -> {}
                }
            } catch (ex: RuntimeException) {
                // Per-entry read failure: a class it would have provided is then absent, an
                // under-approximation. To stay sound, surface it so [coneContentDigest]'s memo recompute
                // would differ — fold an unreadable marker keyed by the entry into a sentinel name.
                out["__unreadable__:$e"] = ex.javaClass.simpleName.toByteArray(StandardCharsets.UTF_8)
            } catch (ex: IOException) {
                out["__unreadable__:$e"] = ex.javaClass.simpleName.toByteArray(StandardCharsets.UTF_8)
            }
        }
        return out
    }

    private fun coneBytesFromDir(dir: Path, wanted: Set<String>, out: MutableMap<String, ByteArray>) {
        Files.walk(dir).use { walk ->
            for (c in Iterable { walk.iterator() }) {
                if (Files.isRegularFile(c) && c.fileName.toString().endsWith(".class")) {
                    val bytes = Files.readAllBytes(c)
                    val name = internalNameOf(bytes) ?: continue
                    if (wanted.contains(name)) {
                        out.putIfAbsent(name, bytes)
                    }
                }
            }
        }
    }

    private fun coneBytesFromJar(jar: Path, wanted: Set<String>, out: MutableMap<String, ByteArray>) {
        ZipInputStream(Files.newInputStream(jar)).use { zin ->
            var ze = zin.nextEntry
            while (ze != null) {
                if (!ze.isDirectory && ze.name.endsWith(".class")) {
                    val bytes = zin.readAllBytes()
                    val name = internalNameOf(bytes)
                    if (name != null && wanted.contains(name)) {
                        out.putIfAbsent(name, bytes)
                    }
                }
                ze = zin.nextEntry
            }
        }
    }

    private fun internalNameOf(bytes: ByteArray): String? = try {
        org.objectweb.asm.ClassReader(bytes).className
    } catch (e: RuntimeException) {
        null
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
