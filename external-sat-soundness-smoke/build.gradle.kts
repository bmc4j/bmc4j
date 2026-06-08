// A tiny, dedicated proof module for the external-SAT (fast solver) soundness smoke + the
// Step-0 empirical probe (CI job `external-sat-soundness`, Linux only — the fast solver ships
// for linux-x64). It holds proofs that GENUINELY need text/String reasoning, so they exercise
// the cardinal invariant: the fast solver (which runs with String reasoning OFF) must never serve
// a false VERIFIED for a text proof. The workflow drives them two ways:
//   - the GUARD path (normal run, solver = "kissat"): a text proof must FAIL LOUD, never verify;
//   - the PROBE path (expert unsafe override on): records what jbmc actually does refinement-off.
//
// Single-threaded + progress so the per-proof lines are deterministic and greppable.
plugins {
    java
    id("org.bmc4j")
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of((providers.gradleProperty("bmcJvmTarget").orNull ?: "21").toInt())) }
}

bmc {
    parallelism.set(1)
    progress.set(true)
    // No solver default here: the workflow sets the solver per phase (guard vs probe) on the CLI so
    // the same proofs are exercised both ways without editing the build.
}

// The guard's plain-language "text/String" refusal is the test-failure exception message, which the
// engine surfaces on the test WORKER's stdout/stderr. Gradle does NOT forward worker output to the
// console unless this is set — and the Phase-1 soundness assertion in smoke.sh greps the console for
// that "text/String" message to prove the guard (not some unrelated error) is what failed the build.
// So this is load-bearing for the soundness smoke, not cosmetic: without it Phase 1 can't SEE the guard.
tasks.withType<Test>().configureEach {
    testLogging { showStandardStreams = true }
}
