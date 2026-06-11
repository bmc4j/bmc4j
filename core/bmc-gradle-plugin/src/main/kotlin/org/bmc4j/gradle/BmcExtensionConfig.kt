package org.bmc4j.gradle

import org.gradle.api.Action
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Nested

/**
 * The `bmc { }` DSL block.
 *
 * ```
 * bmc {
 *     // unwind defaults to AUTO: each proof auto-discovers its loop bound (climbs up to unwindCap).
 *     // unwind = 12                    // PIN one fixed bound for every proof (the expert opt-out)
 *     // unwindCap = 16                 // highest bound auto-discovery climbs to (default 16)
 *     // jbmcPath = "/opt/cbmc/bin/jbmc" // use a local binary instead of the bundled engine
 * }
 * ```
 */
abstract class BmcExtensionConfig {

    /**
     * Optional path to an existing JBMC binary. When set, the bundled engine
     * dependency is not added and this binary is used instead — useful for an
     * internal mirror, a custom build, or air-gapped environments.
     */
    abstract val jbmcPath: Property<String>

    /**
     * Default loop/recursion unwinding bound for proofs that don't override it.
     *
     * Defaults to `AUTO` (auto-discovery): a proof with no explicit bound runs at the smallest unwind
     * that yields a conclusive verdict (the runtime climbs low→high up to [unwindCap]), so a beginner
     * never tunes loop unwinding. Set a POSITIVE value to PIN one fixed bound for every proof in the
     * project (the expert opt-out). A `@BmcProof(unwind = N)` always overrides this per-proof.
     */
    abstract val unwind: Property<Int>

    /**
     * The CAP (highest bound) auto-unwind discovery climbs to before giving up with a clear UNKNOWN
     * ("this proof may have an unbounded / too-deep loop — set an explicit `unwind`"). Defaults to 16.
     * Only consulted when [unwind] is left on AUTO; pinning [unwind] to a positive value runs every
     * proof at exactly that bound with no climb.
     */
    abstract val unwindCap: Property<Int>

    /**
     * Default per-proof wall-clock budget in seconds. When a proof doesn't reach a verdict
     * in time, its engine process tree is force-killed and the proof is reported `UNKNOWN`
     * (undecided — still fails, but distinctly from a refutation). A proof's
     * `@BmcProof(timeoutSeconds=…)` overrides this. Unset / `0` means no timeout (proofs run
     * to completion). Overridable at the command line with `-Dbmc.timeoutSeconds`.
     */
    abstract val timeoutSeconds: Property<Int>

    /**
     * How many proofs to verify **concurrently** — each runs its own `jbmc` process, and
     * proofs are independent, so this scales near-linearly. Defaults to the number of available
     * processors. Set to `1` to run proofs serially (e.g. if heavy proofs strain memory).
     */
    abstract val parallelism: Property<Int>

    /**
     * SAT/SMT backend for proofs. Default is the engine's built-in MiniSat. Options:
     *  - `"kissat"` — the **bundled fast SAT solver**, applied **only to numeric/boolean (text-free)
     *    proofs**: it's typically several times faster on those, but it can't reason about text/String
     *    operations soundly, so bmc4j runs it ONLY on a proof it proves text-free and uses the default
     *    solver for the rest (see [externalSatStringFallback]). No install needed; it ships with the
     *    engine (and falls back to the default solver on platforms without a bundled fast solver).
     *  - `"z3"` / `"boolector"` / `"cvc4"` / `"cvc5"` — SMT solvers (must be on `PATH`); can be faster
     *    on array/bitvector-heavy proofs.
     *  - any other value is passed to the engine's `--sat-solver` (e.g. `"cadical"`, `"glucose"`).
     *
     * A proof can override this with `@BmcProof(solver = "kissat")`. Precedence: per-proof `@BmcProof`
     * > this project default > [externalSat].
     */
    abstract val solver: Property<String>

    /**
     * Project-wide fast external SAT solver applied to **text-free proofs only** — the lowest-precedence
     * way to turn on the fast path (per-proof `@BmcProof(solver)` and [solver] both win over it). Set it
     * to `"kissat"` (the bundled fast solver) or to a DIMACS SAT solver binary path. Like [solver], a
     * text/String-using proof is NEVER run on it (it would be unsound); those proofs use the default
     * solver. Equivalent to the `-PsatPath` / `-Dbmc.externalSat` command-line property the benchmark uses.
     */
    abstract val externalSat: Property<String>

