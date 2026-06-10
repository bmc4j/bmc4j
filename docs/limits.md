# Known limits

These are inherent to bounded model checking or to the current engine — listed in
full, because a proof tool that hides its limits is worse than no proof tool.

A word on the difference between a **boundary** and a **scaling cost**. Some things below
are genuinely *out of scope* — irrational/transcendental floating-point math (`Math.sin`/`cos`/`log`, and even `Math.floor` — see the floating-point bullet), time zones/DST,
thread-interleaving concurrency, residual `invokedynamic`. Those are honest hard edges and
they're called out as such. But "this proof is slow / runs out of memory" is **not** a
boundary — it's a scaling cost with a [toolbox](performance.md) of levers (range reduction,
domain splitting, contracts, parallelism, sharding, caching). Where a limit
below is really a scaling cost, it points at the lever.

- **Bounded, not unbounded.** Loops/recursion are unwound to `unwind`. Proofs pass
  `--unwinding-assertions` by default, so an insufficient bound is reported rather
  than silently trusted - as **UNKNOWN** (incompleteness: exploration was truncated,
  nothing was proven wrong), never as a refutation. This is a *property* of BMC, not a
  dead end: when a recursive or deeply-nested callee is what's pushing the bound, a
  `@Requires`/`@Ensures` [contract](contracts.md) summarizes it (induction over the call
  boundary) so callers don't re-unwind it — see
  [performance → contracts](performance.md#5-contracts--for-recursion--unbounded-depth).
- **Three-way verdict — UNKNOWN is a first-class outcome, not a pass.** A proof is
  **verified**, **refuted** (with a counterexample), or **UNKNOWN** — undecided within
  budget: it timed out (see [configuration → Timeout](configuration.md#timeout)), the
  solver gave up or crashed, or the engine produced output bmc4j couldn't parse. UNKNOWN
  **fails** the test (soundness: the absence of a verdict is not a proof), but with a
  deliberately distinct message — **no counterexample** (nothing was proven wrong) plus
  guidance to make it decidable, and a distinct exception type, `BmcUndecidedError`. So a
  resource exhaustion in CI is never mistaken for "your code is wrong" — it points you at
  the [performance toolbox](performance.md) (tighten the range, `domainSplit` an
  interval-bound proof, contract a heavy callee, raise `unwind`/`timeoutSeconds`). A *slow*
  proof is a tunable proof, not a boundary. See [`examples/fundamentals-java`](../examples/fundamentals-java) `timeout`.
- **Vacuous proofs are caught, not passed.** A proof whose `assume`s are jointly
  *unsatisfiable* checks nothing — it "verifies" over an empty input domain
  (`assume(x > 0); assume(x < 0)`, or a `Bmc.anyString(1)` constrained to a 2-char
  literal). bmc4j injects a **reachability marker** at every proof's normal exit and
  fails such a proof with a dedicated verdict — *"assumptions are unsatisfiable - this
  proof checks nothing"* — instead of showing green. The check is sound for early
  returns and expected-exception paths: a path pruned by `Bmc.assumeUnreachable()` in a
  `catch` is *not* false-flagged as long as some normal exit stays reachable. It also
  guards **contract enforce-proofs**, where an empty `@Requires` would otherwise bless an
  unchecked `@Ensures` and weaken every caller (see [`examples/fundamentals-java`](../examples/fundamentals-java)
  `vacuity` and [`examples/contracts`](../examples/contracts) `vacuity`). One engine run, no extra cost.
- **JDK modeling is partial — but stubs are now detected, not silent.** JBMC's
  bundled models are tiny (boxed primitives, `Math`, `String`, exceptions), and it
  *stubs* anything else to nondeterministic values — which **was** silently unsound. It
  no longer is: bmc4j harvests every nondet stub the analyzed slice reached and surfaces
  it — a footnote on a green proof by default, an UNKNOWN verdict under `strictStubs`, a
  loud warning for a stub from your own code, acknowledged away with `allowStubs`, and
  ranked across the suite by `bmcStubReport` (see [configuration → Nondet stubs](configuration.md#nondet-stubs)).
  So a green verdict resting on a havoc'd stand-in is now visible rather than a confident
  lie. On top of detection, bmc4j ships clean **bounded models** in `bmc-models` for the common
  gaps: `List`/`ArrayList`/`LinkedList`, `Map`/`HashMap`/`LinkedHashMap`/`TreeMap`,
  `Set`/`HashSet`/`LinkedHashSet`, `Optional`, the `of(...)` factories, `Stream`/
  `IntStream` (eager, bounded — `filter`/`map`/`reduce`/`sum`/`collect(toList/toSet)`/…),
  and `BigInteger`/`BigDecimal` — all array/long-backed and sound (`BigDecimal` is exact
  decimal, *not* `double`-backed; see [`examples/stdlib`](../examples/stdlib)); plus `java.time`
  `Instant`/`Duration`/`LocalDate` (epoch primitives, no zones/DST). **Kotlin too:**
  `listOf`/`mutableListOf`/`setOf`/`mutableSetOf`/`mapOf` and the collection
  extensions (`map`/`filter`/`fold`/`forEach`/`sum`/`first`). Still **stubbed
  (unsound without acknowledgment)**: advanced `Collectors` (`groupingBy`/`joining`/`toMap`),
  `LongStream`/`DoubleStream`, collection copy-constructors. Coverage extends
  additively — add a model to `bmc-models`.
- **Kotlin works**, including **null-safety** (`!!`/`?.`/`?:`) — proofs can be written
  in Kotlin (see [`examples/fundamentals-kotlin`](../examples/fundamentals-kotlin)). The runtime bundles a
  clean `kotlin.jvm.internal.Intrinsics` model and prepends it to the analysis
  classpath so the null intrinsics don't trip JBMC.
- **`invokedynamic` is the one fault line** — JBMC links indy call sites to an
  unconstrained result, so anything desugared to indy is at risk. A construct sweep
  found this is the *only* one: all control flow, numerics, enums, sealed types,
  data/value classes (incl. `init {}`) verify soundly. bmc4j desugars the three common
  offenders in its own layer (no engine fork): **string concatenation**
  (`StringConcatFactory`, from `+` and Kotlin templates) → sound `StringBuilder` form;
  **record `equals`/`hashCode`/`toString`** (`ObjectMethods`) → a field-by-field
  comparison, a deterministic `31*r + componentHash` fold, and the canonical
  `"Name[c1=v1, ...]"` builder respectively; and **lambdas / method references**
  (`LambdaMetafactory`) → a generated class implementing the functional interface.
  Pattern switches are desugared for **both** bootstraps — `typeSwitch` (mixed
  type/constant/guarded labels) and `enumSwitch` (an enum subject with e.g. a
  `case null` arm) — to identity/`instanceof`/sound-equals chains, so a symbolic
  subject's selected branch is provably tied to its real value (see
  `proofs.patternswitch` in [`examples/language-java`](../examples/language-java)).
  For records, `equals` and `hashCode` are sound for any component types; the
  guaranteed `hashCode` property proven is *consistency* (equal records hash equal,
  it's a pure function of the components — the JDK does not fix the exact value, so no
  magic constant is asserted). `toString` is desugared+sound only when every component
  renders soundly (primitive or `String`); a record with another reference component
  keeps its original `toString` indy.
- **Residual `invokedynamic` is visibly undecided, never silently trusted.** Whatever
  the desugar passes deliberately leave (a record `toString` with a non-String
  reference component, an unrecognised switch label shape, or any future bootstrap)
  is surfaced through the nondet-stub channel: the site is rewritten to a bodiless
  `ResidualInvokedynamic.<indyName>__<bootstrapOwner>` marker, so a green proof that
  reaches it carries a stub footnote (and `strictStubs` escalates it), and a
  **refutation whose slice includes the havoc'd marker is demoted to a named
  UNKNOWN** — the "counterexample" may be an artifact of the havoc, and REFUTED is
  reserved for real counterexamples. Pin a proof that deliberately exercises such a
  site with `expect = UNKNOWN`. See `proofs.records.ResidualToStringProofs` in
  [`examples/language-java`](../examples/language-java).
- **Strings: content ops and concatenation are made sound in our layer.** JBMC's own
  `String.equals`/`startsWith`/`contains` are unsound; bmc4j rewrites those call sites
  to sound `length`+`charAt` stand-ins (and Kotlin `==`/string `when`, which lower to
  `Intrinsics.areEqual`, compare strings the same sound way), and desugars
  concatenation — both `String`+`String` and primitive interpolation (`"x" + anInt`,
  Kotlin `"x$n"`), by routing `int`/`long` through JBMC's sound `Integer`/`Long.toString`
  (see [`examples/stdlib`](../examples/stdlib) strings, [`examples/language-kotlin`](../examples/language-kotlin) `when`). Bound symbolic strings with
  `Bmc.anyString(n)`. Residual: `Integer.toString(0)` has a JBMC length quirk, so an
  *exact-length* assertion over `"x" + n` where `n` can be `0` may spuriously fail
  (a visible over-approximation, not silent); `float`/`double` formatting is unmodeled.
- **Floating point is supported, with caveats — it is a *scaling cost*, not a boundary, for
  the parts that are sound.** `anyFloat()`/`anyDouble()` introduce genuine symbolic IEEE-754
  inputs (full domain incl. `NaN`/±∞; the bounded `anyFloat(lo,hi)`/`anyDouble(lo,hi)` forms
  exclude `NaN`), and JBMC reasons soundly about float/double **arithmetic** (`+`/`-`/`*`/`/`),
  **ordered comparisons**, and the algebraic `Math` functions `abs`/`sqrt`/`pow`/`min`/`max`
  (all verified over symbolic inputs). The cost is performance: IEEE-754 in a SAT solver is slow
  by construction, so prefer integer models where you can ([performance](performance.md)) — but a
  slow FP proof is tunable, not a dead end. What is **genuinely out of scope** is the
  *irrational/transcendental* `Math` library on **symbolic** inputs: `sin`/`cos`/`tan`/`log`/`exp`
  and even `floor` are modeled by the underlying engine but **imprecisely**, so a symbolic-input
  property over them can spuriously **REFUTE** (e.g. the engine admits `sin(0) == 1`, or
  `floor(0)` a tiny denormal) — these are reliable only over **concrete** inputs. The Kotlin
  `kotlin.math` facade follows the same line: the inline `Math`-delegating functions are sound,
  while `acosh`/`asinh`/`atanh` (no `java.lang.Math` primitive) are `@BmcUnmodelable` and fail
  loud if reached. `double`/`float` **formatting** (`Double.toString`, `"" + aDouble`) is
  separately unsound — see [coverage](coverage.md).
- **Lambdas & method references work** (Java and Kotlin) — they're desugared to real
  classes, so you can pass a lambda into a proof or apply it to symbolic inputs (see
  [`examples/language-java`](../examples/language-java)); stream/collection pipelines
  (`list.stream().map().sum()`, Kotlin `list.map{}.sum()`) work too via the collection
  models. (Kotlin `inline fun`s never produced indy in the first place — which is how
  `assumeValid` stays analysable.)
- **Concurrency: use [Lincheck](https://github.com/JetBrains/lincheck), not this.**
  `@BmcProof` answers *"is my logic sound?"* — symbolic, all-inputs proofs of
  sequential behavior (including Kotlin coroutine *logic* under an **immediate-dispatch
  idealization**: a `suspend` call completes linearly in one call, every nested suspension point
  resolving synchronously — e.g. `runBlocking { … }`, and method contracts on `suspend` functions
  too; see [`examples/kotlin-coroutines-and-lincheck`](../examples/kotlin-coroutines-and-lincheck) and
  [contracts](contracts.md)). It is **not** the right tool
  for *"is my concurrent code correct & safe?"*. The flip side of that idealization is that
  real-world suspension-point *interleaving* — concurrent coroutines, dispatcher hops, cancellation
  timing — is out of scope (a suspend contract's `@Requires` holds at entry and `@Ensures` at
  completion, not across an interleaving). Note the distinction: code that *uses*
  concurrency **constructs** is still analysable for its logic — `AtomicInteger`/`Long`/
  `Boolean`/`Reference`, `CompletableFuture`, `ConcurrentHashMap`/`CopyOnWriteArrayList`
  are modeled with their **single-threaded semantics** (atomic = mutable holder, future =
  ready value, concurrent map = our map), and `synchronized`/`ReentrantLock` already
  analyse fine. What bmc4j does *not* do is verify the concurrency itself — interleaving
  search, races, linearizability, and lock-freedom are all out of scope. For real concurrency
  testing — races, linearizability, lock-freedom, concurrent coroutines — reach for **Lincheck**:
  it's JVM-native, runs your actual bytecode, has a deterministic model-checking mode,
  and is maintained by JetBrains. The two are complementary: BMC for logic, Lincheck
  for concurrency — [`examples/kotlin-coroutines-and-lincheck`](../examples/kotlin-coroutines-and-lincheck) (the `lincheck`
  concept) puts both on the same class to show the split.
