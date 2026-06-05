package org.bmc4j.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Unit tests for {@link ContractRewriter}'s bytecode transform. The end-to-end "JBMC
 * honors the rewrite" property is proven by the spike (spike/contracts MechanismSpikeTest);
 * here we pin the transform itself, which needs no engine.
 */
class ContractRewriterTest {

    private static final ContractRewriter.Redirect TRIANGLE =
            new ContractRewriter.Redirect("pkg/C", "triangle", "(I)I", "pkg/Stubs", "triangle");

    @Test
    void redirects_matching_invokestatic_call_sites_to_the_stub() {
        byte[] out = ContractRewriter.rewriteClass(callerCalling("pkg/C", "triangle", "(I)I"),
                List.of(TRIANGLE));
        List<String> calls = staticCalls(out);
        assertTrue(calls.contains("pkg/Stubs.triangle(I)I"), "call should be redirected to the stub");
        assertTrue(calls.stream().noneMatch(c -> c.startsWith("pkg/C.triangle")),
                "original call target must be gone");
    }

    @Test
    void leaves_non_matching_calls_untouched() {
        // Different descriptor -> no match.
        byte[] out = ContractRewriter.rewriteClass(callerCalling("pkg/C", "triangle", "(J)I"),
                List.of(TRIANGLE));
        assertTrue(staticCalls(out).contains("pkg/C.triangle(J)I"));
    }

    @Test
    void null_descriptor_matches_any_overload() {
        var anyDesc = new ContractRewriter.Redirect("pkg/C", "triangle", null, "pkg/Stubs", "triangle");
        byte[] out = ContractRewriter.rewriteClass(callerCalling("pkg/C", "triangle", "(J)I"),
                List.of(anyDesc));
        assertTrue(staticCalls(out).contains("pkg/Stubs.triangle(J)I"));
    }

    @Test
    void excluded_caller_class_is_left_untouched() {
        // The caller class is "pkg/Caller" (see callerCalling). Excluding it means its
        // call sites pass through unchanged — the modular-enforce case where the proof's
        // own direct call to the method-under-test must stay real.
        byte[] out = ContractRewriter.rewriteClass(callerCalling("pkg/C", "triangle", "(I)I"),
                List.of(TRIANGLE), "pkg/Caller");
        assertTrue(staticCalls(out).contains("pkg/C.triangle(I)I"), "excluded caller must keep the real call");
        assertTrue(staticCalls(out).stream().noneMatch(c -> c.startsWith("pkg/Stubs")), "no redirect in excluded class");
    }

    @Test
    void a_different_excluded_class_still_rewrites_this_one() {
        // Excluding some OTHER class does not protect pkg/Caller.
        byte[] out = ContractRewriter.rewriteClass(callerCalling("pkg/C", "triangle", "(I)I"),
                List.of(TRIANGLE), "pkg/SomethingElse");
        assertTrue(staticCalls(out).contains("pkg/Stubs.triangle(I)I"));
    }

    @Test
    void empty_redirects_returns_classpath_unchanged() {
        String cp = "a.jar" + File.pathSeparator + "b.jar";
        assertEquals(cp, ContractRewriter.rewrite(cp, List.of()));
    }

    @Test
    void mirrors_directories_and_rewrites_class_files(@TempDir Path dir) throws Exception {
        Files.createDirectories(dir.resolve("pkg"));
        Files.write(dir.resolve("pkg/Caller.class"), callerCalling("pkg/C", "triangle", "(I)I"));
        String mirrored = ContractRewriter.rewrite(dir.toString(), List.of(TRIANGLE));
        assertNotEquals(dir.toString(), mirrored);
        byte[] out = Files.readAllBytes(Path.of(mirrored, "pkg/Caller.class"));
        assertTrue(staticCalls(out).contains("pkg/Stubs.triangle(I)I"));
    }

