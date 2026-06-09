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
