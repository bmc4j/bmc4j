package org.bmc4j.engine;

/**
 * Engine-agnostic description of one proof to verify. The extension builds this from
 * the {@code @BmcProof} method and hands it to a {@link VerificationBackend}; each
 * backend does its own engine-specific preparation (model classpaths, IR conversion,
 * bytecode rewrites) and invocation.
 */
public final class BmcRequest {

    private final String entryClass;
    private final String entryFunction;
    private final String classpath;
    private final int unwind;
    private final boolean unwindingAssertions;
    private final int maxStringLength;
    private final boolean concurrent;
    private final String solver;
    private final int timeoutSeconds;

    public BmcRequest(String entryClass, String entryFunction, String classpath,
                      int unwind, boolean unwindingAssertions, int maxStringLength,
                      boolean concurrent) {
        this(entryClass, entryFunction, classpath, unwind, unwindingAssertions, maxStringLength,
                concurrent, "");
    }

    public BmcRequest(String entryClass, String entryFunction, String classpath,
                      int unwind, boolean unwindingAssertions, int maxStringLength,
                      boolean concurrent, String solver) {
        this(entryClass, entryFunction, classpath, unwind, unwindingAssertions, maxStringLength,
                concurrent, solver, 0);
    }

    public BmcRequest(String entryClass, String entryFunction, String classpath,
                      int unwind, boolean unwindingAssertions, int maxStringLength,
                      boolean concurrent, String solver, int timeoutSeconds) {
        this.entryClass = entryClass;
        this.entryFunction = entryFunction;
        this.classpath = classpath;
        this.unwind = unwind;
        this.unwindingAssertions = unwindingAssertions;
        this.maxStringLength = maxStringLength;
        this.concurrent = concurrent;
        this.solver = solver == null ? "" : solver;
        this.timeoutSeconds = timeoutSeconds;
    }

    /** Fully-qualified class declaring the proof method. */
    public String entryClass() {
        return entryClass;
    }

    /** {@code Class.method} entry point. */
    public String entryFunction() {
        return entryFunction;
    }

    /** The compiled bytecode classpath of the test JVM ({@code java.class.path}). */
    public String classpath() {
        return classpath;
    }

    public int unwind() {
        return unwind;
    }

    public boolean unwindingAssertions() {
        return unwindingAssertions;
    }

    public int maxStringLength() {
        return maxStringLength;
    }

    /** Whether the proof opted into concurrency exploration. */
    public boolean concurrent() {
        return concurrent;
    }

    /** Per-proof SAT/SMT solver override (e.g. {@code "z3"}); empty = use {@code -Dbmc.solver}/default. */
    public String solver() {
        return solver;
    }

    /**
     * Per-proof wall-clock budget in seconds. When {@code > 0}, the engine process tree is
     * force-killed on expiry and the proof is reported {@link JbmcResult.Verdict#UNKNOWN UNKNOWN}.
     * {@code 0} means no timeout (run to completion).
     */
    public int timeoutSeconds() {
        return timeoutSeconds;
    }
}
