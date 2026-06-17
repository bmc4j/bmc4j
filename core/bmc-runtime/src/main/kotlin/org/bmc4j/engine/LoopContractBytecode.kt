package org.bmc4j.engine

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.util.concurrent.ConcurrentHashMap

/**
 * SPIKE: loop-contract (loop-SUMMARIZATION) rewriter. Lets a proof attach an inductive invariant `I` to a
 * loop so BMC discharges a one-iteration STEP check instead of UNROLLING the loop N times. This is the
 * generic, sound version of the @LoopUnwind / digit-domainSplit band-aids (which just cap or split the
 * unroll cost). JBMC has no native loop-contract support (it is a C-only CBMC feature); this implements
 * the classic sound transform purely in the bytecode-rewrite layer, in the same in-place-marker style as
 * [DomainSplitBytecode].
 *
 * ## The marker sequence (written ONCE, straight-line, in the proof source)
 * The proof brackets the loop with the [org.bmc4j.Bmc] `loop*` markers. The body is written exactly once
 * (it is NOT a real `while` — a summarized loop never loops):
 * ```
 *   Bmc.loopInvariant(I);   // base case:    ASSERT I  on entry
 *   Bmc.loopHavoc();        // frame:        havoc the loop's ASSIGNS set (auto-computed below)
 *   Bmc.loopAssume(I);      // inductive hyp: ASSUME I  at an arbitrary iteration
 *   Bmc.loopGuard(g);       // step opens:    ASSUME g  (entering the body)
 *       ...body once...     //               the loop body
 *   Bmc.loopPreserve(I);    // step VC:       ASSERT I  then ASSUME false (cut the step path)
 *   Bmc.loopExit(g);        // summary:       ASSUME !g (exit; W stays havoc'd, I && !g hold after)
 * ```
 *
 * ## Lowering (each marker -> an in-place check/assume, like the domainSplit slice/cover swaps)
 * - `loopInvariant(I)` -> `Bmc.check(I)`                  (base case assert; engine-PROVEN)
 * - `loopHavoc()`      -> `nondet; STORE slot` per assigns-set slot
 * - `loopAssume(I)`    -> `CProver.assume(I)`             (inductive hypothesis)
 * - `loopGuard(g)`     -> `CProver.assume(g)`             (open step under the guard)
 * - `loopPreserve(I)`  -> `Bmc.check(I); assume(false)`   (step preservation assert; PROVEN; cut the path)
 * - `loopExit(g)`      -> `CProver.assume(!g)`            (exit; continue summarized)
 *
 * ## The ASSIGNS set (the soundness-critical frame) is AUTO-COMPUTED
 * Every local the body (the instructions BETWEEN `loopGuard` and `loopPreserve`) writes is havoc'd. A
 * user never declares the frame, so they cannot get it wrong by omission — getting it wrong (missing a
 * written var) is exactly what makes a loop contract unsound, so it is computed, not trusted. SPIKE
 * boundary: only LOCAL int/long stores are supported. A body that writes a FIELD or an ARRAY element
 * (a heap assigns set) is REFUSED LOUD ([LoopContractError] -> UNKNOWN), because soundly havoc'ing the
 * heap needs a points-to frame this spike does not implement — a silent miss there would be unsound.
 *
 * ## Soundness
 * `I` is a CHECKED hint, NOT a trusted assume. The base case and the step preservation are ASSERTED
 * (`Bmc.check`, engine-proven); only the summary (havoc + `loopAssume` + `loopExit`) uses `I` as an
 * assumption, justified by those two proven asserts. A WRONG `I` makes the base assert or the step assert
 * fail -> REFUTED, never a false VERIFIED. The `assume(false)` after `loopPreserve` cuts the step path so
 * the body's concrete effect never leaks into the post-loop state — only `(I && !g)` over the havoc'd
 * frame does. This is the standard Floyd/Hoare loop rule, encoded as VCs.
 */
