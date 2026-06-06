package org.bmc4j.gradle

import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Verifies the plugin's wiring with an in-memory [ProjectBuilder] project. */
class BmcPluginTest {

    private fun applied(): Project = ProjectBuilder.builder().build().also {
        it.pluginManager.apply(BmcPlugin::class.java)
    }

    @Test
    fun applies_the_java_plugin() {
        assertTrue(applied().plugins.hasPlugin("java"))
    }

    @Test
    fun registers_the_bmc_extension_with_default_unwind_16() {
        val ext = applied().extensions.findByType(BmcExtensionConfig::class.java)
        assertNotNull(ext, "bmc extension")
        assertEquals(16, ext!!.unwind.get())
    }

    @Test
    fun exposes_a_replayLanguage_property_defaulting_to_unset_auto() {
        val ext = applied().extensions.findByType(BmcExtensionConfig::class.java)
        assertNotNull(ext, "bmc extension")
        // Unset by convention — the runtime treats absent as `auto`.
        assertTrue(!ext!!.replayLanguage.isPresent, "replayLanguage should be unset (auto) by default")
        ext.replayLanguage.set("kotlin")
        assertEquals("kotlin", ext.replayLanguage.get())
    }

    @Test
    fun creates_the_bmcModel_source_set() {
        val sourceSets = applied().extensions.getByType(SourceSetContainer::class.java)
        assertNotNull(sourceSets.findByName("bmcModel"), "bmcModel source set")
    }

    @Test
    fun wires_runtime_junit_and_jdk_models_dependencies() {
        val p = applied()
        assertTrue(hasDependency(p, "testImplementation", "bmc-runtime"),
                "bmc-runtime on testImplementation")
        assertTrue(hasDependency(p, "testImplementation", "junit-jupiter"),
                "JUnit on testImplementation")
        assertTrue(hasDependency(p, "testRuntimeOnly", "bmc-models"),
                "JDK models on testRuntimeOnly")
    }

    private fun hasDependency(p: Project, configuration: String, depName: String): Boolean =
            p.configurations.getByName(configuration).dependencies.any { it.name == depName }
}
