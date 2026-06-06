package org.bmc4j.gradle

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestResult
import java.util.concurrent.ConcurrentHashMap

// The version this plugin was published at, read from its own jar manifest
// (Implementation-Version is stamped into every jar by the central build config -
// the same mechanism Bmc4jVersion uses). The plugin emits this as the coordinate
// for the runtime/engine/models it wires in, so a consumer resolves exactly the
// artifacts this plugin build was published alongside - a snapshot plugin (e.g.
// 0.1.2-ab12cd3) pulls the snapshot runtime + engine, not the released line.
// The dev fallback only appears under includeBuild, where dependency substitution
// replaces every org.bmc4j coordinate with the local projects and the literal
// version is irrelevant.
internal val VERSION: String =
        BmcPlugin::class.java.`package`?.implementationVersion?.takeIf { it.isNotBlank() }
                ?: "0.0.1-local"
private const val JUNIT_VERSION = "5.10.2"
private const val JUNIT_PLATFORM_LAUNCHER_VERSION = "1.10.2"
private const val GROUP = "org.bmc4j"

/**
 * Wires bounded model checking into a JVM project. Applying it is all a consumer
 * needs:
 *
 * ```
 * plugins { id("org.bmc4j") }
 * ```
 *
 * It then:
 * - adds the `bmc-runtime` (`@BmcProof`, `Bmc`) and JUnit 5 to `testImplementation`;
 * - adds the `bmc-engine-<platform>` jar — the JBMC binary bundled as an ordinary,
 *   integrity-verified dependency — to `testRuntimeOnly` (no download at test time);
 * - runs proofs as part of the normal `test` task.
 *
 * Set `bmc { jbmcPath = "..." }` to use a local binary instead of the bundled engine.
 */
class BmcPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        project.pluginManager.apply(JavaPlugin::class.java)

        val ext = project.extensions.create("bmc", BmcExtensionConfig::class.java)
        ext.unwind.convention(16)
        ext.parallelism.convention(Runtime.getRuntime().availableProcessors())
        ext.progress.convention(true)
        ext.cache.convention(true)

        project.dependencies.add("testImplementation", "$GROUP:bmc-runtime:$VERSION")
        project.dependencies.add("testImplementation", "org.junit.jupiter:junit-jupiter:$JUNIT_VERSION")
        // Gradle 9 stopped auto-adding the JUnit Platform launcher to the test runtime classpath;
        // a consumer's proofs would fail to start without it, so the plugin provides it.
        project.dependencies.add("testRuntimeOnly",
                "org.junit.platform:junit-platform-launcher:$JUNIT_PLATFORM_LAUNCHER_VERSION")
        // Kotlin consumers also get the Kotlin helpers (assumeValid { ... }). Added only when the
        // Kotlin JVM plugin is applied, so Java-only projects never pull in kotlin-stdlib.
        project.pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
            project.dependencies.add("testImplementation", "$GROUP:bmc-kotlin:$VERSION")
        }
        // JBMC models for JDK types (e.g. java.time). On the analysis classpath only;
        // the real JVM ignores these java.* classes (bootstrap loader wins).
        project.dependencies.add("testRuntimeOnly", "$GROUP:bmc-models:$VERSION")

        // Method contracts: contracts live in src/test (@BmcContractsFor types), so the
        // processor runs on the TEST sources and generates replace-stubs, enforce-@BmcProofs, and a
        // manifest into the test output. Test code already has bmc-runtime, so the generated code
        // compiles; production code stays free of any bmc reference. No contracts -> nothing generated.
        project.dependencies.add("testAnnotationProcessor", "$GROUP:bmc-contracts:$VERSION")

        // A `src/bmcModel/` source set for consumer-authored JBMC models: a class here
        // (same fully-qualified name as a real one) shadows it on JBMC's analysis
        // classpath only. It is compiled but kept OFF the test runtime classpath, so
        // the real class still runs when tests execute. Models compile against the
        // project's test classpath + Bmc, so a model can return Bmc.anyInt(...).
        val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
        val bmcModel = sourceSets.create("bmcModel")
        project.afterEvaluate { p ->
            bmcModel.compileClasspath = p.files(
                    p.configurations.getByName("testCompileClasspath"),
                    sourceSets.getByName("main").output)
        }

        // Add the bundled engine for the host platform unless a local binary is configured.
        project.afterEvaluate { p ->
            if (!ext.jbmcPath.isPresent) {
                p.dependencies.add("testRuntimeOnly", "$GROUP:bmc-engine-${detectPlatformId()}:$VERSION")
            }
        }

        // Stub report: aggregate the harvested nondet stubs (persisted in each verified
        // proof's verdict-cache entry) into a ranked most-hit list — a data-driven bmc-models backlog.
        // Run `test` first to populate the cache; then `bmcStubReport`.
        project.tasks.register("bmcStubReport", BmcStubReportTask::class.java) { task ->
            task.group = "verification"
            task.description = "Rank the most-hit nondet-stubbed methods across the proof suite."
            task.cacheDir.set(project.layout.buildDirectory.dir("bmc4j/verdict-cache"))
            task.reportFile.set(project.layout.buildDirectory.file("bmc4j/stub-report.txt"))
        }

        project.tasks.withType(Test::class.java).configureEach { test ->
            test.useJUnitPlatform()
            // Consumer models must be compiled before proofs run.
            test.dependsOn(bmcModel.classesTaskName)

            // Progress logging (issue: silence during proof runs looks like a hang). At lifecycle
            // level so it shows in a normal `gradlew test`: a header, a line as each proof starts and
            // finishes (with outcome + wall time), and a final tally. Toggle with bmc { progress }.
            test.doFirst {
                if (ext.progress.getOrElse(true)) {
                    val note = buildString {
                        append("unwind=").append(ext.unwind.get())
                        append(", parallelism=").append(ext.parallelism.get())
                        if (!System.getProperty("bmc.externalSat").isNullOrBlank()) {
                            append(", externalSat")
                        }
                    }
                    test.logger.lifecycle("bmc4j: verifying proofs with JBMC ($note)")
                }
            }
            // Cache-hit detection for the progress line. The extension prints a
            // "  bmc4j: <entryFunction> -> <VERDICT> (cached...)" marker to the test JVM's stdout
            // when a proof is served from the verdict cache - stdout Gradle normally swallows on
            // passing tests, but output EVENTS still reach listeners. Record which proofs carried
            // the marker so the finish line can say "(cached verdict, ...)" instead of looking like
            // a (suspiciously fast) engine run. Keyed by the entry-function FQN PARSED FROM THE
            // MESSAGE, not the event's descriptor: under parallel proof execution the descriptor
            // attribution of stdout events is unreliable (observed: one of two same-class hits
            // attributed, one not). ConcurrentHashMap because test workers report in parallel.
            val cachedProofs = ConcurrentHashMap<String, Boolean>()
            val cachedMarker = Regex("""bmc4j: (\S+) -> \S+ \(cached""")
            test.addTestOutputListener { _, event ->
                val msg = event.message
                if (msg != null && msg.contains("(cached")) {
                    cachedMarker.findAll(msg).forEach { cachedProofs[it.groupValues[1]] = true }
                }
            }
            test.addTestListener(object : TestListener {
                override fun beforeSuite(suite: TestDescriptor) {
                }

                override fun afterSuite(suite: TestDescriptor, result: TestResult) {
                    if (suite.parent == null && ext.progress.getOrElse(true) && result.testCount > 0) {
                        test.logger.lifecycle(String.format(
                                "bmc4j: %d proofs -> %d passed, %d refuted, %d skipped (%.1fs)",
                                result.testCount, result.successfulTestCount,
                                result.failedTestCount, result.skippedTestCount,
                                (result.endTime - result.startTime) / 1000.0))
                    }
                }

                override fun beforeTest(t: TestDescriptor) {
                    if (ext.progress.getOrElse(true)) {
                        test.logger.lifecycle(
                                "  bmc4j > proving ${simpleName(t.className)}.${t.name}")
                    }
                }

                override fun afterTest(t: TestDescriptor, r: TestResult) {
                    if (ext.progress.getOrElse(true)) {
                        val outcome = when {
                            r.resultType == TestResult.ResultType.SUCCESS -> "OK"
                            r.resultType == TestResult.ResultType.SKIPPED -> "SKIP"
                            // Distinguish UNKNOWN (undecided within budget) from a real REFUTED so the
                            // progress log doesn't call a timeout/engine-error a refutation.
                            isUndecided(r) -> "UNKNOWN"
                            else -> "REFUTED"
                        }
                        // A proof served from the verdict cache says so - a 0.0s "OK" otherwise
                        // reads as either suspicious or as engine speed it didn't earn. The map key
                        // is the entry-function FQN the extension printed (class.method, no parens);
                        // the JUnit display name carries "method(...)", so strip the parameter list.
                        val bareName = t.name.replaceFirst(Regex("""\(.*\)$"""), "")
                        val cached = cachedProofs.remove("${t.className}.$bareName") != null
                        test.logger.lifecycle(String.format("  bmc4j < %-7s %s.%s (%s%.1fs)",
                                outcome, simpleName(t.className), t.name,
                                if (cached) "cached verdict, " else "",
                                (r.endTime - r.startTime) / 1000.0))
                    }
                }

                /**
                 * True if the failure was an UNKNOWN verdict (`BmcUndecidedError`). The exception
                 * crosses the test-worker -> Gradle boundary as a `PlaceholderException` (its real
                 * class isn't on Gradle's classpath), so we match the synthesized message — which always
                 * carries the `(UNKNOWN)` verdict tag from BmcProofExtension — rather than the type.
                 */
                private fun isUndecided(r: TestResult): Boolean =
                        r.exceptions.any { e ->
                            generateSequence(e) { it.cause }
                                    .any { it.message?.contains("(UNKNOWN)") == true }
                        }
            })
            // The contracts processor emits enforce-@BmcProofs into the test output, so the test
            // task discovers and runs them with no extra wiring.
            test.doFirst {
                // A command-line -Dbmc.jbmc wins over bmc { jbmcPath } (lets you swap the engine binary
                // for one run; the verdict-cache key pins the binary's content hash, so the swap
                // invalidates cached verdicts).
                val override = System.getProperty("bmc.jbmc")?.takeUnless { it.isBlank() }
                        ?: ext.jbmcPath.orNull
                if (!override.isNullOrBlank()) {
                    test.systemProperty("bmc.jbmc", override)
                }
                // unwind: a command-line -Dbmc.unwind wins over the build default (same CLI-over-build
                // precedence as timeoutSeconds), so a one-off bound change reaches the test JVM and (via
                // the verdict-cache key) invalidates cached verdicts without editing the build.
                forwardCli(test, "bmc.unwind", ext.unwind.get().toString())
                // maxStringLength is read by the runtime from system properties; forward a
                // command-line override so the documented -D flag actually reaches the forked test JVM
                // (which doesn't inherit the Gradle JVM's properties) and busts the verdict cache.
                forwardCli(test, "bmc.maxStringLength", null)
                // Default per-proof timeout. A command-line -Dbmc.timeoutSeconds wins over
                // the build default, so don't clobber it if the user passed one to the Gradle JVM.
                val cliTimeout = System.getProperty("bmc.timeoutSeconds")
                if (!cliTimeout.isNullOrBlank()) {
                    test.systemProperty("bmc.timeoutSeconds", cliTimeout)
                } else if (ext.timeoutSeconds.isPresent) {
                    test.systemProperty("bmc.timeoutSeconds", ext.timeoutSeconds.get().toString())
                }
                // Verdict cache. Default ON; bmc { cache = false } disables it by setting
                // bmc.noCache=true for the test JVM. A command-line -Dbmc.noCache wins over the build
                // flag (so a one-off full re-verification doesn't need an edit), same precedence as
                // -Dbmc.timeoutSeconds above.
                val cliNoCache = System.getProperty("bmc.noCache")
                if (!cliNoCache.isNullOrBlank()) {
                    test.systemProperty("bmc.noCache", cliNoCache)
                } else if (!ext.cache.getOrElse(true)) {
                    test.systemProperty("bmc.noCache", "true")
                }

                // Point JBMC at the consumer's compiled models (empty path if none).
                test.systemProperty("bmc.userModels", bmcModel.output.classesDirs.asPath)

                // Nondet-stub policy. These are READ-TIME policy, NOT part of the verdict-cache
                // key — the stub FACT is cached, judged here — so a command-line flag wins over the build
                // default and flipping either re-judges cached greens without re-running proofs.
                forwardListOrCli(test, "bmc.allowStubs", ext.allowStubs.getOrElse(emptyList()))
                forwardListOrCli(test, "bmc.userPackages", ext.userPackages.getOrElse(emptyList()))
                val cliStrict = System.getProperty("bmc.strictStubs")
                if (!cliStrict.isNullOrBlank()) {
                    test.systemProperty("bmc.strictStubs", cliStrict)
                } else if (ext.strictStubs.getOrElse(false)) {
                    test.systemProperty("bmc.strictStubs", "true")
                }

                // Kotlin proof-parameter semantics (default: auto-assume non-null parameters
                // non-null). Unlike the read-time policies above this changes the analyzed bytecode,
                // so it IS part of the verdict-cache key; a command-line flag still wins.
                val cliKotlinParams = System.getProperty("bmc.kotlinNullableParams")
                if (!cliKotlinParams.isNullOrBlank()) {
                    test.systemProperty("bmc.kotlinNullableParams", cliKotlinParams)
                } else if (ext.kotlinNullableParams.getOrElse(false)) {
                    test.systemProperty("bmc.kotlinNullableParams", "true")
                }

                // User-model trust layer. The declared intents are READ-TIME policy (the model FACTS —
                // declarations + the classes present under src/bmcModel — are judged in the test JVM), so
                // like the stub policy a command-line flag wins and flipping it re-judges cached greens
                // without re-running proofs. Serialize the declarations one entry per ";;", each
                // "intent|fqn|rationale" (newline-free so it survives a -D flag).
                val modelEntries = ext.modelSpec.entries.getOrElse(emptyList())
                val cliModels = System.getProperty("bmc.models")
                if (!cliModels.isNullOrBlank()) {
                    test.systemProperty("bmc.models", cliModels)
                } else if (modelEntries.isNotEmpty()) {
                    test.systemProperty("bmc.models", modelEntries.joinToString(";;"))
                }
                val cliStrictModels = System.getProperty("bmc.strictModels")
                if (!cliStrictModels.isNullOrBlank()) {
                    test.systemProperty("bmc.strictModels", cliStrictModels)
                } else if (ext.strictModels.getOrElse(false)) {
                    test.systemProperty("bmc.strictModels", "true")
                }

                // SAT/SMT backend (default = built-in MiniSat). A command-line -Dbmc.solver wins over
                // the build default (so e.g. swapping the solver also invalidates the verdict cache).
                val cliSolver = System.getProperty("bmc.solver")
                if (!cliSolver.isNullOrBlank()) {
                    test.systemProperty("bmc.solver", cliSolver)
                } else if (ext.solver.isPresent) {
                    test.systemProperty("bmc.solver", ext.solver.get())
                }
                if (ext.solverCmd.isPresent) {
                    test.systemProperty("bmc.solverCmd", ext.solverCmd.get())
                }
                if (ext.solverPath.isPresent && ext.solverPath.get().isNotBlank()) {
                    test.systemProperty("bmc.solverPath", ext.solverPath.get())
                }

                // Run proofs concurrently — each spawns its own jbmc and proofs are independent, so
                // this scales near-linearly. JUnit 5 runs the @BmcProof methods on a fixed pool.
                val parallelism = ext.parallelism.get()
                if (parallelism > 1) {
                    test.systemProperty("junit.jupiter.execution.parallel.enabled", "true")
                    test.systemProperty("junit.jupiter.execution.parallel.mode.default", "concurrent")
                    test.systemProperty("junit.jupiter.execution.parallel.mode.classes.default", "concurrent")
                    test.systemProperty("junit.jupiter.execution.parallel.config.strategy", "fixed")
                    test.systemProperty("junit.jupiter.execution.parallel.config.fixed.parallelism",
                            parallelism.toString())
                }
            }
        }
    }
}

