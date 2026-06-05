package org.bmc4j.engine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the user-model trust facts + policy: {@link ModelManifest} (parsing declarations,
 * scanning the {@code src/bmcModel} output for present classes) and {@link ModelPolicy} (declared vs
 * undeclared vs overriding). These exercise the pure fact-vs-policy core; the runtime wiring (footnote
 * text, strict-UNKNOWN) is covered in {@code BmcProofExtensionTest}.
 */
class ModelPolicyTest {

    /** Lay down empty {@code .class} files so the scanner sees those FQNs as "present". */
    private static void writeClass(Path root, String fqn) throws IOException {
        Path p = root.resolve(fqn.replace('.', '/') + ".class");
        Files.createDirectories(p.getParent());
        Files.write(p, new byte[] {(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE});
    }

    @Test
    void parse_roundTripsConformantAndDomain_withRationaleHoldingPipes() {
        List<UserModel> models = List.of(
                UserModel.conformant("acme.FastList"),
                UserModel.domain("acme.NoCollisionMap", "keys are UUIDs | collision-free"));
        String prop = ModelManifest.serialize(models);
        ModelManifest m = ModelManifest.of(prop, "");
        assertEquals(2, m.declared().size());
        UserModel d = m.declared().get(1);
        assertTrue(d.isDomain());
        assertEquals("acme.NoCollisionMap", d.className());
        assertEquals("keys are UUIDs | collision-free", d.rationale(),
                "a rationale may contain the field separator and must survive the round trip");
    }

    @Test
    void parse_failsLoudlyOnUnknownIntentAndMissingDomainRationale() {
        assertThrows(IllegalArgumentException.class,
                () -> ModelManifest.of("bogus|acme.X|", ""),
                "an unknown intent must break the build, not silently drop the model");
        assertThrows(IllegalArgumentException.class,
                () -> ModelManifest.of("domain|acme.X|", ""),
                "a domain model with a blank rationale is a declaration bug");
    }

    @Test
    void domainModel_requiresNonBlankRationale() {
        assertThrows(IllegalArgumentException.class, () -> UserModel.domain("acme.X", "  "));
    }

    @Test
    void scan_findsPresentTopLevelClasses_skipsNested(@TempDir Path dir) throws IOException {
        writeClass(dir, "acme.FastList");
        writeClass(dir, "acme.FastList$Node"); // nested: not a declared FQN
        writeClass(dir, "acme.NoCollisionMap");
        ModelManifest m = ModelManifest.of("", dir.toString());
        assertTrue(m.presentClasses().contains("acme.FastList"));
        assertTrue(m.presentClasses().contains("acme.NoCollisionMap"));
        assertFalse(m.presentClasses().contains("acme.FastList$Node"),
                "nested/synthetic classes are not declarable model FQNs");
    }

    @Test
    void judge_declaredPresentVsUndeclaredVsOverriding(@TempDir Path dir) throws IOException {
        writeClass(dir, "acme.FastList");          // declared conformant
        writeClass(dir, "acme.NoCollisionMap");    // declared domain
        writeClass(dir, "acme.Sneaky");            // present but UNdeclared
        writeClass(dir, "java.util.HashMap");      // shadows a bundled/JDK verified model
        String prop = ModelManifest.serialize(List.of(
                UserModel.conformant("acme.FastList"),
                UserModel.domain("acme.NoCollisionMap", "no collisions")));
        ModelPolicy policy = ModelPolicy.judge(ModelManifest.of(prop, dir.toString()));

        assertEquals(2, policy.declaredPresent().size(), "both declared models are present");
        assertTrue(policy.hasUndeclared());
        assertTrue(policy.undeclaredPresent().contains("acme.Sneaky"));
        assertTrue(policy.undeclaredPresent().contains("java.util.HashMap"),
                "an undeclared model is undeclared even if it shadows a JDK class");
        assertTrue(policy.hasOverriding());
        assertTrue(policy.overriding().contains("java.util.HashMap"),
                "shadowing a java.* class is flagged as overriding a bundled/verified model");
        assertFalse(policy.overriding().contains("acme.FastList"),
                "a plain user-package model is not an override of a bundled model");
    }

    @Test
    void shadowsBundledModel_coversJdkAndKotlinRoots() {
        assertTrue(ModelPolicy.shadowsBundledModel("java.util.HashMap"));
        assertTrue(ModelPolicy.shadowsBundledModel("javax.crypto.Cipher"));
        assertTrue(ModelPolicy.shadowsBundledModel("kotlin.collections.CollectionsKt"));
        assertFalse(ModelPolicy.shadowsBundledModel("acme.FastList"));
    }
}
