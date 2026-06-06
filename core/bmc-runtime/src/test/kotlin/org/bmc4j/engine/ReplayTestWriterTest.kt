package org.bmc4j.engine

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import javax.tools.ToolProvider

/**
 * Tests for the v2 replay-test writer: it must produce a self-contained file that
 * actually COMPILES (the validation checkbox), and it must be best-effort (never crash a refutation).
 */
internal class ReplayTestWriterTest {

    @Test
    fun writes_a_compilable_test_file(@TempDir dir: Path) {
        System.setProperty("bmc.replayDir", dir.toString())
        try {
            val path = ReplayTestWriter.write("example.arraybounds.GradeBandProofTests.proof",
                    null, violation(JbmcResult.Binding("score", "integer", "100")))
            assertNotNull(path, "writer should return the file path")
            val file = Path.of(path)
            assertTrue(Files.exists(file))
            val src = Files.readString(file)
            assertTrue(src.contains("@Test"), src)
            assertTrue(src.contains("int score = 100;"), src)

            // The file must actually compile.
            val compiler = ToolProvider.getSystemJavaCompiler()
            assertNotNull(compiler, "a JDK (not JRE) is required to run this test")
            val rc = compiler.run(null, null, null,
                    "-cp", System.getProperty("java.class.path"),
                    "-d", dir.toString(),
                    file.toString())
            assertTrue(rc == 0, "generated replay test should compile, rc=$rc")
        } finally {
            System.clearProperty("bmc.replayDir")
        }
    }

    @Test
    fun no_bindings_writes_nothing(@TempDir dir: Path) {
        System.setProperty("bmc.replayDir", dir.toString())
        try {
            assertNull(ReplayTestWriter.write("pkg.C.proof", null, violation()))
        } finally {
            System.clearProperty("bmc.replayDir")
        }
    }

    @Test
    fun renderSource_is_well_formed_for_a_primitive() {
        val src = ReplayTestWriter.renderSource("Foo_proofReplay", "pkg.Foo.proof", null,
                violation(JbmcResult.Binding("n", "integer", "7")))
        assertTrue(src.contains("class Foo_proofReplay"), src)
        assertTrue(src.contains("void replay()"), src)
        assertTrue(src.contains("int n = 7;"), src)
        assertTrue(src.contains("// TODO"), src)
        // No leftover "replay:" framing or hint comment inside the method body.
        assertTrue(!src.contains("replay:"), src)
    }

    companion object {
        private fun violation(vararg bindings: JbmcResult.Binding): JbmcResult.Violation {
            return JbmcResult.Violation("array bounds", "GradeBand.java", 16, listOf(),
                    listOf(), listOf(*bindings))
        }
    }
}