    /**
     * What to do when the fast solver is requested for a proof that DOES use text/String operations
     * (which the fast solver can't verify soundly). Default `false`: such a proof **fails loud** with a
     * plain-language message, so you notice and fix the configuration. Set `true` to instead **silently
     * fall back to the default solver** for those proofs — a SOUND result with no speedup — while the
     * text-free proofs still get the fast solver. There is no mode that runs the fast (text-reasoning-
     * off) solver on a text proof and reports a pass. Overridable with `-Dbmc.externalSatStringFallback`.
     */
    abstract val externalSatStringFallback: Property<Boolean>

    /** Path/command for an external SMT2 solver binary (used with `--smt2`); overrides
     *  [solver]. Use when the solver isn't on `PATH`. */
    abstract val solverCmd: Property<String>

    /**
     * Directory holding the SMT solver binary (e.g. the dir containing `z3`). It's prepended to
     * jbmc's PATH so [solver] / `@BmcProof(solver=…)` can find the solver without it
     * being on the global `PATH`. Keep machine-specific paths out of the repo — set it from a
     * Gradle property, e.g. `solverPath = providers.gradleProperty("z3Path").orNull`.
     */
    abstract val solverPath: Property<String>

    /**
     * Show per-proof progress while the `test` task runs ("proving X", "OK/REFUTED X (Ns)")
     * plus a final summary, at Gradle's lifecycle level so it's visible in a normal `gradlew
     * test`. On by default — proofs can take seconds each, and silence looks like a hang. Set to
     * `false` for quiet CI logs.
     */
    abstract val progress: Property<Boolean>

    /**
     * Per-proof verdict caching. When `true` (the default), a proof that
     * **passed with a deterministic verdict** (`VERIFIED`, or `REFUTED`/`VACUOUS`
     * for a fail-on-purpose proof whose `expect` declares exactly that) and whose inputs
     * (bytecode, flags, engine + runtime semantics) are unchanged is skipped on the next run and
     * reported passed from the cache under `build/bmc4j/verdict-cache/` — so "nothing changed"
     * runs are near-free. Only expectation-matching passes are ever cached; failures always re-run
     * live, and `TIMEOUT`/`UNKNOWN` are never cached even when expected (machine-dependent).
     * Set `false` (or pass `-Dbmc.noCache=true`) to force full
     * re-verification every time. The cache lives under `build/`, so `gradlew clean` clears it.
     */
    abstract val cache: Property<Boolean>

    /**
     * Build-wide acknowledged nondet stubs: methods every proof may rely on as havoc'd
     * stand-ins without warning. JBMC stubs any callee it has no body for to a nondet result; bmc4j
     * footnotes that on green proofs (and, under [strictStubs], turns an unacknowledged stub
     * into UNKNOWN). Listing a method here silences it suite-wide; a proof can add more with
     * `@BmcProof(allowStubs = …)`. Entries are fully-qualified method names with an optional
     * trailing wildcard: `"java.util.Formatter.format"`, `"java.util.Formatter.*"`, or
     * `"java.util.*"`.
     */
    abstract val allowStubs: ListProperty<String>

    /**
     * Build-wide acknowledged UNMODELLED members: real JDK members bmc4j cannot model
     * (a bundled model's `@BmcUnmodelable` / `@BmcModelTail`) that proofs may reach
     * without failing. By DEFAULT, reaching such a member fails the proof as UNKNOWN (a model gap is
     * bmc4j's own limitation, not a counterexample in your code). Listing a member here OPTS OUT
     * suite-wide: it degrades to the classic nondet-stub behavior — treated as an unconstrained havoc,
     * with a loud footnote (never silent) — exactly like [allowStubs] for stubs. A proof can add more
     * with `@BmcProof(acknowledgeUnmodelled = …)`; both sets apply. Entries are fully-qualified member
     * names with an optional trailing wildcard: `"java.util.ArrayList.sort"`, `"java.util.ArrayList.*"`,
     * or `"java.util.*"`. Overridable with `-Dbmc.acknowledgeUnmodelled`; part of the verdict-cache key.
     */
    abstract val acknowledgeUnmodelled: ListProperty<String>