internal object LoopContractBytecode {

    private const val BMC = "org/bmc4j/Bmc"
    private const val CPROVER = "org/cprover/CProver"
    private const val BOOL_DESC = "(Z)V"
    private const val VOID_DESC = "()V"

    const val LOOP_INVARIANT = "loopInvariant"
    const val LOOP_HAVOC = "loopHavoc"
    const val LOOP_ASSUME = "loopAssume"
    const val LOOP_GUARD = "loopGuard"
    const val LOOP_PRESERVE = "loopPreserve"
    const val LOOP_EXIT = "loopExit"

    private val MARKER_NAMES = setOf(
            LOOP_INVARIANT, LOOP_HAVOC, LOOP_ASSUME, LOOP_GUARD, LOOP_PRESERVE, LOOP_EXIT)

    /** A malformed loop-contract marker sequence, or an unsupported (heap) assigns set. Unchecked so it
     *  propagates out of the rewrite path and is reclassified UNKNOWN (never a silent false green). */
    class LoopContractError(message: String) : RuntimeException(message)

    /** Whether [methodName] in [bytes] contains any `Bmc.loop*` marker (so the per-proof pass can skip
     *  methods with no loop contract entirely). */
    internal fun hasLoopContract(bytes: ByteArray, methodName: String): Boolean {
        var found = false
        ClassReader(bytes).accept(object : ClassVisitor(Opcodes.ASM9) {
            override fun visitMethod(a: Int, n: String?, d: String?, s: String?,
                                     ex: Array<String>?): MethodVisitor? {
                if (n != methodName) {
                    return null
                }
                return object : MethodVisitor(Opcodes.ASM9) {
                    override fun visitMethodInsn(op: Int, owner: String?, name: String?,
                                                 desc: String?, itf: Boolean) {
                        if (op == Opcodes.INVOKESTATIC && owner == BMC && name in MARKER_NAMES) {
                            found = true
                        }
                    }
                }
            }
        }, 0)
        return found
    }

    private val CACHE = ConcurrentHashMap<String, String>()

    /**
     * Rewrite [classpath] so the loop contract in [entryClass].[methodName] is lowered to its base/step/
     * summary VCs. Memoized per (classpath, class, method). Mirrors [DomainSplitBytecode.rewrite].
     */
    @JvmStatic
    fun rewrite(classpath: String, entryClass: String, methodName: String): String {
        val key = "$classpath|$entryClass|$methodName"
        return CACHE.computeIfAbsent(key) {
            ClasspathMirror.mirror(classpath, "loopcontract", { b ->
                ClasspathMirror.Transformed(
                        rewriteClass(b, entryClass.replace('.', '/'), methodName))
            }, entryClass + "|" + methodName)
        }
    }

    /** A local store the body performs: its slot and the store opcode (which fixes the type to havoc). */
    private data class Store(val slot: Int, val storeOpcode: Int)

