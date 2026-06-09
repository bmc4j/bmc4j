package org.bmc4j.engine

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

/**
 * Extracts the clean Kotlin runtime models bundled (as resources under
 * `bmc-kotlin-models/`) in this jar to a local directory, so JBMC can be
 * pointed at them. They are resources — never on a runtime classpath — so they don't
 * shadow the real Kotlin classes when tests execute; only JBMC's analysis classpath
 * gets them (prepended), where they replace kotlin-stdlib's `Intrinsics`.
 *
 * ## Atomic, content-keyed extraction (no shared-dir overwrite race)
 * [extractRoot] is called once per proof, on whatever JUnit pool thread runs it, so under
 * parallelism MANY threads extract concurrently - and several agents on one machine share the
 * `~/.cache/bmc4j/` tree. An earlier version copied every model class into ONE fixed directory
 * (`kotlin-models`) with a non-atomic `Files.copy(REPLACE_EXISTING)` on every call. That made the
 * model classes a SHARED MUTABLE FILE that a concurrent reader (the rewrite-chain mirror computing a
 * content hash, [ModelSlice], or JBMC itself) could observe MID-OVERWRITE - a truncated/partial
 * `SequencesKt.class` parses to a bodiless class, which JBMC links to an unconstrained nondet stub
 * (a havoc) even though the class IS on the classpath. That is the non-determinism behind proofs that
 * flip between green and UNKNOWN run-to-run with no code change: a transient torn read of a present
 * model body.
 *
 * The fix applies the SAME discipline [ClasspathMirror] / [ModelSlice] already use for their dir
 * mirrors: extract into a fresh unique temp dir, then ATOMICALLY publish it to a directory whose name
 * is the SHA-256 of the extracted content, marked complete with a `.done` file written last. A
 * completed (`.done`-marked) content dir is IMMUTABLE - no call ever re-opens it for writing - so a
 * reader either sees a complete dir or, on a cache miss, the publisher builds off to the side and moves
 * it into place in one atomic step. The returned path therefore changes only when the bundled
 * resources change (a fresh build), making every downstream content hash over it stable too.
 */
object BundledKotlinModels {

