package org.bmc4j.engine

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Extracts the clean Kotlin runtime models bundled (as resources under
 * `bmc-kotlin-models/`) in this jar to a local directory, so JBMC can be
 * pointed at them. They are resources — never on a runtime classpath — so they don't
 * shadow the real Kotlin classes when tests execute; only JBMC's analysis classpath
 * gets them (prepended), where they replace kotlin-stdlib's `Intrinsics`.
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
            // Sizing helper the inline associate*/groupBy emit (coerceAtLeast) — stubbed to nondet
            // otherwise, which poisons their LinkedHashMap(int) initial capacity.
            "kotlin/ranges/RangesKt.class",
            // compareValues — the keySelector Comparator that sortedBy { } generates delegates to it;
            // stubbed to nondet otherwise, silently unsoundening the sort order.
            "kotlin/comparisons/ComparisonsKt.class",
            // Sequences (sequenceOf/asSequence + map/filter/toList/sumOfInt/count) — route through
            // the SequencesKt facade + stdlib internals JBMC stubs; modeled eager over a bounded
            // ListSequence.
            "kotlin/sequences/SequencesKt.class",
            "kotlin/sequences/Sequence.class",
            "kotlin/sequences/ListSequence.class",
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

    /** Extract the models and return the classpath root dir, or null if none bundled. */
    @JvmStatic
    fun extractRoot(): String? {
        val dir = Path.of(System.getProperty("user.home"), ".cache", "bmc4j", "kotlin-models")
        var any = false
        for (rel in FILES) {
            try {
                BundledKotlinModels::class.java.classLoader
                        .getResourceAsStream("$ROOT/$rel").use { input ->
                            if (input != null) {
                                val target = dir.resolve(rel)
                                Files.createDirectories(target.parent)
                                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING)
                                any = true
                            }
                        }
            } catch (e: IOException) {
                // Best effort: if a model can't be extracted, JBMC falls back to the real class.
            }
        }
        return if (any) dir.toString() else null
    }
}
