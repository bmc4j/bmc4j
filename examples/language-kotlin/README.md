<!-- bmc:metadata
proofs: 10
proof-execution: 138s summed across the module (JBMC time, MiniSat; approximate).
-->

# Language features (Kotlin)

Kotlin-specific constructs made analyzable. One package per concept.

```
./gradlew :examples:language-kotlin:test
./gradlew :examples:language-kotlin:test --tests "proofs.whenexpr.*"
```

## `whenexpr` — Kotlin `when`, every form

`when` takes many subject forms, all sound under BMC:

- **enum** — `$WhenMappings` + `tableswitch` (like a Java `switch`)
- **sealed hierarchy** with `is` branches — *exhaustive*, no `else`, reading the matched
  variant's properties (the headline use)
- **String** — lowered to character-wise equality (sound; JBMC's native `String.equals` is not)
- **Int ranges** and multi-value branches
- **subjectless** `when { cond -> … }`

```kotlin
sealed interface Shape
data class Circle(val r: Int) : Shape
data class Square(val s: Int) : Shape
data class Rect(val w: Int, val h: Int) : Shape

fun area(shape: Shape): Int = when (shape) {   // exhaustive — every case guaranteed
    is Circle -> 3 * shape.r * shape.r
    is Square -> shape.s * shape.s
    is Rect -> shape.w * shape.h
}
```

**The bug it finds:** `grade(score)` is a range `when` with an off-by-one gap — `in 0..78`
leaves `79` uncovered, silently falling to `'F'`. `every_valid_score_is_graded` fails with
`score = 79`. *(5 pass + 1 fail.)*

## `valueclasses` — invariants & `assumeValid`

Kotlin value classes enforce their invariant in the constructor:

```kotlin
@JvmInline
value class Port(val number: Int) { init { require(number in 1..65535) } }
```

JBMC **runs that `init {}` during analysis**, so the invariant is verified, not taken on faith.
And `assumeValid { Port(Bmc.anyInt()) }` runs the constructor over a symbolic input and prunes
every value it would reject — so the proof reasons about valid `Port`s only, without restating
the range as an `assume`. Validation-as-exceptions *is* the spec; reuse it. `assumeValid` is an
`inline fun`, so the lambda is inlined (no lambda object, no `invokedynamic`).

**The bug it finds:** `next(p) = Port(p.number + 1)` overflows the invariant at the maximum port
(`65535 + 1 = 65536`, rejected). `successor_never_overflows` fails at `p.number == 65535`; the
saturating `safeNext` passes. *(3 pass + 1 fail.)*
