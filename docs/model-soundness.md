# How we know the models are sound

bmc4j ships hand-written, bounded stand-ins for JDK and Kotlin classes
(`bmc-models`, `bmc-kotlin-models`) that JBMC analyzes *instead of* the real
library. That makes them the highest-stakes code in the project: **a wrong model
doesn't fail your proof — it proves false things silently.** So the models are not
trusted; they are verified, on two independent axes, with a gate that makes
uncovered models fail the build.

## Axis 1: differential conformance against the real JDK

Module: `core/bmc-models-conformance` (Kotlin + Kotest property tests).

A Gradle task ASM-relocates the compiled models from `java.*` to `bmcref.java.*`
(the JVM refuses user classes in `java.*`), so **the model and the real JDK class
load side by side on a real JVM**. Kotest property and operation-sequence suites
then drive both with generated inputs — inside each model's documented bound — and
compare:

- the **return value**,
- the **exception type** on the failure paths,
- the **post-state** after mutation sequences.

The invariant under test: *within its bounds the model must produce the same
observable behavior as the JDK; outside its bounds it must fail loudly (throw or
trip a CBMC-visible assert) — never return a silently wrong value.*

## Axis 2: model proofs — the models' own laws, under the engine

Module: `model-conformance-proofs` (`@BmcProof` suites, ~200 proofs, all green in
the CI gate).

The differential axis runs on a JVM — but proofs run under **JBMC's semantics**,
and a model can behave correctly on a JVM yet be mis-interpreted by the engine.
So the second axis proves the models' **algebraic laws under JBMC itself**:
add/get round-trips, `BigDecimal` commutativity and scale round-trips,
`HashMap` put/remove laws, `Optional` identities, `java.time` arithmetic, stream
pipelines. Some models (Kotlin's inline collection operators, `Stream`'s CProver
intrinsics) *cannot* run on a JVM at all — for those, this axis is the only
possible coverage, and it's the one that counts: it validates the model exactly
where it's used.

## The gate: no model ships uncovered

`CoverageGateTest` enumerates **every model class** from the relocated jar and
fails the build unless each one is differential-tested, model-proven, or
explicitly **waived with a written reason** (interfaces covered via their
implementations, exception/enum carriers, the engine-only stream internals).
The check runs both ways: a new model with no registry entry fails the build
(this has fired in practice), and a registry entry whose model was removed fails
too, so the list can't rot.

Honest scope: the gate enforces that a coverage *entry* exists per **class** —
the substance of the suite behind it, and coverage of newly added *methods* on
an already-covered model, are the review's job, not the gate's.

## User-supplied models run the same harness

The two axes above aren't only for bundled models. A **user model** declared
`conformant` (see [configuration → User models](configuration.md#user-models-declared-intent--provenance))
claims JDK fidelity, so it belongs on exactly this harness: relocate it alongside the bundled models for
the differential-vs-JDK axis, and prove its algebraic laws under the engine on the model-proof axis. A
passing conformant model earns a plain green — and a clean upstreaming path into `bmc-models`. A `domain`
model deliberately diverges from the JDK, so the *differential* axis does **not** apply to it (it would
"fail" by design); its trust comes from its declared rationale being footnoted on every green proof that
rests on it, not from JDK conformance.

## Where a law can't be proven economically

Some true laws are SAT-pathological (e.g. wide-divisor division chains in
time-of-day round-trips). Those are covered on the **differential axis only**,
with a comment in the proof suite saying so and why — the coverage stays, the
solver bill goes. The CI gate runs every proof under a wall-clock budget, so a
new pathological proof fails fast and named instead of hanging the build.
