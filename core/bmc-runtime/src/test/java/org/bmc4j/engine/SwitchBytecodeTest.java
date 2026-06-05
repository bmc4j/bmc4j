package org.bmc4j.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Unit tests for {@link SwitchBytecode}'s {@code SwitchBootstraps.typeSwitch} desugar. We synthesize
 * the {@code typeSwitch} invokedynamic directly with ASM (so the test stays at the module's Java 17
 * target — pattern {@code switch} source would need Java 21), then load the rewritten class and check
 * the generated helper reproduces the typeSwitch contract exactly: null -> -1, first matching label
 * index {@code >= restartIndex}, else {@code labels.length}; type labels by {@code instanceof},
 * String labels by value. The sound-over-symbolic end-to-end is covered by the patternswitch example.
 */
class SwitchBytecodeTest {

    private static final String BSM_OWNER = "java/lang/runtime/SwitchBootstraps";
    private static final String BSM_DESC =
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;"
                    + "[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;";

    /**
     * Build {@code static int sw(Object target, int restartIndex)} whose body is exactly a
     * {@code typeSwitch} indy over the given labels (Type for a class label, String for a constant),
     * returning the indy result. After desugaring this becomes our helper, so invoking it tests the
     * contract directly.
     */
    private static byte[] classWithTypeSwitch(Object... labels) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "SwC", null, "java/lang/Object", null);
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "sw",
                "(Ljava/lang/Object;I)I", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0); // target
        mv.visitVarInsn(Opcodes.ILOAD, 1); // restartIndex
        Handle bsm = new Handle(Opcodes.H_INVOKESTATIC, BSM_OWNER, "typeSwitch", BSM_DESC, false);
        mv.visitInvokeDynamicInsn("typeSwitch", "(Ljava/lang/Object;I)I", bsm, labels);
        mv.visitInsn(Opcodes.IRETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    @Test
    void typeSwitch_indy_is_replaced_by_a_static_helper() {
        byte[] rewritten = SwitchBytecode.rewriteClass(classWithTypeSwitch(
                org.objectweb.asm.Type.getObjectType("java/lang/Integer"),
                org.objectweb.asm.Type.getObjectType("java/lang/String")));
        List<String> calls = methodCalls(rewritten);
        assertFalse(sawTypeSwitchIndy(rewritten), "typeSwitch indy must be gone");
        assertTrue(calls.stream().anyMatch(c -> c.contains("SwC.bmc$typeSwitch$0")),
                "indy should be replaced by a call to a generated helper: " + calls);
    }

    @Test
    void helper_reproduces_the_typeSwitch_contract() throws Exception {
        Class<?> c = define("SwC", SwitchBytecode.rewriteClass(classWithTypeSwitch(
                org.objectweb.asm.Type.getObjectType("java/lang/Integer"),
                org.objectweb.asm.Type.getObjectType("java/lang/String"))));
        Method sw = c.getMethod("sw", Object.class, int.class);

        // null -> -1
        assertEquals(-1, sw.invoke(null, null, 0));
        // Integer matches label 0
        assertEquals(0, sw.invoke(null, 42, 0));
        // String matches label 1
        assertEquals(1, sw.invoke(null, "hi", 0));
        // restartIndex skips an otherwise-matching earlier label (guard re-entry)
        assertEquals(2, sw.invoke(null, 42, 1)); // Integer's label 0 is below restartIndex -> no match
        // no match -> labels.length
        assertEquals(2, sw.invoke(null, 3.14, 0));
    }

    @Test
    void string_constant_label_is_structurally_desugared() {
        // String-label *content* matching routes through BmcStrings/CProverString, which only has
        // meaning inside JBMC (charAt returns '\0' on a real JVM), so we don't execute that path
        // here — the matching is checked end-to-end by the patternswitch example under JBMC. We do
        // confirm the site is desugared (indy gone) and the helper calls the sound BmcStrings.objEquals.
        byte[] rewritten = SwitchBytecode.rewriteClass(classWithTypeSwitch(
                "hi", org.objectweb.asm.Type.getObjectType("java/lang/String")));
        assertFalse(sawTypeSwitchIndy(rewritten), "typeSwitch indy must be gone");
        assertTrue(methodCalls(rewritten).stream()
                        .anyMatch(c -> c.equals("org/bmc4j/engine/BmcStrings.objEquals"
                                + "(Ljava/lang/Object;Ljava/lang/Object;)Z")),
                "String constant label must compare via the sound BmcStrings.objEquals");
    }

    @Test
    void unrecognised_label_leaves_indy_untouched() {
        // A boxed-long label IS recognised; use a bare Handle (an unexpected label kind) to force the
        // soundness bail-out: the whole site must be left as an indy rather than desugared unsoundly.
        Handle weird = new Handle(Opcodes.H_INVOKESTATIC, "X", "y", "()V", false);
        byte[] rewritten = SwitchBytecode.rewriteClass(classWithTypeSwitch(weird));
        assertTrue(sawTypeSwitchIndy(rewritten),
                "an unrecognised label kind must leave the typeSwitch indy in place");
    }

    @Test
    void boxed_integer_constant_label_matches_by_value() throws Exception {
        Class<?> c = define("SwC", SwitchBytecode.rewriteClass(classWithTypeSwitch(
                Integer.valueOf(7), org.objectweb.asm.Type.getObjectType("java/lang/Integer"))));
        Method sw = c.getMethod("sw", Object.class, int.class);
        assertEquals(0, sw.invoke(null, 7, 0));   // equals the constant 7
        assertEquals(1, sw.invoke(null, 9, 0));   // an Integer but not 7 -> the Integer type label
        assertEquals(2, sw.invoke(null, "x", 0)); // neither -> default
    }

    // ---- helpers -----------------------------------------------------------------------------

    private static boolean sawTypeSwitchIndy(byte[] clazz) {
        boolean[] saw = {false};
        new ClassReader(clazz).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int a, String n, String d, String s, String[] ex) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitInvokeDynamicInsn(String name, String desc, Handle bsm, Object... args) {
                        if (BSM_OWNER.equals(bsm.getOwner()) && name.equals("typeSwitch")) {
                            saw[0] = true;
                        }
                    }
                };
            }
        }, 0);
        return saw[0];
    }

    private static List<String> methodCalls(byte[] clazz) {
        List<String> calls = new ArrayList<>();
        new ClassReader(clazz).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int a, String n, String d, String s, String[] e) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(int op, String owner, String name, String desc, boolean itf) {
                        calls.add(owner + "." + name + desc);
                    }
                };
            }
        }, 0);
        return calls;
    }

    private static Class<?> define(String name, byte[] bytes) {
        return new ChildFirst(SwitchBytecodeTest.class.getClassLoader(), Map.of(name, bytes)).defineNamed(name);
    }

    /** Loads the named classes from given bytes (child-first), delegating everything else to parent. */
    private static final class ChildFirst extends ClassLoader {
        private final Map<String, byte[]> defs;

        ChildFirst(ClassLoader parent, Map<String, byte[]> defs) {
            super(parent);
            this.defs = defs;
        }

        Class<?> defineNamed(String name) {
            byte[] b = defs.get(name);
            return defineClass(name, b, 0, b.length);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (defs.containsKey(name)) {
                synchronized (getClassLoadingLock(name)) {
                    Class<?> c = findLoadedClass(name);
                    if (c == null) {
                        c = defineNamed(name);
                    }
                    if (resolve) {
                        resolveClass(c);
                    }
                    return c;
                }
            }
            return super.loadClass(name, resolve);
        }
    }
}
