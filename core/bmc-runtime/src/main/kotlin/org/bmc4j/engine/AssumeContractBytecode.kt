package org.bmc4j.engine

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Handle
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile

/**
 * The `Bmc.assumeEvery` / `Bmc.assumeStable` marker rewriter — installs a per-proof **assumed
 * output-contract** for an external/unanalyzable dependency, so a proof can reason on top of "IF the
 * dependency upholds this output property, THEN my code is correct" without a model, an annotation, or
 * a string method name.
 *
 * Like [DomainSplitBytecode], the markers ([org.bmc4j.Bmc.assumeEvery] / [org.bmc4j.Bmc.assumeStable])
 * are NOT sequential statements: a call appearing ANYWHERE in the proof installs the micro-model for
 * the WHOLE analysis — including calls inside `<clinit>` and inside callees the proof doesn't control.
 * That reach (a call site a local `assume` can't touch) is the whole point.
 *
 * ## How it reads the target STATICALLY (no invokedynamic execution)
 * `Bmc.assumeEvery(repo::findById) { ... }` compiles, immediately before the marker `invokestatic`, to
 * two `invokedynamic` sites bound by `java.lang.invoke.LambdaMetafactory`: one for the method reference
 * `repo::findById`, one for the predicate lambda. We never symbolically execute either indy (the
 * invokedynamic fault line). Instead we read the **bootstrap method arguments** — `bsmArgs[1]` is a
 * [Handle] carrying the implementation method's exact `owner.name:descriptor`:
 * - the REFERENCE indy's handle is the dependency method to shadow (`UserRepository.findById:(I)LUser;`);
 * - the PREDICATE indy's handle is the compiled lambda body (`Proof.lambda$..:(LUser;[,..])Z`).
 *
 * This is the same static-constant read [LambdaBytecode] / [StringBytecode] use to sidestep indy.
 * Crucially, when the marker parameter types are plain Java functional interfaces (which they are on
 * [org.bmc4j.Bmc]), a KOTLIN caller's `repo::findById` SAM-converts to the identical LambdaMetafactory
 * indy — so one decoder serves Java and Kotlin across compiler versions.
 *
 * ## Lowering (onto the contracts machinery)
 * For each marker we shadow the target with a constrained-nondet stub on the analysis classpath and
 * redirect every call site of the target to it (reusing [ContractRewriter]):
 *
 * ```
 * static R target__assumeStub(<self?,> args) {     // self prepended for an instance target
 *     R r = nondet();
 *     Bmc.assume(predicate(r <, self?, args>));     // the user's pure predicate
 *     return r;
 * }
 * ```
 *
 * `assumeEvery` is **fresh per call** — every call returns any output satisfying the predicate, a sound
 * over-approximation. `assumeStable` **memoizes** `r` in a static, initialised once, so the whole run
 * (every call site, including `<clinit>`) sees one fixed value — the environment/config case.
 *
 * ## Soundness
 * The micro-model is an ASSUMPTION (constrained nondet via `assume`, never `assert`): fresh output
 * bounded only by the predicate, so a property proven on top of it holds for any real implementation
 * that respects the predicate. It is surfaced on the verdict ([org.bmc4j.junit.BmcProofExtension]) so a
 * VERIFIED reached under an assumed contract is flagged NOT unconditional. An over-tight predicate makes
 * the assume unsatisfiable → VACUOUS, surfaced by the existing vacuity detection. The predicate is
 * certified PURE by [ContractPurityAudit] (same audit the annotation contracts use); an impure predicate
 * is rejected loudly. The shadow is per-proof — it applies to this proof's analysis classpath only.
 */
internal object AssumeContractBytecode {

    private const val BMC = "org/bmc4j/Bmc"
    private const val CPROVER = "org/cprover/CProver"
    private const val METAFACTORY = "java/lang/invoke/LambdaMetafactory"

    /** The marker method names on [org.bmc4j.Bmc]. */
    private const val ASSUME_EVERY = "assumeEvery"
    private const val ASSUME_STABLE = "assumeStable"

