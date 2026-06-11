<!-- bmc:metadata
proofs: 15
proof-execution: 178s summed across the module (JBMC time, MiniSat; approximate). Proofs run in
  parallel, so wall-clock is far lower — this number is for spotting slow concepts, not timing the build.
-->

# Fundamentals (Java)

The core bounded-model-checking ideas, one package per concept. Each concept has a pair of
proofs: one that **fails on purpose** (JBMC finds a real defect) and one that passes (the fix
or the correct version). A failing proof *is* the result — it hands you the exact input.

Run the whole module, or one concept:

```
./gradlew :examples:fundamentals-java:test
./gradlew :examples:fundamentals-java:test --tests "proofs.arraybounds.*"
```

In your IDE, click the gutter icon next to any `@BmcProof` method to run that single proof.

## `arraybounds` — the bug a unit test misses
`GradeBand.label` maps a score 1–100 to a grade band and has a one-character off-by-one
(`score / 20 == 5` at `score == 100`). A hand-picked unit test of 1/50/99 passes; JBMC checks
**every** score at once and reports `score = 100`. The clamped `labelSafe` is proven for the
whole range. *(1 fail + 1 pass.)*

## `integeroverflow` — where intuition breaks
`Numbers.abs` overflows: `abs(Integer.MIN_VALUE)` is negative, because `-MIN_VALUE` wraps. The
proof asserts `abs(x) >= 0` and fails with the counterexample. `max` really is `>=` both
arguments, for all inputs — proven. *(1 fail + 1 pass.)*

## `nullsafety` — null is just another input
`Users.adminId` dereferences the result of a lookup that returns `null` when no admin exists.
The null is produced and consumed entirely inside the code; JBMC finds the interleaving of
field values that triggers it. The guarded `adminIdOrDefault` is safe. *(1 fail + 1 pass.)*

## `assumedomain` — `assume` defines the safe domain
`Items.elementAt(a, i)` is correct exactly when `0 <= i < a.length`. With the full bounds
assumed, the access is provably safe; drop the upper bound and `i == length` slips through.
This is how you state a precondition. *(1 pass + 1 fail.)*

## `loopsunwinding` — the unwind bound, and the safety net
`Sums.sumTo` is checked against the closed form `n*(n+1)/2`. By default bmc4j would
**auto-discover** the bound; here we pin it to show the mechanism. At `unwind = 12` the loop
(n ≤ 10) is fully covered and the proof passes; at `unwind = 4` the bound is too small — and
`--unwinding-assertions` (on by default) **reports** that instead of silently "proving" an
under-explored loop. *(1 pass + 1 fail.)*

## `enums` — proving every case is handled
`Bmc.anyOf(Suit.values())` ranges over all enum values. The buggy colour classifier forgets
SPADES, so "exactly one colour" breaks; the fixed one holds. A `switch` over the enum also
verifies — JBMC handles it soundly. *(2 pass + 1 fail.)*

## `vacuity` — when `assume` checks nothing
Contradictory assumptions (`assume(x > 0); assume(x < 0)`) — or a bound too small for the
literals (`anyString(1)` vs a 2-char literal) — leave an **empty input domain**, so the proof
"verifies" while checking nothing. bmc4j injects a reachability marker at each proof's normal
exit and fails such a proof as **VACUOUS** — *"assumptions are unsatisfiable - this proof checks
nothing"* — instead of green. The satisfiable sibling still verifies, and an
`assumeUnreachable()`-pruned `catch` path is **not** false-flagged (one normal exit stays
reachable). *(2 pass + 2 fail-on-purpose.)*

## `timeout` — the third verdict: UNKNOWN
A SAT-pathological proof (a quadratic double-loop over wide symbolic inputs at a high unwind) can't
be decided in a small budget. With `@BmcProof(timeoutSeconds = 1)` bmc4j force-kills the engine
process tree on expiry and reports **UNKNOWN** (undecided) — a distinct verdict from a refutation:
**no counterexample**, with guidance on how to make it decidable (raise unwind/timeout, shrink the
range with `assume`, split the proof, contract the heavy callee). UNKNOWN still **fails** (the
absence of a verdict is not a proof), via `BmcUndecidedError`. The tractable sibling, kept to a tight
range and small bound, finishes comfortably within a generous budget. *(1 pass + 1 fail-on-purpose.)*
