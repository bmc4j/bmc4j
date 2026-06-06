# The API

| | |
|---|---|
| `Bmc.anyInt()`, `anyLong()`, `anyShort()`, `anyByte()`, `anyChar()`, `anyBoolean()`, `anyFloat()`, `anyDouble()` | introduce a symbolic input (floating-point forms include NaN/∞ — see below) |
| `Bmc.anyInt(lo, hi)`, `anyLong(lo, hi)`, `anyFloat(lo, hi)`, `anyDouble(lo, hi)`, `anyPositiveInt()`, `anyNonNegativeInt()` | a symbolic input with its domain folded in (bounded floating-point forms **exclude NaN** by construction) |
| `Bmc.anyOf(values)`, `anyOf(a, b, …)`, `anyOf(List)` | a symbolic element of a domain — array (`anyOf(Suit.values())`), varargs (`anyOf("us", "eu")`), or list |
| `Bmc.intFromProperty("KEY")`, `boolFromEnv(...)`, `doubleFromProperty(...)`, `longFrom…`, `stringFrom…` | the **actual** value of env var / system property `KEY` the run was launched with — pinned into the proof (not symbolic); a required-but-unset or unparseable var fails the proof — booleans must be exactly `true`/`false` (case-insensitive), anything else (`1`, `yes`) fails rather than silently reading as `false` (see [`examples/integrations`](../examples/integrations)) |
| `Bmc.anyString(n)` | a symbolic string of length `0..n` — a length is required so string comparisons stay bounded |
| `Bmc.anyString(min, max)` | a symbolic string with `min <= length <= max` (`anyString(n, n)` = exactly `n`, `anyString(1, n)` = non-empty) |
| `Bmc.anyString(n, alphabet)`, `Bmc.anyAsciiString(n)` | a symbolic string of length `0..n` whose **every char** is from `alphabet` (resp. printable ASCII `0x20..0x7E`) — the string analogue of `anyInt(lo, hi)`; shrinks the per-char SAT domain and dodges the all-UTF-16 trap |
| `Bmc.assume(cond)` | restrict inputs where the helpers don't fit (e.g. relations between inputs) |
| `Bmc.assumeUnreachable()` | prune the current path (`= assume(false)`) — e.g. in a `catch` for input a constructor rejected |
| `assumeValid { Ctor(any()) }` *(Kotlin)* | a symbolic instance **valid by construction** — runs the constructor and prunes inputs it rejects (reuses `init { require(...) }` as the spec); see [`examples/language-kotlin`](../examples/language-kotlin) |
| `checkThrows<E> { … }`, `throws<E> { … }` *(Kotlin)* | check a block throws an `E` for every allowed input (resp. the composable boolean form, for *iff*-laws like `check(throws<E> { Ctor(raw) } == (raw !in valid))`) — the BMC analogue of `assertFailsWith`; a throw of any *other* type propagates and fails the proof with its own trace. `inline`, so no lambda object / `invokedynamic` |
| `bmc { models { domain("acme.Map", "no key collisions") } }` | a **domain model** is `assume(...)` at *classpath* altitude — a `src/bmcModel` class that intentionally diverges from the JDK to encode an invariant. Declare its intent so it's **footnoted on every green proof that rests on it** (a `conformant` one instead claims JDK fidelity + is verifiable like a bundled model); see [configuration → User models](configuration.md#user-models-declared-intent--provenance). Beware the same [vacuity](limits.md) trap as `assume`: an over-restrictive model makes proofs trivially green |
| `Bmc.check(cond[, msg])` | a property that must hold for all allowed inputs |
| *(automatic)* **vacuity check** | a proof whose `assume`s are jointly unsatisfiable fails as *"assumptions are unsatisfiable - this proof checks nothing"* rather than passing vacuously over an empty input domain (see [limits](limits.md)) |
| `@BmcProof void p(User u, String s)` | proof-method **parameters** are symbolic inputs — objects, strings, arrays |
| `@BmcProof(unwind = N, unwindingAssertions = true, maxStringLength = N, timeoutSeconds = N)` | per-proof loop bound + bound-sufficiency check, symbolic-string length bound, and wall-clock budget (`0` = build default / `-Dbmc.*`; `timeoutSeconds = 0` = no timeout) |
| `@BmcProof(expect = Verdict.REFUTED)` (or `VACUOUS` / `UNKNOWN` / `TIMEOUT`) | declare the verdict a **fail-on-purpose** proof exists to produce — the test passes only if the actual verdict matches. A false claim that drifts back to VERIFIED fails loudly naming both verdicts, turning bug-demos into real regression tests. `TIMEOUT` is the structured subtype of `UNKNOWN` (the wall-clock budget actually fired — a solver crash won't satisfy it; `UNKNOWN` accepts any genuine undecided outcome, timeouts included); an engine-infrastructure failure satisfies neither |
| *(automatic)* **three-way verdict** | a proof is **verified** (green), **refuted** (red, with a counterexample), or **UNKNOWN** — undecided within budget (timeout, solver gave up/crashed, or unparseable output). UNKNOWN still fails, but with a distinct message (no counterexample; how to make it decidable) and exception type `BmcUndecidedError` — a resource exhaustion is not "your code is wrong" (see [limits](limits.md)) |
| *(automatic)* **nondet-stub detection** | a green proof that relied on an *unmodeled* method (JBMC stubbed it to nondet) still passes, but prints a one-line **footnote** naming the stubbed methods — the green verdict made honest. Acknowledge known-sound ones with `@BmcProof(allowStubs = {"java.util.Formatter.*"})` / `bmc { allowStubs = [...] }` to silence the footnote; flip `bmc { strictStubs = true }` (or `-Dbmc.strictStubs=true`) to turn any *unacknowledged* stub into **UNKNOWN** instead (see [configuration](configuration.md) + [limits](limits.md)) |
| `@BmcContractsFor(C.class)` on a test-side type with `@Requires`/`@Ensures` mirror methods | a **method contract** for `C` — proven once against the real body, then reused at call sites (see [contracts](contracts.md)) |

Uncaught runtime exceptions (null deref, array bounds, division by zero, class
cast) are checked automatically — you don't have to assert them.

## Symbolic objects

A proof method may take parameters, which JBMC treats as symbolic inputs
(objects with symbolic fields, strings via the string solver, arrays). This is how
you prove over object models — and with the optional **`bmc-constraints-jakarta`**
processor, a model's `@Min`/`@Max`/`@NotNull`/`@Size` annotations are turned into
preconditions automatically (see [`examples/integrations`](../examples/integrations)).

## Floating-point & NaN

`anyDouble()`/`anyFloat()` range over the full IEEE-754 domain, **including `NaN` and
the infinities**. Since every ordered comparison with `NaN` is `false`, an
`assume(x >= 0)` does *not* exclude `NaN`; the bounded `anyDouble(lo, hi)`/
`anyFloat(lo, hi)` forms **exclude `NaN`** by construction (the range assumption is
false for it). bmc4j discourages floating point in proofs — it slows the solver a lot;
prefer integer models where you can.