    /** Internal name of the generated stub class folded onto the analysis classpath, one per proof.
     *  Deliberately OUTSIDE the `org/bmc4j` namespace: JBMC does not lazily load (and thus never enters)
     *  a class under the bmc4j runtime's own package — exactly like the user-package `__BmcStubs` the
     *  annotation contracts generate, this lives in a neutral synthetic package so the engine treats it
     *  as ordinary analysis code and resolves the redirected `invokestatic` to its real body. */
    private const val STUB_CLASS = "bmc4jgen/AssumeContractStubs"

    /**
     * One decoded assumed contract: the dependency method to shadow + the pure predicate that constrains
     * its output. [stable] selects fresh-per-call (`assumeEvery`) vs memoized-once (`assumeStable`).
     *
     * @property targetOwner internal name of the class declaring the shadowed method
     * @property targetName the shadowed method's name
     * @property targetDesc the shadowed method's real descriptor (the dependency's own signature)
     * @property targetIsStatic whether the reference is to a static method (no receiver on the stack)
     * @property predOwner internal name of the class declaring the compiled predicate lambda
     * @property predName the predicate method's name
     * @property predDesc the predicate method's descriptor — `(R<,self?,args>)Z` (args present iff
     *   args-aware), with reference SAM-erasure where the lambda was typed `Object`
     */
    class Decoded(
            @JvmField val targetOwner: String,
            @JvmField val targetName: String,
            @JvmField val targetDesc: String,
            @JvmField val targetIsStatic: Boolean,
            @JvmField val predOwner: String,
            @JvmField val predName: String,
            @JvmField val predDesc: String,
            @JvmField val stable: Boolean) {

        /** The stub method name: a per-target unique, descriptor-keyed symbol on [STUB_CLASS]. Kept free
         *  of `$` (JBMC's method resolver treats `$`-laden synthetic names specially) — a plain
         *  `name__assumeStub_<hash>` like the annotation contracts' `name__stub`. */
        val stubName: String
            get() = "${targetName}__assumeStub_" + Integer.toHexString(
                    "$targetOwner$targetName$targetDesc$stable".hashCode())

        /** Descriptor of the generated static stub: the receiver is prepended for an instance target
         *  (it already sits below the args on the call site's operand stack), the real return kept. */
        val stubDesc: String
            get() = if (targetIsStatic) {
                targetDesc
            } else {
                "(L$targetOwner;" + targetDesc.substring(1)
            }

        /** Human-readable `Owner.method` (dot form) for the verdict footnote. */
        val display: String
            get() = "${targetOwner.replace('/', '.')}.$targetName"

        /** The [ContractRewriter.Redirect] that swaps the target's call sites for the stub. */
        fun redirect(): ContractRewriter.Redirect =
                ContractRewriter.Redirect(targetOwner, targetName, targetDesc, STUB_CLASS, stubName,
                        instance = !targetIsStatic, stubDescriptor = stubDesc)
    }

    /**
     * A malformed `assumeEvery`/`assumeStable` site (the marker's reference/predicate arguments weren't
     * the expected pair of `LambdaMetafactory` indys, or the predicate arity doesn't match the target).
     * Unchecked so it propagates out of the analysis path and fails the proof LOUD — a marker we can't
     * lower soundly must never silently run as an ordinary proof (which would drop the assumption and
     * could read as a false VERIFIED or a phantom REFUTED).
     */
    class AssumeContractError(message: String) : RuntimeException(message)

    /**
     * Decode every `assumeEvery`/`assumeStable` marker in [entryClass].[methodName] on [classpath].
     * Empty when the proof declares none. Reads the ORIGINAL (pre-desugar) bytes so the reference /
     * predicate indys are still present (the desugar passes would otherwise have rewritten them into
     * factory calls).
     */
    @JvmStatic
    fun decode(classpath: String, entryClass: String, methodName: String): List<Decoded> {
        val internal = entryClass.replace('.', '/')
        val bytes = readClass(classpath, internal) ?: return emptyList()
        return decodeBytes(bytes, methodName)
    }

