<!-- bmc:metadata
proofs: 9
proof-execution: ~60s summed across the module (JBMC time, MiniSat; approximate). Proofs run in
  parallel, so wall-clock is far lower - this number is for spotting slow concepts, not timing the build.
-->

# Assumed output-contracts (Kotlin)

The Kotlin counterpart of `examples/assume-contracts`. Same assume-guarantee shapes, driven through the
IDIOMATIC Kotlin call site the feature exists for: a method reference SAM-converted to a `Bmc.Ref`, plus
a trailing-lambda predicate using `it`. This validates the Kotlin method-ref + trailing-lambda path
end-to-end (decode AND proof), not just at the decode level.

```kotlin
Bmc.assumeEvery(repo::findById) { it == null || it.age >= 0 }          // output-only, fresh per call
Bmc.assumeEvery(repo::findById) { user, id -> user == null || user.id == id }  // args-aware
Bmc.assumeStable(env::bucketCount) { it == 8 }                         // one fixed value for the whole run
```

`Bmc.assumeEvery` / `Bmc.assumeStable` let a proof **assume** an external, unanalyzable dependency upholds
an output property and prove on top of it - no model, no annotation, no string method name. This is
**assume-guarantee**: *"IF `findById` returns a valid user, THEN my service is correct."*

bmc4j reads the referenced method **statically** from the method reference's bootstrap handle (it never
executes the `invokedynamic`), shadows the target on the analysis classpath with a constrained-nondet stub
`R m(args){ R r = nondet(); assume(predicate(r [,args])); return r; }`, and redirects every call site -
including those in `<clinit>` and inside callees the proof doesn't control.

- `assumeEvery` is **fresh per call** (a sound over-approximation: every output satisfying the predicate),
  the right default for repositories / services / factories.
- `assumeStable` pins **one value for the whole run** (memoized), the right form for a deterministic query
  whose value seeds a `<clinit>` or a config constant.

**Soundness.** The micro-model is an *assumption* (constrained nondet via `assume`, never `assert`), so a
property proven on top of it holds for any real implementation that respects the predicate. A VERIFIED
reached this way is **flagged on the verdict** ("VERIFIED under assumed contract ... - NOT unconditional").
An over-tight predicate that rules out every output surfaces as **VACUOUS**.

The predicate is **not** purity-audited. Unlike a dischargeable `@Ensures` contract, an assumed contract is
an explicit, per-proof, user-owned assertion, so an impure or effectful predicate is allowed: a
legitimately richer micro-model.

```
./gradlew :examples:assume-contracts-kotlin:test
```

## `repository` - assume-guarantee over an unanalyzable repository

A `UserService` sits over a repository the proof can't see through. The service is correct only IF the
repository upholds an output property; `assumeEvery(repo::findById) { ... }` supplies it. `service
holds...` VERIFIES under the output-only assumption; **drop** it and the same property is **REFUTED** (the
assumption is load-bearing). `args aware...` constrains the output **by** the call argument with a
two-param trailing lambda (`result.id == id`). `an over tight predicate...` rules out every output =>
**VACUOUS**. `an impure predicate is accepted` reads a mutable top-level var inside the predicate and still
VERIFIES - `assumeEvery` is a user-owned assertion, not a purity-audited contract. `anyRef needs no
concrete stub` drops even the `NondetRepository`: `Bmc.anyRef(UserRepository::class.java)` hands the proof
a symbolic repository (any implementation) directly - one typed handle + one assumption, no stub class;
`anyRef without the assumption is refuted` is its load-bearing negative.

## `env` - `assumeStable` and the `<clinit>` case

A `Buckets` static initializer reads a deterministic, unanalyzable `Environment.bucketCount()` into a final
array bound. `assumeStable(env::bucketCount) { it == 8 }` pins it to one value for the whole run - reaching
the static initializer a local `assume` could never touch - so the array is sized 8 and the proof VERIFIES.
Without the pin the captured bound is symbolic and `count() == 8` is **REFUTED**.
