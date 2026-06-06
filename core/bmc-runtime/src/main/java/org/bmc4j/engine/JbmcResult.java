package org.bmc4j.engine;

import java.util.List;

/** Parsed outcome of a JBMC run. */
public final class JbmcResult {

    /**
     * Methods JBMC analyzed as <em>nondet stubs</em> in this run: callees it had no body
     * for in the reachable slice and therefore replaced with an unconstrained nondet result. Harvested
     * from the engine's "opaque symbol" messages and filtered to <em>signal</em> — bmc4j/core models and
     * JBMC-internal synthetics are dropped (see {@link StubFilter}). This is the raw <em>fact</em>; the
     * <em>policy</em> (footnote / acknowledge / strict-UNKNOWN) is applied later, so it is harvested on
     * every run regardless of verdict. Each entry is a fully-qualified {@code pkg.Class.method} name.
     */
    private final List<String> stubbedMethods;

    /**
     * The three-way verdict of a proof run. A proof is either {@code VERIFIED} (holds for
     * all inputs in the bound), {@code REFUTED} (a counterexample exists), or {@code UNKNOWN}
     * (undecided within budget — timeout, engine gave up / crashed, or unparseable output). VACUOUS
     * is carried as a flavour of {@code REFUTED} via {@link #isVacuous()} so existing
     * callers are unaffected.
     */
    public enum Verdict {
        VERIFIED,
        REFUTED,
        UNKNOWN
    }

    private final Verdict verdict;
    private final List<Violation> violations;
    private final String rawOutput;
    private final boolean vacuous;
    private final String undecidedReason;
    private final boolean timedOut;
    private final boolean engineCrash;

    public JbmcResult(boolean verified, List<Violation> violations, String rawOutput) {
        this(verified, violations, rawOutput, false);
    }

    public JbmcResult(boolean verified, List<Violation> violations, String rawOutput, boolean vacuous) {
        this(verified ? Verdict.VERIFIED : Verdict.REFUTED, violations, rawOutput, vacuous, null,
                false, false, List.of());
    }

    private JbmcResult(Verdict verdict, List<Violation> violations, String rawOutput, boolean vacuous,
                       String undecidedReason, boolean timedOut, boolean engineCrash,
                       List<String> stubbedMethods) {
        this.verdict = verdict;
        this.violations = violations;
        this.rawOutput = rawOutput;
        this.vacuous = vacuous;
        this.undecidedReason = undecidedReason;
        this.timedOut = timedOut;
        this.engineCrash = engineCrash;
        this.stubbedMethods = stubbedMethods == null ? List.of() : List.copyOf(stubbedMethods);
    }

    /**
     * An UNKNOWN result: the run was undecided within budget — JBMC neither verified nor
     * refuted the proof. {@code reason} is a short human-readable cause (e.g. {@code "engine exited
     * 6"}, unparseable output) folded into the failure message; {@link #violations()} is empty
     * (there is no counterexample) and {@link #isVerified()} is {@code false}. For a wall-clock
     * expiry use {@link #unknownTimeout} instead, so the structured fact survives — the
     * expected-verdict assertion distinguishes TIMEOUT from other unknowns.
     */
    public static JbmcResult unknown(String reason, String rawOutput) {
        return new JbmcResult(Verdict.UNKNOWN, List.of(), rawOutput, false, reason, false, false, List.of());
    }

    /**
     * An UNKNOWN result caused specifically by the per-proof wall-clock budget expiring (the
     * engine process tree was force-killed). Structurally flagged — not inferred from the reason
     * string — so {@code @BmcProof(expect = TIMEOUT)} can assert this exact outcome.
     */
    public static JbmcResult unknownTimeout(String reason, String rawOutput) {
        return new JbmcResult(Verdict.UNKNOWN, List.of(), rawOutput, false, reason, true, false, List.of());
    }

    /**
     * An UNKNOWN result caused by the engine process exiting with a non-verdict code (it crashed,
     * aborted on an internal invariant, or was OOM-killed — anything but the verified/violation
     * exits). Structurally flagged — not inferred from the reason string — so {@link Jbmc#exec} can
     * retry a crash exactly once: a crash is not a verdict, and jbmc 6.9.0 has rare
     * nondeterministic internal aborts.
     */
    public static JbmcResult unknownEngineCrash(String reason, String rawOutput) {
        return new JbmcResult(Verdict.UNKNOWN, List.of(), rawOutput, false, reason, false, true, List.of());
    }

    /** True if this UNKNOWN was caused by the per-proof wall-clock budget expiring. */
    public boolean isTimeout() {
        return timedOut;
    }

