// Concurrency (Kotlin) — two concepts:
//   - coroutines: proving `suspend` functions via clean bundled models of the coroutine runtime
//   - lincheck:   logic (@BmcProof) vs concurrency (Lincheck) are different concerns, each blind
//                 to the other. The Lincheck tests are opt-in: -Dbmc.lincheck=true (not our library).
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

dependencies {
    // Coroutine builders — JBMC analyzes clean bundled MODELS of these, not the real artifact
    // (which is only here to compile against).
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    // Lincheck for the (opt-in) concurrency tests.
    testImplementation("org.jetbrains.kotlinx:lincheck:2.34")
}
