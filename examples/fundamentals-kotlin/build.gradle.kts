// Fundamentals (Kotlin) — the same core bounded-model-checking concepts as fundamentals-java,
// written in idiomatic Kotlin. JBMC analyzes JVM bytecode, so Kotlin works the same; null-safety
// (`!!`/`?.`/`?:`) analyzes cleanly via the bundled clean Intrinsics model. One package per concept.
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
