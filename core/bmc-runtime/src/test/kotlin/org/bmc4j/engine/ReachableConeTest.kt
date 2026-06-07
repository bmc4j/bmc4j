package org.bmc4j.engine

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Handle
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import java.nio.file.Files
import java.nio.file.Path

/**
 * Unit tests for the reachable-cone walk: the cone reaches what the proof actually depends on
 * (transitively), excludes unrelated classes, and — the soundness contract — **falls back to the whole
 * classpath** whenever it can't bound the dependency set (reflection / method handles, an
 * un-attributable invokedynamic, or a missing entry class). Over-inclusion is fine; under-inclusion
 * (a stale green) never is.
 */
internal class ReachableConeTest {

    @Test
    fun entryReachesDirectAndTransitiveCallees_butNotUnrelated(@TempDir dir: Path) {
        val classes = Files.createDirectory(dir.resolve("classes"))
        // Entry -> A -> B; C is unrelated (nobody references it).
        writeClass(classes, "B", emptyList())
        writeClass(classes, "A", listOf("B"))
        writeClass(classes, "Entry", listOf("A"))
        writeClass(classes, "C", emptyList())

        val cone = ReachableCone.coneClasses("Entry", classes.toString())
        assertTrue(cone.contains("Entry"), "the entry class itself is in the cone")
        assertTrue(cone.contains("A"), "a directly-called class is in the cone")
        assertTrue(cone.contains("B"), "a transitively-called class is in the cone")
        assertFalse(cone.contains("C"), "an unrelated class is NOT in the cone")
    }

    @Test
    fun missingEntryClass_fallsBackToWhole(@TempDir dir: Path) {
        val classes = Files.createDirectory(dir.resolve("classes"))
        writeClass(classes, "A", emptyList())
        val cone = ReachableCone.compute("NotThere", classes.toString())
        assertTrue(cone.whole, "an entry class not on the classpath must fall back to the whole classpath")
    }

    @Test
    fun emptyClasspath_fallsBackToWhole() {
        assertTrue(ReachableCone.compute("Entry", "").whole, "empty classpath -> whole fallback")
        assertTrue(ReachableCone.compute("Entry", null).whole, "null classpath -> whole fallback")
    }

    @Test
    fun reflectionCall_fallsBackToWhole(@TempDir dir: Path) {
        val classes = Files.createDirectory(dir.resolve("classes"))
        // Entry calls Class.forName(String) — an opaque, runtime-resolved edge the walk can't follow.
        writeClassCalling(classes, "Entry", "java/lang/Class", "forName",
                "(Ljava/lang/String;)Ljava/lang/Class;", Opcodes.INVOKESTATIC)
        val cone = ReachableCone.compute("Entry", classes.toString())
        assertTrue(cone.whole, "a reflection call must force the whole-classpath fallback (sound bias)")
    }

    @Test
    fun methodHandleCall_fallsBackToWhole(@TempDir dir: Path) {
        val classes = Files.createDirectory(dir.resolve("classes"))
        writeClassCalling(classes, "Entry", "java/lang/invoke/MethodHandle", "invoke",
                "()Ljava/lang/Object;", Opcodes.INVOKEVIRTUAL)
        val cone = ReachableCone.compute("Entry", classes.toString())
        assertTrue(cone.whole, "a MethodHandle.invoke must force the whole-classpath fallback")
    }

