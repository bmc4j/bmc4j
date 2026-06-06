// Model proofs (axis 2): prove the bmc-models' own algebraic laws with @BmcProof,
// so JBMC verifies — under its *own* semantics, the ones real proofs rely on — that each model
// behaves as intended and is loud (never silently wrong) at its bounds. The proofs reference the
// real java.* types; the org.bmc4j plugin puts the models on JBMC's analysis classpath, exactly as
// for any user proof.
plugins {
    kotlin("jvm") // version from the root settings pluginManagement (-PbmcKotlinVersion overrides)
    id("org.bmc4j")
}

// The CONSUMER-side compile target. Default 25; the Kotlin-version CI matrix passes
// -PbmcJvmTarget=21 alongside -PbmcKotlinVersion, because older KGPs have no
// JVM_25 target - and real Kotlin-2.0 consumers are on older JVMs anyway.
val bmcJvmTarget = providers.gradleProperty("bmcJvmTarget").orNull ?: "25"

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(bmcJvmTarget.toInt())) }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(bmcJvmTarget)) }
}

// Some of these proofs are division-heavy and opt into Z3 via @BmcProof(solver = "z3"). Z3 just has
// to be findable: point bmc.solverPath at the directory containing the z3 binary. The path is read
// from a machine-local Gradle property (set `z3Path=…` in ~/.gradle/gradle.properties) so no one's
// absolute path lands in the repo; with it unset, those proofs surface a clear "solver not found".
bmc {
    providers.gradleProperty("z3Path").orNull?.let { solverPath.set(it) }
}

// Opt-in benchmarking escape hatch (no-op unless -PsatPath is passed): route the proofs at an
// external DIMACS SAT solver (e.g. CryptoMiniSat) instead of jbmc's built-in MiniSAT 2.2.1, to
// compare solver speed on division/array-heavy proofs. Bypasses string refinement, so string-free
// proofs only. The real lever for division proofs turned out to be the symbolic range, not the
// solver — see setScale below — so this stays an experiment, not the default.
tasks.withType<Test>().configureEach {
    doFirst {
        providers.gradleProperty("satPath").orNull?.let { systemProperty("bmc.externalSat", it) }
    }
}

// Dev coverage must match shipped reality at least once — a published consumer gets
// bmc-models AS A JAR, but the rest of this suite (and the examples) run on the includeBuild class
// DIRECTORY. This task runs proofs.jarmodels.JarModelLaws with bmc-models forced onto the analysis
// classpath as its JAR artifact, exercising the jar-mirroring rewrite path (ClasspathMirror) on real
// jbmc. `check` depends on it; the CI gate invokes this task EXPLICITLY (root `test` alone does
// not trigger `check`, and `-p core` never reaches this root-build module).
//
// We resolve the bmc-models jar through a dedicated configuration (substituted to the core
// included-build's :bmc-models project, whose default artifact is its jar), then build this task's
// classpath as: the normal test classpath with every bmc-models CLASS DIRECTORY removed, plus the
// jar. So the only copy of the models JBMC sees is the jar — proving the jar path.
val bmcModelsJar by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
}
dependencies {
    bmcModelsJar("org.bmc4j:bmc-models:0.1.0")
}

val jarModelsConformanceTest by tasks.registering(Test::class) {
    group = "verification"
    description = "Verify proofs with bmc-models supplied as a JAR (jar-mirror rewrite path)."
    val base = tasks.named<Test>("test").get()
    testClassesDirs = base.testClassesDirs
    useJUnitPlatform()
    // Only the dedicated jar-models proof class.
    filter { includeTestsMatching("proofs.jarmodels.*") }
    // Build the classpath: drop bmc-models class dirs, add the bmc-models jar instead.
    val modelsJarFiles = bmcModelsJar
    doFirst {
        providers.gradleProperty("z3Path").orNull?.let { systemProperty("bmc.solverPath", it) }
    }
    classpath = files(
        base.classpath.filter { f ->
            // exclude the bmc-models class-dir output (includeBuild substitutes the project's
            // build/classes/.../bmc-models dir); keep everything else, then append the jar below.
            !f.path.replace('\\', '/').contains("/bmc-models/build/")
        },
        modelsJarFiles,
    )
}

tasks.named("check") { dependsOn(jarModelsConformanceTest) }
