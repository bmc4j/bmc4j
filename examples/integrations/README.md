<!-- bmc:metadata
proofs: 16
proof-execution: 432s summed across the module (JBMC time, MiniSat; approximate). Proofs run in
  parallel, so wall-clock is far lower — this number is for spotting slow concepts, not timing the build.
-->

# Integrations

Wiring bmc4j to real-world inputs: validation annotations, deployment config, and dependencies
JBMC can't see through. One package per concept (the Kotlin custom-models concept lives in
`src/.../kotlin`).

```
./gradlew :examples:integrations:test
./gradlew :examples:integrations:test --tests "proofs.config.*"
```

## `jakarta` — annotations become proof preconditions

If you annotate a model with Jakarta Bean Validation, those annotations already describe the
inputs your system accepts. The `bmc-constraints-jakarta` annotation processor generates a
reflection-free `assumeValid(obj)` from those annotations, so a proof over a **symbolic** model
parameter checks "my code handles every input my validation layer admits."
**The bug:** `@Max(120)` admits age 120, but `group()` indexes a 4-bucket array at `age / 30 == 4`.

The generated `assumeValid` covers a broad slice of `jakarta.validation.constraints.*`:

- **Numeric / size / boolean / null** — `@Min`/`@Max`/`@Positive…`/`@Size`/`@NotEmpty`/`@Null`/
  `@AssertTrue`/`@AssertFalse` (see `User`).
- **Temporal** (`Event`) — `@Past`/`@PastOrPresent`/`@Future`/`@FutureOrPresent` over the modeled
  `java.time` types. All temporal fields share ONE symbolic "now" (the validation moment), so e.g.
  `signupAt <= now < expiry` is enforced relative to the *same* instant. A second
  `assumeValidAt(obj, now)` overload lets a proof pin that moment.
- **Decimal** (`Money`) — `@DecimalMin`/`@DecimalMax` (honoring `inclusive`) and `@Digits` over the
  modeled `BigDecimal`.
- **`@NotBlank`** (`Customer`) — non-null AND not all-whitespace. Unlike the numeric constraints,
  `@NotBlank` *rejects* null (the jakarta asymmetry).
- **Cascading `@Valid`** (`Customer` → `Address`, self-referential `Node`) — a `@Valid` bean field
  recurses into the nested bean's own generated constraints (null-guarded). Cyclic bean graphs are
  explored to the proof's unwind depth (a recursive `assumeValid` is bounded by JBMC's unwind, like
  every loop in the tool).
- **Container elements** (`Order`) — Jakarta 3.0 constraints live *inside* generics:
  `List<@Min(1) Integer>` and `List<@Valid OrderLine>` become bounded element loops. The loop bound
  is the field's `@Size(max=...)` when present, else a default cap (surfaced as a processor NOTE) —
  the first thing to add when an element proof gets slow.

The `null` value passes every constraint except `@NotNull`/`@NotBlank`, so the generated assume keeps
valid-`null` objects in the proof domain. Unmodeled surfaces (regex `@Pattern`/`@Email`,
`ZonedDateTime`, `Map` element constraints) are skipped with a processor NOTE, never silently.

## `jakartakt` — the same, for a **Kotlin** DTO (no Java mirror class)

A `data class Req(@field:Min(1) val qty: Int, …)` is a proof source directly: the
`bmc-constraints-jakarta` **KSP** processor reads the Jakarta annotations off the Kotlin declarations
and generates the identical `ReqConstraints.assumeValid(Req)` helper the Java path generates — so you
no longer write a Java mirror of a Kotlin request type. The plugin wires this automatically for any
Kotlin consumer (the Java DTO path on `annotationProcessor`/`testAnnotationProcessor` is unchanged and
both coexist). **Use the `@field:` use-site target** (`@field:Min(1)`) — that is what validation
libraries resolve, and what the processor reads first; `@param:` and bare annotations are also
honored. A non-nullable `Int` lowers to a primitive (un-guarded bound); a nullable `Int?` is boxed and
its bound is null-guarded, exactly as a Java `Integer` field. *(3 proofs: the `assumeValid` bounds
hold, a divide stays safe, and a valid-`null` boxed field still refutes an unguarded unbox.)*

