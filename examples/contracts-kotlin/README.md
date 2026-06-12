<!-- bmc:metadata
proofs: 24
proof-execution: 320s summed across the module (JBMC time, MiniSat; approximate). Proofs run in
  parallel, so wall-clock is far lower — this number is for spotting slow concepts, not timing the build.
-->

# Contracts (Kotlin)

Method contracts (`@Requires`/`@Ensures`) declared **test-side from Kotlin** with `@BmcContractsFor`,
so production code carries no bmc references. The Kotlin counterpart of [`examples/contracts`](../contracts).

`bmc-contracts` ships a native **KSP** `SymbolProcessor` for the Kotlin path (KSP replaces the
deprecated kapt). The `org.bmc4j` plugin wires it for you: apply the Kotlin plugin and the bmc4j
plugin, and the plugin applies KSP, adds `bmc-contracts` to `kspTest`, and sets
`javaParameters = true` so predicate parameter names survive into bytecode. No KSP block to write:

```kotlin
plugins {
    kotlin("jvm")
    id("org.bmc4j") // applies KSP + wires kspTest(bmc-contracts) + javaParameters
}
```

```kotlin
// src/main — plain Kotlin, no bmc references
object Triangles {
    @JvmStatic fun triangle(n: Int): Int { var s = 0; for (i in 1..n) s += i; return s }
}

// src/test — the contract; @JvmStatic predicates live in the companion
@BmcContractsFor(Triangles::class)
interface TriangleContract {
    @Requires("bounded") @Ensures("nonNeg") fun triangle(n: Int): Int
    companion object {
        @JvmStatic fun bounded(n: Int): Boolean = n in 0..8
        @JvmStatic fun nonNeg(result: Int, n: Int): Boolean = result >= 0
    }
}
```

```
./gradlew :examples:contracts-kotlin:test
./gradlew :examples:contracts-kotlin:test --tests "proofs.instance.*"
```

## Kotlin shapes — what binds, and what's rejected loudly

A contract mirror binds to a method on the `@BmcContractsFor` class by signature. From Kotlin:

| Shape | Status |
| --- | --- |
| `object` / `companion` method with `@JvmStatic` (static target) | works — see `basics` |
| contract host is an **`object`**, predicates are **plain member `fun`s** (no companion / `@JvmStatic`) | works — see `objecthost` (predicates invoked on the singleton `Contract.INSTANCE`) |
| pure instance method (receiver threaded as `self`) | works — see `instance` |
| method with **default parameters** (real + `$default` synthetic) | works — see `defaults` |
| **`suspend`** function (value-returning) | works — see `suspendcontracts` (the `Continuation` is hidden, the declared result recovered, the body driven to completion) |
| bare **top-level** `fun` | not contractable — its file-facade class (`FooKt`) is unnameable from Kotlin (`FooKt::class` is unexpressible). Put it in an `object`/`companion` with `@JvmStatic` (see `basics`). |
| **value/inline-class** parameter or return | rejected loudly — its JVM name is mangled and can't be contracted, so the processor errors naming the value/inline-class cause. Unwrap the value class at the boundary. |
| **`suspend`** function returning a `Flow` (or other stream) | rejected loudly — a contract describes one completed result, not a stream of emissions. |
| **`suspend`** function with an unrecoverable declared result (raw `Continuation`, type-variable result) | rejected loudly — the declared type the predicates bind can't be recovered. |

A silent failure to bind is the failure mode the processor refuses to allow: a `@BmcContractsFor` type
that binds zero contracts is a hard error, not a warning.

## `basics` — the basic shape (`object` + `@JvmStatic`)

`Triangles.triangle(n)` is a loop, costly to inline. With a contract, a caller at `unwind = 2` reuses
`@Ensures result >= 0` instead of unrolling and passes; the identical `TrianglesNaive` (no contract)
must inline and overruns the bound — **UNKNOWN** ("bound too small": truncated exploration is
incompleteness, not a counterexample). The function lives in an `object` with `@JvmStatic` because a
bare top-level `fun` compiles into a facade class Kotlin can't name. *(1 pass + 1 undecided-on-purpose;
plus 1 green enforce proof.)*

## `objecthost` — predicates as plain members of an `object` (no companion / `@JvmStatic`)

The basic shape's predicates have to live in a `companion object` with `@JvmStatic` per predicate, so
they compile to the `static boolean` methods the generated stub/enforce call — ceremony for what should
be a plain function. The processor now also accepts the contract host being a plain Kotlin **`object`**
whose predicates are **ordinary member `fun`s**:

```kotlin
@BmcContractsFor(Squares::class)
object SquaresContract {
    @Requires("bounded") @Ensures("nonNeg") fun pyramid(n: Int): Int = error("mirror")
    fun bounded(n: Int): Boolean = n in 0..8        // plain member — no companion, no @JvmStatic
    fun nonNeg(result: Int, n: Int): Boolean = result >= 0
}
```

