plugins {
    // Same KGP version as the rest of the core build: one Kotlin plugin version per Gradle build.
    kotlin("jvm") version "2.3.21"
    `java-library`
    `maven-publish`
}

// The KSP API version the Kotlin contracts SymbolProcessor compiles against. KSP2 (Kotlin 2.x)
// runs as its own compiler invocation and is tolerant of a consumer Kotlin newer than this; pinned
// to the latest line that matches the core build's Kotlin 2.3.x. The org.bmc4j plugin wires this
// same version's KSP Gradle plugin into Kotlin consumers.
val KSP_VERSION = "2.3.9"

tasks.withType<JavaCompile>().configureEach {
    options.release = 17
}

// The Kotlin contracts processor runs inside the consumer's KSP compiler invocation; the javac
// processor runs inside the consumer's javac. Both target the JVM 17 floor matching options.release.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        // Metadata floor so the KSP-side classes load under an older Kotlin than the build's: KSP2
        // hosts them under the consumer's compiler, exactly like bmc-kotlin's helpers.
        languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_9)
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_9)
    }
}

kotlin {
    coreLibrariesVersion = "1.9.25"
}

dependencies {
    // The @Ensures/@Requires annotations this processor reads, plus the ContractStubGenerator /
    // ContractEnforceProofGenerator / ContractManifest it renders and emits. Exposed so a
    // consumer's annotationProcessor classpath gets the generators transitively.
    api(project(":bmc-runtime"))
    // KSP API: the Kotlin contracts path runs as a KSP SymbolProcessor (ContractSymbolProcessor),
    // replacing the deprecated kapt. compileOnly — the API is provided by the consumer's KSP plugin
    // when this module sits on `kspTest`, so it must not leak onto the runtime/POM classpath.
    compileOnly("com.google.devtools.ksp:symbol-processing-api:$KSP_VERSION")
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
