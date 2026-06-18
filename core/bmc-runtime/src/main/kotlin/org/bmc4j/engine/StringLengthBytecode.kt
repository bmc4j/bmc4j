package org.bmc4j.engine

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.util.concurrent.ConcurrentHashMap

/**
 * Makes a symbolic string's LENGTH bound bind under string refinement OFF ([org.bmc4j.StringMode.CHAR_ARRAY_MODEL]),
 * so the SAME `maxStringLength` / `anyString(n)` value gives the same length semantics in REFINEMENT and
 * CHAR_ARRAY_MODEL - the user never has to know which mode they are in.
 *
 * ## The defect this fixes
 * Under CHAR_ARRAY_MODEL a symbolic string is introduced as `CProver.nondetWithoutNull()`, whose char-array backing
 * JBMC re-havocs across accesses (see the char-array String model). So:
 * - `@BmcProof.maxStringLength` maps to JBMC's `--max-nondet-string-length`, which is OMITTED under CHAR_ARRAY_MODEL
 *   (JBMC rejects it together with `--no-refine-strings`), so the GLOBAL bound did nothing; and
 * - `Bmc.anyString(n)` is `nondetWithoutNull()` + `assume(length <= n)`; the assume pinned ONE
 *   `length()` read but a later read/`charAt` against the re-havoced backing could exceed `n`, so the
 *   PER-CALL bound did not bind either.
 *
 * Net: under CHAR_ARRAY_MODEL both length knobs were silently dropped - a symbolic string's length was effectively
 * unbounded / unstable.
 *
 * ## The fix
 * Under CHAR_ARRAY_MODEL this pass rewrites the symbolic-string INTRODUCTION - a `CProver.nondetWithoutNull()`
 * immediately followed by `CHECKCAST java/lang/String` (the shape `String s = nondetWithoutNull()` and
 * every `Bmc.anyString`/`anyAsciiString` helper body compile to) - into a SOUND BOUNDED char-array
 * construction [BmcStrings.anyCharBacked], which builds `new String(charArrayOfNondetLength_0_to_bound)`.
 * The char-array model backs that String with a real, STABLE array, so its `length()` and every
 * `charAt` read the same array: the existing `assume(length <= n)` (and any per-call min/alphabet/ASCII
 * assume the helper adds) now BIND, and the global `maxStringLength` becomes the backing bound for a
 * bare symbolic string. This is also the gap-2 soundness fix (anyString sound under CHAR_ARRAY_MODEL).
 *
 * The BOUND used for a site is the EFFECTIVE one (the wrinkle: a per-call `n` LARGER than the global
 * default must RAISE the backing bound, not be capped at the global):
 * - inside one of the [BMC_STRING_HELPERS] (`org/bmc4j/Bmc.anyString*` / `anyAsciiString`), the bound is
 *   the helper's OWN `maxLength` parameter (an `ILOAD` of its slot) - so the per-call argument flows
 *   straight through, whatever its value relative to the global; and
 * - at any other site (a bare `nondetWithoutNull()` String in user code, `Bmc.stringFromEnv`/
 *   `stringFromProperty`), the bound is the run's GLOBAL `maxStringLength` (pushed as a constant).
 *
 * The substitution drops the trailing `CHECKCAST` (the factory already returns `String`), so it is
 * stack-neutral overall (no value in, one `String` out, exactly like the original `nondetWithoutNull()`
 * + `checkcast`) and introduces no jump target.
 *
 * ## Why this is NOT in the hoistable/pre-mirrored chain
 * It is PER-PROOF: it fires only under CHAR_ARRAY_MODEL, and the global-bound constant is the run's effective
 * `maxStringLength`. Both are per-proof, so (unlike the env-independent desugars) it cannot be hoisted
 * into the cacheable Gradle mirror; it runs in-JVM in `JbmcBackend.prepareClasspath` after the hoistable
 * passes, gated on the mode. Under REFINEMENT it does NOT run at all, so refinement behaviour - JBMC's
 * `--max-nondet-string-length` flag - is unchanged.
 */
