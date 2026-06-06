package org.bmc4j.engine

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import java.util.concurrent.ConcurrentHashMap

/**
 * Prepares a JBMC analysis classpath for Kotlin coroutines.
 *
 * Kotlin compiles a `suspend` function with more than one suspension point
 * into a state machine whose `LocalVariableTable` has overlapping entries in
 * the parameter slot range. JBMC 6.9.0 trips an internal invariant on that
 * (`create_parameter_names: "should have at most one entry per index"`) and
 * aborts before it can verify anything. We sidestep it by mirroring each classpath
 * *directory* with the `LocalVariableTable` removed from the offending methods. Two
 * complementary rules decide which methods to strip:
 *
 *  - **Coroutine shape (name/descriptor):** suspend functions (a trailing
 *    `kotlin.coroutines.Continuation` parameter) and generated `invokeSuspend` bodies —
 *    the common case, stripped up front so their multi-range parameter LVTs never reach JBMC.
 *  - **The invariant's own precondition (any method):** a method whose `LocalVariableTable`
 *    has more than one entry for some *parameter* slot is exactly what trips
 *    `create_parameter_names`; strip it regardless of its name/descriptor. This catches the
 *    synthetic members the name rule misses — e.g. kotlinx-coroutines' inlined `…$default`
 *    bridges (a heavily-inlined `$default` overload can carry two LVT entries on a parameter slot
 *    yet has neither an `invokeSuspend` name nor a `Continuation` parameter) which JBMC reaches
 *    only on some lazy-conversion orders, producing a timing-dependent abort.
 *
 * Line numbers and all other methods' debug info are untouched, so ordinary counterexamples keep
 * their variable names; only a method that would otherwise crash JBMC (or is a known coroutine
 * body) loses its LVT.
 *
 * Both directory and jar entries are mirrored via [ClasspathMirror]: a published consumer's
 * coroutine classes can arrive in a jar just like its own compiled output.
 */
object CoroutineBytecode {

    private const val CONTINUATION = "Lkotlin/coroutines/Continuation;"

    private val CACHE = ConcurrentHashMap<String, String>()

    /** Strip coroutine LVTs in directory AND jar entries of [classpath]; memoized per classpath
     *  (computed once per worker, which also makes concurrent proofs race-free). */
    @JvmStatic
    fun strip(classpath: String): String =
            CACHE.computeIfAbsent(classpath, CoroutineBytecode::doStrip)

    private fun doStrip(classpath: String): String =
            ClasspathMirror.mirror(classpath, "stripped", { b ->
                ClasspathMirror.Transformed(stripClass(b))
            })

    private fun stripClass(bytes: ByteArray): ByteArray {
        val cr = ClassReader(bytes)
        val cw = ClassWriter(0)
        val cv = object : ClassVisitor(Opcodes.ASM9, cw) {
            override fun visitMethod(access: Int, name: String, desc: String,
                                     sig: String?, exceptions: Array<String>?): MethodVisitor {
                val mv = super.visitMethod(access, name, desc, sig, exceptions)
                // Coroutine shape: strip up front by name/descriptor.
                val coroutine = name == "invokeSuspend" || desc.contains(CONTINUATION)
                if (coroutine) {
                    return object : MethodVisitor(Opcodes.ASM9, mv) {
                        override fun visitLocalVariable(n: String?, d: String?, s: String?,
                                                        start: Label?, end: Label?, index: Int) {
                            // drop LVT/LVTT entries for coroutine methods
                        }
                    }
                }
                // Any other method: buffer its LVT and decide at end-of-method whether it has the
                // shape JBMC's create_parameter_names invariant rejects (>1 entry on a parameter
                // slot). If so, drop the whole table; otherwise emit it verbatim so ordinary
                // counterexamples keep their variable names.
                return BufferingLvtMethodVisitor(mv, parameterSlotCount(access, desc))
            }
        }
        cr.accept(cv, 0)
        return cw.toByteArray()
    }

    /**
     * The number of local-variable slots occupied by a method's parameters: each `long`/`double`
     * argument takes two slots, every other argument one, plus a leading slot for the implicit `this`
     * of an instance method. A slot index below this count is a parameter slot — the range JBMC's
     * `create_parameter_names` requires at most one LVT entry per index in.
     */
    private fun parameterSlotCount(access: Int, desc: String): Int {
        var slots = if (access and Opcodes.ACC_STATIC != 0) 0 else 1
        for (t in Type.getArgumentTypes(desc)) {
            slots += t.size // 2 for long/double, 1 otherwise
        }
        return slots
    }

    /**
     * Buffers a method's `LocalVariableTable` so it can be emitted verbatim, or dropped entirely if
     * any *parameter* slot carries more than one entry — the exact precondition JBMC's
     * `create_parameter_names` asserts. Non-parameter (interior local) duplicates are fine and left
     * untouched; only a method whose parameter range would trip the invariant loses its table.
     */
    private class BufferingLvtMethodVisitor(
            mv: MethodVisitor,
            private val paramSlots: Int,
    ) : MethodVisitor(Opcodes.ASM9, mv) {

        private class Lvt(val n: String?, val d: String?, val s: String?,
                          val start: Label?, val end: Label?, val index: Int)

        private val entries = ArrayList<Lvt>()

        override fun visitLocalVariable(n: String?, d: String?, s: String?,
                                        start: Label?, end: Label?, index: Int) {
            entries.add(Lvt(n, d, s, start, end, index))
        }

        override fun visitEnd() {
            val perSlot = HashMap<Int, Int>()
            var dupOnParam = false
            for (e in entries) {
                val c = (perSlot[e.index] ?: 0) + 1
                perSlot[e.index] = c
                if (e.index < paramSlots && c > 1) {
                    dupOnParam = true
                }
            }
            if (!dupOnParam) {
                // Safe table: replay every entry so counterexamples keep their names.
                for (e in entries) {
                    super.visitLocalVariable(e.n, e.d, e.s, e.start, e.end, e.index)
                }
            }
            // else: a parameter slot has duplicate entries — drop the whole LVT so JBMC's
            // create_parameter_names invariant is never tripped on this method.
            super.visitEnd()
        }
    }
}
