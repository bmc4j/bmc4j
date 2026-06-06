// Fundamentals (Kotlin) — the same core bounded-model-checking concepts as fundamentals-java,
// written in idiomatic Kotlin. JBMC analyzes JVM bytecode, so Kotlin works the same; null-safety
// (`!!`/`?.`/`?:`) analyzes cleanly via the bundled clean Intrinsics model. One package per concept.
plugins {
    kotlin("jvm") // version from the root settings pluginManagement (-PbmcKotlinVersion overrides)
    id("org.bmc4j")
}

// The CONSUMER-side compile target. Default 25; the Kotlin-version CI matrix passes
// -PbmcJvmTarget=21 alongside -PbmcKotlinVersion, because older KGPs have no
// JVM_25 target - and real Kotlin-2.0 consumers are on older JVMs anyway.
val bmcJvmTarget = providers.gradleProperty("bmcJvmTarget").orNull ?: "25"

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(bmcJvmTarget.toInt())) }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(bmcJvmTarget)) }
}
