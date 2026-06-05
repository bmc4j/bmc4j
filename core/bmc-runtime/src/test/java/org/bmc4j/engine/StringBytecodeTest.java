package org.bmc4j.engine;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

/** Unit tests for {@link StringBytecode}'s transform. The sound-semantics end-to-end is covered
 *  by the strings example; here we pin the call-site redirect, which needs no engine. */
class StringBytecodeTest {

    @Test
    void redirects_content_ops_to_BmcStrings_with_receiver_prepended() {
        assertRedirected("equals", "(Ljava/lang/Object;)Z", "(Ljava/lang/String;Ljava/lang/Object;)Z");
        assertRedirected("startsWith", "(Ljava/lang/String;)Z", "(Ljava/lang/String;Ljava/lang/String;)Z");
        assertRedirected("endsWith", "(Ljava/lang/String;)Z", "(Ljava/lang/String;Ljava/lang/String;)Z");
        assertRedirected("contains", "(Ljava/lang/CharSequence;)Z", "(Ljava/lang/String;Ljava/lang/CharSequence;)Z");
    }

    private static void assertRedirected(String name, String desc, String expectedDesc) {
        List<String> calls = methodCalls(StringBytecode.rewriteClass(
                classCalling(Opcodes.INVOKEVIRTUAL, "java/lang/String", name, desc)));
        assertTrue(calls.contains("INVOKESTATIC org/bmc4j/engine/BmcStrings." + name + expectedDesc),
                name + " should be redirected to BmcStrings: " + calls);
        assertFalse(calls.stream().anyMatch(c -> c.contains("java/lang/String." + name)),
                "original String." + name + " call must be gone");
    }

    @Test
    void leaves_other_String_calls_untouched() {
        List<String> calls = methodCalls(StringBytecode.rewriteClass(classCalling(
                Opcodes.INVOKEVIRTUAL, "java/lang/String", "length", "()I")));
        assertTrue(calls.stream().anyMatch(c -> c.contains("java/lang/String.length()I")));
        assertFalse(calls.stream().anyMatch(c -> c.contains("BmcStrings")));
    }

    /**
     * equalsIgnoreCase / compareTo / isEmpty / indexOf / lastIndexOf / substring were
     * probed and found NATIVE-SOUND under JBMC (verified by the conformance pins in
     * {@code proofs.strings.StringLaws}), so they are deliberately NOT redirected to BmcStrings —
     * unlike equals/startsWith/endsWith/contains. This pins that decision: if a future change adds a
     * redirect for one of them, this test fails and forces a deliberate re-evaluation (a needless
     * shim over a native-sound op is wasted unwinding). The arg descriptors below are the real JDK
     * ones for the no-arg / single-arg forms exercised by the conformance proofs.
     */
    @Test
    void native_sound_String_ops_are_left_unredirected() {
        assertNotRedirected("equalsIgnoreCase", "(Ljava/lang/String;)Z");
        assertNotRedirected("compareTo", "(Ljava/lang/String;)I");
        assertNotRedirected("isEmpty", "()Z");
        assertNotRedirected("indexOf", "(Ljava/lang/String;)I");
        assertNotRedirected("indexOf", "(I)I");
        assertNotRedirected("lastIndexOf", "(I)I");
        assertNotRedirected("substring", "(I)Ljava/lang/String;");
        assertNotRedirected("substring", "(II)Ljava/lang/String;");
    }

    private static void assertNotRedirected(String name, String desc) {
        List<String> calls = methodCalls(StringBytecode.rewriteClass(
                classCalling(Opcodes.INVOKEVIRTUAL, "java/lang/String", name, desc)));
        assertTrue(calls.stream().anyMatch(c -> c.contains("java/lang/String." + name + desc)),
                name + " should be left as a native String call: " + calls);
        assertFalse(calls.stream().anyMatch(c -> c.contains("BmcStrings." + name)),
                name + " must NOT be redirected to BmcStrings (it is native-sound): " + calls);
    }

    // ---- #18: Object-typed equals() call sites redirect to BmcStrings.objEquals ----

