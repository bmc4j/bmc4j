# Performance & scaling

If you're new to bounded model checking, the first surprise is usually this: a proof
that covers *every* input can take seconds — and a small change to it can take minutes
or longer. **This is normal, it is not a bug in your code, and it is tunable.** The
solve time of a proof tracks the size of the logical formula it compiles to, not the
runtime of the code — so cheap-looking code can be expensive to *prove* and vice versa.

The good news is that the hard proofs are tractable, because bmc4j gives you a **toolbox**
for them — not one golden hammer, but a set of levers, each aimed at a different kind of
blow-up, and they **compose**. This page is how to pick the right one (and stack them).

## Why a proof gets slow (or runs out of memory)

The solver explores the formula for your code over **all inputs at once**. Formula size
explodes on a few specific shapes — and which shape you're hitting decides which lever wins:

- **Wide symbolic ranges** — `anyInt()` is all 2³² values; most proofs only need a few
  thousand. (the *interval-bound* class)
- **Symbolic multiplication / division / modulo** — the classic SAT-expensive operation;
  a symbolic-by-symbolic multiplier or a divider circuit is full bit-width. (calendar math,
  bucketing, scaling, `BigDecimal.setScale`)
- **Symbolic strings** — every character is a variable; comparisons are per-character.
- **Deep call graphs, loops, and recursion** — everything inlines and unwinds, multiplying
  the above. (the *unbounded-depth* class)
- **Floating point** — IEEE-754 in a SAT solver is slow by construction; prefer integer
  models ([limits](limits.md)).

A big formula is also a big *memory* footprint — the OOM class is the same wide-formula /
long-string blow-up seen from the RAM side, and the toolbox has levers for that too (below).

## The toolbox — which lever for which blow-up

| If the blow-up is… | reach for… | turned on with… |
|---|---|---|
| Re-running unchanged proofs | **verdict caching** | on by default (`bmc { cache = true }`) |
| Throughput (many proofs) **and** peak memory | **parallelism** (capped) | `bmc { parallelism = N }` |
| Whole-suite wall-clock | **sharding** | `-Dbmc.shard.count` / `-Dbmc.shard.index` per worker |
| A wide symbolic **interval** (one slow proof) | **domain splitting** | `Bmc.domainSplit` / `Bmc.slice` |
| **Recursion / unbounded depth** | **contracts** | `@Requires`/`@Ensures` |
| (Always available) tighten symbolic inputs | **range reduction** | `anyInt(lo, hi)`, `anyAsciiString(n)` |

They are not mutually exclusive — the last two examples below stack levers on one proof.

## Diagnosing — `@BmcProfile`

Before reaching for a lever, find out *where the time actually goes*. Annotate a slow or
timing-out proof with **`@BmcProfile`** (alongside `@BmcProof`) and bmc4j prints a per-stage
performance breakdown to the test's output — no second engine run, it summarizes the verbose
stream it already captures:

```java
@BmcProof(unwind = 64, timeoutSeconds = 4, expect = Verdict.TIMEOUT)
@BmcProfile
void heavy_proof() { ... }
```

```
  bmc4j[profile]: ...heavy_proof -> TIMEOUT - engine performance breakdown
  bmc4j[profile]:   phases (where the engine spent wall-time):
  bmc4j[profile]:       (no phase timings captured - did not reach the solver)
  bmc4j[profile]:       reached SAT/SMT solver: NO  (time was spent BEFORE solving - in symbolic execution / formula construction)
  bmc4j[profile]:   top unwound loops (method x iterations):
  bmc4j[profile]:       example.timeout.Heavy.quadraticMix  x2069
```

It is purely additive — it never changes the verdict, only emits extra output — and it works on
a **timed-out** proof (it profiles what was captured up to the kill, the most useful case). Read
it like this:

- **`reached SAT/SMT solver: NO`** → the time went into *symbolic execution / formula
  construction*, not solving. The formula never even reached the solver. Reach for the levers
  that shrink the formula *before* SAT: **range reduction**, **domain splitting**, **contracts**
  for a deep callee, a lower **unwind** bound.
