package org.bmc4j.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor
import java.io.File
import javax.inject.Inject

/**
 * Pre-computes the run-wide half of bmc4j's classpath rewrite as a CACHEABLE Gradle unit of work,
 * instead of paying it at test runtime inside the JUnit extension. The hoisted passes are:
 *
 *  - the six desugars (coroutine-LVT strip, String content ops, lambda/method-ref `invokedynamic`,
 *    pattern-switch `invokedynamic`, residual-`invokedynamic` marker, integer `Math.*`);
 *  - the `Config` bake — pins `Bmc.*From*("KEY")` to this run's real env/property value. The forked test
 *    JVM's `-D` config properties don't reach the worker JVM, so the plugin forwards them as
 *    [configProperties]; `Bmc.*FromEnv` reads the worker's inherited env. The worker records the resolved
 *    config in the manifest and the runtime re-validates it (and the @Input below re-runs the task on a
 *    config change), so a stale config bake is never served.
 *  - `KotlinParam` — relaxes the non-null parameter prologue inside `@BmcProof` methods (its only input
 *    beyond the bytecode is the run-wide `bmc.kotlinNullableParams` flag, recorded here as [kotlinNullableParams]);
 *  - `Reachability` — injects the vacuity marker into `@BmcProof` returns.
 *
 * Each is a full ASM walk of every entry, and under cold parallel proof lanes they contend on the
 * per-user `~/.cache` and balloon. Hoisting them here lets Gradle's own up-to-date checks and build cache
 * own the result: an unchanged classpath replays as `UP-TO-DATE` / `FROM-CACHE`, and `setup-gradle`'s
 * existing `~/.gradle/caches/build-cache-1` persistence reuses it across CI runs for free — with no
 * per-consumer workflow change. The runtime extension then substitutes the mirrored entries (via
 * `bmc.gradleMirrorDir`) and skips those passes; only the per-proof tail (contracts, domain split, purity
 * audit, model slice) runs at test time.
 *
 * ## Coverage
 *
 * The mirror covers the WHOLE analysis classpath: the resolved test runtime classpath ([analysisClasspath]),
 * the consumer's own freshly-compiled output ([projectClasses]) AND the `bmcModel` source-set output
 * ([bmcModelClasses]). The consumer's own dirs used to be left out (they reach the test JVM via
 * `java.class.path` separately from the resolved dependency classpath) and were rewritten in-JVM on every
 * cold run; covering them here means a normal Gradle run rewrites nothing at test time.
 *
 * ## Cacheable + relocatable
 *
 * - [analysisClasspath], [projectClasses], [bmcModelClasses] are `@Classpath` inputs: order-sensitive but
 *   path- and timestamp-insensitive, so the cache key is portable across runners and reuses a hit whenever
 *   the bytes are unchanged.
 * - [mirrorClasspath] (the bmc-runtime + ASM jars that DO the rewrite) is also a `@Classpath` input, so a
 *   change to the rewrite code — equivalently, to `Bmc4jVersion.IDENTITY`, which is stamped into the
 *   bmc-runtime jar manifest — re-runs the task. The worker additionally stamps the identity into the
 *   manifest, and the runtime re-checks it before trusting the mirror, so a stale rewrite can never be
 *   served (over-invalidate, never under).
 * - [kotlinNullableParams] is an `@Input`: it is the only run-wide flag any hoisted pass reads (KotlinParam),
 *   so flipping it re-runs the task. The worker also folds it into the per-entry mirror cache key, so an
 *   honest-JVM mirror never aliases the default one.
 * - The output is content-hashed subdirectories plus a `manifest.txt` of (original entry -> mirrored path
 *   relative to the output dir), so the mirror is itself relocatable.
 *
 * The rewrite runs in a classloader-ISOLATED worker so bmc-runtime (and its relocated ASM) never touches
 * the Gradle daemon's classloader — the same reason the plugin keeps a thin dependency surface.
 */
@CacheableTask
abstract class BmcMirrorClasspathTask : DefaultTask() {

    /** The resolved analysis classpath to mirror: the test runtime classpath (engine jar, bmc-runtime,
     *  models, third-party deps). `@Classpath` = order-sensitive, path/timestamp-insensitive (relocatable
     *  cache key). */
    @get:Classpath
    abstract val analysisClasspath: ConfigurableFileCollection

    /** The consumer's OWN freshly-compiled output (`compileJava` / `compileKotlin` main + test classes).
     *  These reach the test JVM via `java.class.path` independently of the resolved dependency classpath,
     *  so they must be covered here too or they would be rewritten in-JVM on every cold run. */
    @get:Classpath
    abstract val projectClasses: ConfigurableFileCollection

