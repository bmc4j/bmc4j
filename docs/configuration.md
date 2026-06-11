# Configuration

```kotlin
bmc {
    // unwind defaults to AUTO: each proof auto-discovers its loop bound (no tuning needed).
    // unwind = 12                    // PIN one fixed bound for every proof (the expert opt-out)
    // unwindCap = 16                 // highest bound auto-discovery climbs to before UNKNOWN (default 16)
    parallelism = 8                   // proofs verified concurrently (default: CPU count; 1 = serial)
    timeoutSeconds = 120              // default per-proof budget (default: 0 = no timeout)
    cache = true                      // skip re-verifying unchanged green proofs (default: true)
    // jbmcPath = "/opt/cbmc/bin/jbmc" // use a local binary instead of the bundled engine

    // Nondet-stub detection (default: lenient — green + footnote)
    allowStubs = ["java.util.Formatter.*"] // acknowledge known-sound stubs build-wide (silences them)
    strictStubs = false               // true: any *unacknowledged* stub -> UNKNOWN (-Dbmc.strictStubs)
    userPackages = ["com.acme"]       // your module's prefixes: a stub here is a config bug, warned loud

    // Deliberately out-of-scope packages (declare whole areas bmc4j won't model)
    notModeledPackages {              // a reach into one of these = a LOUD out-of-scope (declared) UNKNOWN
        +"javax.swing.*"              // recursive glob: covers subpackages too (java.nio.* ⊇ java.nio.file)
        +"java.sql.*"                 // the registry WINS: a class bmc4j models is still modeled, never waived
    }

    // Kotlin proof parameters (default: Kotlin-type-faithful)
    kotlinNullableParams = false      // true: a @BmcProof's own non-null Kotlin parameters get the
                                      // honest-JVM null domain back — the kotlinc prologue throws on
                                      // null instead of being auto-assumed non-null. For proofs that
                                      // deliberately model hostile Java callers. Part of the
                                      // verdict-cache key. (-Dbmc.kotlinNullableParams=true)
}
```

`jbmcPath` skips the bundled engine entirely — point it at an internal mirror, a
custom build, or a binary placed on an air-gapped machine.

## Automatic unwind discovery

By default a proof with no explicit `unwind` **auto-discovers** its loop bound: bmc4j runs the engine
at increasing bounds and stops at the smallest one that yields a conclusive verdict, so a beginner
never has to understand loop unwinding or decode a cryptic out-of-memory. `--unwinding-assertions`
stays on throughout, so an under-unwind can only fail closed to `UNKNOWN`, never a false `VERIFIED`.
When it lands, the discovered bound is reported in the log and the structured summary —
`auto-unwind: discovered unwind=N — pin with @BmcProof(unwind = N) to skip the search.` — and cached,
so steady-state runs go straight to that bound with no extra solves.

- **Opt out per proof:** `@BmcProof(unwind = N)` pins `N` (no search) — the expert override.
- **Opt out project-wide:** `bmc { unwind = N }` (or `-Dbmc.unwind=N`) pins one fixed bound for every
  proof.
