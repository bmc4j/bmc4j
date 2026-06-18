package org.bmc4j.engine

import kotlin.reflect.KClass

/*
 * The ~18 bytecode passes [JbmcBackend.prepareClasspath] runs, migrated onto the [BmcPass] interface.
 * Each pass object delegates to the SAME entry point the hand-wired pipeline called, so the bytecode is
 * byte-for-byte identical; only the organization changes. The six pure desugars are represented by the
 * single [DesugarPass] because they FUSE into one classpath walk ([DesugarPasses] / [ClasspathMirror.mirrorAll])
 * -- splitting them into six separate transforms would re-introduce the six inflate/deflate round-trips the
 * fusion removed and break the mirror cache, so the fused walk is the unit.
 *
 * CacheablePass = the run-wide, environment-independent prefix the Gradle plugin pre-computes + caches
 * (and that runs once in-JVM otherwise). Order within the group is `Desugar -> AnyRef -> Config ->
 * KotlinParam -> Reachability -> NondetTag`, the exact order [GradleClasspathMirror.mirror] uses, pinned
 * here via [BmcPass.dependsOn] so the orchestrator reproduces it (and so the in-JVM result stays
 * byte-identical to the mirror -- GradleClasspathMirrorTest's invariant).
 */

// --- Cacheable prefix (hoistable, env-independent) ------------------------------------------------------

/** The six fused desugars (coroutine-LVT strip, String content ops, lambda/method-ref indy,
 *  pattern-switch indy, residual-indy marker, integer Math.*) as ONE walk. */
object DesugarPass : CacheablePass {
    override fun transform(classes: ClassSet, ctx: BmcContext): ClassSet =
            ClassSet(ClasspathMirror.mirrorAll(classes.classpath))
}

/** Intrinsify `Bmc.anyRef(Foo.class)` -> `CProver.nondetWithoutNull()` so the erasure checkcast holds. */
object AnyRefPass : CacheablePass {
    override val dependsOn: List<KClass<out BmcPass>> get() = listOf(DesugarPass::class)
    override fun transform(classes: ClassSet, ctx: BmcContext): ClassSet =
            ClassSet(AnyRefBytecode.rewrite(classes.classpath))
}

/** Bake `Bmc.*From*("KEY")` to this run's resolved env/property value as a constant. */
object ConfigPass : CacheablePass {
    override val dependsOn: List<KClass<out BmcPass>> get() = listOf(AnyRefPass::class)
    override fun transform(classes: ClassSet, ctx: BmcContext): ClassSet =
            ClassSet(ConfigBytecode.rewrite(classes.classpath))
}

/** Relax the Kotlin non-null parameter prologue inside `@BmcProof` methods (run-wide flag). */
object KotlinParamPass : CacheablePass {
    override val dependsOn: List<KClass<out BmcPass>> get() = listOf(ConfigPass::class)
    override fun transform(classes: ClassSet, ctx: BmcContext): ClassSet =
            ClassSet(KotlinParamBytecode.rewrite(classes.classpath))
}

/** Inject the vacuity marker into `@BmcProof` returns. */
object ReachabilityPass : CacheablePass {
    override val dependsOn: List<KClass<out BmcPass>> get() = listOf(KotlinParamPass::class)
    override fun transform(classes: ClassSet, ctx: BmcContext): ClassSet =
            ClassSet(ReachabilityBytecode.rewrite(classes.classpath))
}

/** Inject a verification-neutral `Bmc.recordNondet(...)` after each user `Bmc.any*` store. */
object NondetTagPass : CacheablePass {
    override val dependsOn: List<KClass<out BmcPass>> get() = listOf(ReachabilityPass::class)
    override fun transform(classes: ClassSet, ctx: BmcContext): ClassSet =
            ClassSet(NondetTagBytecode.rewrite(classes.classpath))
}

// --- Per-proof tail (env / proof dependent) -------------------------------------------------------------

/**
 * Rewrite contracted call sites to their replace-stubs. Reads the contract [BmcContext.manifest] (deposited
 * at the boundary) -> per-proof. A no-op without a manifest. Must run on the desugared form so a concat /
 * byte-decode call site is in its sound shape before redirection (soundness-critical: depends on the
 * desugars).
 */
object ContractRewritePass : BmcPass {
    override val dependsOn: List<KClass<out BmcPass>> get() = listOf(DesugarPass::class)
    override fun shouldTransform(ctx: BmcContext): Boolean = ctx.manifest?.isEmpty == false
    override fun transform(classes: ClassSet, ctx: BmcContext): ClassSet {
        val contracts = ctx.manifest ?: return classes
        if (contracts.isEmpty) {
            return classes
        }
        val entryInternal = ctx.entryClass.replace('.', '/')
        val excludeCaller =
                if (contracts.enforceProofClasses().contains(entryInternal)) entryInternal else null
        return ClassSet(
                ContractRewriter.rewrite(classes.classpath, contracts.redirects(), excludeCaller))
    }
}

/**
 * Install the per-proof assumed output-contracts (`Bmc.assumeEvery` / `assumeStable`) decoded off the
 * ORIGINAL pre-rewrite classpath into [BmcContext.assumeContracts]. Decode-then-install is the canonical
 * deposit/consume handoff: [decode] reads the markers (deposit), [transform] installs them (consume). A
 * no-op when the proof declares none.
 */
object AssumeContractPass : BmcPass {
    /** Deposit: decode the markers off the original classpath onto [ctx]. Done at the boundary before
     *  [transform], because decode reads the ORIGINAL (un-rewritten) classpath where the indys still are. */
    fun decode(ctx: BmcContext) {
        ctx.assumeContracts = AssumeContractBytecode.decode(
                ctx.request.classpath, ctx.entryClass, ctx.entryMethod)
    }

