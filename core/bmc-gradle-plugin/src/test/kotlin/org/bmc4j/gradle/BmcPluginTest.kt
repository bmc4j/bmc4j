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
    fun registers_the_bmc_extension_with_auto_unwind_and_cap_16() {
        val ext = applied().extensions.findByType(BmcExtensionConfig::class.java)
        assertNotNull(ext, "bmc extension")
        // AUTO by default (auto-discovery); the climb cap defaults to 16. A positive unwind PINS instead.
        assertEquals(-1, ext!!.unwind.get(), "unwind defaults to AUTO (-1)")
        assertEquals(16, ext.unwindCap.get(), "the climb cap defaults to 16")
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
    fun exposes_a_stringMode_property_defaulting_to_unset_refinement() {
        val ext = applied().extensions.findByType(BmcExtensionConfig::class.java)
        assertNotNull(ext, "bmc extension")
        // Unset by convention - the runtime treats absent as REFINEMENT (the default mode).
        assertTrue(!ext!!.stringMode.isPresent, "stringMode should be unset (refinement) by default")
        ext.stringMode.set("none")
        assertEquals("none", ext.stringMode.get())
    }

    @Test
    fun notModeledPackages_dsl_collects_globs_in_declaration_order() {
        val ext = applied().extensions.findByType(BmcExtensionConfig::class.java)!!
        ext.notModeledPackages { spec ->
            with(spec) {
                +"javax.swing.*"
                +"java.sql.*"
                pkg("java.nio.file.*")
            }
        }
        assertEquals(listOf("javax.swing.*", "java.sql.*", "java.nio.file.*"),
                ext.notModeledPackagesSpec.globs.get())
    }

    @Test
    fun creates_the_bmcModel_source_set() {
        val sourceSets = applied().extensions.getByType(SourceSetContainer::class.java)
        assertNotNull(sourceSets.findByName("bmcModel"), "bmcModel source set")
    }

    @Test
    fun a_java_only_project_does_not_apply_ksp() {
        // ksp + kspTest are Kotlin-only: a Java consumer must never get them. The legacy kapt path is
        // gone — assert neither the deprecated kapt nor KSP is applied.
        val p = applied()
        assertTrue(!p.plugins.hasPlugin("com.google.devtools.ksp"),
                "KSP must not be applied to a Java-only project")
        assertTrue(!p.plugins.hasPlugin("org.jetbrains.kotlin.kapt"),
                "the deprecated kapt must never be applied")
        assertTrue(p.configurations.findByName("kspTest") == null,
                "no kspTest configuration on a Java-only project")
    }

    @Test
    fun a_kotlin_project_gets_ksp_and_the_contracts_processor_on_kspTest() {
        val p = ProjectBuilder.builder().build()
        // Apply the Kotlin JVM plugin first, then bmc4j — the wiring runs inside
        // withPlugin("org.jetbrains.kotlin.jvm").
        p.pluginManager.apply("org.jetbrains.kotlin.jvm")
        p.pluginManager.apply(BmcPlugin::class.java)
        assertTrue(p.plugins.hasPlugin("com.google.devtools.ksp"),
                "the Kotlin path must apply KSP to host the contracts SymbolProcessor")
        assertTrue(!p.plugins.hasPlugin("org.jetbrains.kotlin.kapt"),
                "kapt is deprecated and must not be applied (replaced by KSP)")
        assertTrue(hasDependency(p, "kspTest", "bmc-contracts"),
                "bmc-contracts must be wired onto kspTest for a Kotlin consumer")
        // bmc-kotlin helpers come along too.
        assertTrue(hasDependency(p, "testImplementation", "bmc-kotlin"),
                "Kotlin consumers get the bmc-kotlin helpers")
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
