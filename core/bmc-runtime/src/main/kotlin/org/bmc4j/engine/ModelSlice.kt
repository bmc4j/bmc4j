package org.bmc4j.engine

import org.objectweb.asm.ClassReader
import java.io.File
import java.io.IOException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.nio.charset.StandardCharsets

/**
 * Per-proof **model slicing**: hand the engine only the model surface inside this proof's reachable
 * cone, so a proof no longer pays to parse every model class the whole module ever accumulated.
 *
 * The analysis classpath grows with every model added to the portfolio (collections, time, decimal,
 * Kotlin facades, …), and the engine reads the *entire* classpath to build its symbol table on every
 * uncached run — so unrelated model growth taxes every proof, forever. A proof can only be affected by
 * a class in its [reachable cone][ReachableCone]; touching anything outside it cannot change the
 * verdict. Slicing prunes each **directory** classpath entry down to exactly the cone's classes before
 * the engine sees it, so the engine parses the proof's own surface instead of the module's.
 *
 * ## Why directories only
 * Slicing rewrites **directory entries** (the in-repo class dirs: the proof/test classes and the
 * `bmc-models` included-build class directories, which are where the model surface actually lives in a
 * normal run). **Jar entries pass through untouched** — they are the bundled Kotlin models,
 * `core-models.jar`'s JDK class hierarchy (which the engine needs whole for dynamic-cast and thread
 * analysis), and third-party libraries; they are few, already content-cached by [ClasspathMirror], and
 * pruning a shared hierarchy jar would risk the exact soundness hole this must avoid. The directory
 * surface is the part that grows per model PR, so it is the lever.
 *
 * ## Ordering
 * Runs **last** in `JbmcBackend.prepareClasspath`, after every rewrite pass. Running last (rather
 * than first) is deliberate: the rewrite passes ([StringBytecode], [LambdaBytecode], … ) mirror the
 * *full* dirs and are content-hash cached, so all proofs share one rewritten mirror per dir — slicing
 * first would key each rewrite on the per-proof sliced content and re-pay every rewrite per proof.
 * Slicing last copies a subset of the *already-rewritten* classes (a cheap file copy, no bytecode
 * work), so the engine sees only the cone while the expensive rewrites stay amortized across proofs.
 *
 * ## Soundness
 * The keep-set is the cone walked over the **fully-rewritten** classpath — the exact bytecode the
 * engine analyses — so it is a sound **over**-approximation of the classes the engine can reach (it
 * follows the constant-pool superset: supertypes, interfaces, field/parameter/return/exception types,
 * and every type a body references, including the classes the rewrite chain injects/redirects to:
 * lambda impls, contract stub/enforce classes, runtime helpers). Every class the engine can reach by
 * executing the proof is in the cone and survives the slice. A class pruned away is provably never
 * reached, so the engine never loads it and no stub is needed. Two hard rules keep this sound:
 *  - **Fallback proofs are never sliced.** When the cone can't be bounded (reflection / method
 *    handles, an un-attributable `invokedynamic`, the entry class off the classpath, or any walk
 *    error) [ReachableCone.compute] returns the whole-classpath signal and [sliceForCone] returns the
 *    classpath **unchanged** — the engine sees the full surface, exactly as before slicing existed.
 *  - **Slicing never drops a *reached* member.** If a resolved cone somehow under-approximated and the
 *    engine reached a sliced-away class, the engine resolves it to a bodiless symbol and reports the
 *    member-named opaque-symbol UNKNOWN — never a silent stub or a false green. Slicing can only ever
 *    turn a would-be VERIFIED into an UNKNOWN, never the reverse.
 *
 * Always keeps the entry class itself, defensively, even if a future cone change ever omitted it.
 *
 * Mirrors live under `~/.cache/bmc4j/model-slice/`, keyed by a SHA-256 of (the dir's content + the
 * cone set), with a `.done` marker for an atomic, crash-safe cache hit — the same discipline
 * [ClasspathMirror] uses for its dir mirrors. A given (dir-content, cone) tuple is sliced once and
 * reused across runs and across proofs that share it.
 */
internal object ModelSlice {

    private const val DONE_SUFFIX = ".done"
    private const val CACHE_NAME = "model-slice"

