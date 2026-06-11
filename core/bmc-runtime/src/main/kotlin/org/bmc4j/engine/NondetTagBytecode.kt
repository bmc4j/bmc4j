package org.bmc4j.engine

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.util.concurrent.ConcurrentHashMap

/**
 * **SPIKE: explicit user-nondet witness tag.** Emits a named witness tag at every USER symbolic-input
 * call site so a counterexample robustly carries the input's value REGARDLESS of where it later flows
 * (boxed through a `Triple`/carrier, returned from a helper) — the flow-fragility that the LVT-name
 * witness heuristic drops.
 *
 * For each `INVOKESTATIC org/bmc4j/Bmc.any{Int,Long,...}` whose integral result is immediately stored
 * to a local slot, this pass injects right after the store:
 *
 * ```
 * Bmc.recordNondet("<localName>", value)
 * ```
 *
 * where `<localName>` is the destination local's `LocalVariableTable` name (a synthetic
 * `nondet$<slot>` when the class was compiled `-g:none` and carries no table). JBMC does NOT
 * intrinsify `Bmc.recordNondet`, so the call surfaces in the `--json-ui` trace as a plain
 * `function-call` whose argument bindings ([JbmcOutputParser.harvestNondetTags] reads them) are:
 *   - `arg0a` → a `pointer` whose `data` is `java.lang.String.Literal.<name>` (the input NAME), and
 *   - `arg1l` → an `integer` value (the input VALUE).
 *
 * Empirically verified verification-neutral against the bundled cbmc 6.9.0: the engine enters and
 * returns the empty-body sink without constraining the formula — the verdict (and the symbolic value
 * `arg1l` carries) is byte-identical with and without the tag.
 *
 * Why a CALL-SITE rewrite (vs tagging inside `Bmc.anyInt`): the destination local's source name is
 * only known at the call site; inside `anyInt` the name is lost. Tagging at the store also captures
 * the value at the moment it is bound to the user's variable, before any boxing.
 *
 * Scope of the SPIKE: integral scalars stored directly to a local (the `int x = Bmc.anyInt()` shape,
 * including via the boxed-`Triple` helper `val (a,b,c) = ...` lowering). Values consumed straight into
 * an expression without an intervening store are out of scope for this prototype.
 */
object NondetTagBytecode {

    private const val BMC_OWNER = "org/bmc4j/Bmc"
    private const val RECORD_NAME = "recordNondet"
    private const val RECORD_DESC = "(Ljava/lang/String;J)V"

    /** Synthetic source line stamped on the injected tag instructions (kept off real-line collisions,
     *  mirroring [BmcReachability.SENTINEL_LINE]; informational only — the parser keys on the frame id,
     *  not the line). */
    private const val TAG_LINE = 65_534

    /** Marked USER symbolic-input methods: integral-returning `Bmc.any*` (the witness-relevant ones). */
    private val MARKED_INT = setOf("anyInt", "anyPositiveInt", "anyNonNegativeInt")
    private val MARKED_LONG = setOf("anyLong")

    private val CACHE = ConcurrentHashMap<String, String>()

    @JvmStatic
    fun rewrite(classpath: String): String =
            CACHE.computeIfAbsent(classpath, NondetTagBytecode::doRewrite)

    private fun doRewrite(classpath: String): String =
            ClasspathMirror.mirror(classpath, "nondettag", { b ->
                ClasspathMirror.Transformed(rewriteClass(b))
            })

    internal fun rewriteClass(bytes: ByteArray): ByteArray {
        val cr = ClassReader(bytes)
        // COMPUTE_MAXS: the tag pushes (String, long) — extra stack — but adds no new branch targets,
        // so existing stack-map frames stay valid (same rationale as ReachabilityBytecode).
        val cw = ClassWriter(cr, ClassWriter.COMPUTE_MAXS)
        val cv = object : ClassVisitor(Opcodes.ASM9, cw) {
            override fun visitMethod(access: Int, name: String?, desc: String?, sig: String?,
                                     ex: Array<String>?): MethodVisitor =
                    TagMethodVisitor(super.visitMethod(access, name, desc, sig, ex))
        }
        cr.accept(cv, 0)
        return cw.toByteArray()
    }

