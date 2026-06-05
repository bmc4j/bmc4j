package org.bmc4j.contracts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Soundness regression pinning the rule: <b>a contract whose enforce-proof is NOT expected to
 * verify must not publish a reusable redirect.</b>
 *
 * <p>The {@code contract …} line in {@value org.bmc4j.engine.ContractManifest#RESOURCE} is what
 * {@code JbmcBackend.applyContracts} turns into a call-site {@link org.bmc4j.engine.ContractRewriter}
 * redirect — every other proof's calls of the method are rewritten to the stub, which
 * {@code assume(<ensures>)}. If the {@code @Ensures} is deliberately FALSE (the contract type marks
 * its enforce-proof {@code @ExpectEnforce(REFUTED)} precisely because the framework KNOWS it does not
 * hold), publishing that redirect would let any caller verify a property against a false summary — a
 * false green. So a non-VERIFIED contract must still get its {@code __BmcEnforce} proof (the
 * refutation is the demo), but it must NOT emit a {@code contract} redirect line.
 *
 * <p>This runs the real {@link ContractProcessor} in-process over a small contract type holding one
 * VERIFIED mirror and one {@code @ExpectEnforce(REFUTED)} mirror, then inspects the emitted manifest.
 */
class ContractProcessorRedirectTest {

    /** A production target with two static methods to contract. */
    private static final String TARGET_SRC = """
            package demo;
            public final class Calc {
                private Calc() {}
                public static int safe(int n)   { return n < 0 ? -n : n; }
                public static int bogus(int a, int b) { return a - b; }
            }
            """;

    /**
     * One genuine VERIFIED contract ({@code safe}) and one deliberately-false demo contract
     * ({@code bogus}) whose enforce-proof is expected to be REFUTED — so it must NOT be reusable.
     */
    private static final String CONTRACT_SRC = """
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
            """;

    @Test
    void verified_contract_publishes_a_redirect_but_a_refuted_demo_does_not(@TempDir Path out) throws Exception {
        List<String> manifest = processAndReadManifest(out);

        // Both enforce-proof classes are still generated: the refutation demo must keep running.
        assertTrue(generated(out, "demo/CalcContract__BmcEnforce.java"),
                "the enforce-proof class must be generated for BOTH contracts");

        List<String> contractLines = manifest.stream()
                .filter(l -> l.startsWith("contract "))
                .collect(Collectors.toList());

        boolean safeRedirect = contractLines.stream().anyMatch(l -> l.contains(" safe "));
        boolean bogusRedirect = contractLines.stream().anyMatch(l -> l.contains(" bogus "));

        // The VERIFIED contract is reusable -> it publishes a redirect.
        assertTrue(safeRedirect,
                "the VERIFIED contract 'safe' must publish a reusable redirect line; manifest: " + manifest);
        // The REFUTED-expected demo is NOT proven -> it must NOT publish a redirect. On buggy code
        // the redirect IS present, so this assertion FAILS (that is the demonstration).
        assertFalse(bogusRedirect,
                "a non-VERIFIED contract ('bogus', @ExpectEnforce(REFUTED)) must NOT be reusable as a "
                        + "trusted stub — publishing its redirect lets callers verify against a FALSE "
                        + "summary (false green). manifest: " + manifest);

        // And the enforce direction is untouched: the enforce-proof line for the type is still named.
        assertTrue(manifest.stream().anyMatch(l -> l.startsWith("enforce ")),
                "the enforce-proof class must still be named in the manifest; manifest: " + manifest);
    }

    // --- in-process annotation processing harness ---

    /** Compile the two sources with ContractProcessor and return the lines of the emitted manifest. */
    private static List<String> processAndReadManifest(Path out) throws IOException {
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        if (javac == null) {
            fail("no system Java compiler available (run on a JDK, not a JRE)");
        }
        try (StandardJavaFileManager fm = javac.getStandardFileManager(null, null, null)) {
            fm.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(out));
            fm.setLocationFromPaths(StandardLocation.SOURCE_OUTPUT, List.of(out));

            List<JavaFileObject> sources = List.of(
                    new StringSource("demo.Calc", TARGET_SRC),
                    new StringSource("demo.CalcContract", CONTRACT_SRC));

            JavaCompiler.CompilationTask task = javac.getTask(null, fm, null, null, null, sources);
            task.setProcessors(List.of(new ContractProcessor()));
            boolean ok = task.call();
            assertTrue(ok, "the generated stub/enforce sources and the demo sources must compile");

            Path manifest = out.resolve(org.bmc4j.engine.ContractManifest.RESOURCE);
            assertTrue(Files.isRegularFile(manifest),
                    "the processor must emit " + org.bmc4j.engine.ContractManifest.RESOURCE);
            return Files.readAllLines(manifest);
        }
    }

    private static boolean generated(Path out, String relativeSourcePath) {
        return Files.isRegularFile(out.resolve(relativeSourcePath));
    }

    /** An in-memory Java source file. */
    private static final class StringSource extends SimpleJavaFileObject {
        private final String code;

        StringSource(String fqn, String code) {
            super(URI.create("string:///" + fqn.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
            this.code = code;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return code;
        }
    }
}