    /** Identity of the [shouldKeep] policy AND of the classpath the keep-set cone is walked over, folded
     *  into the slice cache key so a policy change never serves a slice built under the old rule, AND
     *  into the verdict-cache key ([VerdictCache.computeKey]) so a verdict computed under a different
     *  slicing policy (or none) is never served either. Bump on any [shouldKeep] semantic change OR any
     *  change to which classpath [sliceForCone] computes the keep-set over.
     *
     *  - `keep-v2-toplevel-owner`: keep-set = cone of the PRE-rewrite classpath + top-level-owner rule.
     *  - `keep-v3-rewritten-cone`: keep-set = cone of the REWRITTEN classpath (the bytecode JBMC sees)
     *    + top-level-owner rule. The rewrite chain injects classes the pre-rewrite cone never names —
     *    lambda impls, and (the break this fixes) contract redirect TARGETS like `<X>Contract__BmcStubs`,
     *    whose unrelated top-level name the owner rule can't catch. Walking the rewritten bytecode sees
     *    those materialized call sites/`new`s directly, so the keep-set covers every post-cone injector
     *    (contracts, lambdas, switch helpers, residual-indy markers, config bakes, …) by construction. */
    internal const val KEEP_POLICY_VERSION = "keep-v3-rewritten-cone"

    /**
     * Slice [classpath] to the reachable cone of [entryClass], or return it unchanged when the cone
     * can't be bounded (the conservative whole-classpath fallback — a fallback proof is never sliced).
     *
     * The keep-set is the cone walked over **[classpath] itself** — the fully-rewritten classpath the
     * engine is about to analyse — not the pre-rewrite classpath. This is the fix for the post-cone
     * injection hazard below: the keep-set must be a superset of every class JBMC can actually reach,
     * and JBMC reaches whatever the *rewritten* bytecode references. Walking the rewritten bytecode is
     * the only walk that sees the injected classes by their real names. The verdict cache's cone digest
     * still keys on the proof's pre-rewrite classpath (unchanged keying semantics); slicing only needs
     * its keep-set to cover the reachable-at-analysis classes, which the rewritten cone guarantees.
     *
     * ## Post-cone injection hazard (injected classes must be kept, not pruned)
     * The rewrite chain *adds / redirects to classes a pre-rewrite cone never named*:
     *  - [LambdaBytecode] desugars each lambda / method-reference `invokedynamic` into a generated
     *    `<owner>$$Lambda$N` class (and an `invokestatic` to a factory inside `<owner>`).
     *  - [ContractRewriter] redirects each contracted call site to an `invokestatic` of a generated
     *    contract stub class (`<X>Contract__BmcStubs.m__stub`) — an UNRELATED top-level class, not a
     *    nested class of any cone owner — and enforce proofs analyse generated `__BmcEnforce` bodies.
     *  - [ResidualIndyBytecode] / [SwitchBytecode] / [Math|String|Config|KotlinParam]Bytecode redirect
     *    to fixed runtime helpers (`org/bmc4j/...`), which ship in jar entries the slice never prunes.
     *
     * If a reached injected class were pruned, the engine would meet a `new`/`invokestatic` with no body,
     * devirtualize/stub it to nondet, and a symbolic-input law over its result would be **REFUTED, not
     * UNKNOWN** — the exact unsoundness slicing must never produce. Computing the keep-set over the
     * rewritten classpath closes this for ALL such injectors at once: the lambda `new`, the contract
     * `invokestatic <stub>`, the residual-indy marker call are all in the rewritten bytecode the walk
     * reads, so their target classes land in the cone and survive the slice. The owner-based rule in
     * [shouldKeep] is retained belt-and-braces (it still catches a nested class an inner-class edge might
     * miss), but it is no longer load-bearing for the injected classes the rewritten cone already names.
     */
    @JvmStatic
    fun sliceForCone(classpath: String, entryClass: String): String {
        // Walk the cone over the REWRITTEN classpath (what JBMC analyses), so injected classes the
        // pre-rewrite cone never named — contract stubs, lambda impls, … — are in the keep-set.
        val cone = ReachableCone.compute(entryClass, classpath)
        if (cone.whole || cone.classes == null) {
            // Unbounded cone -> no slice. The engine sees the whole surface, as if slicing didn't
            // exist. (This is the reflection / unknown-indy / missing-entry / walk-error case.)
            return classpath
        }
        // Keep the entry class itself unconditionally (defensive; the cone already roots on it).
        val keep = HashSet(cone.classes)
        keep.add(entryClass.replace('.', '/'))
        return sliceTo(classpath, keep)
    }