    @Test
    void object_equals_call_site_is_redirected_to_objEquals() {
        // The collection models compare keys/elements via `key.equals(x)` where key is statically
        // typed Object, so javac emits INVOKEVIRTUAL java/lang/Object.equals(Object)Z. That site
        // bypassed the String-owner redirect and dispatched into JBMC's unsound native String.equals
        // (issue #18). It must now route through BmcStrings.objEquals(Object,Object)Z.
        List<String> calls = methodCalls(StringBytecode.rewriteClass(classCalling(
                Opcodes.INVOKEVIRTUAL, "java/lang/Object", "equals", "(Ljava/lang/Object;)Z")));
        assertTrue(calls.contains(
                        "INVOKESTATIC org/bmc4j/engine/BmcStrings.objEquals(Ljava/lang/Object;Ljava/lang/Object;)Z"),
                "Object.equals must be redirected to BmcStrings.objEquals: " + calls);
        assertFalse(calls.stream().anyMatch(c -> c.contains("java/lang/Object.equals")),
                "the original Object.equals call must be gone: " + calls);
    }

    @Test
    void interface_equals_call_site_is_redirected_to_objEquals() {
        // An interface that redeclares equals (e.g. java/util/List) compiles `list.equals(x)` to
        // INVOKEINTERFACE java/util/List.equals(Object)Z; that is also Object-typed dispatch over a
        // potentially-String element and must be redirected too.
        List<String> calls = methodCalls(StringBytecode.rewriteClass(classCallingInterface(
                "java/util/List", "equals", "(Ljava/lang/Object;)Z")));
        assertTrue(calls.contains(
                        "INVOKESTATIC org/bmc4j/engine/BmcStrings.objEquals(Ljava/lang/Object;Ljava/lang/Object;)Z"),
                "interface .equals must be redirected to BmcStrings.objEquals: " + calls);
        assertFalse(calls.stream().anyMatch(c -> c.contains("java/util/List.equals")),
                "the original interface equals call must be gone: " + calls);
    }

    @Test
    void concrete_class_equals_virtual_call_is_left_alone() {
        // A virtual equals on a concrete non-Object class (e.g. Integer.equals) has a non-String
        // receiver and an already-sound modeled equals, so it is deliberately NOT redirected — only
        // Object-typed (INVOKEVIRTUAL java/lang/Object / INVOKEINTERFACE) dispatch is.
        List<String> calls = methodCalls(StringBytecode.rewriteClass(classCalling(
                Opcodes.INVOKEVIRTUAL, "java/lang/Integer", "equals", "(Ljava/lang/Object;)Z")));
        assertTrue(calls.stream().anyMatch(c -> c.contains("java/lang/Integer.equals")),
                "Integer.equals must be left as a native virtual call: " + calls);
        assertFalse(calls.stream().anyMatch(c -> c.contains("objEquals")),
                "Integer.equals must NOT be redirected: " + calls);
    }

    @Test
    void objEquals_routes_strings_soundly_and_delegates_non_strings() {
        // BmcStrings.objEquals is the redirect target: String/String goes through the sound shim,
        // everything else delegates to the receiver's real equals (so boxed primitives and user
        // classes keep normal semantics). On a real JVM the String/String case runs the shim's
        // length()+charAt loop; CProverString.charAt returns '\0' off-engine, but equal-length equal
        // references still compare equal, so identity-equal strings are true and content soundness is
        // covered end-to-end by the BMC proof below.
        assertTrue(BmcStrings.objEquals("abc", "abc"), "identical String content compares equal");
        assertTrue(BmcStrings.objEquals(null, null), "null/null is equal");
        assertFalse(BmcStrings.objEquals("abc", null), "String vs null is not equal");
        assertFalse(BmcStrings.objEquals("abc", 7), "String vs non-String is not equal");
        // Non-String receivers delegate to their own equals — boxed primitives stay correct.
        assertTrue(BmcStrings.objEquals(Integer.valueOf(7), Integer.valueOf(7)),
                "Integer.equals delegated, equal values compare equal");
        assertFalse(BmcStrings.objEquals(Integer.valueOf(7), Integer.valueOf(8)),
                "Integer.equals delegated, differing values are not equal");
        assertFalse(BmcStrings.objEquals(Integer.valueOf(7), Long.valueOf(7L)),
                "Integer.equals delegated, cross-type is not equal");
    }