    /**
     * The assumed output-contract DISPLAYS (`"Owner.method"`, `(stable)`-suffixed for an
     * `assumeStable`) the proof at [entryClass].[entryFunction] declares — the verdict footnote the
     * proof extension prints to flag a VERIFIED reached under an assumed contract as NOT unconditional.
     * Re-decoded from the original [classpath] (the decode is deterministic from the proof source, so it
     * needn't ride through the verdict cache — surfaces on the live AND the cached path). Empty when the
     * proof declares none, or when the entry class can't be read (fail-quiet: no footnote, never a thrown
     * exception out of a green path).
     */
    @JvmStatic
    fun displays(entryClass: String, entryFunction: String, classpath: String): List<String> = try {
        val method = entryFunction.substringAfterLast('.')
        decode(classpath, entryClass, method).map {
            it.display + (if (it.stable) " (stable)" else "")
        }
    } catch (e: RuntimeException) {
        emptyList()
    }

    /** [decode] over already-loaded class bytes. Exposed for unit tests. */
    internal fun decodeBytes(bytes: ByteArray, methodName: String): List<Decoded> {
        val out = ArrayList<Decoded>()
        ClassReader(bytes).accept(object : ClassVisitor(Opcodes.ASM9) {
            override fun visitMethod(a: Int, n: String?, d: String?, s: String?,
                                     ex: Array<String>?): MethodVisitor? {
                if (n != methodName) {
                    return null
                }
                return MarkerScanner(out)
            }
        }, 0)
        return out
    }

    /**
     * Scans one method linearly, remembering the last two SAM-conversion indy handles seen so that when
     * an `assumeEvery`/`assumeStable` `invokestatic` is reached, the reference handle (penultimate) and
     * predicate handle (last) on the operand stack are known. The compiler emits them in source order —
     * reference first, predicate second — directly before the marker call, so the linear two-slot window
     * is exact for a well-formed site; anything else throws [AssumeContractError].
     */
    private class MarkerScanner(private val out: MutableList<Decoded>) : MethodVisitor(Opcodes.ASM9) {

        private var refHandle: Handle? = null
        private var predHandle: Handle? = null

        override fun visitInvokeDynamicInsn(name: String?, descriptor: String?, bsm: Handle?,
                                            vararg bsmArgs: Any?) {
            val impl = samImplHandle(bsm, bsmArgs)
            if (impl != null) {
                // Slide the two-slot window: the older handle becomes the reference, the new one the
                // predicate. A well-formed marker site is exactly [ref-indy, pred-indy, marker-call].
                refHandle = predHandle
                predHandle = impl
            }
        }

        override fun visitMethodInsn(op: Int, owner: String?, name: String?, desc: String?,
                                     itf: Boolean) {
            if (op == Opcodes.INVOKESTATIC && owner == BMC
                    && (name == ASSUME_EVERY || name == ASSUME_STABLE)) {
                out.add(build(name == ASSUME_STABLE))
                refHandle = null
                predHandle = null
            }
        }

        private fun build(stable: Boolean): Decoded {
            val ref = refHandle ?: throw AssumeContractError(
                    "an assumeEvery/assumeStable marker is missing its method-reference argument as a" +
                            " resolvable LambdaMetafactory site — pass a direct bound/static method" +
                            " reference (e.g. repo::findById), not a stored function value.")
            val pred = predHandle ?: throw AssumeContractError(
                    "an assumeEvery/assumeStable marker is missing its predicate lambda as a resolvable" +
                            " LambdaMetafactory site — pass the predicate inline as a lambda.")
            val targetIsStatic = ref.tag == Opcodes.H_INVOKESTATIC
            // Args-aware predicates take (result, args...); output-only take (result). The reference's
            // own arg count is the call's argument count; the predicate beyond its leading result arg
            // must be either 0 (output-only) or exactly that arg count (args-aware).
            val targetArgs = Type.getArgumentTypes(ref.desc).size
            val predExtra = Type.getArgumentTypes(pred.desc).size - 1
            if (predExtra != 0 && predExtra != targetArgs) {
                throw AssumeContractError(
                        "the predicate for ${ref.owner}.${ref.name} takes ${predExtra + 1} arguments;" +
                                " an assumeEvery predicate must take either the result alone (1) or the" +
                                " result plus all ${targetArgs} call arguments (${targetArgs + 1}).")
            }
            return Decoded(ref.owner, ref.name, ref.desc, targetIsStatic,
                    pred.owner, pred.name, pred.desc, stable)
        }
    }

