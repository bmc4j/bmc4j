package org.bmc4j.engine

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import java.nio.file.Files
import java.nio.file.Path

/**
 * The text/String-use classifier — the safety gate that decides whether the fast (String-reasoning-off)
 * external SAT solver may engage. It must over-approximate toward "text-using": a false "text-free" would
 * let the fast solver serve an unsound pass, so every uncertainty (a text type reached, or an unbounded
 * cone) resolves to text-using.
 */
internal class StringUseClassifierTest {

    @Test
    fun numericOnlyProof_isTextFree(@TempDir dir: Path) {
        val classes = Files.createDirectory(dir.resolve("classes"))
        // Entry -> Helper, neither touches any text type.
        writeNumericClass(classes, "Helper")
        writeEntryReferencing(classes, "Entry", "Helper")
        assertFalse(StringUseClassifier.usesText("Entry", classes.toString()),
                "a numeric-only proof reaching no text types must be classified text-free")
    }

    @Test
    fun proofReachingString_isTextUsing(@TempDir dir: Path) {
        val classes = Files.createDirectory(dir.resolve("classes"))
        writeEntryReferencing(classes, "Entry", "java/lang/String")
        assertTrue(StringUseClassifier.usesText("Entry", classes.toString()),
                "a proof whose cone references java.lang.String must be text-using")
    }

    @Test
    fun proofReachingStringBuilder_isTextUsing(@TempDir dir: Path) {
        val classes = Files.createDirectory(dir.resolve("classes"))
        writeEntryReferencing(classes, "Entry", "java/lang/StringBuilder")
        assertTrue(StringUseClassifier.usesText("Entry", classes.toString()),
                "a proof reaching StringBuilder must be text-using")
    }

    @Test
    fun proofReachingCharSequence_isTextUsing(@TempDir dir: Path) {
        val classes = Files.createDirectory(dir.resolve("classes"))
        writeEntryReferencing(classes, "Entry", "java/lang/CharSequence")
        assertTrue(StringUseClassifier.usesText("Entry", classes.toString()),
                "a proof reaching CharSequence must be text-using")
    }

    @Test
    fun unboundedCone_isTreatedAsTextUsing(@TempDir dir: Path) {
        val classes = Files.createDirectory(dir.resolve("classes"))
        // A reflection call forces ReachableCone's whole-classpath fallback — an unbounded cone.
        writeEntryCalling(classes, "Entry", "java/lang/Class", "forName",
                "(Ljava/lang/String;)Ljava/lang/Class;", Opcodes.INVOKESTATIC)
        assertTrue(StringUseClassifier.usesText("Entry", classes.toString()),
                "an unbounded cone (reflection) must be treated as text-using — we can't prove it text-free")
    }

    @Test
    fun missingEntryClass_isTreatedAsTextUsing(@TempDir dir: Path) {
        val classes = Files.createDirectory(dir.resolve("classes"))
        writeNumericClass(classes, "Other")
        // Entry not on the classpath -> ReachableCone falls back to whole -> can't be proven text-free.
        assertTrue(StringUseClassifier.usesText("NotThere", classes.toString()),
                "an unbounded cone (missing entry class) must be treated as text-using")
    }

    @Test
    fun nullClasspath_isTreatedAsTextUsing() {
        assertTrue(StringUseClassifier.usesText("Entry", null),
                "an empty/null classpath can't be proven text-free, so it must be text-using")
    }

    // --- helpers ---------------------------------------------------------------

    private fun writeBytes(dir: Path, internalName: String, bytes: ByteArray) {
        val f = dir.resolve("$internalName.class")
        Files.createDirectories(f.parent)
        Files.write(f, bytes)
    }

    /** A class with a static `run()` doing only integer arithmetic — no reference types. */
    private fun writeNumericClass(dir: Path, name: String) {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null)
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "run", "()I", null, null)
        mv.visitCode()
        mv.visitInsn(Opcodes.ICONST_2)
        mv.visitInsn(Opcodes.ICONST_3)
        mv.visitInsn(Opcodes.IADD)
        mv.visitInsn(Opcodes.IRETURN)
        mv.visitMaxs(0, 0)
        mv.visitEnd()
        cw.visitEnd()
        writeBytes(dir, name, cw.toByteArray())
    }

    /** An Entry class whose `run()` references [referenced] as a type (so it's in the cone). */
    private fun writeEntryReferencing(dir: Path, name: String, referenced: String) {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null)
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "run", "()V", null, null)
        mv.visitCode()
        // A CHECKCAST to the referenced type puts it in the constant pool (works for an interface too).
        mv.visitInsn(Opcodes.ACONST_NULL)
        mv.visitTypeInsn(Opcodes.CHECKCAST, referenced)
        mv.visitInsn(Opcodes.POP)
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(0, 0)
        mv.visitEnd()
        cw.visitEnd()
        writeBytes(dir, name, cw.toByteArray())
    }

    private fun writeEntryCalling(dir: Path, name: String, owner: String, method: String,
                                  desc: String, opcode: Int) {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null)
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "run", "()V", null, null)
        mv.visitCode()
        mv.visitInsn(Opcodes.ACONST_NULL) // arg for forName(String)
        mv.visitMethodInsn(opcode, owner, method, desc, false)
        mv.visitInsn(Opcodes.POP)
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(0, 0)
        mv.visitEnd()
        cw.visitEnd()
        writeBytes(dir, name, cw.toByteArray())
    }
}
