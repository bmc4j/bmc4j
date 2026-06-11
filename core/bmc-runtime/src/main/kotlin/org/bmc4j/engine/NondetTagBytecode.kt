package org.bmc4j.engine

import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.util.concurrent.ConcurrentHashMap

/**
 * Explicit USER-nondet witness tag. Emits a named witness tag at every USER symbolic-input call site so
 * a counterexample robustly carries the input's value REGARDLESS of where it later flows (boxed through a
 * `Triple`/carrier, returned from a helper, minted inside a user model) — the flow-fragility the
 * LVT-name witness heuristic drops.
 *
 * For each `INVOKESTATIC org/bmc4j/Bmc.any*` whose result is immediately stored to a local slot, this
 * pass injects right after the store:
 *
 * ```
 * Bmc.recordNondet("<name>", value)
 * ```
 *
 * where `<name>` is the destination local's `LocalVariableTable` name (a synthetic `nondet$<slot>` when
 * the class was compiled `-g:none`), CLASS-QUALIFIED (`<SimpleClass>.<localName>`) when the nondet
 * originates OUTSIDE a `@BmcProof` method — i.e. in a helper or a user MODEL — so the counterexample
 * reads `DbRepoModel.result = 5`; bare (`result = 5`) for a proof's own direct inputs. JBMC does NOT
 * intrinsify `Bmc.recordNondet`, so the call surfaces in the `--json-ui` trace as a plain
 * `function-call` whose argument bindings ([JbmcOutputParser.harvestNondetTags] reads them) are:
 *   - `arg0a` → a `pointer` whose `data` is `java.lang.String.Literal.<name>` (the input NAME), and
 *   - `arg1*` → the input VALUE (an `integer`/`boolean`/`float`/`double`, a `String` pointer, or — for
 *     an object/array input — the handle whose presence still names the variable).
 *
 * Empirically verified verification-neutral against the bundled cbmc 6.9.0: the engine enters and
 * returns the empty-body sink without constraining the formula — the verdict (and the symbolic value the
 * argument carries) is byte-identical with and without the tag.
 *
 * ## Why a CALL-SITE rewrite
 * The destination local's source name is only known at the call site; inside `anyInt` the name is lost.
 * Tagging at the store also captures the value at the moment it is bound to the user's variable, before
 * any boxing.
 *
 * ## Two-phase rewrite (preserve ALL metadata)
 * The destination local's NAME arrives only with the trailing `LocalVariableTable` callbacks, AFTER the
 * code. Rather than buffer the method (which would force re-emitting every annotation/attribute by hand),
 * this pass scans each method ONCE up front ([Plan]) to learn its marked-store sites + LVT names +
 * `@BmcProof` flag, then makes a normal writer-delegating pass that injects the tag after each planned
 * store. The delegating pass leaves annotations, parameters, the LVT, and frames untouched — only the
 * tag instructions are added.
 *
 * ## User-origin scoping (proofs AND user models, NOT bundled models)
 * The pass runs over the whole analysis classpath, but a tag is only a USER input when it originates in
 * code the consumer authored — their proof, a helper, or their own `src/bmcModel` model. bmc4j's own
 * BUNDLED models (`java.*`/`kotlin.*` stand-ins) and runtime (`org.bmc4j.*`) call `CProver.nondet*`
 * (and `Bmc.any*`) internally as MODELLING havoc, which is noise, not a user input. So a class in a
 * [reserved namespace][WitnessUserCode.isReservedNamespace] is never tagged — the same classpath-origin
 * discrimination [WitnessUserCode] uses for the witness. (Third-party library jars are not reserved but
 * never call `Bmc.any*`, so the pass is a no-op there regardless.)
 */
object NondetTagBytecode {

    private const val BMC_OWNER = "org/bmc4j/Bmc"
    private const val RECORD_NAME = "recordNondet"
    private const val BMC_PROOF_DESC = "Lorg/bmc4j/BmcProof;"

    // The recordNondet overloads, one per witness value kind.
    private const val RECORD_DESC_LONG = "(Ljava/lang/String;J)V"
    private const val RECORD_DESC_BOOL = "(Ljava/lang/String;Z)V"
    private const val RECORD_DESC_FLOAT = "(Ljava/lang/String;F)V"
    private const val RECORD_DESC_DOUBLE = "(Ljava/lang/String;D)V"
    private const val RECORD_DESC_STRING = "(Ljava/lang/String;Ljava/lang/String;)V"
    private const val RECORD_DESC_OBJECT = "(Ljava/lang/String;Ljava/lang/Object;)V"

