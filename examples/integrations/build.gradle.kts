// Integrations — wiring bmc4j to real-world inputs: Jakarta Bean Validation annotations turned
// into proof preconditions, config pinned to the run's real env/property values, and custom
// models (Java & Kotlin) standing in for un-analyzable dependencies. One package per concept.
plugins {
    // Kotlin is here for the Kotlin custom-models concept; the Java concepts compile alongside it.
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
    // Jakarta Bean Validation annotations on the model + the processor that turns them into
    // assumeValid(...). Generated *Constraints code references Bmc on the main compile classpath.
    compileOnly("jakarta.validation:jakarta.validation-api:3.0.2")
    annotationProcessor("org.bmc4j:bmc-constraints-jakarta:0.1.0")
    compileOnly("org.bmc4j:bmc-runtime:0.1.0")
}

// Declare the custom models' INTENT, so a green proof that rests on one footnotes its provenance.
// TaxPolicy is a faithful (conformant) stand-in for the real policy logic; ExchangeRates bounds the
// rate to a domain range the live service can return — an intentional divergence (a domain model), so
// it carries a one-line rationale that shows up on every proof that uses it. Try
// `-Dbmc.strictModels=true` and an UNdeclared src/bmcModel class flips that proof to UNKNOWN.
bmc {
    models {
        conformant("example.custommodels.TaxPolicy")
        domain("example.custommodels.ExchangeRates", "rates bounded to the live service's 0.0001..2.0 range")
    }
}

// config-env concept: bmc4j pins the proofs to the config THIS run is launched with. Change a
// value and the relevant proof re-verifies against it (budgetKb is large on purpose — doubling
// it overflows int).
tasks.withType<Test>().configureEach {
    systemProperty("app.port", "8080")
    systemProperty("app.budgetKb", "2000000000")
    systemProperty("app.debug", "true")
    systemProperty("app.quiet", "false")
    systemProperty("app.sampleRate", "0.25")
    systemProperty("app.mode", "production")
}
