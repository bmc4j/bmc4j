// Assumed output-contracts (Kotlin) - the Kotlin counterpart of examples/assume-contracts. Proves the
// SAME assume-guarantee shapes (Bmc.assumeEvery / Bmc.assumeStable) through IDIOMATIC Kotlin call sites:
// a method reference (repo::findById) SAM-converted to a Bmc.Ref, plus a trailing-lambda predicate using
// `it`. This validates the Kotlin method-ref + trailing-lambda path end-to-end (decode AND proof), not
// just at the decode level. Each proof self-asserts its verdict via @BmcProof(expect = ...).
plugins {
    kotlin("jvm") // version from the root settings pluginManagement (-PbmcKotlinVersion overrides)
    id("org.bmc4j")
}

// The CONSUMER-side compile target. Default 25; the Kotlin-version CI matrix passes
// -PbmcJvmTarget=21 alongside -PbmcKotlinVersion for KGPs without a JVM_25 target.
val bmcJvmTarget = providers.gradleProperty("bmcJvmTarget").orNull ?: "25"

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(bmcJvmTarget.toInt())) }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(bmcJvmTarget)) }
}