    private const val ROOT = "bmc-kotlin-models"
    private val FILES = arrayOf(
            "kotlin/jvm/internal/Intrinsics.class",
            // List/Set/Map factories (listOf/mutableListOf/setOf/mapOf/…) — route through stdlib
            // facades JBMC stubs; these return bmc4j's bounded collection models directly.
            "kotlin/collections/CollectionsKt.class",
            "kotlin/collections/SetsKt.class",
            "kotlin/collections/MapsKt.class",
            "kotlin/Pair.class",
            "kotlin/TuplesKt.class",
            // kotlin.text.StringsKt facade — every String/CharSequence extension a Kotlin call site emits
            // ("x".trim() -> StringsKt.trim((CharSequence)"x")) binds here. Unextracted, JBMC falls back to
            // the real stdlib facade and nondet-stubs it; extracted, the bounded char-array transforms
            // (modeled over the sound java.lang.String primitives) shadow it.
            "kotlin/text/StringsKt.class",
            // Concrete CharIterator backing for CharSequence.iterator() — a real nextChar() body walked
            // by index, so JBMC never nondet-stubs the abstract virtual.
            "kotlin/text/StringCharIterator.class",
            // Sizing helper the inline associate*/groupBy emit (coerceAtLeast) — stubbed to nondet
            // otherwise, which poisons their LinkedHashMap(int) initial capacity.
            "kotlin/ranges/RangesKt.class",
            // compareValues — the keySelector Comparator that sortedBy { } generates delegates to it;
            // stubbed to nondet otherwise, silently unsoundening the sort order.
            "kotlin/comparisons/ComparisonsKt.class",
            // Enum.entries (Kotlin 1.9+): an enum's <clinit> builds $ENTRIES via
            // EnumEntriesKt.enumEntries($VALUES); the real EnumEntriesList stubs to nondet. These
            // return bmc4j's bounded list model populated from values().
            "kotlin/enums/EnumEntriesKt.class",
            "kotlin/enums/EnumEntries.class",
            "kotlin/enums/EnumEntriesList.class",
            // kotlin.time.Duration value class (bit-packed Long ABI) + its facade + unit enum — the inline
            // unit extensions (90.minutes) emit DurationKt.toDuration + Duration."plus-LRDsOJo"/etc., all
            // unmodeled stdlib internals JBMC stubs today (spurious REFUTED).
            "kotlin/time/Duration.class",
            "kotlin/time/DurationKt.class",
            "kotlin/time/DurationUnit.class",
            // kotlin.random.Random bounded-draw model — the "prove for every random outcome" surface.
            // A proof's Random.Default.nextInt(...) dispatches to the real Random$Default ->
            // PlatformThreadLocalRandom, which JBMC stubs to UNCONSTRAINED nondet (spurious REFUTED);
            // these model each draw as nondet-IN-RANGE, and make the seeded factory a loud UNKNOWN.
            "kotlin/random/Random.class",
            "kotlin/random/Random\$Default.class",
            "kotlin/random/RandomKt.class",
            // Sequences (sequenceOf/asSequence + map/filter/toList/sumOfInt/count) — route through
            // the SequencesKt facade + stdlib internals JBMC stubs; modeled eager over a bounded
            // ListSequence.
            "kotlin/sequences/SequencesKt.class",
            "kotlin/sequences/Sequence.class",
            "kotlin/sequences/ListSequence.class",
            // Coroutine CORE type hierarchy (kotlin.coroutines.*): the Continuation / CoroutineContext
            // tree the bundled continuation impls and kotlinx CoroutineDispatcher extend/implement. These
            // are bundled — rather than left to resolve against the real kotlin-stdlib jar — so that the
            // checkcast on a bundled subtype (e.g. `withContext(Dispatchers.IO){}` emits
            // `checkcast CoroutineContext` on the bundled dispatcher; a state machine emits
            // `checkcast Continuation` on its bundled continuation) resolves its whole subtype->supertype
            // chain within ONE classpath source. With the supertype in the real stdlib jar instead, JBMC
            // had to lazily link that hierarchy edge ACROSS classpath sources, and on some platforms /
            // conversion orders dropped the link and havoc'd the cast — a nondeterministic spurious
            // "Dynamic cast check" refutation. Single-source resolution makes the cast deterministic.
            "kotlin/coroutines/Continuation.class",
            "kotlin/coroutines/CoroutineContext.class",
            "kotlin/coroutines/CoroutineContext\$Element.class",
            "kotlin/coroutines/CoroutineContext\$Key.class",
            "kotlin/coroutines/ContinuationInterceptor.class",
            "kotlin/coroutines/ContinuationInterceptor\$KeyImpl.class",
            "kotlin/coroutines/AbstractCoroutineContextElement.class",
            "kotlin/coroutines/EmptyCoroutineContext.class",
            // Coroutine models (suspend support).
            "kotlin/coroutines/intrinsics/CoroutineSingletons.class",
            "kotlin/coroutines/intrinsics/IntrinsicsKt.class",
            "kotlin/Result.class",
            "kotlin/Result\$Failure.class",
            "kotlin/ResultKt.class",
            "kotlin/coroutines/jvm/internal/Boxing.class",
            "kotlin/coroutines/jvm/internal/BaseContinuationImpl.class",
            "kotlin/coroutines/jvm/internal/ContinuationImpl.class",
            "kotlin/coroutines/jvm/internal/SuspendLambda.class",
            // The remaining kotlin.coroutines.jvm.internal.* types a generated state machine references,
            // bundled so EVERY coroutine type a continuation touches resolves from one classpath source
            // (the rest of the hierarchy was bundled for the same single-source-cast reason): the
            // @DebugMetadata method annotation kotlinc stamps on every invokeSuspend, and SpillingKt
            // (loops/ref-spills emit nullOutSpilledVariable). Left in the stdlib jar, the continuation
            // straddled two sources and JBMC dropped the subtype->supertype `checkcast Continuation` link
            // on the older-Kotlin legs (e.g. loop-bodied countTo on kotlin-2.3.21) — a spurious cast REFUTED.
            "kotlin/coroutines/jvm/internal/DebugMetadata.class",
            "kotlin/coroutines/jvm/internal/SpillingKt.class",
            // Idiomatic runBlocking { } support.
            "kotlinx/coroutines/BuildersKt.class",
            "kotlinx/coroutines/BuildersKt\$ImmediateScope.class",
            "kotlinx/coroutines/BuildersKt\$Completion.class",
            // Other scope builders, modeled as immediate synchronous drive so the
            // real dispatcher/JobSupport machinery (which trips JBMC's
            // create_parameter_names invariant) is never loaded.
            "kotlinx/coroutines/CoroutineScopeKt.class",
            "kotlinx/coroutines/CoroutineDispatcher.class",
            "kotlinx/coroutines/DelayKt.class",
            "kotlinx/coroutines/Dispatchers.class",
            // async/await: clean Job/Deferred + a completed-Deferred result, and a
            // clean CoroutineStart enum (the real one's invoke() trips the frontend).
            "kotlinx/coroutines/Job.class",
            "kotlinx/coroutines/Deferred.class",
            "kotlinx/coroutines/CompletedDeferred.class",
            "kotlinx/coroutines/CompletedJob.class",
            "kotlinx/coroutines/CoroutineStart.class",
            "kotlinx/coroutines/Drive.class",
            "kotlinx/coroutines/Drive\$ImmediateScope.class",
            "kotlinx/coroutines/Drive\$Completion.class")

