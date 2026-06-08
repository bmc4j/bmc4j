package org.bmc4j.engine

import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile

/**
 * The `domainSplit` / `slice` marker rewriter — partitions a slow proof's claimed input domain into
 * N parallel slices plus one soundness cover check, expanding ONE annotated proof into N+1 derived
 * verification runs (see [org.bmc4j.junit.BmcProofExtension] for the orchestration and verdict
 * aggregation).
 *
 * The markers ([org.bmc4j.Bmc.domainSplit] / [org.bmc4j.Bmc.slice]) work exactly like
 * `check`/`assume`: the boolean argument is NOT evaluated at runtime — the engine analyses the
 * bytecode that COMPUTES it. The condition for each marker sits inline in the proof body, ending
 * right before the `INVOKESTATIC org/bmc4j/Bmc.{domainSplit,slice}(Z)V` call. This pass never tries
 * to *extract and re-emit* that expression bytecode elsewhere (which would mean composing a
 * disjunction across separate instruction sequences); instead it rewrites the marker CALLS in place,
 * leaving each condition's computation exactly where the compiler put it:
 *
 * - **A slice run** (`[RunPlan.slice]`) verifies the original body restricted to one sub-domain. The
 *   i-th `slice(c_i)` call becomes `CProver.assume(c_i)` (the boolean is already on the stack —
 *   identical `(Z)V` descriptor, a pure owner/name swap, the same move [KotlinParamBytecode] makes);
 *   the `domainSplit` call and every OTHER `slice` call become `POP` (their condition is still
 *   computed — side-effect-free reads of the proof's locals — but the value is discarded). The body
 *   after the split then runs once under that one assumption.
 *
 * - **The cover run** (`[RunPlan.cover]`) verifies `overall => (c1 || c2 || ... || cn)` and runs NONE
 *   of the body. `domainSplit(overall)` becomes `ISTORE overallSlot ; ICONST_0 ; ISTORE unionSlot`
 *   (record the overall condition, init the union accumulator to false); each `slice(c_k)` becomes
 *   `unionSlot |= c_k`. Immediately after the LAST marker the pass injects
 *   `check(!overallSlot || unionSlot)` followed by a `return`, so the cover obligation is checked and
 *   the proof body never runs. The check direction is **subset** (`overall => union`), which forbids
 *   gaps — a point in the declared domain no slice covers — while allowing harmless overlap.
 *
 * The synthetic locals live above the method's own `maxLocals` (recorded by the plan), so they never
 * collide with the proof's variables. The pass is keyed per (class, method, run) so each derived run
 * gets its own mirror — [JbmcBackend] runs the rewrite chain once per request.
 *
 * Mirrors the sibling passes' [ClasspathMirror] mechanics; the run identity is folded into the mirror
 * key so the cover mirror and each slice mirror are distinct, complete cache entries.
 */
object DomainSplitBytecode {

    const val BMC = "org/bmc4j/Bmc"
    const val CPROVER = "org/cprover/CProver"
    private const val BOOL_DESC = "(Z)V"
    const val DOMAIN_SPLIT = "domainSplit"
    const val SLICE = "slice"
    private const val BMC_PROOF_DESC = "Lorg/bmc4j/BmcProof;"

    /** Which derived run a rewrite produces. */
    sealed interface RunPlan {
        /** The i-th slice run (0-based): keep `assume(slice_i)`, discard the other marker conditions. */
        data class Slice(val index: Int) : RunPlan
        /** The single cover run: verify `overall => union(slices)`, skip the body. */
        object Cover : RunPlan
    }

    /**
     * The static analysis of ONE proof method's domain-split markers — how the extension decides how
     * many runs to launch, and the source of the one-split-per-proof / orphan-slice processing errors.
     *
     * @property sliceCount the number of `slice(...)` markers (0 when the proof has no split)
     * @property hasSplit whether the method contains a `domainSplit(...)` marker
     */
    data class Plan(val sliceCount: Int, val hasSplit: Boolean) {
        val isSplit: Boolean get() = hasSplit
    }