object StringLengthBytecode {

    private const val CPROVER_OWNER = "org/cprover/CProver"
    private const val NONDET = "nondetWithoutNull"
    private const val NONDET_DESC = "()Ljava/lang/Object;"
    private const val STRING = "java/lang/String"
    private const val BMC_STRINGS = "org/bmc4j/engine/BmcStrings"
    private const val ANY_CHAR_BACKED = "anyCharBacked"
    private const val ANY_CHAR_BACKED_DESC = "(I)Ljava/lang/String;"
    /** The clone-free literal factory on the char-array String model (no-refine only): adopts the freshly
     *  built char[] as its backing with NO defensive copy, so a fixed literal incurs no `array[char].clone`.
     *  Emitted directly (not via a BmcStrings hop) - this pass runs only under CHAR_ARRAY_MODEL, where the
     *  model shadows `java.lang.String` on jbmc's classpath, so the call resolves to the model. */
    private const val STRING_ADOPT_CHARS = "adoptChars"
    private const val OF_CHARS_DESC = "([C)Ljava/lang/String;"

    /** Constant-pool tag for a CONSTANT_String entry (a `ldc "..."` literal). */
    private const val CONSTANT_STRING_TAG = 8

    private const val BMC_OWNER = "org/bmc4j/Bmc"

    /**
     * The `Bmc` symbolic-string helper methods (by `"name desc"`) whose body introduces a symbolic
     * string, mapped to the local-variable slot of their `maxLength` parameter. Inside these, the
     * bound is that parameter (so the per-call argument is honored as-is); everywhere else the bound is
     * the global `maxStringLength`. Slots are the static-method parameter slots:
     * `anyString(int)` / `anyString(int, String)` / `anyAsciiString(int)` carry maxLength at slot 0;
     * `anyString(int, int)` (minLength, maxLength) carries it at slot 1.
     */
    private val BMC_STRING_HELPERS: Map<String, Int> = mapOf(
            "anyString (I)Ljava/lang/String;" to 0,
            "anyString (II)Ljava/lang/String;" to 1,
            "anyString (ILjava/lang/String;)Ljava/lang/String;" to 0,
            "anyAsciiString (I)Ljava/lang/String;" to 0)

    private val CACHE = ConcurrentHashMap<String, String>()

    /**
     * Rewrite directory AND jar entries of [classpath] for the CHAR_ARRAY_MODEL-mode length-bound fix, with the
     * global bound [maxStringLength]. Memoized per `(classpath, maxStringLength)` - the rewrite output
     * depends on the global bound (the constant pushed at bare sites), so it is part of the cache key.
     */
    @JvmStatic
    fun rewrite(classpath: String, maxStringLength: Int): String =
            CACHE.computeIfAbsent(classpath + "\u0000" + maxStringLength) {
                doRewrite(classpath, maxStringLength)
            }

    private fun doRewrite(classpath: String, maxStringLength: Int): String =
            ClasspathMirror.mirror(classpath, "strlen4-$maxStringLength", { b ->
                ClasspathMirror.Transformed(rewriteClass(b, maxStringLength))
            })

