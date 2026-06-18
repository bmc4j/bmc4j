package conformance

/**
 * The model registries, shared by the class-level coverage gate ([CoverageGateTest]) and the
 * per-member model auditing gate ([ModelAuditGateTest]).
 */

/** Models with active conformance coverage (class-level). */
val COVERED: Set<String> = setOf(
    // Differential (this module) — JVM-runnable JDK models.
    "java.util.ArrayList", "java.util.LinkedList", "java.util.HashMap", "java.util.LinkedHashMap",
    "java.util.TreeMap", "java.util.HashSet", "java.util.LinkedHashSet", "java.util.TreeSet",
    // EnumMap/EnumSet: enum-keyed/enum-element bounded array models built over the HashMap/HashSet
    // models (differential MapConformanceTest/JavaUtilTailConformanceTest + @BmcProof). Objects: the
    // null-safe / bounds-check static utility (differential JavaUtilTailConformanceTest + @BmcProof).
    "java.util.EnumMap", "java.util.EnumSet", "java.util.Objects",
    // The legacy Dictionary/Hashtable/Properties family, brought onto the same faithful shared-state
    // footing as the HashMap family (parallel bounded arrays + linear lookup). Hashtable/Properties are
    // per-member-enforced (concrete recurring-disease classes — the static-Properties-in-<clinit> case
    // that havocs getProperty to a nondet unbounded String); Dictionary is the abstract base (WAIVED).
    // Model proofs: proofs.properties (HashtableLaws / PropertiesLaws).
    "java.util.Hashtable", "java.util.Properties",
    "java.util.ArrayDeque", "java.util.PriorityQueue",
    "java.util.Optional",
    "java.util.OptionalInt", "java.util.OptionalLong", "java.util.OptionalDouble", "java.util.Arrays",
    "java.util.Collections",
    // Summary-statistics accumulators (differential + DoubleStreamLaws). The int/long ones are fully
    // sound (integer min/max, double-division average); DoubleSummaryStatistics walls getMin/getMax off
    // (FP total order) but models accept/getCount/getSum/getAverage.
    "java.util.IntSummaryStatistics", "java.util.LongSummaryStatistics", "java.util.DoubleSummaryStatistics",
    "java.math.BigInteger", "java.math.BigDecimal",
    // java.lang.Character — static case-fold / classification surface modeled for the no-refine
    // (CHAR_ARRAY_MODEL) path, where Character is NOT a JBMC intrinsic and the engine core-models
    // Character NPEs (its CharacterData table is absent). Model proofs: proofs.strings.CharacterLaws.
    // Precise over ASCII (+ the Latin-1 supplement for the case folds), loud beyond — per-member
    // @BmcModelConforms with a throw-loud boundary. NOT per-member-enforced (only the commonly-reached
    // static surface is modeled; the rest is simply absent from the model class).
    "java.lang.Character",
    // java.lang.Integer / java.lang.Long — focused int/long -> String decimal-formatting models. The
    // default toString delegates to the refinement intrinsic (CProverString.toString, unchanged under
    // refinement); a @ConditionalOn(STRING_REFINEMENT_OFF) override does a BOUNDED digit build under
    // no-refine (CHAR_ARRAY_MODEL), where the intrinsic would otherwise return an unconstrained
    // nondet-length String. Class-level COVERED only (toString-focused, not whole-surface enforced);
    // model proofs: proofs.strings.IntToStringLaws / LongToStringLaws.
    "java.lang.Integer", "java.lang.Long",
    "java.time.Instant", "java.time.Duration", "java.time.LocalDate",
    "java.time.LocalTime", "java.time.LocalDateTime", "java.time.Period",
    // java.time small enums + the offset wrapper. The enums (DayOfWeek/Month/IsoEra) carry method-level
    // @BmcModelConforms on their value/arithmetic surface but stay OUT of PER_MEMBER_ENFORCED (enums:
    // values()/valueOf() + the open TemporalAccessor/Adjuster surface), so they're covered at class level
    // here and audited (dangling-decl + loud-body) via the annotations they DO carry. ZoneOffset is a
    // concrete value class → per-member-enforced below; its abstract ZoneId base is WAIVED.
    "java.time.DayOfWeek", "java.time.Month", "java.time.chrono.IsoEra", "java.time.ZoneOffset",
    // java.time.temporal field/unit metadata. ChronoField/ChronoUnit are enums (class-level COVERED only,
    // like the DayOfWeek/Month enums — their constants + the value/classification surface carry method-level
    // @BmcModelConforms; values()/valueOf() + the delegating TemporalField/Unit plumbing keep them out of
    // PER_MEMBER_ENFORCED). ValueRange is a concrete four-long value class → per-member-enforced below.
    "java.time.temporal.ChronoField", "java.time.temporal.ChronoUnit", "java.time.temporal.ValueRange",
    // NB the float/double IEEE total order is NOT a java.lang.Float/Double model — modeling those
    // pervasively-reached classes put a bounded FP model on every proof's classpath and crashed jbmc's
    // solver on unrelated proofs. It lives in the org.bmc4j.models.audit.FpTotalOrder helper that
    // java.util.Arrays calls; the reclaimed Arrays float/double surface is covered under java.util.Arrays
    // (model proofs proofs.primitives.FloatDoubleArraysLaws + differential conformance.OptionalArraysConformanceTest).
    // java.util.Random — the "prove for every random outcome" model (RandomLaws model proofs).
    "java.util.Random",
    "java.util.concurrent.atomic.AtomicInteger", "java.util.concurrent.atomic.AtomicLong",
    "java.util.concurrent.atomic.AtomicBoolean", "java.util.concurrent.atomic.AtomicReference",
    "java.util.concurrent.CompletableFuture", "java.util.concurrent.ConcurrentHashMap",
    "java.util.concurrent.CopyOnWriteArrayList",
    // j.u.c advanced (sequential semantics) — differential here, + model proofs in proofs.concurrent.
    "java.util.concurrent.CountDownLatch", "java.util.concurrent.Semaphore",
    "java.util.concurrent.ArrayBlockingQueue", "java.util.concurrent.LinkedBlockingQueue",
    "java.util.concurrent.ImmediateExecutorService", "java.util.concurrent.Executors",
    "java.util.concurrent.ImmediateScheduledExecutorService",
    // Model proofs (model-conformance-proofs) — Kotlin facades.
    "kotlin.collections.CollectionsKt", "kotlin.collections.SetsKt", "kotlin.collections.MapsKt",
    "kotlin.text.StringsKt",
    // kotlin.collections.ArraysKt (copy/fill surface), kotlin.math.MathKt (non-inline math residue),
    // kotlin.text.CharsKt (non-inline char residue) — facade models (KotlinArraysLaws/KotlinMathLaws/
    // KotlinCharsLaws); per-member-enforced below.
    "kotlin.collections.ArraysKt", "kotlin.math.MathKt", "kotlin.text.CharsKt",
    "kotlin.Pair", "kotlin.Triple", "kotlin.TuplesKt", "kotlin.ranges.RangesKt",
    "kotlin.comparisons.ComparisonsKt",
    // Enum.entries facade + bounded list wrapper (EnumEntriesLaws).
    "kotlin.enums.EnumEntriesKt", "kotlin.enums.EnumEntries", "kotlin.enums.EnumEntriesList",
    // kotlin.time.Duration value-class model + its facade/unit enum (DurationLaws + differential).
    "kotlin.time.Duration", "kotlin.time.DurationKt", "kotlin.time.DurationUnit",
    // kotlin.random.Random "prove for every outcome" model + its seeded-factory facade (RandomLaws).
    "kotlin.random.Random", "kotlin.random.RandomKt",
    // kotlin.Result value-class model — non-inline ABI surface (ResultLaws model proofs).
    "kotlin.Result",
    // kotlin.jvm.internal.Intrinsics null-safety helpers (fundamentals-kotlin null-safety proofs).
    "kotlin.jvm.internal.Intrinsics",
    // Model proofs (model-conformance-proofs) — Kotlin Sequences facade (SequenceLaws).
    "kotlin.sequences.SequencesKt", "kotlin.sequences.Sequence", "kotlin.sequences.ListSequence",
    // IndexedValue(index, value) pair produced by SequencesKt.withIndex (SequenceLaws withIndex proofs).
    "kotlin.collections.IndexedValue",
    // Model proofs (model-conformance-proofs) — stream models (StreamLaws: mapToInt/sum/count/filter/collect).
    "java.util.stream.Stream", "java.util.stream.IntStream", "java.util.stream.ListStream",
    "java.util.stream.IntArrayStream", "java.util.stream.Collectors",
    "java.util.stream.LongStream", "java.util.stream.LongArrayStream",
    "java.util.stream.DoubleStream", "java.util.stream.DoubleArrayStream",
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
    // The skeletal Abstract* collection bases a USER subclass extends (overriding the abstract
    // primitives) so it devirtualizes through the JDK interface. Their derived surface is modeled
    // over those primitives (AbstractList/Map: index/entrySet loops); exercised via the subclass
    // devirt proofs (proofs.abstractcollections AbstractCollectionSubclassLaws). Abstract bases with
    // no own JVM-runnable twin to differentially test — covered structurally via the subclass proofs.
    "java.util.AbstractCollection" to "abstract skeletal base — exercised via user subclasses (AbstractCollectionSubclassLaws)",
    "java.util.AbstractList" to "abstract skeletal base — exercised via user subclasses (AbstractCollectionSubclassLaws)",
    "java.util.AbstractSet" to "abstract skeletal base — exercised via user subclasses (AbstractCollectionSubclassLaws)",
    "java.util.AbstractMap" to "abstract skeletal base — exercised via user subclasses (AbstractCollectionSubclassLaws)",
    "java.util.Dictionary" to "abstract skeletal base — exercised via the concrete Hashtable/Properties models (per-member-enforced)",
    // The kotlin.collections.Abstract* skeletal bases the Kotlin stdlib's read-only/mutable collections
    // and library persistent collections (kotlinx.collections.immutable) extend. They keep the size
    // primitive (getSize) abstract so it resolves to the concrete override, and supply the derived
    // size()/isEmpty()/contains surface over it (the read-only Mutable* ones extend the java.util.Abstract*
    // models). No JVM-runnable twin to differentially test — exercised via the kotlinx persistent-collection
    // structural proofs (size()/isEmpty()/add().size devirt through these bases), like the java.util
    // Abstract* skeletal-base waivers above.
    "kotlin.collections.AbstractCollection" to "abstract skeletal base — exercised via KotlinAbstractCollectionSubclassLaws + the kotlinx persistent-collection size/isEmpty devirt proofs",
    "kotlin.collections.AbstractList" to "abstract skeletal base — exercised via KotlinAbstractCollectionSubclassLaws + the kotlinx persistent-collection size/isEmpty devirt proofs",
    "kotlin.collections.AbstractSet" to "abstract skeletal base — exercised via KotlinAbstractCollectionSubclassLaws + the kotlinx persistent-collection size/isEmpty devirt proofs",
    "kotlin.collections.AbstractMap" to "abstract skeletal base — exercised via KotlinAbstractCollectionSubclassLaws + the kotlinx persistent-collection size/isEmpty devirt proofs",
    "kotlin.collections.AbstractMutableCollection" to "abstract skeletal base — exercised via KotlinAbstractCollectionSubclassLaws + the kotlinx persistent-collection devirt proofs (builders extend it)",
    "kotlin.collections.AbstractMutableList" to "abstract skeletal base — keeps PersistentVectorBuilder non-opaque (modCount accessor); KotlinAbstractCollectionSubclassLaws + kotlinx devirt proofs",
    "kotlin.collections.AbstractMutableSet" to "abstract skeletal base — exercised via KotlinAbstractCollectionSubclassLaws + the kotlinx persistent-collection devirt proofs (builders extend it)",
    "kotlin.collections.AbstractMutableMap" to "abstract skeletal base — exercised via KotlinAbstractCollectionSubclassLaws + the kotlinx persistent-collection devirt proofs (builders extend it)",
    "java.util.ArrayListView" to "internal model helper (no JDK twin) — the live subList/reversed view over the ArrayList model; exercised via ArrayList's subList/reversed conformance + @BmcProof",
    "java.util.BmcSortWitness" to "internal model helper (no JDK twin) — the nondet sorted-permutation witness shared by the comparator-driven sort/sorted models; exercised via the sort surface's @BmcProof (proofs.sort SortWitnessLaws)",
    "java.util.BmcNaturalOrder" to "internal model helper (no JDK twin) — the single concrete, devirtualizable natural-order comparison (bit-precise for the builtin Comparables, loud otherwise) that drives the witness for the natural-order sort/sorted models; exercised via the sort surface's @BmcProof (proofs.sort NaturalOrderSortLaws)",
    "java.util.Map" to "interface — via HashMap/TreeMap/ConcurrentHashMap",
    "java.util.Set" to "interface — via HashSet/LinkedHashSet",
    "java.util.SequencedCollection" to "interface (Java 21+) — head/tail surface modeled on the concrete collections; type-only here for the Java-17 floor build",
    "java.util.SequencedSet" to "interface (Java 21+) — head/tail surface modeled on the concrete sets; type-only here for the Java-17 floor build",
    "java.util.SequencedMap" to "interface (Java 21+) — head/tail surface modeled on the concrete maps; type-only here for the Java-17 floor build",
    "java.util.Queue" to "interface — via ArrayBlockingQueue/LinkedBlockingQueue",
    "java.util.Deque" to "interface — via ArrayDeque (and the LinkedList model's deque surface)",
    "java.util.Iterator" to "interface — via collection iteration",
    "java.util.Enumeration" to "interface — via Collections.enumeration's concrete bounded enumeration",
    "java.util.NoSuchElementException" to "exception type (no behavior)",
    "java.util.concurrent.BlockingQueue" to "interface — via ArrayBlockingQueue/LinkedBlockingQueue",
    "java.util.concurrent.Executor" to "interface — via ImmediateExecutorService",
    "java.util.concurrent.ExecutorService" to "interface — via ImmediateExecutorService",
    "java.util.concurrent.ScheduledExecutorService" to "interface — via ImmediateScheduledExecutorService",
    "java.util.concurrent.Future" to "interface — via ImmediateExecutorService's completed future",
    "java.util.concurrent.CompletionStage" to "interface — via CompletableFuture (its single concrete impl)",
    "java.util.concurrent.ScheduledFuture" to "interface — via ImmediateScheduledExecutorService's completed future",
    "java.util.concurrent.TimeUnit" to "enum — ignored time arg on sequential models (no behavior)",
    "java.math.RoundingMode" to "enum — exercised via BigDecimal divide/setScale",
    // Constant environmental stand-ins: a single-threaded proof reaches these as fixed environment, so
    // currentThread()/getId()/threadId() and getRuntime()/availableProcessors() return fixed representative
    // values (stopping a nondet thread id / processor count from poisoning otherwise-deterministic logic).
    // Constant accessors with no behavioral surface to differentially test; the rest of each class's
    // lifecycle/scheduling resp. process/memory-control surface is the open remainder absorbed (loud) by
    // its class-level @BmcModelTail.
    "java.lang.Thread" to "constant environmental stand-in — fixed representative thread id; no behavioral surface to differentially test (remainder is the class-level @BmcModelTail)",
    "java.lang.Runtime" to "constant environmental stand-in — fixed representative processor count; no behavioral surface to differentially test (remainder is the class-level @BmcModelTail)",
    "java.time.ZoneId" to "abstract base — exercised via the concrete ZoneOffset model (per-member-enforced)",
    "java.util.stream.Collector" to "interface — via Collectors",
    "kotlin.ResultKt" to "coroutine Result plumbing — exercised by the coroutines example",
    // kotlin.coroutines core hierarchy — type-only stand-ins bundled so coroutine casts resolve from a
    // single source. No analysis surface to differentially test (like the java.util Collection/List/Set
    // and SequencedCollection/Set/Map structural-interface waivers); exercised via the coroutines example.
    "kotlin.coroutines.Continuation" to "coroutine context hierarchy — type-only stand-in (no own behavior); coroutines example",
    "kotlin.coroutines.CoroutineContext" to "coroutine context hierarchy — type-only stand-in (no own behavior); coroutines example",
    "kotlin.coroutines.ContinuationInterceptor" to "coroutine context hierarchy — type-only stand-in (no own behavior); coroutines example",
    "kotlin.coroutines.AbstractCoroutineContextElement" to "coroutine context hierarchy — type-only stand-in (no own behavior); coroutines example",
    "kotlin.coroutines.EmptyCoroutineContext" to "coroutine context hierarchy — type-only stand-in (no own behavior); coroutines example",
    "kotlin.coroutines.intrinsics.CoroutineSingletons" to "coroutine runtime model — coroutines example",
    "kotlin.coroutines.intrinsics.IntrinsicsKt" to "coroutine runtime model — coroutines example",
    "kotlin.coroutines.jvm.internal.BaseContinuationImpl" to "coroutine runtime model — coroutines example",
    "kotlin.coroutines.jvm.internal.Boxing" to "coroutine runtime model — coroutines example",
    "kotlin.coroutines.jvm.internal.ContinuationImpl" to "coroutine runtime model — coroutines example",
    "kotlin.coroutines.jvm.internal.SuspendLambda" to "coroutine runtime model — coroutines example",
    "kotlin.coroutines.jvm.internal.DebugMetadata" to "coroutine runtime plumbing — type-only stand-in (synthetic invokeSuspend annotation, no behavior); bundled so the continuation resolves single-source; coroutines example",
    "kotlin.coroutines.jvm.internal.SpillingKt" to "coroutine runtime model (nullOutSpilledVariable = identity, debug-off) — bundled so the continuation resolves single-source; coroutines example",
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
    "kotlin.text.StringCharIterator" to "concrete CharIterator backing for CharSequence.iterator() — " +
        "a by-index walk over a String; exercised by KotlinStringsLaws.as_sequence_iterable_iterator_concrete",
)