    /**
     * A processing-time error in a proof's domain-split markers (two `domainSplit`, a `slice` with no
     * `domainSplit`, or a split with no slices). Unchecked so it propagates out of the analysis path
     * and fails the proof LOUD — a malformed split must never silently run as an ordinary proof.
     */
    class DomainSplitError(message: String) : RuntimeException(message)

    /**
     * Analyse the marker usage of [entryClass].[methodName] on [classpath]. Returns a [Plan]
     * describing the split (or `Plan(0, false)` when the method has no markers), and THROWS a
     * [DomainSplitError] for a malformed split:
     * - two or more `domainSplit(...)` calls in one method;
     * - a `slice(...)` with no `domainSplit(...)`;
     * - a `domainSplit(...)` with zero `slice(...)` children.
     */
    @JvmStatic
    fun analyze(classpath: String, entryClass: String, methodName: String): Plan {
        val internalName = entryClass.replace('.', '/')
        val bytes = readClassFromClasspath(classpath, internalName)
                ?: return Plan(0, false)
        return analyzeBytes(bytes, methodName)
    }

    /** [analyze] over already-loaded class bytes. Exposed for unit tests. */
    internal fun analyzeBytes(bytes: ByteArray, methodName: String): Plan {
        var splitCount = 0
        var sliceCount = 0
        val cr = ClassReader(bytes)
        cr.accept(object : ClassVisitor(Opcodes.ASM9) {
            override fun visitMethod(a: Int, n: String?, d: String?, s: String?,
                                     ex: Array<String>?): MethodVisitor? {
                if (n != methodName) {
                    return null
                }
                return object : MethodVisitor(Opcodes.ASM9) {
                    override fun visitMethodInsn(op: Int, owner: String?, name: String?,
                                                 desc: String?, itf: Boolean) {
                        if (op == Opcodes.INVOKESTATIC && owner == BMC && desc == BOOL_DESC) {
                            when (name) {
                                DOMAIN_SPLIT -> splitCount++
                                SLICE -> sliceCount++
                            }
                        }
                    }
                }
            }
        }, 0)

        if (splitCount > 1) {
            throw DomainSplitError(
                    "$methodName declares $splitCount domainSplit(...) markers — at most ONE is" +
                            " allowed per proof. Nesting would multiply slices (N×M) and turn the cover" +
                            " check into a 2D tiling problem. To split along two axes, put a conjunction" +
                            " inside a single slice(...).")
        }
        if (splitCount == 0 && sliceCount > 0) {
            throw DomainSplitError(
                    "$methodName calls slice(...) with no enclosing domainSplit(...). Every slice" +
                            " registers to a domainSplit that declares the claimed overall domain — add" +
                            " `Bmc.domainSplit(overallCondition)` before the slices.")
        }
        if (splitCount == 1 && sliceCount == 0) {
            throw DomainSplitError(
                    "$methodName calls domainSplit(...) with no slice(...) children. A domain split" +
                            " needs at least one slice(...) sub-domain to partition into.")
        }
        return Plan(sliceCount, splitCount == 1)
    }