    @Test
    void objEquals_call_site_inside_BmcStrings_is_not_rewritten() throws Exception {
        // Soundness/termination guard: objEquals's own `a.equals(b)` fallback is INVOKEVIRTUAL
        // java/lang/Object.equals; if the redirect rewrote it, objEquals would call itself forever.
        // The BMC_STRINGS owner guard must leave BmcStrings's own equals call sites untouched.
        byte[] rewritten = StringBytecode.rewriteClass(classBytes(BmcStrings.class));
        List<String> calls = methodCalls(rewritten);
        assertFalse(calls.stream().anyMatch(c -> c.contains("BmcStrings.objEquals")),
                "BmcStrings's own equals fallback must NOT be redirected into self-recursion: " + calls);
        assertTrue(calls.stream().anyMatch(c -> c.contains("java/lang/Object.equals")),
                "BmcStrings.objEquals must keep its real Object.equals delegation: " + calls);
    }

    // ---- JVM-level behavioral tests: the rewritten bytecode must verify AND compute correctly ----

    /** A real record, so the test exercises javac's actual ObjectMethods bootstrap. */
    record Pt(int x, int y) {
    }

    @Test
    void concat_indy_is_desugared_to_working_bytecode() throws Exception {
        // static String f(String s) { return "[" + s + "]"; }  via StringConcatFactory indy
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "ConcatC", null, "java/lang/Object", null);
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "f",
                "(Ljava/lang/String;)Ljava/lang/String;", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        Handle bsm = new Handle(Opcodes.H_INVOKESTATIC, "java/lang/invoke/StringConcatFactory",
                "makeConcatWithConstants",
                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;"
                        + "Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;", false);
        mv.visitInvokeDynamicInsn("makeConcatWithConstants",
                "(Ljava/lang/String;)Ljava/lang/String;", bsm, "[\u0001]");
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        cw.visitEnd();

