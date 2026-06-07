<!-- bmc:metadata
proofs: 23
proof-execution: 380s summed across the module (JBMC time, MiniSat; approximate). Proofs run in
  parallel, so wall-clock is far lower — this number is for spotting slow concepts, not timing the build.
-->

# Fundamentals (Kotlin)

The same six fundamentals as [fundamentals-java](../fundamentals-java), written in idiomatic
Kotlin, plus two Kotlin-specific ones (symbolic parameters, `lateinit`). JBMC analyzes JVM
bytecode, so Kotlin verifies the same way — the point of this module is that the on-ramp works
in both languages, including Kotlin's null-safety operators.

```
./gradlew :examples:fundamentals-kotlin:test
./gradlew :examples:fundamentals-kotlin:test --tests "proofs.nullsafety.*"
```

## `arraybounds`
`Grades.label(score)` indexes a 5-element band array and breaks at `score == 100` (index 5) —
the off-by-one, in Kotlin. `labelSafe` clamps the index and is proven over 1..100. The same
package also carries the README's headline value-class shape: a `Score` value class
(`init { require(value in 1..100) }`) whose invariant is verified under BMC, and `gradeBand`
proven to never throw for any `Score` — `assumeValid { Score(anyInt()) }` folds the `require`
range into the proof domain, with a refute-pinned proof confirming the domain really is 1..100.
*(2 fail + 3 pass.)*

## `integeroverflow`
`Numbers.abs(x) = if (x < 0) -x else x` overflows: Kotlin `Int` arithmetic wraps just like
Java, so `abs(Int.MIN_VALUE)` is negative. `max` is proven correct for all inputs.
*(1 fail + 1 pass.)*

## `nullsafety` — `!!` / `?.` / `?:` analyze cleanly
`Accounts.parentBalance(a) = a.parent!!.balance` throws when `parent` is null. Kotlin lowers
`!!` to `kotlin.jvm.internal.Intrinsics`, whose real implementation trips JBMC with stack-trace
machinery — so the runtime bundles a **clean `Intrinsics` model** (just the null semantics). The
proof then fails precisely on the missing-parent case, while the null-safe `parentBalanceOrZero`
(`?.`/`?:`) is proven. No configuration needed. *(1 fail + 1 pass.)*

## `assumedomain`
`Items.elementAt(a, i) = a[i]` over an `IntArray`. With `0 <= i < a.size` assumed it's safe;
with only the lower bound, `i == size` slips through. *(1 pass + 1 fail.)*

## `loopsunwinding`
`Sums.sumTo` is a plain counted `while` loop (no range object) checked against `n*(n+1)/2`.
`@BmcProof(unwind = 12)` covers n ≤ 10 and passes; `unwind = 4` is too small and is reported by
the unwinding assertion. *(1 pass + 1 fail.)*

## `enums`
`when` over a Kotlin `enum class` (sound under JBMC — it lowers to a tableswitch, not
invokedynamic). The buggy colour classifier omits SPADES; the fixed one and an exhaustive `rank`
both verify. *(2 pass + 1 fail.)*

## `symbolicparams` — symbolic Kotlin object parameters
A `@BmcProof` taking an object parameter ("for every `Wallet`…") is the most natural proof
shape — and in Kotlin it used to refute before the body ran: kotlinc guards every non-null
parameter with an `Intrinsics.checkNotNullParameter` prologue, and JBMC's nondet input includes
`null` — an input no Kotlin caller can construct. bmc4j relaxes that prologue (and the matching
`@NotNull` annotation) to `assume(p != null)` **in proof methods only**, so the proof ranges
over what the type system admits. Nullable (`Wallet?`) parameters keep `null` in their domain —
pinned by a fail-on-purpose proof — and interior calls keep the throwing semantics.
`bmc { kotlinNullableParams = true }` restores the honest-JVM null domain for proofs that
deliberately model hostile Java callers. The bug found en route: `absCents()` overflows at
`Int.MIN_VALUE` — the same wrap as `integeroverflow`, arriving through a fully symbolic field.
*(2 pass + 2 fail.)*

## `lateinitprops` — `lateinit`, all three directions
Initialization promised, not proven by the type system: the unguarded pre-init read refutes
(the defect), the `::user.isInitialized` guard verifies, and init-then-read verifies — so a
`lateinit` lifecycle bug is findable, and the guarded idiom provably safe. *(2 pass + 1 fail.)*
