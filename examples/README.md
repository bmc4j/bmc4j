# Examples

Read-along examples for `bmc4j`, grouped by topic — one Gradle module per group, and within a
module one **package per concept**. Start with **fundamentals**, then dip into whichever topic you
need. Each concept pairs a proof that **fails on purpose** (JBMC finds a real defect and hands you
the exact input) with one that passes — that failure *is* the result.

| Module | What it covers |
|---|---|
| [fundamentals-java](fundamentals-java) | The core ideas in Java: array bounds, integer overflow, null-safety, `assume` domains, loop unwinding, enums |
| [fundamentals-kotlin](fundamentals-kotlin) | The same six fundamentals in idiomatic Kotlin (incl. `!!`/`?.`/`?:` null-safety), plus symbolic object parameters and `lateinit` |
| [language-java](language-java) | Java language features: lambdas & method references (desugared from invokedynamic) |
| [language-kotlin](language-kotlin) | Kotlin features: `when` in every form (incl. sealed `is`), value classes & `assumeValid`, exception-field proofs via `checkThrows` |
| [language-kotlin24](language-kotlin24) | What's new in Kotlin 2.4: context parameters, explicit backing fields, collection literals (needs kotlinc ≥ 2.4) |
| [stdlib](stdlib) | Standard-library modeling: strings, collections, `BigDecimal`, `java.time` |
| [integrations](integrations) | Real-world inputs: Jakarta validation, env/property config, custom models (Java & Kotlin) |
| [kotlin-coroutines-and-lincheck](kotlin-coroutines-and-lincheck) | Coroutines (`suspend` proofs) and BMC-vs-Lincheck (logic vs concurrency) |
| [contracts-kotlin](contracts-kotlin) | Method contracts from Kotlin (kapt-wired): basics (`object`+`@JvmStatic`), instance receivers, default parameters, the soundness guard, the purity audit on a `this`-mutation |
| [contracts](contracts) | Method contracts (`@Requires`/`@Ensures`) in Java: basics, recursion-as-induction, stacking, the soundness guard |

## Running them

These are child projects of the repo's single Gradle build — nothing to publish first. From the
repo root:

```
./gradlew :examples:fundamentals-java:test                 # one module (topic)
./gradlew :examples:stdlib:test --tests "proofs.bigdecimal.*"   # one concept within a module
./gradlew test --continue                                  # all of them
```

**In your IDE:** open the repo (import the Gradle project) and click the run/debug gutter icon
next to any `@BmcProof` method — it runs that single proof, green/red like any test.

**The bug-finding proofs fail on purpose** — that failure *is* the result (JBMC found the defect).
Each module README shows the expected outcomes per concept. Use `--continue` so every module runs
even after one "fails".

The fundamentals modules need only the bare plugin setup — that's the whole setup a real project
needs:

```kotlin
plugins {
    java
    id("org.bmc4j")
}
```

Other modules add what their topic requires (the Kotlin plugin, a validation dependency, Lincheck).
Each module README records a `bmc:metadata` block with its proof count and summed JBMC
proof-execution time (handy for spotting slow concepts; it is not the wall-clock, since proofs run
in parallel).