    /**
     * Language of the **replay** scratch file bmc4j writes for a refuted proof
     * (`build/bmc4j/replays/<Class>_<method>Replay.{java|kt}`). One of:
     *
     * - `"auto"` (default): a Kotlin proof class (detected by its `kotlin.Metadata`) gets a `.kt`
     *   replay; any other class gets a `.java` replay — so a Kotlin loop stays Kotlin and pure-Java
     *   users see no change.
     * - `"kotlin"` / `"java"`: force that language for every replay regardless of the proof class —
     *   for mixed modules or teams that keep scratch tests in one language.
     *
     * Overridable at the command line with `-Dbmc.replayLanguage=...` (or `-Pbmc.replayLanguage=...`).
     * Any value other than `auto`/`kotlin`/`java` fails the build at configuration time.
     */
    abstract val replayLanguage: Property<String>

    /**
     * Strict nondet-stub mode. When `true`, any *unacknowledged* stub a proof
     * reaches turns its verdict into UNKNOWN (`BmcUndecidedError`) — nothing was proven wrong, but
     * the verdict rests on havoc'd stand-ins, so it isn't trustworthy. Default `false` (lenient:
     * green + footnote). Overridable at the command line with `-Dbmc.strictStubs=true`, so flipping
     * it re-judges from the stored stub fact *without* re-running proofs (the stub list is cached).
     */
    abstract val strictStubs: Property<Boolean>

    /**
     * Honest-JVM semantics for Kotlin proof parameters. By default a `@BmcProof`'s own
     * non-null-typed Kotlin parameters are auto-assumed non-null (kotlinc's
     * `checkNotNullParameter` prologue becomes `assume(p != null)`), so the proof ranges
     * over the inputs the Kotlin type system admits instead of spuriously refuting on `p = null`
     * — an input no Kotlin caller can construct. Interior calls always keep the throwing semantics.
     * Set `true` (or pass `-Dbmc.kotlinNullableParams=true`) to restore the throwing
     * prologue for proofs that deliberately model hostile Java callers passing `null` into
     * Kotlin non-null parameters. Part of the verdict-cache key — flipping it re-verifies.
     */
    abstract val kotlinNullableParams: Property<Boolean>

    /**
     * Package prefixes of the module under test. A stub from one of these — the user's own
     * code — is almost always a missing-dependency config bug, not a JDK modeling gap, so it is warned
     * loudly even in lenient mode (and forces UNKNOWN in strict mode). Comma/space-separated prefixes,
     * e.g. `userPackages = ["com.acme"]`. Overridable with `-Dbmc.userPackages`.
     */
    abstract val userPackages: ListProperty<String>

    // --- User models: declared intent + provenance ------------------------------------------------

    /**
     * Registered user models with their declared **intent**. A class under `src/bmcModel`
     * shadows its real counterpart on JBMC's analysis classpath; registering it here adds the trust
     * metadata bmc4j needs to put provenance on a verdict that rests on it.
     *
     * ```
     * bmc {
     *     models {
     *         conformant("acme.FastList")                       // claims JDK fidelity
     *         domain("acme.NoCollisionMap", "keys are UUIDs, collision-free")  // intentional divergence
     *     }
     * }
     * ```
     *
     * A `domain` model encodes a constraint that deliberately diverges from the JDK -- it is
     * `Bmc.assume()` at classpath altitude -- so it requires a one-line rationale, which is
     * footnoted on every green proof that rests on it. A `conformant` model claims JDK fidelity and
     * can be checked by the same conformance harness as bundled models. Under [strictModels],
     * a model present under `src/bmcModel` but NOT registered here turns the verdict into UNKNOWN.
     */
    @get:Nested
    abstract val modelSpec: ModelSpec

    /** Configure the registered user models -- see [modelSpec]. */
    fun models(action: Action<in ModelSpec>) {
        action.execute(modelSpec)
    }

    // --- Deliberately out-of-scope packages -------------------------------------------------------

