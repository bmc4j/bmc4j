package org.bmc4j.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * Unit tests for the user-model trust facts + policy: [ModelManifest] (parsing declarations,
 * scanning the `src/bmcModel` output for present classes) and [ModelPolicy] (declared vs
 * undeclared vs overriding). These exercise the pure fact-vs-policy core; the runtime wiring (footnote
 * text, strict-UNKNOWN) is covered in `BmcProofExtensionTest`.
 */
internal class ModelPolicyTest {

    @Test
    fun parse_roundTripsConformantAndDomain_withRationaleHoldingPipes() {
        val models = listOf(
                UserModel.conformant("acme.FastList"),
                UserModel.domain("acme.NoCollisionMap", "keys are UUIDs | collision-free"))
        val prop = ModelManifest.serialize(models)
        val m = ModelManifest.of(prop, "")
        assertEquals(2, m.declared().size)
        val d = m.declared()[1]
        assertTrue(d.isDomain)
        assertEquals("acme.NoCollisionMap", d.className)
        assertEquals("keys are UUIDs | collision-free", d.rationale,
                "a rationale may contain the field separator and must survive the round trip")
    }

    @Test
    fun parse_failsLoudlyOnUnknownIntentAndMissingDomainRationale() {
        assertThrows(IllegalArgumentException::class.java,
                { ModelManifest.of("bogus|acme.X|", "") },
                "an unknown intent must break the build, not silently drop the model")
        assertThrows(IllegalArgumentException::class.java,
                { ModelManifest.of("domain|acme.X|", "") },
                "a domain model with a blank rationale is a declaration bug")
    }

    @Test
    fun domainModel_requiresNonBlankRationale() {
        assertThrows(IllegalArgumentException::class.java) { UserModel.domain("acme.X", "  ") }
    }

    @Test
    fun scan_findsPresentTopLevelClasses_skipsNested(@TempDir dir: Path) {
        writeClass(dir, "acme.FastList")
        writeClass(dir, "acme.FastList\$Node") // nested: not a declared FQN
        writeClass(dir, "acme.NoCollisionMap")
        val m = ModelManifest.of("", dir.toString())
        assertTrue(m.presentClasses().contains("acme.FastList"))
        assertTrue(m.presentClasses().contains("acme.NoCollisionMap"))
        assertFalse(m.presentClasses().contains("acme.FastList\$Node"),
                "nested/synthetic classes are not declarable model FQNs")
    }

    @Test
    fun judge_declaredPresentVsUndeclaredVsOverriding(@TempDir dir: Path) {
        writeClass(dir, "acme.FastList")          // declared conformant
        writeClass(dir, "acme.NoCollisionMap")    // declared domain
        writeClass(dir, "acme.Sneaky")            // present but UNdeclared
        writeClass(dir, "java.util.HashMap")      // shadows a bundled/JDK verified model
        val prop = ModelManifest.serialize(listOf(
                UserModel.conformant("acme.FastList"),
                UserModel.domain("acme.NoCollisionMap", "no collisions")))
        val policy = ModelPolicy.judge(ModelManifest.of(prop, dir.toString()))

        assertEquals(2, policy.declaredPresent().size, "both declared models are present")
        assertTrue(policy.hasUndeclared())
        assertTrue(policy.undeclaredPresent().contains("acme.Sneaky"))
        assertTrue(policy.undeclaredPresent().contains("java.util.HashMap"),
                "an undeclared model is undeclared even if it shadows a JDK class")
        assertTrue(policy.hasOverriding())
        assertTrue(policy.overriding().contains("java.util.HashMap"),
                "shadowing a java.* class is flagged as overriding a bundled/verified model")
        assertFalse(policy.overriding().contains("acme.FastList"),
                "a plain user-package model is not an override of a bundled model")
    }

    @Test
    fun shadowsBundledModel_coversJdkAndKotlinRoots() {
        assertTrue(ModelPolicy.shadowsBundledModel("java.util.HashMap"))
        assertTrue(ModelPolicy.shadowsBundledModel("javax.crypto.Cipher"))
        assertTrue(ModelPolicy.shadowsBundledModel("kotlin.collections.CollectionsKt"))
        assertFalse(ModelPolicy.shadowsBundledModel("acme.FastList"))
    }

    companion object {
        /** Lay down empty `.class` files so the scanner sees those FQNs as "present". */
        @Throws(IOException::class)
        private fun writeClass(root: Path, fqn: String) {
            val p = root.resolve(fqn.replace('.', '/') + ".class")
            Files.createDirectories(p.parent)
            Files.write(p, byteArrayOf(0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte()))
        }
    }
}
