package proofs.config;

import example.config.ServerConfig;
import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;
import org.bmc4j.Verdict;

/**
 * Proving against the config <b>this run was launched with</b>. {@code Bmc.intFromProperty("app.port")}
 * etc. are pinned by bmc4j to the real value (set in this module's build.gradle.kts) — so each proof
 * verifies the logic for that concrete config, not over all possible values. A required variable that
 * is unset fails the proof.
 */
class ConfigProofs {

    // PASS: the configured port (8080) is already in range, so clamping is a no-op.
    @BmcProof
    void configured_port_is_in_range() {
        int port = Bmc.intFromProperty("app.port");
        Bmc.check(ServerConfig.clampPort(port) == port);
    }

    // FAIL: this run's budget (2_000_000_000) overflows int when doubled — BMC catches that THIS
    // deployment's config triggers the overflow.
    // Expected verdict: REFUTED - THIS run's configured budget overflows int when doubled.
    @BmcProof(expect = Verdict.REFUTED)
    void configured_budget_does_not_overflow() {
        int kb = Bmc.intFromProperty("app.budgetKb");
        Bmc.check(ServerConfig.doubledBudget(kb) >= 0);
    }

    // PASS: with debug=true, quiet=false the verbosity is the expected 2.
    @BmcProof(unwind = 1)
    void verbosity_matches_flags() {
        boolean debug = Bmc.boolFromProperty("app.debug");
        boolean quiet = Bmc.boolFromProperty("app.quiet");
        Bmc.check(ServerConfig.verbosity(debug, quiet) == 2);
    }

    // PASS: the configured sample rate (0.25) is a valid fraction.
    @BmcProof
    void sample_rate_is_a_fraction() {
        double rate = Bmc.doubleFromProperty("app.sampleRate");
        Bmc.check(rate >= 0.0 && rate <= 1.0);
    }

    // PASS: the configured mode string is exactly "production" (sound string equality).
    @BmcProof(unwind = 16)
    void mode_is_production() {
        String mode = Bmc.stringFromProperty("app.mode");
        Bmc.check(mode.equals("production"));
    }

    // FAIL: a required variable that the run did not set — the proof fails ("config not set").
    // Expected verdict: REFUTED - the required variable is deliberately unset for this run.
    @BmcProof(expect = Verdict.REFUTED)
    void required_but_unset_fails() {
        int timeout = Bmc.intFromProperty("app.timeoutMs");
        Bmc.check(timeout >= 0);
    }

    // FAIL: this run sets app.legacyFlag=1 — truthy in many config schemes, but not a Java boolean.
    // Booleans must be exactly "true"/"false" (case-insensitive); anything else fails the proof
    // rather than silently reading as false and verifying the wrong configuration.
    // Expected verdict: REFUTED - "1" does not parse as a boolean; the proof must not guess.
    @BmcProof(expect = Verdict.REFUTED)
    void malformed_boolean_fails_rather_than_guessing() {
        boolean legacy = Bmc.boolFromProperty("app.legacyFlag");
        Bmc.check(!legacy); // would (wrongly) VERIFY if "1" were silently read as false
    }
}
