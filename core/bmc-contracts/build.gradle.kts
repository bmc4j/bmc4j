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
    // The @Ensures/@Requires annotations this processor reads, plus the ContractStubGenerator /
    // ContractEnforceProofGenerator / ContractManifest it renders and emits. Exposed so a
    // consumer's annotationProcessor classpath gets the generators transitively.
    api(project(":bmc-runtime"))
    // The processor test compiles small @BmcContractsFor sources in-process (ToolProvider
    // javac) and runs ContractProcessor over them, so the test JVM needs the @BmcContractsFor /
    // @Requires / @Ensures / @ExpectEnforce annotations on its classpath. (JUnit is added by the
    // root build's per-module Java setup.)
    testImplementation(project(":bmc-runtime"))
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
