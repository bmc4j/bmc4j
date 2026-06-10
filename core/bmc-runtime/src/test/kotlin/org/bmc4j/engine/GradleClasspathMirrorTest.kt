package org.bmc4j.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Handle
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.ArrayList

/**
 * The Gradle mirror task and its runtime consumer. [GradleClasspathMirror.mirror] pre-applies the six
 * environment-independent desugar passes into a Gradle-owned dir + manifest; [GradleClasspathMirror.substitute]
 * swaps the original entries for the mirrored ones in the test JVM. These tests pin the round trip
 * (mirrored bytecode is really desugared), the manifest's relocatability, and the soundness gates
 * (identity mismatch / missing mirror fall back to the original classpath, never serve a stale rewrite).
 */
internal class GradleClasspathMirrorTest {

    @Test
    fun mirror_then_substitute_yields_desugared_bytecode(@TempDir tmp: Path) {
        val srcDir = tmp.resolve("classes")
        Files.createDirectories(srcDir)
        Files.write(srcDir.resolve("Sample.class"), sampleClass())
        val original = srcDir.toString()

        val out = tmp.resolve("mirror")
        GradleClasspathMirror.mirror(original, out)

        // The manifest exists and is stamped with this runtime's identity.
        val manifest = out.resolve("manifest.txt")
        assertTrue(Files.isRegularFile(manifest), "mirror writes a manifest")
        assertEquals("bmc4j-mirror-identity " + Bmc4jVersion.IDENTITY,
                Files.readAllLines(manifest, StandardCharsets.UTF_8)[0],
                "manifest header carries the runtime semantics identity")

        // substitute swaps the original entry for the mirrored one.
        val substituted = GradleClasspathMirror.substitute(original, out)
        assertNotEquals(original, substituted, "substitute must point at the mirrored entry")
        val mirroredClass = Path.of(substituted).resolve("Sample.class")
        assertTrue(Files.isRegularFile(mirroredClass), "mirrored Sample.class exists")

        // The six passes ran: String.equals redirected to BmcStrings and the concat indy desugared.
        val calls = methodCalls(Files.readAllBytes(mirroredClass))
        assertTrue(calls.contains(
                "INVOKESTATIC org/bmc4j/engine/BmcStrings.equals(Ljava/lang/String;Ljava/lang/Object;)Z"),
                "mirrored class must carry the String-shim redirect: $calls")
        assertFalse(invokeDynamics(Files.readAllBytes(mirroredClass)).any { it.contains("StringConcatFactory") },
                "the concat indy must be desugared in the mirrored class")
    }

    @Test
    fun manifest_paths_are_relative_to_the_output_dir(@TempDir tmp: Path) {
        val srcDir = tmp.resolve("classes")
        Files.createDirectories(srcDir)
        Files.write(srcDir.resolve("Sample.class"), sampleClass())
        val out = tmp.resolve("mirror")
        GradleClasspathMirror.mirror(srcDir.toString(), out)

        // Every mapped target is RELATIVE (no drive letter / leading slash) so the cache is relocatable.
        Files.readAllLines(out.resolve("manifest.txt"), StandardCharsets.UTF_8)
                .drop(1)
                .filter { it.isNotEmpty() }
                .forEach { line ->
                    val rel = line.substringAfter('\t')
                    assertFalse(Path.of(rel).isAbsolute, "manifest target must be relative: $rel")
                }
    }

    @Test
    fun substitute_falls_back_on_identity_mismatch(@TempDir tmp: Path) {
        val srcDir = tmp.resolve("classes")
        Files.createDirectories(srcDir)
        Files.write(srcDir.resolve("Sample.class"), sampleClass())
        val out = tmp.resolve("mirror")
        GradleClasspathMirror.mirror(srcDir.toString(), out)

        // Corrupt the identity header to a different runtime semantics.
        val manifest = out.resolve("manifest.txt")
        val lines = Files.readAllLines(manifest, StandardCharsets.UTF_8).toMutableList()
        lines[0] = "bmc4j-mirror-identity 0.0.0+rX-stale"
        Files.write(manifest, lines.joinToString("\n").toByteArray(StandardCharsets.UTF_8))

        assertEquals(srcDir.toString(), GradleClasspathMirror.substitute(srcDir.toString(), out),
                "a mirror from a different identity must NOT be trusted (fall back to the original)")
    }

