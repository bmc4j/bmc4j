package org.bmc4j.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Path

/**
 * Exercises [Jbmc.exec]'s timeout + engine-error verdict mapping without needing a
 * real jbmc binary: we drive `exec` with stand-in subprocesses (the current JVM's `java`
 * launcher, always present) that sleep, exit with an engine-error code, or print malformed output.
 *
 * It is the unit-level proof that (a) a process exceeding its budget is killed and reported
 * UNKNOWN within ~the budget, and (b) a non-0/10 exit becomes UNKNOWN (undecided), never a refutation.
 */
internal class JbmcExecTimeoutTest {

    @Test
    fun process_exceeding_its_budget_is_killed_and_reported_UNKNOWN_within_budget() {
        // A child that sleeps far longer than the 1s budget. exec must kill its tree and return UNKNOWN.
        val start = System.nanoTime()
        val r = Jbmc.exec(buildSleepCommand(20), "pkg.T.proof", 1)
        val elapsedMs = (System.nanoTime() - start) / 1_000_000

        assertTrue(r.isUnknown, "a timed-out run must be UNKNOWN, got verdict " + r.verdict)
        assertFalse(r.isVerified)
        assertTrue(r.undecidedReason != null && r.undecidedReason!!.contains("timed out"),
                "reason should mention the timeout: " + r.undecidedReason)
        // Killed promptly: nowhere near the child's 20s sleep (generous ceiling for slow CI/JVM start).
        assertTrue(elapsedMs < 15_000, "should have been killed near the 1s budget, took ${elapsedMs}ms")
    }

    @Test
    fun engine_error_exit_is_UNKNOWN_not_a_refutation() {
        // Exit code 6 = neither 0 (verified) nor 10 (violation): an engine error -> UNKNOWN.
        val r = Jbmc.exec(buildExitCommand(6), "pkg.T.proof", 0)
        assertTrue(r.isUnknown, "engine-error exit must map to UNKNOWN, got " + r.verdict)
        assertTrue(r.isEngineCrash, "a non-verdict exit is structurally a crash")
        assertTrue(r.violations.isEmpty(), "UNKNOWN carries no counterexample")
        assertTrue(r.undecidedReason != null && r.undecidedReason!!.contains("6"),
                "reason should mention the exit code: " + r.undecidedReason)
    }

    @Test
    fun persistent_engine_crash_is_retried_once_then_UNKNOWN() {
        // A deterministic crash exit: exec retries once (two launches), then reports the crash
        // UNKNOWN with the persisted-across-a-retry note. Launch count proves the retry happened.
        val before = Jbmc.invocationCount()
        val r = Jbmc.exec(buildExitCommand(134), "pkg.T.proof", 0)
        assertTrue(r.isUnknown && r.isEngineCrash, "still a crash UNKNOWN after the retry")
        assertTrue(r.undecidedReason!!.contains("persisted across a retry"), r.undecidedReason)
        assertTrue(Jbmc.invocationCount() - before == 2L,
                "a crash must be retried exactly once (2 launches), saw " + (Jbmc.invocationCount() - before))
    }

    @Test
    fun nondeterministic_crash_recovers_on_the_retry() {
        // Crash-once-then-succeed, keyed on a state file: first launch exits 134 (creating the
        // marker), the retry sees the marker and exits 0 with (unparseable) output - the second
        // attempt's result is returned, NOT the crash. This is the jbmc-6.9.0 nondeterministic
        // internal-abort scenario the retry exists for.
        val state = File.createTempFile("bmc4j-crash-once", ".marker")
        assertTrue(state.delete(), "start without the marker")
        state.deleteOnExit()
        val path = state.absolutePath.replace("\\", "\\\\")
        val before = Jbmc.invocationCount()
        val r = Jbmc.exec(javaSource(
                "public class S { public static void main(String[] a) throws Exception {" +
                        " java.io.File f = new java.io.File(\"" + path + "\");" +
                        " if (f.createNewFile()) { System.exit(134); }" +
                        " System.out.println(\"recovered-not-json\"); System.exit(0); } }", "S"),
                "pkg.T.proof", 0)
        assertTrue(Jbmc.invocationCount() - before == 2L, "one crash + one retry = 2 launches")
        assertFalse(r.isEngineCrash,
                "the retry's clean exit must be the returned result, not the crash: " + r.undecidedReason)
    }

    @Test
    fun timeout_is_NOT_retried() {
        // The budget is the budget: a timed-out run is killed and reported once - no second spend.
        val before = Jbmc.invocationCount()
        val r = Jbmc.exec(buildSleepCommand(20), "pkg.T.proof", 1)
        assertTrue(r.isTimeout, "timeout stays a timeout")
        assertFalse(r.isEngineCrash, "a timeout is not a crash")
        assertTrue(Jbmc.invocationCount() - before == 1L, "timeouts must not retry")
    }

    @Test
    fun clean_exit_with_malformed_stdout_is_UNKNOWN() {
        // Exit 0 but garbage on stdout (not the json-ui array): the parser yields UNKNOWN.
        val r = Jbmc.exec(buildPrintThenExitCommand("not-json-at-all", 0), "pkg.T.proof", 0)
        assertTrue(r.isUnknown, "unparseable stdout must map to UNKNOWN, got " + r.verdict)
        assertEquals(UnknownKind.PARSE_FAILURE, r.undecidedKind, "unparseable -> PARSE_FAILURE")
    }

