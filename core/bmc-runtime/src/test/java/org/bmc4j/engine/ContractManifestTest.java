package org.bmc4j.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ContractManifestTest {

    @Test
    void round_trips_contract_and_enforce_lines() {
        String text = ContractManifest.contractLine("pkg/C", "triangle", "(I)I", "pkg/C__Stubs", "triangle__stub")
                + "\n" + ContractManifest.enforceLine("pkg/C__Enforce");
        ContractManifest m = ContractManifest.parse(List.of(text.split("\n")));

        assertEquals(1, m.redirects().size());
        ContractRewriter.Redirect r = m.redirects().get(0);
        // verify the redirect actually rewrites pkg/C.triangle -> pkg/C__Stubs.triangle__stub
        assertTrue(r.matches("pkg/C", "triangle", "(I)I"));
        assertEquals("pkg/C__Stubs", r.stubOwner);
        assertEquals("triangle__stub", r.stubName);
        assertTrue(m.enforceProofClasses().contains("pkg/C__Enforce"));
    }

    @Test
    void ignores_blank_lines_comments_and_malformed_records() {
        ContractManifest m = ContractManifest.parse(List.of(
                "", "   ", "# a comment", "contract too few fields", "enforce", "bogus line"));
        assertTrue(m.isEmpty());
    }

    @Test
    void reads_and_merges_manifests_from_classpath_dirs(@TempDir Path a, @TempDir Path b) throws Exception {
        write(a, ContractManifest.contractLine("p/A", "f", "()I", "p/A__Stubs", "f__stub"));
        write(b, ContractManifest.enforceLine("p/A__Enforce"));
        String cp = a + java.io.File.pathSeparator + b + java.io.File.pathSeparator + "nonexistent.jar";

        ContractManifest m = ContractManifest.readFromClasspath(cp);
        assertEquals(1, m.redirects().size());
        assertTrue(m.enforceProofClasses().contains("p/A__Enforce"));
        assertFalse(m.isEmpty());
    }

    private static void write(Path dir, String line) throws Exception {
        Path res = dir.resolve(ContractManifest.RESOURCE);
        Files.createDirectories(res.getParent());
        Files.writeString(res, line + "\n");
    }
}