    /** The implementation [Handle] (`bsmArgs[1]`) of a `LambdaMetafactory` SAM-conversion indy, or null
     *  for any other indy (which we leave to the residual-indy machinery). */
    private fun samImplHandle(bsm: Handle?, bsmArgs: Array<out Any?>): Handle? {
        if (bsm != null && METAFACTORY == bsm.owner
                && (bsm.name == "metafactory" || bsm.name == "altMetafactory")
                && bsmArgs.size >= 2 && bsmArgs[1] is Handle) {
            return bsmArgs[1] as Handle
        }
        return null
    }

    private val CACHE = ConcurrentHashMap<String, String>()

    /**
     * Install the decoded assumed contracts of [entryClass].[methodName] onto [classpath]: generate the
     * shadow-stub class, fold it onto the analysis classpath (leading, so it shadows nothing real), and
     * redirect the target call sites to the stubs (reusing [ContractRewriter]). A no-op (returns
     * [classpath]) when the proof declares no markers. Memoized per (classpath, class, method).
     */
    @JvmStatic
    fun install(classpath: String, entryClass: String, methodName: String,
                decoded: List<Decoded>): String {
        if (decoded.isEmpty()) {
            return classpath
        }
        val key = "$classpath|$entryClass|$methodName"
        return CACHE.computeIfAbsent(key) {
            val stubDir = writeStubClass(decoded)
            val withStub = stubDir.toString() + File.pathSeparator + classpath
            // The redirect rewrite runs over the whole (stub-prefixed) classpath so the dependency's call
            // sites — wherever they are, including <clinit> and uncontrolled callees — route to the stub.
            ContractRewriter.rewrite(withStub, decoded.map { it.redirect() }, null)
        }
    }

    /** The predicate methods this proof's assumed contracts invoke — `owner.name(desc)` redirects fed to
     *  [ContractPurityAudit] so an impure predicate is rejected exactly like an impure annotation
     *  contract. Each predicate is a static synthetic lambda body; a static stub redirect over it lets
     *  the existing audit walk certify it pure-by-construction. */
    @JvmStatic
    fun predicateRedirects(decoded: List<Decoded>): List<ContractRewriter.Redirect> =
            decoded.map {
                // owner.name(predDesc) -> itself; only the OWNER/NAME/DESC are read by the audit walk,
                // which treats the redirect's target as the root method to certify pure.
                ContractRewriter.Redirect(it.predOwner, it.predName, it.predDesc,
                        it.predOwner, it.predName)
            }

    /** Emit [STUB_CLASS] holding one constrained-nondet stub per decoded contract into a fresh mirror
     *  dir, and return that dir. The class is regenerated per (proof) call but content-addressed by the
     *  mirror, so identical decodings reuse one dir. */
    private fun writeStubClass(decoded: List<Decoded>): Path {
        val bytes = generateStubClass(decoded)
        // Reuse the mirror's content-addressed, atomic publish for the generated class — keyed by the
        // stub bytes themselves so two distinct decodings never alias one dir.
        val dir = Files.createTempDirectory("bmc-assume-")
        val classFile = dir.resolve("$STUB_CLASS.class")
        Files.createDirectories(classFile.parent)
        Files.write(classFile, bytes)
        dir.toFile().deleteOnExit()
        return dir
    }