    /** Pure transform: bound symbolic-string introductions by their effective length. Exposed for tests. */
    internal fun rewriteClass(bytes: ByteArray, maxStringLength: Int): ByteArray {
        val cr = ClassReader(bytes)
        // Fast no-op: a class whose constant pool neither names nondetWithoutNull NOR holds a String
        // constant can't need either rewrite (symbolic-string introduction or constant-length pinning),
        // so leave its bytes byte-for-byte untouched (the mirror dedups identical content).
        if (!needsRewrite(cr)) {
            return bytes
        }
        // COMPUTE_MAXS: pushing the bound (an ILOAD or a constant) before the call grows the stack by
        // one transiently; the substitution adds no jump target so existing frames stay valid.
        val cw = ClassWriter(cr, ClassWriter.COMPUTE_MAXS)
        val owner = arrayOfNulls<String>(1)
        val cv = object : ClassVisitor(Opcodes.ASM9, cw) {
            override fun visit(version: Int, access: Int, name: String?, sig: String?,
                               superName: String?, ifs: Array<String>?) {
                owner[0] = name
                super.visit(version, access, name, sig, superName, ifs)
            }

            override fun visitMethod(access: Int, name: String?, desc: String?, sig: String?,
                                     ex: Array<String>?): MethodVisitor {
                val mv = super.visitMethod(access, name, desc, sig, ex)
                // BmcStrings is EXCLUDED: its own ofBytesNondet (the unrecognized-charset fallback) returns
                // a deliberately UNCONSTRAINED nondetWithoutNull() String - bounding it would unsoundly
                // narrow a decode of arbitrary length. anyCharBacked itself uses nondetInt/nondetChar, not
                // this pattern, so it is unaffected regardless; the guard is for ofBytesNondet's soundness.
                if (BMC_STRINGS == owner[0]) {
                    return mv
                }
                // The bound for sites in THIS method: the helper's own maxLength slot when this is a
                // recognized Bmc symbolic-string helper, else null (use the global constant).
                val helperSlot = if (BMC_OWNER == owner[0]) BMC_STRING_HELPERS[name + " " + desc] else null
                // Constant-length LITERAL pinning fires only in USER proof code. Pinning a literal in a
                // substituted JDK model / runtime class re-emits it in a way the no-refinement char-array
                // engine unwinds far more expensively; that re-emission regressed the conformance suite.
                val pinLiterals = !isModelOrRuntime(owner[0])
                return Rewriter(mv, helperSlot, maxStringLength, pinLiterals)
            }
        }
        cr.accept(cv, 0)
        return cw.toByteArray()
    }

    /** Does [cr] need rewriting: does its constant pool name `nondetWithoutNull` (a symbolic-string
     *  introduction to bound) OR hold a String constant (an `ldc` literal whose length must be pinned)?
     *  A class with neither can't be touched by this pass. */
    private fun needsRewrite(cr: ClassReader): Boolean {
        for (i in 1 until cr.itemCount) {
            val item = cr.getItem(i)
            if (item == 0) continue
            // A String CONSTANT (ldc literal) needs its known length pinned (the char[]-model rewrite),
            // even in a class that never names nondetWithoutNull. The tag byte precedes the item data.
            if (cr.readByte(item - 1) == CONSTANT_STRING_TAG) {
                return true
            }
            try {
                if (NONDET == cr.readUTF8(item, CharArray(cr.maxStringLength))) {
                    return true
                }
            } catch (e: RuntimeException) {
                // Not a UTF8 constant at this slot - skip.
            }
        }
        return false
    }

    /** A synthetic source line stamped by an instrumentation pass that runs BEFORE this one
     *  ([ReachabilityBytecode]'s vacuity marker, [NondetTagBytecode]'s nondet witness): the string
     *  literals those passes inject (the marker text, the witness variable name) sit on these lines.
     *  They feed framework sinks, not length-bounded ops, so pinning them is pure cost - it re-emits a
     *  char-array build at every proof exit / witness site and regresses the no-refinement suite. */
    private fun isSyntheticLine(line: Int): Boolean =
            line == BmcReachability.SENTINEL_LINE || line == NondetTagBytecode.TAG_LINE

    /** True for a substituted JDK model, a Kotlin/runtime class, or the bmc4j runtime / CProver - the
     *  classes whose bytecode is the product (not user proof code). Their string literals are NOT pinned
     *  (re-emitting them regressed the no-refinement char-array conformance suite); user proof literals are. */
    private fun isModelOrRuntime(internalName: String?): Boolean {
        if (internalName == null) return false
        return internalName.startsWith("java/") ||
                internalName.startsWith("javax/") ||
                internalName.startsWith("jdk/") ||
                internalName.startsWith("sun/") ||
                internalName.startsWith("kotlin/") ||
                internalName.startsWith("org/bmc4j/") ||
                internalName.startsWith("org/cprover/")
    }

