<p align="center"><img src="assets/logo.svg" width="440" alt="bmc4j"></p>

<p align="center">
  <a href="https://central.sonatype.com/artifact/org.bmc4j/bmc-runtime"><img src="https://img.shields.io/maven-central/v/org.bmc4j/bmc-runtime?label=maven%20central" alt="Maven Central"></a>
  <a href="https://plugins.gradle.org/plugin/org.bmc4j"><img src="https://img.shields.io/gradle-plugin-portal/v/org.bmc4j?label=plugin%20portal" alt="Gradle Plugin Portal"></a>
  <a href="https://github.com/bmc4j/bmc4j/actions/workflows/ci.yml"><img src="https://github.com/bmc4j/bmc4j/actions/workflows/ci.yml/badge.svg" alt="CI"></a>
  <a href="LICENSE"><img src="https://img.shields.io/github/license/bmc4j/bmc4j" alt="License"></a>
</p>

**Prove your Java or Kotlin code correct for *every* input — as easily as writing a JUnit test.**

A unit test checks the inputs you thought of. A property-based test samples a few
hundred more. A `@BmcProof` covers **all of them at once**: apply one Gradle plugin,
write a test-shaped method, and [JBMC](https://www.cprover.org/jbmc/) *proves* (within
an explicit bound) that no input breaks your code — or fails the test with a real stack
trace and the exact input that does.

