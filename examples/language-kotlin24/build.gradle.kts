// Language features (Kotlin 2.4) — what's new in Kotlin 2.4 under BMC: context
// parameters (stable), explicit backing fields (stable), and collection literals
// (experimental). Needs kotlinc >= 2.4, so the CI consumer-Kotlin matrix runs this
// module only on its 2.4+ legs (older legs keep the portable example set).
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
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(bmcJvmTarget))
        // Collection literals are experimental in 2.4 — the one opt-in this module needs.
        freeCompilerArgs.add("-Xcollection-literals")
    }
}
