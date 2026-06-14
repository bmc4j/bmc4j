package org.bmc4j.engine

/**
 * The analysis classpath as it flows through the [BmcPass] pipeline. The codebase already represents the
 * classpath as a single path-separator-delimited String (matching `java.class.path`, what every pass's
 * `rewrite(classpath: String)` entry point and jbmc itself consume), so [ClassSet] is a thin wrapper over
 * that String rather than a new representation: it gives the pass interface a domain-named handle without
 * changing the byte-for-byte classpath the passes and the engine see.
 */
@JvmInline
value class ClassSet(val classpath: String)

/**
 * The forward-passed, mutable bag threaded through the [BmcPass] pipeline for one proof. It carries:
 *  - the immutable request/env for the run ([request], [jbmcPath], the resolved entry [entryClass] /
 *    [entryMethod]), which any pass may READ;
 *  - the inter-pass artifact handoffs that exist in today's pipeline: a pass DEPOSITS a decoded artifact
 *    that a later pass CONSUMES. Today's two are the contract [manifest] (read once, consumed by the
 *    contract rewrite and the purity audit) and the decoded [assumeContracts] (decoded off the ORIGINAL
 *    pre-rewrite classpath, then installed). Modelling them on the context keeps the decode/consume
 *    split explicit instead of buried inside one pass.
 *
 * Mutable and single-threaded by design: one instance per [JbmcBackend.verify] call, used only on that
 * call's own thread while it prepares the classpath (passes run sequentially), so no synchronization is
 * needed -- mirroring [PipelineTiming]'s contract.
 */
class BmcContext(
        /** The proof being prepared. */
        @JvmField val request: BmcRequest,
        /** The resolved jbmc binary path (some passes locate `core-models.jar` next to it; the orchestrator
         *  does not, but a pass may). */
        @JvmField val jbmcPath: String) {

    /** FQ class declaring the proof method (`request.entryClass`). */
    val entryClass: String get() = request.entryClass

    /** The method-name half of the `Class.method` entry function. */
    val entryMethod: String get() = entryMethodNameOf(request.entryFunction)

    /**
     * The contract manifest read from the ORIGINAL request classpath (a resource, never rewritten).
     * Deposited once by the orchestrator boundary (cheap, read from disk) and consumed by the contract
     * rewrite pass and the purity-audit pass. Null until read.
     */
    @JvmField
    var manifest: ContractManifest? = null

    /**
     * The per-proof assumed output-contracts (`Bmc.assumeEvery` / `assumeStable`) decoded STATICALLY off
     * the ORIGINAL pre-rewrite classpath. Deposited by the assume-contract pass's decode step and consumed
     * by its install step (and surfaced on the verdict elsewhere). Empty when the proof declares none.
     */
    @JvmField
    internal var assumeContracts: List<AssumeContractBytecode.Decoded> = emptyList()
}

/** The method-name half of a `Class.method` entry-function string (the text after the last dot). */
internal fun entryMethodNameOf(entryFunction: String): String {
    val dot = entryFunction.lastIndexOf('.')
    return if (dot >= 0) entryFunction.substring(dot + 1) else entryFunction
}
