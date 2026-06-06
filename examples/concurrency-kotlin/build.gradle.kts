// Concurrency (Kotlin) — two concepts:
//   - coroutines: proving `suspend` functions via clean bundled models of the coroutine runtime
//   - lincheck:   logic (@BmcProof) vs concurrency (Lincheck) are different concerns, each blind
//                 to the other. The Lincheck tests are opt-in: -Dbmc.lincheck=true (not our library).
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

dependencies {
    // Coroutine builders — JBMC analyzes clean bundled MODELS of these, not the real artifact
    // (which is only here to compile against).
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    // Lincheck for the (opt-in) concurrency tests.
    testImplementation("org.jetbrains.kotlinx:lincheck:2.34")
}
