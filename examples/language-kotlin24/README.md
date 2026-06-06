<!-- bmc:metadata
proofs: 8
proof-execution: 7s summed across the module (JBMC time, MiniSat; approximate).
-->

# Language features (Kotlin 2.4)

What's new in Kotlin 2.4 under BMC. One package per concept. **Needs kotlinc ≥ 2.4** — the
CI consumer-Kotlin matrix runs this module only on its 2.4+ legs.

```
./gradlew :examples:language-kotlin24:test
./gradlew :examples:language-kotlin24:test --tests "proofs.contextparams.*"
```

## `contextparams` — context parameters (stable)

A context parameter compiles to an extra leading JVM parameter, and the stdlib
`context(value) { ... }` bridge is an `inline fun` — the lambda is inlined at the call site
(no lambda object, no `invokedynamic`). Proofs over context-parameterized functions work
exactly like proofs over plain ones:

```kotlin
context(limits: Limits)
fun clampDeposit(amount: Int): Int = if (amount > limits.max) limits.max else amount

val deposited = context(limits) { clampDeposit(amount) }   // in a proof
```

**The bug it finds:** `clampDeposit` clamps the top but trusts the caller on the bottom —
`clamp_yields_valid_amount` fails with a negative `amount`. The `coerceIn` fix passes.
*(2 pass + 1 fail.)*

## `backingfields` — explicit backing fields (stable)

The public property exposes the read-only view (`List`), the backing field keeps the mutable
type (`MutableList`) — no second `_private` property. On the JVM it's an ordinary field plus a
getter, so nothing new reaches JBMC:

```kotlin
class SessionLog {
    val durations: List<Int>
        field = mutableListOf()
}
```

**The bug it finds:** `record()` takes the duration on faith; one skewed clock and
`totalTime()` goes negative. `total_time_never_negative` fails with a negative duration; the
clamped recorder passes. *(1 pass + 1 fail.)*

## `collectionliterals` — collection literals (experimental, `-Xcollection-literals`)

The bracket literal lowers to the stdlib's `of` factory — under BMC it behaves exactly like
`listOf(...)`, including the part where indexing past its end throws:

```kotlin
val WEEKDAY_FEES: List<Int> = [5, 10, 10, 10, 25]   // five entries
fun feeFor(dayOfWeek: Int): Int = WEEKDAY_FEES[dayOfWeek]
```

**The bug it finds:** callers index by day-of-week `0..6` but the literal has five entries —
`fee_defined_for_every_day` fails at `dayOfWeek == 5` (Saturday) with
`IndexOutOfBoundsException`. The totalized `safeFeeFor` passes. *(2 pass + 1 fail.)*