    /**
     * Whole packages declared **deliberately out of scope for modeling**. A class under a declared
     * package that bmc4j has no model for is classified as an intentional, reviewable out-of-scope
     * declaration rather than an un-tracked gap.
     *
     * ```
     * bmc {
     *     notModeledPackages {
     *         +"javax.swing.*"
     *         +"java.sql.*"
     *         +"java.nio.file.*"
     *     }
     * }
     * ```
     *
     * (The leading `+` is Kotlin's `unaryPlus` — the standard Gradle idiom for a bare-string list
     * statement; Java/Groovy consumers call [NotModeledPackages.pkg] instead.)
     *
     * **Glob semantics — RECURSIVE.** A glob covers the named package *and all its subpackages*:
     * `java.nio.*` matches `java.nio.ByteBuffer` **and** `java.nio.file.Path`,
     * `java.nio.file.attribute.FileTime`, … A bare prefix (`java.nio`) recurses identically; a trailing
     * `.*` is the documented spelling. There is no exact-package-only form — recursion is the only mode,
     * because a subpackage of an out-of-scope area is itself out of scope.
     *
     * **Precedence — the registry WINS.** A package waiver applies ONLY to a class bmc4j does not
     * otherwise model. A modeled class inside a declared package (e.g. a future `java.nio` model) is
     * still the model — the waiver never demotes it; it only catches the otherwise-unmodeled remainder.
     *
     * **Loudness — a waiver CLASSIFIES, it never SUPPRESSES.** Reaching a class under a declared package
     * still produces a LOUD, member-named **out-of-scope (declared)** `UNKNOWN`, never a silent nondet
     * stub and never a path to a false `VERIFIED` — the same loudness the per-member tail guarantees,
     * with a distinct reason so a reviewer can tell "deliberately declined" from "model gap not yet
     * filled". A proof can opt a specific member back into the classic footnoted-nondet behavior with
     * `@BmcProof(acknowledgeUnmodelled = …)` exactly as for an unmodelled member.
     *
     * Overridable / forwardable with `-Dbmc.notModeledPackages` (comma-separated globs).
     */
    @get:Nested
    abstract val notModeledPackagesSpec: NotModeledPackages

    /** Configure the deliberately-out-of-scope packages -- see [notModeledPackagesSpec]. */
    fun notModeledPackages(action: Action<in NotModeledPackages>) {
        action.execute(notModeledPackagesSpec)
    }

    /**
     * The `notModeledPackages { "glob"; … }` DSL block. A Gradle **managed** type (abstract, no
     * fields): its one property is the abstract [globs] list Gradle instantiates. Each statement in the
     * block is a bare string whose Kotlin `invoke` appends it; Java/Groovy consumers call [pkg].
     */
    abstract class NotModeledPackages {

        /** The declared out-of-scope package globs, e.g. `["javax.swing.*", "java.sql.*"]`. */
        abstract val globs: ListProperty<String>

        /** Declare a package glob out of scope (Kotlin DSL: a `+"javax.swing.*"` statement). */
        operator fun String.unaryPlus() {
            pkg(this)
        }

        /** Declare a package glob out of scope (Java/Groovy form). */
        fun pkg(glob: String) {
            if (glob.isBlank()) {
                throw IllegalArgumentException(
                        "bmc { notModeledPackages { ... } } requires a non-blank package glob")
            }
            globs.add(glob.trim())
        }
    }

    /**
     * Strict user-model mode, the `strictStubs` analog. When `true`, a model present under
     * `src/bmcModel` with no `bmc { models { ... } }` intent declaration turns the proof's
     * verdict into UNKNOWN (`BmcUndecidedError`) -- no proof silently rests on an undeclared
     * override. Default `false` (lenient: green + a loud "UNDECLARED model" footnote). Overridable
     * at the command line with `-Dbmc.strictModels=true`; like `strictStubs` it is read-time
     * policy, so flipping it re-judges without re-running proofs.
     */
    abstract val strictModels: Property<Boolean>

    /**
     * The `models { conformant(...) / domain(...) }` DSL block. A Gradle **managed** type
     * (abstract, no fields): its one property is the abstract [entries] list, which Gradle
     * instantiates; the `conformant` / `domain` methods append serialized declarations to it.
     */
    abstract class ModelSpec {

        /** Serialized declarations, one per entry as `intent|fqn|rationale`; joined by the plugin. */
        abstract val entries: ListProperty<String>

        /** Register a conformant user model (claims JDK fidelity). */
        fun conformant(className: String) {
            requireClassName(className, "conformant")
            entries.add("conformant|${className.trim()}|")
        }

        /**
         * Register a domain user model (intentional divergence). `rationale` is required -- a
         * one-line explanation of the assumed constraint, footnoted on green proofs that rest on it.
         */
        fun domain(className: String, rationale: String?) {
            requireClassName(className, "domain")
            if (rationale.isNullOrBlank()) {
                throw IllegalArgumentException("bmc { models { domain(\"$className\", ...) } } " +
                        "requires a rationale: a domain model intentionally diverges from the JDK " +
                        "-- say how (e.g. \"keys are UUIDs, collision-free\").")
            }
            entries.add("domain|${className.trim()}|${rationale.trim()}")
        }

        private fun requireClassName(className: String?, intent: String) {
            if (className.isNullOrBlank()) {
                throw IllegalArgumentException(
                        "bmc { models { $intent(...) } } requires a class name")
            }
        }
    }
}
