# Coverage map

A single map of which JVM language constructs and standard-library APIs bmc4j can
verify soundly today, what's partial, what's stubbed (unsound without
acknowledgment — see the stub policy in [configuration](configuration.md)), and
what's deliberately out of scope. Compiled from the construct/JDK sweeps and the
collection/lambda/string modeling work; kept current as models land. How the
models themselves are kept honest is covered in
[model soundness](model-soundness.md).

### Legend
- ✅ **modeled / verified** — sound under BMC, backed by a sweep or example in this repo
- ⚠️ **partial** — works for the common case; specific sub-cases unsound/unsupported (see note)
- ❌ **not modeled** — JBMC stubs it to nondeterministic values → surfaced by stub detection; a candidate for a future model
- 🚫 **won't do** — deliberately out of scope (external world, or better served another way); not a defect

Reminder: everything is **bounded** (loops/collections unwind to `unwind`; collection/stream models have capacity 64).

---

## Java — language constructs

| Construct | Status | Note |
|---|---|---|
| Primitive arithmetic, `int`/`long`/bit ops | ✅ | |
| `if`/`for`/`while`/`do`, labels, `break`/`continue` | ✅ | |
| Enhanced-for (arrays & modeled collections) | ✅ | |
| `switch` — table/lookup/`String`/`enum` | ✅ | |
| Ternary `?:` | ✅ | |
| `try`/`catch`/`finally`, `throw`, uncaught-exception checks | ✅ | NPE/AIOOBE/div-by-zero/CCE auto-checked |
| `instanceof` + cast | ✅ | |
| Autoboxing / unboxing | ✅ | |
| Arrays (1-D, multi-D) | ✅ | |
| Varargs | ✅ | |
| Generics (erasure, wildcards) | ✅ | |
| Interface dispatch / polymorphism | ✅ | |
| Recursion | ✅ | + [contracts](contracts.md) give induction |
| `record` — components/accessors | ✅ | |
| `record` — `equals` | ✅ | desugared from `ObjectMethods` indy |
| `record` — `hashCode` | ✅ | desugared from `ObjectMethods` indy → deterministic `31*r+componentHash` fold (String components via sound `BmcStrings.hashCode`). Proven property: *consistency* — equal records hash equal, pure function of components; JDK doesn't fix the exact value so no magic constant asserted |
| `record` — `toString` | ⚠️ | desugared+sound for records whose components all render soundly (primitive or `String`) → canonical `"Name[c=v, …]"` via the sound `StringBuilder`/`Integer.toString` path; a record with any other reference component keeps its original `ObjectMethods` indy (left as-is, not desugared unsoundly). `Integer.toString(0)` length quirk applies to exact-length-with-0 claims |
| Lambdas / method refs (static/instance/ctor) | ✅ | desugared from `LambdaMetafactory` ([`examples/language-java`](../examples/language-java)) |
| `try`-with-resources | ✅ | confirmed (incl. a lambda `AutoCloseable`) |
| Nested / inner / anonymous classes | ✅ | confirmed |
| Static initializers | ✅ | confirmed |
| `sealed` classes/interfaces (Java) | ✅ | hierarchy is plain classes |
| Pattern matching for `switch` (`SwitchBootstraps.typeSwitch`) | ✅ | desugared in-layer (`SwitchBytecode`) to an analyzable instanceof/equals chain — sound over a symbolic-typed subject; handles type/constant/enum labels, null, restart-index (guards) |
| Record deconstruction patterns | ✅ | `instanceof Point(int x, int y)` is sound (plain type-check + accessors, no indy); in a `switch` it rides the sound `typeSwitch` desugar too |
| Text blocks | ✅ | plain compile-time String constants; confirmed via `proofs.textblocks` (whitespace stripping is genuinely modeled) |
| Annotations | n/a | Annotations are erased metadata — no control/data-flow effect, nothing to verify. The relevant axis is the *inverse*: bmc4j **synthesizes logic from** a few via source processing — jakarta `@Min/@Max/@NotNull/@Size` → `assume` preconditions ([`examples/integrations`](../examples/integrations)), and its own `@BmcProof`/`@Requires`/`@Ensures`/`@BmcContractsFor` → proof/contract codegen. Custom/other annotations: nothing to do (correctly). |

## Kotlin — language constructs

