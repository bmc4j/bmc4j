package org.bmc4j.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets

/**
 * Behavioral tests: run a real lambda-bearing class through [LambdaBytecode], then load the
 * rewritten class plus its generated lambda classes and execute them — proving the desugaring
 * produces bytecode that both verifies and computes correctly, without needing the engine.
 *
 * The lambda fixtures live in the Java [LambdaBytecodeTestFixtures] (javac's actual
 * LambdaMetafactory sites — see that file's javadoc for why they cannot be Kotlin).
 */
internal class LambdaBytecodeTest {

    @Test
    fun desugars_to_loadable_correct_classes() {
        val name = LambdaBytecodeTestFixtures.Fix::class.java.name
        val orig: ByteArray
        javaClass.classLoader.getResourceAsStream(name.replace('.', '/') + ".class").use { input ->
            orig = input!!.readAllBytes()
        }
        val r = LambdaBytecode.transform(orig)

        // The indy sites are gone; one generated class per lambda site is produced.
        assertFalse(String(r.main, StandardCharsets.ISO_8859_1).contains("LambdaMetafactory"),
                "metafactory bootstrap should be gone from the rewritten class")
        assertTrue(r.extra.size >= 3, "a generated class per lambda site: " + r.extra.size)

        val defs = HashMap<String, ByteArray>()
        defs[name] = r.main
        for (g in r.extra) {
            defs[g.internalName.replace('/', '.')] = g.bytes
        }
        val cl = ChildFirst(javaClass.classLoader, defs)
        val c = cl.loadClass(name)

        assertEquals(5, invoke(c, "capturing", 0))   // 5 + 0
        assertEquals(15, invoke(c, "capturing", 10)) // 5 + 10
        assertEquals(12, invokeNoArg(c, "staticRef")) // triple(4)
        assertEquals(5, invokeNoArg(c, "instanceRef")) // "abcde".length()
    }

    companion object {
        private fun invoke(c: Class<*>, m: String, arg: Int): Int {
            val method = c.getDeclaredMethod(m, Int::class.javaPrimitiveType)
            method.isAccessible = true
            return method.invoke(null, arg) as Int
        }

        private fun invokeNoArg(c: Class<*>, m: String): Int {
            val method = c.getDeclaredMethod(m)
            method.isAccessible = true
            return method.invoke(null) as Int
        }
    }

    /** Loads the named classes from bytes (child-first), delegating everything else to the parent. */
    private class ChildFirst(parent: ClassLoader, private val defs: Map<String, ByteArray>) :
            ClassLoader(parent) {

        @Throws(ClassNotFoundException::class)
        override fun loadClass(name: String, resolve: Boolean): Class<*> {
            if (defs.containsKey(name)) {
                synchronized(getClassLoadingLock(name)) {
                    var c = findLoadedClass(name)
                    if (c == null) {
                        val b = defs[name]!!
                        c = defineClass(name, b, 0, b.size)
                    }
                    if (resolve) {
                        resolveClass(c)
                    }
                    return c
                }
            }
            return super.loadClass(name, resolve)
        }
    }
}
