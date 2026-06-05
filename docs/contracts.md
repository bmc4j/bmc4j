# Modular proofs: method contracts

By default JBMC re-analyzes a method's body at every call site. For a pure helper —
especially one with a loop or recursion — that re-analysis dominates, and a caller's
`unwind` bound has to be large enough to cover the callee. **Method contracts** make
proofs modular: prove a method's postcondition *once*, then let callers assume it.

Contracts live **in your tests, not your production code**. Production stays plain:

```java
// src/main — no bmc references
public static int sumTo(int n) {
    if (n <= 0) return 0;
    return n + sumTo(n - 1);
}
```

```java
// src/test — the contract, declared against the production class
@BmcContractsFor(Recursive.class)
interface SumToContract {
    @Requires("inRange") @Ensures("closedForm") int sumTo(int n);   // mirrors the target's signature
    static boolean inRange(int n)                { return n >= 0 && n <= 12; }
    static boolean closedForm(int result, int n) { return result == n * (n + 1) / 2; }
}
```

A contract names ordinary, type-checked `static boolean` predicate methods — no string
DSL — and binds to the production method by signature (the way a `src/bmcModel` model binds
to its class). The **`bmc-contracts`** annotation processor (wired onto the *test* compile
by the plugin) then generates, into your build:

- a **replace-stub** (`assert(requires); r = nondet(); assume(ensures); return r;`) that the
  backend redirects call sites to — so a caller reuses the postcondition instead of
  re-analyzing the body;
- an **enforce-proof**: an auto-generated `@BmcProof` that runs the *real* body under
  `@Requires` and asserts `@Ensures`. It appears in your test report and goes red if the
  contract is false. **Annotating is not asserting** — the obligation is discharged
  structurally, not on trust;
- a manifest the backend reads to know which calls to redirect.

The enforce-proof is **modular too**: the method's own contracted callees — including a
recursive self-call — are summarized while its body is analyzed. So enforcing a recursive
contract is the *inductive step*, and enforcing a call chain reaches only one contract deep.
See [`examples/contracts`](../examples/contracts) — the `basics`, `recursion` (induction), and
`stacking` (composition) concepts.

## Soundness invariants

Read these before trusting a contract:

- **Pure only.** Contracts are sound only for side-effect-free, value-returning methods;
  a contract on an impure method drops its effects at every replaced call site.
- **Enforce-before-reuse is automatic.** A contract is trustworthy only once its
  enforce-proof is green — and that proof is generated for you, so a false contract turns
  the build red rather than silently weakening every caller.
- **Keep postconditions tight.** An over-wide `@Ensures` can hand callers a value the real
  body never returns (e.g. a result range that overflows downstream). The enforce-proof
  checks the contract is *true*, not that it is *strong enough* — that part is on you.
- **Recursion is partial correctness.** A contract proves "*if* the method returns, the
  postcondition holds". Termination is a separate question BMC does not settle.

v1 targets `static`, value-returning methods; predicates are `static boolean` methods on the
test-side contract type. Binding is by signature, so a production rename can orphan a contract
(the generated enforce proof then fails to compile against the missing method — a fail-fast).
