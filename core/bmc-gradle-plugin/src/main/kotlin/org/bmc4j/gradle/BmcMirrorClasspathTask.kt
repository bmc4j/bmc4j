package org.bmc4j.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor
import java.io.File
import javax.inject.Inject

/**
 * Pre-computes the environment-INDEPENDENT half of bmc4j's classpath rewrite — the six desugar passes
 * (coroutine-LVT strip, String content ops, lambda/method-ref `invokedynamic`, pattern-switch
 * `invokedynamic`, residual-`invokedynamic` marker, integer `Math.*`) — as a CACHEABLE Gradle unit of
 * work, instead of paying it at test runtime inside the JUnit extension.
 *
 * Those six passes are pure functions of the input bytecode (no env, no system property, no per-proof
 * state) and are the expensive part of preparing a real consumer classpath (bmc4j + Spring + Kotlin
 * jars) for JBMC. Hoisting them here lets Gradle's own up-to-date checks and build cache own the result:
 * an unchanged analysis classpath replays as `UP-TO-DATE` / `FROM-CACHE`, and `setup-gradle`'s existing
 * `~/.gradle/caches/build-cache-1` persistence reuses it across CI runs for free — with no per-consumer
 * workflow change. The runtime extension then substitutes the mirrored entries (via `bmc.gradleMirrorDir`)
 * and skips those passes; the remaining environment / per-proof passes (config bake, Kotlin params,
 * contracts, domain split, vacuity, slicing) still run at test time exactly as before.
 *
 * ## Cacheable + relocatable
 *
 * - [analysisClasspath] is a `@Classpath` input: order-sensitive but path- and timestamp-insensitive, so
 *   the cache key is portable across runners and reuses a hit whenever the bytes are unchanged.
 * - [mirrorClasspath] (the bmc-runtime + ASM jars that DO the rewrite) is also a `@Classpath` input, so a
 *   change to the rewrite code — equivalently, to `Bmc4jVersion.IDENTITY`, which is stamped into the
 *   bmc-runtime jar manifest — re-runs the task. The worker additionally stamps the identity into the
 *   manifest, and the runtime re-checks it before trusting the mirror, so a stale rewrite can never be
 *   served (over-invalidate, never under).
 * - The output is content-hashed subdirectories plus a `manifest.txt` of (original entry -> mirrored path
 *   relative to the output dir), so the mirror is itself relocatable.
 *
 * The rewrite runs in a classloader-ISOLATED worker so bmc-runtime (and its relocated ASM) never touches
 * the Gradle daemon's classloader — the same reason the plugin keeps a thin dependency surface.
 */
@CacheableTask
abstract class BmcMirrorClasspathTask : DefaultTask() {

    /** The analysis classpath to mirror: the test runtime classpath plus the consumer's bmcModel output.
     *  `@Classpath` = order-sensitive, path/timestamp-insensitive (relocatable cache key). */
    @get:Classpath
    abstract val analysisClasspath: ConfigurableFileCollection

    /** The bmc-runtime (+ relocated ASM) jars that perform the rewrite, run in an isolated worker.
     *  `@Classpath` so a rewrite-code / version change (i.e. a `Bmc4jVersion.IDENTITY` change, stamped
     *  into the runtime jar) busts the cache and re-mirrors. */
    @get:Classpath
    abstract val mirrorClasspath: ConfigurableFileCollection

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
        val cp = analysisClasspath.files.filter { it.exists() }
                .joinToString(File.pathSeparator) { it.absolutePath }
        val queue = workerExecutor.classLoaderIsolation { spec ->
            spec.classpath.from(mirrorClasspath)
        }
        queue.submit(MirrorWorkAction::class.java) { params ->
            params.classpath.set(cp)
            params.outputDir.set(out)
        }
        queue.await()
    }

    interface MirrorParams : WorkParameters {
        val classpath: Property<String>
        val outputDir: DirectoryProperty
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
            // GradleClasspathMirror.mirror is @JvmStatic — invoke the static directly (null receiver). The
            // class is loaded from the isolated worker classpath (bmc-runtime), never the plugin's own.
            val cls = Class.forName("org.bmc4j.engine.GradleClasspathMirror")
            val method = cls.getMethod("mirror", String::class.java, java.nio.file.Path::class.java)
            method.invoke(null, cp, out)
        }
    }
}