The mirror method carries a throwaway `error("mirror")` body — never used; only its signature (which
binds to `Squares.pyramid`) and `@Requires`/`@Ensures` matter, exactly like the abstract interface
mirror. The processor invokes the object's predicates on the singleton (`SquaresContract.INSTANCE.bounded(n)`
in the generated Java) instead of statically; a pure boolean over its arguments reading no object state
is analyzed by JBMC identically to a static one (and the purity audit certifies the singleton read via
its `static final INSTANCE` field), so `enforce__pyramid` discharges **IDENTICALLY to the
companion/static form**. `bogus` ships a deliberately-**false** object-hosted contract pinned
`@ExpectEnforce(REFUTED)` — it publishes no redirect and passes by refutation, so annotating an
object-hosted contract is no more "asserting" it than the static form. The companion/static form stays
available (see `basics`) — this is additive. *(2 caller proofs: 1 pass + 1 undecided-on-purpose; plus 1
green + 1 refuted enforce proof.)*

## `instance` — pure instance methods (receiver as `self`)

`Account.project(amount)` is a pure instance method (reads `this.balance`, mutates nothing); its
contract threads the receiver into the predicates as `self`, so `@Ensures result >= self.balance` is a
postcondition over the receiver's field. A caller at `unwind = 2` reuses the summary — the
`invokevirtual` is redirected to a static stub with the receiver prepended — while the no-contract
`AccountNaive` overruns the bound (**UNKNOWN**). The auto-generated `enforce__project` discharges the
contract with a **symbolic receiver** (`self` nondet, so `@Requires` must bound `self.balance` or
`balance + amount` overflows). `projectAgain` ships a deliberately-**false** instance contract pinned
`@ExpectEnforce(REFUTED)` — it publishes no redirect and passes by refutation. *(2 caller proofs: 1
pass + 1 undecided-on-purpose; plus 1 green + 1 refuted enforce proof.)*

## `defaults` — Kotlin default parameters

`Discount.price(qty, rebate = 0)` compiles to two JVM methods: the real `price(qty, rebate)` and a
synthetic `price$default(...)` that fills the default and calls the real one. The mirror binds to the
real method; both a caller that passes the argument and one that omits it pass at `unwind = 2` — the
redirect summarizes the contracted call on either path, including the `$default` wrapper's internal
call. The processor needs no special knowledge of default parameters. *(2 pass; plus 1 green enforce
proof.)*

## `soundness` — the guard (annotate ≠ proven)

One contract type mixes an honest mirror and a deliberately false one. `absDelta`'s `@Ensures result
>= 0` is true, so `enforce__absDelta` VERIFIES and publishes a redirect. `delta`'s is a lie (false when
`a < b`), pinned `@ExpectEnforce(REFUTED)`: `enforce__delta` passes BY refutation (counterexample `a =
0, b = 1`) and publishes no redirect, so nothing reuses the false summary. There's no hand-written
proof — the auto-generated enforce proofs *are* the tests. *(1 refuted-on-purpose + 1 pass enforce
proof.)*

## `purity` — contracts must be pure (and that's audited)

The Kotlin soundness story: `Ledger.record(amount)` returns the running total *and* mutates the
receiver's `total` field — a `this`-mutation, the most common instance-method impurity. A contract
summarizes only the return value, so a redirected caller would silently drop the mutation, yet the
enforce-proof (which checks `@Ensures`, not purity) would pass. bmc4j's **purity audit** rejects the
contract before any proof can reuse it, failing the build with a `ContractPurityError` that names the
`PUTFIELD` on the receiver — an unconditional error (no `@ExpectEnforce` can bless an impure target).
The contract's enforce-proof is therefore excluded from the suite (removing that exclusion in
`build.gradle.kts` is itself the regression check); `PurityAuditDemoTest` documents, in plain Kotlin,
the caller-observable effect a stub would erase. *(1 plain test; the impure enforce-proof is rejected,
not run.)*

## `suspendcontracts` — contracts on `suspend` functions

"Most Kotlin is written as `suspend`", so contracts cover suspend targets. A `suspend fun f(n): Int` is
compiled to `Object f(int, Continuation)` over a state machine; bmc4j binds it under the same
**immediate-dispatch idealization** the coroutine proofs use — a suspend call completes linearly in one
call. The processor hides the trailing `Continuation` from the predicates and recovers the declared
result (`Int`) from it, so `@Requires`/`@Ensures` bind `f(n): Int` exactly as written. `Calcs.stepTo` is
a suspend loop (a per-iteration suspension point), costly to inline: a caller at `unwind = 2` reuses the
contract `@Ensures result == n` whether the **caller** is itself `suspend` (called from a `runBlocking`
proof body) or a non-suspend caller driving the call through `runBlocking { }` — both redirect the same
lowered `(n, Continuation)` call site. The no-contract `CalcsNaive` overruns the bound (**UNKNOWN**). The
auto-generated `enforce__stepTo` drives the real body to completion and discharges `@Ensures`;
`stepBuggy` ships a deliberately-**false** suspend contract pinned `@ExpectEnforce(REFUTED)` (a
post-suspension off-by-one), so it publishes no redirect and passes by refutation.

The purity audit applies unchanged: it allows the benign per-call coroutine plumbing every suspend body
contains (writes to its own fresh state-machine fields, the `COROUTINE_SUSPENDED` sentinel read) but
still rejects a real `this`-mutation — `Accumulator.add` is an **impure** suspend method whose contract
the audit rejects with a `ContractPurityError` naming the receiver `PUTFIELD` (its enforce-proof is
excluded, exactly like `purity`'s; `SuspendPurityAuditDemoTest` documents it). *(3 caller proofs: 2 pass
+ 1 undecided-on-purpose; plus 1 green + 1 refuted enforce proof; plus 1 plain rejection-demo test.)*