- **Cap:** `bmc { unwindCap = N }` (default 16) bounds the climb; reaching it without a conclusive
  verdict reports a clear `UNKNOWN` ("this proof may have an unbounded / too-deep loop — set an
  explicit `unwind`") instead of climbing forever.

## Solver

Proofs run on the engine's built-in SAT solver (MiniSat) by default. For **numeric/boolean proofs
that don't touch text**, a **bundled fast SAT solver** (`solver = "kissat"`) is typically several times
faster — it ships with the engine, so there's nothing to install:

```kotlin
@BmcProof(solver = "kissat")          // per proof
void wide_division_is_in_range() { ... }

bmc { solver = "kissat" }             // project default
```

Precedence: per-proof `@BmcProof(solver)` > project `bmc { solver }` > the global
`bmc { externalSat = "kissat" }` / `-PsatPath` escape hatch.

The fast solver works by turning off the engine's text/String reasoning, so it is **only sound for
text-free proofs**. bmc4j enforces this for you: it classifies each proof and runs the fast solver
**only** on proofs it proves text-free, using the default solver for the rest automatically — no
knowledge required. Asking for the fast solver on a proof that *does* use text **fails loud** by default
(a plain-language message); set `bmc { externalSatStringFallback = true }` to instead silently fall back
to the sound default solver for those proofs while text-free proofs still get the speedup. There is no
mode that runs text-reasoning-off on a text proof and reports it verified — every pass is sound. On a
platform with no bundled fast solver (e.g. Windows), the request transparently uses the default solver.

The SMT path (`solver = "z3"` etc.) is **inert on this engine** for most proofs. When a proof's solve
time blows up, the levers are shrinking the symbolic range (`anyInt(lo, hi)` over `anyInt()`),
[splitting the domain](performance.md#4-domain-splitting--for-interval-bound-blow-ups-and-memory),
or summarizing the heavy callee with a [contract](contracts.md) — those beat a solver swap when a proof
is genuinely SAT-hard. See [performance → the fast solver](performance.md#the-fast-solver-for-numericboolean-proofs).

## Parallelism

Proofs are independent processes, so they run concurrently by default
(one `jbmc` per proof, on a pool sized to your CPUs). Lower `parallelism` if heavy
proofs strain memory; set `1` for serial.

## Verdict cache

A proof's deterministic verdict is a pure function of its inputs, so re-verifying a passing
proof whose inputs haven't changed buys nothing — and BMC is the expensive kind of test. By default
bmc4j caches each **expectation-matching pass** under `build/bmc4j/verdict-cache/` and skips its engine
run on the next build when nothing relevant changed, so a "nothing changed" run is near-free — a
warm second pass skips the engine entirely and finishes in a fraction of the cold time. A *pass* means
`VERIFIED` for a normal proof, or `REFUTED`/`VACUOUS` for a fail-on-purpose demo whose
[`expect`](api.md) declares exactly that verdict — a refutation is as deterministic a fact as
a verification, and the demo's pass *is* the refutation. The cache key composes everything
that can change a verdict: the analysis-classpath **content** (every compiled class in the module plus
the model jars — the `expect` attribute lives in the compiled test class, so changing it invalidates
too), the effective request (`unwind`, `unwindingAssertions`, `maxStringLength`,
`timeoutSeconds`, and — when the fast solver is in play — the resolved fast-solver binary identity
**and** whether the run used text/String reasoning, so a fast-solver verdict is never served for a
default-solver request or vice versa), the engine identity
(bundled engine version, or a content hash of an explicit `jbmcPath` binary), and the bmc4j
runtime's analysis-semantics version (its bytecode-rewrite layer). Change any of them — edit a class, bump `unwind`, swap the engine — and the affected verdicts
re-run. It is deliberately coarse and **biased toward over-invalidation**: a *stale pass is a soundness
bug*, so the cache stores the verdict **fact** and the expectation is re-judged on every run —
**failures are never cached** (any expectation mismatch always comes from a live engine run, with a
fresh counterexample and no flaky failure pinned), and **`TIMEOUT`/`UNKNOWN` are never cached even when
expected** (a timeout is a function of machine speed, not of the inputs — a cached "TIMEOUT, as
expected" on a faster machine would hide the drift the expectation exists to catch). Any error reading
or writing the cache is fail-open (the proof just runs). One side effect: a cached `REFUTED` demo pass
does not re-render the counterexample replay file — delete the entry or run with `-Dbmc.noCache=true`
to regenerate it. The cache lives under `build/`, so `gradlew clean`
clears it and it is never committed. Disable it with `bmc { cache = false }` or `-Dbmc.noCache=true` to
force full re-verification.

## Nondet stubs

JBMC stubs any method it has no body for to a nondeterministic result — which is
*silently unsound*, not just imprecise (see [limits](limits.md)). bmc4j detects this and makes it visible.
On **every** run it harvests the methods the analyzed slice stubbed (filtering out bmc4j's own models,
the bundled core-models, and engine synthetics, so the list is signal), then applies a **policy**:

- **Default (lenient): green + footnote.** A verified proof that relied on an *unacknowledged* stub
  still passes, but prints a one-line footnote naming the stubbed methods — the green verdict, made
  honest. This never breaks a currently-green proof; a fully-modeled proof prints nothing.
- **Acknowledge what you've reasoned about.** `@BmcProof(allowStubs = {"java.util.Formatter.*"})`
  (per proof) and `bmc { allowStubs = [...] }` (build-wide) silence specific methods — exact
  (`a.b.C.m`), class-wide (`a.b.C.*`), or package-wide (`a.b.*`). What's still in the footnote is
  then something nobody has consciously signed off on. (Nondet is *conservative* for a pure value the
  proof only ranges over — green is sound, only spurious reds are possible — so acknowledging is often
  the right call; the unsound cases are "doesn't throw" proofs and dropped side effects.)
- **Strict: any unacknowledged stub → UNKNOWN.** `bmc { strictStubs = true }` / `-Dbmc.strictStubs=true`
  turns an unacknowledged stub into the **UNKNOWN** verdict (`BmcUndecidedError`) — nothing was proven
  wrong, the verdict just isn't trustworthy — listing the stubs and the three exits (model it in
  `bmc-models`/`src/bmcModel`, `allowStubs` it, or restructure so it isn't reached). No new verdict
  type: three verdicts + a footnote.
- **Your own code is loud.** A stub from one of `bmc { userPackages = [...] }` (your module under test)
  is almost always a missing-dependency *config bug*, not a JDK modeling gap — so it's warned loudly
  even in lenient mode.

The harvested stub list is stored in the verdict-cache entry, so the policy is judged at cache **read**
time: flipping `strictStubs` or editing `allowStubs` re-decides from the stored fact **without** an
engine re-run (the cache key is unchanged). The **`bmcStubReport`** task aggregates the harvested stubs
across the suite into a ranked "most-hit unmodeled methods" list — a data-driven `bmc-models` backlog.
See [`examples/stdlib`](../examples/stdlib) (the `stubs` concept).

## Deliberately out-of-scope packages

`bmc { notModeledPackages { ... } }` declares whole packages **deliberately out of scope for
modeling** — the vast un-modeled remainder of the stdlib bmc4j has no stand-in for. It turns a reach
into one of those areas from a silent gap into an intentional, reviewable decision, and lets the audit
assert *completeness* (every reached class is modeled **or** declared out of scope).

```kotlin
bmc {
    notModeledPackages {
        +"javax.swing.*"
        +"java.sql.*"
        +"java.nio.file.*"
    }
}
```

- **Glob semantics — recursive.** A glob covers the named package **and all subpackages**: `java.nio.*`
  (or the bare `java.nio`) matches `java.nio.ByteBuffer` **and** `java.nio.file.Path` — a subpackage of
  an out-of-scope area is itself out of scope. There is no exact-package-only form.
- **Precedence — the registry wins.** A waiver applies only to a class bmc4j does **not** otherwise
  model. A modeled class inside a declared package is still the model; the waiver never demotes it.
- **Loudness — a waiver classifies, it never suppresses.** Reaching a declared-package class still
  produces a **LOUD, member-named `out-of-scope (declared)` UNKNOWN** — never a silent nondet stub and
  never a path to a false `VERIFIED`. The reason text is **distinct** from a generic unmodelled-member
  gap, so a reviewer can tell *"deliberately declined"* from *"model gap not yet filled"*. A proof can
  opt a member back into footnoted-nondet with `@BmcProof(acknowledgeUnmodelled = …)`, exactly as for an
  unmodelled member.

This is the package-grain **completeness ratchet**: a newly-reached, *undeclared* package becomes a
build failure (a deliberate decision point — model it, or declare the package), and removing a glob
re-surfaces its classes as failures. Overridable with `-Dbmc.notModeledPackages` (comma-separated globs).

## User models (declared intent + provenance)

You can supply your own JBMC models — both to fill a JDK coverage gap and, deliberately, to encode a
**domain constraint that diverges from the JDK** ("keys never collide", "lists bounded to 32", "no DST
crossings"). A class under `src/bmcModel/` (same fully-qualified name as a real one) shadows it on JBMC's
*analysis* classpath only — it is compiled but kept off the test runtime, so the real class still runs
when tests execute.

A **domain model is [`Bmc.assume()`](api.md) at classpath altitude**: legitimate, but invisible at the
proof site — so the requirement isn't JDK fidelity, it's **declared intent + provenance on the verdict**.
Mirror of the [nondet-stub ladder](#nondet-stubs) (footnote → warn → strict):

```kotlin
bmc {
    models {
        conformant("acme.FastList")                                   // claims JDK fidelity
        domain("acme.NoCollisionMap", "keys are UUIDs, collision-free")  // intentional divergence
    }
    strictModels = false   // true: a present-but-undeclared user model -> UNKNOWN (-Dbmc.strictModels)
}
```

- **Provenance footnote.** A green proof whose analysis classpath included a registered user model
  *names it* — and for a `domain` model appends the rationale: *"VERIFIED under user model(s): domain
  model `acme.NoCollisionMap` (assumes keys are UUIDs, collision-free)"*. The green verdict, made honest
  about what it rests on.
- **Override warning.** Shadowing a bundled/verified model (a `java.*`/`javax.*`/`jdk.*`/`kotlin.*`/
  `kotlinx.*` class bmc4j already [verifies](model-soundness.md)) warns loudly even in lenient mode —
  you've replaced a *checked* stand-in with an unchecked one.
- **Strict: an undeclared override → UNKNOWN.** `bmc { strictModels = true }` / `-Dbmc.strictModels=true`
  turns a model present under `src/bmcModel` but with *no* declared intent into the **UNKNOWN** verdict —
  no proof silently rests on an undeclared override. Declared models stay green.
- **Conformant models are verifiable like bundled ones.** A `conformant` model claims JDK fidelity, so it
  can run the same [differential-vs-JDK + laws-under-the-engine](model-soundness.md) conformance harness
  as the bundled models — a passing one earns a plain green and a clean upstreaming path into `bmc-models`.

**Granularity (be honest):** relevance is *"the user model was on this proof's analysis classpath"*, not
*"provably called by this proof"* — JBMC emits a which-method-was-stubbed report (that's what the stub
footnote uses) but no which-model-body-was-linked report, so the footnote is classpath-scoped and can
over-attribute a model to a proof in the same module that didn't actually touch it.

> **Vacuity caveat.** A domain model is an assumption, and an *over-restrictive* one can make proofs
> **trivially green** — exactly like an over-tight `assume(...)`. The automatic
> [vacuity check](limits.md) only catches the **empty-domain extreme** (a model that admits *no* reachable
> behavior at all); a model that merely over-narrows the domain (e.g. "this map holds at most one key")
> will pass proofs that wouldn't hold for the real class, and reachability won't flag it. Keep a domain
> model's rationale honest, and prefer the *weakest* constraint that captures your real invariant.

## Timeout

A SAT-pathological proof can run for a very long time. Set a per-proof
wall-clock budget — `bmc { timeoutSeconds = N }` (build default), `-Dbmc.timeoutSeconds=N`
(command line, wins over the build default), or `@BmcProof(timeoutSeconds = N)` (per proof,
wins over both). On expiry the engine **process tree is force-killed** (the solver is a child
of `jbmc`) and the proof is reported **UNKNOWN** rather than hanging the build. Default is `0` =
no timeout. The expiry is structurally flagged as the **TIMEOUT** flavour of UNKNOWN, so a
fail-on-purpose demo can assert it precisely with `@BmcProof(expect = Verdict.TIMEOUT)` —
a solver crash won't satisfy it (see [api.md](api.md)).
