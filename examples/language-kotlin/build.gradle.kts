// Language features (Kotlin) — Kotlin constructs made analyzable: `when` in every form
// (enum, sealed `is`, String, ranges, subjectless) and value classes whose `init { require }`
// invariants are verified under BMC.
plugins {
    kotlin("jvm") // version from the root settings pluginManagement (-PbmcKotlinVersion overrides)
    id("org.bmc4j")
}

// The CONSUMER-side compile target. Default 25; the Kotlin-version CI matrix passes
// -PbmcKotlinJvmTarget=21 alongside -PbmcKotlinVersion, because older KGPs have no
// JVM_25 target - and real Kotlin-2.0 consumers are on older JVMs anyway.
val bmcKotlinJvmTarget = providers.gradleProperty("bmcKotlinJvmTarget").orNull ?: "25"

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(bmcKotlinJvmTarget.toInt())) }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(bmcKotlinJvmTarget)) }
}
