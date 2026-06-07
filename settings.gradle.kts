// Repo-root build: a thin aggregator so the whole project opens as ONE Gradle
// import. It includes the product build (core/) and holds the examples as child
// projects. includeBuild supplies BOTH the plugin (for `plugins { id(...) }`) and
// the runtime/engine dependencies (by coordinate substitution) — no publishing,
// no mavenLocal. Each example module is individually runnable from your IDE.
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    // The Kotlin version the CONSUMER-side builds (examples + the conformance suite)
    // compile with - i.e. the user's kotlinc, whose emitted bytecode the rewrite layer
    // and models must handle. The CI Kotlin matrix overrides it (-PbmcKotlinVersion=2.0.21
    // etc., paired with -PbmcJvmTarget for KGPs without newer JVM targets); the
    // product build under core/ keeps its own pinned Kotlin - shipped artifacts don't
    // vary with the consumer's compiler.
    plugins {
        id("org.jetbrains.kotlin.jvm") version (providers.gradleProperty("bmcKotlinVersion").orNull ?: "2.4.0")
    }
    // The KSP plugin (replacing the deprecated kapt) that the org.bmc4j plugin applies to a Kotlin
    // consumer is NOT declared here: it travels with the bmc plugin (an `implementation` dependency of
    // bmc-gradle-plugin) and is applied programmatically, so it needs no version pin in this block.
    // KSP2 runs as its own compiler invocation and drives a newer consumer Kotlin than its own
    // version, so the bmc-pinned KSP works across the -PbmcKotlinVersion legs.
}

// Auto-provision JDK toolchains (the examples + model-conformance-proofs pin a
// Java 25 toolchain via JavaLanguageVersion.of(25)). The Foojay resolver lets
// Gradle download a matching JDK on runners that don't have one pre-installed,
// so the conformance gate can run on a stock CI runner. 1.0.0 is the first
// release compatible with Gradle 9 (earlier versions reference the removed
// JvmVendorSpec.IBM_SEMERU and fail to apply).
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

// One inclusion of the product build: provides the plugin to `plugins { }` AND
// substitutes the runtime/engine coordinates with its projects (no publishing).
includeBuild("core")

rootProject.name = "bmc4j"

// Examples are grouped by topic, one Gradle module per group; within a module each
// concept lives in its own package. Java and Kotlin are split where both carry weight.
include(
    "examples:fundamentals-java",
    "examples:fundamentals-kotlin",
    "examples:language-java",
    "examples:language-kotlin",
    "examples:language-kotlin24", // needs kotlinc >= 2.4; older -PbmcKotlinVersion legs must not build it
    "examples:stdlib",
    "examples:integrations",
    "examples:kotlin-coroutines-and-lincheck",
    "examples:contracts",
    "examples:contracts-kotlin",
)

// Model proofs: @BmcProof proofs of the bmc-models' own algebraic laws, verified by JBMC itself
// (the second conformance axis to the JVM-differential suite in core/). Lives here in the
// root build because it needs the org.bmc4j plugin (wired via includeBuild for the examples).
include(
    "model-conformance-proofs",
)

// Verdict-cache soundness smoke: a tiny dedicated proof module the `cache-soundness`
// CI job drives through the four cache phases (cold green -> unchanged HIT -> mutated
// RE-SOLVE+FAIL -> unrelated-touch still-HIT). Kept out of the main proof matrix; it
// runs only in its own workflow. See .github/workflows/cache-soundness.yml.
include(
    "cache-soundness-smoke",
)