| Construct | Status | Note |
|---|---|---|
| `when` (subject / range / no-subject) | ✅ | [`examples/language-kotlin`](../examples/language-kotlin) |
| `sealed` class + exhaustive `when` | ✅ | |
| `data class` — `equals`/`copy`/`componentN`/destructuring | ✅ | |
| `value class` (`@JvmInline`) incl. `init { require(...) }` | ✅ | [`examples/language-kotlin`](../examples/language-kotlin); `assumeValid { }` reuses it |
| Null-safety (`!!`, `?.`, `?:`) | ✅ | clean `Intrinsics` model ([`examples/fundamentals-kotlin`](../examples/fundamentals-kotlin)) |
| `lateinit` | ✅ | unguarded pre-init read refutes; the `::x.isInitialized` guard and init-then-read verify (pinned in [`examples/fundamentals-kotlin`](../examples/fundamentals-kotlin)) |
| Symbolic non-null `@BmcProof` parameters | ✅ | the kotlinc `checkNotNullParameter` prologue + `@NotNull` annotation are relaxed to `assume(p != null)` in **proof methods only** — the proof ranges over what the Kotlin type system admits instead of refuting on an un-constructible `p = null`. Nullable (`T?`) parameters keep `null`; interior calls keep throwing; `bmc { kotlinNullableParams = true }` restores honest-JVM semantics ([`examples/fundamentals-kotlin`](../examples/fundamentals-kotlin)) |
| Smart casts | ✅ | |
| Ranges (`in`, `for (i in a..b)`) | ✅ | |
| Extension functions | ✅ | |
| Default / named arguments | ✅ | |
| `inline fun` (+ inlined lambdas) | ✅ | how `assumeValid` stays analysable |
| Lambdas / SAM / method refs | ✅ | desugared ([`examples/language-java`](../examples/language-java)) |
| Coroutines (`suspend`, `runBlocking`, `async`/`await`, `withContext`) | ✅ | clean bundled models, **sequential logic only** ([`examples/kotlin-coroutines-and-lincheck`](../examples/kotlin-coroutines-and-lincheck)) |
| String templates — `"a$str"` (String interp) | ✅ | via concat desugaring |
| String templates — `"a$int"` (primitive interp) | ⚠️ | routed via `Integer/Long.toString`; content + multi-digit length sound; `Integer.toString(0)` length quirk → exact-length-with-0 may spuriously fail |
| `object` / `companion object` | ✅ | confirmed |
| Operator overloading | ✅ | custom `operator fun plus` confirmed |
| Interface delegation (`by`) | ✅ | confirmed (delegated properties untested) |
| `reified` generics | ✅ | inlines `x is T`/`as? T` to a concrete `instanceof` at the call site; confirmed via `proofs.reified` |
| Sequences (`asSequence`, `sequenceOf`) | ✅ | eager bounded model — `sequenceOf`/`asSequence`/`map`/`filter`/`toList`/`sum`/`count` + `take`/`drop`/`distinct`/`flatMap`/`toSet` (facade) and inlined `fold`/`reduce`/`sumOf{}` (`proofs.kotlinsequences`, concrete + symbolic) |
| `Enum.entries` (1.9+) | ✅ | `kotlin.enums.EnumEntriesKt`/`EnumEntriesList` model — an enum's `<clinit>` builds `$ENTRIES` via `EnumEntriesKt.enumEntries($VALUES)`; modeled as bmc4j's bounded list over `values()`, so `entries.size`/`entries[i]`/`indexOf`/iteration are sound (`entries[i].ordinal == i`, declaration order). Model proofs in `proofs.kotlinenums`; example `proofs.enumentries`. Previously spuriously REFUTED ("no body for `java.util.List.size()`") |

## Strings

