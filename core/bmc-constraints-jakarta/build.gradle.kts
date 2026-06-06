plugins {
    // Same KGP version as the rest of the core build: one Kotlin plugin version per Gradle build.
    kotlin("jvm") version "2.3.21"
    `java-library`
    `maven-publish`
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 17
}

// Annotation-processor-path code: it runs inside the consumer's javac (with its own resolved
// kotlin-stdlib from this POM), never on a test/analysis classpath — so no metadata floor is
// needed, only the JVM 17 target matching options.release.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // Exposed so a consumer's annotationProcessor classpath gets the generator too.
    api(project(":bmc-constraints"))
    // The Jakarta Bean Validation annotations the processor reads.
    implementation("jakarta.validation:jakarta.validation-api:3.0.2")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