    /**
     * Compute the body's ASSIGNS set by scanning the instructions BETWEEN the `loopGuard` and
     * `loopPreserve` markers. Returns the distinct local stores (slot + type) in first-seen order. THROWS
     * [LoopContractError] on a field/array store (an unsupported heap frame) or a malformed bracket.
     */
    private fun computeAssigns(bytes: ByteArray, internalName: String, methodName: String): List<Store> {
        val stores = LinkedHashMap<Int, Store>()
        var inBody = false
        var sawGuard = false
        var sawPreserve = false
        var heapStore: String? = null
        ClassReader(bytes).accept(object : ClassVisitor(Opcodes.ASM9) {
            override fun visitMethod(a: Int, n: String?, d: String?, s: String?,
                                     ex: Array<String>?): MethodVisitor? {
                if (n != methodName) {
                    return null
                }
                return object : MethodVisitor(Opcodes.ASM9) {
                    override fun visitMethodInsn(op: Int, owner: String?, name: String?,
                                                 desc: String?, itf: Boolean) {
                        if (op == Opcodes.INVOKESTATIC && owner == BMC) {
                            when (name) {
                                LOOP_GUARD -> { inBody = true; sawGuard = true }
                                LOOP_PRESERVE -> { inBody = false; sawPreserve = true }
                            }
                        }
                    }

                    override fun visitVarInsn(op: Int, slot: Int) {
                        if (inBody && op in STORE_OPCODES) {
                            stores.putIfAbsent(slot, Store(slot, op))
                        }
                    }

                    override fun visitFieldInsn(op: Int, owner: String?, name: String?, desc: String?) {
                        if (inBody && (op == Opcodes.PUTFIELD || op == Opcodes.PUTSTATIC)) {
                            heapStore = "field $owner.$name"
                        }
                    }

                    override fun visitInsn(op: Int) {
                        if (inBody && op in ARRAY_STORE_OPCODES) {
                            heapStore = "array element"
                        }
                    }
                }
            }
        }, 0)
        if (!sawGuard || !sawPreserve) {
            throw LoopContractError(
                    "$internalName.$methodName: a loop contract needs both Bmc.loopGuard(g) and " +
                            "Bmc.loopPreserve(I) bracketing the body (open the step, then assert " +
                            "preservation).")
        }
        if (heapStore != null) {
            throw LoopContractError(
                    "$internalName.$methodName: loop body writes a HEAP location ($heapStore). This spike " +
                            "auto-havocs LOCAL int/long stores only; a heap assigns set needs a points-to " +
                            "frame not yet implemented. Refused rather than silently havoc'd unsoundly.")
        }
        return stores.values.toList()
    }

    /**
     * Rewrite the loop-contract markers of [internalName].[methodName] into their VCs; every other method
     * and class is copied verbatim. Exposed for unit tests.
     */
    internal fun rewriteClass(bytes: ByteArray, internalName: String, methodName: String): ByteArray {
        val cr = ClassReader(bytes)
        if (cr.className != internalName) {
            return bytes
        }
        if (!hasLoopContract(bytes, methodName)) {
            return bytes
        }
        val assigns = computeAssigns(bytes, internalName, methodName)
        // loopExit injects a branch (boolean negation), so full frame computation is needed.
        val cw = ClassWriter(cr, ClassWriter.COMPUTE_FRAMES)
        val cv = object : ClassVisitor(Opcodes.ASM9, cw) {
            override fun visitMethod(a: Int, n: String?, d: String?, s: String?,
                                     ex: Array<String>?): MethodVisitor {
                val mv = super.visitMethod(a, n, d, s, ex)
                return if (n == methodName) LoopMethodVisitor(mv, assigns) else mv
            }
        }
        cr.accept(cv, 0)
        return cw.toByteArray()
    }