- **`reached SAT/SMT solver: YES`** with a large `Solver` phase and big SAT `variables/clauses` →
  the formula is built but hard to *solve*. Reach for the **fast solver** (numeric/boolean
  proofs) or shrink the symbolic operations driving the clause count.
- The **top unwound loops** list pinpoints the hot method — the one whose loop body dominates
  unwinding (e.g. `…quadraticMix ×2069`). That is where range reduction / a contract pays off
  most.

Default off: only `@BmcProfile`-annotated proofs produce it, so the normal run is unaffected.

### 1. Verdict caching — *skip the engine entirely on unchanged proofs*

**What it does.** A proof that passed with a deterministic verdict (`VERIFIED`, or the
expected `REFUTED`/`VACUOUS` for a fail-on-purpose proof) and whose inputs are unchanged is
**not re-solved** — it's reported passed straight from `build/bmc4j/verdict-cache/`. The key
is the proof's *reachable cone* (its transitively-reached classes + flags + engine/runtime
semantics), so an unrelated edit elsewhere in the module no longer invalidates it.

**Blow-up it addresses.** Re-runs / unchanged code — the most common "slow" case in practice
is re-proving things nothing touched. A "nothing changed" build is near-free.

**Turn it on.** On by default. `bmc { cache = false }` (or `-Dbmc.noCache=true`) forces full
re-verification; `gradlew clean` clears it (the cache lives under `build/`). Only
expectation-matching passes are ever cached — failures always re-run live, and
`TIMEOUT`/`UNKNOWN` are never cached (machine-speed dependent).

### 2. Parallelism — *throughput, and a cap on peak memory*

