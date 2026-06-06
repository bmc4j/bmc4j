package org.bmc4j.engine;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Builds and runs a JBMC process against a single proof entry point. */
public final class Jbmc {

    private final String executable;

    /**
     * Every live JBMC process, so it — and any child solver it spawned (e.g. z3 via {@code --z3}) —
     * can be force-killed if the test run is cancelled or the JVM shuts down. Without this, stopping
     * a Gradle task leaves orphaned {@code jbmc.exe}/{@code z3.exe} processes burning CPU.
     */
    private static final Set<Process> RUNNING = ConcurrentHashMap.newKeySet();

    /**
     * Count of actual jbmc process launches in this JVM: every engine invocation goes
     * through {@link #exec}, so this is the ground truth for "did the verdict cache skip the engine?".
     * A cache hit returns before {@code exec}, so a second unchanged run should add zero here for the
     * cached proofs. Exposed via {@link #invocationCount()} for tests/diagnostics.
     */
    private static final java.util.concurrent.atomic.AtomicLong INVOCATIONS =
            new java.util.concurrent.atomic.AtomicLong();

    /** Number of jbmc processes launched in this JVM (a cache hit never increments this). */
    public static long invocationCount() {
        return INVOCATIONS.get();
    }

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            for (Process p : RUNNING) {
                killTree(p);
            }
        }, "bmc-jbmc-reaper"));
    }

    public Jbmc(String executable) {
        this.executable = executable;
    }

    private static void killTree(Process p) {
        try {
            p.descendants().forEach(ProcessHandle::destroyForcibly); // jbmc's children (e.g. z3) first
        } catch (RuntimeException ignored) {
            // best effort
        }
        p.destroyForcibly();
    }

    /**
     * Run JBMC with the given entry function and classpath.
     *
     * @param entryClass    fully qualified class containing the proof method
     * @param entryFunction {@code Class.method} entry point
     * @param classpath     classpath JBMC loads the bytecode from
     * @param unwind        loop unwinding bound
     * @param unwindingAssertions add --unwinding-assertions to flag insufficient bounds
     * @param maxStringLength bound on nondeterministic (input) string length; ignored if &lt;= 0
     * @param concurrent      explore thread interleavings (--java-threading)
     */
    public JbmcResult run(String entryClass, String entryFunction, String classpath,
                          int unwind, boolean unwindingAssertions, int maxStringLength,
                          boolean concurrent, String solver) {
        return run(entryClass, entryFunction, classpath, unwind, unwindingAssertions, maxStringLength,
                concurrent, solver, 0);
    }

    public JbmcResult run(String entryClass, String entryFunction, String classpath,
                          int unwind, boolean unwindingAssertions, int maxStringLength,
                          boolean concurrent, String solver, int timeoutSeconds) {
        preflightSolver(solver); // fail clearly now if a requested external solver isn't available
        List<String> command = new ArrayList<>();
        command.add(executable);
        command.addAll(args(entryClass, entryFunction, classpath, unwind, unwindingAssertions,
                maxStringLength, concurrent, solver));
        return exec(command, entryFunction, timeoutSeconds);
    }

    /** The JBMC argument list — everything after the executable. */
    static List<String> args(String entryClass, String entryFunction, String classpath,
                             int unwind, boolean unwindingAssertions, int maxStringLength,
                             boolean concurrent, String solver) {
        List<String> cmd = new ArrayList<>();
        cmd.add(entryClass);
        cmd.add("--classpath");
        cmd.add(classpath);
        cmd.add("--function");
        cmd.add(entryFunction);
        cmd.add("--unwind");
        cmd.add(Integer.toString(unwind));
        if (unwindingAssertions) {
            cmd.add("--unwinding-assertions");
        }
        if (maxStringLength > 0) {
            cmd.add("--max-nondet-string-length");
            cmd.add(Integer.toString(maxStringLength));
        }
        if (concurrent) {
            cmd.add("--java-threading");
        }
        addSolver(cmd, solver);
        cmd.add("--json-ui");
        cmd.add("--trace");
        // Bump verbosity so the engine emits its "opaque symbol" messages — the nondet-stub fact we
        // harvest per proof. At the default level those messages are suppressed; level 10
        // surfaces them in the same --json-ui STATUS-MESSAGE stream. The extra messages are ignored by
        // the verdict logic (only the result array + cProverStatus drive the verdict), so this changes
        // what we can OBSERVE, not the verdict.
        cmd.add("--verbosity");
        cmd.add("10");
        return cmd;
    }

    /**
     * Select the SAT/SMT backend. A per-proof {@code override} (from {@code @BmcProof(solver=…)})
     * wins; otherwise {@code -Dbmc.solver} (default = JBMC's built-in MiniSat). SMT backends
     * (z3/boolector/cvc4/cvc5) can be much faster on array/bitvector-heavy formulas; any other value
     * is passed to {@code --sat-solver} (e.g. cadical, glucose). {@code -Dbmc.solverCmd} points at an
     * external SMT2 solver binary (used with {@code --smt2}).
     */
    private static void addSolver(List<String> cmd, String override) {
        // External SAT solver (e.g. CryptoMiniSat) via DIMACS — the ONE solver swap that actually
        // works on jbmc: it bit-blasts to CNF and hands it to a modern SAT solver, bypassing string
        // refinement (so numeric/boolean proofs only). jbmc's --z3/--smt2 path can't be used (it
        // crashes converting Java string types to SMT2). Configured globally via bmc.externalSat.
        String externalSat = System.getProperty("bmc.externalSat");
        if (externalSat != null && !externalSat.isBlank()) {
            cmd.add("--external-sat-solver");
            cmd.add(externalSat.trim());
            return;
        }
        String external = System.getProperty("bmc.solverCmd");
        if (external != null && !external.isBlank()) {
            cmd.add("--smt2");
            cmd.add("--external-smt2-solver");
            cmd.add(external.trim());
            return;
        }
        String solver = (override != null && !override.isBlank())
                ? override : System.getProperty("bmc.solver");
        if (solver == null || solver.isBlank()) {
            return; // default: built-in MiniSat
        }
        switch (solver.trim().toLowerCase()) {
            case "minisat":
            case "minisat2":
                return;
            case "z3":
                cmd.add("--z3");
                break;
            case "boolector":
                cmd.add("--boolector");
                break;
            case "cvc4":
                cmd.add("--cvc4");
                break;
            case "cvc5":
                cmd.add("--cvc5");
                break;
            default:
                cmd.add("--sat-solver");
                cmd.add(solver.trim());
        }
    }

    static JbmcResult exec(List<String> command, String entryFunction) {
        return exec(command, entryFunction, 0);
    }

    /**
     * Run a fully-built {@code jbmc ...} command and parse its {@code --json-ui} output. Exit 0 =
     * verified, 10 = violation; any other exit is an engine error → {@link JbmcResult.Verdict#UNKNOWN
     * UNKNOWN} (the run was undecided, not a refutation). If {@code timeoutSeconds > 0} and the process
     * doesn't finish in time, its whole tree is force-killed (the solver is a child of jbmc) and the
     * result is UNKNOWN with a timeout reason.
     *
     * <p><b>Crash-class exits are retried ONCE.</b> An engine-error exit (anything other than the two
     * verdict exits) is a process that fell over, not a verdict — and jbmc 6.9.0 has rare
     * NONDETERMINISTIC internal aborts (observed in CI: an {@code Invariant check failed} in
     * {@code create_parameter_names} during mid-symex lazy conversion, exit 134, on a proof that
     * passes identically before and after). Re-running a crashed process is sound: a deterministic
     * crash just fails twice into the same UNKNOWN, a nondeterministic one recovers a real verdict
     * instead of failing the gate. The retry is LOUD (printed), never silent; timeouts are NOT
     * retried (the budget is the budget), and each attempt counts as a real engine launch.
     */
    static JbmcResult exec(List<String> command, String entryFunction, int timeoutSeconds) {
        JbmcResult first = execOnce(command, entryFunction, timeoutSeconds);
        if (!first.isEngineCrash()) {
            return first;
        }
        System.out.println("  bmc4j: engine crashed on " + entryFunction
                + " - retrying once (a crash is not a verdict)");
        JbmcResult second = execOnce(command, entryFunction, timeoutSeconds);
        if (second.isEngineCrash()) {
            return JbmcResult.unknownEngineCrash(
                    second.undecidedReason() + "\n    (the crash persisted across a retry)",
                    second.rawOutput());
        }
        return second;
    }

    private static JbmcResult execOnce(List<String> command, String entryFunction, int timeoutSeconds) {
        INVOCATIONS.incrementAndGet(); // ground-truth engine-launch counter for the verdict cache
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(false);
        applySolverPath(pb); // so jbmc's --z3 (etc.) finds the solver even if it's not on global PATH
        Process p = null;
        try {
            p = pb.start();
            RUNNING.add(p);
            // Drain both streams on background threads so a full pipe buffer can't deadlock the
            // process while we're blocked in waitFor(timeout) (a single-threaded readAllBytes would
            // also ignore the timeout entirely — it blocks until EOF, i.e. until the process exits).
            StreamGobbler out = new StreamGobbler(p.getInputStream());
            StreamGobbler err = new StreamGobbler(p.getErrorStream());
            out.start();
            err.start();

            boolean finished;
            if (timeoutSeconds > 0) {
                finished = p.waitFor(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS);
            } else {
                p.waitFor();
                finished = true;
            }
            if (!finished) {
                // Budget exhausted: kill jbmc AND its solver child.
                killTree(p);
                p.waitFor(); // reap so exitValue/streams settle
                out.join();
                err.join();
                return JbmcResult.unknownTimeout("timed out after " + timeoutSeconds + "s", out.text());
            }
            out.join();
            err.join();
            int exit = p.exitValue();
            if (exit != 0 && exit != 10) {
                // Engine error (solver gave up / crashed / bad invocation). Undecided, not refuted:
                // there's no counterexample, so report UNKNOWN rather than a refutation. The detail
                // (exit code + stderr) is folded into the message for actionable diagnosis. Flagged
                // as a CRASH structurally so exec() retries it once.
                return JbmcResult.unknownEngineCrash(
                        engineErrorReason(command, exit, err.text(), out.text()), out.text());
            }
            return JbmcOutputParser.parse(out.text(), entryFunction);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not start JBMC process: " + String.join(" ", command), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (p != null) {
                killTree(p); // cancelled mid-run — don't leave jbmc (and its z3 child) orphaned
            }
            throw new IllegalStateException("Interrupted while running JBMC (process terminated)", e);
        } finally {
            if (p != null) {
                RUNNING.remove(p);
                if (p.isAlive()) {
                    killTree(p);
                }
            }
        }
    }

    /** Drains a process stream to a buffer on its own thread (so reads can't deadlock or block waitFor). */
    private static final class StreamGobbler extends Thread {
        private final InputStream in;
        private volatile String text = "";

        StreamGobbler(InputStream in) {
            super("bmc-jbmc-gobbler");
            setDaemon(true);
            this.in = in;
        }

        @Override
        public void run() {
            try {
                text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                // Stream closed by a kill mid-read — keep whatever was captured (possibly empty).
            }
        }

        String text() {
            return text;
        }
    }

    /** Build the UNKNOWN reason line for an engine error exit (solver gave up / crashed / bad args). */
    private static String engineErrorReason(List<String> command, int exit, String stderr, String stdout) {
        StringBuilder sb = new StringBuilder("JBMC exited with engine error code ").append(exit)
                .append(" (solver gave up or crashed)");
        String solverFlag = solverFlagIn(command);
        if (solverFlag != null) {
            sb.append("; a non-default solver (").append(solverFlag)
                    .append(") was in play — if it isn't installed/visible that's the likely cause");
        }
        String detail = (stderr != null && !stderr.isBlank()) ? stderr
                : (stdout != null ? stdout : "");
        detail = detail.strip();
        if (!detail.isEmpty()) {
            if (detail.length() > 600) {
                detail = detail.substring(0, 600) + " ...";
            }
            sb.append('\n').append(detail);
        }
        return sb.toString();
    }

    private static String solverFlagIn(List<String> command) {
        for (String a : command) {
            if (a.equals("--z3") || a.equals("--boolector") || a.equals("--cvc4") || a.equals("--cvc5")
                    || a.equals("--sat-solver") || a.equals("--external-smt2-solver")) {
                return a;
            }
        }
        return null;
    }

    /**
     * Fail clearly BEFORE launching jbmc if a requested external solver isn't available, instead of
     * letting jbmc fail with a cryptic error. The default (MiniSat, built into jbmc) needs nothing.
     */
    private static void preflightSolver(String override) {
        String solverCmd = System.getProperty("bmc.solverCmd");
        if (solverCmd != null && !solverCmd.isBlank()) {
            if (!new File(solverCmd.trim()).isFile()) {
                throw new IllegalStateException("bmc.solverCmd points at a file that does not exist: " + solverCmd.trim());
            }
            return;
        }
        String solver = (override != null && !override.isBlank()) ? override : System.getProperty("bmc.solver");
        if (solver == null || solver.isBlank()) {
            return; // built-in MiniSat
        }
        String name = solver.trim().toLowerCase();
        if (name.equals("minisat") || name.equals("minisat2") || solverBinaryOnPath(name)) {
            return;
        }
        String solverPath = System.getProperty("bmc.solverPath", "");
        throw new IllegalStateException(
                "SMT/SAT solver '" + solver.trim() + "' was requested (@BmcProof(solver=...) or -Dbmc.solver) but its"
                        + " binary was not found. Install it and put it on PATH, set bmc { solverPath = \"<dir>\" },"
                        + " or bmc { solverCmd = \"<full path>\" }."
                        + (solverPath.isBlank() ? "" : "  (bmc.solverPath=" + solverPath + ")"));
    }

    private static boolean solverBinaryOnPath(String name) {
        List<String> dirs = new ArrayList<>();
        String sp = System.getProperty("bmc.solverPath");
        if (sp != null && !sp.isBlank()) {
            dirs.addAll(List.of(sp.split(File.pathSeparator)));
        }
        String path = System.getenv("PATH");
        if (path == null) {
            path = System.getenv("Path"); // Windows
        }
        if (path != null) {
            dirs.addAll(List.of(path.split(File.pathSeparator)));
        }
        String[] candidates = { name, name + ".exe", name + ".bat", name + ".cmd" };
        for (String d : dirs) {
            if (d.isBlank()) {
                continue;
            }
            for (String c : candidates) {
                if (new File(d.trim(), c).isFile()) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Prepend {@code -Dbmc.solverPath} to the subprocess PATH so jbmc can find the solver binary. */
    private static void applySolverPath(ProcessBuilder pb) {
        String sp = System.getProperty("bmc.solverPath");
        if (sp == null || sp.isBlank()) {
            return;
        }
        Map<String, String> env = pb.environment();
        String key = "PATH";
        for (String k : env.keySet()) {
            if (k.equalsIgnoreCase("PATH")) { // Windows uses "Path"
                key = k;
                break;
            }
        }
        env.put(key, sp.trim() + File.pathSeparator + env.getOrDefault(key, ""));
    }
}