    /**
     * Buffers a method's instructions so the destination-local NAME (delivered only by the trailing
     * [visitLocalVariable] callbacks) is known before we emit each tag. A pending tag is queued when we
     * see a marked `Bmc.any*` invoke immediately followed by an integral store; at [visitEnd] every
     * queued tag's local name is resolved from the harvested table and replayed into the writer.
     */
    private class TagMethodVisitor(private val out: MethodVisitor) :
            MethodVisitor(Opcodes.ASM9, null) {

        /** A buffered instruction: re-issues itself against the real writer at visitEnd. */
        private val buf = ArrayList<(MethodVisitor) -> Unit>()

        /** slot -> first-declared local name, from the LocalVariableTable. */
        private val localNames = HashMap<Int, String>()

        /** Queued (var slot, kind) tags to inject right after the buffered store at [bufIndexAfterStore]. */
        private data class Tag(val bufIndexAfterStore: Int, val slot: Int, val long: Boolean)
        private val tags = ArrayList<Tag>()

        /** Was the immediately-preceding instruction a marked Bmc.any* invoke (and of which width)? */
        private var pendingMarked: Boolean? = null // null=no, false=int, true=long

        override fun visitMethodInsn(opcode: Int, owner: String?, name: String?, desc: String?, itf: Boolean) {
            buf.add { it.visitMethodInsn(opcode, owner, name, desc, itf) }
            pendingMarked = if (opcode == Opcodes.INVOKESTATIC && owner == BMC_OWNER) {
                when (name) {
                    in MARKED_INT -> false
                    in MARKED_LONG -> true
                    else -> null
                }
            } else {
                null
            }
        }

        override fun visitVarInsn(opcode: Int, varIndex: Int) {
            buf.add { it.visitVarInsn(opcode, varIndex) }
            val marked = pendingMarked
            if (marked != null && ((!marked && opcode == Opcodes.ISTORE) || (marked && opcode == Opcodes.LSTORE))) {
                // Store of a marked nondet into a local: queue a tag to fire right after this store.
                tags.add(Tag(buf.size, varIndex, marked))
            }
            pendingMarked = null
        }

        override fun visitLocalVariable(name: String?, descriptor: String?, signature: String?,
                                        start: Label?, end: Label?, index: Int) {
            if (name != null && index !in localNames) {
                localNames[index] = name
            }
            super.visitLocalVariable(name, descriptor, signature, start, end, index)
        }

        // Any non-invoke/non-store instruction clears the "immediately followed by store" window.
        override fun visitInsn(opcode: Int) { buf.add { it.visitInsn(opcode) }; pendingMarked = null }
        override fun visitIntInsn(o: Int, op: Int) { buf.add { it.visitIntInsn(o, op) }; pendingMarked = null }
        override fun visitTypeInsn(o: Int, t: String?) { buf.add { it.visitTypeInsn(o, t) }; pendingMarked = null }
        override fun visitFieldInsn(o: Int, ow: String?, n: String?, d: String?) {
            buf.add { it.visitFieldInsn(o, ow, n, d) }; pendingMarked = null
        }
        override fun visitJumpInsn(o: Int, l: Label?) { buf.add { it.visitJumpInsn(o, l) }; pendingMarked = null }
        override fun visitLabel(l: Label?) { buf.add { it.visitLabel(l) } }
        override fun visitLdcInsn(v: Any?) { buf.add { it.visitLdcInsn(v) }; pendingMarked = null }
        override fun visitIincInsn(v: Int, i: Int) { buf.add { it.visitIincInsn(v, i) }; pendingMarked = null }
        override fun visitLineNumber(line: Int, s: Label?) { buf.add { it.visitLineNumber(line, s) } }
        override fun visitFrame(t: Int, nl: Int, l: Array<Any?>?, ns: Int, s: Array<Any?>?) {
            buf.add { it.visitFrame(t, nl, l, ns, s) }
        }
        override fun visitTableSwitchInsn(mn: Int, mx: Int, d: Label?, vararg lbls: Label?) {
            buf.add { it.visitTableSwitchInsn(mn, mx, d, *lbls) }; pendingMarked = null
        }
        override fun visitLookupSwitchInsn(d: Label?, keys: IntArray?, lbls: Array<Label?>?) {
            buf.add { it.visitLookupSwitchInsn(d, keys, lbls) }; pendingMarked = null
        }
        override fun visitMultiANewArrayInsn(d: String?, dims: Int) {
            buf.add { it.visitMultiANewArrayInsn(d, dims) }; pendingMarked = null
        }

        override fun visitMaxs(maxStack: Int, maxLocals: Int) {
            // COMPUTE_MAXS recomputes; pass through.
            buf.add { it.visitMaxs(maxStack, maxLocals) }
        }

        override fun visitCode() { buf.add { it.visitCode() } }

        override fun visitEnd() {
            // Resolve each queued tag's local name now that the LocalVariableTable is known, then replay
            // the buffer, splicing the tag emission right after the store instruction it follows.
            val byIndex = HashMap<Int, MutableList<Tag>>()
            for (t in tags) {
                byIndex.getOrPut(t.bufIndexAfterStore) { ArrayList() }.add(t)
            }
            for (i in buf.indices) {
                buf[i](out)
                byIndex[i + 1]?.forEach { emitTag(out, it) }
            }
            // visitMaxs/visitEnd: the buffered visitMaxs already ran above; finalize the delegate.
            out.visitEnd()
        }

        /** Emit `Bmc.recordNondet("<name>", value)`: reload the value from its slot, ldc the name,
         *  widen an int to long, and invoke the sink. Reading the slot back (rather than dup-before-store)
         *  keeps the rewrite a clean post-store splice with no stack juggling. */
        private fun emitTag(mv: MethodVisitor, tag: Tag) {
            val name = localNames[tag.slot] ?: "nondet\$${tag.slot}"
            val l = Label()
            mv.visitLabel(l)
            mv.visitLineNumber(TAG_LINE, l)
            mv.visitLdcInsn(name)
            if (tag.long) {
                mv.visitVarInsn(Opcodes.LLOAD, tag.slot)
            } else {
                mv.visitVarInsn(Opcodes.ILOAD, tag.slot)
                mv.visitInsn(Opcodes.I2L)
            }
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, BMC_OWNER, RECORD_NAME, RECORD_DESC, false)
        }
    }
}
