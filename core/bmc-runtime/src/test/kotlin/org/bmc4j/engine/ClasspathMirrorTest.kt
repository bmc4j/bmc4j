package org.bmc4j.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Handle
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.ArrayList
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * The rewrite passes must mirror JAR classpath entries, not only directory entries. The
 * in-repo test bed runs on `includeBuild` class dirs, but a published consumer gets
 * `bmc-models` and third-party libs as jars — if jars passed through unrewritten, the shipped
 * product would silently lose every String shim / concat / record / typeSwitch / Math desugar.
 *
 * These tests drive the real [StringBytecode.rewrite] (which now delegates to
 * [ClasspathMirror]) over a synthesized classpath, so they cover both the shared mirror engine
 * and that StringBytecode wires into it. A directory regression and the content-hash cache behaviour
 * are pinned alongside.
 */
internal class ClasspathMirrorTest {

    @Test
    fun jar_entry_is_rewritten_in_place(@TempDir tmp: Path) {
        val jar = writeJar(tmp, "models.jar", sampleClass())
        val rewritten = StringBytecode.rewrite(jar.toString())

        // The classpath entry must now point at a DIFFERENT jar (the rewritten mirror), not the input.
        assertNotEquals(jar.toString(), rewritten, "jar entry must be mirrored, not passed through")
        val mirroredJar = Path.of(rewritten)
        assertTrue(mirroredJar.fileName.toString().endsWith(".jar"), "mirror is a jar")
        assertTrue(Files.isRegularFile(mirroredJar), "mirrored jar exists")

        val calls = methodCallsInJar(mirroredJar, "Sample.class")
        // String.equals -> BmcStrings.equals
        assertTrue(calls.contains("INVOKESTATIC org/bmc4j/engine/BmcStrings.equals(Ljava/lang/String;Ljava/lang/Object;)Z"),
                "String.equals inside the jar must be redirected to BmcStrings: $calls")
        assertFalse(calls.any { it.contains("java/lang/String.equals") },
                "original String.equals must be gone from the jar's class")
        // concat indy -> generated helper (no StringConcatFactory indy left)
        assertFalse(invokeDynamicsInJar(mirroredJar, "Sample.class").any { it.contains("StringConcatFactory") },
                "concat indy inside the jar must be desugared")

        // The non-class resource is copied verbatim.
        ZipFile(mirroredJar.toFile()).use { zf ->
            assertTrue(zf.getEntry("META-INF/MANIFEST.MF") != null, "manifest copied into mirror")
        }
    }

    @Test
    fun directory_entry_still_works(@TempDir tmp: Path) {
        // Regression: a class DIRECTORY entry is mirrored exactly as before.
        val dir = tmp.resolve("classes")
        Files.createDirectories(dir)
        Files.write(dir.resolve("Sample.class"), sampleClass())

        val rewritten = StringBytecode.rewrite(dir.toString())
        assertNotEquals(dir.toString(), rewritten, "directory entry is mirrored")
        val mirroredClass = Path.of(rewritten).resolve("Sample.class")
        assertTrue(Files.isRegularFile(mirroredClass), "mirrored class exists in the dir")

        val calls = methodCalls(Files.readAllBytes(mirroredClass))
        assertTrue(calls.contains("INVOKESTATIC org/bmc4j/engine/BmcStrings.equals(Ljava/lang/String;Ljava/lang/Object;)Z"),
                "directory class still gets the String-shim redirect: $calls")
    }

    @Test
    fun jar_mirror_is_content_hash_cached_no_rewrite_on_rerun(@TempDir tmp: Path) {
        // Drive the shared mirror directly with a transform that COUNTS how many class files it rewrites,
        // so we can prove a second run over the same (unchanged) jar does no re-rewrite (cache hit).
        val jar = writeJar(tmp, "cached.jar", sampleClass())
        val transforms = intArrayOf(0)
        val counting = ClasspathMirror.ClassTransform { bytes ->
            transforms[0]++
            ClasspathMirror.Transformed(StringBytecode.rewriteClass(bytes))
        }

        val first = ClasspathMirror.mirror(jar.toString(), "test-cache-" + unique(), counting)
        val afterFirst = transforms[0]
        assertTrue(afterFirst >= 1, "first run rewrites the class")

        // Same content -> same cache key -> no re-rewrite.
        val second = ClasspathMirror.mirror(jar.toString(), cacheNameOf(first), counting)
        assertEquals(first, second, "same jar content resolves to the same mirrored jar")
        assertEquals(afterFirst, transforms[0], "second run must be a cache hit: no class re-rewritten")
    }

