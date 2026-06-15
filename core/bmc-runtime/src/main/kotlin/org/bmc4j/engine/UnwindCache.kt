package org.bmc4j.engine

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * The discovered-bound cache for automatic unwind discovery: a small "winning bound for this proof"
 * record so a steady-state run goes STRAIGHT to the bound the climb already found, instead of re-running
 * the whole low→high search every time.
 *
 * ## Relationship to the verdict cache
 * [VerdictCache] already keys each bound as a distinct solve (the bound is in the verdict-cache key), so
 * once the climb has run, the WINNING bound's VERIFIED/REFUTED/VACUOUS is itself cached there. This cache
 * adds the one missing piece: which bound won. The auto path:
 *  1. reads the discovered bound here;
 *  2. on a hit, runs the proof at exactly that bound — which then hits the verdict cache (zero solves);
 *  3. on a miss, climbs, and on a conclusive verdict records the winning bound here.
 *
 * So a re-run of an unchanged AUTO proof does ZERO extra solves: one verdict-cache hit at the recorded
 * bound, no climb.
 *
 * ## Soundness
 * This cache records only a COST hint (which bound to try first), never a verdict — so a stale entry can
 * never produce a wrong result. The recorded bound is still re-verified through the verdict cache (whose
 * own key folds in the bound, the classpath content, the engine + runtime identity, …). A stale or wrong
 * recorded bound at worst causes a verdict-cache miss and a fresh run (which may itself re-climb if that
 * bound turns out non-conclusive). Keyed by proof identity via [VerdictCache.computeKey] over a request
 * NORMALIZED to the AUTO sentinel (so the key is the same across the climb's rungs and across re-runs),
 * and stored under [Bmc4jVersion.IDENTITY] + engine identity like every other cached fact. Fail-open:
 * any read/write error is swallowed (a miss → climb).
 */
object UnwindCache {

    /** The unwind value a discovered-bound key is normalized to, so the key is bound-independent. */
    private const val NORMALIZED_UNWIND = org.bmc4j.BmcProof.AUTO

    /** A discovered per-loop "smart" unwind record: the global [base] bound plus the per-loop overrides
     *  ([unwindSet]) the climb landed a conclusive verdict at. The single-int [store]/[lookup] can't carry
     *  the per-loop map, so the smart path records this richer shape under a sibling key. */
    class SmartRecord internal constructor(
            @JvmField val base: Int,
            @JvmField val unwindSet: Map<String, Int>)

    /** Cache directory: `<module>/build/bmc4j/unwind-cache/` — a sibling of the verdict cache, removed
     *  by `gradlew clean`, redirected by tests via `user.dir`. */
    private fun cacheDir(): Path =
            Path.of(System.getProperty("user.dir", "."), "build", "bmc4j", "unwind-cache")

    /** True when caching is disabled — honors the same `-Dbmc.noCache` switch as [VerdictCache]. */
    private fun disabled(): Boolean = VerdictCache.disabled()

    /**
     * The previously-discovered bound for [request] under [engineIdentity], or `null` on a miss, a
     * disabled cache, an unparseable entry, or any error (fail-open). [request] is the AUTO proof's
     * request; its `unwind` is ignored (normalized away) so the lookup is bound-independent.
     */
    @JvmStatic
    fun lookup(request: BmcRequest, engineIdentity: String?): Int? {
        if (disabled()) {
            return null
        }
        return try {
            val entry = cacheDir().resolve(key(request, engineIdentity))
            if (!Files.isRegularFile(entry)) {
                null
            } else {
                Files.readAllLines(entry, StandardCharsets.UTF_8)
                        .firstOrNull()?.trim()?.toIntOrNull()?.takeIf { it > 0 }
            }
        } catch (e: RuntimeException) {
            null
        } catch (e: IOException) {
            null
        }
    }

    /**
     * Record [bound] as the discovered winning bound for [request] under [engineIdentity]. No-op when
     * the cache is disabled, the bound is non-positive, or on any write error (fail-open). Written
     * atomically (temp + move) so a concurrent reader never sees a half-written entry.
     */
    @JvmStatic
    fun store(request: BmcRequest, engineIdentity: String?, bound: Int) {
        if (disabled() || bound <= 0) {
            return
        }
        try {
            val k = key(request, engineIdentity)
            Files.createDirectories(cacheDir())
            val entry = cacheDir().resolve(k)
            val tmp = cacheDir().resolve(k + ".tmp." + java.lang.Long.toHexString(System.nanoTime()))
            Files.writeString(tmp, bound.toString() + "\n", StandardCharsets.UTF_8)
            try {
                Files.move(tmp, entry, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (atomicUnsupported: IOException) {
                Files.move(tmp, entry, StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (e: RuntimeException) {
            // fail-open: a write failure must never break the build or vary a verdict.
        } catch (e: IOException) {
        }
    }

    /**
     * The previously-discovered per-loop "smart" record for [request] under [engineIdentity], or `null`
     * on a miss / disabled cache / unparseable entry / any error (fail-open). Lets an unchanged smart-AUTO
     * proof go STRAIGHT to its discovered (base, unwindSet) and hit the verdict cache, instead of
     * re-running the whole per-loop climb every time. [request]'s `unwind` is normalized away (the key is
     * bound-independent, like [lookup]).
     */
    @JvmStatic
    fun lookupSmart(request: BmcRequest, engineIdentity: String?): SmartRecord? {
        if (disabled()) {
            return null
        }
        return try {
            val entry = cacheDir().resolve(smartKey(request, engineIdentity))
            if (!Files.isRegularFile(entry)) {
                null
            } else {
                parseSmart(Files.readAllLines(entry, StandardCharsets.UTF_8))
            }
        } catch (e: RuntimeException) {
            null
        } catch (e: IOException) {
            null
        }
    }

    /**
     * Record the discovered ([base], [unwindSet]) as the winning per-loop configuration for [request]
     * under [engineIdentity]. No-op when the cache is disabled, [base] is non-positive, or on any write
     * error (fail-open). Written atomically (temp + move), like [store].
     */
    @JvmStatic
    fun storeSmart(request: BmcRequest, engineIdentity: String?, base: Int, unwindSet: Map<String, Int>) {
        if (disabled() || base <= 0) {
            return
        }
        try {
            val k = smartKey(request, engineIdentity)
            Files.createDirectories(cacheDir())
            val entry = cacheDir().resolve(k)
            val tmp = cacheDir().resolve(k + ".tmp." + java.lang.Long.toHexString(System.nanoTime()))
            Files.writeString(tmp, renderSmart(base, unwindSet), StandardCharsets.UTF_8)
            try {
                Files.move(tmp, entry, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (atomicUnsupported: IOException) {
                Files.move(tmp, entry, StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (e: RuntimeException) {
        } catch (e: IOException) {
        }
    }

    /** Serialize a smart record: `base <n>` on line 1, then one `<loopId>\t<bound>` per override (sorted
     *  so the file is deterministic). Loop ids may contain ':' and '/', so they go in the CONTENT (never
     *  the filename) and the bound is split off at the LAST tab (ids never contain a tab). */
    private fun renderSmart(base: Int, unwindSet: Map<String, Int>): String {
        val sb = StringBuilder()
        sb.append("base ").append(base).append('\n')
        for ((id, bound) in unwindSet.toSortedMap()) {
            sb.append(id).append('\t').append(bound).append('\n')
        }
        return sb.toString()
    }

    /** Inverse of [renderSmart]; null if the first line isn't a positive `base <n>`. */
    private fun parseSmart(lines: List<String>): SmartRecord? {
        val first = lines.firstOrNull()?.trim() ?: return null
        if (!first.startsWith("base ")) {
            return null
        }
        val base = first.removePrefix("base ").trim().toIntOrNull()?.takeIf { it > 0 } ?: return null
        val map = LinkedHashMap<String, Int>()
        for (i in 1 until lines.size) {
            val line = lines[i]
            val tab = line.lastIndexOf('\t')
            if (tab <= 0) {
                continue
            }
            val bound = line.substring(tab + 1).trim().toIntOrNull()?.takeIf { it > 0 } ?: continue
            map[line.substring(0, tab)] = bound
        }
        return SmartRecord(base, map)
    }

    /** The discovered-bound key: the verdict-cache key over a request normalized to the AUTO sentinel
     *  unwind, so all of a proof's climb rungs (and re-runs) share one bound-independent key. */
    private fun key(request: BmcRequest, engineIdentity: String?): String =
            VerdictCache.computeKey(normalized(request), engineIdentity)

    /** The per-loop smart record's key: a sibling of [key] (same normalized identity, `.smart` suffix),
     *  so a smart record and a single-bound record for the same proof never collide. */
    private fun smartKey(request: BmcRequest, engineIdentity: String?): String =
            key(request, engineIdentity) + ".smart"

    /** [request] with `unwind` normalized to the AUTO sentinel (bound-independent identity). */
    private fun normalized(request: BmcRequest): BmcRequest =
            if (request.unwind == NORMALIZED_UNWIND) request
            else BmcRequest(request.entryClass, request.entryFunction, request.classpath,
                    NORMALIZED_UNWIND, request.unwindingAssertions, request.maxStringLength,
                    request.solver, request.timeoutSeconds, request.domainSplitRun,
                    request.externalSatPath, request.stringRefinementOff,
                    request.removeExceptionMessages, request.stringMode, request.profile,
                    // unwindSet is deliberately normalized away (the single-bound discovered key); the
                    // per-loop smart record keys separately. excludeModels IS preserved: a different
                    // exclusion set links different bytecode, so it can discover a different bound.
                    request.jbmcOptions, emptyMap(), request.excludeModels)
}
