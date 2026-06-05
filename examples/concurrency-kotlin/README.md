<!-- bmc:metadata
proofs: 13 @BmcProof (+ 3 Lincheck tests, opt-in via -Dbmc.lincheck=true)
proof-execution: 277s summed across the module (JBMC time, MiniSat; approximate). The Lincheck
  interleaving search adds ~150s when enabled.
-->

# Concurrency (Kotlin)

Two concepts. The **Lincheck** tests exercise a JetBrains library (not bmc4j) and its
interleaving search is slow, so they're **opt-in** — by default only the `@BmcProof` side runs,
keeping regressions fast.

```
./gradlew :examples:concurrency-kotlin:test                       # @BmcProof only
./gradlew :examples:concurrency-kotlin:test -Dbmc.lincheck=true   # + Lincheck
```

## `coroutines` — proving `suspend` functions

Write a normal coroutine test — a method wrapped in `runBlocking { }` calling `suspend`
functions — and JBMC proves it for all inputs. A `suspend fun` compiles to a state machine; the
real `runBlocking`/dispatcher machinery is far too much to verify, so the plugin puts **clean
models** of the coroutine runtime (`runBlocking`/`coroutineScope`/`withContext`/`async`/`launch`,
`Dispatchers`, `delay` as a no-op, and the `Continuation` state-machine runtime) on JBMC's
analysis classpath. This verifies the **sequential logic** of suspend code under an immediate
dispatcher — `async`/`launch` run their blocks inline, not racing. **The bug:** `computeBuggy`
is off-by-one after a suspension point. *(9 pass + 1 fail.)*

## `lincheck` — logic vs. concurrency are different concerns

A `@BmcProof` proves your **logic** is sound; it does **not** prove **thread-safety**. Three
accounts differ by one protection each — the overdraft guard (logic) and the lock
(`@Synchronized`, concurrency) — and exactly the matching tool goes red while the other shrugs:

| Class | Guard | Lock | `@BmcProof` | Lincheck |
|---|:---:|:---:|:---:|:---:|
| `RacyAccount` | ✅ | ❌ | **PASS** | **FAIL** |
| `OverdraftAccount` | ❌ | ✅ | **FAIL** | **PASS** |
| `SafeAccount` | ✅ | ✅ | PASS | PASS |

`@BmcProof` answers "is my logic sound" (all inputs × one sequential execution); Lincheck answers
"is my concurrency correct" (all interleavings × sampled inputs). Each is blind to the other's
bug. *(LogicProofs: 2 pass + 1 fail; the 3 Lincheck tests are skipped unless opted in.)*
