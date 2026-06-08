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

// Proofs that reference Java 21+ API (e.g. ArrayList's SequencedCollection head/tail methods:
// addFirst/addLast/getFirst/getLast/removeFirst/removeLast) cannot COMPILE on the JDK-17 floor leg,
// where those members do not exist on java.util.List. Keep them in a dedicated src/test21 tree that
// is only added to the test compilation when the target floor is 21 or newer, so the 17 conformance
// leg still builds while 21/25 legs keep exercising that surface.
if (bmcJvmTarget.toInt() >= 21) {
    kotlin.sourceSets.named("test") {
        kotlin.srcDir("src/test21/kotlin")
    }
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
    // Opt-in concurrency cap (-PbmcParallelism=N): string-heavy proofs can eat all machine
    // memory at the default per-core fan-out; cap the jbmc pool when running these locally.
    providers.gradleProperty("bmcParallelism").orNull?.let { parallelism.set(it.toInt()) }
    // Deliberately out-of-scope packages — the soundness-bounded areas bmc4j has decided NOT to model.
    // This is the package-grain encoding of policy ALREADY documented in docs/coverage.md (the 🚫/❌
    // rows), not new policy. Declaring them makes the package-grain completeness ratchet
    // (proofs.audit.PackageCompletenessRatchetTest) and the per-proof out-of-scope demotion MEANINGFUL:
    // every class the proof cone reaches must now be either MODELED or matched by one of these globs,
    // else a reach surfaces as a LOUD, member-named out-of-scope (declared) UNKNOWN (never a silent
    // nondet stub, never a false VERIFIED) — and the registry still WINS (a modeled class inside a
    // declared package stays modeled; the waiver only ever consumes harvested stubs). Recursive glob.
    //
    // Lives PER-MODULE here, not as a plugin-baked portfolio default. A shared default is technically
    // expressible as a `notModeledPackagesSpec.globs.convention(...)` in BmcPlugin, but it does NOT
    // compose cleanly: Gradle's ListProperty.add() (what the `+"…"` DSL calls) DISCARDS a convention the
    // moment a consumer declares its first own glob — so a real game that adds one package would
    // silently LOSE every inherited JDK default. For a soundness tool that is an unacceptable footgun
    // (a dropped default = a reach that should be loud-out-of-scope silently demoting wrong), so the
    // plugin keeps seeding no notModeledPackages convention. This module owns bmc4j's completeness
    // ratchet and is the authoritative "what our proof suite reaches" surface, so the list belongs here.
    // (examples/integrations also has a bmc{} block but its proofs reach NONE of these packages and it
    // is not a green gate — a waiver there would be inert, so it is intentionally not duplicated.)
    notModeledPackages {
        // GUI / desktop — external world, like the java.io/nio/net row below: no in-scope reason for a
        // BMC proof to drive a UI toolkit, and no model exists.
        +"javax.swing.*"           // GUI/desktop toolkit — external world (no model)
        +"java.awt.*"              // GUI/desktop toolkit — external world (no model)
        // IO / external world — coverage.md 🚫 "java.io / java.nio / java.net: external world
        // (files/sockets) — outside what BMC can reason about". Scoped PRECISELY: java.nio.file.* (the
        // filesystem subpackage) and java.net.* are waived; bare java.io.* is deliberately OMITTED
        // because java.io.IOException and friends are reached incidentally across the runtime/JDK and a
        // broad waiver there could mis-demote an incidental reach — the precise filesystem/socket
        // subpackages carry the same external-world policy without that blast radius.
        +"java.nio.file.*"         // coverage.md 🚫: java.nio filesystem — external world
        +"java.net.*"             // coverage.md 🚫: java.net sockets — external world
        // Persistence — JDBC/DataSource are external-world I/O (live DB connections), the same class of
        // thing as the io/net row; no model exists.
        +"java.sql.*"              // persistence: external DB connections (external-world I/O)
        +"javax.sql.*"             // persistence: DataSource/rowset — external DB connections
        // Regex engine — coverage.md 🚫 "java.util.regex Pattern/Matcher … regex engines are
        // mature/well-proven and rarely the code under proof; faithful BMC modeling is large/low-value".
        +"java.util.regex.*"       // coverage.md 🚫: Pattern/Matcher — deliberately not modeled
        // Zones / text formatting & parsing — coverage.md ❌ "ZonedDateTime/formatters/zones: need the
        // IANA tz DB / text parsing — out of scope for a bounded model". DELIBERATELY scoped to the
        // .zone / .format subpackages and EXCLUDES java.time.chrono.* / java.time.temporal.*: the
        // LocalDate/LocalDateTime/Period models IMPLEMENT those interfaces (ChronoLocalDate/…/Temporal)
        // — the #150 interface-cast fix — so those interface types are load-bearing and must NOT be
        // declared out of scope (registry-wins protects the concrete classes, but not the iface types).
        +"java.time.zone.*"        // coverage.md ❌: zones need the IANA tz DB — out of scope
        +"java.time.format.*"      // coverage.md ❌: formatters need text parsing — out of scope
        // Kotlin stdlib external-world surface — the kotlin.* analogues of the java rows above. Most of
        // kotlin-stdlib is inline (no JVM method) or thin facades over java.util/java.lang (modeled on
        // the Java side), so only the genuinely-external packages need declaring; the modelable facades
        // (kotlin.collections/sequences/ranges/comparisons + the math/text/random candidates) stay OFF.
        +"kotlin.io.*"             // file/console IO — external world, like java.nio.file/java.net above
        +"kotlin.reflect.*"        // reflection — runtime type introspection, not BMC-reasonable
        +"kotlin.system.*"         // exitProcess / measureTime wall-clock — external/non-deterministic
    }
    // DELIBERATELY NOT declared (waiving these would be wrong / unsound):
    //  - kotlin.random.* — NOT external/unmodelable: the bounded-draw surface (nextInt(bound)/nextBoolean/
    //    range draws) is soundly modelable as nondet-in-range (the ideal "prove for every outcome" BMC
    //    use); only seeded reproducibility resists modeling. A model candidate, not a waiver.
    //  - kotlin.math.* (Math-like, modelable) and kotlin.text.* (routes through the modeled java.lang.String
    //    surface) — both in-scope; leaving them non-waived keeps them as model candidates.
    //  - java.time.chrono.* / java.time.temporal.* — the time models implement these interfaces (above).
    //  - java.util.concurrent (wholesale) — it ALSO holds MODELED classes (Atomic*/CompletableFuture/
    //    ConcurrentHashMap/CountDownLatch/Semaphore/blocking queues/executors; coverage.md ✅). Only the
    //    Phaser/CyclicBarrier multi-party-barrier surface is 🚫, not the package. Registry-wins would
    //    protect the modeled classes, but a package-wide j.u.c waiver is bad hygiene — leave j.u.c
    //    partial so a reach into an unmodeled j.u.c member stays a loud per-member UNKNOWN.
    //  - java.lang.reflect.* / java.lang.invoke.* — NO coverage.md "won't do" row, and the desugar layer
    //    already handles indy soundly (lambda/indy bootstraps rewritten away; residual indy redirected to
    //    org.bmc4j.analysis, not left as a java.lang.invoke reach). Declaring either would invent policy
    //    the docs don't carry — and could shadow the reflection types the link-failure/stub detection
    //    references. Left non-waived.
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
