plugins {
    // Same KGP version as the rest of the core build: one Kotlin plugin version per Gradle build.
    // Engine INTERNALS are being ported to Kotlin; the analysis-facing classes (Bmc, BmcMath,
    // BmcStrings, BmcKotlin, ConfigSupport, the annotations) stay Java permanently — their
    // bytecode is the product JBMC analyzes inside consumers' proofs.
    kotlin("jvm") version "2.3.21"
    `java-library`
    `maven-publish`
    // Shade + relocate gson/asm INTO the published jar so they never appear as
    // POM runtime deps (which they would as plain `implementation` of a published
    // java-library) and therefore can't conflict with versions a consumer pins.
    // 9.x is the line that targets Gradle 9 (its baseline is Gradle 9.0).
    id("com.gradleup.shadow") version "9.4.2"
}

// The Shadow plugin (the only build-classpath plugin here that drags them in) pulls
// log4j-core and plexus-utils as transitives of its Apache Maven model layer. Those
// are BUILD-TIME-ONLY (Shadow runs at jar time to relocate gson/asm; nothing here ships
// or executes them against untrusted input), but Dependabot still flags the vulnerable
// versions. Shadow 9.4.2 is the latest line and still bundles the old transitives, so we
// can't fix this by bumping the plugin — force the patched versions on the plugin classpath.
// Drop these once Shadow ships the patched transitives itself.
buildscript {
    configurations.classpath {
        resolutionStrategy {
            force(
                "org.apache.logging.log4j:log4j-core:2.25.4",
                "org.apache.logging.log4j:log4j-api:2.25.4",
                "org.codehaus.plexus:plexus-utils:4.0.3",
            )
        }
    }
}

// Note: withSourcesJar()/withJavadocJar() are applied centrally to every JVM module
// in core/build.gradle.kts, so they are NOT repeated here.

tasks.withType<JavaCompile>().configureEach {
    options.release = 17
    // Retain method parameter names: the replay renderer maps counterexample bindings
    // back to a proof's declared parameter types/names (enums, strings) via reflection.
    options.compilerArgs.add("-parameters")
}

// kotlin-stdlib is a DELIBERATE plain POM dependency (test scope for consumers via the plugin),
// NOT shaded: relocated stdlib bytes would still land on the analysis classpath, so shading buys
// little and costs shadow×KGP×metadata maintenance forever. The 1.9 metadata + stdlib floor
// mirrors bmc-kotlin's: newer consumers resolve their own stdlib upward; a Kotlin-1.9 consumer is
// never dragged past what its compiler can read.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_9)
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_9)
        // Parity with javac's -parameters above.
        javaParameters.set(true)
    }
}
kotlin {
    coreLibrariesVersion = "1.9.25"
}

// gson (parses JBMC's --json-ui output) and asm (rewrites bytecode for JBMC) are
// genuine RUNTIME dependencies of this module's code, but they are a pure
// implementation detail — leaking them as POM `runtime` deps onto every consumer's
// classpath risks version conflicts. So instead of `implementation(...)` we collect
// them in a `shaded` configuration that:
//   - is NOT a consumer-facing variant (never added to api/runtimeElements), so it
//     contributes NO entries to the published POM; and
//   - is bundled (relocated) into the published jar by `shadowJar` below.
// `compileOnly`/`testImplementation` extend it so the main code and the unit tests
// still compile and run against the ORIGINAL package names locally; only the
// published artifact carries the relocated copies.
val shaded: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}
configurations.named("compileOnly") { extendsFrom(shaded) }
configurations.named("testImplementation") { extendsFrom(shaded) }

dependencies {
    // Consumers write @BmcProof methods, so they need the JUnit API transitively.
    api("org.junit.jupiter:junit-jupiter-api:5.10.2")
    // The proof-sharding PostDiscoveryFilter (ServiceLoader-registered) implements a
    // junit-platform-launcher SPI. compileOnly: the launcher is already on every proof leg's test
    // RUNTIME classpath (the Gradle plugin adds junit-platform-launcher to testRuntimeOnly), so it
    // must not become a POM/runtime dep here — it is purely a test-time presence.
    compileOnly("org.junit.platform:junit-platform-launcher:1.10.2")
    // The filter is unit-tested directly, so the launcher must be on bmc-runtime's OWN test classpath.
    testImplementation("org.junit.platform:junit-platform-launcher:1.10.2")
    // Internal: parsing JBMC's --json-ui output. Shaded + relocated (see below).
    shaded("com.google.code.gson:gson:2.11.0")
    // Internal: LVT-stripping coroutine bytecode for JBMC (see CoroutineBytecode). Shaded.
    shaded("org.ow2.asm:asm:9.8")
    // Internal: the ASM tree API (control-flow loop recovery in LoopInvariantBytecode). Shaded; same
    // org.objectweb.asm package root as asm-core, so the existing relocate covers it.
    shaded("org.ow2.asm:asm-tree:9.8")
    // Test-only: compile a generated Kotlin replay file in-process (the Kotlin analog of the
    // JDK's javax.tools compiler the Java replay-writer test uses) to prove it is valid Kotlin.
    testImplementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.3.21")
}