    // --- retryable-drives-retry (the generalized in-process retry) -------------------------------

    @Test
    fun a_retryable_PARSE_FAILURE_retries_once_then_stays_UNKNOWN_persisted() {
        // Exit 0 with garbage on stdout = PARSE_FAILURE, which is RETRYABLE. The deterministic garbage
        // reproduces on the retry, so exec runs the engine exactly twice and the result STAYS UNKNOWN
        // (never masked to a pass), annotated as persisted across the retry.
        val before = Jbmc.invocationCount()
        val r = Jbmc.exec(buildPrintThenExitCommand("not-json-at-all", 0), "pkg.T.proof", 0)
        assertEquals(2L, Jbmc.invocationCount() - before,
                "a retryable UNKNOWN must re-run the engine exactly once (2 launches)")
        assertTrue(r.isUnknown, "a recurring retryable kind STAYS UNKNOWN")
        assertEquals(UnknownKind.PARSE_FAILURE, r.undecidedKind)
        assertTrue(r.undecidedReason!!.contains("persisted across a retry"), r.undecidedReason)
        assertFalse(r.isVerified, "soundness: a persisted retryable UNKNOWN is NEVER promoted to VERIFIED")
    }

    @Test
    fun a_non_retryable_UNKNOWN_does_not_retry() {
        // A run with no reachability markers parses fine but yields SOLVER_GAVE_UP (markers missing),
        // which is NOT retryable: exec must NOT re-run it. One launch only.
        val markerless = "[{\\\"result\\\":[{\\\"property\\\":\\\"pkg.T.p.a\\\",\\\"status\\\":\\\"SUCCESS\\\"}]}]"
        val before = Jbmc.invocationCount()
        val r = Jbmc.exec(buildPrintThenExitCommand(markerless, 0), "pkg.T.proof", 0)
        assertTrue(r.isUnknown)
        assertEquals(UnknownKind.SOLVER_GAVE_UP, r.undecidedKind, "markers missing -> SOLVER_GAVE_UP")
        assertEquals(1L, Jbmc.invocationCount() - before,
                "a non-retryable UNKNOWN must NOT retry (1 launch)")
    }

    @Test
    fun a_retryable_UNKNOWN_that_recovers_a_real_verdict_keeps_the_better_outcome() {
        // First launch exits 134 (ENGINE_CRASH, retryable); the retry — keyed on a marker file —
        // exits 0 with a clean VERIFIED --json-ui array. exec must keep the RETRY's real verdict, not
        // the crash. (Proof that "keep the better verdict" promotes a recovered pass.)
        val state = File.createTempFile("bmc4j-retry-recover", ".marker")
        assertTrue(state.delete(), "start without the marker")
        state.deleteOnExit()
        val path = state.absolutePath.replace("\\", "\\\\")
        // A minimal --json-ui array with one reachable reachability marker -> VERIFIED.
        val markerLine = BmcReachability.SENTINEL_LINE
        val verifiedJson = ("[{\\\"result\\\":[{\\\"sourceLocation\\\":{\\\"line\\\":\\\"" + markerLine +
                "\\\"},\\\"status\\\":\\\"FAILURE\\\"}]}]")
        val before = Jbmc.invocationCount()
        val r = Jbmc.exec(javaSource(
                "public class S { public static void main(String[] a) throws Exception {" +
                        " java.io.File f = new java.io.File(\"" + path + "\");" +
                        " if (f.createNewFile()) { System.exit(134); }" +
                        " System.out.println(\"" + verifiedJson + "\"); System.exit(0); } }", "S"),
                "pkg.T.proof", 0)
        assertEquals(2L, Jbmc.invocationCount() - before, "one crash + one retry = 2 launches")
        assertFalse(r.isUnknown, "the retry recovered a real verdict: " + r.undecidedReason)
        assertTrue(r.isVerified, "the recovered VERIFIED is kept over the crash")
    }

    companion object {
        /** Path to the JVM's own `java` launcher — a subprocess we can always start here and in CI. */
        private fun javaBin(): String {
            val home = System.getProperty("java.home")
            val exe = if (System.getProperty("os.name", "").lowercase().contains("windows"))
                "java.exe" else "java"
            return Path.of(home, "bin", exe).toString()
        }

        // --- stand-in subprocesses via `java --source` single-file programs --------

        private fun buildSleepCommand(seconds: Int): List<String> {
            return javaSource(
                    "public class S { public static void main(String[] a) throws Exception {" +
                            " Thread.sleep(" + seconds + "000L); } }", "S")
        }

        private fun buildExitCommand(code: Int): List<String> {
            return javaSource(
                    "public class S { public static void main(String[] a) {" +
                            " System.exit(" + code + "); } }", "S")
        }

        private fun buildPrintThenExitCommand(text: String, code: Int): List<String> {
            return javaSource(
                    "public class S { public static void main(String[] a) {" +
                            " System.out.println(\"" + text + "\"); System.exit(" + code + "); } }", "S")
        }

        /** Write a single-file Java program to a temp file and return the `java --source 17 <file>` command. */
        private fun javaSource(source: String, className: String): List<String> {
            try {
                val f = File.createTempFile("bmc4j-exec-$className", ".java")
                f.deleteOnExit()
                java.nio.file.Files.writeString(f.toPath(), source)
                return listOf(javaBin(), "--source", "17", f.absolutePath)
            } catch (e: java.io.IOException) {
                throw RuntimeException(e)
            }
        }
    }
}