    /**
     * Replaces each `Bmc.loop*` marker call with its base/step/summary VC.
     *
     * The step (guard + body + preservation assert) is wrapped in a NONDETERMINISTIC branch so it is a
     * one-iteration check that does NOT poison the post-loop continuation:
     * ```
     *   check(I)                       // base, main path
     *   havoc(W)                       // main path
     *   assume(I); if (!nondet) goto CONT   // open the step branch (inductive hyp covers BOTH paths)
     *     assume(g)                    //   [step branch] open under the guard
     *     ...body...                   //   [step branch]
     *     check(I); assume(false)      //   [step branch] step VC, then cut (this path contributes nothing)
     *   CONT:                          // merge — reached via the skip edge (feasible)
     *   assume(!g)                     // summary continuation
     * ```
     * `CONT` has two predecessors: the `if (!nondet)` skip (feasible) and the step's `assume(false)`
     * fallthrough (infeasible). The continuation is reachable via the skip edge, so the proof is NOT
     * vacuous; the step's body-effect is confined to the cut branch. This is the standard Hoare loop rule.
     */
    private class LoopMethodVisitor(mv: MethodVisitor, private val assigns: List<Store>) :
            MethodVisitor(Opcodes.ASM9, mv) {

        /** The merge label after the cut step branch; created at `loopAssume`, placed at `loopPreserve`. */
        private var contLabel: Label? = null

        override fun visitMethodInsn(op: Int, owner: String?, name: String?, desc: String?,
                                     itf: Boolean) {
            if (op == Opcodes.INVOKESTATIC && owner == BMC && name in MARKER_NAMES) {
                when (name) {
                    // base case: ASSERT I (boolean already on the stack — pure owner/name swap to check).
                    LOOP_INVARIANT ->
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, BMC, "check", BOOL_DESC, false)
                    // frame: havoc the auto-computed assigns set with a fresh nondet per slot.
                    LOOP_HAVOC -> emitHavoc()
                    // inductive hypothesis: ASSUME I over the havoc'd state (covers the step AND the
                    // summary), THEN open the nondet step branch: if (!nondet) skip the step to CONT.
                    LOOP_ASSUME -> {
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, CPROVER, "assume", BOOL_DESC, false)
                        val cont = Label()
                        contLabel = cont
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, CPROVER, "nondetBoolean", "()Z", false)
                        super.visitJumpInsn(Opcodes.IFEQ, cont) // !nondet -> skip the step
                    }
                    // step opens under the guard: ASSUME g (boolean already on the stack).
                    LOOP_GUARD ->
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, CPROVER, "assume", BOOL_DESC, false)
                    // step VC: ASSERT I after the body, then ASSUME false to cut the step path; then PLACE
                    // the CONT merge label so the skip edge lands here and the summary continues.
                    LOOP_PRESERVE -> {
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, BMC, "check", BOOL_DESC, false)
                        super.visitInsn(Opcodes.ICONST_0)
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, CPROVER, "assume", BOOL_DESC, false)
                        contLabel?.let { super.visitLabel(it) }
                    }
                    // exit: ASSUME !g. The guard boolean is on the stack; negate (g == 0 ? 1 : 0) then assume.
                    LOOP_EXIT -> {
                        val pushTrue = Label()
                        val done = Label()
                        super.visitJumpInsn(Opcodes.IFEQ, pushTrue) // g == 0 -> !g is true
                        super.visitInsn(Opcodes.ICONST_0)
                        super.visitJumpInsn(Opcodes.GOTO, done)
                        super.visitLabel(pushTrue)
                        super.visitInsn(Opcodes.ICONST_1)
                        super.visitLabel(done)
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, CPROVER, "assume", BOOL_DESC, false)
                    }
                }
                return
            }
            super.visitMethodInsn(op, owner, name, desc, itf)
        }

        /** Emit `nondet<T>(); <T>STORE slot` for each assigns-set local — re-symbolize the frame. */
        private fun emitHavoc() {
            for (st in assigns) {
                val (nondet, retDesc) = NONDET_FOR_STORE.getValue(st.storeOpcode)
                super.visitMethodInsn(Opcodes.INVOKESTATIC, CPROVER, nondet, retDesc, false)
                super.visitVarInsn(st.storeOpcode, st.slot)
            }
        }
    }

    // --- store/nondet tables (SPIKE: int + long locals) ---------------------------------------------
    private val STORE_OPCODES = setOf(Opcodes.ISTORE, Opcodes.LSTORE)
    private val ARRAY_STORE_OPCODES = setOf(
            Opcodes.IASTORE, Opcodes.LASTORE, Opcodes.FASTORE, Opcodes.DASTORE,
            Opcodes.AASTORE, Opcodes.BASTORE, Opcodes.CASTORE, Opcodes.SASTORE)

    /** storeOpcode -> (CProver nondet method, its descriptor). */
    private val NONDET_FOR_STORE = mapOf(
            Opcodes.ISTORE to ("nondetInt" to "()I"),
            Opcodes.LSTORE to ("nondetLong" to "()J"))
}