    /**
     * Read the bytes of [internalName] (`a/b/C`) from the first [classpath] entry that holds it
     * (classpath order, exactly as the JVM/JBMC resolve), or null when absent. Both directory and jar
     * entries are searched. Fail-safe: a bad/locked entry is skipped, never throws.
     */
    private fun readClassFromClasspath(classpath: String, internalName: String): ByteArray? {
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
                // skip a bad entry; the next one may hold the class
            }
        }
        return null
    }

    private val CACHE = ConcurrentHashMap<String, String>()

    /**
     * Rewrite [classpath] for one derived [run] of the split proof at [entryClass].[methodName].
     * Memoized per (classpath, class, method, run). Both directory and jar entries are mirrored.
     */
    @JvmStatic
    fun rewrite(classpath: String, entryClass: String, methodName: String, run: RunPlan): String {
        val key = "$classpath|$entryClass|$methodName|${runKey(run)}"
        return CACHE.computeIfAbsent(key) {
            ClasspathMirror.mirror(classpath, "domainsplit", { b ->
                ClasspathMirror.Transformed(rewriteClass(b, entryClass.replace('.', '/'), methodName, run))
            }, runKey(run) + "|" + entryClass + "|" + methodName)
        }
    }

    private fun runKey(run: RunPlan): String = when (run) {
        is RunPlan.Slice -> "slice${run.index}"
        RunPlan.Cover -> "cover"
    }

    /**
     * Rewrite the markers of [internalName].[methodName] for [run]; every other method and class is
     * copied verbatim. Exposed for unit tests.
     */
    internal fun rewriteClass(bytes: ByteArray, internalName: String, methodName: String,
                              run: RunPlan): ByteArray {
        val cr = ClassReader(bytes)
        val thisInternal = cr.className
        if (thisInternal != internalName) {
            return bytes // not the entry class — nothing to rewrite
        }
        // Count the markers up front so the cover transform knows WHICH marker is last (it injects the
        // cover finale right after it). COMPUTE_MAXS: we add a handful of slots (stores, the check
        // arguments) but introduce no new branch targets that invalidate existing frames in the slice
        // case; the cover case adds a jump, so COMPUTE_FRAMES is used there. Decide per-run below.
        val plan = analyzeBytes(bytes, methodName)
        if (!plan.hasSplit) {
            return bytes // defensive: nothing to do without a split
        }
        // The cover run inserts an `if`, so it needs full frame computation; the slice run only swaps
        // calls for assume/POP (no new control flow), so COMPUTE_MAXS suffices and avoids loading
        // classes for frame merge.
        val flags = if (run is RunPlan.Cover) ClassWriter.COMPUTE_FRAMES else ClassWriter.COMPUTE_MAXS
        val cw = ClassWriter(cr, flags)
        val cv = object : ClassVisitor(Opcodes.ASM9, cw) {
            override fun visitMethod(a: Int, n: String?, d: String?, s: String?,
                                     ex: Array<String>?): MethodVisitor {
                val mv = super.visitMethod(a, n, d, s, ex)
                if (n != methodName) {
                    return mv
                }
                return when (run) {
                    is RunPlan.Slice -> SliceMethodVisitor(mv, run.index)
                    RunPlan.Cover -> CoverMethodVisitor(mv, plan.sliceCount)
                }
            }
        }
        cr.accept(cv, 0)
        return cw.toByteArray()
    }

    /**
     * SLICE run: route the chosen slice's condition to `assume`, discard every other marker's
     * condition with `POP`. No new control flow — the body after the split runs under that one
     * assumption.
     */
    private class SliceMethodVisitor(mv: MethodVisitor, private val keepIndex: Int) :
            MethodVisitor(Opcodes.ASM9, mv) {

        private var sliceSeen = 0
        private var isProof = false

        override fun visitAnnotation(descriptor: String?, visible: Boolean): AnnotationVisitor? {
            if (BMC_PROOF_DESC == descriptor) {
                isProof = true
            }
            return super.visitAnnotation(descriptor, visible)
        }

        override fun visitMethodInsn(op: Int, owner: String?, name: String?, desc: String?,
                                     itf: Boolean) {
            if (op == Opcodes.INVOKESTATIC && owner == BMC && desc == BOOL_DESC
                    && (name == DOMAIN_SPLIT || name == SLICE)) {
                if (name == SLICE) {
                    val idx = sliceSeen++
                    if (idx == keepIndex) {
                        // keep: assume(condition) — boolean already on the stack, same descriptor.
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, CPROVER, "assume", BOOL_DESC, false)
                        return
                    }
                }
                // domainSplit, or a non-chosen slice: discard the computed boolean.
                super.visitInsn(Opcodes.POP)
                return
            }
            super.visitMethodInsn(op, owner, name, desc, itf)
        }
    }

    /**
     * COVER run: build `overall => (c1 || ... || cn)` from the marker conditions and verify it without
     * running the body. `domainSplit(overall)` records `overall` into `overallSlot` and initialises
     * `unionSlot = false`; each `slice(c_k)` folds `unionSlot |= c_k`; after the LAST marker the cover
     * check `check(!overall || union)` is injected, then a `return` so the body is skipped.
     *
     * The synthetic locals sit above the method's parameter/local slots — ASM's COMPUTE_FRAMES sees
     * the stores and grows maxLocals — so they never alias a proof variable. The injected `if` is the
     * only new branch, which is why this run uses COMPUTE_FRAMES.
     */
    private class CoverMethodVisitor(mv: MethodVisitor, private val sliceCount: Int) :
            MethodVisitor(Opcodes.ASM9, mv) {

        // Synthetic local slots placed well above any realistic method's own locals. The cover proof
        // body is trivial (a few stores + the check), so a fixed high base is simpler than threading
        // maxLocals through, and COMPUTE_FRAMES tolerates the gap.
        private val overallSlot = SYNTH_BASE
        private val unionSlot = SYNTH_BASE + 1

        private var slicesSeen = 0
        private var coverEmitted = false

        override fun visitMethodInsn(op: Int, owner: String?, name: String?, desc: String?,
                                     itf: Boolean) {
            if (op == Opcodes.INVOKESTATIC && owner == BMC && desc == BOOL_DESC
                    && (name == DOMAIN_SPLIT || name == SLICE)) {
                when (name) {
                    DOMAIN_SPLIT -> {
                        // overallSlot = overall; unionSlot = false.
                        super.visitVarInsn(Opcodes.ISTORE, overallSlot)
                        super.visitInsn(Opcodes.ICONST_0)
                        super.visitVarInsn(Opcodes.ISTORE, unionSlot)
                    }
                    SLICE -> {
                        // unionSlot = unionSlot | c_k. The condition c_k is on the stack; OR it in.
                        super.visitVarInsn(Opcodes.ILOAD, unionSlot)
                        super.visitInsn(Opcodes.IOR)
                        super.visitVarInsn(Opcodes.ISTORE, unionSlot)
                        slicesSeen++
                        if (slicesSeen == sliceCount) {
                            emitCover()
                        }
                    }
                }
                return
            }
            super.visitMethodInsn(op, owner, name, desc, itf)
        }

        /**
         * Inject `Bmc.check(!overall || union)` then `return`, ending the cover proof before the body.
         * Subset cover: `overall => union` is `!overall || union`. We compute it as
         * `(overall == 0) || (union != 0)` and pass the boolean to `Bmc.check(Z)V`, then return.
         */
        private fun emitCover() {
            if (coverEmitted) {
                return
            }
            coverEmitted = true
            // Compute coverHolds = (!overall) || union, leaving a 0/1 int on the stack.
            val unionTrue = Label()
            val overallFalse = Label()
            val pushTrue = Label()
            val done = Label()

            // if (overall == 0) goto overallFalse   (overall false => implication holds)
            super.visitVarInsn(Opcodes.ILOAD, overallSlot)
            super.visitJumpInsn(Opcodes.IFEQ, overallFalse)
            // overall is true: holds iff union is true.
            super.visitVarInsn(Opcodes.ILOAD, unionSlot)
            super.visitJumpInsn(Opcodes.IFNE, unionTrue)
            // overall true AND union false => GAP: push false.
            super.visitInsn(Opcodes.ICONST_0)
            super.visitJumpInsn(Opcodes.GOTO, done)

            super.visitLabel(overallFalse)
            super.visitJumpInsn(Opcodes.GOTO, pushTrue)
            super.visitLabel(unionTrue)
            super.visitLabel(pushTrue)
            super.visitInsn(Opcodes.ICONST_1)

            super.visitLabel(done)
            // Bmc.check(coverHolds) — reuses the proof framework's assertion-error property.
            super.visitMethodInsn(Opcodes.INVOKESTATIC, BMC, "check", BOOL_DESC, false)
            // End the cover proof here — the body never runs. The method is void (a @BmcProof),
            // so a bare RETURN is correct; the reachability pass will later replace it as usual.
            super.visitInsn(Opcodes.RETURN)
        }

        companion object {
            private const val SYNTH_BASE = 200
        }
    }
}
