# Configuration

```kotlin
bmc {
    unwind = 16                       // default loop bound
    parallelism = 8                   // proofs verified concurrently (default: CPU count; 1 = serial)
    timeoutSeconds = 120              // default per-proof budget (default: 0 = no timeout)
    cache = true                      // skip re-verifying unchanged green proofs (default: true)
    // externalSat = "/path/to/cryptominisat" // hand the SAT problem to an external solver
    // jbmcPath = "/opt/cbmc/bin/jbmc" // use a local binary instead of the bundled engine

    // Nondet-stub detection (default: lenient — green + footnote)
    allowStubs = ["java.util.Formatter.*"] // acknowledge known-sound stubs build-wide (silences them)
    strictStubs = false               // true: any *unacknowledged* stub -> UNKNOWN (-Dbmc.strictStubs)
    userPackages = ["com.acme"]       // your module's prefixes: a stub here is a config bug, warned loud

    // Kotlin proof parameters (default: Kotlin-type-faithful)
    kotlinNullableParams = false      // true: a @BmcProof's own non-null Kotlin parameters get the
                                      // honest-JVM null domain back — the kotlinc prologue throws on
                                      // null instead of being auto-assumed non-null. For proofs that
                                      // deliberately model hostile Java callers. Part of the
                                      // verdict-cache key. (-Dbmc.kotlinNullableParams=true)

    replayLanguage = "auto"           // language of the refutation replay scratch file:
                                      // auto (default) = .kt for a Kotlin proof class, .java otherwise;
                                      // "kotlin" / "java" force one regardless. (-Pbmc.replayLanguage)
}
```

`jbmcPath` skips the bundled engine entirely — point it at an internal mirror, a
custom build, or a binary placed on an air-gapped machine.

## Solver

Proofs run on JBMC's built-in SAT solver (MiniSat). There is **no in-engine solver
swap** — JBMC's SMT path is inert on this engine — so the one supported alternative is
handing the SAT problem to an **external solver binary**:
`bmc { externalSat = "/path/to/cryptominisat" }`. Worth trying for heavy, string-free
numeric proofs (~25% in our measurements); for everything else the bigger lever is
shrinking the symbolic range (`anyInt(lo, hi)` over `anyInt()`) or summarizing the
heavy callee with a [contract](contracts.md).

## Parallelism

Proofs are independent processes, so they run concurrently by default
(one `jbmc` per proof, on a pool sized to your CPUs). Lower `parallelism` if heavy
proofs strain memory; set `1` for serial.

## Verdict cache

A proof's deterministic verdict is a pure function of its inputs, so re-verifying a passing
proof whose inputs haven't changed buys nothing — and BMC is the expensive kind of test. By default
bmc4j caches each **expectation-matching pass** under `build/bmc4j/verdict-cache/` and skips its engine
run on the next build when nothing relevant changed, so a "nothing changed" run is near-free (in our
model-proof suite, a warm second pass runs 131 proofs in ~2s instead of ~80s). A *pass* means
`VERIFIED` for a normal proof, or `REFUTED`/`VACUOUS` for a fail-on-purpose demo whose
[`expect`](api.md) declares exactly that verdict — a refutation is as deterministic a fact as
a verification, and the demo's pass *is* the refutation. The cache key composes everything
that can change a verdict: the analysis-classpath **content** (every compiled class in the module plus
the model jars — the `expect` attribute lives in the compiled test class, so changing it invalidates
too), the effective request (`unwind`, `unwindingAssertions`, `maxStringLength`,
`timeoutSeconds`, `concurrent`, and the external-solver identity when set), the engine identity
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
