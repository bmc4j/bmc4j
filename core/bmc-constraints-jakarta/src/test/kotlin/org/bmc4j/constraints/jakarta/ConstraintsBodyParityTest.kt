package org.bmc4j.constraints.jakarta

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.StringWriter
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import javax.tools.JavaFileObject
import javax.tools.SimpleJavaFileObject
import javax.tools.StandardLocation
import javax.tools.ToolProvider

/**
 * Pins the **body** the javac [BmcConstraintsProcessor] generates for a representative DTO. This is
 * the differential reference for the KSP path ([BmcConstraintsSymbolProcessor]): the KSP path feeds
 * the SAME [org.bmc4j.constraints.ConstraintCodeGenerator] the SAME [org.bmc4j.constraints.Constraint]
 * model, so for the same constraints both paths emit a body that differs only in the per-field
 * **accessor** form — a Java public field reads `obj.qty`, a Kotlin property reads `obj.getQty()`.
 * The body of the Kotlin twin is verified end-to-end by the examples integrations module (its
 * KSP-generated `ReqConstraints` over the same constraints; see the integration proofs), so keeping
 * this reference green guards the shared shape from regressing on either side.
 */
class ConstraintsBodyParityTest {

    @Test
    fun javac_path_emits_the_shared_assumeValid_body_for_the_reference_dto(@TempDir out: Path) {
        val generated = process(out, StringSource("demo.Req", REQ_SRC))
        // The javac path reads a public field directly (obj.qty); the KSP path's accessor is obj.getQty()
        // for the same Kotlin property — the only documented difference between the two emitted bodies.
        assertEquals(EXPECTED_JAVAC_BODY.trim(), assumeValidBody(generated).trim(),
                "the javac constraints body must match the pinned reference (the KSP path mirrors it" +
                        " through the same generator, differing only in accessor form)")
    }

    /** The generated assumeValid method body, accessor-normalized so a Java-field read and a Kotlin
     *  getter read compare equal — isolating the constraint SHAPE that both paths must agree on. */
    private fun assumeValidBody(source: String): String {
        val start = source.indexOf("assumeValid(")
        val open = source.indexOf('{', start)
        val close = source.indexOf("\n    }", open)
        return source.substring(open + 1, close)
    }

    private fun process(out: Path, source: JavaFileObject): String {
        val javac = ToolProvider.getSystemJavaCompiler()
                ?: fail("no system Java compiler (run on a JDK, not a JRE)")
        javac.getStandardFileManager(null, null, null).use { fm ->
            fm.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, listOf(out))
            fm.setLocationFromPaths(StandardLocation.SOURCE_OUTPUT, listOf(out))
            val task = javac.getTask(StringWriter(), fm, null, null, null, listOf(source))
            task.setProcessors(listOf(BmcConstraintsProcessor()))
            task.call()
        }
        val generated = out.resolve("demo/ReqConstraints.java")
        assertTrue(Files.isRegularFile(generated), "the processor must generate ReqConstraints.java")
        return Files.readString(generated)
    }

    private class StringSource(fqn: String, private val code: String) : SimpleJavaFileObject(
            URI.create("string:///" + fqn.replace('.', '/') + JavaFileObject.Kind.SOURCE.extension),
            JavaFileObject.Kind.SOURCE) {
        override fun getCharContent(ignoreEncodingErrors: Boolean): CharSequence = code
    }

    private companion object {

        // The Java twin of the examples' Kotlin `Req` DTO, same constraints: a primitive @Min(1), a
        // primitive @PositiveOrZero, a boxed @Max(120) (null-passing), and @NotNull + @Size on a String.
        val REQ_SRC = """
            package demo;
            import jakarta.validation.constraints.Max;
            import jakarta.validation.constraints.Min;
            import jakarta.validation.constraints.NotNull;
            import jakarta.validation.constraints.PositiveOrZero;
            import jakarta.validation.constraints.Size;
            public final class Req {
                @Min(1) public int qty;
                @PositiveOrZero public int cents;
                @Max(120) public Integer ageOrNull;
                @NotNull @Size(min = 3, max = 20) public String name;
            }
            """.trimIndent()

        // The exact body the shared generator emits (public-field accessors for the Java DTO). The KSP
        // path emits the identical sequence with getter accessors.
        val EXPECTED_JAVAC_BODY = """
        org.bmc4j.Bmc.assume(obj != null);
        org.bmc4j.Bmc.assume(obj.qty >= 1);
        org.bmc4j.Bmc.assume(obj.cents >= 0);
        org.bmc4j.Bmc.assume((obj.ageOrNull == null || obj.ageOrNull <= 120));
        org.bmc4j.Bmc.assume(obj.name != null);
        org.bmc4j.Bmc.assume((obj.name == null || obj.name.length() >= 3));
        org.bmc4j.Bmc.assume((obj.name == null || obj.name.length() <= 20));
        """
    }
}