    /**
     * Slice [classpath]'s directory entries down to exactly the internal class names in [keep], leaving
     * jar / non-directory entries untouched, and fail SAFE to the unsliced [classpath] on any error.
     * This is the slice mechanism with the cone supplied explicitly — [sliceForCone] computes the cone
     * (with its fallback) and calls here. Visible internally so the soundness probe can drive a
     * deliberately-deficient [keep] (a slice that drops a class the proof actually reaches) and assert
     * the engine surfaces it as a member-named opaque-symbol UNKNOWN, never a silent stub or a false
     * green — pinning the hard-soundness floor that the cone's over-approximation makes unreachable in
     * normal runs.
     */
    @JvmStatic
    fun sliceTo(classpath: String, keep: Set<String>): String {
        return try {
            sliceDirs(classpath, keep)
        } catch (e: RuntimeException) {
            // Fail SAFE toward the full surface: a slice failure must never under-feed the engine
            // (that could hide a class the proof depends on). Returning the unsliced classpath only
            // forgoes the speed-up; correctness is unchanged.
            classpath
        } catch (e: IOException) {
            classpath
        }
    }

    /** Replace each directory entry of [classpath] with a cone-scoped mirror dir; leave jars/other as-is. */
    private fun sliceDirs(classpath: String, keep: Set<String>): String {
        val out = mutableListOf<String>()
        for (entry in classpath.split(File.pathSeparator)) {
            if (entry.isEmpty()) {
                continue
            }
            val p = Path.of(entry)
            if (Files.isDirectory(p)) {
                out.add(sliceDir(p, keep).toString())
            } else {
                // A jar or other container: passes through whole (see the class doc — jars carry the
                // shared JDK hierarchy and are few + already content-cached).
                out.add(entry)
            }
        }
        return out.joinToString(File.pathSeparator)
    }

    /**
     * Mirror [dir] to a cache dir containing only the `.class` files whose internal name is in [keep],
     * plus every non-class resource verbatim (resources are cheap and could be read by the engine).
     * Keyed by SHA-256 of (the dir's full content + the cone set) with a `.done` marker, so the slice
     * is computed once per (content, cone) tuple and the cache hit is atomic.
     */
    private fun sliceDir(dir: Path, keep: Set<String>): Path {
        val files = mutableListOf<Path>()
        Files.walk(dir).use { walk ->
            walk.forEach { src -> if (!Files.isDirectory(src)) files.add(src) }
        }
        files.sortBy { dir.relativize(it).toString() }

        val hash = sliceHash(dir, files, keep)
        val root = cacheRoot()
        Files.createDirectories(root)
        val dest = root.resolve(hash)
        val done = root.resolve(hash + DONE_SUFFIX)

        if (Files.isDirectory(dest) && Files.isRegularFile(done)) {
            return dest // cache hit: a completed slice for this exact (content, cone) already exists
        }

        // Build into a fresh temp dir, then atomically publish (the marker last) so a concurrent
        // reader never sees a partial slice as complete and a fresh dir leaves no stale class behind.
        val tmp = Files.createTempDirectory(root, "$hash-")
        try {
            for (src in files) {
                val rel = dir.relativize(src).toString()
                if (src.toString().endsWith(".class")) {
                    val bytes = Files.readAllBytes(src)
                    val name = internalNameOf(bytes)
                    // Drop a class outside the cone; keep an unparseable one (can't prove it's out).
                    if (name != null && !shouldKeep(name, keep)) {
                        continue
                    }
                    val target = tmp.resolve(rel)
                    Files.createDirectories(target.parent)
                    Files.write(target, bytes)
                } else {
                    val target = tmp.resolve(rel)
                    Files.createDirectories(target.parent)
                    Files.copy(src, target, StandardCopyOption.REPLACE_EXISTING)
                }
            }
            if (!Files.isDirectory(dest)) {
                try {
                    Files.move(tmp, dest, StandardCopyOption.ATOMIC_MOVE)
                } catch (raced: FileAlreadyExistsException) {
                    // a peer published the identical-content slice first — reuse it
                } catch (atomicUnsupported: IOException) {
                    if (!Files.isDirectory(dest)) {
                        Files.move(tmp, dest)
                    }
                }
            }
            if (!Files.isRegularFile(done)) {
                Files.write(done, ByteArray(0)) // completion marker last
            }
        } finally {
            deleteRecursivelyIfExists(tmp) // no-op if the move consumed it
        }
        return dest
    }

