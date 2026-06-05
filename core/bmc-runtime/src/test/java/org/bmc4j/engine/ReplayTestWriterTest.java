package org.bmc4j.engine;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for the v2 replay-test writer: it must produce a self-contained file that
 * actually COMPILES (the validation checkbox), and it must be best-effort (never crash a refutation).
 */
class ReplayTestWriterTest {

    private static JbmcResult.Violation violation(JbmcResult.Binding... bindings) {
        return new JbmcResult.Violation("array bounds", "GradeBand.java", 16, List.of(),
                List.of(), List.of(bindings));
    }

    @Test
    void writes_a_compilable_test_file(@TempDir Path dir) throws Exception {
        System.setProperty("bmc.replayDir", dir.toString());
        try {
            String path = ReplayTestWriter.write("example.arraybounds.GradeBandProofTests.proof",
                    null, violation(new JbmcResult.Binding("score", "integer", "100")));
            assertNotNull(path, "writer should return the file path");
            Path file = Path.of(path);
            assertTrue(Files.exists(file));
            String src = Files.readString(file);
            assertTrue(src.contains("@Test"), src);
            assertTrue(src.contains("int score = 100;"), src);

            // The file must actually compile.
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            assertNotNull(compiler, "a JDK (not JRE) is required to run this test");
            int rc = compiler.run(null, null, null,
                    "-cp", System.getProperty("java.class.path"),
                    "-d", dir.toString(),
                    file.toString());
            assertTrue(rc == 0, "generated replay test should compile, rc=" + rc);
        } finally {
            System.clearProperty("bmc.replayDir");
        }
    }

    @Test
    void no_bindings_writes_nothing(@TempDir Path dir) {
        System.setProperty("bmc.replayDir", dir.toString());
        try {
            assertNull(ReplayTestWriter.write("pkg.C.proof", null, violation()));
        } finally {
            System.clearProperty("bmc.replayDir");
        }
    }

    @Test
    void renderSource_is_well_formed_for_a_primitive() {
        String src = ReplayTestWriter.renderSource("Foo_proofReplay", "pkg.Foo.proof", null,
                violation(new JbmcResult.Binding("n", "integer", "7")));
        assertTrue(src.contains("class Foo_proofReplay"), src);
        assertTrue(src.contains("void replay()"), src);
        assertTrue(src.contains("int n = 7;"), src);
        assertTrue(src.contains("// TODO"), src);
        // No leftover "replay:" framing or hint comment inside the method body.
        assertTrue(!src.contains("replay:"), src);
    }
}
