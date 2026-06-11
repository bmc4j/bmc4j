// Intentionally minimal. The examples under examples/ are this build's child
// projects; each applies the `org.bmc4j` plugin (resolved from the included
// core/ build). Run them with, e.g.:
//
//   ./gradlew :examples:fundamentals-java:test   # one topic module
//   ./gradlew test                               # everything (fail-on-purpose demos self-assert their verdicts)

// Proof Test tasks always execute, and are never stored in / replayed from the build cache.
// A @BmcProof suite is verified by the engine + the per-proof verdict cache, NOT by Gradle's
// task-output cache: the CI matrix must genuinely RUN each leg (no cross-leg FROM-CACHE replay
// deduping, say, a kotlin leg against jdk21), and a re-run is already cheap because unchanged
// proofs hit the verdict cache. Forcing only the Test tasks lets the build cache stay ON for
// everything else — most importantly the expensive `bmcMirrorClasspath` (@CacheableTask), which
// then replays FROM-CACHE instead of re-mirroring the whole analysis classpath every run.
// Scoped via `org.bmc4j` so core's own unit tests (a separate included build) are untouched.
subprojects {
    plugins.withId("org.bmc4j") {
        tasks.withType(org.gradle.api.tasks.testing.Test::class.java).configureEach {
            outputs.upToDateWhen { false }
            outputs.cacheIf { false }
        }
    }
}