## `config` — pinned to the run's real values

`Bmc.intFromProperty("app.port")` / `intFromEnv` / `boolFromProperty` / `doubleFrom…` /
`stringFrom…` read the **actual** value this run was launched with and pin the proof input to it —
so you verify against *this* deployment's config (for "every value", use `anyInt()` directly).
bmc4j resolves each value at analysis-setup time (in the test JVM, which has the real environment)
and bakes it in as a constant; a required-but-unset (or unparseable) variable fails the proof.
Booleans must be exactly `true`/`false` (case-insensitive) — `1`/`yes` fail rather than silently
reading as `false`. Keys must be string literals. **The bugs:** this run's `app.budgetKb` overflows
`int` when doubled; `app.timeoutMs` isn't set, so the required-config proof reports it rather than
silently passing; `app.legacyFlag=1` is malformed, so its proof refuses to guess. *(4 pass + 3 fail.)*

## `custommodels` (Java) & `custommodelskt` (Kotlin) — un-analyzable dependencies

Real code calls things a model checker can't see: network/DB clients, the clock, native methods.
Write a small **model** with the same fully-qualified name in `src/bmcModel/` — the plugin compiles
it (against `Bmc`, so it can return `Bmc.anyInt(...)`) and **prepends it to JBMC's analysis
classpath**, shadowing the real class during verification only (never on the test runtime
classpath). Here a `Pricer` depends on a live `ExchangeRates` service that throws in `src/main`;
the model returns a *symbolic* rate, so the proof holds for **every** rate at once. Delete
`src/bmcModel` and the proofs fail on the real `UnsupportedOperationException`. Shown in both Java
(`custommodels`) and Kotlin (`custommodelskt`).

A model is real bytecode on the analysis classpath, so it goes through the **same soundness rewrite
passes** as your proof code: `String` content ops, string concatenation, record/lambda/pattern-switch
`invokedynamic`, and the integer `Math.*` helpers are all desugared inside the model exactly as they
are in `src/main`/`src/test`. So a faithful, real-looking model (e.g. one that branches on
`String.equals` or computes with `Math.floorDiv`) is analyzed soundly, not against JBMC's nondet
defaults — see `TaxPolicy` and `TaxPolicyProofTests`, whose proofs *refute* if the model is left
unrewritten and *verify* once it is. The override still wins by classpath order: your model shadows
the bundled `bmc-models`, the Kotlin models, and JBMC's core-models. The flip side is that a
model carries its **own soundness burden** — it is analysis-only (the real class still runs in the
JVM), the conformance harness doesn't cover it, and stub detection won't flag it (the method now has
a body). Differential-test a model against the real class the same way the bundled models are tested.
*(7 pass.)*

## `conformkt` (Kotlin) — conform a model against the **real implementation**

The models above stand in for code JBMC *can't* see, so their real leg is intractable on purpose and
you simply don't conform them. But when a model shadows a class that IS analyzable, you can hold the
model honest automatically with **`@ConformProofsAgainstModel(SomeClass::class)`**: it runs each
annotated `@BmcProof` **twice** —

- the **model leg** — the normal run, with the `src/bmcModel` model substituted; and
- the **real leg** — the same proof with that model *excluded*, so the **real** class is analysed.

**Both legs must reach the proof's expected verdict** or the proof fails, naming the leg. An unsound
model — one that VERIFIES a property the real class would REFUTE — fails the real leg and is reported
as UNSOUND. That is the whole semantics: there's no verdict-diff to reason about, failing either leg
fails the proof.

`Volume` is a tiny clamp-into-`0..100` helper carrying its **real, loop-free** implementation (no
service call to model away), and its `src/bmcModel/kotlin` model is a faithful copy. Because the real
`Volume.adjust` is just as tractable as the model, both legs of each proof verify: the clamp bound
holds for every (overflow-prone) delta, and an in-range step is applied exactly. Drop the model's
overflow-safe `Long` arithmetic and the real leg would surface the divergence. *(2 pass, each ×2
legs.)*
