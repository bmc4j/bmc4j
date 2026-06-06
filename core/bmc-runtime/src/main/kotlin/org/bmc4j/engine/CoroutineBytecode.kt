package org.bmc4j.engine

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.util.concurrent.ConcurrentHashMap

/**
 * Prepares a JBMC analysis classpath for Kotlin coroutines.
 *
 * Kotlin compiles a `suspend` function with more than one suspension point
 * into a state machine whose `LocalVariableTable` has overlapping entries in
 * the parameter slot range. JBMC 6.9.0 trips an internal invariant on that
 * (`create_parameter_names: "should have at most one entry per index"`) and
 * aborts before it can verify anything. We sidestep it by mirroring each classpath
 * *directory* with the `LocalVariableTable` removed from coroutine
 * methods only — suspend functions (those with a trailing
 * `kotlin.coroutines.Continuation` parameter) and generated
 * `invokeSuspend` bodies. Line numbers and all other methods' debug info are
 * untouched, so ordinary counterexamples keep their variable names.
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
                val coroutine = name == "invokeSuspend" || desc.contains(CONTINUATION)
                if (!coroutine) {
                    return mv
                }
                return object : MethodVisitor(Opcodes.ASM9, mv) {
                    override fun visitLocalVariable(n: String?, d: String?, s: String?,
                                                    start: Label?, end: Label?, index: Int) {
                        // drop LVT/LVTT entries for coroutine methods
                    }
                }
            }
        }
        cr.accept(cv, 0)
        return cw.toByteArray()
    }
}