    /** The `bmcModel` source-set output (consumer-authored JBMC models). Kept off the test runtime
     *  classpath, so it is a distinct input; covering it here means a user model is rewritten (desugared)
     *  by the cacheable task rather than in-JVM. */
    @get:Classpath
    abstract val bmcModelClasses: ConfigurableFileCollection

    /** The bmc-runtime (+ relocated ASM) jars that perform the rewrite, run in an isolated worker.
     *  `@Classpath` so a rewrite-code / version change (i.e. a `Bmc4jVersion.IDENTITY` change, stamped
     *  into the runtime jar) busts the cache and re-mirrors. */
    @get:Classpath
    abstract val mirrorClasspath: ConfigurableFileCollection

    /** The run-wide `bmc.kotlinNullableParams` flag — the only flag KotlinParam reads. `@Input` so
     *  flipping it re-runs the task; the worker also folds it into the mirror cache key, so the two
     *  settings never alias. */
    @get:Input
    abstract val kotlinNullableParams: Property<Boolean>

    /** The config the Config bake must pin: the test task's configured system properties (the
     *  `Bmc.*FromProperty` source), forwarded so the worker JVM bakes the SAME constants the forked test
     *  JVM will resolve. `@Input` so a changed config value re-runs the task; the runtime additionally
     *  re-validates the baked config against the live environment, so a stale bake is never served even if
     *  this input under-captures (e.g. a `Bmc.*FromEnv` var, which is the worker's inherited env). */
    @get:Input
    abstract val configProperties: MapProperty<String, String>

    /** Where the mirrored classpath + manifest land — owned and cached by Gradle. */
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Inject
    abstract val workerExecutor: WorkerExecutor

    @TaskAction
    fun mirror() {
        val out = outputDir.get().asFile
        out.deleteRecursively()
        out.mkdirs()
        // Mirror the resolved dependency classpath, the consumer's own compiled output, AND the bmcModel
        // output as ONE classpath, so every entry the test JVM analyses is covered exactly once. Order
        // within the mirror is irrelevant — the runtime substitutes by original-entry key, not position.
        val cp = (analysisClasspath.files + projectClasses.files + bmcModelClasses.files)
                .filter { it.exists() }
                .map { it.absolutePath }
                .distinct()
                .joinToString(File.pathSeparator)
        val flag = kotlinNullableParams.getOrElse(false)
        val config = configProperties.getOrElse(mapOf())
        val queue = workerExecutor.classLoaderIsolation { spec ->
            spec.classpath.from(mirrorClasspath)
        }
        queue.submit(MirrorWorkAction::class.java) { params ->
            params.classpath.set(cp)
            params.outputDir.set(out)
            params.kotlinNullableParams.set(flag)
            params.configProperties.set(config)
        }
        queue.await()
    }

    interface MirrorParams : WorkParameters {
        val classpath: Property<String>
        val outputDir: DirectoryProperty
        val kotlinNullableParams: Property<Boolean>
        val configProperties: MapProperty<String, String>
    }

    /**
     * Runs in a classloader-isolated worker that has bmc-runtime on its classpath, so it can invoke the
     * real rewrite passes (the single source of truth) reflectively — the plugin's own ABI never links
     * against bmc-runtime, keeping the Gradle daemon classloader clean.
     */
    abstract class MirrorWorkAction : WorkAction<MirrorParams> {
        override fun execute() {
            val cp = parameters.classpath.get()
            val out = parameters.outputDir.get().asFile.toPath()
            val flag = parameters.kotlinNullableParams.getOrElse(false)
            val config = parameters.configProperties.getOrElse(mapOf())
            // GradleClasspathMirror.mirror is @JvmStatic — invoke the static directly (null receiver). The
            // class is loaded from the isolated worker classpath (bmc-runtime), never the plugin's own.
            try {
                val cls = Class.forName("org.bmc4j.engine.GradleClasspathMirror")
                val method = cls.getMethod("mirror", String::class.java, java.nio.file.Path::class.java,
                        java.lang.Boolean.TYPE, Map::class.java)
                method.invoke(null, cp, out, flag, config)
            } catch (e: java.lang.reflect.InvocationTargetException) {
                // Surface the REAL cause. A Gradle worker failure crosses the worker->daemon boundary by
                // serialization; the underlying exception (thrown from ASM / IO inside the rewrite) may not
                // be cleanly serializable, in which case Gradle drops it and prints only the generic
                // "A failure occurred while executing MirrorWorkAction" with no `Caused by`. Render the full
                // stack trace into a plain-String RuntimeException message so the actual error always reaches
                // the build log, even without --stacktrace.
                val cause = e.targetException ?: e
                val sw = java.io.StringWriter()
                cause.printStackTrace(java.io.PrintWriter(sw))
                throw RuntimeException("bmc4j classpath mirror failed:\n$sw")
            }
        }
    }
}
