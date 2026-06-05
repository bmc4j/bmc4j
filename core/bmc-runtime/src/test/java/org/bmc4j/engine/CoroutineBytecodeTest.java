package org.bmc4j.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Tests {@link CoroutineBytecode#strip} — classpath routing + the LVT removal itself. */
class CoroutineBytecodeTest {

    @Test
    void passes_through_jars_and_missing_entries_unchanged() {
        String jar = "C:\\some\\lib.jar";
        String missing = "C:\\does\\not\\exist";
        String out = CoroutineBytecode.strip(jar + File.pathSeparator + missing);
        assertEquals(jar + File.pathSeparator + missing, out);
    }

    @Test
    void mirrors_directories_to_a_new_path(@TempDir Path dir) throws Exception {
        Files.write(dir.resolve("note.txt"), "hi".getBytes());
        String out = CoroutineBytecode.strip(dir.toString());
        assertNotEquals(dir.toString(), out, "a directory entry should be mirrored to a cache copy");
        assertTrue(Files.exists(Path.of(out, "note.txt")), "non-class files are copied verbatim");
    }

    @Test
    void strips_lvt_from_coroutine_methods_only(@TempDir Path dir) throws Exception {
        Path classFile = dir.resolve("Demo.class");
        Files.write(classFile, classWithTwoMethods());

        String mirrored = CoroutineBytecode.strip(dir.toString());
        byte[] out = Files.readAllBytes(Path.of(mirrored, "Demo.class"));

        Map<String, Integer> lvtCounts = countLocalVariables(out);
        assertEquals(0, lvtCounts.getOrDefault("suspendy", 0),
                "coroutine method (Continuation param) must have its LVT stripped");
        assertTrue(lvtCounts.getOrDefault("plain", 0) > 0,
                "ordinary method keeps its LVT so counterexamples keep variable names");
    }

    /** A class with one ordinary method and one "suspend-like" method (trailing Continuation). */
    private static byte[] classWithTwoMethods() {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "Demo", null, "java/lang/Object", null);
        emit(cw, "plain", "(I)V");
        emit(cw, "suspendy", "(ILkotlin/coroutines/Continuation;)V");
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static void emit(ClassWriter cw, String name, String desc) {
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, name, desc, null, null);
        mv.visitCode();
        Label start = new Label();
        Label end = new Label();
        mv.visitLabel(start);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitLabel(end);
        mv.visitLocalVariable("x", "I", null, start, end, 0);   // an LVT entry to (maybe) strip
        mv.visitMaxs(1, 3);
        mv.visitEnd();
    }

    private static Map<String, Integer> countLocalVariables(byte[] clazz) {
        Map<String, Integer> counts = new HashMap<>();
        new ClassReader(clazz).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int a, String name, String d, String s, String[] e) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitLocalVariable(String n, String d2, String s2, Label st, Label en, int i) {
                        counts.merge(name, 1, Integer::sum);
                    }
                };
            }
        }, 0);
        return counts;
    }
}
