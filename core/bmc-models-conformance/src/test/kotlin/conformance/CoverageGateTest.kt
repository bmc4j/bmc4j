package conformance

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.io.File
import java.util.jar.JarFile

/**
 * The fail-fast coverage gate. Enumerates every bmc-model class and requires each to be
 * either differential-tested here, validated by a model proof, or explicitly waived with a reason.
 * A NEW model added to bmc-models/bmc-kotlin-models with no coverage makes this fail — so model
 * soundness can't silently erode as the library grows. Update COVERED/WAIVED when adding a model.
 */
class CoverageGateTest : FunSpec({

    test("every model class has a differential suite, a model proof, or an explicit waiver") {
        val jarPath = System.getProperty("java.class.path").split(File.pathSeparatorChar)
            .firstOrNull { it.replace('\\', '/').endsWith("bmcref-models.jar") }
            ?: error("relocated models jar not found on the test classpath")

        val models = JarFile(jarPath).use { jar ->
            jar.entries().asSequence()
                .map { it.name }
                .filter { it.endsWith(".class") && !it.contains('$') && !it.endsWith("package-info.class") }
                .map { it.removeSuffix(".class").removePrefix("bmcref/").replace('/', '.') }
                .toSortedSet()
        }

        val registered = COVERED + WAIVED.keys
        withClue("Model(s) with no conformance suite/proof or waiver — add one, or a WAIVED entry:\n  ${(models - registered).toSortedSet()}") {
            (models - registered).isEmpty() shouldBe true
        }
        withClue("Registry entries that are no longer models — remove them:\n  ${(registered - models).toSortedSet()}") {
            (registered - models).isEmpty() shouldBe true
        }
    }
})

/** Models with active conformance coverage. */
private val COVERED = setOf(
    // Differential (this module) — JVM-runnable JDK models.
    "java.util.ArrayList", "java.util.LinkedList", "java.util.HashMap", "java.util.LinkedHashMap",
    "java.util.TreeMap", "java.util.HashSet", "java.util.LinkedHashSet", "java.util.Optional", "java.util.Arrays",
    "java.math.BigInteger", "java.math.BigDecimal",
    "java.time.Instant", "java.time.Duration", "java.time.LocalDate",
    "java.time.LocalTime", "java.time.LocalDateTime", "java.time.Period",
    "java.util.concurrent.atomic.AtomicInteger", "java.util.concurrent.atomic.AtomicLong",
    "java.util.concurrent.atomic.AtomicBoolean", "java.util.concurrent.atomic.AtomicReference",
    "java.util.concurrent.CompletableFuture", "java.util.concurrent.ConcurrentHashMap",
    "java.util.concurrent.CopyOnWriteArrayList",
    // j.u.c advanced (sequential semantics) — differential here, + model proofs in proofs.concurrent.
    "java.util.concurrent.CountDownLatch", "java.util.concurrent.Semaphore",
    "java.util.concurrent.ArrayBlockingQueue", "java.util.concurrent.LinkedBlockingQueue",
    "java.util.concurrent.ImmediateExecutorService", "java.util.concurrent.Executors",
    // Model proofs (model-conformance-proofs) — Kotlin facades.
    "kotlin.collections.CollectionsKt", "kotlin.collections.SetsKt", "kotlin.collections.MapsKt",
    "kotlin.Pair", "kotlin.Triple", "kotlin.TuplesKt", "kotlin.ranges.RangesKt",
    "kotlin.comparisons.ComparisonsKt",
    // Enum.entries facade + bounded list wrapper (EnumEntriesLaws).
    "kotlin.enums.EnumEntriesKt", "kotlin.enums.EnumEntries", "kotlin.enums.EnumEntriesList",
    // kotlin.time.Duration value-class model + its facade/unit enum (DurationLaws + differential).
    "kotlin.time.Duration", "kotlin.time.DurationKt", "kotlin.time.DurationUnit",
    // Model proofs (model-conformance-proofs) — Kotlin Sequences facade (SequenceLaws).
    "kotlin.sequences.SequencesKt", "kotlin.sequences.Sequence", "kotlin.sequences.ListSequence",
    // Model proofs (model-conformance-proofs) — stream models (StreamLaws: mapToInt/sum/count/filter/collect).
    "java.util.stream.Stream", "java.util.stream.IntStream", "java.util.stream.ListStream",
    "java.util.stream.IntArrayStream", "java.util.stream.Collectors",
    "java.util.stream.LongStream", "java.util.stream.LongArrayStream",
)

