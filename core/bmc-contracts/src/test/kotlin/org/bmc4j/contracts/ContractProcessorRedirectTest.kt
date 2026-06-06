package org.bmc4j.contracts

import org.bmc4j.engine.ContractManifest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import javax.tools.JavaFileObject
import javax.tools.SimpleJavaFileObject
import javax.tools.StandardLocation
import javax.tools.ToolProvider

/**
 * Soundness regression pinning the rule: **a contract whose enforce-proof is NOT expected to
 * verify must not publish a reusable redirect.**
 *
 * The `contract …` line in [ContractManifest.RESOURCE] is what
 * `JbmcBackend.applyContracts` turns into a call-site `ContractRewriter`
 * redirect — every other proof's calls of the method are rewritten to the stub, which
 * `assume(<ensures>)`. If the `@Ensures` is deliberately FALSE (the contract type marks
 * its enforce-proof `@ExpectEnforce(REFUTED)` precisely because the framework KNOWS it does not
 * hold), publishing that redirect would let any caller verify a property against a false summary — a
 * false green. So a non-VERIFIED contract must still get its `__BmcEnforce` proof (the
 * refutation is the demo), but it must NOT emit a `contract` redirect line.
 *
 * This runs the real [ContractProcessor] in-process over a small contract type holding one
 * VERIFIED mirror and one `@ExpectEnforce(REFUTED)` mirror, then inspects the emitted manifest.
 */
class ContractProcessorRedirectTest {

    @Test
    fun verified_contract_publishes_a_redirect_but_a_refuted_demo_does_not(@TempDir out: Path) {
        val manifest = processAndReadManifest(out)

        // Both enforce-proof classes are still generated: the refutation demo must keep running.
        assertTrue(generated(out, "demo/CalcContract__BmcEnforce.java"),
                "the enforce-proof class must be generated for BOTH contracts")

        val contractLines = manifest.filter { it.startsWith("contract ") }

        val safeRedirect = contractLines.any { it.contains(" safe ") }
        val bogusRedirect = contractLines.any { it.contains(" bogus ") }

        // The VERIFIED contract is reusable -> it publishes a redirect.
        assertTrue(safeRedirect,
                "the VERIFIED contract 'safe' must publish a reusable redirect line; manifest: $manifest")
        // The REFUTED-expected demo is NOT proven -> it must NOT publish a redirect. On buggy code
        // the redirect IS present, so this assertion FAILS (that is the demonstration).
        assertFalse(bogusRedirect,
                "a non-VERIFIED contract ('bogus', @ExpectEnforce(REFUTED)) must NOT be reusable as a " +
                        "trusted stub — publishing its redirect lets callers verify against a FALSE " +
                        "summary (false green). manifest: $manifest")

        // And the enforce direction is untouched: the enforce-proof line for the type is still named.
        assertTrue(manifest.any { it.startsWith("enforce ") },
                "the enforce-proof class must still be named in the manifest; manifest: $manifest")
    }

    // --- in-process annotation processing harness ---

    /** Compile the two sources with ContractProcessor and return the lines of the emitted manifest. */
    private fun processAndReadManifest(out: Path): List<String> {
        val javac = ToolProvider.getSystemJavaCompiler()
                ?: fail("no system Java compiler available (run on a JDK, not a JRE)")
        javac.getStandardFileManager(null, null, null).use { fm ->
            fm.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, listOf(out))
            fm.setLocationFromPaths(StandardLocation.SOURCE_OUTPUT, listOf(out))

            val sources = listOf<JavaFileObject>(
                    StringSource("demo.Calc", TARGET_SRC),
                    StringSource("demo.CalcContract", CONTRACT_SRC))

            val task = javac.getTask(null, fm, null, null, null, sources)
            task.setProcessors(listOf(ContractProcessor()))
            assertTrue(task.call(), "the generated stub/enforce sources and the demo sources must compile")

            val manifest = out.resolve(ContractManifest.RESOURCE)
            assertTrue(Files.isRegularFile(manifest),
                    "the processor must emit ${ContractManifest.RESOURCE}")
            return Files.readAllLines(manifest)
        }
    }

    private fun generated(out: Path, relativeSourcePath: String): Boolean =
            Files.isRegularFile(out.resolve(relativeSourcePath))

    /** An in-memory Java source file. */
    private class StringSource(fqn: String, private val code: String) : SimpleJavaFileObject(
            URI.create("string:///" + fqn.replace('.', '/') + JavaFileObject.Kind.SOURCE.extension),
            JavaFileObject.Kind.SOURCE) {

        override fun getCharContent(ignoreEncodingErrors: Boolean): CharSequence = code
    }

    private companion object {

        /** A production target with two static methods to contract. */
        val TARGET_SRC = """
            package demo;
            public final class Calc {
                private Calc() {}
                public static int safe(int n)   { return n < 0 ? -n : n; }
                public static int bogus(int a, int b) { return a - b; }
            }
            """.trimIndent()

        /**
         * One genuine VERIFIED contract (`safe`) and one deliberately-false demo contract
         * (`bogus`) whose enforce-proof is expected to be REFUTED — so it must NOT be reusable.
         */
        val CONTRACT_SRC = """
            package demo;
            import org.bmc4j.BmcContractsFor;
            import org.bmc4j.Ensures;
            import org.bmc4j.ExpectEnforce;
            import org.bmc4j.Requires;
            import org.bmc4j.Verdict;

            @BmcContractsFor(Calc.class)
            interface CalcContract {
                // genuine: |n| really is non-negative -> enforce verifies -> safe to reuse.
                @Requires("bounded") @Ensures("nonNeg") int safe(int n);

                // FALSE on purpose: a - b is negative whenever a < b. Its enforce-proof is
                // EXPECTED to be refuted, so it must NOT publish a reusable redirect.
                @ExpectEnforce(Verdict.REFUTED)
                @Requires("bounded2") @Ensures("nonNeg2") int bogus(int a, int b);

                static boolean bounded(int n)            { return n >= 0 && n <= 1000; }
                static boolean nonNeg(int result, int n) { return result >= 0; }
                static boolean bounded2(int a, int b)    { return a >= 0 && b >= 0; }
                static boolean nonNeg2(int result, int a, int b) { return result >= 0; }
            }
            """.trimIndent()
    }
}
