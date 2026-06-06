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
 * Injects the **vacuity reachability marker** into every `@BmcProof` method
 * (including generated enforce-proofs, which are themselves `@BmcProof`-annotated).
 *
 * For each such method this pass *replaces every normal `return`* with a synthetic
 *
 * ```
 * throw new AssertionError("bmc4j.reachability");
 * ```
 *
 * stamped on a [sentinel source line][BmcReachability.SENTINEL_LINE]. JBMC turns each into
 * an `assertion` property: a marker FAILS iff that exit is reachable under the proof's
 * assumptions. [JbmcOutputParser] then treats a proof as **vacuous** (assumptions
 * unsatisfiable) exactly when it has markers and *all* of them are SUCCESS (every normal exit
 * dead) — see [BmcReachability].
 *
 * Replacing each `return` (rather than inserting before it) keeps the bytecode trivially
 * well-formed: the `athrow` ends the block with no dead successor, so no new stack-map frame is
 * needed and the original frames are preserved. Proof methods are only ever JBMC entry points, so
 * never returning normally during analysis is harmless.
 *
 * Ordering: this runs **last** in `JbmcBackend.prepareClasspath` (after the
 * concat/lambda/switch/config desugars and the contract rewrite) so the marker is injected into the
 * *final* proof bodies and no later pass can strip it. Like the sibling passes, both directory
 * and jar entries are mirrored via [ClasspathMirror].
 */
object ReachabilityBytecode {

    private const val BMC_PROOF_DESC = "Lorg/bmc4j/BmcProof;"

    private val CACHE = ConcurrentHashMap<String, String>()

    /** Rewrite directory AND jar entries of [classpath], memoized per classpath (race-free). */
    @JvmStatic
    fun rewrite(classpath: String): String =
            CACHE.computeIfAbsent(classpath, ReachabilityBytecode::doRewrite)

    private fun doRewrite(classpath: String): String =
            ClasspathMirror.mirror(classpath, "reachability", { b ->
                ClasspathMirror.Transformed(rewriteClass(b))
            })

    @JvmStatic
    @JvmName("rewriteClass") // internal functions are name-mangled in bytecode; Java tests call it
    internal fun rewriteClass(bytes: ByteArray): ByteArray {
        val cr = ClassReader(bytes)
        // COMPUTE_MAXS only: we add a few stack slots (new/dup/ldc) but introduce no new jump targets,
        // so existing stack-map frames stay valid and we avoid the class-loading COMPUTE_FRAMES needs.
        val cw = ClassWriter(cr, ClassWriter.COMPUTE_MAXS)
        val cv = object : ClassVisitor(Opcodes.ASM9, cw) {
            override fun visitMethod(access: Int, name: String?, desc: String?, sig: String?,
                                     ex: Array<String>?): MethodVisitor {
                return MarkerMethodVisitor(super.visitMethod(access, name, desc, sig, ex))
            }
        }
        cr.accept(cv, 0)
        return cw.toByteArray()
    }

    /**
     * Buffers a method until it knows whether it is a proof: a method visitor cannot inject after it
     * has already streamed instructions to the writer. We detect `@BmcProof` via the first
     * `visitAnnotation`, which the JVM/ASM delivers before any code — so we can decide whether to
     * rewrite `return` sites as we visit them.
     */
    private class MarkerMethodVisitor(mv: MethodVisitor) : MethodVisitor(Opcodes.ASM9, mv) {

        private var isProof = false

        override fun visitAnnotation(descriptor: String?, visible: Boolean): AnnotationVisitor? {
            if (BMC_PROOF_DESC == descriptor) {
                isProof = true
            }
            return super.visitAnnotation(descriptor, visible)
        }

        override fun visitInsn(opcode: Int) {
            if (isProof && isReturn(opcode)) {
                emitMarker()
                return // replace the return with the marker throw
            }
            super.visitInsn(opcode)
        }

        private fun emitMarker() {
            val l = Label()
            super.visitLabel(l)
            super.visitLineNumber(BmcReachability.SENTINEL_LINE, l)
            super.visitTypeInsn(Opcodes.NEW, "java/lang/AssertionError")
            super.visitInsn(Opcodes.DUP)
            super.visitLdcInsn(BmcReachability.MARKER_TEXT)
            super.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/AssertionError",
                    "<init>", "(Ljava/lang/Object;)V", false)
            super.visitInsn(Opcodes.ATHROW)
        }

        private fun isReturn(opcode: Int): Boolean =
                opcode == Opcodes.RETURN || opcode == Opcodes.IRETURN || opcode == Opcodes.LRETURN
                        || opcode == Opcodes.FRETURN || opcode == Opcodes.DRETURN || opcode == Opcodes.ARETURN
    }
}