    /** Synthetic source line stamped on the injected tag instructions (kept off real-line collisions,
     *  mirroring [BmcReachability.SENTINEL_LINE]; informational only — the parser keys on the frame id,
     *  not the line). */
    private const val TAG_LINE = 65_534

    /** The witness VALUE kind a tagged store carries — selects the [RECORD_DESC_*] overload and the
     *  reload/widen sequence used to feed the sink. */
    private enum class Kind { LONG, BOOL, FLOAT, DOUBLE, STRING, OBJECT }

    /**
     * The marked USER symbolic-input methods of [Bmc] → the witness kind their result carries.
     * EVERY `Bmc.any*` is here: the integral kinds (int/long/short/byte/char + their ranged/positive/
     * non-negative forms) widen to [Kind.LONG]; boolean/float/double get their own; `anyString*` →
     * [Kind.STRING]; `anyOf` and the symbolic arrays → [Kind.OBJECT] (the array handle rides through the
     * Object sink so the variable is still named, while the heap reconstruction renders `[..]`). The
     * `*FromEnv`/`*FromProperty` config readers are deliberately ABSENT: they are pinned to the run's
     * real config (a CONSTANT after the Config bake), not a symbolic input to witness.
     */
    private val MARKED: Map<String, Kind> = buildMap {
        for (m in listOf("anyInt", "anyPositiveInt", "anyNonNegativeInt", "anyLong",
                "anyShort", "anyByte", "anyChar")) {
            put(m, Kind.LONG)
        }
        put("anyBoolean", Kind.BOOL)
        put("anyFloat", Kind.FLOAT)
        put("anyDouble", Kind.DOUBLE)
        for (m in listOf("anyString", "anyAsciiString")) {
            put(m, Kind.STRING)
        }
        for (m in listOf("anyOf", "anyArrayOfInts", "anyArrayOfLongs")) {
            put(m, Kind.OBJECT)
        }
    }

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
        // User-origin scoping: a class in a reserved namespace (bundled models / runtime) is never
        // tagged — its internal nondet havoc is modelling noise, not a user input. Leave its bytes
        // untouched so the pass is byte-for-byte a no-op there (and the mirror dedups identical content).
        if (WitnessUserCode.isReservedNamespace(cr.className)) {
            return bytes
        }
        // Phase 1: scan every method for marked-store sites (with their resolved local names + proof
        // flag). No marked stores anywhere -> nothing to inject; return the original bytes unchanged so
        // the pass is a byte-for-byte no-op on classes that take no user nondet (every library/JDK class).
        val plans = scan(cr)
        if (plans.isEmpty()) {
            return bytes
        }
        val simpleClass = simpleName(cr.className)
        // Phase 2: a normal writer-delegating pass that splices the tag after each planned store.
        // COMPUTE_MAXS: the tag pushes (String, value) — extra stack — but adds no new branch targets,
        // so existing stack-map frames stay valid (same rationale as ReachabilityBytecode).
        val cw = ClassWriter(cr, ClassWriter.COMPUTE_MAXS)
        val cv = object : ClassVisitor(Opcodes.ASM9, cw) {
            override fun visitMethod(access: Int, name: String?, desc: String?, sig: String?,
                                     ex: Array<String>?): MethodVisitor {
                val mv = super.visitMethod(access, name, desc, sig, ex)
                val plan = plans[name + desc] ?: return mv
                return InjectingMethodVisitor(mv, plan, simpleClass)
            }
        }
        cr.accept(cv, 0)
        return cw.toByteArray()
    }

    /** `pkg/Outer$Inner` → `Inner` (the simple class name used to qualify a non-proof nondet). */
    private fun simpleName(internal: String): String =
            internal.substringAfterLast('/').substringAfterLast('$')

    /** One marked store to tag: the 0-based index of the store instruction within its method, the value
     *  [kind], whether it is a 2-slot long (LSTORE), and the resolved witness NAME (already qualified). */
    private class Site(val storeIndex: Int, val kind: Kind, val longSlot: Boolean, val name: String)

    /** A method's injection plan: its [sites] keyed by store-instruction index, and whether it is a
     *  proof (its inputs are named bare). */
    private class Plan(val isProof: Boolean) {
        val sites = HashMap<Int, Site>()
    }

    /** Phase 1: scan [cr] and build a [Plan] per method that has at least one marked store. */
    private fun scan(cr: ClassReader): Map<String, Plan> {
        val out = HashMap<String, Plan>()
        cr.accept(object : ClassVisitor(Opcodes.ASM9) {
            override fun visitMethod(access: Int, name: String?, desc: String?, sig: String?,
                                     ex: Array<String>?): MethodVisitor =
                    ScanMethodVisitor(name + desc, out)
        }, 0)
        return out
    }

    /** Scans one method: counts instructions (so a store's index is stable across the two passes),
     *  records marked-store sites with their slot/kind, harvests LVT names, and notes `@BmcProof`. Local
     *  names arrive AFTER the code, so the slot→name resolution is finished in [visitEnd]. */
    private class ScanMethodVisitor(private val key: String, private val out: HashMap<String, Plan>) :
            MethodVisitor(Opcodes.ASM9) {

        private var insnIndex = 0
        private var isProof = false
        private var pendingMarked: Kind? = null
        private val localNames = HashMap<Int, String>()
        // Provisional sites, slot kept so the name can be resolved once the LVT is known.
        private class Pending(val storeIndex: Int, val slot: Int, val kind: Kind, val longSlot: Boolean)
        private val pending = ArrayList<Pending>()

        override fun visitAnnotation(descriptor: String?, visible: Boolean): AnnotationVisitor? {
            if (BMC_PROOF_DESC == descriptor) {
                isProof = true
            }
            return null
        }

        override fun visitMethodInsn(opcode: Int, owner: String?, name: String?, desc: String?, itf: Boolean) {
            pendingMarked = if (opcode == Opcodes.INVOKESTATIC && owner == BMC_OWNER) MARKED[name] else null
            insnIndex++
        }

        override fun visitVarInsn(opcode: Int, varIndex: Int) {
            val kind = pendingMarked
            if (kind != null && storeMatches(kind, opcode)) {
                pending.add(Pending(insnIndex, varIndex, kind, opcode == Opcodes.LSTORE))
            }
            pendingMarked = null
            insnIndex++
        }

        override fun visitLocalVariable(name: String?, descriptor: String?, signature: String?,
                                        start: Label?, end: Label?, index: Int) {
            if (name != null && index !in localNames) {
                localNames[index] = name
            }
        }

        // Every other instruction advances the counter and clears the "immediately followed by store"
        // window. (Labels / line numbers / frames are NOT instructions and must NOT advance the index —
        // the injecting pass counts the same instruction callbacks, so the two indices must align.)
        override fun visitInsn(opcode: Int) { pendingMarked = null; insnIndex++ }
        override fun visitIntInsn(o: Int, op: Int) { pendingMarked = null; insnIndex++ }
        override fun visitTypeInsn(o: Int, t: String?) { pendingMarked = null; insnIndex++ }
        override fun visitFieldInsn(o: Int, ow: String?, n: String?, d: String?) { pendingMarked = null; insnIndex++ }
        override fun visitJumpInsn(o: Int, l: Label?) { pendingMarked = null; insnIndex++ }
        override fun visitLdcInsn(v: Any?) { pendingMarked = null; insnIndex++ }
        override fun visitIincInsn(v: Int, i: Int) { pendingMarked = null; insnIndex++ }
        override fun visitTableSwitchInsn(mn: Int, mx: Int, d: Label?, vararg lbls: Label?) { pendingMarked = null; insnIndex++ }
        override fun visitLookupSwitchInsn(d: Label?, keys: IntArray?, lbls: Array<Label?>?) { pendingMarked = null; insnIndex++ }
        override fun visitMultiANewArrayInsn(d: String?, dims: Int) { pendingMarked = null; insnIndex++ }
        override fun visitInvokeDynamicInsn(n: String?, d: String?, h: org.objectweb.asm.Handle?, vararg a: Any?) {
            pendingMarked = null; insnIndex++
        }

        override fun visitEnd() {
            if (pending.isEmpty()) {
                return
            }
            val plan = Plan(isProof)
            for (p in pending) {
                val local = localNames[p.slot] ?: "nondet\$${p.slot}"
                // Bare for a proof's own direct input; class-qualified for a helper/model nondet. The
                // simple class name is prepended by the injecting pass (it knows the class); here we only
                // store the LOCAL name and let the injector qualify, so the same Plan works regardless.
                plan.sites[p.storeIndex] = Site(p.storeIndex, p.kind, p.longSlot, local)
            }
            out[key] = plan
        }
    }

    /** Phase 2: re-emit a method through the writer unchanged, but splice the tag instructions right
     *  after each planned store (identified by the same instruction index the scan computed). */
    private class InjectingMethodVisitor(mv: MethodVisitor, private val plan: Plan,
                                         private val simpleClass: String) :
            MethodVisitor(Opcodes.ASM9, mv) {

        private var insnIndex = 0

        override fun visitMethodInsn(opcode: Int, owner: String?, name: String?, desc: String?, itf: Boolean) {
            super.visitMethodInsn(opcode, owner, name, desc, itf)
            afterInsn()
        }

        override fun visitVarInsn(opcode: Int, varIndex: Int) {
            super.visitVarInsn(opcode, varIndex)
            // A planned store at this index: emit the tag right after it (the value is in [varIndex]).
            val site = plan.sites[insnIndex]
            if (site != null) {
                emitTag(site, varIndex)
            }
            afterInsn()
        }

        override fun visitInsn(opcode: Int) { super.visitInsn(opcode); afterInsn() }
        override fun visitIntInsn(o: Int, op: Int) { super.visitIntInsn(o, op); afterInsn() }
        override fun visitTypeInsn(o: Int, t: String?) { super.visitTypeInsn(o, t); afterInsn() }
        override fun visitFieldInsn(o: Int, ow: String?, n: String?, d: String?) { super.visitFieldInsn(o, ow, n, d); afterInsn() }
        override fun visitJumpInsn(o: Int, l: Label?) { super.visitJumpInsn(o, l); afterInsn() }
        override fun visitLdcInsn(v: Any?) { super.visitLdcInsn(v); afterInsn() }
        override fun visitIincInsn(v: Int, i: Int) { super.visitIincInsn(v, i); afterInsn() }
        override fun visitTableSwitchInsn(mn: Int, mx: Int, d: Label?, vararg lbls: Label?) { super.visitTableSwitchInsn(mn, mx, d, *lbls); afterInsn() }
        override fun visitLookupSwitchInsn(d: Label?, keys: IntArray?, lbls: Array<Label?>?) { super.visitLookupSwitchInsn(d, keys, lbls); afterInsn() }
        override fun visitMultiANewArrayInsn(d: String?, dims: Int) { super.visitMultiANewArrayInsn(d, dims); afterInsn() }
        override fun visitInvokeDynamicInsn(n: String?, d: String?, h: org.objectweb.asm.Handle?, vararg a: Any?) {
            super.visitInvokeDynamicInsn(n, d, h, *a); afterInsn()
        }

        private fun afterInsn() {
            insnIndex++
        }

        /** Emit `Bmc.recordNondet("<name>", value)` after the store: ldc the (class-qualified-when-not-a-
         *  proof) name, reload the value from [slot] (widening an integral to long), and invoke the
         *  matching sink. */
        private fun emitTag(site: Site, slot: Int) {
            val name = if (plan.isProof) site.name else "$simpleClass.${site.name}"
            val l = Label()
            super.visitLabel(l)
            super.visitLineNumber(TAG_LINE, l)
            super.visitLdcInsn(name)
            when (site.kind) {
                Kind.LONG -> {
                    if (site.longSlot) {
                        super.visitVarInsn(Opcodes.LLOAD, slot)
                    } else {
                        super.visitVarInsn(Opcodes.ILOAD, slot)
                        super.visitInsn(Opcodes.I2L)
                    }
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, BMC_OWNER, RECORD_NAME, RECORD_DESC_LONG, false)
                }
                Kind.BOOL -> {
                    super.visitVarInsn(Opcodes.ILOAD, slot)
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, BMC_OWNER, RECORD_NAME, RECORD_DESC_BOOL, false)
                }
                Kind.FLOAT -> {
                    super.visitVarInsn(Opcodes.FLOAD, slot)
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, BMC_OWNER, RECORD_NAME, RECORD_DESC_FLOAT, false)
                }
                Kind.DOUBLE -> {
                    super.visitVarInsn(Opcodes.DLOAD, slot)
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, BMC_OWNER, RECORD_NAME, RECORD_DESC_DOUBLE, false)
                }
                Kind.STRING -> {
                    super.visitVarInsn(Opcodes.ALOAD, slot)
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, BMC_OWNER, RECORD_NAME, RECORD_DESC_STRING, false)
                }
                Kind.OBJECT -> {
                    super.visitVarInsn(Opcodes.ALOAD, slot)
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, BMC_OWNER, RECORD_NAME, RECORD_DESC_OBJECT, false)
                }
            }
        }
    }

    /** Does [opcode] store a value of [kind] (so the marked nondet was bound to a local)? */
    private fun storeMatches(kind: Kind, opcode: Int): Boolean = when (kind) {
        Kind.LONG -> opcode == Opcodes.ISTORE || opcode == Opcodes.LSTORE
        Kind.BOOL -> opcode == Opcodes.ISTORE // boolean is an int on the stack
        Kind.FLOAT -> opcode == Opcodes.FSTORE
        Kind.DOUBLE -> opcode == Opcodes.DSTORE
        Kind.STRING, Kind.OBJECT -> opcode == Opcodes.ASTORE
    }
}
