package org.bmc4j.engine

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile

/**
 * The `coldBranch` marker rewriter for SOUND BRANCH DECOMPOSITION (see [org.bmc4j.BmcBranchDecompose]
 * for the user-facing semantics; [org.bmc4j.junit.BmcProofExtension] for the orchestration + verdict
 * aggregation + localised-cost report).
 *
 * The marker ([org.bmc4j.Bmc.coldBranch]) works exactly like `check`/`assume`/`slice`: the boolean
 * argument is NOT evaluated at runtime - the engine analyses the bytecode that COMPUTES it. The
 * condition sits inline in the proof body, ending right before the
 * `INVOKESTATIC org/bmc4j/Bmc.coldBranch(Z)V` call. This pass never extracts and re-emits that
 * expression elsewhere; it rewrites the marker CALL in place, leaving the condition's computation
 * exactly where the compiler put it (the same in-place move [DomainSplitBytecode] makes).
 *
 * It expands ONE annotated proof into TWO independent derived runs:
 *
 * - **The LEAF run** ([RunPlan.Leaf]) proves the EXTRACTED branch on its own. The `coldBranch(cond)`
 *   call becomes `CProver.assume(cond)` (the boolean is already on the stack - identical `(Z)V`
 *   descriptor, a pure owner/name swap), so the proof body runs once under the branch's path-condition
 *   as its precondition. This is the contracts ENFORCE direction applied to a synthetic branch method:
 *   `assume(pre); run body; check(post)`.
 *
 * - **The PARENT run** ([RunPlan.Parent]) discharges the branch's proven summary at the call site. The
 *   `coldBranch(cond)` call becomes `cond; ICONST_1; IXOR; CProver.assume(...)` - i.e. `assume(!cond)`
 *   - so the parent proof never re-explores the (already-proven) cold branch. This is the contracts
 *   SUMMARIZE direction: the branch's obligations were discharged by the leaf, and its trivial summary
 *   (a cold branch in a `check`-style proof leaves no post-state the proof's remaining checks depend on)
 *   is assumed by simply pruning the branch's sub-domain. The negation reuses the boolean already on the
 *   stack: `b XOR 1` flips a clean 0/1, so the descriptor and stack are unchanged.
 *
 * Leaf + parent cover `cond || !cond` - the full input domain - so this is a SOUND case split, NOT
 * dead-branch deletion: the branch is proven (leaf), then summarized (parent), never dropped. Neither
 * run introduces NEW control flow (a marker call becomes a marker call, optionally with a `XOR 1`
 * prefix), so `COMPUTE_MAXS` suffices for both and no frames are recomputed.
 *
 * Mirrors the sibling passes' [ClasspathMirror] mechanics; the run identity is folded into the mirror
 * key so the leaf mirror and the parent mirror are distinct, complete cache entries.
 */
object BranchDecomposeBytecode {

    const val BMC = "org/bmc4j/Bmc"
    const val CPROVER = "org/cprover/CProver"
    private const val BOOL_DESC = "(Z)V"
    const val COLD_BRANCH = "coldBranch"

    /** Which derived run a rewrite produces. */
    sealed interface RunPlan {
        /** The leaf run: `assume(cond)` - the extracted branch under its path-condition. */
        object Leaf : RunPlan
        /** The parent run: `assume(!cond)` - the branch discharged as its (trivial) summary. */
        object Parent : RunPlan
    }

    /**
     * The static analysis of ONE proof method's `coldBranch` markers - how the extension decides
     * whether a proof is decomposed, and the source of the one-branch-per-proof processing error.
     *
     * @property branchCount the number of `coldBranch(...)` markers (0 when the proof has none)
     */
    data class Plan(val branchCount: Int) {
        /** True when this proof opted a branch into decomposition (has at least one marker). */
        val isDecomposed: Boolean get() = branchCount > 0
    }

    /**
     * A processing-time error in a proof's `coldBranch` markers (two or more in one method). Unchecked
     * so it propagates out of the analysis path and fails the proof LOUD - a malformed decomposition
     * must never silently run as an ordinary proof.
     */
    class BranchDecomposeError(message: String) : RuntimeException(PROCESSING_TAG + message) {
        companion object {
            /** Stable leading tag on every error message, so a malformed-decomposition PROCESSING error
             *  is recognisable across the test-worker -> Gradle boundary (the exception arrives as a
             *  PlaceholderException; the listener matches the message, not the type) and reads as a
             *  processing error rather than a "REFUTED" verdict on the runner line. */
            const val PROCESSING_TAG = "branch-decompose processing error: "
        }
    }

    /**
     * Analyse the `coldBranch` marker usage of [entryClass].[methodName] on [classpath]. Returns a
     * [Plan] (or `Plan(0)` when the method has no marker), and THROWS a [BranchDecomposeError] for two
     * or more markers (increment 1 supports exactly one extracted branch per proof).
     */
    @JvmStatic
    fun analyze(classpath: String, entryClass: String, methodName: String): Plan {
        val internalName = entryClass.replace('.', '/')
        val bytes = readClassFromClasspath(classpath, internalName) ?: return Plan(0)
        return analyzeBytes(bytes, methodName)
    }

