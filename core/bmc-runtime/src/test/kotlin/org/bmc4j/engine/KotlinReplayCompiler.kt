package org.bmc4j.engine

import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import java.nio.file.Path

/**
 * Test-only helper: compiles a generated Kotlin replay file in-process with the embedded Kotlin
 * compiler — the Kotlin analog of the `javax.tools` Java compiler the Java replay-writer test uses.
 * Proves a generated `.kt` is valid Kotlin (the "compiles when dropped into `src/test/kotlin`"
 * validation checkbox) without spawning a Gradle subproject.
 */
internal object KotlinReplayCompiler {

    /**
     * Compile [file] (the generated `.kt`) with the current JVM's classpath (so the JUnit
     * `@Test`/`import` it references resolve), writing classes under [outDir]. Returns true on a
     * clean compile.
     */
    fun compiles(file: Path, outDir: Path): Boolean {
        val args = K2JVMCompilerArguments().apply {
            freeArgs = listOf(file.toString())
            classpath = System.getProperty("java.class.path")
            destination = outDir.resolve("kt-classes").toString()
            noStdlib = true       // stdlib is already on java.class.path
            noReflect = true
            // Match the module's own target so embedded-compiler defaults don't drift.
            jvmTarget = "17"
        }
        val collector = object : MessageCollector {
            var hasErrors = false
            override fun clear() {}
            override fun hasErrors() = hasErrors
            override fun report(severity: CompilerMessageSeverity, message: String,
                                location: CompilerMessageSourceLocation?) {
                if (severity.isError) {
                    hasErrors = true
                    System.err.println("kotlinc: $message ${location ?: ""}")
                }
            }
        }
        val exit = K2JVMCompiler().exec(collector, org.jetbrains.kotlin.config.Services.EMPTY, args)
        return exit == ExitCode.OK && !collector.hasErrors
    }
}
