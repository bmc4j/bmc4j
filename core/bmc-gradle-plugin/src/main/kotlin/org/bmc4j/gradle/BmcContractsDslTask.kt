package org.bmc4j.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor
import javax.inject.Inject

/**
 * Lowers the contracts DSL ([org.bmc4j.contracts.contractFor]) to generated enforce-proof classes. After
 * the test classes are compiled, this scans them for `@org.bmc4j.BmcContracts` registrations, decodes each
 * `contractFor(...)` site (reading the method reference + predicate lambdas statically from bytecode), and
 * writes one `<Class>__BmcDslEnforce` `@BmcProof` per registration into [outputDir]. The plugin adds that
 * dir to the test classpath, so JUnit discovers and runs the generated proofs - the DSL analogue of the
 * `@BmcContractsFor` processor's `__BmcEnforce` output.
 *
 * Runs in a classloader-ISOLATED worker whose classpath is bmc-runtime (which carries the decoder +
 * relocated ASM), exactly like [BmcMirrorClasspathTask], so the plugin's ABI never links bmc-runtime.
 */
abstract class BmcContractsDslTask : DefaultTask() {

    /** The compiled test classes to scan for `@BmcContracts` registrations. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val testClasses: ConfigurableFileCollection

    /** The bmc-runtime (+ relocated ASM) jars that perform the decode, run in an isolated worker. */
    @get:Classpath
    abstract val dslWorker: ConfigurableFileCollection

    /** Where the generated enforce-proof classes land; added to the test classpath by the plugin. */
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Inject
    abstract val workerExecutor: WorkerExecutor

    @TaskAction
    fun lower() {
        val out = outputDir.get().asFile
        out.deleteRecursively()
        out.mkdirs()
        // The consumer's test-classes dir(s). Decode each in turn; the worker is a no-op when a dir holds
        // no @BmcContracts registration, so passing every test-output root is safe.
        val dirs = testClasses.files.filter { it.isDirectory }.map { it.absolutePath }
        val queue = workerExecutor.classLoaderIsolation { spec ->
            spec.classpath.from(dslWorker)
        }
        queue.submit(DslWorkAction::class.java) { params ->
            params.testClassesDirs.set(dirs)
            params.outputDir.set(out)
        }
        queue.await()
    }

    interface DslParams : WorkParameters {
        val testClassesDirs: org.gradle.api.provider.ListProperty<String>
        val outputDir: DirectoryProperty
    }

    /** Invokes the bmc-runtime DSL lowering reflectively from the isolated worker classpath. */
    abstract class DslWorkAction : WorkAction<DslParams> {
        override fun execute() {
            val out = parameters.outputDir.get().asFile.toPath()
            val dirs = parameters.testClassesDirs.getOrElse(emptyList())
            try {
                val cls = Class.forName("org.bmc4j.engine.GradleContractsDsl")
                val method = cls.getMethod("generate", java.nio.file.Path::class.java,
                        java.nio.file.Path::class.java)
                for (dir in dirs) {
                    method.invoke(null, java.nio.file.Path.of(dir), out)
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
