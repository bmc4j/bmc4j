# Modular proofs: method contracts

By default JBMC re-analyzes a method's body at every call site. For a pure helper —
especially one with a loop or recursion — that re-analysis dominates, and a caller's
`unwind` bound has to be large enough to cover the callee. **Method contracts** make
proofs modular: prove a method's postcondition *once*, then let callers assume it.

Contracts live **in your tests, not your production code**. Production stays plain.

## Kotlin (kapt)

bmc4j is Kotlin-first, and so are contracts. The `bmc-contracts` processor is a **javac** annotation
processor, so Kotlin test sources run it through **kapt** (KSP can't host a javac AP). The `org.bmc4j`
plugin wires this for you the moment the Kotlin plugin is applied — it applies kapt, adds
`bmc-contracts` to `kaptTest`, and sets `javaParameters = true` so predicate parameter names survive
into bytecode. There is no kapt block to write:

```kotlin
plugins {
    kotlin("jvm")
    id("org.bmc4j") // applies kapt + wires kaptTest(bmc-contracts) + javaParameters
}
```

```kotlin
// src/main — plain Kotlin, no bmc references. A top-level fun's facade class is unnameable from
// Kotlin (no `FooKt::class`), so expose the target on an `object`/`companion` with @JvmStatic.
object Recursive {
    @JvmStatic fun sumTo(n: Int): Int = if (n <= 0) 0 else n + sumTo(n - 1)
}
```

```kotlin
// src/test — the contract, declared against the production class. @JvmStatic predicates in the
// companion become the static boolean predicates the generated stub/enforce-proof call.
@BmcContractsFor(Recursive::class)
interface SumToContract {
    @Requires("inRange") @Ensures("closedForm") fun sumTo(n: Int): Int   // mirrors the target
    companion object {
        @JvmStatic fun inRange(n: Int): Boolean = n in 0..12
        @JvmStatic fun closedForm(result: Int, n: Int): Boolean = result == n * (n + 1) / 2
    }
}
```

**Kotlin shapes** that bind: `object`/`companion` `@JvmStatic` methods (static targets), pure instance
methods (receiver threaded as `self`), and methods with **default parameters** (the `$default`
synthetic's call is redirected too). Shapes the processor rejects **loudly** (a silent failure to bind
is a hard error): a **value/inline-class** parameter or return (kapt mangles the JVM name and drops the
annotations — unwrap the value class at the boundary), a bare **top-level `fun`** (its facade class is
unnameable from Kotlin — use an `object`/`companion`), and **`suspend`** functions (they return a
coroutine state machine, not a value). See [`examples/contracts-kotlin`](../examples/contracts-kotlin).

## Java

Java contracts are wired the same way, via `testAnnotationProcessor` (no kapt). Production stays plain:

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

- **Pure only — and now audited, not on you.** Contracts are sound only for
  side-effect-free, value-returning methods: a contract on an impure method would drop its
  effects at every replaced call site (the replace-stub summarizes only the return value), yet
  the enforce-proof — which checks `@Ensures`, not purity — would still pass. That was the last
  false-green vector in the design. bmc4j now closes it with a **conservative purity audit**:
  before any proof reuses a contract, the target's body is certified *provably pure by
  construction* against the rewritten analysis classpath, or the build fails loud with a
  `ContractPurityError` naming the offending instruction. It is a sound over-approximation —
  certifying an impure body is impossible by design; the only cost is a false *rejection* of a
  body it can't see through (the same conservative bias as the verdict cache). A rejection is an
  **unconditional** build error: unlike a deliberately-false `@Ensures` (which an
  `@ExpectEnforce(REFUTED)` demo can pin), no annotation can bless an impure target — it simply
  isn't a legal contract target.

  What the audit **rejects** (caller-observable effects beyond the return value), naming the
  instruction or the reached-callee chain:

  - **heap writes to pre-existing state** — `PUTFIELD`/array stores on an object the body
    didn't itself allocate, and any `PUTSTATIC`. A *fresh* allocation populated and returned is
    fine (escape-aware: writes to a `new` object made in the body don't escape to a caller);
  - **known-impure calls** — I/O, `System.nanoTime`/`currentTimeMillis`, `Random`,
    threads/locks, `Unsafe`, reflection/`MethodHandle`s, and native methods;
  - **`monitorenter`** — a concurrency effect;
  - **reads of mutable statics** — a `GETSTATIC` of a non-`static final` reference field (its
    value can differ between the enforce-proof and a real call site, so the contract wouldn't be
    a function of its inputs);
  - **anything it can't see through** — a non-devirtualizable virtual/interface call, an
    unknown jar, or an intrinsified JDK method with no model and not on the pure-JDK floor.

  The audit is **transitive**: a body is pure only if everything it reaches is. It follows the
  static call graph over the same model-bearing bytecode JBMC analyses (so JDK calls resolve to
  bmc's sound models), and treats a call to another contract's stub as an already-summarized
  pure leaf — that callee's purity is its own contract's obligation, exactly like modular
  enforce. **Exception behaviour is deliberately not audited:** the replace-stub never throws,
  and the enforce-proof runs the real body under `@Requires`, so enforce-green *already is* a
  no-throw-under-`requires` proof. The `examples/contracts` `purity` concept ships an
  intentionally-impure contract whose enforce-proof the audit rejects (its `PUTSTATIC` named).
- **Enforce-before-reuse is automatic.** A contract is trustworthy only once its
  enforce-proof is green — and that proof is generated for you, so a false contract turns
  the build red rather than silently weakening every caller.
- **Keep postconditions tight.** An over-wide `@Ensures` can hand callers a value the real
  body never returns (e.g. a result range that overflows downstream). The enforce-proof
  checks the contract is *true*, not that it is *strong enough* — that part is on you.
- **Recursion is partial correctness.** A contract proves "*if* the method returns, the
  postcondition holds". Termination is a separate question BMC does not settle.

## Static and pure-instance methods

Contracts target `static` **and pure instance**, value-returning methods. Predicates are
`static boolean` methods on the test-side contract type. For an instance method the **receiver is
threaded into the predicates as a leading `self` parameter** — the mirror's own signature stays
the production signature (no `self`), but `@Requires`/`@Ensures` see the receiver first:

```java
// src/main — a pure instance method (reads `this`, mutates nothing)
public final class Account {
    private final int balance;
    public int balance() { return balance; }
    public int project(int amount) { /* pure projection over this.balance + amount */ }
}

// src/test — the contract; `self` is the receiver
@BmcContractsFor(Account.class)
interface AccountContract {
    @Requires("nonNegative") @Ensures("atLeastBalance") int project(int amount);
    static boolean nonNegative(Account self, int amount)            { return self.balance() >= 0 && amount >= 0; }
    static boolean atLeastBalance(int result, Account self, int amount) { return result >= self.balance(); }
}
```

Whether a target is static or instance is decided by **resolving the mirror's signature against the
`@BmcContractsFor` class** — so the contract type doesn't repeat that fact, and a mirror that binds
to nothing is reported as an orphan (see below). The replace-stub and the enforce-proof thread the
receiver as an ordinary symbolic input: JBMC treats the entry function's parameters — `this`
included — as nondet, so a contract whose postcondition reads a receiver field is only green if it
holds for **all** receiver states (constrain the receiver in `@Requires` exactly as you would an
argument; an unbounded balance would let `balance + amount` overflow). The call-site rewrite turns
the `invokevirtual a.project(amount)` into an `invokestatic` to a stub whose descriptor prepends the
receiver — which is already on the operand stack below the args, so the stack is unchanged.

**Purity is what makes instance contracts safe.** A pure instance method reads `this` but never
mutates it, so no `old()`/two-state machinery is needed — `self` in the postcondition is the same
object state as in the precondition. The purity audit already treats the receiver as
pre-existing (a `PUTFIELD` on `this` is a write to non-fresh state), so receiver mutation — the most
common impurity for an instance method — is rejected, exactly like a `PUTSTATIC`. Binding is exact
to the named class (no virtual dispatch of the *target* method).

Out of scope (future, if ever): mutating methods + `old()` expressions, constructors, and
virtual/interface binding of the *target* method.

Binding is by signature, so a production rename can orphan a contract — the processor reports a
**named error** at processing time (naming the contract interface, the missing mirror signature,
and the target class) rather than letting the generated enforce-proof fail to compile cryptically.
