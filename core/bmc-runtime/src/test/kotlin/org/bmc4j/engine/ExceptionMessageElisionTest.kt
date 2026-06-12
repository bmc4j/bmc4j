package org.bmc4j.engine

import org.bmc4j.RemoveExceptionMessages
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.nio.file.Files
import java.nio.file.Path

/**
 * Unit tests for [ExceptionMessageElision]:
 *  - the **rewrite** drops the message-building bytecode at a `Throwable`-subtype `<init>(String)` site
 *    and passes `null`, leaves multi-arg and non-`Throwable` ctors verbatim, and produces JVM-valid
 *    bytecode that still constructs/throws the exception (with a null message);
 *  - the **observability gate** elides iff the reachable cone contains no exception-message observer,
 *    and never elides when an observer exists or the cone can't be bounded — the soundness contract.
 */
internal class ExceptionMessageElisionTest {

    // ---- rewrite ----------------------------------------------------------------------------------

    /** `isThrowable` that recognizes only the JDK roots (no classpath bytes) — enough for the rewrite
     *  tests, which throw `IllegalArgumentException` / a custom subtype we mark explicitly. */
    private val jdkThrowable: (String) -> Boolean = { n ->
        ExceptionMessageElision.Index("").isThrowable(n)
    }

    @Test
    fun single_string_arg_Throwable_ctor_has_its_message_elided() {
        // static void f(int n) { throw new IllegalArgumentException(new StringBuilder().append("bad: ")
        //                                                            .append(n).toString()); }
        // The whole message build (NEW StringBuilder; append; append; toString) sits between the
        // NEW IAE; DUP and the IAE.<init>(String) — it must be DROPPED, replaced by ACONST_NULL.
        val bytes = throwingClass("ThrowIae", "java/lang/IllegalArgumentException", withMessageBuild = true)
        val rewritten = ExceptionMessageElision.rewriteClass(bytes, jdkThrowable)
        val calls = methodCalls(rewritten)

        assertTrue(calls.any { it.contains("java/lang/IllegalArgumentException.<init>(Ljava/lang/String;)V") },
                "the exception is still constructed via <init>(String): $calls")
        assertFalse(calls.any { it.contains("StringBuilder") },
                "the message-building StringBuilder calls must be DROPPED: $calls")
        assertTrue(opcodes(rewritten).contains("ACONST_NULL"),
                "the dropped message must be replaced by an explicit null")

        // The exception is STILL thrown — only its message is null now. (JVM-valid bytecode runs.)
        val c = define("ThrowIae", rewritten)
        val m = c.getMethod("f", Int::class.javaPrimitiveType)
        val thrown = runCatching { m.invoke(null, 7) }.exceptionOrNull()?.cause
        assertTrue(thrown is IllegalArgumentException, "still throws IllegalArgumentException, got $thrown")
        assertNull((thrown as IllegalArgumentException).message, "the message was elided to null")
    }

    @Test
    fun custom_Throwable_subtype_single_string_ctor_is_elided() {
        // A user exception resolved as a Throwable via the super-chain (extends RuntimeException).
        val bytes = throwingClass("ThrowCustom", "my/Boom", withMessageBuild = true)
        val isThrowable: (String) -> Boolean = { n -> n == "my/Boom" || jdkThrowable(n) }
        val rewritten = ExceptionMessageElision.rewriteClass(bytes, isThrowable)
        val calls = methodCalls(rewritten)
        assertTrue(calls.any { it.contains("my/Boom.<init>(Ljava/lang/String;)V") },
                "the custom exception is still constructed: $calls")
        assertFalse(calls.any { it.contains("StringBuilder") },
                "the message build must be dropped for a custom Throwable too: $calls")
    }