    /** Build the stub class: `R name(<self?,>args){ R r = [memoized] nondet(); assume(pred(...)); return r; }`.
     *  COMPUTE_FRAMES (the stable stub has an init-once branch) with a non-loading
     *  [getCommonSuperClass] — the stub's referenced types (the dependency's return type, the predicate
     *  owner) are analysis-only and may not be loadable here; the only frame merge is over identical or
     *  Object-compatible references, so collapsing every merge to Object is sound for these stubs. */
    internal fun generateStubClass(decoded: List<Decoded>): ByteArray {
        val cw = object : ClassWriter(COMPUTE_FRAMES) {
            override fun getCommonSuperClass(type1: String, type2: String): String =
                    if (type1 == type2) type1 else "java/lang/Object"
        }
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC or Opcodes.ACC_FINAL or Opcodes.ACC_SYNTHETIC,
                STUB_CLASS, null, "java/lang/Object", null)
        for (d in decoded) {
            if (d.stable) {
                emitStableField(cw, d)
            }
            emitStub(cw, d)
        }
        cw.visitEnd()
        return cw.toByteArray()
    }

    /** The memo field for an `assumeStable` target: `static R <stub>$value;` plus a `static boolean
     *  <stub>$init;` init-once guard, so every call site (and `<clinit>`) reuses one fixed symbol. */
    private fun emitStableField(cw: ClassWriter, d: Decoded) {
        val ret = Type.getReturnType(d.targetDesc)
        cw.visitField(Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC, d.stubName + "__value",
                ret.descriptor, null, null).visitEnd()
        cw.visitField(Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC, d.stubName + "__init",
                "Z", null, null).visitEnd()
    }

    private fun emitStub(cw: ClassWriter, d: Decoded) {
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC or Opcodes.ACC_SYNTHETIC,
                d.stubName, d.stubDesc, null, null)
        mv.visitCode()
        val ret = Type.getReturnType(d.targetDesc)
        // Local layout: the stub's declared params occupy slots [0, paramSlots); the havoc'd result `r`
        // goes in the next free slot.
        val paramTypes = Type.getArgumentTypes(d.stubDesc)
        var rSlot = 0
        for (p in paramTypes) {
            rSlot += p.size
        }

        if (d.stable) {
            emitStableLoadOrInit(mv, d, ret, rSlot)
        } else {
            pushNondet(mv, ret)
            mv.visitVarInsn(ret.getOpcode(Opcodes.ISTORE), rSlot)
        }

        // assume(predicate(r <, params...>)): output-only predicates ignore the params.
        emitPredicateCall(mv, d, ret, rSlot, paramTypes)

        mv.visitVarInsn(ret.getOpcode(Opcodes.ILOAD), rSlot)
        mv.visitInsn(ret.getOpcode(Opcodes.IRETURN))
        mv.visitMaxs(0, 0)
        mv.visitEnd()
    }

    /** Stable: `if (!init) { value = nondet(); assume(pred(value, params)); init = true; } r = value;`.
     *  The assume is INSIDE the init guard so the predicate constrains the single symbol exactly once;
     *  every later call reloads the same constrained value. */
    private fun emitStableLoadOrInit(mv: MethodVisitor, d: Decoded, ret: Type, rSlot: Int) {
        val valueField = d.stubName + "__value"
        val initField = d.stubName + "__init"
        val afterInit = org.objectweb.asm.Label()
        mv.visitFieldInsn(Opcodes.GETSTATIC, STUB_CLASS, initField, "Z")
        mv.visitJumpInsn(Opcodes.IFNE, afterInit)
        // value = nondet()
        pushNondet(mv, ret)
        mv.visitFieldInsn(Opcodes.PUTSTATIC, STUB_CLASS, valueField, ret.descriptor)
        // assume(pred(value, params)) — load value into rSlot first so the shared predicate emit works.
        mv.visitFieldInsn(Opcodes.GETSTATIC, STUB_CLASS, valueField, ret.descriptor)
        mv.visitVarInsn(ret.getOpcode(Opcodes.ISTORE), rSlot)
        emitPredicateCall(mv, d, ret, rSlot, Type.getArgumentTypes(d.stubDesc))
        mv.visitInsn(Opcodes.ICONST_1)
        mv.visitFieldInsn(Opcodes.PUTSTATIC, STUB_CLASS, initField, "Z")
        mv.visitLabel(afterInit)
        // r = value
        mv.visitFieldInsn(Opcodes.GETSTATIC, STUB_CLASS, valueField, ret.descriptor)
        mv.visitVarInsn(ret.getOpcode(Opcodes.ISTORE), rSlot)
    }

    /** Emit `Bmc.assume(predicate(r <, params...>))`. The predicate's first arg is the result; an
     *  args-aware predicate also takes every call argument (the stub's params, minus the leading
     *  receiver for an instance target — the predicate is written over the call args, not the receiver). */
    private fun emitPredicateCall(mv: MethodVisitor, d: Decoded, ret: Type, rSlot: Int,
                                  paramTypes: Array<Type>) {
        val predArgs = Type.getArgumentTypes(d.predDesc)
        val argsAware = predArgs.size > 1
        // Load the result, coerced to the predicate's first parameter type (SAM erasure may have widened
        // a reference result to Object in the lambda descriptor).
        mv.visitVarInsn(ret.getOpcode(Opcodes.ILOAD), rSlot)
        coerce(mv, ret, predArgs[0])
        if (argsAware) {
            // The call arguments are the stub params AFTER the leading receiver (instance target) or all
            // of them (static target). Load each, coerced to the predicate's corresponding param type.
            val firstCallParam = if (d.targetIsStatic) 0 else 1
            var slot = 0
            for (i in 0 until firstCallParam) {
                slot += paramTypes[i].size
            }
            for (i in firstCallParam until paramTypes.size) {
                val src = paramTypes[i]
                mv.visitVarInsn(src.getOpcode(Opcodes.ILOAD), slot)
                coerce(mv, src, predArgs[1 + (i - firstCallParam)])
                slot += src.size
            }
        }
        // The predicate lambda body is emitted by the compiler as a `private static` synthetic, so it is
        // always an invokestatic; LambdaBytecode has bumped it to public on the analysis classpath.
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, d.predOwner, d.predName, d.predDesc, false)
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, CPROVER, "assume", "(Z)V", false)
    }

    private fun pushNondet(mv: MethodVisitor, t: Type) {
        when (t.sort) {
            Type.INT, Type.SHORT, Type.BYTE, Type.CHAR, Type.BOOLEAN ->
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, CPROVER, nondetName(t), "()" + t.descriptor, false)
            Type.LONG ->
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, CPROVER, "nondetLong", "()J", false)
            Type.FLOAT ->
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, CPROVER, "nondetFloat", "()F", false)
            Type.DOUBLE ->
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, CPROVER, "nondetDouble", "()D", false)
            else -> {
                // Reference / array result: an arbitrary value INCLUDING null (the predicate decides
                // whether null is admissible — `it == null || …` is the canonical form, a SOUND
                // over-approximation that must keep the null case reachable).
                //
                // We must NOT use `nondetWithNull()` directly: JBMC represents its result as an
                // under-modelled `&_constarray` placeholder whose object fields are not consistently
                // tracked, so a field read inside the predicate and the same read in the caller can
                // disagree — the assumption then fails to constrain the caller's view. Instead we havoc a
                // PROPERLY-MODELLED fresh object with `nondetWithoutNull()` (the form the annotation
                // contracts use, whose fields ARE symbolically tracked) and havoc null-ness separately:
                //   r = nondetBoolean() ? null : (R) nondetWithoutNull();
                // so the result still ranges over null AND every well-modelled object — the sound
                // over-approximation — while a non-null object's fields stay consistent across reads.
                val nullLabel = org.objectweb.asm.Label()
                val done = org.objectweb.asm.Label()
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, CPROVER, "nondetBoolean", "()Z", false)
                mv.visitJumpInsn(Opcodes.IFNE, nullLabel)
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, CPROVER, "nondetWithoutNull",
                        "()Ljava/lang/Object;", false)
                if (t.internalName != "java/lang/Object") {
                    mv.visitTypeInsn(Opcodes.CHECKCAST, t.internalName)
                }
                mv.visitJumpInsn(Opcodes.GOTO, done)
                mv.visitLabel(nullLabel)
                mv.visitInsn(Opcodes.ACONST_NULL)
                mv.visitLabel(done)
            }
        }
    }

    private fun nondetName(t: Type): String = when (t.sort) {
        Type.INT -> "nondetInt"
        Type.SHORT -> "nondetShort"
        Type.BYTE -> "nondetByte"
        Type.CHAR -> "nondetChar"
        Type.BOOLEAN -> "nondetBoolean"
        else -> throw IllegalArgumentException("not an int-family type: $t")
    }

    /** Coerce the top-of-stack value from [src] to [dst] (box/unbox/cast), mirroring [LambdaBytecode]'s
     *  adapter so the SAM-erased predicate parameter types line up with the stub's real value types. */
    private fun coerce(mv: MethodVisitor, src: Type, dst: Type) {
        if (src.descriptor == dst.descriptor) {
            return
        }
        val srcPrim = src.sort in Type.BOOLEAN..Type.DOUBLE
        val dstPrim = dst.sort in Type.BOOLEAN..Type.DOUBLE
        when {
            srcPrim && dstPrim -> widen(mv, src, dst)
            srcPrim -> { // box
                val w = wrapper(src)
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, w, "valueOf",
                        "(" + src.descriptor + ")L" + w + ";", false)
            }
            dstPrim -> { // unbox
                val w = wrapper(dst)
                mv.visitTypeInsn(Opcodes.CHECKCAST, w)
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, w, unbox(dst), "()" + dst.descriptor, false)
            }
            dst.internalName != "java/lang/Object" -> mv.visitTypeInsn(Opcodes.CHECKCAST, dst.internalName)
        }
    }

    private fun widen(mv: MethodVisitor, src: Type, dst: Type) {
        if (src.sort <= Type.INT) {
            when (dst.sort) {
                Type.LONG -> mv.visitInsn(Opcodes.I2L)
                Type.FLOAT -> mv.visitInsn(Opcodes.I2F)
                Type.DOUBLE -> mv.visitInsn(Opcodes.I2D)
            }
        } else if (src.sort == Type.LONG) {
            when (dst.sort) {
                Type.FLOAT -> mv.visitInsn(Opcodes.L2F)
                Type.DOUBLE -> mv.visitInsn(Opcodes.L2D)
            }
        } else if (src.sort == Type.FLOAT && dst.sort == Type.DOUBLE) {
            mv.visitInsn(Opcodes.F2D)
        }
    }

    private fun wrapper(t: Type): String = when (t.sort) {
        Type.BOOLEAN -> "java/lang/Boolean"
        Type.CHAR -> "java/lang/Character"
        Type.BYTE -> "java/lang/Byte"
        Type.SHORT -> "java/lang/Short"
        Type.INT -> "java/lang/Integer"
        Type.LONG -> "java/lang/Long"
        Type.FLOAT -> "java/lang/Float"
        Type.DOUBLE -> "java/lang/Double"
        else -> throw IllegalArgumentException("not a primitive: $t")
    }

    private fun unbox(t: Type): String = when (t.sort) {
        Type.BOOLEAN -> "booleanValue"
        Type.CHAR -> "charValue"
        Type.BYTE -> "byteValue"
        Type.SHORT -> "shortValue"
        Type.INT -> "intValue"
        Type.LONG -> "longValue"
        Type.FLOAT -> "floatValue"
        Type.DOUBLE -> "doubleValue"
        else -> throw IllegalArgumentException("not a primitive: $t")
    }

    /** Read the bytes of `internalName` from the first [classpath] entry that holds it (classpath order),
     *  searching directory and jar entries. Null when absent; a bad entry is skipped, never throws. */
    private fun readClass(classpath: String, internalName: String): ByteArray? {
        val resource = "$internalName.class"
        for (entry in classpath.split(File.pathSeparator)) {
            if (entry.isEmpty()) {
                continue
            }
            try {
                val p = Path.of(entry)
                if (Files.isDirectory(p)) {
                    val f = p.resolve(resource)
                    if (Files.isRegularFile(f)) {
                        return Files.readAllBytes(f)
                    }
                } else if (Files.isRegularFile(p)
                        && (entry.endsWith(".jar", true) || entry.endsWith(".zip", true))) {
                    ZipFile(p.toFile()).use { zf ->
                        val e = zf.getEntry(resource)
                        if (e != null) {
                            return zf.getInputStream(e).use { it.readAllBytes() }
                        }
                    }
                }
            } catch (e: Exception) {
                // skip a bad entry; the next may hold the class
            }
        }
        return null
    }
}
