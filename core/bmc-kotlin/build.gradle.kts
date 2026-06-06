// Kotlin-facing helpers for bmc4j (e.g. `assumeValid { ... }`). These are `inline`
// functions, so their bodies are inlined into the consumer's proof at compile time —
// no lambda object, no invokedynamic — which keeps them analysable by JBMC. The plugin
// adds this module to a project's testImplementation only when the Kotlin JVM plugin is
// applied, so Java-only consumers never pull in kotlin-stdlib.
plugins {
    kotlin("jvm") version "2.3.21"
    `maven-publish`
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 17
}

// Built with the Kotlin 2.3 compiler (Gradle 9 dropped the 1.x plugin's removed APIs),
// but the EMITTED metadata/bytecode is pinned to Kotlin 1.9 + JVM 17 so consumers still
// on Kotlin 1.9 can depend on this module — its inline helpers are the public surface.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_9)
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_9)
    }
}

// The metadata pin above is necessary but NOT sufficient: KGP auto-adds kotlin-stdlib at
// its OWN version (2.3.x) as an api dependency, so the published POM would force-upgrade
// every consumer's stdlib to 2.3 — and a pre-2.2 kotlinc cannot read a 2.3 stdlib's
// metadata (the Kotlin-version CI matrix caught exactly this as a FIR crash on the 2.0
// leg). Declare the stdlib dependency at the same 1.9 FLOOR as the language/api pin:
// newer consumers win resolution upward to their own stdlib; older consumers are never
// dragged past their compiler.
kotlin {
    coreLibrariesVersion = "1.9.25"
}

dependencies {
    // Bmc (assume/assumeUnreachable) is referenced from inline bodies; the consumer already
    // has bmc-runtime via the plugin, so we only need it to compile here.
    compileOnly(project(":bmc-runtime"))
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
