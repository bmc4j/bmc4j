package conformance

/**
 * The model registries, shared by the class-level coverage gate ([CoverageGateTest]) and the
 * per-member model auditing gate ([ModelAuditGateTest]).
 */

/** Models with active conformance coverage (class-level). */
val COVERED: Set<String> = setOf(
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

/**
 * COVERED models that have NO own auditable surface to pin a method-level [BmcModelConforms] on, yet
 * are genuinely covered structurally — so the per-member auditing gate must not demand an annotation
 * they have nowhere to put. (Before the annotation went method-only these carried a class-level
 * blanket `@BmcModelConforms`; with no own conforming members, there is no method to move it to.)
 *
 *  - `kotlin.enums.EnumEntries`: a marker sub-interface of `List` with ZERO own members; all behavior
 *    is supplied (and audited) by the concrete `kotlin.enums.EnumEntriesList` model.
 *  - `kotlin.time.DurationUnit`: an enum of constants only (no conforming instance methods); exercised
 *    via the `kotlin.time.Duration` model's conversions.
 */
val COVERED_NO_OWN_SURFACE: Set<String> = setOf(
    "kotlin.enums.EnumEntries",
    "kotlin.time.DurationUnit",
)

/** Models intentionally not given a dedicated suite, each with the reason it's safe. */
val WAIVED: Map<String, String> = mapOf(
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

/**
 * The classes the PER-MEMBER auditing gate enforces: a real JDK class loads side by side with the
 * model and its full public/protected surface is enumerated and required to be modeled, declared, or
 * tail-waived. These are the concrete java.* models — exactly the recurring-disease cases where a
 * missing member becomes a silent nondet stub on the analysis path.
 *
 * <p>Kotlin facade containers (`*Kt`), value classes, enums, and coroutine plumbing are NOT in this
 * set: their "real target" is an open-universe of top-level/extension functions for which "complete
 * coverage of the real class's surface" is neither well-defined nor valuable. They remain covered at
 * CLASS level (COVERED/WAIVED) and are checked by [CoverageGateTest]; the per-member gate still
 * audits any annotations they DO carry (dangling-declaration + implemented-but-unannotated checks)
 * but does not demand whole-surface enumeration. See ModelAuditGateTest for the precise rules.
 */
val PER_MEMBER_ENFORCED: Set<String> = setOf(
    "java.util.ArrayList", "java.util.LinkedList", "java.util.HashMap", "java.util.LinkedHashMap",
    "java.util.TreeMap", "java.util.HashSet", "java.util.LinkedHashSet", "java.util.Optional", "java.util.Arrays",
    "java.math.BigInteger", "java.math.BigDecimal",
    "java.time.Instant", "java.time.Duration", "java.time.LocalDate",
    "java.time.LocalTime", "java.time.LocalDateTime", "java.time.Period",
    "java.util.concurrent.atomic.AtomicInteger", "java.util.concurrent.atomic.AtomicLong",
    "java.util.concurrent.atomic.AtomicBoolean", "java.util.concurrent.atomic.AtomicReference",
    "java.util.concurrent.CompletableFuture", "java.util.concurrent.ConcurrentHashMap",
    "java.util.concurrent.CopyOnWriteArrayList",
    "java.util.concurrent.CountDownLatch", "java.util.concurrent.Semaphore",
    "java.util.concurrent.ArrayBlockingQueue", "java.util.concurrent.LinkedBlockingQueue",
    "java.util.concurrent.Executors",
    "java.util.stream.Stream", "java.util.stream.IntStream", "java.util.stream.Collectors",
    "java.util.stream.LongStream",
)
