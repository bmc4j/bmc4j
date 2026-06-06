plugins {
    // Same KGP version as the rest of the core build (bmc-kotlin / bmc-models-conformance):
    // one Kotlin plugin version per Gradle build.
    kotlin("jvm") version "2.3.21"
    `java-gradle-plugin`
    `maven-publish`
    // Gradle Plugin Portal publishing (`publishPlugins`; `--validate-only` for dry runs).
    // Credentials come from the GRADLE_PUBLISH_KEY / GRADLE_PUBLISH_SECRET env vars the
    // plugin reads natively - supplied as CI secrets, never stored here.
    id("com.gradle.plugin-publish") version "2.1.1"
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 17
}

// The plugin runs INSIDE the consumer's Gradle daemon, where Gradle's EMBEDDED kotlin-stdlib
// wins classloading for kotlin.* — so the emitted metadata/bytecode is pinned to the same
// Kotlin 1.9 + JVM 17 floor as bmc-kotlin: loadable on any Gradle whose embedded Kotlin
// is >= 1.9, regardless of the (newer) compiler that built it.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_9)
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_9)
    }
}

// Same floor for the POM's stdlib dependency (KGP would otherwise pin its OWN 2.3.x stdlib as an
// api dependency): newer consumers resolve upward to the Gradle-embedded stdlib anyway; older
// ones are never dragged past their embedded version. Mirrors bmc-kotlin's pin.
kotlin {
    coreLibrariesVersion = "1.9.25"
}

dependencies {
    testImplementation(gradleTestKit())
}

gradlePlugin {
    website = "https://bmc4j.org"
    vcsUrl = "https://github.com/bmc4j/bmc4j"
    plugins {
        create("bmc") {
            id = "org.bmc4j"
            implementationClass = "org.bmc4j.gradle.BmcPlugin"
            displayName = "bmc4j"
            description = "Bounded model checking for JVM tests, powered by JBMC. Auto-provisions the engine."
            tags = listOf("verification", "testing", "model-checking", "formal-methods", "jbmc", "bmc")
        }
    }
}