    @Test
    void a_rewrite_failure_fails_LOUD_never_falls_back_to_the_original_dir(@TempDir Path dir) throws Exception {
        // A malformed .class makes ASM's ClassReader throw mid-transform. The contract rewrite must
        // surface that as the fail-loud MirrorException (-> UNKNOWN), NOT swallow it and return the
        // original, un-redirected dir (which would analyse the real call sites as the contract proof).
        Files.createDirectories(dir.resolve("pkg"));
        Files.write(dir.resolve("pkg/Caller.class"), new byte[]{0, 1, 2, 3, 4}); // not a valid class
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> ContractRewriter.rewrite(dir.toString(), List.of(TRIANGLE)),
                "a rewrite failure must throw (fail loud), never silently fall back to the original dir");
        assertTrue(ex.getMessage() != null && ex.getMessage().contains(dir.toString()),
                "the failure message must name the entry that couldn't be mirrored: " + ex.getMessage());
    }

    @Test
    void distinct_configs_over_the_same_dir_get_distinct_complete_mirrors(@TempDir Path dir) throws Exception {
        // The contract mirror's identity folds in (redirects, excludeCaller) as extra key material,
        // so two distinct configurations over the SAME source dir must resolve to DISTINCT mirrors —
        // no 32-bit-hash collision mixing one config's classes into the other — and BOTH must be
        // complete (a .done marker present beside each mirror dir).
        Files.createDirectories(dir.resolve("pkg"));
        Files.write(dir.resolve("pkg/Caller.class"), callerCalling("pkg/C", "triangle", "(I)I"));

        // Config A: replace direction (exclude nothing) -> the call site is redirected to the stub.
        String mirrorA = ContractRewriter.rewrite(dir.toString(), List.of(TRIANGLE));
        // Config B: enforce direction with pkg/Caller excluded -> its call site stays real.
        String mirrorB = ContractRewriter.rewrite(dir.toString(), List.of(TRIANGLE), "pkg/Caller");

        assertNotEquals(mirrorA, mirrorB,
                "distinct (redirects, excludeCaller) configs must map to distinct mirror dirs (no collision)");
        // Both directions stay correct.
        assertTrue(staticCalls(Files.readAllBytes(Path.of(mirrorA, "pkg/Caller.class")))
                        .contains("pkg/Stubs.triangle(I)I"),
                "replace config A must redirect the call site to the stub");
        assertTrue(staticCalls(Files.readAllBytes(Path.of(mirrorB, "pkg/Caller.class")))
                        .contains("pkg/C.triangle(I)I"),
                "enforce config B (excluded caller) must keep the real call site");
        // Both mirrors are complete: a .done marker sits beside each mirror dir.
        assertTrue(Files.isRegularFile(doneMarkerOf(mirrorA)), "config A mirror must be .done-marked complete");
        assertTrue(Files.isRegularFile(doneMarkerOf(mirrorB)), "config B mirror must be .done-marked complete");
        assertNotEquals(doneMarkerOf(mirrorA), doneMarkerOf(mirrorB), "distinct configs -> distinct markers");
    }

    @Test
    void a_cache_hit_reuses_a_done_marked_mirror(@TempDir Path dir) throws Exception {
        // A second rewrite over the same (dir, config) is a cache hit: it returns the same .done-marked
        // mirror dir rather than building a new one.
        Files.createDirectories(dir.resolve("pkg"));
        Files.write(dir.resolve("pkg/Caller.class"), callerCalling("pkg/C", "triangle", "(I)I"));

        String first = ContractRewriter.rewrite(dir.toString(), List.of(TRIANGLE));
        assertTrue(Files.isRegularFile(doneMarkerOf(first)), "first mirror must be .done-marked complete");
        String second = ContractRewriter.rewrite(dir.toString(), List.of(TRIANGLE));
        assertEquals(first, second, "same (dir, config) must reuse the same .done-marked mirror (cache hit)");
        assertFalse(first.equals(dir.toString()), "the mirror must be a cache dir, not the original source dir");
    }

    // --- helpers ---

    /** The {@code <hash>.done} completion marker beside a mirror dir (sibling in the same cache root). */
    private static Path doneMarkerOf(String mirrorDir) {
        Path p = Path.of(mirrorDir);
        return p.resolveSibling(p.getFileName().toString() + ".done");
    }


    /** A class pkg/Caller with a method that does: invokestatic owner.name desc; return. */
    private static byte[] callerCalling(String owner, String name, String desc) {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "pkg/Caller", null, "java/lang/Object", null);
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "use", "()V", null, null);
        mv.visitCode();
        // push a default arg of the right type, then call.
        if (desc.startsWith("(J)")) {
            mv.visitInsn(Opcodes.LCONST_0);
        } else {
            mv.visitInsn(Opcodes.ICONST_0);
        }
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, owner, name, desc, false);
        mv.visitInsn(Opcodes.POP);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(2, 0);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static List<String> staticCalls(byte[] clazz) {
        List<String> calls = new ArrayList<>();
        new ClassReader(clazz).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int a, String n, String d, String s, String[] e) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(int op, String owner, String name, String desc, boolean itf) {
                        if (op == Opcodes.INVOKESTATIC) {
                            calls.add(owner + "." + name + desc);
                        }
                    }
                };
            }
        }, 0);
        return calls;
    }
}