    /**
     * Extract the models and return the classpath root dir, or null if none bundled.
     *
     * Extraction is ATOMIC and content-keyed (see the class doc): the models are read into memory,
     * hashed, and published once into a `<sha256>` directory marked complete with a `.done` file. A
     * completed content dir is immutable, so concurrent callers (proofs on the JUnit pool, or other
     * agents sharing `~/.cache/bmc4j/`) never observe a partial/mid-overwrite model class — the torn
     * read that intermittently made JBMC nondet-stub a present-on-classpath model body (e.g.
     * `SequencesKt`), flipping a clean proof to UNKNOWN with no code change.
     */
    @JvmStatic
    fun extractRoot(): String? {
        // Read every bundled model resource into memory first (no filesystem writes yet), so the
        // content hash and the published dir see one consistent snapshot.
        val contents = LinkedHashMap<String, ByteArray>()
        for (rel in FILES) {
            try {
                BundledKotlinModels::class.java.classLoader
                        .getResourceAsStream("$ROOT/$rel").use { input ->
                            if (input != null) {
                                contents[rel] = input.readAllBytes()
                            }
                        }
            } catch (e: IOException) {
                // Best effort: if a model can't be read, JBMC falls back to the real class.
            }
        }
        if (contents.isEmpty()) {
            return null
        }

        val root = cacheRoot()
        val hash = contentHash(contents)
        val dest = root.resolve(hash)
        val done = root.resolve(hash + DONE_SUFFIX)

        // Cache hit: a completed extraction for this exact content already exists and is immutable.
        if (Files.isDirectory(dest) && Files.isRegularFile(done)) {
            return dest.toString()
        }

        try {
            Files.createDirectories(root)
            // Build into a fresh unique temp dir, then atomically publish it (the marker last). A fresh
            // dir guarantees no stale class survives; building off to the side keeps a concurrent reader
            // from ever seeing a partial extraction as complete.
            val tmp = Files.createTempDirectory(root, "$hash-")
            try {
                for ((rel, bytes) in contents) {
                    val target = tmp.resolve(rel)
                    Files.createDirectories(target.parent)
                    Files.write(target, bytes)
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
                if (!Files.isRegularFile(done)) {
                    Files.write(done, ByteArray(0)) // completion marker last
                }
            } finally {
                deleteRecursivelyIfExists(tmp) // no-op if the move consumed it
            }
        } catch (e: IOException) {
            // Publishing failed (IO/permissions). Fall back to the just-built (or pre-existing) dest if
            // it is complete; otherwise signal "none extracted" so JBMC uses the real classes — never
            // return a half-written dir.
            if (Files.isDirectory(dest) && Files.isRegularFile(done)) {
                return dest.toString()
            }
            return null
        }
        return if (Files.isDirectory(dest)) dest.toString() else null
    }

    private const val DONE_SUFFIX = ".done"

    private fun cacheRoot(): Path =
            Path.of(System.getProperty("user.home"), ".cache", "bmc4j", "kotlin-models")

    /** SHA-256 over the extracted content: each entry's relative path then its bytes, length-framed so
     *  two different splits can't hash the same. Keys the published dir so distinct content gets a
     *  distinct dest — the analogue of [ClasspathMirror]'s `dirContentHash`. */
    private fun contentHash(contents: Map<String, ByteArray>): String {
        val md = try {
            MessageDigest.getInstance("SHA-256")
        } catch (e: NoSuchAlgorithmException) {
            throw IllegalStateException("SHA-256 unavailable", e)
        }
        for (rel in contents.keys.sorted()) {
            val relBytes = rel.replace('\\', '/').toByteArray(StandardCharsets.UTF_8)
            md.update(intToBytes(relBytes.size))
            md.update(relBytes)
            val bytes = contents.getValue(rel)
            md.update(intToBytes(bytes.size))
            md.update(bytes)
        }
        return toHex(md.digest())
    }

    private fun intToBytes(v: Int): ByteArray = byteArrayOf(
            (v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte())

    private fun deleteRecursivelyIfExists(dir: Path) {
        if (!Files.exists(dir)) {
            return
        }
        Files.walk(dir).use { walk ->
            walk.sorted(Comparator.reverseOrder()).forEach { p ->
                try {
                    Files.deleteIfExists(p)
                } catch (ignored: IOException) {
                    // best-effort temp cleanup; a leftover temp dir never affects correctness
                }
            }
        }
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
