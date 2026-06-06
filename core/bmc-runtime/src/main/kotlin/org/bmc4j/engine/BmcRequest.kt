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
        /** Whether the proof opted into concurrency exploration. */
        @get:JvmName("concurrent") val concurrent: Boolean,
        /** Per-proof SAT/SMT solver override (e.g. `"z3"`); empty = use `-Dbmc.solver`/default. */
        @get:JvmName("solver") val solver: String = "",
        /**
         * Per-proof wall-clock budget in seconds. When `> 0`, the engine process tree is
         * force-killed on expiry and the proof is reported [UNKNOWN][JbmcResult.Verdict.UNKNOWN].
         * `0` means no timeout (run to completion).
         */
        @get:JvmName("timeoutSeconds") val timeoutSeconds: Int = 0)