    /** [analyze] over already-loaded class bytes. Exposed for unit tests. */
    internal fun analyzeBytes(bytes: ByteArray, methodName: String): Plan {
        var count = 0
        ClassReader(bytes).accept(object : ClassVisitor(Opcodes.ASM9) {
            override fun visitMethod(a: Int, n: String?, d: String?, s: String?,
                                     ex: Array<String>?): MethodVisitor? {
                if (n != methodName) {
                    return null
                }
                return object : MethodVisitor(Opcodes.ASM9) {
                    override fun visitMethodInsn(op: Int, owner: String?, name: String?,
                                                 desc: String?, itf: Boolean) {
                        if (op == Opcodes.INVOKESTATIC && owner == BMC && desc == BOOL_DESC
                                && name == COLD_BRANCH) {
                            count++
                        }
                    }
                }
            }
        }, 0)

        if (count > 1) {
            throw BranchDecomposeError(
                    "$methodName declares $count coldBranch(...) markers - at most ONE is allowed per" +
                            " proof in this increment. Extracting independent branches into a tree of" +
                            " obligations is a later increment; for now decompose a single cold branch," +
                            " or combine conditions into one coldBranch(...).")
        }
        return Plan(count)
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
     * Rewrite [classpath] for one derived [run] of the decomposed proof at [entryClass].[methodName].
     * Memoized per (classpath, class, method, run). Both directory and jar entries are mirrored.
     */
    @JvmStatic
    fun rewrite(classpath: String, entryClass: String, methodName: String, run: RunPlan): String {
        val key = "$classpath|$entryClass|$methodName|${runKey(run)}"
        return CACHE.computeIfAbsent(key) {
            ClasspathMirror.mirror(classpath, "branchdecompose", { b ->
                ClasspathMirror.Transformed(rewriteClass(b, entryClass.replace('.', '/'), methodName, run))
            }, runKey(run) + "|" + entryClass + "|" + methodName)
        }
    }

    private fun runKey(run: RunPlan): String = when (run) {
        RunPlan.Leaf -> "leaf"
        RunPlan.Parent -> "parent"
    }

    /**
     * Rewrite the `coldBranch` marker of [internalName].[methodName] for [run]; every other method and
     * class is copied verbatim. The leaf routes the marker to `assume(cond)`; the parent routes it to
     * `assume(!cond)` (flipping the boolean already on the stack with `ICONST_1 ; IXOR`). Neither adds
     * control flow, so `COMPUTE_MAXS` suffices. Exposed for unit tests.
     */
    internal fun rewriteClass(bytes: ByteArray, internalName: String, methodName: String,
                              run: RunPlan): ByteArray {
        val cr = ClassReader(bytes)
        if (cr.className != internalName) {
            return bytes // not the entry class - nothing to rewrite
        }
        val plan = analyzeBytes(bytes, methodName)
        if (!plan.isDecomposed) {
            return bytes // defensive: nothing to do without a marker
        }
        // No new branch targets (a marker call becomes a marker call, with at most a `XOR 1` prefix),
        // so COMPUTE_MAXS suffices and we avoid loading classes for frame merge.
        val cw = ClassWriter(cr, ClassWriter.COMPUTE_MAXS)
        val cv = object : ClassVisitor(Opcodes.ASM9, cw) {
            override fun visitMethod(a: Int, n: String?, d: String?, s: String?,
                                     ex: Array<String>?): MethodVisitor {
                val mv = super.visitMethod(a, n, d, s, ex)
                if (n != methodName) {
                    return mv
                }
                return MarkerMethodVisitor(mv, run)
            }
        }
        cr.accept(cv, 0)
        return cw.toByteArray()
    }

    /**
     * Route the `coldBranch` marker to `assume(cond)` (leaf) or `assume(!cond)` (parent). The boolean
     * is already on the stack; the parent flips it with `ICONST_1 ; IXOR` (a clean 0/1 XOR 1), so the
     * `(Z)V` descriptor and the operand stack are unchanged either way. Every other instruction passes
     * through untouched.
     */
    private class MarkerMethodVisitor(mv: MethodVisitor, private val run: RunPlan) :
            MethodVisitor(Opcodes.ASM9, mv) {

        override fun visitMethodInsn(op: Int, owner: String?, name: String?, desc: String?,
                                     itf: Boolean) {
            if (op == Opcodes.INVOKESTATIC && owner == BMC && desc == BOOL_DESC && name == COLD_BRANCH) {
                if (run == RunPlan.Parent) {
                    // Negate the boolean already on the stack: b XOR 1.
                    super.visitInsn(Opcodes.ICONST_1)
                    super.visitInsn(Opcodes.IXOR)
                }
                // Both runs assume the (possibly negated) condition - same descriptor, owner/name swap.
                super.visitMethodInsn(Opcodes.INVOKESTATIC, CPROVER, "assume", BOOL_DESC, false)
                return
            }
            super.visitMethodInsn(op, owner, name, desc, itf)
        }
    }
}
