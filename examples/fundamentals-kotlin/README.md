<!-- bmc:metadata
proofs: 13
proof-execution: 334s summed across the module (JBMC time, MiniSat; approximate). Proofs run in
  parallel, so wall-clock is far lower — this number is for spotting slow concepts, not timing the build.
-->

# Fundamentals (Kotlin)

The same six fundamentals as [fundamentals-java](../fundamentals-java), written in idiomatic
Kotlin. JBMC analyzes JVM bytecode, so Kotlin verifies the same way — the point of this module
is that the on-ramp works in both languages, including Kotlin's null-safety operators.

```
./gradlew :examples:fundamentals-kotlin:test
./gradlew :examples:fundamentals-kotlin:test --tests "proofs.nullsafety.*"
```

## `arraybounds`
`Grades.label(score)` indexes a 5-element band array and breaks at `score == 100` (index 5) —
the off-by-one, in Kotlin. `labelSafe` clamps the index and is proven over 1..100.
*(1 fail + 1 pass.)*

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
