package org.bmc4j.engine;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@link Jbmc#exec}'s timeout + engine-error verdict mapping without needing a
 * real jbmc binary: we drive {@code exec} with stand-in subprocesses (the current JVM's {@code java}
 * launcher, always present) that sleep, exit with an engine-error code, or print malformed output.
 *
 * <p>It is the unit-level proof that (a) a process exceeding its budget is killed and reported
 * UNKNOWN within ~the budget, and (b) a non-0/10 exit becomes UNKNOWN (undecided), never a refutation.
 */
class JbmcExecTimeoutTest {

    /** Path to the JVM's own {@code java} launcher — a subprocess we can always start here and in CI. */
    private static String javaBin() {
        String home = System.getProperty("java.home");
        String exe = System.getProperty("os.name", "").toLowerCase().contains("windows")
                ? "java.exe" : "java";
        return Path.of(home, "bin", exe).toString();
    }

    @Test
    void process_exceeding_its_budget_is_killed_and_reported_UNKNOWN_within_budget() throws Exception {
        // A child that sleeps far longer than the 1s budget. exec must kill its tree and return UNKNOWN.
        long start = System.nanoTime();
        JbmcResult r = Jbmc.exec(buildSleepCommand(20), "pkg.T.proof", 1);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertTrue(r.isUnknown(), "a timed-out run must be UNKNOWN, got verdict " + r.verdict());
        assertFalse(r.isVerified());
        assertTrue(r.undecidedReason() != null && r.undecidedReason().contains("timed out"),
                "reason should mention the timeout: " + r.undecidedReason());
        // Killed promptly: nowhere near the child's 20s sleep (generous ceiling for slow CI/JVM start).
        assertTrue(elapsedMs < 15_000, "should have been killed near the 1s budget, took " + elapsedMs + "ms");
    }

    @Test
    void engine_error_exit_is_UNKNOWN_not_a_refutation() throws Exception {
        // Exit code 6 = neither 0 (verified) nor 10 (violation): an engine error -> UNKNOWN.
        JbmcResult r = Jbmc.exec(buildExitCommand(6), "pkg.T.proof", 0);
        assertTrue(r.isUnknown(), "engine-error exit must map to UNKNOWN, got " + r.verdict());
        assertTrue(r.violations().isEmpty(), "UNKNOWN carries no counterexample");
        assertTrue(r.undecidedReason() != null && r.undecidedReason().contains("6"),
                "reason should mention the exit code: " + r.undecidedReason());
    }

    @Test
    void clean_exit_with_malformed_stdout_is_UNKNOWN() throws Exception {
        // Exit 0 but garbage on stdout (not the json-ui array): the parser yields UNKNOWN.
        JbmcResult r = Jbmc.exec(buildPrintThenExitCommand("not-json-at-all", 0), "pkg.T.proof", 0);
        assertTrue(r.isUnknown(), "unparseable stdout must map to UNKNOWN, got " + r.verdict());
    }

    // --- stand-in subprocesses via `java --source` single-file programs --------

    private static List<String> buildSleepCommand(int seconds) {
        return javaSource(
                "public class S { public static void main(String[] a) throws Exception {"
                        + " Thread.sleep(" + seconds + "000L); } }", "S");
    }

    private static List<String> buildExitCommand(int code) {
        return javaSource(
                "public class S { public static void main(String[] a) {"
                        + " System.exit(" + code + "); } }", "S");
    }

    private static List<String> buildPrintThenExitCommand(String text, int code) {
        return javaSource(
                "public class S { public static void main(String[] a) {"
                        + " System.out.println(\"" + text + "\"); System.exit(" + code + "); } }", "S");
    }

    /** Write a single-file Java program to a temp file and return the `java --source 17 <file>` command. */
    private static List<String> javaSource(String source, String className) {
        try {
            File f = File.createTempFile("bmc4j-exec-" + className, ".java");
            f.deleteOnExit();
            java.nio.file.Files.writeString(f.toPath(), source);
            return List.of(javaBin(), "--source", "17", f.getAbsolutePath());
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }
}
