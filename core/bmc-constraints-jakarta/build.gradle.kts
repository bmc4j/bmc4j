plugins {
    // Same KGP version as the rest of the core build: one Kotlin plugin version per Gradle build.
    kotlin("jvm") version "2.3.21"
    `java-library`
    `maven-publish`
}

// The KSP API version the Kotlin constraints SymbolProcessor compiles against. Pinned to the same
// line as bmc-contracts (the other KSP processor that composes alongside this one on `kspTest`); the
// org.bmc4j plugin wires this same version's KSP Gradle plugin into Kotlin consumers.
val KSP_VERSION = "2.3.9"

tasks.withType<JavaCompile>().configureEach {
    options.release = 17
}

// Both paths run inside the consumer's compiler invocation (its own resolved kotlin-stdlib), never on
// a test/analysis classpath. The javac processor runs under javac; the KSP SymbolProcessor is hosted
// under the consumer's (possibly older) Kotlin compiler exactly like bmc-contracts, so it carries the
// same 1.9 metadata floor. The JVM target matches options.release.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_9)
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_9)
    }
}

kotlin {
    coreLibrariesVersion = "1.9.25"
}

dependencies {
    // Exposed so a consumer's annotationProcessor classpath gets the generator too.
    api(project(":bmc-constraints"))
    // The Jakarta Bean Validation annotations the processor reads.
    implementation("jakarta.validation:jakarta.validation-api:3.0.2")
    // KSP API: the Kotlin constraints path runs as a KSP SymbolProcessor (BmcConstraintsSymbolProcessor),
    // wired onto a consumer's `kspTest` by the org.bmc4j plugin. compileOnly — the API is provided by
    // the consumer's KSP plugin, so it must not leak onto the runtime/POM classpath.
    compileOnly("com.google.devtools.ksp:symbol-processing-api:$KSP_VERSION")
    // The in-process APT body test compiles small jakarta-annotated DTOs (ToolProvider javac) and runs
    // BmcConstraintsProcessor over them, so the test JVM needs the constraint annotations on classpath.
    testImplementation("jakarta.validation:jakarta.validation-api:3.0.2")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
