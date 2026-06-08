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
