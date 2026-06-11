package org.bmc4j.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * Unit tests for [NondetTagBytecode] — the explicit USER-nondet witness tag pass. It must inject a
 * `Bmc.recordNondet("name", value)` after each user `Bmc.any*` store (the right overload per kind),
 * name a proof's own input BARE and a helper/model nondet CLASS-QUALIFIED, and leave bundled-model /
 * reserved-namespace classes untouched (user-origin scoping). Verification-neutrality (the sink is
 * empty-body) is a property of `Bmc.recordNondet`, not this pass.
 */
internal class NondetTagBytecodeTest {

    /** Every `recordNondet` call the rewritten [clazz] makes, as `"<nameLiteral>|<descriptor>"`, in
     *  emission order. The name literal is recovered from the LDC immediately preceding the call. */
    private fun recordCalls(clazz: ByteArray): List<String> {
        val out = ArrayList<String>()
        ClassReader(clazz).accept(object : ClassVisitor(Opcodes.ASM9) {
            override fun visitMethod(a: Int, n: String?, d: String?, s: String?,
                                     e: Array<String>?): MethodVisitor =
                    object : MethodVisitor(Opcodes.ASM9) {
                        private var lastLdc: String? = null
                        override fun visitLdcInsn(value: Any?) {
                            if (value is String) lastLdc = value
                        }
                        override fun visitMethodInsn(op: Int, owner: String?, name: String?,
                                                     desc: String?, itf: Boolean) {
                            if (owner == "org/bmc4j/Bmc" && name == "recordNondet") {
                                out.add("$lastLdc|$desc")
                            }
                        }
                    }
        }, 0)
        return out
    }

    @Test
    fun proof_int_input_is_tagged_with_a_bare_name() {
        val bytes = classWithMethod("MyProof", "p", isProof = true) { mv ->
            // int x = Bmc.anyInt();  (x in slot 0)
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/bmc4j/Bmc", "anyInt", "()I", false)
            mv.visitVarInsn(Opcodes.ISTORE, 0)
            mv.visitInsn(Opcodes.RETURN)
        }
        val calls = recordCalls(NondetTagBytecode.rewriteClass(bytes))
        // Bare name (proof's own input), widened int -> long sink.
        assertEquals(listOf("x|(Ljava/lang/String;J)V"), calls)
    }

    @Test
    fun model_nondet_is_class_qualified() {
        val bytes = classWithMethod("DbRepoModel", "query", isProof = false) { mv ->
            // int result = Bmc.anyInt();  (result in slot 0)
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/bmc4j/Bmc", "anyInt", "()I", false)
            mv.visitVarInsn(Opcodes.ISTORE, 0)
            mv.visitInsn(Opcodes.RETURN)
        }
        val calls = recordCalls(NondetTagBytecode.rewriteClass(bytes))
        // NOT a proof method -> qualified by the simple class name: DbRepoModel.result.
        assertEquals(listOf("DbRepoModel.result|(Ljava/lang/String;J)V"), calls)
    }

    @Test
    fun each_primitive_kind_uses_its_overload() {
        // long l, boolean b, float f, double d, short s, byte by, char c — one store each, distinct slots.
        val bytes = classWithMethod("Kinds", "all", isProof = true) { mv ->
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/bmc4j/Bmc", "anyLong", "()J", false)
            mv.visitVarInsn(Opcodes.LSTORE, 0) // long occupies slots 0-1
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/bmc4j/Bmc", "anyBoolean", "()Z", false)
            mv.visitVarInsn(Opcodes.ISTORE, 2)
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/bmc4j/Bmc", "anyFloat", "()F", false)
            mv.visitVarInsn(Opcodes.FSTORE, 3)
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/bmc4j/Bmc", "anyDouble", "()D", false)
            mv.visitVarInsn(Opcodes.DSTORE, 4) // double occupies 4-5
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/bmc4j/Bmc", "anyShort", "()S", false)
            mv.visitVarInsn(Opcodes.ISTORE, 6)
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/bmc4j/Bmc", "anyChar", "()C", false)
            mv.visitVarInsn(Opcodes.ISTORE, 7)
            mv.visitInsn(Opcodes.RETURN)
        }
        val descs = recordCalls(NondetTagBytecode.rewriteClass(bytes)).map { it.substringAfter('|') }
        assertEquals(listOf(
                "(Ljava/lang/String;J)V", // long
                "(Ljava/lang/String;Z)V", // boolean
                "(Ljava/lang/String;F)V", // float
                "(Ljava/lang/String;D)V", // double
                "(Ljava/lang/String;J)V", // short -> long
                "(Ljava/lang/String;J)V"), // char -> long
                descs)
    }