bmc4j targets the **JVM**: **Java** (17–25) and **Kotlin** (2.0–2.3) are the verified,
first-class languages — it analyses the bytecode, so your build doesn't change beyond
the one plugin. (Kotlin 1.9 is supported via the artifacts' 1.9 metadata/stdlib floor,
just no longer re-verified on every merge — see
[docs/internals.md](docs/internals.md#java--kotlin-versions).)

> ⚠️ **Early development.** bmc4j is pre-1.0 (`0.x`) and under active development:
> APIs, the `bmc { }` DSL, and model coverage are still moving. While pre-1.0, minor
> releases may add features *and* break things; patch releases are fixes and model
> additions only — see the [GitHub releases](https://github.com/bmc4j/bmc4j/releases)
> for per-release notes.
> The soundness discipline is not provisional — but treat everything else as subject
> to change.

```kotlin
// build.gradle.kts
plugins {
    java
    id("org.bmc4j") version "<version>" // latest: see the Maven Central badge above
}
```

```java
import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;

class GradeBandProofTests {

    @BmcProof
    void gradeBand_never_throws_for_valid_scores() {
        int score = Bmc.anyInt();               // symbolic input — ALL values at once
        Bmc.assume(score >= 1 && score <= 100); // restrict to the valid domain
        Example.gradeBand(score);               // proven for every score in range
    }

    @BmcProof
    void clamp_result_is_always_within_bounds() {
        int x = Bmc.anyInt(), lo = Bmc.anyInt(), hi = Bmc.anyInt();
        Bmc.assume(lo <= hi);
        int r = Example.clamp(x, lo, hi);
        Bmc.check(r >= lo && r <= hi);          // a property to prove
    }
}
```

```
./gradlew test
```

That's it. The engine ships as an ordinary, integrity-verified dependency — no runtime
download, no install, works offline. Each `@BmcProof` is a real JUnit 5 test: it shows
up in the test tree, runs from the IDE gutter, and a refuted proof fails like any
assertion — except the failure comes with the *proof* that it had to:

```
gradeBand_never_throws_for_valid_scores()  FAILED
  org.bmc4j.engine.BmcVerificationError: JBMC refuted ...gradeBand_never_throws_for_valid_scores
    ✗ Array index should be < length  (Example.java:16)
      counterexample: score = 100
      replay:
        int score = 100;
        // then run the body of GradeBandProofTests.gradeBand_never_throws_for_valid_scores with these value(s)
      replay test written to: build/bmc4j/replays/GradeBandProofTests_gradeBand_never_throws_for_valid_scoresReplay.java
    at example.Example.gradeBand(Example.java:16)
    at proofs.GradeBandProofTests.gradeBand_never_throws_for_valid_scores(GradeBandProofTests.java:20)
```

The counterexample renders as concrete Java you can paste into a scratch test and step
through in a debugger — and bmc4j drops a ready-to-run `@Test` scaffold next to it.

## What is bounded model checking?

Instead of *running* your code on some inputs, BMC **translates** it — bytecode,
branches, loops (unwound to a bound) — together with the property into one big logical
formula, and hands that to a SAT solver. If the formula is satisfiable, the solver has
constructed an input that breaks the property: that's your counterexample, decoded back
into concrete values. If it's unsatisfiable, **no such input exists** — the property is
proven for *every* input within the bound, not observed on a sample of them. So a
`@BmcProof` isn't a very fast test loop; it's a single symbolic execution of all inputs
simultaneously, with a solver doing the search.

## Why a proof instead of more tests?

| | covers | misses | on failure |
|---|---|---|---|
| **Unit test** | the inputs you wrote down | everything you didn't think of | a known case |
| **Property-based test** | a few hundred random samples per run | needle-in-a-haystack inputs (one bad value in 4 billion), rare branch combinations | a shrunken sample, if sampling found one |
| **`@BmcProof`** | **every input within the bound** | nothing inside the bound — refutation is guaranteed if a bad input exists | the exact counterexample, rendered as runnable code |

The off-by-one that only fires at `score = 100`, the overflow at `Integer.MIN_VALUE`,
the rounding rule that's wrong in one of 10⁹ cases — sampling is structurally bad at
these; exhaustive search is not. Proofs are also **deterministic**: no flaky seed, no
"passed this run." The three approaches complement each other — bmc4j replaces neither
your unit tests nor your integration tests; it covers the class of bug they can't.

## When to use it

`@BmcProof` answers *"is my logic sound?"* It shines on pure(ish), bounded logic where
a wrong answer is expensive:

- **Money & rounding** — pricing, tax, interest, `BigDecimal` scale rules
- **Parsers, codecs, serializers** — round-trips, bounds, malformed input
- **State machines & business rules** — invariants, unreachable-state proofs
- **Validation & clamping** — range checks, normalization, overflow on arithmetic
- **Index arithmetic** — pagination, buffers, windowing, binary search
- **Equality/ordering laws** — `equals`/`hashCode`/`compareTo` contracts, record laws

It's also a **debugger that works backwards**: when you know a bad state exists — the
total that went negative in production, the enum combination that "can't happen", the
corrupted invariant — but not which input gets there, write the bad state as the
property (`Bmc.check(!bad)`) and let the refutation *find the input for you*. The
counterexample is a ready-made reproduction of the bug, dropped into
`build/bmc4j/replays/` as a runnable test.

And when **not** to: I/O and frameworks (mock boundaries, prove the logic between them),
float-heavy numerics (IEEE-754 is slow in a SAT solver — prefer integer models),
unbounded structures (everything is proven *within a bound*), and serious concurrency
testing (see the note below).

> **Concurrency: supported but experimental, deliberately not first-class.** bmc4j can
> exhaustively search **JVM thread** interleavings for small state spaces (see
> [`examples/concurrency-java`](examples/concurrency-java)) — a few threads, short
> critical sections, basic `synchronized`/atomic patterns. It will find the data race
> and show you the exact interleaving. But interleaving search explodes
> combinatorially, so it does not scale to realistic concurrent code **and never will
> under BMC** — that's solver physics, not a roadmap item. **Kotlin coroutines: not at
> all.** The interleaving search understands JVM threads only — dispatchers,
> suspension scheduling and structured concurrency aren't modeled, so coroutine
> *concurrency* cannot be verified, full stop. (The *sequential* logic inside `suspend`
> functions still proves fine via the bundled `runBlocking` model — see
> [`examples/concurrency-kotlin`](examples/concurrency-kotlin).) For serious
> concurrency testing — linearizability, lock-freedom, large state spaces, coroutines —
> use [Lincheck](https://github.com/JetBrains/lincheck); bmc4j is its complement for
> sequential logic, not its competitor.

## The tradeoffs, honestly

Bounded model checking is a power tool with a real contract. The short version:

- **Bounded, not unbounded.** Loops unwind to `unwind`; proofs hold *within* the bound.
  An insufficient bound is **reported, not silently trusted** (`--unwinding-assertions`
  is on by default).
- **Three-way verdict.** A proof is **verified**, **refuted** (with a counterexample),
  or **UNKNOWN** — undecided within budget. UNKNOWN fails the test but says so
  distinctly: a solver timeout is not "your code is wrong."
- **Solver time can blow up** on specific shapes — symbolic division/modulo and long
  symbolic strings are the classic ones. The levers, in order: shrink the symbolic
  range (`anyInt(lo, hi)` beats `anyInt()` + `assume`), summarize heavy callees with a
  [contract](docs/contracts.md), set a `timeoutSeconds` budget.
- **The JDK is modeled, not loaded.** bmc4j ships sound bounded models for the common
  surface (collections, `Optional`, `Stream`, `BigInteger`/`BigDecimal`, `java.time`,
  Kotlin stdlib) and **detects** anything unmodeled instead of letting it pass silently
  — a green proof that leaned on an unmodeled method gets a footnote naming it
  (`strictStubs` turns that into UNKNOWN).
- **Soundness is engineered, not assumed.** JBMC's known unsound spots (string
  equality, `invokedynamic` from string concat / records / lambdas) are rewritten to
  sound forms in bmc4j's own layer; vacuous proofs (contradictory `assume`s) fail
  loudly instead of passing over an empty domain. And the models themselves are
  **verified on two axes** — differential property tests against the real JDK, plus
  ~200 `@BmcProof` laws proven under the engine itself — with a coverage gate that
  fails the build on any unverified model
  ([how we know the models are sound](docs/model-soundness.md)).

The full, unvarnished list — including what is still stubbed and the residual quirks —
is in [docs/limits.md](docs/limits.md). Read it before trusting a proof with money.

## New to BMC? About proof runtimes

The first surprise for newcomers: a proof's runtime tracks the size of its *formula*,
not the speed of your code — so a proof can take seconds, and a small change can make
it take minutes. **That's normal, it isn't a bug in your code, and it's very tunable**:
the same proof over a sensible input range typically solves orders of magnitude faster
while still covering every value you'll ever see, and a timeout turns a runaway solve
into a clean, named UNKNOWN instead of a hung build. Don't let one slow proof scare you
off — [docs/performance.md](docs/performance.md) is the decision tree: which shapes
explode, the levers in order of payoff, and what the tool already does for you (the
verdict cache means the expensive solve happens once per change, not once per run).

## Documentation

| | |
|---|---|
| [docs/api.md](docs/api.md) | the full `Bmc.*` API: symbolic inputs, `assume`/`check`, symbolic objects & strings, config readers, floating-point rules, stub detection |
| [docs/contracts.md](docs/contracts.md) | modular proofs: `@Requires`/`@Ensures` method contracts — prove once, reuse at every call site |
| [docs/configuration.md](docs/configuration.md) | the `bmc { }` block: unwind, parallelism, the external SAT solver, the verdict cache, timeouts, stub policy |
| [docs/performance.md](docs/performance.md) | when proofs get slow: why formula size explodes and the tuning levers in order of payoff |
| [docs/trust.md](docs/trust.md) | trust & isolation: the bundled-engine model, `jbmcPath` escape hatch |
| [docs/model-soundness.md](docs/model-soundness.md) | how we know the models are sound: differential conformance vs the real JDK + the models' own laws proven under the engine, gated in CI |
| [docs/internals.md](docs/internals.md) | how it works, module layout, platform support, verified Java/Kotlin ranges |
| [docs/coverage.md](docs/coverage.md) | the coverage map: every language construct and stdlib API — modeled / partial / stubbed / out-of-scope |
| [docs/limits.md](docs/limits.md) | known limits, in full |
| [`examples/`](examples) | a guided, read-along tour — 9 topic modules, each concept with its own README, code, and expected output |

## Status

| | |
|---|---|
| Engine | CBMC 6.9.0 / JBMC, bundled per platform: windows-x64, linux-x64/arm64 (glibc), macos-x64/arm64. Windows-arm64 and Alpine/musl Linux are unsupported (fail fast) |
| Java | 17 – 25 verified on every merge (full suite on 21/25, core + conformance on the 17 floor) |
| Kotlin | 2.0 – 2.3 verified on every merge via a consumer-compiler matrix (null-safety, data classes, collections, `runBlocking` logic); 1.9 supported via the artifacts' 1.9 metadata/stdlib floor |
| CI | per-platform engine jars + a proof gate on every platform, every supported JDK, and consumer-Kotlin 2.0/2.2 |
| License | [Apache-2.0](LICENSE); bundled engine binaries under the [CBMC license](THIRD-PARTY-NOTICES.md) |

## Developing

The repo is one Gradle build: the product lives under `core/`, the `examples/` and the
model-conformance proof suite are child projects wired via `includeBuild` — open the
repo, click the gutter icon next to any `@BmcProof`.

```powershell
./gradlew -p core build                                        # product + unit tests
./gradlew test                                                 # everything: conformance proofs + all examples
./gradlew :examples:fundamentals-java:test                     # or just one topic module
```

The fail-on-purpose demo proofs declare their expected verdict
(`@BmcProof(expect = REFUTED / VACUOUS / TIMEOUT)`), so they pass while the guard
they demonstrate holds — the root `test` is green by design, and a demo drifting
back to VERIFIED fails loudly.

## License & acknowledgements

bmc4j is licensed under the [Apache License 2.0](LICENSE) — including the
`examples/`, so you can copy-paste from them freely. The per-platform engine jars
redistribute the official CBMC/JBMC release binaries, which remain under the
[CBMC license](THIRD-PARTY-NOTICES.md) (BSD-4-clause style); every published jar
carries the full notices in `META-INF/`.

> This product includes software developed by Daniel Kroening, Edmund Clarke,
> Computer Science Department, University of Oxford, Computer Science Department,
> Carnegie Mellon University

Contributions: see [CONTRIBUTING.md](CONTRIBUTING.md). Found a way to make a
false proof show green? That's a security-grade bug — see [SECURITY.md](SECURITY.md).
