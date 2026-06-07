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
    // The Kotlin Gradle plugin API, for the (Kotlin-only) `wireKotlinContracts` path that sets
    // `javaParameters` on KotlinCompile tasks. compileOnly: it is NEVER a runtime/POM dependency of
    // this plugin — at apply time in a Kotlin consumer the consumer's own KGP is on the classpath
    // (the wiring runs only inside `withPlugin("org.jetbrains.kotlin.jvm")`), and a Java-only
    // consumer never loads the class. Pinned to the plugin's own build KGP (2.3.21); the
    // compilerOptions.javaParameters property is ABI-stable across the 2.x KGPs consumers use.
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.21")
    // The KSP Gradle plugin, so the bmc plugin can `pluginManager.apply("com.google.devtools.ksp")`
    // for a Kotlin consumer with zero ceremony (the consumer needn't declare KSP itself). Unlike kapt
    // — which ships inside KGP and is on the consumer classpath automatically — KSP is a separate
    // plugin, so it must travel with this plugin to be applyable programmatically. KSP2 runs as its
    // own compiler invocation and drives a newer consumer Kotlin than its own version, so pinning
    // 2.3.9 here still works under the -PbmcKotlinVersion legs. `implementation`: it is on the
    // plugin's runtime/buildscript classpath, but only ever applied inside the Kotlin-JVM `withPlugin`
    // block, so a Java-only consumer never triggers KSP.
    implementation("com.google.devtools.ksp:symbol-processing-gradle-plugin:2.3.9")
    testImplementation(gradleTestKit())
    // ProjectBuilder tests apply the Kotlin JVM + KSP plugins to assert the Kotlin contracts wiring,
    // so KGP must be on the test classpath (it is only compileOnly for main); KSP comes via the
    // implementation dependency above.
    testImplementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.21")
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
