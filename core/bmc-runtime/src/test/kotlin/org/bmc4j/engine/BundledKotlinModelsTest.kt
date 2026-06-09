package org.bmc4j.engine

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * Guards the bundling of the Kotlin models. A past "all coroutine proofs crash" bug was
 * caused by the model classes not being bundled/extracted; these tests fail loudly if the
 * resources go missing again.
 */
internal class BundledKotlinModelsTest {

    @Test
    fun extracts_the_models_and_returns_a_real_directory() {
        val root = BundledKotlinModels.extractRoot()
        assertNotNull(root, "models should be bundled as resources and extract to a dir")
        assertTrue(Files.isDirectory(Path.of(root)))
    }

    @Test
    fun critical_model_classes_are_present() {
        val root = Path.of(BundledKotlinModels.extractRoot())
        // The two that broke before: the null-safety Intrinsics and a coroutine builder.
        assertTrue(Files.isRegularFile(root.resolve("kotlin/jvm/internal/Intrinsics.class")),
                "Intrinsics model missing — Kotlin null-safety proofs would crash")
        assertTrue(Files.isRegularFile(root.resolve("kotlinx/coroutines/BuildersKt.class")),
                "BuildersKt model missing — coroutine proofs would crash")
    }

    @Test
    fun coroutine_core_type_hierarchy_is_bundled() {
        // The kotlin.coroutines.* core hierarchy must be bundled so a checkcast on a bundled coroutine
        // subtype (e.g. CoroutineDispatcher -> CoroutineContext, a state machine -> Continuation) resolves
        // its whole supertype chain within ONE classpath source. If these resolve against the real
        // kotlin-stdlib jar instead, JBMC has to lazily link the hierarchy across classpath sources and
        // can nondeterministically havoc the cast (a spurious "Dynamic cast check" refutation).
        val root = Path.of(BundledKotlinModels.extractRoot())
        for (rel in listOf(
                "kotlin/coroutines/Continuation.class",
                "kotlin/coroutines/CoroutineContext.class",
                "kotlin/coroutines/CoroutineContext\$Element.class",
                "kotlin/coroutines/ContinuationInterceptor.class",
                "kotlin/coroutines/AbstractCoroutineContextElement.class",
                "kotlin/coroutines/EmptyCoroutineContext.class")) {
            assertTrue(Files.isRegularFile(root.resolve(rel)),
                    "$rel missing — the coroutine cast hierarchy is no longer single-source")
        }
    }

    @Test
    fun a_representative_spread_of_models_extracts() {
        val root = Path.of(BundledKotlinModels.extractRoot())
        val classes: Long
        Files.walk(root).use { walk ->
            classes = walk.filter { p -> p.toString().endsWith(".class") }.count()
        }
        // The manifest lists ~24 model classes; if bundling silently empties, catch it.
        assertTrue(classes >= 15, "expected the bundled model set, found only $classes")
    }
}