/** Models intentionally not given a dedicated suite, each with the reason it's safe. */
private val WAIVED = mapOf(
    "java.lang.Iterable" to "interface — exercised via concrete impls",
    "java.util.Collection" to "interface — via impls",
    "java.util.List" to "interface — via ArrayList/LinkedList",
    "java.util.Map" to "interface — via HashMap/TreeMap/ConcurrentHashMap",
    "java.util.Set" to "interface — via HashSet/LinkedHashSet",
    "java.util.Queue" to "interface — via ArrayBlockingQueue/LinkedBlockingQueue",
    "java.util.Iterator" to "interface — via collection iteration",
    "java.util.NoSuchElementException" to "exception type (no behavior)",
    "java.util.concurrent.BlockingQueue" to "interface — via ArrayBlockingQueue/LinkedBlockingQueue",
    "java.util.concurrent.Executor" to "interface — via ImmediateExecutorService",
    "java.util.concurrent.ExecutorService" to "interface — via ImmediateExecutorService",
    "java.util.concurrent.Future" to "interface — via ImmediateExecutorService's completed future",
    "java.util.concurrent.TimeUnit" to "enum — ignored time arg on sequential models (no behavior)",
    "java.math.RoundingMode" to "enum — exercised via BigDecimal divide/setScale",
    "java.util.stream.Collector" to "interface — via Collectors",
    "kotlin.Result" to "coroutine Result plumbing — exercised by the coroutines example",
    "kotlin.ResultKt" to "coroutine Result plumbing — exercised by the coroutines example",
    "kotlin.jvm.internal.Intrinsics" to "null-safety intrinsic — exercised by fundamentals-kotlin null-safety proofs",
    "kotlin.coroutines.intrinsics.CoroutineSingletons" to "coroutine runtime model — coroutines example",
    "kotlin.coroutines.intrinsics.IntrinsicsKt" to "coroutine runtime model — coroutines example",
    "kotlin.coroutines.jvm.internal.BaseContinuationImpl" to "coroutine runtime model — coroutines example",
    "kotlin.coroutines.jvm.internal.Boxing" to "coroutine runtime model — coroutines example",
    "kotlin.coroutines.jvm.internal.ContinuationImpl" to "coroutine runtime model — coroutines example",
    "kotlin.coroutines.jvm.internal.SuspendLambda" to "coroutine runtime model — coroutines example",
    "kotlinx.coroutines.BuildersKt" to "coroutine runtime model (immediate-drive) — coroutines example",
    "kotlinx.coroutines.CompletedDeferred" to "coroutine runtime model — coroutines example",
    "kotlinx.coroutines.CompletedJob" to "coroutine runtime model — coroutines example",
    "kotlinx.coroutines.CoroutineDispatcher" to "coroutine runtime model — coroutines example",
    "kotlinx.coroutines.CoroutineScopeKt" to "coroutine runtime model — coroutines example",
    "kotlinx.coroutines.CoroutineStart" to "coroutine runtime model — coroutines example",
    "kotlinx.coroutines.Deferred" to "coroutine runtime model — coroutines example",
    "kotlinx.coroutines.DelayKt" to "coroutine runtime model — coroutines example",
    "kotlinx.coroutines.Dispatchers" to "coroutine runtime model — coroutines example",
    "kotlinx.coroutines.Drive" to "coroutine runtime helper — coroutines example",
    "kotlinx.coroutines.Job" to "coroutine runtime model — coroutines example",
)
