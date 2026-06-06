package org.bmc4j.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * Unit tests for [KotlinParamBytecode]: the kotlinc `checkNotNullParameter` prologue is
 * redirected to the assume shim inside `@BmcProof` methods ONLY — interior (un-annotated)
 * methods keep the throwing intrinsic, which is what keeps a null flowing into a non-null parameter
 * *inside* the analyzed code a real, refutable bug. End-to-end semantics are pinned by the
 * `proofs.symbolicparams` examples.
 */
internal class KotlinParamBytecodeTest {

    @Test
    fun redirects_param_check_inside_proof_methods() {
        val calls = methodCalls(KotlinParamBytecode.rewriteClass(
                classWithCheck(true, "checkNotNullParameter")))
        assertTrue(calls.contains("INVOKESTATIC $SHIM"),
                "proof-method prologue should be redirected to the assume shim: $calls")
        assertFalse(calls.any { it.contains("Intrinsics.checkNotNullParameter") },
                "original intrinsic call must be gone: $calls")
    }

    @Test
    fun redirects_legacy_param_check_name() {
        val calls = methodCalls(KotlinParamBytecode.rewriteClass(
                classWithCheck(true, "checkParameterIsNotNull")))
        assertTrue(calls.contains("INVOKESTATIC $SHIM"),
                "legacy-named prologue should be redirected too: $calls")
    }

    @Test
    fun leaves_interior_methods_throwing() {
        // The same call in an UN-annotated method — an interior callee — must keep the throwing
        // intrinsic: a null reaching a non-null parameter inside analyzed code is a real bug.
        val calls = methodCalls(KotlinParamBytecode.rewriteClass(
                classWithCheck(false, "checkNotNullParameter")))
        assertTrue(calls.any { it.contains("Intrinsics.checkNotNullParameter") },
                "interior parameter check must keep throwing semantics: $calls")
        assertFalse(calls.any { it.contains("BmcKotlin") },
                "no redirect outside @BmcProof methods: $calls")
    }

    @Test
    fun leaves_other_intrinsics_untouched_even_in_proof_methods() {
        // checkNotNull / checkNotNullExpressionValue back !!, lateinit, platform-type asserts —
        // those are the user's own null-safety logic and stay refutable inside proofs.
        for (other in arrayOf("checkNotNullExpressionValue", "checkNotNull")) {
            val desc = if (other == "checkNotNull") "(Ljava/lang/Object;)V" else DESC
            val calls = methodCalls(KotlinParamBytecode.rewriteClass(
                    classWithCall(true, other, desc)))
            assertTrue(calls.any { it.contains("Intrinsics.$other") },
                    "$other must pass through: $calls")
            assertFalse(calls.any { it.contains("BmcKotlin") },
                    "no redirect for $other: $calls")
        }
    }

    @Test
    fun strips_notnull_param_annotation_from_proof_methods() {
        // kotlinc also stamps @org.jetbrains.annotations.NotNull on the parameter, and JBMC asserts
        // it against the entry function's nondet inputs ("Not null annotation check") — it must go
        // wherever the prologue is relaxed, or the annotation re-imposes the refutation.
        assertFalse(paramAnnotations(KotlinParamBytecode.rewriteClass(
                classWithCheck(true, "checkNotNullParameter")))
                .contains("Lorg/jetbrains/annotations/NotNull;"),
                "@NotNull must be stripped from proof-method parameters")
    }

    @Test
    fun keeps_notnull_param_annotation_on_interior_methods() {
        assertTrue(paramAnnotations(KotlinParamBytecode.rewriteClass(
                classWithCheck(false, "checkNotNullParameter")))
                .contains("Lorg/jetbrains/annotations/NotNull;"),
                "interior methods keep their @NotNull parameter annotations")
    }

    @Test
    fun honest_jvm_flag_skips_the_pass() {
        // -Dbmc.kotlinNullableParams=true restores the throwing prologue: the pass returns the
        // classpath untouched (no mirror is even attempted — the path below doesn't exist).
        val prev = System.getProperty("bmc.kotlinNullableParams")
        try {
            System.setProperty("bmc.kotlinNullableParams", "true")
            assertEquals("/no/such/classes",
                    KotlinParamBytecode.rewrite("/no/such/classes"),
                    "honest-JVM mode must leave the classpath untouched")
        } finally {
            if (prev == null) {
                System.clearProperty("bmc.kotlinNullableParams")
            } else {
                System.setProperty("bmc.kotlinNullableParams", prev)
            }
        }
    }

    companion object {
        private const val INTRINSICS = "kotlin/jvm/internal/Intrinsics"
        private const val DESC = "(Ljava/lang/Object;Ljava/lang/String;)V"
        private const val SHIM = "org/bmc4j/engine/BmcKotlin.assumeNotNullParameter$DESC"

        // ---- helpers ----

        private fun classWithCheck(annotated: Boolean, checkName: String): ByteArray {
            return classWithCall(annotated, checkName, DESC)
        }

        /** A class with one method (optionally @BmcProof-annotated) making one Intrinsics static call. */
        private fun classWithCall(annotated: Boolean, name: String, desc: String): ByteArray {
            val cw = ClassWriter(ClassWriter.COMPUTE_MAXS or ClassWriter.COMPUTE_FRAMES)
            cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "C", null, "java/lang/Object", null)
            val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "m", "(Ljava/lang/Object;)V", null, null)
            if (annotated) {
                mv.visitAnnotation("Lorg/bmc4j/BmcProof;", true).visitEnd()
            }
            mv.visitParameterAnnotation(0, "Lorg/jetbrains/annotations/NotNull;", false).visitEnd()
            mv.visitCode()
            mv.visitVarInsn(Opcodes.ALOAD, 1)
            if (DESC == desc) {
                mv.visitLdcInsn("p")
            }
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, INTRINSICS, name, desc, false)
            mv.visitInsn(Opcodes.RETURN)
            mv.visitMaxs(0, 0)
            mv.visitEnd()
            cw.visitEnd()
            return cw.toByteArray()
        }

        private fun paramAnnotations(clazz: ByteArray): List<String> {
            val annotations = ArrayList<String>()
            ClassReader(clazz).accept(object : ClassVisitor(Opcodes.ASM9) {
                override fun visitMethod(a: Int, n: String?, d: String?, s: String?,
                                         e: Array<String>?): MethodVisitor {
                    return object : MethodVisitor(Opcodes.ASM9) {
                        override fun visitParameterAnnotation(
                                parameter: Int, desc: String?, visible: Boolean): AnnotationVisitor? {
                            annotations.add(desc!!)
                            return null
                        }
                    }
                }
            }, 0)
            return annotations
        }

        private fun methodCalls(clazz: ByteArray): List<String> {
            val calls = ArrayList<String>()
            ClassReader(clazz).accept(object : ClassVisitor(Opcodes.ASM9) {
                override fun visitMethod(a: Int, n: String?, d: String?, s: String?,
                                         e: Array<String>?): MethodVisitor {
                    return object : MethodVisitor(Opcodes.ASM9) {
                        override fun visitMethodInsn(op: Int, owner: String?, name: String?,
                                                     desc: String?, itf: Boolean) {
                            calls.add((if (op == Opcodes.INVOKESTATIC) "INVOKESTATIC " else "INVOKE ")
                                    + owner + "." + name + desc)
                        }
                    }
                }
            }, 0)
            return calls
        }
    }
}
