package org.bmc4j.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

internal class ContractManifestTest {

    @Test
    fun round_trips_contract_and_enforce_lines() {
        val text = ContractManifest.contractLine("pkg/C", "triangle", "(I)I", "pkg/C__Stubs", "triangle__stub") +
                "\n" + ContractManifest.enforceLine("pkg/C__Enforce")
        val m = ContractManifest.parse(text.split("\n"))

        assertEquals(1, m.redirects().size)
        val r = m.redirects()[0]
        // verify the redirect actually rewrites pkg/C.triangle -> pkg/C__Stubs.triangle__stub
        assertTrue(r.matches("pkg/C", "triangle", "(I)I"))
        assertEquals("pkg/C__Stubs", r.stubOwner)
        assertEquals("triangle__stub", r.stubName)
        assertTrue(m.enforceProofClasses().contains("pkg/C__Enforce"))
    }

    @Test
    fun ignores_blank_lines_comments_and_malformed_records() {
        val m = ContractManifest.parse(listOf(
                "", "   ", "# a comment", "contract too few fields", "enforce", "bogus line"))
        assertTrue(m.isEmpty)
    }

    @Test
    fun reads_and_merges_manifests_from_classpath_dirs(@TempDir a: Path, @TempDir b: Path) {
        write(a, ContractManifest.contractLine("p/A", "f", "()I", "p/A__Stubs", "f__stub"))
        write(b, ContractManifest.enforceLine("p/A__Enforce"))
        val cp = a.toString() + File.pathSeparator + b.toString() + File.pathSeparator + "nonexistent.jar"

        val m = ContractManifest.readFromClasspath(cp)
        assertEquals(1, m.redirects().size)
        assertTrue(m.enforceProofClasses().contains("p/A__Enforce"))
        assertFalse(m.isEmpty)
    }

    companion object {
        private fun write(dir: Path, line: String) {
            val res = dir.resolve(ContractManifest.RESOURCE)
            Files.createDirectories(res.parent)
            Files.writeString(res, line + "\n")
        }
    }
}