    @Test
    fun string_input_uses_the_string_overload() {
        val bytes = classWithMethod("P", "s", isProof = true) { mv ->
            mv.visitInsn(Opcodes.ICONST_2)
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/bmc4j/Bmc", "anyString", "(I)Ljava/lang/String;", false)
            mv.visitVarInsn(Opcodes.ASTORE, 0)
            mv.visitInsn(Opcodes.RETURN)
        }
        assertEquals(listOf("region|(Ljava/lang/String;Ljava/lang/String;)V"),
                recordCalls(NondetTagBytecode.rewriteClass(bytes)))
    }

    @Test
    fun array_and_anyOf_use_the_object_overload() {
        val bytes = classWithMethod("P", "arr", isProof = true) { mv ->
            mv.visitInsn(Opcodes.ICONST_4)
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/bmc4j/Bmc", "anyArrayOfInts", "(I)[I", false)
            mv.visitVarInsn(Opcodes.ASTORE, 0)
            mv.visitInsn(Opcodes.RETURN)
        }
        assertEquals(listOf("a|(Ljava/lang/String;Ljava/lang/Object;)V"),
                recordCalls(NondetTagBytecode.rewriteClass(bytes)))
    }

    @Test
    fun reserved_namespace_classes_are_untouched() {
        // A class in a reserved namespace (a bundled model) that internally calls Bmc.anyInt must NOT be
        // tagged — its nondet is modelling havoc, not a user input. The bytes come back unchanged.
        val bytes = classWithMethod("Foo", "m", isProof = false, internalClass = "java/util/Foo") { mv ->
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/bmc4j/Bmc", "anyInt", "()I", false)
            mv.visitVarInsn(Opcodes.ISTORE, 0)
            mv.visitInsn(Opcodes.RETURN)
        }
        val rewritten = NondetTagBytecode.rewriteClass(bytes)
        assertTrue(recordCalls(rewritten).isEmpty(), "a reserved-namespace class is never tagged")
        org.junit.jupiter.api.Assertions.assertArrayEquals(bytes, rewritten,
                "a reserved-namespace class comes back byte-for-byte unchanged")
    }

    @Test
    fun a_non_nondet_invoke_is_not_tagged() {
        // An ordinary call followed by a store must NOT be tagged (only Bmc.any* sites are).
        val bytes = classWithMethod("P", "m", isProof = true) { mv ->
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "parseInt", "(Ljava/lang/String;)I", false)
            mv.visitVarInsn(Opcodes.ISTORE, 1)
            mv.visitInsn(Opcodes.RETURN)
        }
        // parseInt's arg: load a constant so the method is well-formed.
        assertTrue(recordCalls(NondetTagBytecode.rewriteClass(bytes)).isEmpty())
    }

    // --- helpers --------------------------------------------------------------

    /**
     * Build a class named [internalClass] (default `pkg/<simpleName>`) with one static method [method]
     * carrying (optionally) the `@BmcProof` annotation and a LocalVariableTable so the tag's name
     * resolves. [body] writes the method body; [localName] (if used by the caller's body) is declared at
     * slot 0..N as needed. For simplicity every slot 0..7 is declared with a name derived from the test.
     */
    private fun classWithMethod(simpleName: String, method: String, isProof: Boolean,
                                internalClass: String? = null,
                                body: (MethodVisitor) -> Unit): ByteArray {
        val internal = internalClass ?: "pkg/$simpleName"
        val cw = ClassWriter(ClassWriter.COMPUTE_MAXS or ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, internal, null, "java/lang/Object", null)
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, method, "()V", null, null)
        if (isProof) {
            mv.visitAnnotation("Lorg/bmc4j/BmcProof;", true).visitEnd()
        }
        mv.visitCode()
        val start = Label()
        val end = Label()
        mv.visitLabel(start)
        body(mv)
        mv.visitLabel(end)
        // Declare named locals so the tag resolves a name (slot 0 = x/result/region/a, others named nN).
        for (slot in 0..7) {
            val name = when (slot) {
                0 -> defaultLocalName(method)
                2 -> "b"; 3 -> "f"; 4 -> "d"; 6 -> "s"; 7 -> "c"
                else -> "n$slot"
            }
            mv.visitLocalVariable(name, "I", null, start, end, slot)
        }
        mv.visitMaxs(0, 0)
        mv.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }

    /** The slot-0 local name keyed off the method, so each test's primary input resolves a clear name. */
    private fun defaultLocalName(method: String): String = when (method) {
        "query" -> "result"
        "s" -> "region"
        "arr" -> "a"
        "all" -> "n0"
        else -> "x"
    }
}