/**
 * Forward a `bmc.*` system property to the forked test JVM: a command-line `-D` value on
 * the Gradle JVM wins; otherwise `buildDefault` is used (when non-null). The test JVM does not
 * inherit the Gradle daemon's system properties, so without this a documented `-Dbmc.*` flag
 * would be silently dropped — and a flag that's part of the verdict-cache key must reach
 * the test JVM to invalidate correctly.
 */
private fun forwardCli(test: Test, key: String, buildDefault: String?) {
    val cli = System.getProperty(key)
    if (!cli.isNullOrBlank()) {
        test.systemProperty(key, cli)
    } else if (buildDefault != null) {
        test.systemProperty(key, buildDefault)
    }
}

/**
 * Forward a comma-separated `bmc.*` list property: a command-line `-D` value
 * on the Gradle JVM wins; otherwise the build-DSL list is joined with commas. Empty list / unset =
 * nothing forwarded (the runtime treats a missing property as "none"). The runtime splits on commas.
 */
private fun forwardListOrCli(test: Test, key: String, buildList: List<String>) {
    val cli = System.getProperty(key)
    if (!cli.isNullOrBlank()) {
        test.systemProperty(key, cli)
    } else if (buildList.isNotEmpty()) {
        test.systemProperty(key, buildList.joinToString(","))
    }
}