/**
 * The classes the PER-MEMBER auditing gate enforces: a real JDK class loads side by side with the
 * model and its full public/protected surface is enumerated and required to be modeled, declared, or
 * tail-waived. These are the concrete java.* models — exactly the recurring-disease cases where a
 * missing member becomes a silent nondet stub on the analysis path.
 *
 * <p>The Kotlin facade containers (`*Kt`), value classes, and the `Intrinsics` runtime helper ARE now
 * enforced here too. Their "real target" is the corresponding class on the kotlin-stdlib jar on this
 * (conformance) module's classpath: reflecting it gives the real member list, the modeled subset carries
 * a method-level [BmcModelConforms], and the (large) exotic remainder is absorbed by a class-level
 * [BmcModelTail] — the same machinery as the java.* models. Two facade-specific conventions:
 *   - **Facade dedup:** facades like `CollectionsKt`/`SetsKt`/`MapsKt` return bmc4j's bounded `java.util`
 *     collection models (already per-member-audited on the JDK side). The gate enumerates only the
 *     Kotlin-VISIBLE surface (the `*Kt` class's own members), so those java.util members are never
 *     double-counted here.
 *   - **Mangled value-class ABI** (`kotlin.time.Duration`): the real members carry kotlinc-mangled ABI
 *     names (`plus-LRDsOJo`, `getInWholeSeconds-impl`). The model is authored with legal Java placeholder
 *     names that the bmc-kotlin-models build rewrites to those exact dashed names (carrying the
 *     [BmcModelConforms] along), so a modeled member keys against its real twin by the mangled name with
 *     no special casing in the gate.
 *
 * <p>Open-universe extension facades whose "real target" has no well-defined member surface, enums
 * (`values()`/`valueOf()`), pure marker interfaces, and coroutine plumbing stay OUT of this set: they
 * remain covered at CLASS level (COVERED/COVERED_NO_OWN_SURFACE/WAIVED) and are checked by
 * [CoverageGateTest]; the per-member gate still audits any annotations they DO carry (dangling-declaration
 * + implemented-but-unannotated checks) but does not demand whole-surface enumeration.
 */