    override fun shouldTransform(ctx: BmcContext): Boolean = ctx.assumeContracts.isNotEmpty()
    override fun transform(classes: ClassSet, ctx: BmcContext): ClassSet =
            ClassSet(AssumeContractBytecode.install(
                    classes.classpath, ctx.entryClass, ctx.entryMethod, ctx.assumeContracts))
}

/**
 * Bound a symbolic string's LENGTH under CHAR_ARRAY_MODEL the way `--max-nondet-string-length` does under
 * refinement. Per-proof (mode + the run's effective maxStringLength); a no-op under REFINEMENT.
 */
object StringLengthPass : BmcPass {
    override fun shouldTransform(ctx: BmcContext): Boolean =
            ctx.request.stringMode == org.bmc4j.StringMode.CHAR_ARRAY_MODEL
    override fun transform(classes: ClassSet, ctx: BmcContext): ClassSet =
            ClassSet(StringLengthBytecode.rewrite(classes.classpath, ctx.request.maxStringLength))
}

/**
 * Swap in each [org.bmc4j.ConditionalOn] override whose [org.bmc4j.BmcCondition] holds for this proof's
 * resolved config, by redirecting every call to the override's target to the override (call-site
 * redirect). Per-proof (the condition is evaluated against the run's string mode). Runs AFTER the model
 * jars are spliced so the override classes are present and their target call sites are reachable. A no-op
 * when no override fires (no model declares one whose condition holds for this mode).
 */
object ConditionalOnPass : BmcPass {
    override fun transform(classes: ClassSet, ctx: BmcContext): ClassSet =
            ClassSet(ConditionalOnBytecode.rewrite(classes.classpath, ctx.request))
}

/**
 * Certify each contract this proof CONSUMES is pure-by-construction against the fully-prepared, model-bearing
 * classpath, or fail LOUD ([ContractPurityError]). A CHECK, not a rewrite: returns [classes] unchanged.
 * Reads the contract [BmcContext.manifest] -> per-proof; runs on the desugared, model-bearing form (depends
 * on the desugars). A no-op without contracts.
 */
object PurityAuditPass : BmcPass {
    override val dependsOn: List<KClass<out BmcPass>> get() = listOf(DesugarPass::class)
    override fun shouldTransform(ctx: BmcContext): Boolean = ctx.manifest?.isEmpty == false
    override fun transform(classes: ClassSet, ctx: BmcContext): ClassSet {
        val manifest = ctx.manifest ?: return classes
        ContractPurityAudit.auditRelevant(
                manifest, ctx.entryClass, ctx.entryMethod, ctx.request.classpath, classes.classpath)
        return classes
    }
}

/**
 * Drop the construction of a thrown exception's message when unobserved (AUTO) or forced (ON). Per-proof
 * (the proof's mode + reachable cone). Runs on the desugared form so a message-building concat is in its
 * sound shape (depends on the desugars). OFF is a no-op.
 */
object ExceptionMessageElisionPass : BmcPass {
    override val dependsOn: List<KClass<out BmcPass>> get() = listOf(DesugarPass::class)
    override fun transform(classes: ClassSet, ctx: BmcContext): ClassSet =
            ClassSet(ExceptionMessageElision
                    .apply(classes.classpath, ctx.entryClass, ctx.entryMethod,
                            ctx.request.removeExceptionMessages).classpath)
}

/**
 * Construct the proof's RECEIVER: synthesise a loop-free static wrapper into the entry class
 * (`new EntryClass().proofMethod()`) so jbmc runs `<init>` on a freshly-constructed `this`, pinning
 * instance fields to their initializers (see [ConstructReceiverBytecode]). [JbmcBackend] redirects
 * `--function` to the wrapper. The eligibility decision (instance proof method + analysable no-arg ctor)
 * is taken once at the boundary onto [BmcContext.receiverDecision]; a fallback proof skips this pass and
 * keeps today's nondet-`this` entry. Runs on the desugared form (the entry class is already in its sound
 * shape) and BEFORE the model slice, so the wrapper (and the `<init>` it reaches) ride the reachable cone.
 */
object ConstructReceiverPass : BmcPass {
    override val dependsOn: List<KClass<out BmcPass>> get() = listOf(DesugarPass::class)
    override fun shouldTransform(ctx: BmcContext): Boolean = ctx.receiverDecision.eligible
    override fun transform(classes: ClassSet, ctx: BmcContext): ClassSet {
        val desc = ctx.receiverDecision.proofDesc ?: return classes
        return ClassSet(ConstructReceiverBytecode.rewrite(
                classes.classpath, ctx.entryClass, ctx.entryMethod, desc))
    }
}

/**
 * Hand the engine only this proof's reachable cone (LAST). Computed over the FULLY-REWRITTEN classpath so
 * every injected/redirected class is in the keep-set; a proof whose cone can't be bounded is returned
 * unchanged. A slice failure fails safe to the unsliced classpath. Depends on every other pass having run
 * (it must see the final bytecode), so it sorts last among the per-proof tail.
 */
object ModelSlicePass : BmcPass {
    override val dependsOn: List<KClass<out BmcPass>>
        get() = listOf(ContractRewritePass::class, AssumeContractPass::class, StringLengthPass::class,
                ConditionalOnPass::class, PurityAuditPass::class, ExceptionMessageElisionPass::class,
                ConstructReceiverPass::class)
    override fun transform(classes: ClassSet, ctx: BmcContext): ClassSet =
            ClassSet(ModelSlice.sliceForCone(classes.classpath, ctx.entryClass))
}