/** Last segment of a fully-qualified class name (drop the package) for compact progress lines. */
private fun simpleName(className: String?): String = className?.substringAfterLast('.') ?: "?"

private fun detectPlatformId(): String {
    val osName = System.getProperty("os.name", "")
    val osArch = System.getProperty("os.arch", "")
    val os = osName.lowercase()
    val arm = osArch.lowercase().let { it.contains("aarch64") || it.contains("arm") }
    // Match "windows", not "win": "darwin" contains "win" and must fall through to mac.
    return when {
        os.contains("windows") -> {
            // Keep this in sync with Platform.of: there is no windows-arm64 engine artifact, so
            // fail fast instead of adding the x64 engine (which would only run under emulation).
            if (arm) {
                throw GradleException(
                        "bmc4j has no engine for windows-arm64 (os.name=$osName, os.arch=$osArch). " +
                                "Supported: windows-x64, linux-x64, linux-x64-musl, linux-arm64, " +
                                "macos-x64, macos-arm64.")
            }
            "windows-x64"
        }
        os.contains("mac") || os.contains("darwin") -> if (arm) "macos-arm64" else "macos-x64"
        arm -> "linux-arm64"
        // Keep in sync with Platform.current(): a glibc and a musl x64 host both report Linux/amd64,
        // but the glibc jbmc can't exec under musl, so a musl/Alpine x64 host gets the musl engine
        // jar. The runtime's BundledEngine.isMuslLibc does the same probe (Alpine marker or ld-musl
        // loader); the plugin can't depend on bmc-runtime, so the check is inlined here.
        isMuslLibc() -> "linux-x64-musl"
        else -> "linux-x64"
    }
}

/**
 * True if this host uses the musl C library (Alpine) rather than glibc. Mirrors
 * `BundledEngine.isMuslLibc`: the Alpine release marker or a musl dynamic loader under
 * `/lib`|`/usr/lib`. The plugin can't depend on bmc-runtime, so the probe is duplicated.
 */
private fun isMuslLibc(): Boolean {
    if (java.io.File("/etc/alpine-release").exists()) {
        return true
    }
    return hasMuslLoader("/lib") || hasMuslLoader("/usr/lib")
}

private fun hasMuslLoader(dir: String): Boolean =
        java.io.File(dir).listFiles()?.any { it.name.startsWith("ld-musl-") } ?: false
