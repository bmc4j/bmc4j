package org.bmc4j.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

/** Verifies the plugin's wiring with an in-memory {@link ProjectBuilder} project. */
class BmcPluginTest {

    private static Project applied() {
        Project p = ProjectBuilder.builder().build();
        p.getPluginManager().apply(BmcPlugin.class);
        return p;
    }

    @Test
    void applies_the_java_plugin() {
        assertTrue(applied().getPlugins().hasPlugin("java"));
    }

    @Test
    void registers_the_bmc_extension_with_default_unwind_16() {
        BmcExtensionConfig ext = applied().getExtensions().findByType(BmcExtensionConfig.class);
        assertNotNull(ext, "bmc extension");
        assertEquals(16, ext.getUnwind().get());
    }

    @Test
    void creates_the_bmcModel_source_set() {
        SourceSetContainer sourceSets = applied().getExtensions().getByType(SourceSetContainer.class);
        assertNotNull(sourceSets.findByName("bmcModel"), "bmcModel source set");
    }

    @Test
    void wires_runtime_junit_and_jdk_models_dependencies() {
        Project p = applied();
        assertTrue(hasDependency(p, "testImplementation", "bmc-runtime"),
                "bmc-runtime on testImplementation");
        assertTrue(hasDependency(p, "testImplementation", "junit-jupiter"),
                "JUnit on testImplementation");
        assertTrue(hasDependency(p, "testRuntimeOnly", "bmc-models"),
                "JDK models on testRuntimeOnly");
    }

    private static boolean hasDependency(Project p, String configuration, String depName) {
        for (Dependency d : p.getConfigurations().getByName(configuration).getDependencies()) {
            if (depName.equals(d.getName())) {
                return true;
            }
        }
        return false;
    }
}
