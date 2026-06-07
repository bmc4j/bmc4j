// A tiny, dedicated proof module for the verdict-cache soundness smoke (CI job
// `cache-soundness`). One proven class + one unrelated class on the same classpath,
// and a single @BmcProof — small enough to prove cold in seconds. The smoke script
// drives it through the four cache phases (cold green, unchanged HIT, mutated
// RE-SOLVE+FAIL, unrelated-touch still-HIT); see .github/workflows/cache-soundness.yml.
//
// Single-threaded (parallelism = 1) so the per-proof progress line's "cached verdict"
// marker is emitted deterministically, and progress = true so that line is logged.
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
}
