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

    // --- language selection ---------------------------------------------------

    /** A Kotlin class (carries kotlin.Metadata) — the auto-detection target. */
    internal class KotlinProofHolder {
        fun proof() {}
    }

    @Test
    fun auto_emits_kotlin_for_a_kotlin_proof_class(@TempDir dir: Path) {
        System.setProperty("bmc.replayDir", dir.toString())
        try {
            val m = KotlinProofHolder::class.java.getDeclaredMethod("proof")
            val path = ReplayTestWriter.write(
                    "org.bmc4j.engine.ReplayTestWriterTest\$KotlinProofHolder.proof", m,
                    violation(JbmcResult.Binding("score", "integer", "100")))
            assertNotNull(path)
            assertTrue(path!!.endsWith(".kt"), path)
            val src = Files.readString(Path.of(path))
            assertTrue(src.contains("fun replay()"), src)
            assertTrue(src.contains("val score = 100"), src)
            assertTrue(src.contains("src/test/kotlin"), src)
        } finally {
            System.clearProperty("bmc.replayDir")
        }
    }

    @Test
    fun auto_emits_java_for_a_java_proof_class(@TempDir dir: Path) {
        System.setProperty("bmc.replayDir", dir.toString())
        try {
            // JavaProofFixture is a plain Java class (no kotlin.Metadata) -> .java by default.
            val m = JavaProofFixture::class.java.getDeclaredMethod("proof")
            val path = ReplayTestWriter.write("pkg.JavaProofFixture.proof", m,
                    violation(JbmcResult.Binding("score", "integer", "100")))
            assertNotNull(path)
            assertTrue(path!!.endsWith(".java"), path)
        } finally {
            System.clearProperty("bmc.replayDir")
        }
    }

    @Test
    fun force_java_overrides_a_kotlin_proof_class(@TempDir dir: Path) {
        System.setProperty("bmc.replayDir", dir.toString())
        System.setProperty("bmc.replayLanguage", "java")
        try {
            val m = KotlinProofHolder::class.java.getDeclaredMethod("proof")
            val path = ReplayTestWriter.write(
                    "org.bmc4j.engine.ReplayTestWriterTest\$KotlinProofHolder.proof", m,
                    violation(JbmcResult.Binding("score", "integer", "100")))
            assertNotNull(path)
            assertTrue(path!!.endsWith(".java"), path)
        } finally {
            System.clearProperty("bmc.replayDir")
            System.clearProperty("bmc.replayLanguage")
        }
    }

    @Test
    fun force_kotlin_overrides_a_java_proof_class(@TempDir dir: Path) {
        System.setProperty("bmc.replayDir", dir.toString())
        System.setProperty("bmc.replayLanguage", "kotlin")
        try {
            val m = JavaProofFixture::class.java.getDeclaredMethod("proof")
            val path = ReplayTestWriter.write("pkg.JavaProofFixture.proof", m,
                    violation(JbmcResult.Binding("score", "integer", "100")))
            assertNotNull(path)
            assertTrue(path!!.endsWith(".kt"), path)
        } finally {
            System.clearProperty("bmc.replayDir")
            System.clearProperty("bmc.replayLanguage")
        }
    }

    // --- Kotlin file shape & backtick names -----------------------------------

    @Test
    fun kotlin_renderSource_shape_is_well_formed() {
        val src = ReplayTestWriter.renderSource("Foo_proofReplay", "pkg.Foo.proof", null,
                violation(JbmcResult.Binding("n", "integer", "7")),
                ReplayRenderer.Language.KOTLIN)
        assertTrue(src.contains("class Foo_proofReplay"), src)
        assertTrue(src.contains("fun replay()"), src)
        assertTrue(src.contains("val n = 7"), src)
        assertTrue(src.contains("// TODO"), src)
        // No Java semicolons in declarations, no "replay:" framing.
        assertTrue(!src.contains("int n = 7;"), src)
        assertTrue(!src.contains("replay:"), src)
    }

    @Test
    fun kotlin_degraded_binding_renders_as_comment(@TempDir dir: Path) {
        System.setProperty("bmc.replayDir", dir.toString())
        System.setProperty("bmc.replayLanguage", "kotlin")
        try {
            val path = ReplayTestWriter.write("pkg.C.proof", null,
                    violation(JbmcResult.Binding("obj", "pointer", "0x1")))
            assertNotNull(path)
            val src = Files.readString(Path.of(path))
            assertTrue(src.contains("// obj:"), src)
            assertTrue(!src.contains("val obj"), src)
        } finally {
            System.clearProperty("bmc.replayDir")
            System.clearProperty("bmc.replayLanguage")
        }
    }

    @Test
    fun backtick_named_kotlin_proof_yields_a_plain_identifier_filename(@TempDir dir: Path) {
        System.setProperty("bmc.replayDir", dir.toString())
        System.setProperty("bmc.replayLanguage", "kotlin")
        try {
            // A Kotlin proof method named `clamp is in bounds` carries the spaces in its JVM name.
            val path = ReplayTestWriter.write("pkg.Clamp.clamp is in bounds", null,
                    violation(JbmcResult.Binding("x", "integer", "5")))
            assertNotNull(path)
            val file = Path.of(path)
            // File name must be a plain identifier — no spaces or backticks.
            val name = file.fileName.toString()
            assertTrue(name == "Clamp_clamp_is_in_boundsReplay.kt", name)
            val src = Files.readString(file)
            assertTrue(src.contains("class Clamp_clamp_is_in_boundsReplay"), src)
            // The original (space-bearing) method name appears backtick-quoted in the call hint.
            assertTrue(src.contains("`clamp is in bounds`(...)"), src)
        } finally {
            System.clearProperty("bmc.replayDir")
            System.clearProperty("bmc.replayLanguage")
        }
    }

    // --- the generated Kotlin file actually compiles --------------------------

    @Test
    fun writes_a_compilable_kotlin_test_file(@TempDir dir: Path) {
        System.setProperty("bmc.replayDir", dir.toString())
        System.setProperty("bmc.replayLanguage", "kotlin")
        try {
            val path = ReplayTestWriter.write("pkg.GradeBand.proof", null,
                    violation(
                            JbmcResult.Binding("score", "integer", "100"),
                            JbmcResult.Binding("region", "string", "eu\$west")))
            assertNotNull(path, "writer should return the file path")
            val file = Path.of(path)
            assertTrue(Files.exists(file))
            val src = Files.readString(file)
            assertTrue(src.contains("@Test"), src)
            assertTrue(src.contains("val score = 100"), src)
            // The $ in the string was escaped, so it is a literal, not a template.
            assertTrue(src.contains("\\\$west"), src)

            assertTrue(KotlinReplayCompiler.compiles(file, dir),
                    "generated Kotlin replay test should compile:\n$src")
        } finally {
            System.clearProperty("bmc.replayDir")
            System.clearProperty("bmc.replayLanguage")
        }
    }

    companion object {
        private fun violation(vararg bindings: JbmcResult.Binding): JbmcResult.Violation {
            return JbmcResult.Violation("array bounds", "GradeBand.java", 16, listOf(),
                    listOf(), listOf(*bindings))
        }
    }
}
