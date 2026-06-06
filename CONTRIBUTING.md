# Contributing to bmc4j

Thanks for considering it. bmc4j is a verification tool, which makes one rule
overriding: **a change that is merely wrong fails tests; a change that is
unsound makes the tool lie.** Most of the conventions below exist to keep the
second kind impossible to land quietly.

## Language policy: Kotlin by default, Java only where the bytecode is the product

bmc4j is written in **Kotlin by default.** New code — the engine, the JUnit 5
extension, the Gradle plugin, the annotation processors, the contracts layer —
should be Kotlin. The bulk of `bmc-runtime` is already Kotlin (the JBMC driver,
the trace/counterexample decoding, the verdict cache, the bytecode-rewrite
passes), and `bmc-gradle-plugin` is Kotlin throughout.

There is one deliberate, **permanent** Java boundary: the **analysis-facing code
whose compiled bytecode is itself the artifact JBMC analyses.** For that code the
exact, stable bytecode shape *is* the product — it must stay hand-controlled and
free of `kotlin-stdlib` references, so that what the engine sees is precisely what
we intend, not whatever a given kotlinc version chose to emit. **Do not port these
to Kotlin:**

- The user-facing facades and API surface in `bmc-runtime`: `Bmc`
  (`core/bmc-runtime/src/main/java/org/bmc4j/Bmc.java`), and the engine-package
  facades `BmcMath`, `BmcStrings`, `BmcKotlin`, and `ConfigSupport`
  (`core/bmc-runtime/src/main/java/org/bmc4j/engine/`).
- The annotations and verdict enum at `core/bmc-runtime/src/main/java/org/bmc4j/`:
  `BmcProof`, `Requires`, `Ensures`, `BmcContractsFor`, `ExpectEnforce`, and
  `Verdict`.
- The analysis support classes JBMC walks directly:
  `analysis/ResidualInvokedynamic` and `concurrent/Latch`
  (under `core/bmc-runtime/src/main/java/org/bmc4j/`).
- **Every** model class in `bmc-models` and `bmc-kotlin-models` — these are the
  hand-written, kotlin-stdlib-free stand-ins the engine analyses *instead of* the
  real JDK/Kotlin classes; they are Java by necessity (see the model section
  below) and must stay that way.

If you find yourself reaching for Kotlin inside one of those, stop: that boundary
is intentional, not technical debt. Everywhere else, prefer Kotlin.

## The green gate (read this first)

```powershell
./gradlew -p core build                  # product + unit tests (incl. the conformance gate)
./gradlew test                           # conformance proofs + every example module
```

Both must be green. The examples include *fail-on-purpose* demo proofs, but each
declares its expected verdict (`@BmcProof(expect = REFUTED / VACUOUS / TIMEOUT)`),
so they pass while the guard they demonstrate holds — and fail loudly if it drifts
(most dangerously, a deliberately false claim coming back VERIFIED). CI runs
exactly this (plus per-platform engine smokes). Iterate on a single proof class
with `--tests "proofs.<area>.*"` rather than re-running the whole suite.

## Changing or adding a JDK/Kotlin model

Models in `bmc-models`/`bmc-kotlin-models` are stand-ins the engine analyses
*instead of* the real classes — a wrong model **proves false things silently**.
Every model change therefore needs (see [docs/model-soundness.md](docs/model-soundness.md)):

1. **Differential coverage** (`core/bmc-models-conformance`, Kotest): the model
   vs the real JDK on a real JVM — return value, exception type, post-state —
   *or* (for JVM-unrunnable models) **model proofs**: `@BmcProof` laws in
   `model-conformance-proofs`.
2. A **coverage-gate entry**: `CoverageGateTest` enumerates every model class
   and fails the build for any class that is neither covered nor waived with a
   written reason. New model = new `COVERED` (or justified `WAIVED`) entry.
3. The invariant to honor: *within its documented bounds the model matches the
   JDK's observable behavior; outside them it fails loudly — never a silent
   wrong value.*

## Changing the bytecode-rewrite layer

The rewrite passes (`StringBytecode`, `MathBytecode`, `LambdaBytecode`, …) are
the soundness boundary. A change there needs a unit test on the transform
itself **and** an end-to-end proof demonstrating the soundness property it
exists for (the existing tests show the pattern).

## Conventions

- Proof-bearing changes should keep an eye on solver cost: a proof needing
  more than ~60s on a 4-core runner wants range reduction, a contract, or the
  differential axis instead (the CI gate runs proofs under a 180s budget and
  will fail it by name).
- Match the style around you; comment *why*, not *what*.

## Licensing

Contributions are accepted under [Apache-2.0](LICENSE). The bundled engine
binaries remain under the [CBMC license](THIRD-PARTY-NOTICES.md) — don't add
code copied from CBMC/JBMC sources without flagging it for license review.
