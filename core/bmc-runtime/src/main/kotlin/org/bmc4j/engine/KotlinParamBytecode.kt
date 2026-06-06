package org.bmc4j.engine

import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.util.concurrent.ConcurrentHashMap

/**
 * Makes symbolic non-null object parameters usable in **Kotlin** proofs. kotlinc emits
 * `Intrinsics.checkNotNullParameter(p, "p")` as the prologue of every method with a
 * non-null-typed parameter; JBMC's nondet domain for a proof-method parameter includes
 * `null`, so the (correctly modeled) check throws and every Kotlin proof with a symbolic
 * object parameter spuriously refutes before its body runs — with a counterexample
 * (`p = null`) no Kotlin caller could ever construct.
 *
 * kotlinc states the non-null contract through TWO mechanisms, and JBMC enforces both against
 * its nondet entry inputs, so both must be relaxed — for `@BmcProof`-annotated methods ONLY:
 * 1. the `Intrinsics.checkNotNullParameter` prologue call → redirected to
 *    `BmcKotlin.assumeNotNullParameter`, i.e. `assume(p != null)`;
 * 2. the `@org.jetbrains.annotations.NotNull` parameter annotation, which JBMC asserts
 *    on the entry function's inputs ("Not null annotation check") → stripped from the proof
 *    method's parameters.
 *
 * The proof then ranges over the inputs the Kotlin type system admits — the same trust move the
 * author already made by writing the non-null type.
 *
 * - **Interior calls keep throwing semantics.** Only the proof method's own prologue is
 *   rewritten; a null flowing into a non-null parameter *inside* the analyzed code is a
 *   real, refutable bug and stays one. (The intrinsic is only ever emitted as a parameter
 *   prologue, so matching the call inside an annotated method is exact.)
 * - **Nullable parameters are untouched** — kotlinc emits no check for `p: T?`, so
 *   their domain keeps `null`.
 * - **Honest-JVM mode stays available**: `-Dbmc.kotlinNullableParams=true` (or the
 *   `bmc { kotlinNullableParams = true }` DSL) skips this pass, restoring the throwing
 *   prologue for proofs that deliberately model hostile Java callers. The flag is folded into
 *   the verdict-cache key (see `VerdictCache.computeKey`) so flipping it re-judges.
 * - **Java proofs are unaffected** — no Kotlin prologue, nothing matches.
 *
 * Mirrors the [MathBytecode] pattern: identical descriptor, one-instruction owner swap,
 * directory and jar entries mirrored via [ClasspathMirror].
 */
object KotlinParamBytecode {

    private const val INTRINSICS = "kotlin/jvm/internal/Intrinsics"
    private const val BMC_KOTLIN = "org/bmc4j/engine/BmcKotlin"
    private const val DESC = "(Ljava/lang/Object;Ljava/lang/String;)V"
    private const val BMC_PROOF = "Lorg/bmc4j/BmcProof;"
    private const val NOT_NULL = "Lorg/jetbrains/annotations/NotNull;"

    /** Current + legacy (pre-1.4 kotlinc) names of the parameter-check intrinsic. */
    private val PARAM_CHECKS = setOf("checkNotNullParameter", "checkParameterIsNotNull")

    private val CACHE = ConcurrentHashMap<String, String>()

    /** Rewrite directory AND jar entries of [classpath], returning the new classpath. Memoized
     *  per (flag, classpath) — the honest-JVM flag changes the output, so it is part of the key. */
    @JvmStatic
    fun rewrite(classpath: String): String {
        val honestJvm = java.lang.Boolean.getBoolean("bmc.kotlinNullableParams")
        if (honestJvm) {
            return classpath // honest-JVM mode: keep the throwing prologue, untouched.
        }
        return CACHE.computeIfAbsent(classpath, KotlinParamBytecode::doRewrite)
    }

    private fun doRewrite(classpath: String): String =
            ClasspathMirror.mirror(classpath, "kotlinparam", { b ->
                ClasspathMirror.Transformed(rewriteClass(b))
            })

    /** Pure transform: inside `@BmcProof` methods, swap the parameter-check intrinsic's owner
     *  to `BmcKotlin`. Exposed for unit tests. */
    internal fun rewriteClass(bytes: ByteArray): ByteArray {
        val cr = ClassReader(bytes)
        val cw = ClassWriter(0)
        val cv = object : ClassVisitor(Opcodes.ASM9, cw) {
            override fun visitMethod(a: Int, n: String?, d: String?, s: String?,
                                     ex: Array<String>?): MethodVisitor {
                val mv = super.visitMethod(a, n, d, s, ex)
                return object : MethodVisitor(Opcodes.ASM9, mv) {
                    private var isProof = false

                    override fun visitAnnotation(desc: String?, visible: Boolean): AnnotationVisitor? {
                        // Method annotations are visited before parameter annotations and before
                        // any instruction, so the flag is set in time for both rewrites below.
                        if (BMC_PROOF == desc) {
                            isProof = true
                        }
                        return super.visitAnnotation(desc, visible)
                    }

                    override fun visitParameterAnnotation(parameter: Int, desc: String?,
                                                          visible: Boolean): AnnotationVisitor? {
                        // JBMC asserts @NotNull on the entry function's nondet inputs ("Not null
                        // annotation check") — drop it from the proof's parameters so the relaxed
                        // prologue isn't re-imposed by the annotation. @Nullable is untouched.
                        if (isProof && NOT_NULL == desc) {
                            return null
                        }
                        return super.visitParameterAnnotation(parameter, desc, visible)
                    }

                    override fun visitMethodInsn(op: Int, mOwner: String?, name: String?,
                                                 desc: String?, itf: Boolean) {
                        if (isProof && op == Opcodes.INVOKESTATIC && INTRINSICS == mOwner
                                && DESC == desc && name in PARAM_CHECKS) {
                            // Identical descriptor -> operand stack unchanged; swap the owner (and
                            // normalize the legacy name) only.
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, BMC_KOTLIN,
                                    "assumeNotNullParameter", DESC, false)
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