val PER_MEMBER_ENFORCED: Set<String> = setOf(
    "java.util.ArrayList", "java.util.LinkedList", "java.util.HashMap", "java.util.LinkedHashMap",
    "java.util.TreeMap", "java.util.HashSet", "java.util.LinkedHashSet", "java.util.TreeSet",
    "java.util.EnumMap", "java.util.EnumSet", "java.util.Objects",
    "java.util.Hashtable", "java.util.Properties",
    "java.util.ArrayDeque", "java.util.PriorityQueue",
    "java.util.Optional",
    "java.util.OptionalInt", "java.util.OptionalLong", "java.util.OptionalDouble", "java.util.Arrays",
    "java.util.Collections",
    "java.util.IntSummaryStatistics", "java.util.LongSummaryStatistics", "java.util.DoubleSummaryStatistics",
    "java.math.BigInteger", "java.math.BigDecimal",
    "java.time.Instant", "java.time.Duration", "java.time.LocalDate",
    "java.time.LocalTime", "java.time.LocalDateTime", "java.time.Period",
    // ZoneOffset: a concrete total-seconds value class (its abstract ZoneId base is WAIVED, the enums are
    // class-level COVERED only).
    "java.time.ZoneOffset",
    // ValueRange: a concrete four-long range value class. ChronoField/ChronoUnit are enums (class-level
    // COVERED only — values()/valueOf() + the delegating plumbing keep them out of per-member enforcement,
    // exactly like the DayOfWeek/Month enums).
    "java.time.temporal.ValueRange",
    "java.util.Random",
    "java.util.concurrent.atomic.AtomicInteger", "java.util.concurrent.atomic.AtomicLong",
    "java.util.concurrent.atomic.AtomicBoolean", "java.util.concurrent.atomic.AtomicReference",
    "java.util.concurrent.CompletableFuture", "java.util.concurrent.ConcurrentHashMap",
    "java.util.concurrent.CopyOnWriteArrayList",
    "java.util.concurrent.CountDownLatch", "java.util.concurrent.Semaphore",
    "java.util.concurrent.ArrayBlockingQueue", "java.util.concurrent.LinkedBlockingQueue",
    "java.util.concurrent.Executors",
    "java.util.stream.Stream", "java.util.stream.IntStream", "java.util.stream.Collectors",
    "java.util.stream.LongStream", "java.util.stream.DoubleStream",
    // Kotlin models — facades, value classes, and the Intrinsics null-safety helper. The real target is
    // the same-named class on the kotlin-stdlib jar on this module's classpath; see the doc above for the
    // facade-dedup and mangled-ABI conventions.
    "kotlin.collections.CollectionsKt", "kotlin.collections.SetsKt", "kotlin.collections.MapsKt",
    "kotlin.text.StringsKt",
    // ArraysKt absorbs its ~1300-member array-extension surface with a class-level @BmcModelTail; MathKt
    // and CharsKt enumerate their (small) non-inline JVM residue per member (the common forms are
    // @InlineOnly → no JVM member, so never on the enumerated surface).
    "kotlin.collections.ArraysKt", "kotlin.math.MathKt", "kotlin.text.CharsKt",
    "kotlin.collections.IndexedValue",
    "kotlin.sequences.SequencesKt",
    "kotlin.Pair", "kotlin.Triple", "kotlin.TuplesKt",
    "kotlin.ranges.RangesKt", "kotlin.comparisons.ComparisonsKt",
    "kotlin.enums.EnumEntriesKt",
    "kotlin.random.Random", "kotlin.random.RandomKt",
    "kotlin.time.Duration", "kotlin.time.DurationKt",
    // kotlin.Result value class — mangled -impl ABI; real target is kotlin-stdlib's kotlin.Result.
    "kotlin.Result",
    "kotlin.jvm.internal.Intrinsics",
)
