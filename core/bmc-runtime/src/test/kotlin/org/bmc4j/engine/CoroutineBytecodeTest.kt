package org.bmc4j.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/** Tests [CoroutineBytecode.strip] — classpath routing + the LVT removal itself. */
internal class CoroutineBytecodeTest {

    @Test
    fun passes_through_jars_and_missing_entries_unchanged() {
        val jar = "C:\\some\\lib.jar"
        val missing = "C:\\does\\not\\exist"
        val out = CoroutineBytecode.strip(jar + File.pathSeparator + missing)
        assertEquals(jar + File.pathSeparator + missing, out)
    }

    @Test
    fun mirrors_directories_to_a_new_path(@TempDir dir: Path) {
        Files.write(dir.resolve("note.txt"), "hi".toByteArray())
        val out = CoroutineBytecode.strip(dir.toString())
        assertNotEquals(dir.toString(), out, "a directory entry should be mirrored to a cache copy")
        assertTrue(Files.exists(Path.of(out, "note.txt")), "non-class files are copied verbatim")
    }

    @Test
    fun strips_lvt_from_coroutine_methods_only(@TempDir dir: Path) {
        val classFile = dir.resolve("Demo.class")
        Files.write(classFile, classWithTwoMethods())

        val mirrored = CoroutineBytecode.strip(dir.toString())
        val out = Files.readAllBytes(Path.of(mirrored, "Demo.class"))

        val lvtCounts = countLocalVariables(out)
        assertEquals(0, lvtCounts.getOrDefault("suspendy", 0),
                "coroutine method (Continuation param) must have its LVT stripped")
        assertTrue(lvtCounts.getOrDefault("plain", 0) > 0,
                "ordinary method keeps its LVT so counterexamples keep variable names")
    }

    @Test
    fun strips_lvt_from_a_non_coroutine_method_with_a_duplicate_parameter_slot(@TempDir dir: Path) {
        // A heavily-inlined synthetic like kotlinx-coroutines' `executeUnconfined$default`: NOT named
        // invokeSuspend and NO Continuation parameter, yet a *parameter* slot carries two LVT entries.
        // That is exactly the shape JBMC's create_parameter_names invariant aborts on, so it must be
        // stripped even though the name/descriptor rule does not match it.
        val classFile = dir.resolve("Demo.class")
        Files.write(classFile, classWithDuplicateParamSlot())

        val mirrored = CoroutineBytecode.strip(dir.toString())
        val out = Files.readAllBytes(Path.of(mirrored, "Demo.class"))

        val lvtCounts = countLocalVariables(out)
        assertEquals(0, lvtCounts.getOrDefault("dupParam", 0),
                "a method with >1 LVT entry on a parameter slot must have its whole LVT stripped")
        assertTrue(lvtCounts.getOrDefault("plain", 0) > 0,
                "a method with a clean LVT keeps it so counterexamples keep variable names")
    }

    @Test
    fun keeps_lvt_when_the_duplicate_is_on_a_non_parameter_slot(@TempDir dir: Path) {
        // Duplicate entries on an *interior local* slot (not a parameter) do NOT trip the invariant,
        // so the table must be preserved — only parameter-slot duplicates are the crash trigger.
        val classFile = dir.resolve("Demo.class")
        Files.write(classFile, classWithDuplicateLocalSlot())

        val mirrored = CoroutineBytecode.strip(dir.toString())
        val out = Files.readAllBytes(Path.of(mirrored, "Demo.class"))

        val lvtCounts = countLocalVariables(out)
        assertTrue(lvtCounts.getOrDefault("dupLocal", 0) > 0,
                "a duplicate on a non-parameter (interior local) slot is harmless; keep the LVT")
    }

    companion object {
        /** A class with one ordinary method and one "suspend-like" method (trailing Continuation). */
        private fun classWithTwoMethods(): ByteArray {
            val cw = ClassWriter(0)
            cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "Demo", null, "java/lang/Object", null)
            emit(cw, "plain", "(I)V")
            emit(cw, "suspendy", "(ILkotlin/coroutines/Continuation;)V")
            cw.visitEnd()
            return cw.toByteArray()
        }

        /** A class with a clean ordinary method and one whose parameter slot 0 has two LVT entries. */
        private fun classWithDuplicateParamSlot(): ByteArray {
            val cw = ClassWriter(0)
            cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "Demo", null, "java/lang/Object", null)
            emit(cw, "plain", "(I)V")
            emitDuplicateOnSlot(cw, "dupParam", "(I)V", 0) // slot 0 is the (only) parameter -> param dup
            cw.visitEnd()
            return cw.toByteArray()
        }

        /** A class whose method has two LVT entries on an interior-local slot (not a parameter). */
        private fun classWithDuplicateLocalSlot(): ByteArray {
            val cw = ClassWriter(0)
            cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "Demo", null, "java/lang/Object", null)
            // static (I)V: parameter occupies slot 0; the duplicate is on slot 1, an interior local.
            emitDuplicateOnSlot(cw, "dupLocal", "(I)V", 1)
            cw.visitEnd()
            return cw.toByteArray()
        }

        /** Emit a static method with two distinct LVT entries on the same `slot`. */
        private fun emitDuplicateOnSlot(cw: ClassWriter, name: String, desc: String, slot: Int) {
            val mv = cw.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, name, desc, null, null)
            mv.visitCode()
            val start = Label()
            val mid = Label()
            val end = Label()
            mv.visitLabel(start)
            mv.visitInsn(Opcodes.NOP)
            mv.visitLabel(mid)
            mv.visitInsn(Opcodes.RETURN)
            mv.visitLabel(end)
            // two overlapping live ranges on the SAME slot — the create_parameter_names trigger when slot
            // is in the parameter range.
            mv.visitLocalVariable("a", "I", null, start, mid, slot)
            mv.visitLocalVariable("b", "I", null, mid, end, slot)
            mv.visitMaxs(1, slot + 2)
            mv.visitEnd()
        }

        private fun emit(cw: ClassWriter, name: String, desc: String) {
            val mv = cw.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, name, desc, null, null)
            mv.visitCode()
            val start = Label()
            val end = Label()
            mv.visitLabel(start)
            mv.visitInsn(Opcodes.RETURN)
            mv.visitLabel(end)
            mv.visitLocalVariable("x", "I", null, start, end, 0)   // an LVT entry to (maybe) strip
            mv.visitMaxs(1, 3)
            mv.visitEnd()
        }

        private fun countLocalVariables(clazz: ByteArray): Map<String, Int> {
            val counts = HashMap<String, Int>()
            ClassReader(clazz).accept(object : ClassVisitor(Opcodes.ASM9) {
                override fun visitMethod(a: Int, name: String?, d: String?, s: String?,
                                         e: Array<String>?): MethodVisitor {
                    return object : MethodVisitor(Opcodes.ASM9) {
                        override fun visitLocalVariable(n: String?, d2: String?, s2: String?,
                                                        st: Label?, en: Label?, i: Int) {
                            counts.merge(name!!, 1, Int::plus)
                        }
                    }
                }
            }, 0)
            return counts
        }
    }
}
