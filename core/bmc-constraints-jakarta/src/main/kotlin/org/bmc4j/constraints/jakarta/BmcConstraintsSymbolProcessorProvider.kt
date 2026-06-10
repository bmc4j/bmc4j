package org.bmc4j.constraints.jakarta

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

/**
 * KSP entry point: builds the [BmcConstraintsSymbolProcessor] for a Kotlin consumer's `kspTest` run,
 * so a Kotlin DTO's `jakarta.validation.constraints.*` annotations generate the same `assumeValid`
 * helper a Java DTO's do. Wired by the `org.bmc4j` Gradle plugin; the javac
 * [BmcConstraintsProcessor] still serves pure-Java consumers via `testAnnotationProcessor`.
 */
class BmcConstraintsSymbolProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
            BmcConstraintsSymbolProcessor(environment.codeGenerator, environment.logger)
}
