package org.bmc4j.engine

/**
 * Engine-agnostic description of one proof to verify. The extension builds this from
 * the `@BmcProof` method and hands it to a [VerificationBackend]; each
 * backend does its own engine-specific preparation (model classpaths, IR conversion,
 * bytecode rewrites) and invocation. (`@get:JvmName` keeps the original record-style
 * accessor names — `entryClass()`, `unwind()`, … — for the Java call sites.)
 */
class BmcRequest @JvmOverloads constructor(
        /** Fully-qualified class declaring the proof method. */
        @get:JvmName("entryClass") val entryClass: String,
        /** `Class.method` entry point. */
        @get:JvmName("entryFunction") val entryFunction: String,
        /** The compiled bytecode classpath of the test JVM (`java.class.path`). */
        @get:JvmName("classpath") val classpath: String,
        @get:JvmName("unwind") val unwind: Int,
        @get:JvmName("unwindingAssertions") val unwindingAssertions: Boolean,
        @get:JvmName("maxStringLength") val maxStringLength: Int,
        /** Per-proof SAT/SMT solver override (e.g. `"z3"`); empty = use `-Dbmc.solver`/default. */
        @get:JvmName("solver") val solver: String = "",
        /**
         * Per-proof wall-clock budget in seconds. When `> 0`, the engine process tree is
         * force-killed on expiry and the proof is reported [UNKNOWN][JbmcResult.Verdict.UNKNOWN].
         * `0` means no timeout (run to completion).
         */
        @get:JvmName("timeoutSeconds") val timeoutSeconds: Int = 0,
        /**
         * When non-null, this request is ONE derived run of a `domainSplit` proof: the
         * [DomainSplitBytecode] pass rewrites the entry method's markers for this run (a slice's
         * `assume`, or the cover obligation) before analysis. Null for an ordinary, un-split proof.
         * The orchestration (how N+1 runs are launched and their verdicts aggregated) lives in
         * [org.bmc4j.junit.BmcProofExtension].
         */
        @get:JvmName("domainSplitRun") val domainSplitRun: DomainSplitBytecode.RunPlan? = null,
        /**
         * The RESOLVED external DIMACS SAT solver binary this proof runs under, or empty for the
         * engine's default/built-in/SMT path. Distinct from [solver] (the requested name): this is the
         * concrete path the safe-by-default [SolverPlan] decided on — populated ONLY for a proof proven
         * text-free (or under the expert unsafe override). Part of the verdict-cache identity: the
         * resolved binary, not just the requested name, must bust the cache.
         */
        @get:JvmName("externalSatPath") val externalSatPath: String = "",
        /**
         * Whether the engine runs this proof with its String reasoning turned OFF — true exactly when
         * an external SAT solver is engaged ([externalSatPath] non-empty). A verdict proven with String
         * reasoning off is NOT interchangeable with one proven with it on, so this is part of the
         * verdict-cache identity (over-keying is always sound; under-keying would serve a refinement-off
         * verdict for a refinement-on request, a soundness bug).
         */
        @get:JvmName("stringRefinementOff") val stringRefinementOff: Boolean = false,
        /**
         * Exception-message elision control for this proof (see [ExceptionMessageElision]). [AUTO][
         * ExceptionMessageElision.Mode.AUTO] (the default) elides an unobserved exception message iff the
         * coarse observability gate clears; `ON` forces elision (a user-asserted override, surfaced on the
         * verdict); `OFF` never elides. Part of the verdict-cache identity — a verdict proven with a
         * message elided is not interchangeable with one proven without (over-keying is sound;
         * under-keying could serve an elided verdict for a non-eliding request).
         */
        @get:JvmName("removeExceptionMessages") val removeExceptionMessages: org.bmc4j.RemoveExceptionMessages =
                org.bmc4j.RemoveExceptionMessages.AUTO,
        /**
         * How JBMC models `java.lang.String` for this proof (see [org.bmc4j.StringMode]).
         * [REFINEMENT][org.bmc4j.StringMode.REFINEMENT] (the default) runs JBMC's string-refinement
         * solver and passes `--max-nondet-string-length`; [CHAR_ARRAY_MODEL][org.bmc4j.StringMode.CHAR_ARRAY_MODEL] turns
         * refinement off (`--no-refine-strings`) and OMITS `--max-nondet-string-length` (JBMC rejects
         * the two together). A verdict proven under one mode is NOT interchangeable with the other, so
         * this is part of the verdict-cache identity (it rides the verdict-relevant flag signature in
         * [Jbmc.appendVerdictRelevantFlags]; over-keying is sound, under-keying could serve a
         * refinement-off verdict for a refinement-on request).
         *
         * Distinct from [stringRefinementOff], which is an INTERNAL flag tracking that the external-SAT
         * path implicitly disables refinement; [stringMode] is the USER-FACING, per-proof choice that
         * explicitly emits `--no-refine-strings`.
         */
        @get:JvmName("stringMode") val stringMode: org.bmc4j.StringMode = org.bmc4j.StringMode.REFINEMENT,
        /**
         * Whether to emit a per-stage PERFORMANCE BREAKDOWN for this run (the [org.bmc4j.BmcProfile]
         * capability). Purely additive output: it makes the engine driver parse the verbose stream into a
         * [JbmcProfile] and attach it to the result, and it is DELIBERATELY EXCLUDED from the verdict-cache
         * key — a profiled and an unprofiled run produce the same verdict, so a profiled run must not be a
         * cache miss on that account (instead the extension bypasses the cache short-circuit so a live run
         * actually happens to profile). Defaults to off, so the normal path is unaffected.
         */
        @get:JvmName("profile") val profile: Boolean = false,
        /**
         * Raw extra jbmc arguments from `@JbmcOptions`, tokenized on whitespace and appended verbatim
         * to the command for this proof. An UNGUARDED passthrough (no validation/soundness checks). It
         * is part of the verdict-cache identity — setting or changing it forces a fresh engine run; the
         * empty default keys identically to a proof with no `@JbmcOptions`.
         */
        @get:JvmName("jbmcOptions") val jbmcOptions: String = "",
        /**
         * Per-loop unwind overrides for this run, each `<engine loopId> -> bound`, emitted as
         * `--unwindset <loopId>:<bound>` (see [Jbmc]). Raises the bound for ONLY those loops, leaving
         * every other loop on the global [unwind] — the mechanism behind per-loop "smart" unwinding
         * ([SmartUnwind]), which discovers under-bounded loops with `--unwinding-assertions` and bumps
         * just them, so one loop needing a high bound no longer inflates the formula on every other loop.
         *
         * Part of the verdict-cache identity: it rides the verdict-relevant flag signature in
         * [Jbmc.appendVerdictRelevantFlags] (over-keying is sound; under-keying could serve a verdict
         * proven at one per-loop bound set for a request with a different one). Empty for an ordinary
         * single-bound run (the default), so a proof that never engages smart unwinding is unaffected.
         */
        @get:JvmName("unwindSet") val unwindSet: Map<String, Int> = emptyMap())
