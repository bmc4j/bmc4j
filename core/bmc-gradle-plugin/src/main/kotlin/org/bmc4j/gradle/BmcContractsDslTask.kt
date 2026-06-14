package org.bmc4j.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor
import javax.inject.Inject

/**
 * Lowers the contracts DSL ([org.bmc4j.contracts.contractFor]) to generated enforce-proof classes by
 * EXECUTING the contracts (execute, then translate). After the contracts source set (the test classes,
 * which hold the top-level `val` contracts) compiles, this runs each contracts facade's `<clinit>` to
 * self-register the definitions, drains the registry, and writes one `<Facade>__BmcDslEnforce` `@BmcProof`
 * per facade into [outputDir]. The plugin adds that dir to the test classpath + JUnit discovery roots.
 *
 * Runs in a classloader-ISOLATED worker whose classpath is bmc-runtime (the DSL + registry + decoder),
 * exactly like [BmcMirrorClasspathTask], so the plugin's ABI never links bmc-runtime. The contracts and
 * the app they contract reach the worker via a separate classpath the worker loads reflectively.
 */
abstract class BmcContractsDslTask : DefaultTask() {

    /** The compiled contracts classes (the test output) to scan for `contractFor` facades. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val contractsClasses: ConfigurableFileCollection

    /** The classpath the contracts facade needs to LOAD: the app under contract + Kotlin stdlib + the
     *  consumer's dependencies (NOT bmc-runtime, which the worker already has on its parent loader). */
    @get:Classpath
    abstract val loadClasspath: ConfigurableFileCollection

    /** The bmc-runtime (+ relocated ASM) jars that perform the execute/decode, run in an isolated worker. */
    @get:Classpath
    abstract val dslWorker: ConfigurableFileCollection

    /** Where the generated enforce-proof classes land; added to the test classpath by the plugin. */
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    /** The Java executable the worker forks with - the consumer's test toolchain launcher, so the worker
     *  JVM can LOAD the consumer's compiled facade bytecode (which may target a newer Java than the Gradle
     *  daemon's JVM). Null falls back to in-daemon isolation (same-JVM, used when the targets match). */
    @get:org.gradle.api.tasks.Internal
    abstract val javaExecutable: Property<String>

    @get:Inject
    abstract val workerExecutor: WorkerExecutor

    @TaskAction
    fun lower() {
        val out = outputDir.get().asFile
        out.deleteRecursively()
        out.mkdirs()
        val contractDirs = contractsClasses.files.filter { it.isDirectory }.map { it.absolutePath }
        val loadCp = loadClasspath.files.map { it.absolutePath }
        // Execute-then-translate loads the consumer's compiled facade, so the worker JVM must understand
        // its bytecode version: fork a process on the consumer's test toolchain when one is wired (the
        // common case - the consumer targets a newer Java than the Gradle daemon). Otherwise stay in the
        // daemon (classloader isolation) where the bmc-runtime parent loader holds the registry directly.
        val exe = javaExecutable.orNull
        val queue = if (exe != null) {
            workerExecutor.processIsolation { spec ->
                spec.classpath.from(dslWorker)
                spec.forkOptions { fork -> fork.executable = exe }
            }
        } else {
            workerExecutor.classLoaderIsolation { spec ->
                spec.classpath.from(dslWorker)
            }
        }
        queue.submit(DslWorkAction::class.java) { params ->
            params.contractsClassesDirs.set(contractDirs)
            params.loadClasspath.set(loadCp)
            params.outputDir.set(out)
        }
        queue.await()
    }

    interface DslParams : WorkParameters {
        val contractsClassesDirs: org.gradle.api.provider.ListProperty<String>
        val loadClasspath: org.gradle.api.provider.ListProperty<String>
        val outputDir: DirectoryProperty
    }

    /** Invokes the bmc-runtime DSL lowering reflectively from the isolated worker classpath. */
    abstract class DslWorkAction : WorkAction<DslParams> {
        override fun execute() {
            val out = parameters.outputDir.get().asFile.toPath()
            val dirs = parameters.contractsClassesDirs.getOrElse(emptyList())
            val loadCp = parameters.loadClasspath.getOrElse(emptyList())
                    .map { java.nio.file.Path.of(it) }
            try {
                val cls = Class.forName("org.bmc4j.engine.GradleContractsDsl")
                val method = cls.getMethod("generate", java.nio.file.Path::class.java,
                        java.nio.file.Path::class.java, List::class.java)
                for (dir in dirs) {
                    method.invoke(null, java.nio.file.Path.of(dir), out, loadCp)
                }
            } catch (e: java.lang.reflect.InvocationTargetException) {
                val cause = e.targetException ?: e
                val sw = java.io.StringWriter()
                cause.printStackTrace(java.io.PrintWriter(sw))
                throw RuntimeException("bmc4j contracts-DSL lowering failed:\n$sw")
            }
        }
    }
}