    @Test
    fun substitute_falls_back_when_mirror_is_absent(@TempDir tmp: Path) {
        val original = tmp.resolve("classes").toString()
        val out = tmp.resolve("does-not-exist")
        assertEquals(original, GradleClasspathMirror.substitute(original, out),
                "a missing mirror dir must fall back to the original classpath")
    }

    @Test
    fun unmapped_entries_pass_through_substitution(@TempDir tmp: Path) {
        val srcDir = tmp.resolve("classes")
        Files.createDirectories(srcDir)
        Files.write(srcDir.resolve("Sample.class"), sampleClass())
        val out = tmp.resolve("mirror")
        GradleClasspathMirror.mirror(srcDir.toString(), out)

        // Substitute a classpath that includes an entry the mirror never saw: it must pass through
        // unchanged (the in-JVM passes still rewrite it), while the mapped entry is substituted.
        val foreign = tmp.resolve("foreign").toString()
        val input = srcDir.toString() + File.pathSeparator + foreign
        val substituted = GradleClasspathMirror.substitute(input, out).split(File.pathSeparator)
        assertEquals(2, substituted.size, "entry count preserved")
        assertNotEquals(srcDir.toString(), substituted[0], "the mapped entry is substituted")
        assertEquals(foreign, substituted[1], "the unmapped entry passes through unchanged")
    }

    companion object {
        private fun sampleClass(): ByteArray {
            val cw = org.objectweb.asm.ClassWriter(
                    org.objectweb.asm.ClassWriter.COMPUTE_MAXS or org.objectweb.asm.ClassWriter.COMPUTE_FRAMES)
            cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "Sample", null, "java/lang/Object", null)
            val eq = cw.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "eq",
                    "(Ljava/lang/String;Ljava/lang/String;)Z", null, null)
            eq.visitCode()
            eq.visitVarInsn(Opcodes.ALOAD, 0)
            eq.visitVarInsn(Opcodes.ALOAD, 1)
            eq.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "equals",
                    "(Ljava/lang/Object;)Z", false)
            eq.visitInsn(Opcodes.IRETURN)
            eq.visitMaxs(0, 0)
            eq.visitEnd()
            val wrap = cw.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "wrap",
                    "(Ljava/lang/String;)Ljava/lang/String;", null, null)
            wrap.visitCode()
            wrap.visitVarInsn(Opcodes.ALOAD, 0)
            val bsm = Handle(Opcodes.H_INVOKESTATIC, "java/lang/invoke/StringConcatFactory",
                    "makeConcatWithConstants",
                    "(Ljava/lang/invoke/MethodHandles\$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;" +
                            "Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;", false)
            wrap.visitInvokeDynamicInsn("makeConcatWithConstants",
                    "(Ljava/lang/String;)Ljava/lang/String;", bsm, "[]")
            wrap.visitInsn(Opcodes.ARETURN)
            wrap.visitMaxs(0, 0)
            wrap.visitEnd()
            cw.visitEnd()
            return cw.toByteArray()
        }

        private fun methodCalls(clazz: ByteArray): List<String> {
            val calls = ArrayList<String>()
            ClassReader(clazz).accept(object : ClassVisitor(Opcodes.ASM9) {
                override fun visitMethod(a: Int, n: String?, d: String?, s: String?,
                                         e: Array<String>?): MethodVisitor =
                        object : MethodVisitor(Opcodes.ASM9) {
                            override fun visitMethodInsn(op: Int, owner: String?, name: String?,
                                                         desc: String?, itf: Boolean) {
                                val kind = if (op == Opcodes.INVOKESTATIC) "INVOKESTATIC" else "INVOKE"
                                calls.add("$kind $owner.$name$desc")
                            }
                        }
            }, 0)
            return calls
        }

        private fun invokeDynamics(clazz: ByteArray): List<String> {
            val out = ArrayList<String>()
            ClassReader(clazz).accept(object : ClassVisitor(Opcodes.ASM9) {
                override fun visitMethod(a: Int, n: String?, d: String?, s: String?,
                                         e: Array<String>?): MethodVisitor =
                        object : MethodVisitor(Opcodes.ASM9) {
                            override fun visitInvokeDynamicInsn(name: String?, desc: String?,
                                                                bsm: Handle?, vararg args: Any?) {
                                out.add(bsm?.owner + "." + name)
                            }
                        }
            }, 0)
            return out
        }
    }
}