        byte[] rewritten = StringBytecode.rewriteClass(cw.toByteArray());
        // The indy is gone, replaced by a call to a generated helper.
        List<String> calls = methodCalls(rewritten);
        assertFalse(calls.stream().anyMatch(c -> c.contains("StringConcatFactory")), "indy should be gone");
        Class<?> c = define("ConcatC", rewritten);
        Method f = c.getMethod("f", String.class);
        assertEquals("[hi]", f.invoke(null, "hi"));
        assertEquals("[]", f.invoke(null, ""));
    }

    @Test
    void record_equals_indy_is_desugared_to_working_bytecode() throws Exception {
        String name = Pt.class.getName();
        byte[] orig;
        try (var in = getClass().getClassLoader().getResourceAsStream(name.replace('.', '/') + ".class")) {
            orig = in.readAllBytes();
        }
        byte[] rewritten = StringBytecode.rewriteClass(orig);
        Class<?> pt = new ChildFirst(getClass().getClassLoader(), Map.of(name, rewritten)).loadClass(name);
        var ctor = pt.getDeclaredConstructor(int.class, int.class);
        ctor.setAccessible(true);
        Object a = ctor.newInstance(1, 2);
        Object b = ctor.newInstance(1, 2);
        Object c = ctor.newInstance(1, 3);
        Object d = ctor.newInstance(9, 2);
        Method eq = pt.getMethod("equals", Object.class);
        eq.setAccessible(true);
        assertTrue((boolean) eq.invoke(a, b), "equal records compare equal");
        assertFalse((boolean) eq.invoke(a, c), "differing second component");
        assertFalse((boolean) eq.invoke(a, d), "differing first component");
        assertFalse((boolean) eq.invoke(a, "not a Pt"), "non-record is not equal");
    }

    /**
     * A record with every primitive component, so the JVM-level test exercises each
     * {@code emitComponentHash} arithmetic branch (int/long/boolean/double/float). String/reference
     * components are NOT included here because their sound hash routes through
     * {@code CProverString.charAt}, which only has meaning inside JBMC (it returns '\0' on a real
     * JVM); the String-component dependency is checked in the BMC conformance proofs instead.
     */
    record Prims(int i, long l, boolean b, double d, float f) {
    }

    @Test
    void record_hashCode_indy_is_desugared_to_consistent_pure_function() throws Exception {
        // Property under test (the soundness contract): hashCode is a pure, deterministic function of
        // the components — equal records hash equal, repeated calls agree, and it varies with input.
        // We deliberately do NOT assert a specific magic value (the JDK leaves it unspecified).
        Class<?> prims = loadRewritten(Prims.class);
        var ctor = prims.getDeclaredConstructor(int.class, long.class, boolean.class, double.class, float.class);
        ctor.setAccessible(true);
        Method hc = prims.getMethod("hashCode");
        hc.setAccessible(true);

        Object a = ctor.newInstance(7, 99L, true, 3.5d, 1.25f);
        Object b = ctor.newInstance(7, 99L, true, 3.5d, 1.25f);   // equal components
        Object c = ctor.newInstance(8, 99L, true, 3.5d, 1.25f);   // differs in i
        Object dl = ctor.newInstance(7, 100L, true, 3.5d, 1.25f);  // differs in long
        Object e = ctor.newInstance(7, 99L, false, 3.5d, 1.25f);  // differs in boolean
        Object dd = ctor.newInstance(7, 99L, true, 4.5d, 1.25f);  // differs in double
        Object df = ctor.newInstance(7, 99L, true, 3.5d, 2.25f);  // differs in float

        int ha = (int) hc.invoke(a);
        assertEquals(ha, (int) hc.invoke(b), "equal records must have equal hashCode");
        assertEquals(ha, (int) hc.invoke(a), "hashCode must be consistent across calls");
        assertTrue(ha != (int) hc.invoke(c), "hashCode should depend on int component");
        assertTrue(ha != (int) hc.invoke(dl), "hashCode should depend on long component");
        assertTrue(ha != (int) hc.invoke(e), "hashCode should depend on boolean component");
        assertTrue(ha != (int) hc.invoke(dd), "hashCode should depend on double component");
        assertTrue(ha != (int) hc.invoke(df), "hashCode should depend on float component");
    }

    @Test
    void record_toString_indy_is_desugared_to_canonical_form() throws Exception {
        // For an all-primitive record the generated toString must equal javac's canonical
        // "Name[c1=v1, c2=v2]" exactly (no charAt involved, so this is meaningful on a real JVM).
        Class<?> prims = loadRewritten(Prims.class);
        var ctor = prims.getDeclaredConstructor(int.class, long.class, boolean.class, double.class, float.class);
        ctor.setAccessible(true);
        Object o = ctor.newInstance(7, 99L, true, 3.5d, 1.25f);
        Method ts = prims.getMethod("toString");
        ts.setAccessible(true);
        assertEquals("Prims[i=7, l=99, b=true, d=3.5, f=1.25]", ts.invoke(o));
    }

    @Test
    void record_toString_with_reference_component_is_left_alone() throws Exception {
        // A record with a non-String reference component cannot be rendered soundly (String.valueOf of
        // a reference is JBMC-nondet), so its toString indy is intentionally NOT desugared.
        byte[] rewritten = StringBytecode.rewriteClass(classBytes(WithRef.class));
        boolean[] sawToString = {false};
        new ClassReader(rewritten).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int a, String n, String d, String s, String[] ex) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitInvokeDynamicInsn(String name, String desc, Handle bsm, Object... args) {
                        if (bsm.getOwner().equals("java/lang/runtime/ObjectMethods") && name.equals("toString")) {
                            sawToString[0] = true;
                        }
                    }
                };
            }
        }, 0);
        assertTrue(sawToString[0], "toString indy with a non-String reference component must be left untouched");
    }

    /** A record whose component is a non-String reference, so toString stays un-desugared. */
    record WithRef(int n, Object ref) {
    }

    @Test
    void record_object_methods_indy_are_gone_for_all_primitive_record() throws Exception {
        // For an all-primitive record, all three ObjectMethods sites (equals/hashCode/toString) are
        // desugared away; no invokedynamic to ObjectMethods remains.
        byte[] rewritten = StringBytecode.rewriteClass(classBytes(Pt.class));
        java.util.List<String> remaining = new ArrayList<>();
        new ClassReader(rewritten).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int a, String n, String d, String s, String[] ex) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitInvokeDynamicInsn(String name, String desc, Handle bsm, Object... args) {
                        if (bsm.getOwner().equals("java/lang/runtime/ObjectMethods")) {
                            remaining.add(name);
                        }
                    }
                };
            }
        }, 0);
        assertTrue(remaining.isEmpty(),
                "all ObjectMethods sites (equals/hashCode/toString) must be desugared: " + remaining);
    }

    // ---- BmcStrings.contains must not throw a spurious CCE on a non-String CharSequence ----

    @Test
    void contains_with_String_needle_runs_without_a_classcast() {
        // The sound path: a String needle takes the char-loop. On a real JVM CProverString.charAt
        // returns '\0' (its meaning is only inside JBMC), so we don't assert content semantics here —
        // only that the shim runs and returns a boolean without throwing (the loop is unchanged by the
        // CharSequence-needle fix). Content soundness is covered end-to-end by the strings conformance proofs.
        assertDoesNotThrow(() -> BmcStrings.contains("haystack", "needle"));
    }

    @Test
    void contains_with_StringBuilder_needle_does_not_throw_classcast() {
        // The redirected descriptor is (CharSequence)Z, so s.contains(aStringBuilder) used to
        // hit `String n = (String) needle` and throw a ClassCastException INSIDE our own shim — a
        // spurious refutation pointing at bmc4j. After the fix a non-String CharSequence degrades
        // gracefully (routes through toString()) instead of crashing.
        StringBuilder needle = new StringBuilder("abc");
        assertDoesNotThrow(() -> BmcStrings.contains("xxabcxx", needle),
                "a StringBuilder needle must not throw a ClassCastException inside BmcStrings");
        // And it must still reject null per String.contains semantics.
        assertThrows(NullPointerException.class, () -> BmcStrings.contains("x", null));
    }

    private static byte[] classBytes(Class<?> c) throws Exception {
        String name = c.getName();
        try (var in = StringBytecodeTest.class.getClassLoader()
                .getResourceAsStream(name.replace('.', '/') + ".class")) {
            return in.readAllBytes();
        }
    }

    private static Class<?> loadRewritten(Class<?> c) throws Exception {
        String name = c.getName();
        byte[] rewritten = StringBytecode.rewriteClass(classBytes(c));
        return new ChildFirst(StringBytecodeTest.class.getClassLoader(), Map.of(name, rewritten)).loadClass(name);
    }

    private static Class<?> define(String name, byte[] bytes) {
        return new ChildFirst(StringBytecodeTest.class.getClassLoader(), Map.of(name, bytes)).defineNamed(name);
    }

    /** Loads the named classes from given bytes (child-first), delegating everything else to the parent. */
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

    /** A class C with a method that makes exactly the given call (args already on the stack as needed). */
    private static byte[] classCalling(int op, String owner, String name, String desc) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "C", null, "java/lang/Object", null);
        // static use(String a, String b) { a.<call>(...); }  (b loaded only for the 1-arg equals case)
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "use",
                "(Ljava/lang/String;Ljava/lang/String;)V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);                       // receiver
        if (!desc.startsWith("()")) {
            mv.visitVarInsn(Opcodes.ALOAD, 1);                  // the single (ref) argument, if any
        }
        mv.visitMethodInsn(op, owner, name, desc, false);
        mv.visitInsn(Opcodes.POP);                              // discard result
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    /** A class C with a method making exactly one INVOKEINTERFACE call to owner.name(arg). */
    private static byte[] classCallingInterface(String owner, String name, String desc) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "C", null, "java/lang/Object", null);
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "use",
                "(L" + owner + ";Ljava/lang/Object;)V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);                       // interface-typed receiver
        mv.visitVarInsn(Opcodes.ALOAD, 1);                       // the Object argument
        mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, owner, name, desc, true);
        mv.visitInsn(Opcodes.POP);                              // discard result
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
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