    @Test
    fun published_jar_entry_fast_path_does_zero_writes(@TempDir tmp: Path) {
        // A content-hashed jar is immutable once published. When the dest jar AND its .done marker
        // already exist, the mirror must take the fast path: return the existing path and perform
        // ZERO writes/re-opens-for-write on it. (On Windows, re-opening an already-published jar that a
        // concurrent reader holds open for writing is exactly the sharing-violation this fixes.)
        // We pre-populate dest + .done, mark dest READ-ONLY, then assert the call succeeds, returns
        // dest unchanged, and never rewrites the class (transform count stays 0).
        val jar = writeJar(tmp, "published.jar", sampleClass())
        val cacheName = "test-fastpath-" + unique()
        val root = Path.of(System.getProperty("user.home"), ".cache", "bmc4j", cacheName)
        Files.createDirectories(root)
        val hash = ClasspathMirror.contentHash(jar)
        val dest = root.resolve("$hash.jar")
        val done = root.resolve("$hash.done")
        // Sentinel payload distinct from any real rewrite, so any rewrite would change size/content.
        val sentinel = "SENTINEL-PUBLISHED-JAR".toByteArray(StandardCharsets.UTF_8)
        Files.write(dest, sentinel)
        Files.write(done, ByteArray(0))
        val sizeBefore = Files.size(dest)
        val mtimeBefore = Files.getLastModifiedTime(dest).toMillis()
        // Make dest read-only: if the fast path tried to open/replace it, the write would FAIL.
        assertTrue(dest.toFile().setReadOnly(), "set dest read-only for the no-write assertion")

        val transforms = intArrayOf(0)
        val counting = ClasspathMirror.ClassTransform { bytes ->
            transforms[0]++
            ClasspathMirror.Transformed(StringBytecode.rewriteClass(bytes))
        }
        val result: String
        try {
            result = ClasspathMirror.mirror(jar.toString(), cacheName, counting)
        } finally {
            dest.toFile().setWritable(true) // let @TempDir / cache cleanup remove it
        }

        assertEquals(dest.toString(), result, "fast path must return the already-published jar")
        assertEquals(0, transforms[0], "fast path must do NO rewrite (the entry is already published)")
        assertEquals(sizeBefore, Files.size(dest), "published jar must be untouched (size unchanged)")
        assertEquals(mtimeBefore, Files.getLastModifiedTime(dest).toMillis(),
                "published jar must be untouched (mtime unchanged)")
        assertEquals(String(sentinel, StandardCharsets.UTF_8),
                Files.readString(dest, StandardCharsets.UTF_8),
                "published jar bytes must be byte-for-byte unchanged (zero writes)")
    }

    @Test
    fun jar_missing_done_marker_is_redone(@TempDir tmp: Path) {
        // A half-written jar (dest present but NO .done marker, e.g. from a crashed/concurrent run)
        // must be REDONE, not mistaken for complete: the mirror rewrites and publishes the marker.
        val jar = writeJar(tmp, "halfwritten.jar", sampleClass())
        val cacheName = "test-halfwritten-" + unique()
        val root = Path.of(System.getProperty("user.home"), ".cache", "bmc4j", cacheName)
        Files.createDirectories(root)
        val hash = ClasspathMirror.contentHash(jar)
        val dest = root.resolve("$hash.jar")
        val done = root.resolve("$hash.done")
        // Pre-populate a bogus partial jar with NO .done marker.
        Files.write(dest, "PARTIAL-NO-DONE".toByteArray(StandardCharsets.UTF_8))
        assertFalse(Files.exists(done), "precondition: no .done marker yet")

        val transforms = intArrayOf(0)
        val counting = ClasspathMirror.ClassTransform { bytes ->
            transforms[0]++
            ClasspathMirror.Transformed(StringBytecode.rewriteClass(bytes))
        }
        val result = ClasspathMirror.mirror(jar.toString(), cacheName, counting)

        assertEquals(dest.toString(), result, "redo resolves to the same content-hashed dest")
        assertTrue(transforms[0] >= 1, "missing .done must trigger a redo (the class is rewritten)")
        assertTrue(Files.exists(done), "redo publishes the .done marker")
        // The dest is now a real rewritten jar (the desugar reached the class), not the partial.
        val calls = methodCallsInJar(dest, "Sample.class")
        assertTrue(calls.contains(
                "INVOKESTATIC org/bmc4j/engine/BmcStrings.equals(Ljava/lang/String;Ljava/lang/Object;)Z"),
                "redone jar must be a real rewrite (String.equals redirected): $calls")
    }