**What it does.** Proofs are independent, each spawning its own `jbmc` process, so they run
**concurrently** on a fixed pool sized to `bmc { parallelism }`. A JVM-wide semaphore
(`JbmcConcurrency`) bounds the **total** live `jbmc` processes to that same number — so a
[domain-split](#4-domain-splitting--for-interval-bound-blow-ups-and-memory) proof's fan-out
can't blow past the cap either.

**Blow-up it addresses.** Two at once: **throughput** (near-linear with cores) **and peak
memory**. Each live `jbmc` holds its own formula in RAM, so capping concurrency directly caps
concurrent memory — lower `parallelism` (down to `1`) when heavy proofs strain RAM, and the
semaphore guarantees even a wide fan-out respects it (the lesson behind the cap: a 50-slice
split on a 4-wide machine must still run 4 at a time, not 50).

**Turn it on.** `bmc { parallelism = N }` (default = available processors; `1` = serial). The
underlying concurrency budget can be overridden in a test run with `-Dbmc.parallelism=N`.

### 3. Sharding — *split the suite across N workers*

**What it does.** Runs 1/N of the proof suite per worker, so a suite can be spread across as
many machines/jobs as you have. A `PostDiscoveryFilter` in `bmc-runtime` selects each shard's
slice, balanced at the *method* level by a hash of each test's id. Because the verdict cache is
content-keyed, the shards' caches are **mergeable by union** — unioning every shard's cache
into one snapshot that every shard restores next run, so each shard starts from *every* shard's
proven proofs and only re-solves its own slice plus whatever changed.

**Blow-up it addresses.** Whole-suite wall-clock. Proofs are embarrassingly parallel (per-proof
process cost dominates), so more workers shrink a leg near-linearly — down to a floor of the
slowest single proof + setup, past which making slow proofs cheaper is what helps.

**Turn it on.** Set `-Dbmc.shard.count` / `-Dbmc.shard.index` per worker (count `1` =
unsharded). Pin a known-slow proof or class with
`@org.bmc4j.Shard(N)` so the expensive ones spread one-per-shard instead of hash-clustering.

### 4. Domain splitting — *for interval-bound blow-ups (and memory)*

**What it does.** Partition a slow proof's claimed input domain into N slices that the engine
verifies **independently and in parallel**, plus one **soundness cover check**
(`overall ⇒ slice₁ ∨ … ∨ sliceₙ`) that fails loud if the slices leave a gap — so a split can
never produce a false green. A refuting slice surfaces its counterexample and cancels the rest
(early-exit), turning a long monolithic solve into a fast counterexample.

**Blow-up it addresses.** The **interval-bound** class — a wide symbolic range that makes the
monolithic formula huge. Each slice ranges over a fraction of the interval, so its SAT search
is a fraction of the whole. And because **N slices = N smaller formulas**, each slice's *peak
memory* is a fraction of the monolith's — so a split is also a direct **memory** lever against
the wide-formula OOM class. It's the lever that lets you **reclaim the wide range** you'd
otherwise have to give up to range reduction (below).

**Turn it on.** Markers in the proof body — one `domainSplit` per proof:

```java
int x = Bmc.anyInt();
Bmc.domainSplit(x >= 0 && x <= 100_000);   // the claimed domain
Bmc.slice(x >= 0      && x < 25_000);
Bmc.slice(x >= 25_000 && x < 50_000);
Bmc.slice(x >= 50_000 && x <= 100_000);
Bmc.check(property(x));                      // body runs once per slice
```

```kotlin
domainSplit(x in 0..100_000) {               // Kotlin sugar — lowers to the same markers
    slice(x in 0 until 25_000)
    slice(x in 25_000 until 50_000)
    slice(x in 50_000..100_000)
}
```

**In practice.** A proof that multiplies two symbolic ints over a wide range may have to be
*range-reduced* just to clear the budget. Splitting it into contiguous sub-range bands lets
each slice solve a narrow interval independently — fast enough that the **wide range can be
restored**, making the proof *strictly stronger* than the reduced one. The lever is per-slice
*interval size*, not operand bit-width (equal-width bands solve in the same time at any
magnitude), so a plain contiguous sub-range split is the right shape.

### 5. Contracts — *for recursion / unbounded depth*

**What it does.** `@Requires`/`@Ensures` method contracts (declared in your tests via
`@BmcContractsFor`) make proofs **modular**: prove a method's postcondition *once*, then let
every caller **assume** it instead of re-analysing — and re-unwinding — the body. That's
induction over the call boundary: a recursive or deeply-nested helper is **summarized**, not
unwound at every site.

**Blow-up it addresses.** Recursion and otherwise-unbounded depth — the case where a caller's
`unwind` bound would have to be large enough to cover a loopy/recursive callee at every call
site. See [docs/contracts.md](contracts.md) for the full annotation surface (Kotlin-first, incl.
`suspend` functions).

```java
@BmcContractsFor(Recursive.class)
interface SumToContract {
    @Requires("inRange") @Ensures("closedForm") int sumTo(int n);
    // static boolean predicates inRange(n) / closedForm(result, n)
}
```

### Range reduction — the baseline lever, always available

Before any of the above, ask: **what's the smallest domain over which the property is still
meaningful?** Prefer the bounded symbolic forms over `assume`-ing after the fact:

```java
int qty   = Bmc.anyInt(0, 10_000);     // not anyInt() + assume
String id  = Bmc.anyAsciiString(8);     // not anyString(8) over all of UTF-16
```

Identity / round-trip / cap laws hold at *any* range, so a tight range is **just as strong a
proof** — and wide-range confidence already rides the differential (vs-JDK) axis. A tight
range that solves quickly beats an all-of-int proof that needs an hour and still covers every
value you'll ever see. The one thing that's changed: **domainSplit now lets you reclaim the
wide range** where you previously had to reduce it — so range reduction is the cheap default,
and a split is the upgrade when the wide range genuinely matters.

## The levers compose

The point of a toolbox is that you stack levers, because they hit *different* parts of the
blow-up:

- **Sharding × caching** — sharding splits the suite across workers; the content-keyed cache
  makes the shards' results mergeable, so the union is sound and each shard starts warm.
- **Parallelism × domainSplit** — the same concurrency budget bounds both normal proofs and a
  split's fan-out, so you get throughput *and* a memory cap without the fan-out overrunning it.
- **Caching × everything** — once a proof is proven, every later run (sharded or not) skips it.

## A note on memory (OOM)

Peak-memory blow-ups are the wide-formula / long-string class seen from the RAM side, and two
levers address them honestly:

- **Cap parallelism.** Each live `jbmc` holds its own formula; the `JbmcConcurrency` semaphore
  bounds total concurrent `jbmc` to `parallelism`, so lowering it directly caps concurrent
  memory. This is a *bound*, not a shrink — it trades throughput for a RAM ceiling.
- **Domain-split the offender.** N slices = N **smaller** formulas, so each slice's peak is a
  fraction of the monolith's. This actually *shrinks* the per-process footprint, not just
  bounds the count.

What this does **not** do: make a single fundamentally-huge formula fit. If one slice is still
too big, it needs range reduction or a contract — splitting and capping bound and divide the
memory, they don't conjure it.

## Rules of thumb

- A proof that needs **> ~60s**: try range reduction first (or domainSplit to keep the wide
  range), then a contract for a heavy callee.
- Set a **`timeoutSeconds`** budget so a SAT-pathological proof fails as a named **UNKNOWN** in
  bounded time instead of hanging the build (CI runs `-Dbmc.timeoutSeconds=300`). UNKNOWN is the
  tool saying "tune this proof", never "your code is broken" — see
  [configuration → Timeout](configuration.md#timeout).
- **Pin the unwind bound** per-proof (`@BmcProof(unwind = N)`) on perf-sensitive proofs. By
  default `unwind` is `AUTO` — bmc4j climbs to the smallest sufficient bound, which is convenient
  but pays for the search (several solves per proof on a cold run). Pinning the bound it reports
  runs a single solve at that bound; too low is *safe* (`--unwinding-assertions` reports an
  insufficient bound rather than trusting it). For a large, repeatedly-verified suite that's the
  difference, not a micro-optimization.
- Symbolic-by-symbolic **multiply / divide / modulo** over wide values is the most common single
  culprit — check there first ([coverage](coverage.md) notes the known-expensive areas).

## The fast solver (for numeric/boolean proofs)

bmc4j ships a **bundled fast SAT solver** ("kissat") that is typically several times faster than the
default on **numeric/boolean proofs that don't touch text**. You don't install anything — it comes
with the engine.

Turn it on for a single proof, or for the whole project:

```kotlin
// per proof — the few that are slow and text-free
@BmcProof(solver = "kissat")
void wide_division_is_in_range() { ... }
```
```kotlin
// project default — every proof tries the fast solver where it's safe
bmc { solver = "kissat" }
```

Resolution precedence: per-proof `@BmcProof(solver)` > project `bmc { solver }` > the global
`bmc { externalSat = "kissat" }` / `-PsatPath` escape hatch (the benchmark uses the last one).

**It applies to text-free proofs only — and bmc4j enforces that for you.** The fast solver works by
turning off the engine's text/String reasoning, which is sound for numbers and booleans but *not* for a
proof that reasons about strings. So bmc4j classifies each proof first and runs the fast solver **only**
on proofs it can prove text-free; every other proof uses the default solver automatically. You never
have to know which proofs are which.

If you ask for the fast solver on a proof that *does* use text, the default is to **fail loud** with a
plain-language message, so you notice and tune the right proofs. If you'd rather it just quietly use the
default solver for those proofs (a sound result, no speedup) while the text-free proofs still get the
fast solver, set:

```kotlin
bmc { solver = "kissat"; externalSatStringFallback = true }
```

There is **no** mode that runs the fast (text-reasoning-off) solver on a text proof and reports it
verified — a pass you get is always sound. On a platform with no bundled fast solver (e.g. Windows),
a request for it transparently falls back to the default solver.

Note the SMT path (`solver = "z3"` etc.) is **inert on this engine** for most proofs — the fast SAT
solver above is the lever that actually works. Range reduction still beats a solver swap when a proof is
genuinely SAT-hard; reach for the fast solver when the formula is fine and you just want it faster.
