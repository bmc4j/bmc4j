// Language features (Kotlin) — Kotlin constructs made analyzable: `when` in every form
// (enum, sealed `is`, String, ranges, subjectless) and value classes whose `init { require }`
// invariants are verified under BMC.
plugins {
    kotlin("jvm") version "2.3.21"
    id("org.bmc4j")
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(25)) }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25) }
}