| API | Status | Note |
|---|---|---|
| `equals` / `startsWith` / `endsWith` / `contains` | ✅ | sound shims (`BmcStrings`, redirected by `StringBytecode`); conformance-pinned both directions (concrete true/false + symbolic reflexive/self/empty + a `charAt`-scan agreement for `equals`) in `proofs.strings.StringShimLaws` |
| `String` + `String` concatenation (`+`, templates) | ✅ | desugared `StringConcatFactory` indy |
| `equalsIgnoreCase` / `compareTo` / `isEmpty` | ✅ | native jbmc string ops, confirmed sound — true/false & lexicographic sign pinned concretely, reflexive symbolically (`proofs.strings.StringLaws`). No shim needed |
| `indexOf(String)` / `indexOf(String,from)` / `indexOf(char)` / `lastIndexOf(String)` / `lastIndexOf(char)` | ✅ | native (sound) — exact hit position + `-1` miss pinned (`proofs.strings.StringLaws`) |
| `toLowerCase` / `toUpperCase` | ✅ | native (sound) — length-preserving + idempotent (concrete + symbolic; ASCII case fold probed); `proofs.strings.StringLaws` |
| `concat(String)` | ✅ | native (sound) — result length additive, content/prefix pinned (concrete + symbolic); `proofs.strings.StringLaws` |
| `replace(CharSequence,CharSequence)` | ✅ | native (sound) — concrete substring replace + symbolic identity no-op (`proofs.strings.StringLaws`) |
| `String.valueOf(int)` | ⚠️ | native — routes via `Integer.toString`; multi-digit content + length sound; `Integer.toString(0)` length quirk for a `0` (`proofs.strings.StringLaws`) |
| `length` / `charAt` | ✅ | sound primitives the shims build on; `String.charAt` pinned in `proofs.strings.StringLaws` |
| `Character.isDigit/isLetter/isWhitespace` / `toUpperCase(char)` / `toLowerCase(char)` | ✅ | native (sound) — both directions concrete + ASCII-band symbolic laws (`proofs.strings.CharacterLaws`) |
| `Character.isLetterOrDigit(char)` | ❌ | UNSOUND in jbmc (unconstrained result), unlike `isLetter`/`isDigit` individually — compose `isLetter(c) \|\| isDigit(c)`; documented in `proofs.strings.CharacterLaws`. Conservatively over-refutes (no false green) |
| `StringBuilder.reverse/insert/deleteCharAt/length/charAt` | ✅ | native (sound) — exact results pinned (concrete + symbolic reverse); `proofs.strings.StringBuilderLaws` |
| `Bmc.anyString(min,max)` / `anyString(n,alphabet)` / `anyAsciiString(n)` | ✅ | charset/length-bounded symbolic strings — length bounds + per-char domain assumed over the sound `charAt` primitive, so later sound reads honour them (`proofs.strings.CharsetProofs`) |
| `"x" + anInt` / `+ aLong` (primitive→string) | ⚠️ | sound for content + multi-digit length (routed via `Integer/Long.toString`); `Integer.toString(0)` length quirk caveat |
| `"x" + aFloat` | ✅ | `StringBuilder.append(float)` is sound in jbmc (`1.5f`→`"1.5"`; `proofs.strings.StringBuilderAppendLaws`) |
| `"x" + aDouble` | ⚠️ | `StringBuilder.append(double)` is UNSOUND in jbmc (rendered result unconstrained; even `Double.toString` refutes — double formatting isn't modeled, unlike float). The desugar still emits `append(double)`, so the result string is unconstrained — conservatively SOUND (jbmc over-refutes, never a false green), just imprecise |
| `StringBuilder.append(...)` overloads | ✅/⚠️ | per-overload conformance pins in `proofs.strings.StringBuilderAppendLaws`: String/char/boolean/int/long/float/Object(String)/CharSequence(String) SOUND; `append(char[])` and `append(double)` UNSOUND (unconstrained), neither on the concat desugar's sound emission path (char[] routes via `append(Object)`) |
| `substring` / `replace(char,char)` | ✅ | native jbmc string ops, confirmed sound (concrete + symbolic, `proofs.strings`) |
| `trim` / `isBlank`-style blankness | ✅ | native (sound) — `trim().isEmpty()` agrees with a per-`charAt` blankness scan over every bounded string, concrete + symbolic both directions (`proofs.strings.StringLaws`). Backs the jakarta `@NotBlank` lowering; the `charAt`-loop formulation is pinned as the fallback |
| `split` / `chars()` / `format` / `repeat` / `strip` / `isBlank` | ❌ | `split` UNSOUND (regex-metachar delimiter → unconstrained array length; caught adversarially); `chars()` returns an unconstrained IntStream; `format` not modeled; `repeat(int)` UNSOUND (unconstrained result); the Java-11 `strip`/`isBlank` UNSOUND (unlike Java-1.0 `trim`, which IS sound — use `trim` for blankness). All conservatively over-refute (no false green); documented in `proofs.strings.StringLaws` |

## `java.*` standard library

> **Per-member audit.** The exact member-by-member coverage of each modeled JDK class — what's
> modeled, what's deliberately not (with reasons), and the loud exotic tail — is mechanically
> enforced by the audit annotations and **generated** into [model-coverage.md](model-coverage.md)
> (regenerate via `gradlew -p core :bmc-models-conformance:test --tests
> conformance.ModelCoverageDocsTest -Dbmc.regenerateDocs=true`). The rows below stay as a
> human-readable summary.

| API | Status | Note |
|---|---|---|
| Boxed primitives, `Math` | ✅ | bundled in JBMC `core-models`; the integer methods jbmc stubbed to nondet (`floorDiv`/`floorMod`/`addExact`/`subtractExact`/`multiplyExact`/`negateExact`/`incrementExact`/`decrementExact`/`toIntExact`) are soundly redirected to `BmcMath` via an in-layer bytecode pass (`MathBytecode`), overflow loud; `sqrt`/`pow`/trig pass through to the modeled core-models |
| `StringBuilder` / `StringBuffer` | ✅ | core-models |
| `List` / `ArrayList` / `LinkedList` | ✅ | bounded array-backed model ([`examples/stdlib`](../examples/stdlib)). Surface: `add`/`get`/`set`/`indexOf`/`contains`/`remove(int)`/`remove(Object)`/`clear`/`iterator`/`stream` (`remove(Object)` — the `Collection` overload, distinct from by-index — is modeled; differential + @BmcProof). List bulk ops `addAll`/`removeAll`/`retainAll`/`removeIf`/`forEach`/`toArray` over the backing array (differential + @BmcProof, lambdas devirtualize through `removeIf`/`forEach`; `addAll` past capacity 64 is loud out-of-bounds). `LinkedList` adds the full **Deque/Queue surface** — `addFirst`/`addLast`/`offer(First/Last)`/`push`/`getFirst`/`getLast`/`peek(First/Last)`/`element`/`removeFirst`/`removeLast`/`pop`/`poll(First/Last)`/`remove()`/`peek()` — over the same bounded array, JDK-exact empty-list split (get/remove/pop/element throw `NoSuchElementException`; peek/poll → null), insertion order, and inherited-List interplay |
| `Map` / `HashMap` / `LinkedHashMap` / `TreeMap` | ✅ | `put`/`get`/`remove`/`containsKey`/`containsValue`/`getOrDefault`/`putIfAbsent`/`keySet`/`values`/`entrySet` (`containsValue`/`putIfAbsent` modeled; differential + @BmcProof). Functional-arg ops `computeIfAbsent`/`computeIfPresent`/`compute`/`merge`/`forEach`/`replace(k,v)`/`replace(k,old,new)` on the HashMap model (differential + @BmcProof; lambdas devirtualize). The present-but-null / null-result-removal traps are pinned exactly: `computeIfAbsent` treats a null mapping as absent and a null result leaves the key absent; `compute`/`computeIfPresent` remove on a null result; `merge` removes on a null result. `ConcurrentHashMap` overrides reject null keys/values/functions (NPE) and never store null, like the JDK |
| `Set` / `HashSet` / `LinkedHashSet` | ✅ | the `List` bulk ops (`addAll`/`removeAll`/`retainAll`/`removeIf`/`forEach`/`toArray`) are **not** modeled on the Set models — using them hits a JBMC nondet stub (declared on the `List` model only, to keep the Set surface unchanged); a candidate for a future model |
| `Optional` | ✅ | + `orElseThrow(Supplier)`, `flatMap`, `ifPresentOrElse`, `or`, `stream` (0/1-element via the bounded `Stream` model — sound, no extra hole). `flatMap`/`or` reject a null Optional from the mapper/supplier (NPE), like the JDK |
| `List.of` / `Set.of` / `Map.of`, `Arrays.asList` | ✅ | |
| `Iterator`, for-each over collections | ✅ | |
| `Stream` / `IntStream` (filter/map/reduce/sum/count/anyMatch/toList/…) | ✅ | eager, bounded |
| `stream().collect(Collectors.toList/toSet)` | ✅ | bounded |
| `Collectors` `toMap`/`groupingBy`/`joining` | ✅ | fold into bounded HashMap/ArrayList; `toMap` dup-key throws like the JDK (`proofs.stream`). `joining()`/`joining(sep)` sound — `ListStream.collect` builds the result with an explicit `StringBuilder` (sound `append`/`toString`, no indy concat / regex); verified by `proofs.stream.CollectorsLaws` |
| `LongStream` | ✅ | mirrors IntStream (of/range/map/filter/sum/count + `mapToLong`). `DoubleStream` 🚫 by design (bmc4j avoids `double`; use exact types) |
| collection copy-constructors (`new ArrayList<>(c)`, `new HashMap<>(m)`) | ✅ | List/Set + Map (incl. `Linked*`/`TreeMap`); differential + @BmcProof |
| Map views `keySet`/`values`/`entrySet` (+ `Map.Entry`, `for (e : map.entrySet())`) | ✅ | bounded snapshots; differential + @BmcProof |
| `BigInteger` | ✅ | bounded model backed by `long`; the `String` constructor parses radix-10 (optional sign + digits), rejecting garbage with `NumberFormatException` and failing LOUDLY (ArithmeticException via `Math.*Exact`) past the long bound — never a silent wrap (differential both directions + @BmcProof round-trips). One documented edge: `BigInteger("-9223372036854775808")` (exactly `Long.MIN_VALUE`) fails loudly because the magnitude is accumulated as a positive long before negation — out of the documented bound, never silently wrong |
| `BigDecimal` | ✅ | exact: unscaled `long` + scale, `RoundingMode` divide/setScale ([`examples/stdlib`](../examples/stdlib)). `double` constructor 🚫 by design (use String/long). Past the ~18-digit `long` bound — arithmetic AND the `String`-ctor digit accumulation — fails LOUDLY (overflow guard), never a silent wrap |
| `java.time` `Instant`/`Duration`/`LocalDate`/`LocalTime`/`LocalDateTime` | ⚠️ | epoch/field primitives, no zones/DST. `LocalTime` = nano-of-day; `LocalDateTime` = epoch-day + nano-of-day (y/m/d conversion mirrors the JDK's proleptic-Gregorian algo, differential-verified across leap years). Day/time arithmetic (`plusDays`/`plusHours`/`plusMinutes`/`plusSeconds`) sound. Calendar-month arithmetic modeled: `LocalDate.plusMonths`/`plusYears` (+`minus*`) and `LocalDateTime.plusMonths`/`plusYears` (+`minus*`) apply the JDK's exact month-carry + day-of-month **clamp** (2024-01-31 +1mo = 2024-02-29; 2024-02-29 +1yr = 2025-02-28) — differential-verified bit-for-bit vs the JDK across month-ends/leap days/negatives, plus @BmcProof laws (12mo==1yr, clamp, round-trip). `LocalDate` exposes `getYear`/`getMonthValue`/`getDayOfMonth` |
| `java.time` `Period` | ⚠️ | (years, months, days) triple; `of`/`ofYears`/`ofMonths`/`ofWeeks`/`ofDays`, accessors, `plus*`/`minus*`/`negated`/`normalized`, `toTotalMonths` — all differential-verified vs the JDK (loud int overflow via `Math.*Exact`). `Period.between(LocalDate,LocalDate)` modeled (replicates `LocalDate.until`'s y/m/d decomposition exactly; differential-verified vs the JDK across month-ends/leap/negative/cross-boundary cases — differential-only on the proof axis because its body uses `Math.toIntExact`, unmodeled by JBMC) |
| `java.time` `ZonedDateTime`/formatters/zones | ❌ | need the IANA tz DB / text parsing — out of scope for a bounded model |
| `java.util.regex` `Pattern`/`Matcher` (`String.matches`) | 🚫 | regex engines are mature/well-proven and rarely the code under proof; faithful BMC modeling is large/low-value. Future *extension* (not engine modeling): an `assumeMatches(s, pattern)` / `anyStringMatching(pattern, n)` helper that *assumes* (or produces, for simple pattern classes) a conforming symbolic string — reuse the constraint, don't model the engine |
| `java.util.Random` | ✅ | core-models (nondet) |
| `java.io` / `java.nio` / `java.net` | 🚫 | external world (files/sockets) — outside what BMC can reason about; abstract behind a `src/bmcModel` stub if needed |
| `java.util.concurrent` **constructs** in sequential logic (`Atomic*`, `CompletableFuture`, `ConcurrentHashMap`, `CopyOnWriteArrayList`, `synchronized`/`ReentrantLock`) | ✅ | modeled with single-threaded semantics (atomic = holder, future = ready value, concurrent map = our map); locks analyse as-is |
| concurrency **verification** (races/interleavings/linearizability) | 🚫 | Lincheck's job, not BMC's (see [limits](limits.md)) |
| `j.u.c` advanced — `CountDownLatch`, `Semaphore`, `BlockingQueue` (`ArrayBlockingQueue`/`LinkedBlockingQueue`), executors (`Executors`/`ExecutorService`/`Future`) | ✅ | sequential semantics, differential + @BmcProof (`proofs.concurrent.ConcurrentLaws`). Latch = floored counter; semaphore = permit counter; blocking queues = array-backed FIFO (non-blocking surface sound; the no-arg `LinkedBlockingQueue()` default is UNBOUNDED like the JDK - logical capacity `Integer.MAX_VALUE`, `offer` never rejects - and holding more than the 64-slot model storage bound fails loud as out-of-bounds, never a silent false rejection); executors = immediate/same-thread (`submit` runs synchronously → completed `Future`). **Blocking ops are an assume-prune idealization, not throws:** `acquire` (assume a permit), `take` (assume non-empty), `put` (assume room), and `await` (assume `count==0`) each proceed assuming their blocking precondition holds and prune the would-block path — the standard sound BMC "the thread waits here until it can proceed" — so the *logic* through these constructs stays fully testable. `await` is **not** an unconditional no-op (that would be unsound); a proof that can ONLY block prunes to no feasible path and is flagged **vacuous** — the right outcome. Blocking ops use `CProver.assume` (proof axis only); the non-blocking surface (`tryAcquire`/`offer`/`poll`/…) stays JVM-runnable + differential-tested |
| `j.u.c` advanced — `Phaser`, `CyclicBarrier`, multi-party barriers, true blocking-on-full/empty | 🚫 | these are *about* thread interleavings/handoff, which a single-threaded model can't represent soundly — concurrency **verification** is Lincheck's job (row above) |

## Kotlin standard library

| API | Status | Note |
|---|---|---|
| `listOf` / `mutableListOf` (incl. varargs) | ✅ | `CollectionsKt` model |
| `setOf` / `mutableSetOf` | ✅ | `SetsKt` model |
| `mutableMapOf()` (empty) + `m[k]=v` / `m[k]` | ✅ | maps to `java.util` |
| `mapOf(... to ...)` / `mutableMapOf(...)` | ✅ | `MapsKt` + `Pair` models |
| Collection extensions `map`/`filter`/`fold`/`forEach`/`sum`/`first` | ✅ | `CollectionsKt` helpers + `Iterable`/`Collection` hierarchy |
| Other collection extensions (`groupBy`/`associate*`/`zip`/`maxOrNull`/`minOrNull`/`count`/`any`/`all`/`none`/`sorted`/`sortedBy`/`take`/`drop`/`distinct`/`toSet`/`toMutableList`/`flatMap`/`fold`/`reduce`/`sumOf{}`) | ✅ | modeled/inlined over the bounded collections; concrete + symbolic laws (`proofs.kotlincollections`). `distinct`/`toSet` use a bounded `LinkedHashSet` (first-occurrence order); `take`/`drop` throw on negative n |
| `kotlin.jvm.internal.Intrinsics` | ✅ | clean model |
| `kotlinx.coroutines.*` | ✅ | clean models |
| `kotlin.Pair` / `kotlin.Triple` (first/second/third/componentN) | ✅ | clean models + destructuring |
| Sequences (`asSequence`/`sequenceOf`) | ✅ | see the Kotlin language-constructs table (eager bounded model — core ops + `take`/`drop`/`distinct`/`flatMap`/`toSet`/`fold`/`reduce`/`sumOf{}`) |
| `kotlin.time.Duration` (+ `DurationKt`, `DurationUnit`) | ⚠️ | value-class model reproducing the real unit-discriminating bit-packed `Long` (nanos/millis ranges + saturation) and its erased ABI (`plus-LRDsOJo`/`getInWholeSeconds-impl`/…, produced by a build-time bytecode rename). Construction from `Int`/`Long` units, `+`/`-`/unary `-`, comparison, `inWhole*`, negatives, and the nanos/millis saturation boundary are **differential-verified vs the JVM `kotlin.time.Duration`** (`conformance.KotlinDurationConformanceTest`) + @BmcProof laws (`proofs.kotlintime`); example `proofs.durations`. **Holes** (unmodeled → JBMC nondet stubs): `toString`/`toIsoString`/`parse` (string formatting), the `Double` construction/arithmetic overloads (bmc4j avoids `double` — use the `Int`/`Long` unit extensions), `times`/`div`, components (`toComponents`), and `TimeSource`/`TimeMark` (wall-clock). `java.time.Duration` is the fuller-surface modeled alternative. Previously spuriously REFUTED ("no uncaught exception") |
