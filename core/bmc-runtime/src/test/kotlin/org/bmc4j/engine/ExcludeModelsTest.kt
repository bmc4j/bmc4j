package org.bmc4j.engine

import org.bmc4j.BmcProof
import org.bmc4j.ExcludeModels
import org.bmc4j.junit.BmcProofExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * Unit tests for `@ExcludeModels`: the annotation is parsed (method-level MERGED with class-level)
 * into a set of FQNs threaded onto [BmcRequest]; [excludeFromUserModels] drops those
 * classes' `.class` entries from the `bmc.userModels` overlay so the real class is analysed; and the
 * exclusion set is part of the verdict-cache identity ([VerdictCache.computeKey]).
 */
internal class ExcludeModelsTest {

    // --- annotation parsing / merge -------------------------------------------------------------

    private class PlainModel
    private class OtherModel

    @ExcludeModels(PlainModel::class)
    private class ClassLevelExcluded {
        @BmcProof
        fun inheritsClassExclusion() {
        }

        @BmcProof
        @ExcludeModels(OtherModel::class)
        fun addsMethodExclusion() {
        }
    }

    private class NoClassExclusion {
        @BmcProof
        fun none() {
        }

        @BmcProof
        @ExcludeModels(PlainModel::class, OtherModel::class)
        fun methodOnly() {
        }
    }

    @Test
    fun resolve_noAnnotation_isEmpty() {
        val m = NoClassExclusion::class.java.getDeclaredMethod("none")
        assertTrue(BmcProofExtension.resolveExcludedModels(m).isEmpty(),
                "a proof with no @ExcludeModels must resolve to the empty set")
    }

    @Test
    fun resolve_methodLevel_namesTheExcludedClasses() {
        val m = NoClassExclusion::class.java.getDeclaredMethod("methodOnly")
        assertEquals(setOf(PlainModel::class.java.name, OtherModel::class.java.name),
                BmcProofExtension.resolveExcludedModels(m))
    }

    @Test
    fun resolve_classLevel_appliesToEveryProof() {
        val m = ClassLevelExcluded::class.java.getDeclaredMethod("inheritsClassExclusion")
        assertEquals(setOf(PlainModel::class.java.name),
                BmcProofExtension.resolveExcludedModels(m))
    }

    @Test
    fun resolve_methodMergesWithClassLevel() {
        val m = ClassLevelExcluded::class.java.getDeclaredMethod("addsMethodExclusion")
        assertEquals(setOf(PlainModel::class.java.name, OtherModel::class.java.name),
                BmcProofExtension.resolveExcludedModels(m),
                "a method-level @ExcludeModels must MERGE with (not replace) the class-level set")
    }

    // --- overlay filtering ----------------------------------------------------------------------

    /** Lay out a fake bmcModel output dir with the given dotted FQNs as empty `.class` files. */
    private fun modelDir(root: Path, vararg fqns: String): String {
        for (fqn in fqns) {
            val rel = fqn.replace('.', '/') + ".class"
            val f = root.resolve(rel)
            Files.createDirectories(f.parent)
            Files.write(f, ByteArray(0))
        }
        return root.toString()
    }

    private fun classFilesUnder(dir: String): Set<String> {
        val root = Path.of(dir)
        val out = HashSet<String>()
        Files.walk(root).use { walk ->
            walk.forEach { p ->
                if (!Files.isDirectory(p) && p.toString().endsWith(".class")) {
                    out.add(root.relativize(p).toString().replace('\\', '/'))
                }
            }
        }
        return out
    }

    @Test
    fun exclude_emptySet_returnsOverlayUnchanged(@TempDir tmp: Path) {
        val um = modelDir(tmp, "com.ex.Foo", "com.ex.Bar")
        assertEquals(um, excludeFromUserModels(um, emptySet()),
                "no exclusions must leave the overlay classpath byte-identical")
    }

    @Test
    fun exclude_dropsNamedModel_keepsOthers(@TempDir tmp: Path) {
        val um = modelDir(tmp, "com.ex.Foo", "com.ex.Bar")
        val filtered = excludeFromUserModels(um, setOf("com.ex.Foo"))
        assertNotEquals(um, filtered, "an exclusion that hits this dir must produce a filtered copy")
        val kept = classFilesUnder(filtered)
        assertFalse(kept.contains("com/ex/Foo.class"), "the excluded model must be dropped: $kept")
        assertTrue(kept.contains("com/ex/Bar.class"), "a non-excluded model must survive: $kept")
    }

    @Test
    fun exclude_dropsNestedClassesOfExcludedModel(@TempDir tmp: Path) {
        val um = modelDir(tmp, "com.ex.Foo", "com.ex.Foo\$Inner", "com.ex.Bar")
        val kept = classFilesUnder(excludeFromUserModels(um, setOf("com.ex.Foo")))
        assertFalse(kept.contains("com/ex/Foo.class"))
        assertFalse(kept.contains("com/ex/Foo\$Inner.class"),
                "a nested class of an excluded model must be dropped too: $kept")
        assertTrue(kept.contains("com/ex/Bar.class"))
    }

    @Test
    fun exclude_onlyRewritesDirsThatContainAnExcludedClass(@TempDir tmp: Path) {
        // Two model dirs: only the first holds the excluded class. The untouched dir must pass through.
        val dirA = modelDir(Files.createDirectory(tmp.resolve("a")), "com.ex.Foo")
        val dirB = modelDir(Files.createDirectory(tmp.resolve("b")), "com.ex.Bar")
        val cp = dirA + File.pathSeparator + dirB
        val filtered = excludeFromUserModels(cp, setOf("com.ex.Foo"))
        val entries = filtered.split(File.pathSeparator)
        assertEquals(2, entries.size)
        assertNotEquals(dirA, entries[0], "the dir holding the excluded class must be a filtered copy")
        assertEquals(dirB, entries[1], "a dir with no excluded class must pass through unchanged")
    }

    // --- verdict-cache keying -------------------------------------------------------------------

    private fun request(exclude: Set<String>): BmcRequest =
            BmcRequest("pkg.C", "pkg.C.proof", "/cp", 16, true, 16, "", 0,
                    excludeModels = exclude)

    @Test
    fun cacheKey_absentExclusion_keysIdenticallyToNoAnnotation() {
        assertEquals(VerdictCache.computeKey(request(emptySet()), "engine-id"),
                VerdictCache.computeKey(request(emptySet()), "engine-id"),
                "an empty exclusion set must not perturb the verdict-cache key")
    }

    @Test
    fun cacheKey_changesWhenAModelIsExcluded() {
        assertNotEquals(VerdictCache.computeKey(request(emptySet()), "engine-id"),
                VerdictCache.computeKey(request(setOf("com.ex.Foo")), "engine-id"),
                "excluding a model must bust the verdict cache (it links different bytecode)")
    }

    @Test
    fun cacheKey_isOrderIndependent() {
        assertEquals(
                VerdictCache.computeKey(request(setOf("com.ex.Foo", "com.ex.Bar")), "engine-id"),
                VerdictCache.computeKey(request(setOf("com.ex.Bar", "com.ex.Foo")), "engine-id"),
                "the exclusion set's iteration order must not change the key")
    }
}
