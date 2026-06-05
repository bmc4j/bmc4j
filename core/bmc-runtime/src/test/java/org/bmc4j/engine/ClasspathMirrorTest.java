package org.bmc4j.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * The rewrite passes must mirror JAR classpath entries, not only directory entries. The
 * in-repo test bed runs on {@code includeBuild} class dirs, but a published consumer gets
 * {@code bmc-models} and third-party libs as jars — if jars passed through unrewritten, the shipped
 * product would silently lose every String shim / concat / record / typeSwitch / Math desugar.
 *
 * <p>These tests drive the real {@link StringBytecode#rewrite(String)} (which now delegates to
 * {@link ClasspathMirror}) over a synthesized classpath, so they cover both the shared mirror engine
 * and that StringBytecode wires into it. A directory regression and the content-hash cache behaviour
 * are pinned alongside.
 */
class ClasspathMirrorTest {

    /** A class {@code Sample} with: a {@code String.equals} call (String-shim redirect) and a
     *  {@code makeConcatWithConstants} indy (concat desugar) — the two transforms the jar mirror must reach
     *  inside a jar. */
    private static byte[] sampleClass() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "Sample", null, "java/lang/Object", null);
        // boolean eq(String a, String b) { return a.equals(b); }
        MethodVisitor eq = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "eq",
                "(Ljava/lang/String;Ljava/lang/String;)Z", null, null);
        eq.visitCode();
        eq.visitVarInsn(Opcodes.ALOAD, 0);
        eq.visitVarInsn(Opcodes.ALOAD, 1);
        eq.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "equals",
                "(Ljava/lang/Object;)Z", false);
        eq.visitInsn(Opcodes.IRETURN);
        eq.visitMaxs(0, 0);
        eq.visitEnd();
        // String wrap(String s) { return "[" + s + "]"; }  via StringConcatFactory indy
        MethodVisitor wrap = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "wrap",
                "(Ljava/lang/String;)Ljava/lang/String;", null, null);
        wrap.visitCode();
        wrap.visitVarInsn(Opcodes.ALOAD, 0);
        Handle bsm = new Handle(Opcodes.H_INVOKESTATIC, "java/lang/invoke/StringConcatFactory",
                "makeConcatWithConstants",
                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;"
                        + "Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;", false);
        wrap.visitInvokeDynamicInsn("makeConcatWithConstants",
                "(Ljava/lang/String;)Ljava/lang/String;", bsm, "[]");
        wrap.visitInsn(Opcodes.ARETURN);
        wrap.visitMaxs(0, 0);
        wrap.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static Path writeJar(Path dir, String name, byte[] sampleClassBytes) throws IOException {
        Path jar = dir.resolve(name);
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(jar))) {
            // a non-class resource, to prove it is copied verbatim
            zos.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
            zos.write("Manifest-Version: 1.0\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("Sample.class"));
            zos.write(sampleClassBytes);
            zos.closeEntry();
        }
        return jar;
    }

    @Test
    void jar_entry_is_rewritten_in_place(@TempDir Path tmp) throws Exception {
        Path jar = writeJar(tmp, "models.jar", sampleClass());
        String rewritten = StringBytecode.rewrite(jar.toString());

        // The classpath entry must now point at a DIFFERENT jar (the rewritten mirror), not the input.
        assertNotEquals(jar.toString(), rewritten, "jar entry must be mirrored, not passed through");
        Path mirroredJar = Path.of(rewritten);
        assertTrue(mirroredJar.getFileName().toString().endsWith(".jar"), "mirror is a jar");
        assertTrue(Files.isRegularFile(mirroredJar), "mirrored jar exists");

        List<String> calls = methodCallsInJar(mirroredJar, "Sample.class");
        // String.equals -> BmcStrings.equals
        assertTrue(calls.contains("INVOKESTATIC org/bmc4j/engine/BmcStrings.equals(Ljava/lang/String;Ljava/lang/Object;)Z"),
                "String.equals inside the jar must be redirected to BmcStrings: " + calls);
        assertFalse(calls.stream().anyMatch(c -> c.contains("java/lang/String.equals")),
                "original String.equals must be gone from the jar's class");
        // concat indy -> generated helper (no StringConcatFactory indy left)
        assertFalse(invokeDynamicsInJar(mirroredJar, "Sample.class").stream()
                        .anyMatch(s -> s.contains("StringConcatFactory")),
                "concat indy inside the jar must be desugared");

        // The non-class resource is copied verbatim.
        try (ZipFile zf = new ZipFile(mirroredJar.toFile())) {
            assertTrue(zf.getEntry("META-INF/MANIFEST.MF") != null, "manifest copied into mirror");
        }
    }

    @Test
    void directory_entry_still_works(@TempDir Path tmp) throws Exception {
        // Regression: a class DIRECTORY entry is mirrored exactly as before.
        Path dir = tmp.resolve("classes");
        Files.createDirectories(dir);
        Files.write(dir.resolve("Sample.class"), sampleClass());

        String rewritten = StringBytecode.rewrite(dir.toString());
        assertNotEquals(dir.toString(), rewritten, "directory entry is mirrored");
        Path mirroredClass = Path.of(rewritten).resolve("Sample.class");
        assertTrue(Files.isRegularFile(mirroredClass), "mirrored class exists in the dir");

        List<String> calls = methodCalls(Files.readAllBytes(mirroredClass));
        assertTrue(calls.contains("INVOKESTATIC org/bmc4j/engine/BmcStrings.equals(Ljava/lang/String;Ljava/lang/Object;)Z"),
                "directory class still gets the String-shim redirect: " + calls);
    }

    @Test
    void jar_mirror_is_content_hash_cached_no_rewrite_on_rerun(@TempDir Path tmp) throws Exception {
        // Drive the shared mirror directly with a transform that COUNTS how many class files it rewrites,
        // so we can prove a second run over the same (unchanged) jar does no re-rewrite (cache hit).
        Path jar = writeJar(tmp, "cached.jar", sampleClass());
        int[] transforms = {0};
        ClasspathMirror.ClassTransform counting = bytes -> {
            transforms[0]++;
            return new ClasspathMirror.Transformed(StringBytecode.rewriteClass(bytes));
        };

        String first = ClasspathMirror.mirror(jar.toString(), "test-cache-" + unique(), counting);
        int afterFirst = transforms[0];
        assertTrue(afterFirst >= 1, "first run rewrites the class");

        // Same content -> same cache key -> no re-rewrite.
        String second = ClasspathMirror.mirror(jar.toString(), cacheNameOf(first), counting);
        assertEquals(first, second, "same jar content resolves to the same mirrored jar");
        assertEquals(afterFirst, transforms[0], "second run must be a cache hit: no class re-rewritten");
    }

    @Test
    void published_jar_entry_fast_path_does_zero_writes(@TempDir Path tmp) throws Exception {
        // A content-hashed jar is immutable once published. When the dest jar AND its .done marker
        // already exist, the mirror must take the fast path: return the existing path and perform
        // ZERO writes/re-opens-for-write on it. (On Windows, re-opening an already-published jar that a
        // concurrent reader holds open for writing is exactly the sharing-violation this fixes.)
        // We pre-populate dest + .done, mark dest READ-ONLY, then assert the call succeeds, returns
        // dest unchanged, and never rewrites the class (transform count stays 0).
        Path jar = writeJar(tmp, "published.jar", sampleClass());
        String cacheName = "test-fastpath-" + unique();
        Path root = Path.of(System.getProperty("user.home"), ".cache", "bmc4j", cacheName);
        Files.createDirectories(root);
        String hash = ClasspathMirror.contentHash(jar);
        Path dest = root.resolve(hash + ".jar");
        Path done = root.resolve(hash + ".done");
        // Sentinel payload distinct from any real rewrite, so any rewrite would change size/content.
        byte[] sentinel = "SENTINEL-PUBLISHED-JAR".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(dest, sentinel);
        Files.write(done, new byte[0]);
        long sizeBefore = Files.size(dest);
        long mtimeBefore = Files.getLastModifiedTime(dest).toMillis();
        // Make dest read-only: if the fast path tried to open/replace it, the write would FAIL.
        assertTrue(dest.toFile().setReadOnly(), "set dest read-only for the no-write assertion");

        int[] transforms = {0};
        ClasspathMirror.ClassTransform counting = bytes -> {
            transforms[0]++;
            return new ClasspathMirror.Transformed(StringBytecode.rewriteClass(bytes));
        };
        String result;
        try {
            result = ClasspathMirror.mirror(jar.toString(), cacheName, counting);
        } finally {
            dest.toFile().setWritable(true); // let @TempDir / cache cleanup remove it
        }

        assertEquals(dest.toString(), result, "fast path must return the already-published jar");
        assertEquals(0, transforms[0], "fast path must do NO rewrite (the entry is already published)");
        assertEquals(sizeBefore, Files.size(dest), "published jar must be untouched (size unchanged)");
        assertEquals(mtimeBefore, Files.getLastModifiedTime(dest).toMillis(),
                "published jar must be untouched (mtime unchanged)");
        assertEquals(new String(sentinel, java.nio.charset.StandardCharsets.UTF_8),
                Files.readString(dest, java.nio.charset.StandardCharsets.UTF_8),
                "published jar bytes must be byte-for-byte unchanged (zero writes)");
    }

    @Test
    void jar_missing_done_marker_is_redone(@TempDir Path tmp) throws Exception {
        // A half-written jar (dest present but NO .done marker, e.g. from a crashed/concurrent run)
        // must be REDONE, not mistaken for complete: the mirror rewrites and publishes the marker.
        Path jar = writeJar(tmp, "halfwritten.jar", sampleClass());
        String cacheName = "test-halfwritten-" + unique();
        Path root = Path.of(System.getProperty("user.home"), ".cache", "bmc4j", cacheName);
        Files.createDirectories(root);
        String hash = ClasspathMirror.contentHash(jar);
        Path dest = root.resolve(hash + ".jar");
        Path done = root.resolve(hash + ".done");
        // Pre-populate a bogus partial jar with NO .done marker.
        Files.write(dest, "PARTIAL-NO-DONE".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertFalse(Files.exists(done), "precondition: no .done marker yet");

        int[] transforms = {0};
        ClasspathMirror.ClassTransform counting = bytes -> {
            transforms[0]++;
            return new ClasspathMirror.Transformed(StringBytecode.rewriteClass(bytes));
        };
        String result = ClasspathMirror.mirror(jar.toString(), cacheName, counting);

        assertEquals(dest.toString(), result, "redo resolves to the same content-hashed dest");
        assertTrue(transforms[0] >= 1, "missing .done must trigger a redo (the class is rewritten)");
        assertTrue(Files.exists(done), "redo publishes the .done marker");
        // The dest is now a real rewritten jar (the desugar reached the class), not the partial.
        List<String> calls = methodCallsInJar(dest, "Sample.class");
        assertTrue(calls.contains(
                        "INVOKESTATIC org/bmc4j/engine/BmcStrings.equals(Ljava/lang/String;Ljava/lang/Object;)Z"),
                "redone jar must be a real rewrite (String.equals redirected): " + calls);
    }

    @Test
    void changed_jar_content_gets_a_fresh_mirror(@TempDir Path tmp) throws Exception {
        // Content-hash keying: a different jar content must NOT reuse the old mirror.
        String cacheName = "test-change-" + unique();
        Path jar = writeJar(tmp, "v.jar", sampleClass());
        String a = ClasspathMirror.mirror(jar.toString(), cacheName, b ->
                new ClasspathMirror.Transformed(StringBytecode.rewriteClass(b)));

        // Rewrite the SAME path with different bytes (an extra resource changes the content hash).
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(jar))) {
            zos.putNextEntry(new ZipEntry("Sample.class"));
            zos.write(sampleClass());
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("extra.txt"));
            zos.write(new byte[]{1, 2, 3});
            zos.closeEntry();
        }
        String b = ClasspathMirror.mirror(jar.toString(), cacheName, t ->
                new ClasspathMirror.Transformed(StringBytecode.rewriteClass(t)));
        assertNotEquals(a, b, "changed jar content must map to a new mirror jar (hash-keyed)");
    }

    @Test
    void unreadable_jar_fails_LOUD_not_open(@TempDir Path tmp) throws Exception {
        // A mirror failure must NOT silently fall back to the unrewritten entry (that would
        // analyse unsound String/indy classes as "verified"). It throws; the engine-error handling
        // turns that into UNKNOWN. Here we assert the throw + an actionable message naming the entry.
        Path bad = tmp.resolve("corrupt.jar");
        Files.write(bad, new byte[]{0, 1, 2, 3, 4}); // not a valid zip
        RuntimeException ex = org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> ClasspathMirror.mirror(bad.toString(), "test-faillloud-" + unique(),
                        b -> new ClasspathMirror.Transformed(StringBytecode.rewriteClass(b))),
                "a corrupt jar must fail LOUD (throw), never pass through unrewritten");
        assertTrue(ex.getMessage() != null && ex.getMessage().contains(bad.toString()),
                "the failure message must name the entry that couldn't be mirrored: " + ex.getMessage());
    }

    @Test
    void simulated_io_failure_mirroring_a_dir_fails_LOUD(@TempDir Path tmp) throws Exception {
        // A transform that blows up (a stand-in for any IO/RuntimeException while mirroring)
        // must NOT be swallowed into a silent pass-through — it must throw so the proof goes UNKNOWN.
        Path dir = tmp.resolve("classes");
        Files.createDirectories(dir);
        Files.write(dir.resolve("Sample.class"), sampleClass());
        ClasspathMirror.ClassTransform boom = b -> {
            throw new RuntimeException("simulated mirror IO failure");
        };
        RuntimeException ex = org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> ClasspathMirror.mirror(dir.toString(), "test-dirboom-" + unique(), boom),
                "a mirror failure on a dir entry must fail LOUD");
        assertTrue(ex.getMessage() != null && ex.getMessage().contains(dir.toString()),
                "the failure message must name the dir entry: " + ex.getMessage());
    }

    @Test
    void distinct_dir_paths_cannot_share_a_dest(@TempDir Path tmp) throws Exception {
        // The dir mirror is keyed by a FULL content hash, not a 32-bit String.hashCode
        // of the path — two distinct dirs must never collide into one mirror and mix their classes.
        // Two dirs with DIFFERENT content must resolve to different dests.
        Path a = tmp.resolve("a");
        Path b = tmp.resolve("b");
        Files.createDirectories(a);
        Files.createDirectories(b);
        Files.write(a.resolve("A.class"), sampleClass());
        // give b a distinct entry so its content hash differs from a's
        Files.write(b.resolve("A.class"), sampleClass());
        Files.write(b.resolve("extra.txt"), new byte[]{9, 9, 9});
        ClasspathMirror.ClassTransform t = bytes ->
                new ClasspathMirror.Transformed(StringBytecode.rewriteClass(bytes));
        String cacheName = "test-distinct-" + unique();
        String ma = ClasspathMirror.mirror(a.toString(), cacheName, t);
        String mb = ClasspathMirror.mirror(b.toString(), cacheName, t);
        assertNotEquals(ma, mb, "two distinct dir contents must map to different mirror dirs (full hash)");
    }

    @Test
    void deleted_class_is_absent_from_the_next_dir_mirror(@TempDir Path tmp) throws Exception {
        // Staleness: a class present in mirror v1, then deleted in the source, must be
        // ABSENT from the v2 mirror — never linger as a phantom class on the analysis classpath.
        Path dir = tmp.resolve("classes");
        Files.createDirectories(dir);
        Files.write(dir.resolve("Sample.class"), sampleClass());
        Files.write(dir.resolve("Gone.class"), sampleClass());
        ClasspathMirror.ClassTransform t = bytes ->
                new ClasspathMirror.Transformed(StringBytecode.rewriteClass(bytes));
        String cacheName = "test-stale-" + unique();

        String v1 = ClasspathMirror.mirror(dir.toString(), cacheName, t);
        assertTrue(Files.isRegularFile(Path.of(v1).resolve("Gone.class")), "v1 mirror has Gone.class");

        // Delete a class from the source and re-mirror; content changed -> fresh content-hashed dest.
        Files.delete(dir.resolve("Gone.class"));
        String v2 = ClasspathMirror.mirror(dir.toString(), cacheName, t);
        assertNotEquals(v1, v2, "changed dir content must map to a new mirror (content-hash keyed)");
        assertTrue(Files.isRegularFile(Path.of(v2).resolve("Sample.class")), "v2 keeps the surviving class");
        assertFalse(Files.isRegularFile(Path.of(v2).resolve("Gone.class")),
                "a deleted source class must be ABSENT from the next mirror (no phantom)");
    }

    @Test
    void dir_mirror_is_content_hash_cached_no_rewrite_on_rerun(@TempDir Path tmp) throws Exception {
        // The dir branch is content-hash cached with a .done marker, like jars: a second run over the
        // same (unchanged) dir is a cache hit and re-rewrites nothing.
        Path dir = tmp.resolve("classes");
        Files.createDirectories(dir);
        Files.write(dir.resolve("Sample.class"), sampleClass());
        int[] transforms = {0};
        ClasspathMirror.ClassTransform counting = bytes -> {
            transforms[0]++;
            return new ClasspathMirror.Transformed(StringBytecode.rewriteClass(bytes));
        };
        String cacheName = "test-dircache-" + unique();
        String first = ClasspathMirror.mirror(dir.toString(), cacheName, counting);
        int afterFirst = transforms[0];
        assertTrue(afterFirst >= 1, "first run rewrites the class");
        String second = ClasspathMirror.mirror(dir.toString(), cacheName, counting);
        assertEquals(first, second, "same dir content resolves to the same mirror dir");
        assertEquals(afterFirst, transforms[0], "second run must be a cache hit: no class re-rewritten");
    }

    @Test
    void cache_lives_under_dot_cache_bmc4j(@TempDir Path tmp) throws Exception {
        // The rewrite mirror caches are unified under ~/.cache/bmc4j/ (not the legacy dir).
        Path dir = tmp.resolve("classes");
        Files.createDirectories(dir);
        Files.write(dir.resolve("Sample.class"), sampleClass());
        String mirrored = ClasspathMirror.mirror(dir.toString(), "test-cachepath-" + unique(),
                b -> new ClasspathMirror.Transformed(StringBytecode.rewriteClass(b)));
        Path expectedRoot = Path.of(System.getProperty("user.home"), ".cache", "bmc4j");
        assertTrue(Path.of(mirrored).toAbsolutePath().startsWith(expectedRoot.toAbsolutePath()),
                "mirror cache must live under ~/.cache/bmc4j/: " + mirrored);
    }

    // ---- helpers ----

    private static String unique() {
        return Long.toHexString(System.nanoTime()) + "-" + Integer.toHexString(System.identityHashCode(new Object()));
    }

    /** Recover the cache subdir name from a mirrored jar path (its parent dir's name). */
    private static String cacheNameOf(String mirroredJarPath) {
        Path p = Path.of(mirroredJarPath);
        return p.getParent().getFileName().toString();
    }

    private static List<String> methodCallsInJar(Path jar, String entry) throws IOException {
        try (ZipFile zf = new ZipFile(jar.toFile())) {
            ZipEntry e = zf.getEntry(entry);
            try (var in = zf.getInputStream(e)) {
                return methodCalls(in.readAllBytes());
            }
        }
    }

    private static List<String> invokeDynamicsInJar(Path jar, String entry) throws IOException {
        List<String> out = new ArrayList<>();
        byte[] bytes;
        try (ZipFile zf = new ZipFile(jar.toFile())) {
            try (var in = zf.getInputStream(zf.getEntry(entry))) {
                bytes = in.readAllBytes();
            }
        }
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int a, String n, String d, String s, String[] ex) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitInvokeDynamicInsn(String name, String desc, Handle bsm, Object... args) {
                        out.add(bsm.getOwner() + "." + name);
                    }
                };
            }
        }, 0);
        return out;
    }

    private static List<String> methodCalls(byte[] clazz) {
        List<String> calls = new ArrayList<>();
        new ClassReader(clazz).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int a, String n, String d, String s, String[] e) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(int op, String owner, String name, String desc, boolean itf) {
                        String kind = op == Opcodes.INVOKESTATIC ? "INVOKESTATIC"
                                : op == Opcodes.INVOKEVIRTUAL ? "INVOKEVIRTUAL" : "INVOKE";
                        calls.add(kind + " " + owner + "." + name + desc);
                    }
                };
            }
        }, 0);
        return calls;
    }
}
