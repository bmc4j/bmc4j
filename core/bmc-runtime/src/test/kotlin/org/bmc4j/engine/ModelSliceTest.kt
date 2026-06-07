package org.bmc4j.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Unit tests for per-proof model slicing: the analysis classpath the engine sees carries only the
 * classes in the proof's reachable cone (class level), unrelated model growth is pruned, and the two
 * hard rules hold — a fallback (unbounded) proof is sliced AWAY (returned unchanged), and jar entries
 * always pass through untouched. The end-to-end member-named-UNKNOWN soundness floor (a reached but
 * sliced-away class never silently passes) is pinned by the live `ModelSliceSoundnessProbe` against the
 * real engine; here we pin the pure slicing mechanism.
 */
internal class ModelSliceTest {

    @Test
    fun slicedDir_keepsConeClasses_andEntry_dropsUnrelated(@TempDir dir: Path) {
        val classes = Files.createDirectory(dir.resolve("classes"))
        // Entry -> Dep; Unrelated is grown model surface the proof never touches.
        writeClass(classes, "pkg/Dep", emptyList())
        writeClass(classes, "pkg/Entry", listOf("pkg/Dep"))
        writeClass(classes, "pkg/Unrelated", emptyList())

        val sliced = ModelSlice.sliceForCone(classes.toString(), "pkg.Entry", classes.toString())
        val names = classNamesIn(sliced)

        assertTrue(names.contains("pkg/Entry"), "the entry class is on the sliced classpath")
        assertTrue(names.contains("pkg/Dep"), "a reached class is on the sliced classpath")
        assertFalse(names.contains("pkg/Unrelated"),
                "an out-of-cone class is pruned from the sliced classpath")
    }

    @Test
    fun fallbackProof_isNotSliced_returnsClasspathUnchanged(@TempDir dir: Path) {
        val classes = Files.createDirectory(dir.resolve("classes"))
        writeClass(classes, "pkg/Other", emptyList())
        // Entry class is NOT on the classpath -> cone can't be bounded -> whole-classpath fallback ->
        // the slice must return the classpath UNCHANGED (a fallback proof keeps the whole surface).
        val cp = classes.toString()
        val sliced = ModelSlice.sliceForCone(cp, "pkg.NotThere", cp)
        assertEquals(cp, sliced, "a proof whose cone fell back to whole-classpath mode is not sliced")
    }

    @Test
    fun reflectionProof_isNotSliced_returnsClasspathUnchanged(@TempDir dir: Path) {
        val classes = Files.createDirectory(dir.resolve("classes"))
        // Entry reaches Class.forName -> an opaque dispatch the cone can't bound -> whole fallback.
        writeClassCallingForName(classes, "pkg/Entry")
        val cp = classes.toString()
        val sliced = ModelSlice.sliceForCone(cp, "pkg.Entry", cp)
        assertEquals(cp, sliced, "a reflection-reaching proof falls back and is not sliced")
    }

    @Test
    fun jarEntries_passThroughUntouched(@TempDir dir: Path) {
        val classes = Files.createDirectory(dir.resolve("classes"))
        writeClass(classes, "pkg/Dep", emptyList())
        writeClass(classes, "pkg/Entry", listOf("pkg/Dep"))
        // A jar carries the shared model/JDK hierarchy — it must pass through verbatim, never pruned.
        val jar = dir.resolve("models.jar")
        writeJar(jar, "org/bmc4j/models/M.class", classBytes("org/bmc4j/models/M"))

        val cp = classes.toString() + File.pathSeparator + jar.toString()
        val sliced = ModelSlice.sliceForCone(cp, "pkg.Entry", cp)

        val entries = sliced.split(File.pathSeparator)
        assertTrue(entries.contains(jar.toString()),
                "the jar entry is passed through unchanged (its path is preserved)")
        // The directory entry was rewritten to a cache dir (not the original path).
        assertFalse(entries.contains(classes.toString()),
                "the directory entry was sliced to a mirror dir, not left as the original")
    }

    @Test
    fun sliceTo_withDeficientKeep_dropsAReachedClass(@TempDir dir: Path) {
        // The mechanism the soundness probe leans on: sliceTo honours an explicit keep set even when it
        // OMITS a class the proof reaches. (Normal runs can't hit this — the cone over-approximates —
        // but the engine-side honesty floor must hold if it ever did; the live probe asserts that.)
        val classes = Files.createDirectory(dir.resolve("classes"))
        writeClass(classes, "pkg/Dep", emptyList())
        writeClass(classes, "pkg/Entry", listOf("pkg/Dep"))

        val sliced = ModelSlice.sliceTo(classes.toString(), setOf("pkg/Entry")) // deliberately omit Dep
        val names = classNamesIn(sliced)
        assertTrue(names.contains("pkg/Entry"))
        assertFalse(names.contains("pkg/Dep"),
                "sliceTo prunes any class not in the explicit keep set (the deficient-cone case)")
    }

    // --- helpers ---------------------------------------------------------------

    /** Every internal class name reachable across the sliced classpath's directory entries. */
    private fun classNamesIn(classpath: String): Set<String> {
        val out = HashSet<String>()
        for (entry in classpath.split(File.pathSeparator).filter { it.isNotEmpty() }) {
            val p = Path.of(entry)
            if (!Files.isDirectory(p)) {
                continue
            }
            Files.walk(p).use { walk ->
                for (c in Iterable { walk.iterator() }) {
                    if (Files.isRegularFile(c) && c.fileName.toString().endsWith(".class")) {
                        out.add(ClassReader(Files.readAllBytes(c)).className)
                    }
                }
            }
        }
        return out
    }

    private fun classBytes(internalName: String, callees: List<String> = emptyList()): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, internalName, null, "java/lang/Object", null)
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
        return cw.toByteArray()
    }

    private fun writeClass(dir: Path, internalName: String, callees: List<String>) {
        val f = dir.resolve("$internalName.class")
        Files.createDirectories(f.parent)
        Files.write(f, classBytes(internalName, callees))
    }

    private fun writeClassCallingForName(dir: Path, internalName: String) {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, internalName, null, "java/lang/Object", null)
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "run", "()V", null, null)
        mv.visitCode()
        mv.visitInsn(Opcodes.ACONST_NULL)
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Class", "forName",
                "(Ljava/lang/String;)Ljava/lang/Class;", false)
        mv.visitInsn(Opcodes.POP)
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(0, 0)
        mv.visitEnd()
        cw.visitEnd()
        val f = dir.resolve("$internalName.class")
        Files.createDirectories(f.parent)
        Files.write(f, cw.toByteArray())
    }

    private fun writeJar(jar: Path, entryName: String, content: ByteArray) {
        ZipOutputStream(Files.newOutputStream(jar)).use { zout ->
            zout.putNextEntry(ZipEntry(entryName))
            zout.write(content)
            zout.closeEntry()
        }
    }
}