    /** True if this UNKNOWN was caused by a non-verdict engine exit (crash/abort), not a timeout. */
    public boolean isEngineCrash() {
        return engineCrash;
    }

    /**
     * Return a copy of this result carrying the harvested nondet-stub list. The verdict,
     * violations, and other fields are unchanged — stubs are a parallel <em>fact</em> attached after the
     * verdict is computed, judged later by policy. Returns {@code this} when the list is empty/unchanged.
     */
    public JbmcResult withStubbedMethods(List<String> stubs) {
        if (stubs == null || stubs.isEmpty()) {
            return this;
        }
        return new JbmcResult(verdict, violations, rawOutput, vacuous, undecidedReason, timedOut,
                engineCrash, stubs);
    }

    /**
     * Methods JBMC analyzed as nondet stubs in this run, filtered to signal. Empty when the
     * reachable slice was fully modeled. The <em>fact</em> only — the footnote / strict-UNKNOWN policy
     * is applied by the caller against the build's allowlist and strictness.
     */
    public List<String> stubbedMethods() {
        return stubbedMethods;
    }

    public Verdict verdict() {
        return verdict;
    }

    /** True if JBMC found no property violation within the bound. */
    public boolean isVerified() {
        return verdict == Verdict.VERIFIED;
    }

    /** True if the run was undecided within budget (timeout / engine gave up / unparseable). */
    public boolean isUnknown() {
        return verdict == Verdict.UNKNOWN;
    }

    /** Short cause of an UNKNOWN result (null otherwise). */
    public String undecidedReason() {
        return undecidedReason;
    }

    /**
     * True if the proof failed because its assumptions are <em>unsatisfiable</em> — every normal exit
     * was unreachable, so it verified over an empty input domain and checked nothing. When
     * set, {@link #isVerified()} is {@code false} and {@link #violations()} carries the dedicated
     * vacuity message ({@link BmcReachability#VACUOUS_MESSAGE}).
     */
    public boolean isVacuous() {
        return vacuous;
    }

    public List<Violation> violations() {
        return violations;
    }

    public String rawOutput() {
        return rawOutput;
    }

    /** A single refuted property, with enough detail to build a stack trace. */
    public static final class Violation {

        private final String description;
        private final String file;
        private final int line;
        private final List<StackTraceElement> stack;
        private final List<String> counterexample;
        private final List<Binding> bindings;

        public Violation(String description, String file, int line,
                         List<StackTraceElement> stack, List<String> counterexample) {
            this(description, file, line, stack, counterexample, List.of());
        }

        public Violation(String description, String file, int line,
                         List<StackTraceElement> stack, List<String> counterexample,
                         List<Binding> bindings) {
            this.description = description;
            this.file = file;
            this.line = line;
            this.stack = stack;
            this.counterexample = counterexample;
            this.bindings = bindings;
        }

        public String description() {
            return description;
        }

        public String file() {
            return file;
        }

        public int line() {
            return line;
        }

        /** Synthesized stack trace, innermost (violation site) first. */
        public List<StackTraceElement> stack() {
            return stack;
        }

        /** Human-readable input assignments that trigger the violation, e.g. {@code score = 100}. */
        public List<String> counterexample() {
            return counterexample;
        }

        /**
         * Structured form of the counterexample inputs: each proof-local symbolic input
         * with its JBMC value {@code kind} and raw {@code data}, used to render a replayable concrete
         * test. Empty when there are no reconstructible bindings. Parallel to {@link #counterexample()}
         * (which is the human-readable {@code name = value} display form).
         */
        public List<Binding> bindings() {
            return bindings;
        }
    }

    /**
     * One structured counterexample input: a proof-local symbolic variable, its JBMC
     * value {@code kind} (e.g. {@code "integer"}, {@code "boolean"}), and the raw {@code data} string
     * JBMC assigned it. The replay renderer maps this back to concrete Java.
     */
    public static final class Binding {
        private final String name;
        private final String kind;
        private final String data;

        public Binding(String name, String kind, String data) {
            this.name = name;
            this.kind = kind;
            this.data = data;
        }

        /** The proof-local variable name (e.g. {@code "score"}). */
        public String name() {
            return name;
        }

        /** The JBMC value kind (e.g. {@code "integer"}, {@code "boolean"}, {@code "float"}). */
        public String kind() {
            return kind;
        }

        /** The raw JBMC value data (e.g. {@code "100"}, {@code "true"}). */
        public String data() {
            return data;
        }
    }
}
