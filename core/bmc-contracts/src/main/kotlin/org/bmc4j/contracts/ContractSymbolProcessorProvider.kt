package org.bmc4j.contracts

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

/**
 * KSP entry point: builds the [ContractSymbolProcessor] for a Kotlin consumer's `kspTest` run. Wired
 * by the `org.bmc4j` Gradle plugin (KSP replaces the deprecated kapt for the Kotlin contracts path);
 * the javac [ContractProcessor] still serves pure-Java consumers via `testAnnotationProcessor`.
 */
class ContractSymbolProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
            ContractSymbolProcessor(environment.codeGenerator, environment.logger)
}