    /**
     * Rewrites `nondetWithoutNull()` + `CHECKCAST String` into a bounded [BmcStrings.anyCharBacked]
     * call. Buffers a pending `nondetWithoutNull()` (it has no stack inputs to disturb) and resolves it
     * the moment the next instruction is seen: a `CHECKCAST String` consumes it as a bounded symbolic
     * string introduction; anything else replays the original call verbatim (a `nondetWithoutNull()` used
     * for a non-String type - anyRef, a generic nondet - is left untouched).
     */
    private class Rewriter(mv: MethodVisitor, private val helperSlot: Int?,
                           private val globalMax: Int, private val pinLiterals: Boolean)
        : MethodVisitor(Opcodes.ASM9, mv) {

        /** True when the immediately-preceding instruction was `INVOKESTATIC nondetWithoutNull()`. */
        private var pendingNondet = false

        /** The source line of the instruction currently being visited (-1 before the first
         *  `visitLineNumber`). Used to leave instrumentation-injected literals - which the earlier
         *  reachability / nondet-witness passes stamp on a [synthetic sentinel line][isSyntheticLine] -
         *  unpinned. */
        private var currentLine = -1

        /** Replay a buffered, unconsumed `nondetWithoutNull()` verbatim. */
        private fun flushNondet() {
            if (pendingNondet) {
                pendingNondet = false
                super.visitMethodInsn(Opcodes.INVOKESTATIC, CPROVER_OWNER, NONDET, NONDET_DESC, false)
            }
        }

        /** Push the effective length bound for a symbolic-string site in this method. */
        private fun pushBound() {
            val slot = helperSlot
            if (slot != null) {
                super.visitVarInsn(Opcodes.ILOAD, slot)   // the helper's own maxLength parameter
            } else {
                pushInt(globalMax)                          // the run's global maxStringLength
            }
        }

        /** Push an int constant with the smallest opcode (iconst / bipush / sipush / ldc). */
        private fun pushInt(v: Int) {
            when {
                v in -1..5 -> super.visitInsn(Opcodes.ICONST_0 + v)
                v in Byte.MIN_VALUE..Byte.MAX_VALUE -> super.visitIntInsn(Opcodes.BIPUSH, v)
                v in Short.MIN_VALUE..Short.MAX_VALUE -> super.visitIntInsn(Opcodes.SIPUSH, v)
                else -> super.visitLdcInsn(v)
            }
        }

        override fun visitMethodInsn(opcode: Int, owner: String?, name: String?, desc: String?,
                                     itf: Boolean) {
            flushNondet()
            if (opcode == Opcodes.INVOKESTATIC && CPROVER_OWNER == owner && NONDET == name
                    && NONDET_DESC == desc) {
                // Buffer it: only a following CHECKCAST String makes it a bounded symbolic string.
                pendingNondet = true
                return
            }
            super.visitMethodInsn(opcode, owner, name, desc, itf)
        }

        override fun visitTypeInsn(opcode: Int, type: String?) {
            if (pendingNondet && opcode == Opcodes.CHECKCAST && STRING == type) {
                // String s = nondetWithoutNull(): introduce it as a bounded char-array construction.
                // Push the effective bound, call the sound factory (which returns String), and DROP the
                // checkcast - the factory's result is already a String.
                pendingNondet = false
                pushBound()
                super.visitMethodInsn(Opcodes.INVOKESTATIC, BMC_STRINGS, ANY_CHAR_BACKED,
                        ANY_CHAR_BACKED_DESC, false)
                return
            }
            flushNondet()
            super.visitTypeInsn(opcode, type)
        }

        // Any other instruction resolves a buffered nondet verbatim (not a String introduction).
        override fun visitInsn(opcode: Int) { flushNondet(); super.visitInsn(opcode) }
        override fun visitIntInsn(opcode: Int, operand: Int) { flushNondet(); super.visitIntInsn(opcode, operand) }
        override fun visitVarInsn(opcode: Int, varIdx: Int) { flushNondet(); super.visitVarInsn(opcode, varIdx) }
        override fun visitFieldInsn(o: Int, w: String?, n: String?, d: String?) { flushNondet(); super.visitFieldInsn(o, w, n, d) }
        override fun visitLdcInsn(value: Any?) {
            flushNondet()
            if (pinLiterals && value is String && !isSyntheticLine(currentLine)) {
                // A String CONSTANT in USER proof code. Under CHAR_ARRAY_MODEL jbmc backs an `ldc` literal
                // with a SYMBOLIC-length char array (the literal's known length is lost), so a
                // length-bounded op over it iterates on a symbolic bound and symex explodes. Build the
                // literal as a FIXED char array and route it through the loop-free literal factory, pinning
                // the length to its ACTUAL value. (Skipped for model/runtime classes - see visitMethod -
                // and for literals INJECTED by the reachability / nondet-witness passes, which stamp them
                // on a synthetic sentinel line; pinning those re-emits a char-array build into every proof
                // exit / witness site, regressing the no-refinement conformance suite.)
                emitFixedString(value)
                return
            }
            super.visitLdcInsn(value)
        }

        /** Emit `String.adoptChars(new char[]{ <literal chars> })` for a String constant [s] - the char[]
         *  build is UNROLLED (no loop) and the model's `adoptChars` ADOPTS the fresh array with no copy, so
         *  the literal's length is concrete, a literal longer than the unwind bound is not truncated by a
         *  per-char append, AND no `array[char].clone` is incurred (the array is owned outright here). */
        private fun emitFixedString(s: String) {
            pushInt(s.length)
            super.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_CHAR)
            for (i in s.indices) {
                super.visitInsn(Opcodes.DUP)
                pushInt(i)
                pushInt(s[i].code)
                super.visitInsn(Opcodes.CASTORE)
            }
            super.visitMethodInsn(Opcodes.INVOKESTATIC, STRING, STRING_ADOPT_CHARS, OF_CHARS_DESC, false)
        }
        override fun visitJumpInsn(o: Int, l: org.objectweb.asm.Label?) { flushNondet(); super.visitJumpInsn(o, l) }
        override fun visitLabel(l: org.objectweb.asm.Label?) { flushNondet(); super.visitLabel(l) }
        override fun visitLineNumber(line: Int, start: org.objectweb.asm.Label?) { flushNondet(); currentLine = line; super.visitLineNumber(line, start) }
        override fun visitIincInsn(varIdx: Int, increment: Int) { flushNondet(); super.visitIincInsn(varIdx, increment) }
        override fun visitInvokeDynamicInsn(n: String?, d: String?, h: org.objectweb.asm.Handle?, vararg a: Any?) { flushNondet(); super.visitInvokeDynamicInsn(n, d, h, *a) }
        override fun visitTableSwitchInsn(mn: Int, mx: Int, d: org.objectweb.asm.Label?, vararg ls: org.objectweb.asm.Label?) { flushNondet(); super.visitTableSwitchInsn(mn, mx, d, *ls) }
        override fun visitLookupSwitchInsn(d: org.objectweb.asm.Label?, k: IntArray?, ls: Array<out org.objectweb.asm.Label>?) { flushNondet(); super.visitLookupSwitchInsn(d, k, ls) }
        override fun visitMultiANewArrayInsn(d: String?, dims: Int) { flushNondet(); super.visitMultiANewArrayInsn(d, dims) }

        override fun visitMaxs(maxStack: Int, maxLocals: Int) {
            // Safety net: a trailing buffered nondet (method ends right after it) is replayed verbatim.
            flushNondet()
            super.visitMaxs(maxStack, maxLocals)
        }
    }
}