// Build the published jar by bundling ONLY the `shaded` configuration's classes,
// relocated under org.bmc4j.internal.shaded.* so they can't clash with a consumer's
// own gson/asm. shadowJar takes the bare classifier so it REPLACES the plain jar as
// this module's single artifact (the plain `jar` is disabled below) — meaning every
// consumer path serves the shaded jar: the maven publication AND the includeBuild
// substitution the examples use (apiElements/runtimeElements resolve this artifact).
tasks.shadowJar {
    archiveClassifier.set("")
    configurations = listOf(shaded)
    // Shadow rewrites the runtime code's references to the relocated packages inside
    // the shaded jar automatically.
    relocate("com.google.gson", "org.bmc4j.internal.shaded.gson")
    relocate("org.objectweb.asm", "org.bmc4j.internal.shaded.asm")
}
// Disable the plain jar so its (empty-classifier) output doesn't collide with
// shadowJar's, and so the shaded jar is the one published / consumed everywhere.
tasks.named<Jar>("jar") { enabled = false }
// Make the consumer-facing variants (apiElements/runtimeElements) and the
// maven publication carry the shaded jar instead of the disabled plain jar.
configurations.named("apiElements") {
    outgoing.artifacts.clear()
    outgoing.artifact(tasks.shadowJar)
}
configurations.named("runtimeElements") {
    outgoing.artifacts.clear()
    outgoing.artifact(tasks.shadowJar)
}

// Clean Kotlin runtime models (kotlin.jvm.internal.Intrinsics, kotlinx.coroutines.*)
// live in the :bmc-kotlin-models module and are bundled here as RESOURCES under
// bmc-kotlin-models/ — so they are NEVER on the test JVM's classpath (they would shadow
// the real classes at runtime). BundledKotlinModels extracts them and the extension
// prepends them to JBMC's analysis classpath, where they shadow kotlin-stdlib's
// Intrinsics (whose stack-trace internals trip analysis).
//
// We consume the module via a resolvable-only configuration and unzip its jar into the
// resources tree — deliberately NOT as `implementation`, which would leak the model
// classes onto consumers' runtime classpath.
val kotlinModels: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}
dependencies {
    kotlinModels(project(":bmc-kotlin-models"))
}
val bundleKotlinModel by tasks.registering(Copy::class) {
    // `elements` is a Provider that carries the task dependency on building the
    // :bmc-kotlin-models jar, so it's compiled before we unzip it.
    from(kotlinModels.elements.map { jars -> jars.map { zipTree(it) } }) {
        exclude("META-INF/**")
    }
    into(layout.buildDirectory.dir("kotlin-model/bmc-kotlin-models"))
}
sourceSets.named("main") {
    resources.srcDir(layout.buildDirectory.dir("kotlin-model"))
}
tasks.named("processResources") {
    dependsOn(bundleKotlinModel)
}

// Sound char-array-backed java.lang.String / StringBuilder / AbstractStringBuilder / StringBuffer
// models for JBMC's analysis classpath, used ONLY under --no-refine-strings (StringMode.CHAR_ARRAY_MODEL). Same
// packaging discipline as the kotlin models above: these classes carry real JDK names, so they ship as
// inert RESOURCES (NEVER on a runtime classpath - the bootstrap loader wins for java.*, and a stray
// test-classpath copy would break tests). BundledStringModel extracts them and the no-refine path
// prepends them to JBMC's analysis classpath, where they shadow the cbmc core-models' degenerate
// intrinsic-only String/StringBuilder.
val stringModel: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}
dependencies {
    stringModel(project(":bmc-string-model"))
}
val bundleStringModel by tasks.registering(Copy::class) {
    from(stringModel.elements.map { jars -> jars.map { zipTree(it) } }) {
        exclude("META-INF/**")
    }
    into(layout.buildDirectory.dir("string-model/bmc-string-model"))
}
sourceSets.named("main") {
    resources.srcDir(layout.buildDirectory.dir("string-model"))
}
tasks.named("processResources") {
    dependsOn(bundleStringModel)
}
// sourcesJar packs main.allSource, which includes the kotlin-model resources dir
// bundleKotlinModel produces — Gradle's strict validation requires the explicit
// dependency (this was the pre-existing `-p core build` failure).
tasks.named("sourcesJar") {
    dependsOn(bundleKotlinModel)
    dependsOn(bundleStringModel)
}

// Don't add Shadow's extra `shadow` variant/component to the java component: the
// plain jar IS the shaded jar (above), so a second variant would be redundant and
// would muddy the published Gradle Module Metadata.
shadow {
    addShadowVariantIntoJavaComponent = false
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            // components["java"] keeps the `api` junit dep (compile scope) in the POM;
            // gson/asm are no longer api/implementation/runtimeOnly, so they are ABSENT
            // from the POM — they ride inside the jar, relocated. The published main jar
            // is the shaded jar (apiElements/runtimeElements rewired above).
            from(components["java"])
        }
    }
}
