package org.bmc4j.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.testing.Test;
import org.gradle.api.tasks.testing.TestDescriptor;
import org.gradle.api.tasks.testing.TestListener;
import org.gradle.api.tasks.testing.TestResult;

/**
 * Wires bounded model checking into a JVM project. Applying it is all a consumer
 * needs:
 *
 * <pre>{@code
 * plugins { id("org.bmc4j") }
 * }</pre>
 *
 * It then:
 * <ul>
 *   <li>adds the {@code bmc-runtime} ({@code @BmcProof}, {@code Bmc}) and JUnit 5 to {@code testImplementation};</li>
 *   <li>adds the {@code bmc-engine-<platform>} jar — the JBMC binary bundled as an ordinary,
 *       integrity-verified dependency — to {@code testRuntimeOnly} (no download at test time);</li>
 *   <li>runs proofs as part of the normal {@code test} task.</li>
 * </ul>
 *
 * Set {@code bmc { jbmcPath = "..." }} to use a local binary instead of the bundled engine.
 */
public class BmcPlugin implements Plugin<Project> {

    /** Kept in sync with the root build version; runtime + engine publish at this coordinate. */
    static final String VERSION = "0.1.0";
    private static final String JUNIT_VERSION = "5.10.2";
    private static final String JUNIT_PLATFORM_LAUNCHER_VERSION = "1.10.2";
    private static final String GROUP = "org.bmc4j";

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(JavaPlugin.class);

        BmcExtensionConfig ext = project.getExtensions().create("bmc", BmcExtensionConfig.class);
        ext.getUnwind().convention(16);
        ext.getParallelism().convention(Runtime.getRuntime().availableProcessors());
        ext.getProgress().convention(true);
        ext.getCache().convention(true);

        project.getDependencies().add("testImplementation", GROUP + ":bmc-runtime:" + VERSION);
        project.getDependencies().add("testImplementation", "org.junit.jupiter:junit-jupiter:" + JUNIT_VERSION);
        // Gradle 9 stopped auto-adding the JUnit Platform launcher to the test runtime classpath;
        // a consumer's proofs would fail to start without it, so the plugin provides it.
        project.getDependencies().add("testRuntimeOnly",
                "org.junit.platform:junit-platform-launcher:" + JUNIT_PLATFORM_LAUNCHER_VERSION);
        // Kotlin consumers also get the Kotlin helpers (assumeValid { ... }). Added only when the
        // Kotlin JVM plugin is applied, so Java-only projects never pull in kotlin-stdlib.
        project.getPluginManager().withPlugin("org.jetbrains.kotlin.jvm", applied ->
                project.getDependencies().add("testImplementation", GROUP + ":bmc-kotlin:" + VERSION));
        // JBMC models for JDK types (e.g. java.time). On the analysis classpath only;
        // the real JVM ignores these java.* classes (bootstrap loader wins).
        project.getDependencies().add("testRuntimeOnly", GROUP + ":bmc-models:" + VERSION);

        // Method contracts: contracts live in src/test (@BmcContractsFor types), so the
        // processor runs on the TEST sources and generates replace-stubs, enforce-@BmcProofs, and a
        // manifest into the test output. Test code already has bmc-runtime, so the generated code
        // compiles; production code stays free of any bmc reference. No contracts -> nothing generated.
        project.getDependencies().add("testAnnotationProcessor", GROUP + ":bmc-contracts:" + VERSION);

        // A `src/bmcModel/` source set for consumer-authored JBMC models: a class here
        // (same fully-qualified name as a real one) shadows it on JBMC's analysis
        // classpath only. It is compiled but kept OFF the test runtime classpath, so
        // the real class still runs when tests execute. Models compile against the
        // project's test classpath + Bmc, so a model can return Bmc.anyInt(...).
        SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
        SourceSet bmcModel = sourceSets.create("bmcModel");
        project.afterEvaluate(p -> bmcModel.setCompileClasspath(p.files(
                p.getConfigurations().getByName("testCompileClasspath"),
                sourceSets.getByName("main").getOutput())));