    @Test
    fun unknownInvokedynamic_fallsBackToWhole(@TempDir dir: Path) {
        val classes = Files.createDirectory(dir.resolve("classes"))
        // An invokedynamic whose bootstrap is NOT one of the known desugaring bootstraps — opaque.
        writeClassWithIndy(classes, "Entry",
                Handle(Opcodes.H_INVOKESTATIC, "com/acme/MyBsm", "bootstrap",
                        "(Ljava/lang/invoke/MethodHandles\$Lookup;Ljava/lang/String;" +
                                "Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false))
        val cone = ReachableCone.compute("Entry", classes.toString())
        assertTrue(cone.whole, "an un-attributable invokedynamic must force the whole-classpath fallback")
    }

    @Test
    fun knownLambdaIndy_isBounded_andReachesImpl(@TempDir dir: Path) {
        val classes = Files.createDirectory(dir.resolve("classes"))
        writeClass(classes, "LambdaImplOwner", emptyList())
        // A LambdaMetafactory indy whose implementation handle names LambdaImplOwner.impl — a bounded
        // edge: the walk follows the bsm arg to the impl owner instead of falling back.
        writeClassWithIndy(classes, "Entry",
                Handle(Opcodes.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory",
                        "(Ljava/lang/invoke/MethodHandles\$Lookup;Ljava/lang/String;" +
                                "Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;" +
                                "Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)" +
                                "Ljava/lang/invoke/CallSite;", false),
                implOwner = "LambdaImplOwner")
        val cone = ReachableCone.compute("Entry", classes.toString())
        assertFalse(cone.whole, "a known LambdaMetafactory indy is bounded, not a fallback")
        assertTrue(cone.classes!!.contains("LambdaImplOwner"),
                "the lambda implementation method's owner is in the cone via the bsm arg")
    }

    @Test
    fun fieldTypeReference_isInCone(@TempDir dir: Path) {
        val classes = Files.createDirectory(dir.resolve("classes"))
        writeClass(classes, "FieldType", emptyList())
        writeClassWithField(classes, "Entry", "f", "LFieldType;")
        val cone = ReachableCone.coneClasses("Entry", classes.toString())
        assertTrue(cone.contains("FieldType"), "a field's declared type is reached via the constant pool")
    }

    // --- helpers ---------------------------------------------------------------

    private fun writeBytes(dir: Path, internalName: String, bytes: ByteArray) {
        val f = dir.resolve("$internalName.class")
        Files.createDirectories(f.parent)
        Files.write(f, bytes)
    }

    /** A class `name` with a `run()` method that does `new` of each owner in [callees] (so each is a
     *  type reference reachable from the entry). */
    private fun writeClass(dir: Path, name: String, callees: List<String>) {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null)
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "run", "()V", null, null)
        mv.visitCode()
        for (c in callees) {
            mv.visitTypeInsn(Opcodes.NEW, c)
            mv.visitInsn(Opcodes.POP)
        }
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(0, 0)
        mv.visitEnd()
        cw.visitEnd()
        writeBytes(dir, name, cw.toByteArray())
    }

    private fun writeClassCalling(dir: Path, name: String, owner: String, method: String,
                                  desc: String, opcode: Int) {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null)
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "run", "()V", null, null)
        mv.visitCode()
        if (opcode != Opcodes.INVOKESTATIC) {
            mv.visitInsn(Opcodes.ACONST_NULL) // a (null) receiver for a virtual call we never execute
        }
        if (opcode == Opcodes.INVOKESTATIC) {
            mv.visitInsn(Opcodes.ACONST_NULL) // arg for forName(String)
        }
        mv.visitMethodInsn(opcode, owner, method, desc, false)
        if (Type.getReturnType(desc).sort != Type.VOID) {
            mv.visitInsn(Opcodes.POP)
        }
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(0, 0)
        mv.visitEnd()
        cw.visitEnd()
        writeBytes(dir, name, cw.toByteArray())
    }

    private fun writeClassWithField(dir: Path, name: String, fieldName: String, fieldDesc: String) {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null)
        cw.visitField(Opcodes.ACC_PRIVATE, fieldName, fieldDesc, null, null).visitEnd()
        cw.visitEnd()
        writeBytes(dir, name, cw.toByteArray())
    }

    private fun writeClassWithIndy(dir: Path, name: String, bsm: Handle, implOwner: String? = null) {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null)
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "run", "()V", null, null)
        mv.visitCode()
        val bsmArgs: Array<Any> = if (implOwner != null) {
            arrayOf(
                    Type.getType("()V"),
                    Handle(Opcodes.H_INVOKESTATIC, implOwner, "impl", "()V", false),
                    Type.getType("()V"))
        } else {
            arrayOf()
        }
        mv.visitInvokeDynamicInsn("apply", "()Ljava/lang/Runnable;", bsm, *bsmArgs)
        mv.visitInsn(Opcodes.POP)
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(0, 0)
        mv.visitEnd()
        cw.visitEnd()
        writeBytes(dir, name, cw.toByteArray())
    }
}
