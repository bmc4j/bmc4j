<!-- bmc:metadata
proofs: 6
proof-execution: ~60s summed across the module (JBMC time, MiniSat; approximate). Proofs run in
  parallel, so wall-clock is far lower — this number is for spotting slow concepts, not timing the build.
-->

# Assumed output-contracts

`Bmc.assumeEvery` / `Bmc.assumeStable` let a proof **assume** an external, unanalyzable dependency
upholds an output property and prove on top of it — no model, no annotation, no string method name.
This is **assume-guarantee**: *"IF `findById` returns a valid user, THEN my service is correct."* One
typed call in the proof.

```java
Bmc.assumeEvery(repo::findById, u -> u == null || u.age() >= 0);          // output-only, fresh per call
Bmc.assumeEvery(repo::findById, (u, id) -> u == null || u.id() == id);    // args-aware (result + call args)
Bmc.assumeStable(env::bucketCount, n -> n == 8);                          // one fixed value for the whole run
```

bmc4j reads the referenced method **statically** from the method reference's `LambdaMetafactory`
bootstrap handle (it never executes the `invokedynamic`), shadows the target on the analysis classpath
with a constrained-nondet stub `R m(args){ R r = nondet(); assume(predicate(r [,args])); return r; }`,
and redirects every call site of the target to it — **including those in `<clinit>` and inside callees
the proof doesn't control**. That reach is the point: it constrains a call site a local `assume` can't
touch.

- `assumeEvery` is **fresh per call** (a sound over-approximation: every output satisfying the predicate),
  the right default for repositories / services / factories.
- `assumeStable` pins **one value for the whole run** (memoized), the right form for a deterministic query
  whose value seeds a `<clinit>` or a config constant.

**Soundness.** The micro-model is an *assumption* (constrained nondet via `assume`, never `assert`), so a
property proven on top of it holds for any real implementation that respects the predicate. A VERIFIED
reached this way is **flagged on the verdict** ("VERIFIED under assumed contract … — NOT unconditional").
An over-tight predicate that rules out every output surfaces as **VACUOUS**. The predicate must be
**pure** — certified by the same audit the annotation contracts use; an impure one is rejected with a
`ContractPurityError`.

```
./gradlew :examples:assume-contracts:test
```

## `repository` — assume-guarantee over an unanalyzable repository

`UserService` is correct only IF the repository upholds an output property; `assumeEvery(repo::findById,
…)` supplies it. `service_holds…` VERIFIES under the output-only assumption; **drop** the assumption and
the same property is **REFUTED** (the assumption is load-bearing). `args_aware…` constrains the output
**by** the call argument (`result.id == id`). `an_over_tight_predicate…` rules out every output ⇒
**VACUOUS**.

## `env` — `assumeStable` and the `<clinit>` case

A `Buckets.<clinit>` reads a deterministic, unanalyzable `Environment.bucketCount()` into a `static final`
array bound. `assumeStable(env::bucketCount, n -> n == 8)` pins it to one value for the whole run —
reaching the static initializer a local `assume` could never touch — so the array is sized 8 and the proof
VERIFIES. Without the pin the captured bound is symbolic and `count() == 8` is **REFUTED**.

## `purity` — the predicate must be pure

`ImpurePredicateDemo` declares a predicate that reads the wall clock (`System.nanoTime()`); bmc4j rejects
it at proof time with a `ContractPurityError` naming the impure call. It is **excluded** from the test run
(the rejection is an unconditional build failure); removing the exclusion in `build.gradle.kts` is a
regression check that the purity gate still fires for assumed-contract predicates.