    /**
     * SHA-256 over the dir's content (each file's relative path then its bytes, length-framed) AND the
     * cone set (sorted internal names, length-framed). Folding the cone in means a different cone over
     * the same dir gets a distinct slice (no aliasing), and a content change gets a fresh key (no stale
     * class). The directory analogue of [ClasspathMirror]'s `dirContentHash`.
     */
    private fun sliceHash(dir: Path, sortedFiles: List<Path>, keep: Set<String>): String {
        val md = sha256()
        // Fold the keep-policy version in first: the cache hashes (content + cone) but the SLICE a
        // given (content, cone) yields also depends on the keep RULE (see shouldKeep) AND on which
        // classpath the cone was walked over (now the rewritten one — see KEEP_POLICY_VERSION). When
        // either changes the same key must not serve a slice built under the old rule (e.g. one that
        // dropped contract stub or generated lambda classes and made a sliced proof REFUTE). Bump
        // KEEP_POLICY_VERSION whenever shouldKeep's semantics or the keep-set's source classpath change.
        run {
            val v = KEEP_POLICY_VERSION.toByteArray(StandardCharsets.UTF_8)
            md.update(intToBytes(v.size))
            md.update(v)
        }
        // Fold the cone set in next (sorted + length-framed) so distinct cones never alias.
        for (name in keep.toSortedSet()) {
            val b = name.toByteArray(StandardCharsets.UTF_8)
            md.update(intToBytes(b.size))
            md.update(b)
        }
        md.update(intToBytes(-1)) // separator between the cone set and the content
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

    /**
     * Whether the class [name] survives the slice given the cone set [keep]. A class is kept when it is
     * itself in the cone, OR when its **top-level enclosing class** is. With the keep-set now walked over
     * the *rewritten* bytecode (see [sliceForCone]), the injected classes — lambda impls, contract stubs —
     * are already named in the cone by their own internal name, so this top-level-owner rule is retained
     * **belt-and-braces** rather than load-bearing: it still keeps any real nested/anonymous class
     * (`<owner>$Inner`, `<owner>$1`) of a kept owner that a constant-pool walk might not reference
     * directly. The top-level name is the substring before the first `$`; a nested class can only exist
     * alongside its top-level owner (same source/rewrite unit), so this is a sound over-approximation: it
     * never keeps a class whose owner the proof can't reach, and never drops a class nested in one it can.
     */
    private fun shouldKeep(name: String, keep: Set<String>): Boolean {
        if (keep.contains(name)) {
            return true
        }
        val dollar = name.indexOf('$')
        if (dollar <= 0) {
            return false // a top-level class not itself in the cone
        }
        return keep.contains(name.substring(0, dollar))
    }

    private fun internalNameOf(bytes: ByteArray): String? = try {
        ClassReader(bytes).className
    } catch (e: RuntimeException) {
        null
    }

    private fun cacheRoot(): Path =
            Path.of(System.getProperty("user.home"), ".cache", "bmc4j", CACHE_NAME)

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

    private fun sha256(): MessageDigest = try {
        MessageDigest.getInstance("SHA-256")
    } catch (e: NoSuchAlgorithmException) {
        throw IllegalStateException("SHA-256 unavailable", e)
    }

    private fun toHex(d: ByteArray): String {
        val sb = StringBuilder(d.size * 2)
        for (b in d) {
            sb.append(Character.forDigit((b.toInt() shr 4) and 0xf, 16))
                    .append(Character.forDigit(b.toInt() and 0xf, 16))
        }
        return sb.toString()
    }
}