    @Test
    fun changed_jar_content_gets_a_fresh_mirror(@TempDir tmp: Path) {
        // Content-hash keying: a different jar content must NOT reuse the old mirror.
        val cacheName = "test-change-" + unique()
        val jar = writeJar(tmp, "v.jar", sampleClass())
        val a = ClasspathMirror.mirror(jar.toString(), cacheName,
                ClasspathMirror.ClassTransform { b -> ClasspathMirror.Transformed(StringBytecode.rewriteClass(b)) })

        // Rewrite the SAME path with different bytes (an extra resource changes the content hash).
        ZipOutputStream(Files.newOutputStream(jar)).use { zos ->
            zos.putNextEntry(ZipEntry("Sample.class"))
            zos.write(sampleClass())
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("extra.txt"))
            zos.write(byteArrayOf(1, 2, 3))
            zos.closeEntry()
        }
        val b = ClasspathMirror.mirror(jar.toString(), cacheName,
                ClasspathMirror.ClassTransform { t -> ClasspathMirror.Transformed(StringBytecode.rewriteClass(t)) })
        assertNotEquals(a, b, "changed jar content must map to a new mirror jar (hash-keyed)")
    }

    @Test
    fun unreadable_jar_fails_LOUD_not_open(@TempDir tmp: Path) {
        // A mirror failure must NOT silently fall back to the unrewritten entry (that would
        // analyse unsound String/indy classes as "verified"). It throws; the engine-error handling
        // turns that into UNKNOWN. Here we assert the throw + an actionable message naming the entry.
        val bad = tmp.resolve("corrupt.jar")
        Files.write(bad, byteArrayOf(0, 1, 2, 3, 4)) // not a valid zip
        val ex = assertThrows(RuntimeException::class.java,
                { ClasspathMirror.mirror(bad.toString(), "test-faillloud-" + unique(),
                        ClasspathMirror.ClassTransform { b -> ClasspathMirror.Transformed(StringBytecode.rewriteClass(b)) }) },
                "a corrupt jar must fail LOUD (throw), never pass through unrewritten")
        assertTrue(ex.message != null && ex.message!!.contains(bad.toString()),
                "the failure message must name the entry that couldn't be mirrored: " + ex.message)
    }

    @Test
    fun simulated_io_failure_mirroring_a_dir_fails_LOUD(@TempDir tmp: Path) {
        // A transform that blows up (a stand-in for any IO/RuntimeException while mirroring)
        // must NOT be swallowed into a silent pass-through — it must throw so the proof goes UNKNOWN.
        val dir = tmp.resolve("classes")
        Files.createDirectories(dir)
        Files.write(dir.resolve("Sample.class"), sampleClass())
        val boom = ClasspathMirror.ClassTransform { _ ->
            throw RuntimeException("simulated mirror IO failure")
        }
        val ex = assertThrows(RuntimeException::class.java,
                { ClasspathMirror.mirror(dir.toString(), "test-dirboom-" + unique(), boom) },
                "a mirror failure on a dir entry must fail LOUD")
        assertTrue(ex.message != null && ex.message!!.contains(dir.toString()),
                "the failure message must name the dir entry: " + ex.message)
    }

    @Test
    fun distinct_dir_paths_cannot_share_a_dest(@TempDir tmp: Path) {
        // The dir mirror is keyed by a FULL content hash, not a 32-bit String.hashCode
        // of the path — two distinct dirs must never collide into one mirror and mix their classes.
        // Two dirs with DIFFERENT content must resolve to different dests.
        val a = tmp.resolve("a")
        val b = tmp.resolve("b")
        Files.createDirectories(a)
        Files.createDirectories(b)
        Files.write(a.resolve("A.class"), sampleClass())
        // give b a distinct entry so its content hash differs from a's
        Files.write(b.resolve("A.class"), sampleClass())
        Files.write(b.resolve("extra.txt"), byteArrayOf(9, 9, 9))
        val t = ClasspathMirror.ClassTransform { bytes ->
            ClasspathMirror.Transformed(StringBytecode.rewriteClass(bytes))
        }
        val cacheName = "test-distinct-" + unique()
        val ma = ClasspathMirror.mirror(a.toString(), cacheName, t)
        val mb = ClasspathMirror.mirror(b.toString(), cacheName, t)
        assertNotEquals(ma, mb, "two distinct dir contents must map to different mirror dirs (full hash)")
    }

    @Test
    fun deleted_class_is_absent_from_the_next_dir_mirror(@TempDir tmp: Path) {
        // Staleness: a class present in mirror v1, then deleted in the source, must be
        // ABSENT from the v2 mirror — never linger as a phantom class on the analysis classpath.
        val dir = tmp.resolve("classes")
        Files.createDirectories(dir)
        Files.write(dir.resolve("Sample.class"), sampleClass())
        Files.write(dir.resolve("Gone.class"), sampleClass())
        val t = ClasspathMirror.ClassTransform { bytes ->
            ClasspathMirror.Transformed(StringBytecode.rewriteClass(bytes))
        }
        val cacheName = "test-stale-" + unique()

        val v1 = ClasspathMirror.mirror(dir.toString(), cacheName, t)
        assertTrue(Files.isRegularFile(Path.of(v1).resolve("Gone.class")), "v1 mirror has Gone.class")

        // Delete a class from the source and re-mirror; content changed -> fresh content-hashed dest.
        Files.delete(dir.resolve("Gone.class"))
        val v2 = ClasspathMirror.mirror(dir.toString(), cacheName, t)
        assertNotEquals(v1, v2, "changed dir content must map to a new mirror (content-hash keyed)")
        assertTrue(Files.isRegularFile(Path.of(v2).resolve("Sample.class")), "v2 keeps the surviving class")
        assertFalse(Files.isRegularFile(Path.of(v2).resolve("Gone.class")),
                "a deleted source class must be ABSENT from the next mirror (no phantom)")
    }

    @Test
    fun dir_mirror_is_content_hash_cached_no_rewrite_on_rerun(@TempDir tmp: Path) {
        // The dir branch is content-hash cached with a .done marker, like jars: a second run over the
        // same (unchanged) dir is a cache hit and re-rewrites nothing.
        val dir = tmp.resolve("classes")
        Files.createDirectories(dir)
        Files.write(dir.resolve("Sample.class"), sampleClass())
        val transforms = intArrayOf(0)
        val counting = ClasspathMirror.ClassTransform { bytes ->
            transforms[0]++
            ClasspathMirror.Transformed(StringBytecode.rewriteClass(bytes))
        }
        val cacheName = "test-dircache-" + unique()
        val first = ClasspathMirror.mirror(dir.toString(), cacheName, counting)
        val afterFirst = transforms[0]
        assertTrue(afterFirst >= 1, "first run rewrites the class")
        val second = ClasspathMirror.mirror(dir.toString(), cacheName, counting)
        assertEquals(first, second, "same dir content resolves to the same mirror dir")
        assertEquals(afterFirst, transforms[0], "second run must be a cache hit: no class re-rewritten")
    }

    @Test
    fun cache_lives_under_dot_cache_bmc4j(@TempDir tmp: Path) {
        // The rewrite mirror caches are unified under ~/.cache/bmc4j/ (not the legacy dir).
        val dir = tmp.resolve("classes")
        Files.createDirectories(dir)
        Files.write(dir.resolve("Sample.class"), sampleClass())
        val mirrored = ClasspathMirror.mirror(dir.toString(), "test-cachepath-" + unique(),
                ClasspathMirror.ClassTransform { b -> ClasspathMirror.Transformed(StringBytecode.rewriteClass(b)) })
        val expectedRoot = Path.of(System.getProperty("user.home"), ".cache", "bmc4j")
        assertTrue(Path.of(mirrored).toAbsolutePath().startsWith(expectedRoot.toAbsolutePath()),
                "mirror cache must live under ~/.cache/bmc4j/: $mirrored")
    }

    companion object {
        /** A class `Sample` with: a `String.equals` call (String-shim redirect) and a
         *  `makeConcatWithConstants` indy (concat desugar) — the two transforms the jar mirror must reach
         *  inside a jar. */
        private fun sampleClass(): ByteArray {
            val cw = ClassWriter(ClassWriter.COMPUTE_MAXS or ClassWriter.COMPUTE_FRAMES)
            cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "Sample", null, "java/lang/Object", null)
            // boolean eq(String a, String b) { return a.equals(b); }
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
            // String wrap(String s) { return "[" + s + "]"; }  via StringConcatFactory indy
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

        private fun writeJar(dir: Path, name: String, sampleClassBytes: ByteArray): Path {
            val jar = dir.resolve(name)
            ZipOutputStream(Files.newOutputStream(jar)).use { zos ->
                // a non-class resource, to prove it is copied verbatim
                zos.putNextEntry(ZipEntry("META-INF/MANIFEST.MF"))
                zos.write("Manifest-Version: 1.0\n".toByteArray(StandardCharsets.UTF_8))
                zos.closeEntry()
                zos.putNextEntry(ZipEntry("Sample.class"))
                zos.write(sampleClassBytes)
                zos.closeEntry()
            }
            return jar
        }

        // ---- helpers ----

        private fun unique(): String =
                java.lang.Long.toHexString(System.nanoTime()) + "-" +
                        Integer.toHexString(System.identityHashCode(Any()))

        /** Recover the cache subdir name from a mirrored jar path (its parent dir's name). */
        private fun cacheNameOf(mirroredJarPath: String): String {
            val p = Path.of(mirroredJarPath)
            return p.parent.fileName.toString()
        }

        private fun methodCallsInJar(jar: Path, entry: String): List<String> {
            ZipFile(jar.toFile()).use { zf ->
                val e = zf.getEntry(entry)
                zf.getInputStream(e).use { input ->
                    return methodCalls(input.readAllBytes())
                }
            }
        }

        private fun invokeDynamicsInJar(jar: Path, entry: String): List<String> {
            val out = ArrayList<String>()
            val bytes: ByteArray
            ZipFile(jar.toFile()).use { zf ->
                zf.getInputStream(zf.getEntry(entry)).use { input ->
                    bytes = input.readAllBytes()
                }
            }
            ClassReader(bytes).accept(object : ClassVisitor(Opcodes.ASM9) {
                override fun visitMethod(a: Int, n: String?, d: String?, s: String?,
                                         ex: Array<String>?): MethodVisitor {
                    return object : MethodVisitor(Opcodes.ASM9) {
                        override fun visitInvokeDynamicInsn(name: String?, desc: String?,
                                                            bsm: Handle?, vararg args: Any?) {
                            out.add(bsm?.owner + "." + name)
                        }
                    }
                }
            }, 0)
            return out
        }

        private fun methodCalls(clazz: ByteArray): List<String> {
            val calls = ArrayList<String>()
            ClassReader(clazz).accept(object : ClassVisitor(Opcodes.ASM9) {
                override fun visitMethod(a: Int, n: String?, d: String?, s: String?,
                                         e: Array<String>?): MethodVisitor {
                    return object : MethodVisitor(Opcodes.ASM9) {
                        override fun visitMethodInsn(op: Int, owner: String?, name: String?,
                                                     desc: String?, itf: Boolean) {
                            val kind = when (op) {
                                Opcodes.INVOKESTATIC -> "INVOKESTATIC"
                                Opcodes.INVOKEVIRTUAL -> "INVOKEVIRTUAL"
                                else -> "INVOKE"
                            }
                            calls.add("$kind $owner.$name$desc")
                        }
                    }
                }
            }, 0)
            return calls
        }
    }
}
