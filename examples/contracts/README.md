<!-- bmc:metadata
proofs: 13
proof-execution: 281s summed across the module (JBMC time, MiniSat; approximate). Proofs run in
  parallel, so wall-clock is far lower — this number is for spotting slow concepts, not timing the build.
-->

# Contracts

Method contracts (`@Requires`/`@Ensures`) declared test-side with `@BmcContractsFor`, so
production code carries no bmc references. The processor turns each contract into three things: a
**replace-stub** (callers reuse the summary instead of re-analyzing the body), an **enforce
proof** (discharges `@Ensures` against the real body), and a manifest entry. A contract can only
be *reused* once it has been *discharged* — and the discharge is automatic. The low per-proof
bounds that make the "contracts beat inlining" point are set with `@BmcProof(unwind = …)`; the
enforce proofs summarize self-recursion via the contract, so they hold at the default bound.

```
./gradlew :examples:contracts:test
./gradlew :examples:contracts:test --tests "proofs.stacking.*"
```

## `basics` — the basic shape

`Triangle.triangle(n)` is a loop, costly to inline. With a contract, a caller at `unwind = 2`
reuses `@Ensures result >= 0` instead of unrolling the loop and passes; the identical
`TriangleNaive` (no contract) must inline and overruns the bound — which reports **UNKNOWN**
("bound too small": truncated exploration is incompleteness, not a counterexample). Same code,
same bound, provable only with the summary. *(1 pass + 1 undecided-on-purpose.)*

## `recursion` — recursion as induction

`@Ensures` is the loop invariant in closed form. When the enforce proof analyzes
`Recursive.sumTo`, the recursive call is summarized by this same contract — so the proof is
exactly the inductive step (assume it for `n-1`; show it for `n`), not a full unroll. The
no-contract baseline must unroll all 12 levels and overruns the bound (**UNKNOWN**, the recursion
flavour of bound-too-small). *(1 pass + 1 undecided-on-purpose.)*

## `stacking` — additive cost, not multiplicative

The payoff: three recursive functions chained `f → g → h`, each to depth 10. Inlined, that's an
unwindable-at-no-modest-bound stack. With contracts, each level is proven using the contract of
the level below — cost is additive in the number of contracts, not multiplicative with depth and
chaining. A proof reaches only one contract deep. *(2 pass + 1 undecided-on-purpose; plus 3 green
enforce proofs.)*

## `vacuity` — an empty precondition checks nothing

`@Requires("impossible")` with `impossible(x) = x < 0 && x > 0` is **unsatisfiable**, so the
generated `enforce__clamp` would discharge its `@Ensures` over an empty domain — passing
vacuously and blessing a summary that was never actually checked, which then weakens every caller
that reuses it. The vacuity guard (a reachability marker injected into each enforce proof)
catches it: `enforce__clamp` fails as **VACUOUS** — *"assumptions are unsatisfiable - this proof
checks nothing"*. This is the highest-value place for the check. *(1 fail-on-purpose enforce proof.)*

## `soundness` — the guard (annotate ≠ proven)

A `@Ensures` is a *claim*. This concept ships a **false** contract on purpose: `delta(a, b) = a - b`
is annotated `result >= 0`, which is a lie when `a < b`. There's no hand-written proof — the
auto-generated `enforce__delta` *is* the test, and it goes red (counterexample `a = 0, b = 1`)
before any caller can rely on the false summary. `absDelta` is honest and passes. *(1 pass + 1 fail.)*