        // Add the bundled engine for the host platform unless a local binary is configured.
        project.afterEvaluate(p -> {
            if (!ext.getJbmcPath().isPresent()) {
                String platform = detectPlatformId();
                p.getDependencies().add("testRuntimeOnly", GROUP + ":bmc-engine-" + platform + ":" + VERSION);
            }
        });

        // Stub report: aggregate the harvested nondet stubs (persisted in each verified
        // proof's verdict-cache entry) into a ranked most-hit list — a data-driven bmc-models backlog.
        // Run `test` first to populate the cache; then `bmcStubReport`.
        project.getTasks().register("bmcStubReport", BmcStubReportTask.class, task -> {
            task.setGroup("verification");
            task.setDescription("Rank the most-hit nondet-stubbed methods across the proof suite.");
            task.getCacheDir().set(project.getLayout().getBuildDirectory().dir("bmc4j/verdict-cache"));
            task.getReportFile().set(project.getLayout().getBuildDirectory().file("bmc4j/stub-report.txt"));
        });

        project.getTasks().withType(Test.class).configureEach(test -> {
            test.useJUnitPlatform();
            // Consumer models must be compiled before proofs run.
            test.dependsOn(bmcModel.getClassesTaskName());

            // Progress logging (issue: silence during proof runs looks like a hang). At lifecycle
            // level so it shows in a normal `gradlew test`: a header, a line as each proof starts and
            // finishes (with outcome + wall time), and a final tally. Toggle with bmc { progress }.
            test.doFirst(task -> {
                if (ext.getProgress().getOrElse(true)) {
                    StringBuilder note = new StringBuilder("unwind=").append(ext.getUnwind().get())
                            .append(", parallelism=").append(ext.getParallelism().get());
                    String sat = System.getProperty("bmc.externalSat");
                    if (sat != null && !sat.isBlank()) {
                        note.append(", externalSat");
                    }
                    test.getLogger().lifecycle("bmc4j: verifying proofs with JBMC (" + note + ")");
                }
            });
            // Cache-hit detection for the progress line. The extension prints a
            // "  bmc4j: <entryFunction> -> <VERDICT> (cached...)" marker to the test JVM's stdout
            // when a proof is served from the verdict cache - stdout Gradle normally swallows on
            // passing tests, but output EVENTS still reach listeners. Record which proofs carried
            // the marker so the finish line can say "(cached verdict, ...)" instead of looking like
            // a (suspiciously fast) engine run. Keyed by the entry-function FQN PARSED FROM THE
            // MESSAGE, not the event's descriptor: under parallel proof execution the descriptor
            // attribution of stdout events is unreliable (observed: one of two same-class hits
            // attributed, one not). ConcurrentHashMap because test workers report in parallel.
            java.util.concurrent.ConcurrentHashMap<String, Boolean> cachedProofs =
                    new java.util.concurrent.ConcurrentHashMap<>();
            java.util.regex.Pattern cachedMarker =
                    java.util.regex.Pattern.compile("bmc4j: (\\S+) -> \\S+ \\(cached");
            test.addTestOutputListener((descriptor, event) -> {
                String msg = event.getMessage();
                if (msg != null && msg.contains("(cached")) {
                    java.util.regex.Matcher m = cachedMarker.matcher(msg);
                    while (m.find()) {
                        cachedProofs.put(m.group(1), Boolean.TRUE);
                    }
                }
            });
            test.addTestListener(new TestListener() {
                @Override
                public void beforeSuite(TestDescriptor suite) {
                }

                @Override
                public void afterSuite(TestDescriptor suite, TestResult result) {
                    if (suite.getParent() == null && ext.getProgress().getOrElse(true)
                            && result.getTestCount() > 0) {
                        test.getLogger().lifecycle(String.format(
                                "bmc4j: %d proofs -> %d passed, %d refuted, %d skipped (%.1fs)",
                                result.getTestCount(), result.getSuccessfulTestCount(),
                                result.getFailedTestCount(), result.getSkippedTestCount(),
                                (result.getEndTime() - result.getStartTime()) / 1000.0));
                    }
                }

                @Override
                public void beforeTest(TestDescriptor t) {
                    if (ext.getProgress().getOrElse(true)) {
                        test.getLogger().lifecycle(
                                "  bmc4j > proving " + simpleName(t.getClassName()) + "." + t.getName());
                    }
                }

                @Override
                public void afterTest(TestDescriptor t, TestResult r) {
                    if (ext.getProgress().getOrElse(true)) {
                        String outcome;
                        if (r.getResultType() == TestResult.ResultType.SUCCESS) {
                            outcome = "OK";
                        } else if (r.getResultType() == TestResult.ResultType.SKIPPED) {
                            outcome = "SKIP";
                        } else {
                            // Distinguish UNKNOWN (undecided within budget) from a real REFUTED so the
                            // progress log doesn't call a timeout/engine-error a refutation.
                            outcome = isUndecided(r) ? "UNKNOWN" : "REFUTED";
                        }
                        // A proof served from the verdict cache says so - a 0.0s "OK" otherwise
                        // reads as either suspicious or as engine speed it didn't earn. The map key
                        // is the entry-function FQN the extension printed (class.method, no parens);
                        // the JUnit display name carries "method(...)", so strip the parameter list.
                        String bareName = t.getName().replaceFirst("\\(.*\\)$", "");
                        boolean cached = cachedProofs.remove(t.getClassName() + "." + bareName) != null;
                        test.getLogger().lifecycle(String.format("  bmc4j < %-7s %s.%s (%s%.1fs)",
                                outcome, simpleName(t.getClassName()), t.getName(),
                                cached ? "cached verdict, " : "",
                                (r.getEndTime() - r.getStartTime()) / 1000.0));
                    }
                }

                /**
                 * True if the failure was an UNKNOWN verdict ({@code BmcUndecidedError}). The exception
                 * crosses the test-worker → Gradle boundary as a {@code PlaceholderException} (its real
                 * class isn't on Gradle's classpath), so we match the synthesized message — which always
                 * carries the {@code (UNKNOWN)} verdict tag from BmcProofExtension — rather than the type.
                 */
                private boolean isUndecided(TestResult r) {
                    for (Throwable e : r.getExceptions()) {
                        for (Throwable c = e; c != null; c = c.getCause()) {
                            String msg = c.getMessage();
                            if (msg != null && msg.contains("(UNKNOWN)")) {
                                return true;
                            }
                        }
                    }
                    return false;
                }
            });
            // The contracts processor emits enforce-@BmcProofs into the test output, so the test
            // task discovers and runs them with no extra wiring.
            test.doFirst(task -> {
                // A command-line -Dbmc.jbmc wins over bmc { jbmcPath } (lets you swap the engine binary
                // for one run; the verdict-cache key pins the binary's content hash, so the swap
                // invalidates cached verdicts).
                String cliJbmc = System.getProperty("bmc.jbmc");
                String override = (cliJbmc != null && !cliJbmc.isBlank()) ? cliJbmc : ext.getJbmcPath().getOrNull();
                if (override != null && !override.isBlank()) {
                    test.systemProperty("bmc.jbmc", override);
                }
                // unwind: a command-line -Dbmc.unwind wins over the build default (same CLI-over-build
                // precedence as timeoutSeconds), so a one-off bound change reaches the test JVM and (via
                // the verdict-cache key) invalidates cached verdicts without editing the build.
                forwardCli(test, "bmc.unwind", String.valueOf(ext.getUnwind().get()));
                // maxStringLength is read by the runtime from system properties; forward a
                // command-line override so the documented -D flag actually reaches the forked test JVM
                // (which doesn't inherit the Gradle JVM's properties) and busts the verdict cache.
                forwardCli(test, "bmc.maxStringLength", null);
                // Default per-proof timeout. A command-line -Dbmc.timeoutSeconds wins over
                // the build default, so don't clobber it if the user passed one to the Gradle JVM.
                String cliTimeout = System.getProperty("bmc.timeoutSeconds");
                if (cliTimeout != null && !cliTimeout.isBlank()) {
                    test.systemProperty("bmc.timeoutSeconds", cliTimeout);
                } else if (ext.getTimeoutSeconds().isPresent()) {
                    test.systemProperty("bmc.timeoutSeconds",
                            String.valueOf(ext.getTimeoutSeconds().get()));
                }
                // Verdict cache. Default ON; bmc { cache = false } disables it by setting
                // bmc.noCache=true for the test JVM. A command-line -Dbmc.noCache wins over the build
                // flag (so a one-off full re-verification doesn't need an edit), same precedence as
                // -Dbmc.timeoutSeconds above.
                String cliNoCache = System.getProperty("bmc.noCache");
                if (cliNoCache != null && !cliNoCache.isBlank()) {
                    test.systemProperty("bmc.noCache", cliNoCache);
                } else if (!ext.getCache().getOrElse(true)) {
                    test.systemProperty("bmc.noCache", "true");
                }

                // Point JBMC at the consumer's compiled models (empty path if none).
                test.systemProperty("bmc.userModels",
                        bmcModel.getOutput().getClassesDirs().getAsPath());

                // Nondet-stub policy. These are READ-TIME policy, NOT part of the verdict-cache
                // key — the stub FACT is cached, judged here — so a command-line flag wins over the build
                // default and flipping either re-judges cached greens without re-running proofs.
                forwardListOrCli(test, "bmc.allowStubs", ext.getAllowStubs().getOrElse(java.util.List.of()));
                forwardListOrCli(test, "bmc.userPackages", ext.getUserPackages().getOrElse(java.util.List.of()));
                String cliStrict = System.getProperty("bmc.strictStubs");
                if (cliStrict != null && !cliStrict.isBlank()) {
                    test.systemProperty("bmc.strictStubs", cliStrict);
                } else if (ext.getStrictStubs().getOrElse(false)) {
                    test.systemProperty("bmc.strictStubs", "true");
                }

                // Kotlin proof-parameter semantics (default: auto-assume non-null parameters
                // non-null). Unlike the read-time policies above this changes the analyzed bytecode,
                // so it IS part of the verdict-cache key; a command-line flag still wins.
                String cliKotlinParams = System.getProperty("bmc.kotlinNullableParams");
                if (cliKotlinParams != null && !cliKotlinParams.isBlank()) {
                    test.systemProperty("bmc.kotlinNullableParams", cliKotlinParams);
                } else if (ext.getKotlinNullableParams().getOrElse(false)) {
                    test.systemProperty("bmc.kotlinNullableParams", "true");
                }

                // User-model trust layer. The declared intents are READ-TIME policy (the model FACTS —
                // declarations + the classes present under src/bmcModel — are judged in the test JVM), so
                // like the stub policy a command-line flag wins and flipping it re-judges cached greens
                // without re-running proofs. Serialize the declarations one entry per ";;", each
                // "intent|fqn|rationale" (newline-free so it survives a -D flag).
                java.util.List<String> modelEntries =
                        ext.getModelSpec().getEntries().getOrElse(java.util.List.of());
                String cliModels = System.getProperty("bmc.models");
                if (cliModels != null && !cliModels.isBlank()) {
                    test.systemProperty("bmc.models", cliModels);
                } else if (!modelEntries.isEmpty()) {
                    test.systemProperty("bmc.models", String.join(";;", modelEntries));
                }
                String cliStrictModels = System.getProperty("bmc.strictModels");
                if (cliStrictModels != null && !cliStrictModels.isBlank()) {
                    test.systemProperty("bmc.strictModels", cliStrictModels);
                } else if (ext.getStrictModels().getOrElse(false)) {
                    test.systemProperty("bmc.strictModels", "true");
                }

                // SAT/SMT backend (default = built-in MiniSat). A command-line -Dbmc.solver wins over
                // the build default (so e.g. swapping the solver also invalidates the verdict cache).
                String cliSolver = System.getProperty("bmc.solver");
                if (cliSolver != null && !cliSolver.isBlank()) {
                    test.systemProperty("bmc.solver", cliSolver);
                } else if (ext.getSolver().isPresent()) {
                    test.systemProperty("bmc.solver", ext.getSolver().get());
                }
                if (ext.getSolverCmd().isPresent()) {
                    test.systemProperty("bmc.solverCmd", ext.getSolverCmd().get());
                }
                if (ext.getSolverPath().isPresent() && !ext.getSolverPath().get().isBlank()) {
                    test.systemProperty("bmc.solverPath", ext.getSolverPath().get());
                }

                // Run proofs concurrently — each spawns its own jbmc and proofs are independent, so
                // this scales near-linearly. JUnit 5 runs the @BmcProof methods on a fixed pool.
                int parallelism = ext.getParallelism().get();
                if (parallelism > 1) {
                    test.systemProperty("junit.jupiter.execution.parallel.enabled", "true");
                    test.systemProperty("junit.jupiter.execution.parallel.mode.default", "concurrent");
                    test.systemProperty("junit.jupiter.execution.parallel.mode.classes.default", "concurrent");
                    test.systemProperty("junit.jupiter.execution.parallel.config.strategy", "fixed");
                    test.systemProperty("junit.jupiter.execution.parallel.config.fixed.parallelism",
                            String.valueOf(parallelism));
                }
            });
        });
    }

    /**
     * Forward a {@code bmc.*} system property to the forked test JVM: a command-line {@code -D} value on
     * the Gradle JVM wins; otherwise {@code buildDefault} is used (when non-null). The test JVM does not
     * inherit the Gradle daemon's system properties, so without this a documented {@code -Dbmc.*} flag
     * would be silently dropped — and a flag that's part of the verdict-cache key must reach
     * the test JVM to invalidate correctly.
     */
    private static void forwardCli(Test test, String key, String buildDefault) {
        String cli = System.getProperty(key);
        if (cli != null && !cli.isBlank()) {
            test.systemProperty(key, cli);
        } else if (buildDefault != null) {
            test.systemProperty(key, buildDefault);
        }
    }

    /**
     * Forward a comma-separated {@code bmc.*} list property: a command-line {@code -D} value
     * on the Gradle JVM wins; otherwise the build-DSL list is joined with commas. Empty list / unset =
     * nothing forwarded (the runtime treats a missing property as "none"). The runtime splits on commas.
     */
    private static void forwardListOrCli(Test test, String key, java.util.List<String> buildList) {
        String cli = System.getProperty(key);
        if (cli != null && !cli.isBlank()) {
            test.systemProperty(key, cli);
        } else if (buildList != null && !buildList.isEmpty()) {
            test.systemProperty(key, String.join(",", buildList));
        }
    }

    /** Last segment of a fully-qualified class name (drop the package) for compact progress lines. */
    private static String simpleName(String className) {
        if (className == null) {
            return "?";
        }
        int dot = className.lastIndexOf('.');
        return dot >= 0 ? className.substring(dot + 1) : className;
    }

    private static String detectPlatformId() {
        String osName = System.getProperty("os.name", "");
        String osArch = System.getProperty("os.arch", "");
        String os = osName.toLowerCase();
        String arch = osArch.toLowerCase();
        boolean arm = arch.contains("aarch64") || arch.contains("arm");
        // Match "windows", not "win": "darwin" contains "win" and must fall through to mac.
        if (os.contains("windows")) {
            // Keep this in sync with Platform.of: there is no windows-arm64 engine artifact, so
            // fail fast instead of adding the x64 engine (which would only run under emulation).
            if (arm) {
                throw new org.gradle.api.GradleException(
                        "bmc4j has no engine for windows-arm64 (os.name=" + osName + ", os.arch="
                                + osArch + "). Supported: windows-x64, linux-x64, linux-arm64, "
                                + "macos-x64, macos-arm64.");
            }
            return "windows-x64";
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return arm ? "macos-arm64" : "macos-x64";
        }
        return arm ? "linux-arm64" : "linux-x64";
    }
}
