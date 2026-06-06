package org.bmc4j.engine

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.util.concurrent.ConcurrentHashMap

/**
 * Redirects the integer-valued `java.lang.Math` call sites that JBMC's bundled
 * `core-models.jar` does NOT model — `floorDiv`, `floorMod`, the `*Exact`
 * family, `toIntExact`, `absExact` and `abs(int/long)` — to the sound
 * [BmcMath] reimplementations. JBMC stubs those intrinsics to an unconstrained (nondet)
 * result, so a proof touching them is silently unsound (e.g. `Math.floorDiv(-7, 3) == -3`
 * spuriously refutes; this routed several `java.time`/`Period` proofs to the
 * differential axis). The methods JBMC *does* model soundly (`sqrt`/`pow`/`sin`/...) are
 * left untouched — this is a targeted redirect, NOT a wholesale `Math` shadow,
 * so JBMC's real floating-point math models are preserved.
 *
 * Mirrors the `StringBytecode` pattern: every redirected `Math.*` signature has a
 * [BmcMath] method with the *identical* descriptor, so the rewrite is a one-instruction
 * owner swap (`java/lang/Math` → `org/bmc4j/engine/BmcMath`) with the operand stack
 * unchanged. Like the other passes, both directory and jar entries are mirrored (with sites
 * rewritten) via [ClasspathMirror].
 */
object MathBytecode {

    private const val MATH = "java/lang/Math"
    private const val BMC_MATH = "org/bmc4j/engine/BmcMath"

    /** `"name desc"` of every `Math` static method we redirect to [BmcMath]. Each
     *  one is reimplemented soundly in [BmcMath] with the exact same descriptor. Methods JBMC
     *  already models soundly (sqrt/pow/trig/etc.) are deliberately absent so they pass through. */
    private val REDIRECTS = setOf(
            // floorDiv / floorMod (all JDK overloads)
            "floorDiv (II)I",
            "floorDiv (JJ)J",
            "floorDiv (JI)J",
            "floorMod (II)I",
            "floorMod (JJ)J",
            "floorMod (JI)I",
            // addExact / subtractExact / multiplyExact
            "addExact (II)I",
            "addExact (JJ)J",
            "subtractExact (II)I",
            "subtractExact (JJ)J",
            "multiplyExact (II)I",
            "multiplyExact (JJ)J",
            "multiplyExact (JI)J",
            // negateExact / incrementExact / decrementExact
            "negateExact (I)I",
            "negateExact (J)J",
            "incrementExact (I)I",
            "incrementExact (J)J",
            "decrementExact (I)I",
            "decrementExact (J)J",
            // toIntExact / absExact
            "toIntExact (J)I",
            "absExact (I)I",
            "absExact (J)J",
            // abs(int/long) — JBMC's stub returns nondet for the integer overloads
            "abs (I)I",
            "abs (J)J")

    private val CACHE = ConcurrentHashMap<String, String>()

    /** Rewrite directory AND jar entries of [classpath], returning the new classpath. Memoized
     *  per classpath — computed once per worker, which also makes concurrent proofs race-free. */
    @JvmStatic
    fun rewrite(classpath: String): String =
            CACHE.computeIfAbsent(classpath, MathBytecode::doRewrite)

    private fun doRewrite(classpath: String): String =
            ClasspathMirror.mirror(classpath, "math", { b ->
                ClasspathMirror.Transformed(rewriteClass(b))
            })

    /** Pure transform: redirect the unmodeled `Math.*` static call sites to [BmcMath].
     *  Exposed for unit tests. */
    internal fun rewriteClass(bytes: ByteArray): ByteArray {
        val cr = ClassReader(bytes)
        val cw = ClassWriter(0)
        val cv = object : ClassVisitor(Opcodes.ASM9, cw) {
            override fun visitMethod(a: Int, n: String?, d: String?, s: String?,
                                     ex: Array<String>?): MethodVisitor {
                val mv = super.visitMethod(a, n, d, s, ex)
                return object : MethodVisitor(Opcodes.ASM9, mv) {
                    override fun visitMethodInsn(op: Int, mOwner: String?, name: String?,
                                                 desc: String?, itf: Boolean) {
                        if (op == Opcodes.INVOKESTATIC && MATH == mOwner
                                && "$name $desc" in REDIRECTS) {
                            // Identical descriptor -> operand stack unchanged; swap the owner only.
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, BMC_MATH, name, desc, false)
                        } else {
                            super.visitMethodInsn(op, mOwner, name, desc, itf)
                        }
                    }
                }
            }
        }
        cr.accept(cv, 0)
        return cw.toByteArray()
    }
}
