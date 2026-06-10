package org.bmc4j.engine

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.UncheckedIOException
import java.nio.charset.StandardCharsets
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.LinkedHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Shared classpath-mirroring engine for the bytecode rewrite passes (string/concat/record, lambda,
 * switch, math, config, reachability, coroutine-LVT). Each pass is "rewrite every `.class` on
 * the analysis classpath with this transform, copy everything else verbatim"; this class owns the
 * common *where* (which entries, where the mirror lands, how it's cached) so each pass only
 * supplies the *what* (its [ClassTransform]).
 *
 * **Both directory AND jar entries are mirrored**. The in-repo test bed runs on
 * `includeBuild` class *directories*, but a published consumer gets `bmc-models`
 * and every third-party library as *jars*; if jars passed through unrewritten, the shipped
 * product would be silently less sound than the test bed that validates it (no String shim, no
 * concat/record/lambda/typeSwitch/Math desugaring on any jar'd class). So:
 * - **Directory entries** are mirrored to a cache dir keyed by a full SHA-256 of the dir's
 *   *content* (every `.class`/resource's path + bytes), with a `.done` marker —
 *   the same content-hash + atomic-publish scheme as jars. Hashing the content (not
 *   just the path) means a deleted/renamed class yields a fresh key, so a fresh complete mirror,
 *   so stale phantom classes can never linger on the analysis classpath across builds; and a full
 *   hash (not a 32-bit `String.hashCode`) means two distinct dirs can't collide into one
 *   mirror and silently mix their classes;
 * - **Jar entries** are mirrored to a rewritten jar under the cache keyed by the jar's
 *   *content hash*, so the (more expensive) rewrite happens once per distinct jar and is
 *   reused across runs. A finished marker makes the cache hit atomic: a half-written jar from a
 *   crashed/concurrent run is never mistaken for a complete one.
 *
 * **Fail LOUD on a mirror failure.** Mirroring is the *only* thing that
 * makes JBMC's unsound constructs (nondet String ops, unconstrained `invokedynamic`) sound. If
 * an entry can't be mirrored (IO error, a malformed class, a corrupt jar) we must NOT silently fall
 * back to the original entry: that would analyse unrewritten String/indy classes as "verified" when
 * they were never made sound — a silent green that is exactly the unsoundness this layer exists to
 * close. Instead we THROW. The engine-error handling catches a `RuntimeException`
 * out of the rewrite/run path and reclassifies it as UNKNOWN (`BmcUndecidedError`) — so a
 * mirror failure fails the proof toward UNKNOWN, never toward a false VERIFIED. (The verdict cache
 * is the inverse direction: it fails toward re-running. Both fail toward "we don't actually know".)
 */
internal object ClasspathMirror {

    /** Transforms one `.class` file's bytes. May emit extra generated classes (e.g. the lambda
     *  pass synthesizes a class per lambda site); the simple owner-swap passes emit only the main. */
    fun interface ClassTransform {
        fun apply(classBytes: ByteArray): Transformed
    }

    /** The output of a [ClassTransform]: the rewritten main class plus any extra classes it
     *  generated, keyed by internal name (`a/b/C`). Extra is usually empty. */
    class Transformed @JvmOverloads constructor(
            @JvmField val main: ByteArray,
            @JvmField val extra: Map<String, ByteArray> = mapOf())

    /**
     * Mirror every entry of [classpath] under [cacheName], rewriting each `.class`
     * with [transform] and copying every other resource verbatim. Returns the new classpath
     * (same order, same separators). Directory entries become mirror directories; jar entries become
     * rewritten jars (both content-hash cached).
     *
     * **Throws on any mirror failure** rather than passing the original entry
     * through — a silent fall-back to the unrewritten entry would analyse unsound String/indy classes
     * as "verified". The thrown [MirrorException] propagates out of the engine-run path and is
     * reclassified as UNKNOWN (`BmcUndecidedError`) by the engine-error handling, so a mirror
     * failure surfaces as "we couldn't decide", never as a false green.
     *
     * The 4-arg overload folds [extraKey] into every entry's content hash. This lets a pass whose
     * transform is parameterized (e.g. the contract rewrite, where the same source dir yields a
     * *different* mirror per redirect set and excluded caller) keep distinct configurations in
     * distinct, complete cache entries — the same content-hash + atomic-publish + `.done` discipline,
     * just over (content + config) instead of content alone.
     */
    @JvmStatic
    @JvmOverloads
    @JvmName("mirror") // internal-object members keep their names, but be explicit for the Java callers
    fun mirror(classpath: String, cacheName: String, transform: ClassTransform,
               extraKey: String = ""): String {
        val effectiveKey = effectiveExtraKey(extraKey)
        val out = mutableListOf<String>()
        for (entry in classpath.split(File.pathSeparator)) {
            if (entry.isEmpty()) {
                continue
            }
            val p = Path.of(entry)
            try {
                when {
                    Files.isDirectory(p) ->
                        out.add(mirrorDir(p, cacheName, transform, effectiveKey).toString())
                    isJar(p) ->
                        out.add(mirrorJar(p, cacheName, transform, effectiveKey).toString())
                    // not a class container we rewrite (no soundness rewrite to lose)
                    else -> out.add(entry)
                }
            } catch (e: Exception) {
                // Fail LOUD, not open: an unmirrored String/indy class analysed as-is is silently
                // unsound. Throw so the proof fails toward UNKNOWN , never a false
                // VERIFIED. The actionable message names the entry that couldn't be mirrored.
                throw MirrorException(
                        "Could not mirror classpath entry for sound rewriting: " + entry +
                                " (cache '" + cacheName + "'). The bytecode rewrite that makes " +
                                "String ops and invokedynamic sound for JBMC could not be applied, " +
                                "so this proof cannot be soundly decided. Cause: " + e, e)
            }
        }
        return out.joinToString(File.pathSeparator)
    }

    /**
     * A failure to mirror+rewrite a classpath entry. Unchecked so it propagates out of
     * the engine-run path, where the engine-error handling reclassifies it as UNKNOWN
     * (`BmcUndecidedError`) — a mirror failure must fail the proof toward UNKNOWN, never let an
     * unrewritten (unsound) entry be analysed as a silent green.
     */
    class MirrorException(message: String, cause: Throwable) : RuntimeException(message, cause)

    /** A regular file whose name ends in `.jar` or `.zip` (the jar layout JBMC reads). */
    private fun isJar(p: Path): Boolean {
        if (!Files.isRegularFile(p)) {
            return false
        }
        val name = p.fileName.toString().lowercase()
        return name.endsWith(".jar") || name.endsWith(".zip")
    }

    /**
     * Optional override for where mirrors land, set for the lifetime of one [withCacheRoot] block.
     * Default (null) keeps the per-user `~/.cache/bmc4j/` cache used by the runtime extension and any
     * direct (non-Gradle) invocation. The Gradle mirror task sets it to its own `@OutputDirectory` so
     * Gradle's build cache — not an out-of-band `~/.cache` dir — owns the mirrored classpath. Per-thread
     * because a worker process runs the whole pass chain on one thread under one fixed root; it never
     * mixes a Gradle root and the user cache in the same JVM (the runtime fallback never sets it).
     */
    private val cacheRootOverride = ThreadLocal<Path?>()

    /** Run [body] with all mirrors landing under [root] instead of `~/.cache/bmc4j/`. */
    internal fun <T> withCacheRoot(root: Path, body: () -> T): T {
        val prev = cacheRootOverride.get()
        cacheRootOverride.set(root)
        try {
            return body()
        } finally {
            cacheRootOverride.set(prev)
        }
    }

    private fun cacheRoot(cacheName: String): Path {
        val override = cacheRootOverride.get()
        if (override != null) {
            return override.resolve(cacheName)
        }
        // Unified under ~/.cache/bmc4j/ alongside the engine cache (previously
        // legacy pre-rename dir). A one-time rebuild of the rewrite mirrors is harmless.
        return Path.of(System.getProperty("user.home"), ".cache", "bmc4j", cacheName)
    }

    // ---- directories: mirror to a FRESH dir keyed by CONTENT HASH, .done-marked ----
    //
    // Previously this keyed the dest on Integer.toHexString(absPath.hashCode()) and overwrote in place.
    // Two problems: (a) a 32-bit String.hashCode can collide, silently mixing two dirs' classes into
    // one mirror; (b) overwriting never deletes, so a class removed/renamed in the source kept its
    // stale .class on the analysis classpath forever (a phantom class). Both are fixed by mirroring,
    // exactly like the jar branch, into a dir named for the FULL SHA-256 of the dir's content: a
    // collision is cryptographically infeasible, and any content change (incl. a deletion) yields a
    // fresh key -> a fresh, complete mirror with no leftovers. A .done marker makes the cache hit
    // atomic so a half-written mirror from a crashed/racing run is never reused as complete.

    private fun mirrorDir(dir: Path, cacheName: String, transform: ClassTransform,
                          extraKey: String): Path {
        // Snapshot the dir's files (sorted by relative path for a stable hash) once, so the hash and
        // the mirror see the same set even if the dir changes underneath us mid-run.
        val files = mutableListOf<Path>()
        Files.walk(dir).use { walk ->
            walk.forEach { src ->
                if (!Files.isDirectory(src)) {
                    files.add(src)
                }
            }
        }
        files.sortBy { dir.relativize(it).toString() }

        val hash = dirContentHash(dir, files, extraKey)
        val root = cacheRoot(cacheName)
        Files.createDirectories(root)
        val dest = root.resolve(hash)
        val done = root.resolve(hash + DONE_SUFFIX)

        // Cache hit: a completed mirror for this exact content already exists.
        if (Files.isDirectory(dest) && Files.isRegularFile(done)) {
            return dest
        }

        // Build into a fresh unique temp dir, then atomically publish it (and the marker last). A
        // fresh dir guarantees no stale class survives; building off to the side keeps a concurrent
        // reader from ever seeing a partial mirror as complete.
        val tmp = Files.createTempDirectory(root, "$hash-")
        try {
            for (src in files) {
                val target = tmp.resolve(dir.relativize(src).toString())
                Files.createDirectories(target.parent)
                if (src.toString().endsWith(".class")) {
                    val t = transform.apply(Files.readAllBytes(src))
                    Files.write(target, t.main)
                    for ((genName, genBytes) in t.extra) {
                        val gp = tmp.resolve("$genName.class")
                        Files.createDirectories(gp.parent)
                        Files.write(gp, genBytes)
                    }
                } else {
                    Files.copy(src, target, StandardCopyOption.REPLACE_EXISTING)
                }
            }
            // Publish: a racing writer may have already published the same content-hash dest; that's
            // fine (identical content), so only move if dest is absent, and tolerate a lost race.
            if (!Files.isDirectory(dest)) {
                try {
                    Files.move(tmp, dest, StandardCopyOption.ATOMIC_MOVE)
                } catch (raced: FileAlreadyExistsException) {
                    // another run published first — its content equals ours, so reuse it
                } catch (atomicUnsupported: IOException) {
                    if (!Files.isDirectory(dest)) {
                        Files.move(tmp, dest)
                    }
                }
            }
            Files.write(done, ByteArray(0)) // completion marker last
        } finally {
            deleteRecursivelyIfExists(tmp) // no-op if the move consumed it
        }
        return dest
    }

    /** SHA-256 over the dir's content: each file's relative path then its bytes (length-framed so two
     *  different splits can't hash the same). Keys the mirror so distinct content gets distinct dests
     *  (no path-collision, no staleness) — the directory analogue of [contentHash]. */
    private fun dirContentHash(dir: Path, sortedFiles: List<Path>, extraKey: String): String {
        val md = try {
            MessageDigest.getInstance("SHA-256")
        } catch (e: NoSuchAlgorithmException) {
            throw IOException("SHA-256 unavailable", e)
        }
        // Fold the config key in first (length-framed), so distinct (content, config) tuples can never
        // alias the same dest — the directory analogue of the jar branch's extra-key mixing.
        val ek = extraKey.toByteArray(StandardCharsets.UTF_8)
        md.update(intToBytes(ek.size))
        md.update(ek)
        val buf = ByteArray(1 shl 16)
        for (src in sortedFiles) {
            val rel = dir.relativize(src).toString().replace('\\', '/')
                    .toByteArray(StandardCharsets.UTF_8)
            md.update(intToBytes(rel.size))
            md.update(rel)
            md.update(longToBytes(Files.size(src)))
            Files.newInputStream(src).use { input ->
                var n = input.read(buf)
                while (n > 0) {
                    md.update(buf, 0, n)
                    n = input.read(buf)
                }
            }
        }
        return toHex(md.digest())
    }

    private fun intToBytes(v: Int): ByteArray = byteArrayOf(
            (v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte())

    private fun longToBytes(v: Long): ByteArray {
        var x = v
        val b = ByteArray(8)
        for (i in 7 downTo 0) {
            b[i] = x.toByte()
            x = x ushr 8
        }
        return b
    }

    private fun deleteRecursivelyIfExists(root: Path) {
        if (!Files.exists(root)) {
            return
        }
        Files.walk(root).use { walk ->
            walk.sorted(Comparator.reverseOrder()).forEach { p ->
                try {
                    Files.deleteIfExists(p)
                } catch (ignored: IOException) {
                    // best-effort temp cleanup; a leftover temp dir never affects correctness
                }
            }
        }
    }

    // ---- jars: mirror to a rewritten jar keyed by CONTENT HASH (one-time per jar, reused) ----

    private const val DONE_SUFFIX = ".done"

    private fun mirrorJar(jar: Path, cacheName: String, transform: ClassTransform,
                          extraKey: String): Path {
        val hash = jarContentHash(jar, extraKey)
        val root = cacheRoot(cacheName)
        Files.createDirectories(root)
        val dest = root.resolve("$hash.jar")
        val done = root.resolve(hash + DONE_SUFFIX)

        // Cache hit: a completed rewrite for this exact content already exists. The marker makes the
        // check atomic — a half-written .jar from a crashed/racing run has no .done, so it's redone.
        if (Files.isRegularFile(done) && Files.isRegularFile(dest)) {
            return dest
        }

        // Write to a unique temp jar, then atomically publish (jar first, then the marker) so a
        // concurrent reader never sees a partial jar as complete and racing writers don't corrupt.
        val tmp = Files.createTempFile(root, "$hash-", ".jar.tmp")
        try {
            rewriteJarTo(jar, tmp, transform)
            // Publish. A content-hashed jar is IMMUTABLE once its .done marker is published, so a
            // PUBLISHED entry must never be re-opened for writing (the Windows sharing violation:
            // moving onto a jar a concurrent reader holds open -> "used by another process"). The fast
            // path already returned on dest+.done; here we are the first publisher, the loser of a
            // publish race, or redoing a half-written jar (dest present but NO .done). Re-check the
            // marker so a winner that published between the fast path and here makes us touch nothing.
            if (!Files.isRegularFile(done)) {
                publishJar(tmp, dest, done)
            }
            // else: lost the race — the winner's identical-content jar + marker are already in place.
        } finally {
            Files.deleteIfExists(tmp)
        }
        // Whether we published or lost a publish race, a COMPLETE (.done-marked) jar must now exist —
        // fail loud if it somehow doesn't, so a swallowed race can never return a path the caller would
        // analyse as a half-written/absent mirror.
        if (!Files.isRegularFile(dest) || !Files.isRegularFile(done)) {
            throw IOException("mirror jar not completely published: $dest")
        }
        return dest
    }

    /**
     * Publish the freshly-rewritten [tmp] jar as [dest], then write the [done] marker
     * last. [dest]'s name IS its content hash, so a COMPLETE (.done-marked) [dest] is by
     * construction the correct content and must never be re-opened for writing (on Windows, moving onto
     * a jar a concurrent reader holds open is a "used by another process" sharing violation). The two
     * fault modes the marker disambiguates:
     * - **Concurrent publisher** — another writer installed the identical-content [dest]
     *   and is about to (or did) write `.done`. We must NOT replace it; we wait briefly for
     *   its marker and reuse it.
     * - **Stale half-write** — a [dest] from a crashed run with no live reader and no
     *   `.done` (and none ever coming). It must be REDONE: replaced with our real rewrite.
     *
     * A first publish (no [dest]) uses `ATOMIC_MOVE` with no `REPLACE_EXISTING`
     * (Windows can't atomically replace). Replacement of a stale half-write tolerates a peer replacing
     * it concurrently (re-checking the marker), so racing redo-ers never fail each other.
     */
    private fun publishJar(tmp: Path, dest: Path, done: Path) {
        // First publish: install into an absent dest. ATOMIC_MOVE so a reader never sees partial bytes.
        if (!Files.isRegularFile(dest)) {
            try {
                Files.move(tmp, dest, StandardCopyOption.ATOMIC_MOVE)
                writeMarker(done)
                return
            } catch (raced: FileAlreadyExistsException) {
                // A racer installed the identical-content dest first — fall through to the marker wait.
            } catch (atomicUnsupported: IOException) {
                if (!Files.isRegularFile(dest)) {
                    Files.move(tmp, dest) // ATOMIC_MOVE unsupported here; dest absent -> no live reader.
                    writeMarker(done)
                    return
                }
                // dest appeared (a racer) — fall through to the marker wait.
            }
        }
        // dest already exists. Either a concurrent publisher is finishing (its .done is imminent) or it
        // is a stale half-write to redo. Wait briefly for the publisher's marker before deciding.
        var i = 0
        while (i < 200 && !Files.isRegularFile(done)) {
            sleepMillis(10, done)
            i++
        }
        if (Files.isRegularFile(done)) {
            return // a concurrent publisher completed the identical-content jar — reuse it, write nothing.
        }
        // No marker after the wait: a stale half-write with no live reader (no consumer resolves dest
        // without .done). Replace it with our real rewrite, tolerating a peer redoing it concurrently.
        try {
            Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING)
        } catch (peerRedoing: IOException) {
            if (!Files.isRegularFile(done) && !Files.isRegularFile(dest)) {
                throw peerRedoing // genuinely couldn't place a mirror — fail loud.
            }
            // a peer replaced dest concurrently; its content equals ours — reuse it.
        }
        writeMarker(done)
    }

    /** Idempotently create the empty `.done` marker; concurrent creates of an empty file are
     *  harmless, so a racer having written it first is fine. */
    private fun writeMarker(done: Path) {
        if (!Files.isRegularFile(done)) {
            Files.write(done, ByteArray(0))
        }
    }

    private fun sleepMillis(ms: Long, waitingFor: Path) {
        try {
            Thread.sleep(ms)
        } catch (ie: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("interrupted waiting for a concurrent jar publish: $waitingFor", ie)
        }
    }

    /** Rewrite every `.class` entry of [input] into a fresh jar [out]; copy the rest
     *  (manifest, resources) verbatim. Generated extra classes are added as new entries. */
    private fun rewriteJarTo(input: Path, out: Path, transform: ClassTransform) {
        // Collect generated extras across all entries; add them after, de-duped (a given generated
        // name is deterministic per source class, so collisions across entries would be identical).
        val generated = LinkedHashMap<String, ByteArray>()
        val written = mutableSetOf<String>()
        ZipFile(input.toFile()).use { zf ->
            ZipOutputStream(Files.newOutputStream(out)).use { zos ->
                val en = zf.entries()
                while (en.hasMoreElements()) {
                    val e = en.nextElement()
                    if (e.isDirectory) {
                        zos.putNextEntry(ZipEntry(e.name))
                        zos.closeEntry()
                        written.add(e.name)
                        continue
                    }
                    val bytes = zf.getInputStream(e).use { it.readAllBytes() }
                    if (e.name.endsWith(".class")) {
                        val t = transform.apply(bytes)
                        writeEntry(zos, e.name, t.main)
                        written.add(e.name)
                        generated.putAll(t.extra)
                    } else {
                        writeEntry(zos, e.name, bytes)
                        written.add(e.name)
                    }
                }
                for ((genName, genBytes) in generated) {
                    val name = "$genName.class"
                    if (written.add(name)) {
                        writeEntry(zos, name, genBytes)
                    }
                }
            }
        }
    }

    private fun writeEntry(zos: ZipOutputStream, name: String, bytes: ByteArray) {
        val out = ZipEntry(name)
        out.time = 0L // deterministic: content hash already pins identity
        zos.putNextEntry(out)
        zos.write(bytes)
        zos.closeEntry()
    }

    /**
     * The effective per-entry key config: the runtime semantics identity prepended to the pass's own
     * [extraKey]. The transform CODE is part of a mirror's semantics but not of its input
     * content, so a rewriter change (artifact bump or `SEMANTICS_REVISION` bump) must
     * re-mirror — or jbmc re-analyzes STALE transforms of unchanged app dirs (the verdict cache
     * re-runs, its key has the identity, but on old bytecode). Over-invalidation on a version bump
     * is the safe, cheap direction.
     */
    private fun effectiveExtraKey(extraKey: String): String =
            Bmc4jVersion.IDENTITY + "|" + extraKey

    /** The published cache key of a config-free pass for this jar: SHA-256 over the runtime
     *  semantics identity + the jar's bytes. Identical content under the same runtime reuses the
     *  same rewritten jar across runs; a content change OR a semantics-identity change yields a
     *  fresh key (over-invalidate, never under: a stale rewrite is never reused). */
    internal fun contentHash(file: Path): String = jarContentHash(file, effectiveExtraKey(""))

    /** SHA-256 of [extraKey] (length-framed) followed by the jar's bytes — the config-aware jar
     *  cache key. Every caller passes an [effectiveExtraKey] (never empty), so the key always
     *  carries the runtime semantics identity; a pass's own extra config (the contract pass)
     *  additionally separates distinct redirect/exclude configurations over the same jar into
     *  distinct mirrors. */
    private fun jarContentHash(file: Path, extraKey: String): String {
        try {
            val md = MessageDigest.getInstance("SHA-256")
            if (extraKey.isNotEmpty()) {
                val ek = extraKey.toByteArray(StandardCharsets.UTF_8)
                md.update(intToBytes(ek.size))
                md.update(ek)
            }
            Files.newInputStream(file).use { input ->
                val buf = ByteArray(1 shl 16)
                var n = input.read(buf)
                while (n > 0) {
                    md.update(buf, 0, n)
                    n = input.read(buf)
                }
            }
            return toHex(md.digest())
        } catch (e: NoSuchAlgorithmException) {
            throw UncheckedIOException(IOException("hash failed", e))
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }
    }

    /** Lowercase hex encoding of a digest — the shared cache-key spelling for jars and dirs. */
    private fun toHex(d: ByteArray): String {
        val sb = StringBuilder(d.size * 2)
        for (b in d) {
            sb.append(Character.forDigit((b.toInt() shr 4) and 0xf, 16))
                    .append(Character.forDigit(b.toInt() and 0xf, 16))
        }
        return sb.toString()
    }
}
