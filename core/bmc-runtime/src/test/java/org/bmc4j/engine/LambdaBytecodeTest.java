package org.bmc4j.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;
import org.junit.jupiter.api.Test;

/**
 * Behavioral tests: run a real lambda-bearing class through {@link LambdaBytecode}, then load the
 * rewritten class plus its generated lambda classes and execute them — proving the desugaring
 * produces bytecode that both verifies and computes correctly, without needing the engine.
 */
class LambdaBytecodeTest {

    /** Fixtures with real javac-emitted LambdaMetafactory sites. */
    static final class Fix {
        static int capturing(int base) {
            IntUnaryOperator f = x -> x + base; // capturing lambda
            return f.applyAsInt(5);
        }

        static int staticRef() {
            IntUnaryOperator f = Fix::triple; // static method reference
            return f.applyAsInt(4);
        }

        static int instanceRef() {
            Function<String, Integer> len = String::length; // unbound instance ref + box/unbox
            return len.apply("abcde");
        }

        static int triple(int z) {
            return z * 3;
        }
    }

    @Test
    void desugars_to_loadable_correct_classes() throws Exception {
        String name = Fix.class.getName();
        byte[] orig;
        try (var in = getClass().getClassLoader().getResourceAsStream(name.replace('.', '/') + ".class")) {
            orig = in.readAllBytes();
        }
        LambdaBytecode.Result r = LambdaBytecode.transform(orig);

        // The indy sites are gone; one generated class per lambda site is produced.
        assertFalse(new String(r.main, java.nio.charset.StandardCharsets.ISO_8859_1).contains("LambdaMetafactory"),
                "metafactory bootstrap should be gone from the rewritten class");
        assertTrue(r.extra.size() >= 3, "a generated class per lambda site: " + r.extra.size());

        Map<String, byte[]> defs = new HashMap<>();
        defs.put(name, r.main);
        for (LambdaBytecode.GeneratedClass g : r.extra) {
            defs.put(g.internalName.replace('/', '.'), g.bytes);
        }
        ChildFirst cl = new ChildFirst(getClass().getClassLoader(), defs);
        Class<?> c = cl.loadClass(name);

        assertEquals(5, invoke(c, "capturing", 0));   // 5 + 0
        assertEquals(15, invoke(c, "capturing", 10)); // 5 + 10
        assertEquals(12, invokeNoArg(c, "staticRef")); // triple(4)
        assertEquals(5, invokeNoArg(c, "instanceRef")); // "abcde".length()
    }

    private static int invoke(Class<?> c, String m, int arg) throws Exception {
        Method method = c.getDeclaredMethod(m, int.class);
        method.setAccessible(true);
        return (int) method.invoke(null, arg);
    }

    private static int invokeNoArg(Class<?> c, String m) throws Exception {
        Method method = c.getDeclaredMethod(m);
        method.setAccessible(true);
        return (int) method.invoke(null);
    }

    /** Loads the named classes from bytes (child-first), delegating everything else to the parent. */
    private static final class ChildFirst extends ClassLoader {
        private final Map<String, byte[]> defs;

        ChildFirst(ClassLoader parent, Map<String, byte[]> defs) {
            super(parent);
            this.defs = defs;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (defs.containsKey(name)) {
                synchronized (getClassLoadingLock(name)) {
                    Class<?> c = findLoadedClass(name);
                    if (c == null) {
                        byte[] b = defs.get(name);
                        c = defineClass(name, b, 0, b.length);
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