    @Test
    fun non_Throwable_single_string_ctor_is_left_verbatim() {
        // new java/lang/StringBuilder(String) — a String-arg ctor on a non-Throwable: never elided.
        val cw = ClassWriter(ClassWriter.COMPUTE_MAXS or ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "MakeSb", null, "java/lang/Object", null)
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "f",
                "()Ljava/lang/StringBuilder;", null, null)
        mv.visitCode()
        mv.visitTypeInsn(Opcodes.NEW, "java/lang/StringBuilder")
        mv.visitInsn(Opcodes.DUP)
        mv.visitLdcInsn("seed")
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/StringBuilder", "<init>",
                "(Ljava/lang/String;)V", false)
        mv.visitInsn(Opcodes.ARETURN)
        mv.visitMaxs(0, 0)
        mv.visitEnd()
        cw.visitEnd()

        val rewritten = ExceptionMessageElision.rewriteClass(cw.toByteArray(), jdkThrowable)
        val ops = opcodes(rewritten)
        assertTrue(methodCalls(rewritten).any { it.contains("java/lang/StringBuilder.<init>(Ljava/lang/String;)V") },
                "a non-Throwable String ctor stays verbatim")
        assertFalse(ops.contains("ACONST_NULL"), "no elision for a non-Throwable ctor")
        val c = define("MakeSb", rewritten)
        assertEquals("seed", (c.getMethod("f").invoke(null) as StringBuilder).toString())
    }

    @Test
    fun multi_arg_Throwable_ctor_is_left_verbatim() {
        // new IllegalStateException(message, cause) — (String, Throwable): the message can't be isolated
        // from the other operand soundly, so the site is left untouched (no elision).
        val cw = ClassWriter(ClassWriter.COMPUTE_MAXS or ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "ThrowTwo", null, "java/lang/Object", null)
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "f", "()V", null, null)
        mv.visitCode()
        mv.visitTypeInsn(Opcodes.NEW, "java/lang/IllegalStateException")
        mv.visitInsn(Opcodes.DUP)
        mv.visitLdcInsn("boom")
        mv.visitInsn(Opcodes.ACONST_NULL) // the cause
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/IllegalStateException", "<init>",
                "(Ljava/lang/String;Ljava/lang/Throwable;)V", false)
        mv.visitInsn(Opcodes.ATHROW)
        mv.visitMaxs(0, 0)
        mv.visitEnd()
        cw.visitEnd()

        val rewritten = ExceptionMessageElision.rewriteClass(cw.toByteArray(), jdkThrowable)
        assertTrue(methodCalls(rewritten).any {
            it.contains("java/lang/IllegalStateException.<init>(Ljava/lang/String;Ljava/lang/Throwable;)V")
        }, "a multi-arg Throwable ctor is left verbatim (not elided)")
        // The literal "boom" LDC is still present (message build was NOT dropped).
        assertTrue(opcodes(rewritten).contains("LDC \"boom\""), "the multi-arg message is preserved")
    }

    @Test
    fun message_build_with_intervening_label_is_still_elided() {
        // A LineNumberTable anchor (label + line) sits inside the message-building region, the shape a
        // multi-line `throw new IAE("..." + x)` emits. The region drop must NOT abandon on that label
        // (no stack effect), exactly like StringBytecode's #296 fix.
        val cw = ClassWriter(ClassWriter.COMPUTE_MAXS or ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "ThrowLbl", null, "java/lang/Object", null)
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "f", "()V", null, null)
        mv.visitCode()
        mv.visitTypeInsn(Opcodes.NEW, "java/lang/IllegalArgumentException")
        mv.visitInsn(Opcodes.DUP)
        mv.visitLdcInsn("seed")
        val mid = org.objectweb.asm.Label()
        mv.visitLabel(mid)
        mv.visitLineNumber(99, mid)
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/IllegalArgumentException", "<init>",
                "(Ljava/lang/String;)V", false)
        mv.visitInsn(Opcodes.ATHROW)
        mv.visitMaxs(0, 0)
        mv.visitEnd()
        cw.visitEnd()

        val rewritten = ExceptionMessageElision.rewriteClass(cw.toByteArray(), jdkThrowable)
        val ops = opcodes(rewritten)
        assertTrue(ops.contains("ACONST_NULL"), "an intervening label must not defeat the elision")
        assertFalse(ops.contains("LDC \"seed\""), "the message literal must be dropped")
        // Still valid bytecode that throws.
        val c = define("ThrowLbl", rewritten)
        val thrown = runCatching { c.getMethod("f").invoke(null) }.exceptionOrNull()?.cause
        assertTrue(thrown is IllegalArgumentException)
        assertNull((thrown as IllegalArgumentException).message)
    }

    // ---- observability gate -----------------------------------------------------------------------

    @Test
    fun gate_does_not_elide_when_a_message_observer_is_reachable(@TempDir dir: Path) {
        val classes = Files.createDirectory(dir.resolve("classes"))
        // Entry.p observes e.getMessage() — an exception-message observer reachable from the entry. Must
        // NOT auto-elide.
        writeObserverClass(classes, "Entry", observe = true)
        val d = ExceptionMessageElision.decide(classes.toString(), "Entry", "p", RemoveExceptionMessages.AUTO)
        assertFalse(d.elide, "a reachable getMessage() observer must suppress auto-elision: ${d.reason}")
    }

    @Test
    fun gate_elides_when_no_message_observer_is_reachable(@TempDir dir: Path) {
        val classes = Files.createDirectory(dir.resolve("classes"))
        // Entry.p never reads any exception message. No observer reachable -> elision is sound.
        writeObserverClass(classes, "Entry", observe = false)
        val d = ExceptionMessageElision.decide(classes.toString(), "Entry", "p", RemoveExceptionMessages.AUTO)
        assertTrue(d.elide, "no reachable observer -> elide: ${d.reason}")
        assertFalse(d.forced, "an AUTO elision is not forced")
    }

    @Test
    fun gate_does_not_elide_when_an_observer_is_reachable_through_a_callee(@TempDir dir: Path) {
        val classes = Files.createDirectory(dir.resolve("classes"))
        // Entry.p calls Helper.h, which reads getMessage(): the observer is reached transitively, through
        // a call edge — the walk must follow it and decline.
        writeObserverThroughCallee(classes)
        val d = ExceptionMessageElision.decide(classes.toString(), "Entry", "p", RemoveExceptionMessages.AUTO)
        assertFalse(d.elide, "a transitively-reachable observer must suppress auto-elision: ${d.reason}")
    }

    @Test
    fun gate_does_not_elide_when_the_callgraph_cannot_be_bounded(@TempDir dir: Path) {
        val classes = Files.createDirectory(dir.resolve("classes"))
        // Entry.p uses Class.forName — an opaque dispatch the walk can't follow. An observer could hide
        // behind it, so AUTO must NOT elide.
        writeReflectionClass(classes, "Entry")
        val d = ExceptionMessageElision.decide(classes.toString(), "Entry", "p", RemoveExceptionMessages.AUTO)
        assertFalse(d.elide, "an unbounded call-graph must suppress auto-elision: ${d.reason}")
    }

    @Test
    fun ON_forces_elision_and_OFF_disables_it() {
        val on = ExceptionMessageElision.decide("", "Entry", "p", RemoveExceptionMessages.ON)
        assertTrue(on.elide && on.forced, "ON forces elision (a user-asserted override)")
        val off = ExceptionMessageElision.decide("", "Entry", "p", RemoveExceptionMessages.OFF)
        assertFalse(off.elide, "OFF never elides")
    }

    @Test
    fun isThrowable_resolves_a_subtype_over_the_classpath(@TempDir dir: Path) {
        val classes = Files.createDirectory(dir.resolve("classes"))
        // my/Boom extends RuntimeException (a known JDK root) -> resolves Throwable via the super chain.
        writeSubclass(classes, "my/Boom", "java/lang/RuntimeException")
        // my/Plain extends Object -> not a Throwable.
        writeSubclass(classes, "my/Plain", "java/lang/Object")
        val index = ExceptionMessageElision.Index(classes.toString())
        assertTrue(index.isThrowable("my/Boom"), "a RuntimeException subtype is a Throwable")
        assertFalse(index.isThrowable("my/Plain"), "an Object subtype is not a Throwable")
        assertTrue(index.isThrowable("java/lang/IllegalArgumentException"), "a known JDK exception")
        assertFalse(index.isThrowable("java/lang/Object"), "Object is not a Throwable")
    }

    // ---- helpers ----------------------------------------------------------------------------------

    /** static void f(int n) { throw new <exc>(<message build?>); } — the message is a
     *  StringBuilder().append("bad: ").append(n).toString() when [withMessageBuild], else a literal. */
    private fun throwingClass(className: String, exc: String, withMessageBuild: Boolean): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_MAXS or ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, className, null, "java/lang/Object", null)
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "f", "(I)V", null, null)
        mv.visitCode()
        mv.visitTypeInsn(Opcodes.NEW, exc)
        mv.visitInsn(Opcodes.DUP)
        if (withMessageBuild) {
            mv.visitTypeInsn(Opcodes.NEW, "java/lang/StringBuilder")
            mv.visitInsn(Opcodes.DUP)
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false)
            mv.visitLdcInsn("bad: ")
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
                    "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false)
            mv.visitVarInsn(Opcodes.ILOAD, 0)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
                    "(I)Ljava/lang/StringBuilder;", false)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "toString",
                    "()Ljava/lang/String;", false)
        } else {
            mv.visitLdcInsn("bad")
        }
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, exc, "<init>", "(Ljava/lang/String;)V", false)
        mv.visitInsn(Opcodes.ATHROW)
        mv.visitMaxs(0, 0)
        mv.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }

    /** A class whose method throws an exception and (optionally) also reads `e.getMessage()`. */
    private fun writeObserverClass(dir: Path, internalName: String, observe: Boolean) {
        val cw = ClassWriter(ClassWriter.COMPUTE_MAXS or ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, internalName, null, "java/lang/Object", null)
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "p",
                "(Ljava/lang/Throwable;)Ljava/lang/String;", null, null)
        mv.visitCode()
        if (observe) {
            mv.visitVarInsn(Opcodes.ALOAD, 0)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Throwable", "getMessage",
                    "()Ljava/lang/String;", false)
            mv.visitInsn(Opcodes.ARETURN)
        } else {
            mv.visitInsn(Opcodes.ACONST_NULL)
            mv.visitInsn(Opcodes.ARETURN)
        }
        mv.visitMaxs(0, 0)
        mv.visitEnd()
        cw.visitEnd()
        writeClassFile(dir, internalName, cw.toByteArray())
    }

    /** Entry.p() -> Helper.h(Throwable) -> e.getMessage(): the observer is one call edge away. */
    private fun writeObserverThroughCallee(dir: Path) {
        val helper = ClassWriter(ClassWriter.COMPUTE_MAXS or ClassWriter.COMPUTE_FRAMES)
        helper.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "Helper", null, "java/lang/Object", null)
        val h = helper.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "h",
                "(Ljava/lang/Throwable;)Ljava/lang/String;", null, null)
        h.visitCode()
        h.visitVarInsn(Opcodes.ALOAD, 0)
        h.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Throwable", "getMessage",
                "()Ljava/lang/String;", false)
        h.visitInsn(Opcodes.ARETURN)
        h.visitMaxs(0, 0)
        h.visitEnd()
        helper.visitEnd()
        writeClassFile(dir, "Helper", helper.toByteArray())

        val entry = ClassWriter(ClassWriter.COMPUTE_MAXS or ClassWriter.COMPUTE_FRAMES)
        entry.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "Entry", null, "java/lang/Object", null)
        val p = entry.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "p",
                "(Ljava/lang/Throwable;)Ljava/lang/String;", null, null)
        p.visitCode()
        p.visitVarInsn(Opcodes.ALOAD, 0)
        p.visitMethodInsn(Opcodes.INVOKESTATIC, "Helper", "h",
                "(Ljava/lang/Throwable;)Ljava/lang/String;", false)
        p.visitInsn(Opcodes.ARETURN)
        p.visitMaxs(0, 0)
        p.visitEnd()
        entry.visitEnd()
        writeClassFile(dir, "Entry", entry.toByteArray())
    }

    private fun writeReflectionClass(dir: Path, internalName: String) {
        val cw = ClassWriter(ClassWriter.COMPUTE_MAXS or ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, internalName, null, "java/lang/Object", null)
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "p", "()V", null, null)
        mv.visitCode()
        mv.visitLdcInsn("X")
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Class", "forName",
                "(Ljava/lang/String;)Ljava/lang/Class;", false)
        mv.visitInsn(Opcodes.POP)
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(0, 0)
        mv.visitEnd()
        cw.visitEnd()
        writeClassFile(dir, internalName, cw.toByteArray())
    }

    private fun writeSubclass(dir: Path, internalName: String, superName: String) {
        val cw = ClassWriter(0)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, internalName, null, superName, null)
        cw.visitEnd()
        writeClassFile(dir, internalName, cw.toByteArray())
    }

    private fun writeClassFile(dir: Path, internalName: String, bytes: ByteArray) {
        val target = dir.resolve("$internalName.class")
        Files.createDirectories(target.parent)
        Files.write(target, bytes)
    }

    private fun opcodes(clazz: ByteArray): List<String> {
        val ops = ArrayList<String>()
        ClassReader(clazz).accept(object : ClassVisitor(Opcodes.ASM9) {
            override fun visitMethod(a: Int, n: String?, d: String?, s: String?,
                                     e: Array<String>?): MethodVisitor {
                return object : MethodVisitor(Opcodes.ASM9) {
                    override fun visitInsn(op: Int) {
                        if (op == Opcodes.ACONST_NULL) ops.add("ACONST_NULL")
                    }

                    override fun visitLdcInsn(value: Any?) {
                        ops.add("LDC \"$value\"")
                    }
                }
            }
        }, 0)
        return ops
    }

    private fun methodCalls(clazz: ByteArray): List<String> {
        val calls = ArrayList<String>()
        ClassReader(clazz).accept(object : ClassVisitor(Opcodes.ASM9) {
            override fun visitMethod(a: Int, n: String?, d: String?, s: String?,
                                     e: Array<String>?): MethodVisitor {
                return object : MethodVisitor(Opcodes.ASM9) {
                    override fun visitMethodInsn(op: Int, owner: String?, name: String?,
                                                 desc: String?, itf: Boolean) {
                        calls.add("$owner.$name$desc")
                    }
                }
            }
        }, 0)
        return calls
    }

    private fun define(name: String, bytes: ByteArray): Class<*> =
            object : ClassLoader(ExceptionMessageElisionTest::class.java.classLoader) {
                fun def(): Class<*> = defineClass(name, bytes, 0, bytes.size)
            }.def()
}
