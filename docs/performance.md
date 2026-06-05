# When proofs get slow

If you're new to bounded model checking, the first surprise is usually this: a proof
that covers *every* input can take seconds — and a small change to it can take minutes
or longer. **This is normal, it is not a bug in your code, and it is tunable.** The
solve time of a proof tracks the size of the logical formula it compiles to, not the
runtime of the code — so cheap-looking code can be expensive to *prove* and vice versa.

This page is the decision tree for dealing with it.

## Why it happens

The solver explores the formula for your code over **all inputs at once**. Formula size
explodes on a few specific shapes:

- **Wide symbolic ranges** — `anyInt()` is all 2³² values; most proofs only need a few
  thousand
- **Symbolic division / modulo** — the classic SAT-expensive operation (calendar math,
  bucketing, scaling)
- **Symbolic strings** — every character is a variable; comparisons are per-character
- **Deep call graphs and loops** — everything inlines and unwinds, multiplying the above
- **Floating point** — IEEE-754 in a SAT solver is slow by construction; prefer integer
  models ([limits](limits.md))

## The levers, in order of payoff

**1. Shrink the symbolic range.** This is the big one — usually worth more than every
other lever combined. Prefer the bounded forms over `assume`-ing after the fact:

```java
int qty   = Bmc.anyInt(0, 10_000);          // not anyInt() + assume
String id = Bmc.anyAsciiString(8);          // not anyString(8) over all of UTF-16
String cc = Bmc.anyString(2, "ABCDEFGHIJKLMNOPQRSTUVWXYZ");
```

Ask: what's the smallest domain over which the property is still *meaningful*? A proof
over `[0, 10_000]` that solves in 2s beats an all-of-int proof that needs an hour — and
covers every value you'll ever see.

**2. Set a timeout, and read UNKNOWN correctly.** `bmc { timeoutSeconds = 120 }` (or
per-proof) force-kills a runaway solve and reports **UNKNOWN** — which fails the test
*distinctly*: no counterexample, nothing proven wrong, message says how to make it
decidable. A timeout is the tool telling you "tune this proof", never "your code is
broken". See [configuration → Timeout](configuration.md#timeout).

**3. Tune the unwind bound.** Loops unwind to `unwind` (default 16). If a proof's loops
genuinely need fewer iterations, lower it per-proof (`@BmcProof(unwind = 6)`) — formula
size drops fast. Too low is safe: `--unwinding-assertions` (on by default) *reports* an
insufficient bound rather than silently trusting it.

**4. Bound symbolic strings tightly.** `maxStringLength` (default 16) multiplies into
every string operation. An ISO country code needs 2; an identifier rarely needs more
than 12.

**5. Contract the heavy callee.** If one helper (recursive, loopy, arithmetic-dense)
dominates the formula at every call site, prove it *once* with a
[`@Requires`/`@Ensures` contract](contracts.md) and let callers assume the
postcondition instead of re-analyzing the body.

**6. Split the proof.** Two proofs over halves of a domain, or separate lemmas for
separate properties, often solve disproportionately faster than one combined proof.

**7. External SAT solver.** For heavy *string-free* numeric proofs,
`bmc { externalSat = "/path/to/cryptominisat" }` can shave ~25%. It's the last lever
for a reason — range reduction usually beats it.

## What you get for free

- **The verdict cache**: a passing proof whose inputs haven't changed is never
  re-solved (including a fail-on-purpose demo whose expected `REFUTED`/`VACUOUS` arrived) — a
  "nothing changed" run is near-free, so the expensive solve happens
  once per actual change ([configuration → Verdict cache](configuration.md#verdict-cache)).
- **Parallelism**: proofs verify concurrently, one engine process per proof, sized to
  your CPUs.
- **Fail-fast budgets in CI**: a per-run `-Dbmc.timeoutSeconds=180` means a
  SAT-pathological proof fails in three minutes with its name on it, instead of hanging
  the build.

## Rules of thumb

- A proof that needs **> ~60s** wants lever 1 (range), then 5 (contract), then 6 (split).
- If tightening the range feels like it weakens the proof, write the bound into the
  property's documentation — "proven for quantities up to 10,000" is a *stronger*
  statement than an hour-long proof nobody runs.
- Division/modulo over wide symbolic values is the most common single culprit — check
  there first ([coverage](coverage.md) notes the known-expensive areas).
