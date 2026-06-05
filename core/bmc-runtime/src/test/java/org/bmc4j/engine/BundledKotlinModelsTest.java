package org.bmc4j.engine;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Guards the bundling of the Kotlin models. A past "all coroutine proofs crash" bug was
 * caused by the model classes not being bundled/extracted; these tests fail loudly if the
 * resources go missing again.
 */
class BundledKotlinModelsTest {

    @Test
    void extracts_the_models_and_returns_a_real_directory() {
        String root = BundledKotlinModels.extractRoot();
        assertNotNull(root, "models should be bundled as resources and extract to a dir");
        assertTrue(Files.isDirectory(Path.of(root)));
    }

    @Test
    void critical_model_classes_are_present() {
        Path root = Path.of(BundledKotlinModels.extractRoot());
        // The two that broke before: the null-safety Intrinsics and a coroutine builder.
        assertTrue(Files.isRegularFile(root.resolve("kotlin/jvm/internal/Intrinsics.class")),
                "Intrinsics model missing — Kotlin null-safety proofs would crash");
        assertTrue(Files.isRegularFile(root.resolve("kotlinx/coroutines/BuildersKt.class")),
                "BuildersKt model missing — coroutine proofs would crash");
    }

    @Test
    void a_representative_spread_of_models_extracts() {
        Path root = Path.of(BundledKotlinModels.extractRoot());
        long classes;
        try (var walk = Files.walk(root)) {
            classes = walk.filter(p -> p.toString().endsWith(".class")).count();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        // The manifest lists ~24 model classes; if bundling silently empties, catch it.
        assertTrue(classes >= 15, "expected the bundled model set, found only " + classes);
    }
}
