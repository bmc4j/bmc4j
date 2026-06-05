plugins {
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
