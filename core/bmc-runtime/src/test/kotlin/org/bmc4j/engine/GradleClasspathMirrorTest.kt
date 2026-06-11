package org.bmc4j.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Handle
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.ArrayList

/**
 * The Gradle mirror task and its runtime consumer. [GradleClasspathMirror.mirror] pre-applies the
 * run-wide passes (the six desugars + Config bake + KotlinParam + Reachability + NondetTag) into a Gradle-owned dir
 * + manifest; [GradleClasspathMirror.substitute] swaps the original entries for the mirrored ones in the
 * test JVM. These tests pin the round trip (mirrored bytecode is really rewritten), the manifest's
 * relocatability, BYTE-IDENTITY against the in-JVM pipeline over a representative classpath including a
 * project class dir, and the soundness gates (identity / Kotlin-param-flag mismatch and a missing mirror
 * fall back to the original classpath, never serve a stale rewrite).
 */
internal class GradleClasspathMirrorTest {

    @Test
    fun mirror_then_substitute_yields_desugared_bytecode(@TempDir tmp: Path) {
        val srcDir = tmp.resolve("classes")
        Files.createDirectories(srcDir)
        Files.write(srcDir.resolve("Sample.class"), sampleClass())
        val original = srcDir.toString()

        val out = tmp.resolve("mirror")
        GradleClasspathMirror.mirror(original, out)

        // The manifest exists and carries the two header lines: the identity (semantics + the Kotlin-param
        // flag the default mirror was produced under) and the resolved-config line.
        val manifest = out.resolve("manifest.txt")
        assertTrue(Files.isRegularFile(manifest), "mirror writes a manifest")
        val headerLines = Files.readAllLines(manifest, StandardCharsets.UTF_8)
        assertEquals("bmc4j-mirror-identity " + Bmc4jVersion.IDENTITY + "|knp=false", headerLines[0],
                "manifest header carries the runtime semantics identity + Kotlin-param flag")
        assertTrue(headerLines[1].startsWith("bmc4j-mirror-config "),
                "manifest carries the resolved-config line: ${headerLines[1]}")

        // substitute swaps the original entry for the mirrored one.
        val substituted = GradleClasspathMirror.substitute(original, out)
        assertNotEquals(original, substituted, "substitute must point at the mirrored entry")
        val mirroredClass = Path.of(substituted).resolve("Sample.class")
        assertTrue(Files.isRegularFile(mirroredClass), "mirrored Sample.class exists")

        // The six passes ran: String.equals redirected to BmcStrings and the concat indy desugared.
        val calls = methodCalls(Files.readAllBytes(mirroredClass))
        assertTrue(calls.contains(
                "INVOKESTATIC org/bmc4j/engine/BmcStrings.equals(Ljava/lang/String;Ljava/lang/Object;)Z"),
                "mirrored class must carry the String-shim redirect: $calls")
        assertFalse(invokeDynamics(Files.readAllBytes(mirroredClass)).any { it.contains("StringConcatFactory") },
                "the concat indy must be desugared in the mirrored class")
    }

    @Test
    fun manifest_paths_are_relative_to_the_output_dir(@TempDir tmp: Path) {
        val srcDir = tmp.resolve("classes")
        Files.createDirectories(srcDir)
        Files.write(srcDir.resolve("Sample.class"), sampleClass())
        val out = tmp.resolve("mirror")
        GradleClasspathMirror.mirror(srcDir.toString(), out)

        // Every mapped target (after the 2 header lines: identity + config) is RELATIVE (no drive letter /
        // leading slash) so the cache is relocatable.
        Files.readAllLines(out.resolve("manifest.txt"), StandardCharsets.UTF_8)
                .drop(2)
                .filter { it.isNotEmpty() }
                .forEach { line ->
                    val rel = line.substringAfter('\t')
                    assertFalse(Path.of(rel).isAbsolute, "manifest target must be relative: $rel")
                }
    }

    @Test
    fun substitute_falls_back_on_identity_mismatch(@TempDir tmp: Path) {
        val srcDir = tmp.resolve("classes")
        Files.createDirectories(srcDir)
        Files.write(srcDir.resolve("Sample.class"), sampleClass())
        val out = tmp.resolve("mirror")
        GradleClasspathMirror.mirror(srcDir.toString(), out)

        // Corrupt the identity header to a different runtime semantics.
        val manifest = out.resolve("manifest.txt")
        val lines = Files.readAllLines(manifest, StandardCharsets.UTF_8).toMutableList()
        lines[0] = "bmc4j-mirror-identity 0.0.0+rX-stale"
        Files.write(manifest, lines.joinToString("\n").toByteArray(StandardCharsets.UTF_8))

        assertEquals(srcDir.toString(), GradleClasspathMirror.substitute(srcDir.toString(), out),
                "a mirror from a different identity must NOT be trusted (fall back to the original)")
    }

    @Test
    fun substitute_falls_back_when_mirror_is_absent(@TempDir tmp: Path) {
        val original = tmp.resolve("classes").toString()
        val out = tmp.resolve("does-not-exist")
        assertEquals(original, GradleClasspathMirror.substitute(original, out),
                "a missing mirror dir must fall back to the original classpath")
    }

    @Test
    fun unmapped_entries_pass_through_substitution(@TempDir tmp: Path) {
        val srcDir = tmp.resolve("classes")
        Files.createDirectories(srcDir)
        Files.write(srcDir.resolve("Sample.class"), sampleClass())
        val out = tmp.resolve("mirror")
        GradleClasspathMirror.mirror(srcDir.toString(), out)

        // Substitute a classpath that includes an entry the mirror never saw: it must pass through
        // unchanged (the in-JVM passes still rewrite it), while the mapped entry is substituted.
        val foreign = tmp.resolve("foreign").toString()
        val input = srcDir.toString() + File.pathSeparator + foreign
        val substituted = GradleClasspathMirror.substitute(input, out).split(File.pathSeparator)
        assertEquals(2, substituted.size, "entry count preserved")
        assertNotEquals(srcDir.toString(), substituted[0], "the mapped entry is substituted")
        assertEquals(foreign, substituted[1], "the unmapped entry passes through unchanged")
    }

    @Test
    fun substitute_falls_back_on_kotlin_param_flag_mismatch(@TempDir tmp: Path) {
        // A mirror produced under the DEFAULT Kotlin-param semantics must not be trusted by an honest-JVM
        // run (kotlinNullableParams=true), even on disk — the flag is folded into the manifest identity.
        val srcDir = tmp.resolve("classes")
        Files.createDirectories(srcDir)
        Files.write(srcDir.resolve("Sample.class"), sampleClass())
        val out = tmp.resolve("mirror")
        GradleClasspathMirror.mirror(srcDir.toString(), out, false) // default-flag mirror

        val prev = System.getProperty("bmc.kotlinNullableParams")
        System.setProperty("bmc.kotlinNullableParams", "true")
        try {
            assertEquals(srcDir.toString(), GradleClasspathMirror.substitute(srcDir.toString(), out),
                    "a default-flag mirror must NOT be served to an honest-JVM run (fall back to original)")
            assertTrue(GradleClasspathMirror.coveredEntries(out).isEmpty(),
                    "coveredEntries is empty on a flag mismatch — the runtime rewrites in-JVM")
        } finally {
            if (prev == null) System.clearProperty("bmc.kotlinNullableParams")
            else System.setProperty("bmc.kotlinNullableParams", prev)
        }
    }

    @Test
    fun config_match_re_validates_the_baked_config(@TempDir tmp: Path) {
        // A class that reads Bmc.intFromProperty("cfg.k") — a config call site the bake pins.
        val srcDir = tmp.resolve("classes")
        Files.createDirectories(srcDir)
        Files.write(srcDir.resolve("ConfigSample.class"), configSample())
        val cp = srcDir.toString()
        val out = tmp.resolve("mirror")

        val prev = System.getProperty("cfg.k")
        System.setProperty("cfg.k", "7")
        try {
            // Bake the mirror with cfg.k=7 (passed as a worker config property, as the plugin forwards it).
            GradleClasspathMirror.mirror(cp, out, false, mapOf("cfg.k" to "7"))
            assertTrue(GradleClasspathMirror.configMatches(cp, out),
                    "config matches when the run resolves the SAME value the bake used")
            // Flip the live property: the baked constant (7) is now stale -> must NOT match.
            System.setProperty("cfg.k", "9")
            assertFalse(GradleClasspathMirror.configMatches(cp, out),
                    "config must NOT match when a consumed property changed since the bake")
        } finally {
            if (prev == null) System.clearProperty("cfg.k") else System.setProperty("cfg.k", prev)
        }
    }

    /**
     * The path-format match regression guard. Gradle's test worker can spell a `java.class.path` entry
     * differently than the task's resolved file path -- notably DOUBLED backslashes on Windows, a forward/
     * back slash mix, or a trailing separator. The mirror match must collapse all of these (via
     * [GradleClasspathMirror.canonicalKey]) so coverage is NON-EMPTY and substitution fires; a raw string
     * compare scored 0 hits and SILENTLY disabled the mirror on Windows for several releases. This pins
     * that every such re-spelling of a mirrored entry still: (a) is reported covered, (b) is substituted
     * for its mirror, and (c) does NOT trip the 0-match warning guard.
     */
    @Test
    fun mirror_matches_respelled_classpath_entries(@TempDir tmp: Path) {
        val srcDir = tmp.resolve("classes")
        Files.createDirectories(srcDir)
        Files.write(srcDir.resolve("Sample.class"), sampleClass())
        val out = tmp.resolve("mirror")
        GradleClasspathMirror.mirror(srcDir.toString(), out)

        // The "true" entry as the task resolved it, plus the spellings a worker / test JVM can produce for
        // the SAME location. canonicalKey must collapse every one to the manifest's canonical key.
        val canonical = srcDir.toString()
        val respellings = linkedMapOf(
                "canonical" to canonical,
                "doubled-backslash" to canonical.replace("\\", "\\\\"),
                "forward-slash" to canonical.replace(File.separatorChar, '/'),
                "trailing-separator" to canonical + File.separator,
                "trailing-dot" to canonical + File.separator + ".")

        for ((label, entry) in respellings) {
            // (a) coverage is reported for the re-spelled entry.
            assertTrue(GradleClasspathMirror.canonicalKey(entry) in GradleClasspathMirror.coveredEntries(out),
                    "$label entry must be reported covered: [$entry]")
            // (b) substitution swaps it for the mirror, not pass-through.
            val substituted = GradleClasspathMirror.substitute(entry, out)
            assertNotEquals(entry, substituted, "$label entry must be SUBSTITUTED, not passed through")
            assertTrue(Path.of(substituted).resolve("Sample.class").let { Files.isRegularFile(it) },
                    "$label substituted entry points at the mirrored Sample.class")
            // (c) the 0-match guard does NOT fire for a matching (re-spelled) classpath.
            assertEquals(1, GradleClasspathMirror.warnIfMirrorMatchedNothing(entry, out),
                    "$label entry must match the mirror (no 0-match warning)")
        }
    }

    /**
     * The silent-recurrence guard. The original Windows bug was indistinguishable from a legitimate
     * "nothing to substitute": a mirror that covers N>0 entries but matches 0 of the live classpath looks
     * exactly like "all-uncovered". This pins that [GradleClasspathMirror.warnIfMirrorMatchedNothing]
     * (a) returns 0 (the warned signal) on a total miss against a NON-EMPTY cover, and (b) is a silent
     * no-op when there is no trusted cover at all (an empty mirror is the honest full-in-JVM path, not a
     * bug). It never throws -- the guard warns, never fails, so it can't turn a legitimate disjoint
     * classpath into a false failure.
     */
    @Test
    fun zero_match_against_a_nonempty_cover_is_flagged(@TempDir tmp: Path) {
        val srcDir = tmp.resolve("classes")
        Files.createDirectories(srcDir)
        Files.write(srcDir.resolve("Sample.class"), sampleClass())
        val out = tmp.resolve("mirror")
        GradleClasspathMirror.mirror(srcDir.toString(), out)
        assertFalse(GradleClasspathMirror.coveredEntries(out).isEmpty(),
                "precondition: the mirror covers a non-empty set")

        // A classpath that shares NO entry with the mirror -> 0 matches against a non-empty cover -> warned.
        val foreign = tmp.resolve("totally-unrelated-dir").toString()
        assertEquals(0, GradleClasspathMirror.warnIfMirrorMatchedNothing(foreign, out),
                "a non-empty cover matched by zero live entries must be flagged (return 0)")

        // No trusted cover (missing mirror dir) -> empty cover -> a 0-match is the honest path, NOT flagged
        // as a bug. Still returns 0, but takes the empty-cover early-out (no warning side effect).
        val absent = tmp.resolve("no-mirror-here")
        assertEquals(0, GradleClasspathMirror.warnIfMirrorMatchedNothing(srcDir.toString(), absent),
                "an absent/empty mirror is the honest full-in-JVM path, returns 0 without warning")
    }

    /**
     * The headline soundness proof for the HOISTED set: the bytecode the cacheable Gradle task produces
     * ([GradleClasspathMirror.mirror]) must be byte-for-byte what the in-JVM run-wide pipeline
     * produces — `6-desugar (ClasspathMirror.mirrorAll) -> Config -> KotlinParam -> Reachability ->
     * NondetTag` — over a
     * representative classpath that INCLUDES a project class dir carrying a `@BmcProof` method (so
     * Reachability fires) with a kotlinc non-null parameter prologue (so KotlinParam fires), alongside a
     * String/concat/lambda/Math class (so the desugars fire). A divergence here is a soundness
     * regression: a covered entry would be analysed differently than an uncovered (in-JVM) one.
     */
    @Test
    fun mirror_is_byte_identical_to_the_in_jvm_env_independent_pipeline(@TempDir tmp: Path) {
        val prev = System.getProperty("bmc.mirrorParallelism")
        System.setProperty("bmc.mirrorParallelism", "1") // deterministic entry order for the diff
        try {
            // A dependency-style dir (desugar triggers) AND a project-style dir (a @BmcProof with a
            // non-null param prologue → KotlinParam + Reachability triggers), so every hoisted pass fires.
            val deps = tmp.resolve("deps")
            Files.createDirectories(deps)
            Files.write(deps.resolve("Sample.class"), sampleClass())
            val proj = tmp.resolve("proj")
            Files.createDirectories(proj)
            Files.write(proj.resolve("ProofSample.class"), proofSample())
            val classpath = deps.toString() + File.pathSeparator + proj.toString()

            // IN-JVM reference: the exact chain JbmcBackend.applyHoistablePasses runs (same entry points,
            // same order: 6-desugar -> Config -> KotlinParam -> Reachability -> NondetTag). Lands under
            // ~/.cache via the default ClasspathMirror root. The samples have no Bmc.*From* / Bmc.any* call
            // sites, so Config and NondetTag are no-ops here — but including them keeps the reference
            // faithful to the worker's pipeline.
            val inJvm = run {
                var cp = ClasspathMirror.mirrorAll(classpath)
                cp = ConfigBytecode.rewrite(cp)
                cp = KotlinParamBytecode.rewrite(cp)
                cp = ReachabilityBytecode.rewrite(cp)
                NondetTagBytecode.rewrite(cp)
            }
            // HOISTED: the cacheable task's worker entry point (default Kotlin-param flag = what the
            // in-JVM reference uses with no honest-JVM property set).
            val out = tmp.resolve("mirror")
            GradleClasspathMirror.mirror(classpath, out, false)
            val mirrored = GradleClasspathMirror.substitute(classpath, out)

            val inJvmEntries = inJvm.split(File.pathSeparator).filter { it.isNotEmpty() }
            val mirroredEntries = mirrored.split(File.pathSeparator).filter { it.isNotEmpty() }
            assertEquals(2, inJvmEntries.size, "in-JVM pipeline is 1:1 over the two entries")
            assertEquals(inJvmEntries.size, mirroredEntries.size,
                    "the hoisted mirror must be 1:1 over the same entries")

            for (i in inJvmEntries.indices) {
                val expected = collectClasses(Path.of(inJvmEntries[i]))
                val actual = collectClasses(Path.of(mirroredEntries[i]))
                assertEquals(expected.keys, actual.keys,
                        "entry $i: hoisted and in-JVM must emit the SAME class files")
                for ((name, bytes) in expected) {
                    org.junit.jupiter.api.Assertions.assertArrayEquals(bytes, actual[name],
                            "entry $i: class $name must be byte-for-byte identical (hoisted vs in-JVM)")
                }
            }
            // Sanity: the proof class really exercised KotlinParam + Reachability (not a vacuous match).
            val proofClass = collectClasses(Path.of(mirroredEntries[1]))["ProofSample.class"]!!
            val calls = methodCalls(proofClass)
            assertTrue(calls.any { it.contains("org/bmc4j/engine/BmcKotlin.assumeNotNullParameter") },
                    "KotlinParam must have rewritten the prologue intrinsic: $calls")
            assertTrue(calls.any { it.contains("java/lang/AssertionError.<init>") },
                    "Reachability must have injected the vacuity marker throw: $calls")
        } finally {
            if (prev == null) System.clearProperty("bmc.mirrorParallelism")
            else System.setProperty("bmc.mirrorParallelism", prev)
        }
    }

    companion object {
        private fun sampleClass(): ByteArray {
            val cw = org.objectweb.asm.ClassWriter(
                    org.objectweb.asm.ClassWriter.COMPUTE_MAXS or org.objectweb.asm.ClassWriter.COMPUTE_FRAMES)
            cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "Sample", null, "java/lang/Object", null)
            val eq = cw.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "eq",
                    "(Ljava/lang/String;Ljava/lang/String;)Z", null, null)
            eq.visitCode()
            eq.visitVarInsn(Opcodes.ALOAD, 0)
            eq.visitVarInsn(Opcodes.ALOAD, 1)
            eq.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "equals",
                    "(Ljava/lang/Object;)Z", false)
            eq.visitInsn(Opcodes.IRETURN)
            eq.visitMaxs(0, 0)
            eq.visitEnd()
            val wrap = cw.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "wrap",
                    "(Ljava/lang/String;)Ljava/lang/String;", null, null)
            wrap.visitCode()
            wrap.visitVarInsn(Opcodes.ALOAD, 0)
            val bsm = Handle(Opcodes.H_INVOKESTATIC, "java/lang/invoke/StringConcatFactory",
                    "makeConcatWithConstants",
                    "(Ljava/lang/invoke/MethodHandles\$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;" +
                            "Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;", false)
            wrap.visitInvokeDynamicInsn("makeConcatWithConstants",
                    "(Ljava/lang/String;)Ljava/lang/String;", bsm, "[]")
            wrap.visitInsn(Opcodes.ARETURN)
            wrap.visitMaxs(0, 0)
            wrap.visitEnd()
            cw.visitEnd()
            return cw.toByteArray()
        }

        private fun methodCalls(clazz: ByteArray): List<String> {
            val calls = ArrayList<String>()
            ClassReader(clazz).accept(object : ClassVisitor(Opcodes.ASM9) {
                override fun visitMethod(a: Int, n: String?, d: String?, s: String?,
                                         e: Array<String>?): MethodVisitor =
                        object : MethodVisitor(Opcodes.ASM9) {
                            override fun visitMethodInsn(op: Int, owner: String?, name: String?,
                                                         desc: String?, itf: Boolean) {
                                val kind = if (op == Opcodes.INVOKESTATIC) "INVOKESTATIC" else "INVOKE"
                                calls.add("$kind $owner.$name$desc")
                            }
                        }
            }, 0)
            return calls
        }

        private fun invokeDynamics(clazz: ByteArray): List<String> {
            val out = ArrayList<String>()
            ClassReader(clazz).accept(object : ClassVisitor(Opcodes.ASM9) {
                override fun visitMethod(a: Int, n: String?, d: String?, s: String?,
                                         e: Array<String>?): MethodVisitor =
                        object : MethodVisitor(Opcodes.ASM9) {
                            override fun visitInvokeDynamicInsn(name: String?, desc: String?,
                                                                bsm: Handle?, vararg args: Any?) {
                                out.add(bsm?.owner + "." + name)
                            }
                        }
            }, 0)
            return out
        }

        /**
         * A class with a `@BmcProof` method that (a) opens with the kotlinc non-null parameter prologue
         * `Intrinsics.checkNotNullParameter(p, "p")` (→ KotlinParam relaxes it) and (b) returns normally
         * (→ Reachability replaces the return with the vacuity-marker throw). Mirrors the bytecode kotlinc
         * emits for a `@BmcProof fun(p: String)` proof, so both hoisted proof-aware passes fire.
         */
        private fun proofSample(): ByteArray {
            val cw = org.objectweb.asm.ClassWriter(
                    org.objectweb.asm.ClassWriter.COMPUTE_MAXS or org.objectweb.asm.ClassWriter.COMPUTE_FRAMES)
            cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "ProofSample", null, "java/lang/Object", null)
            val m = cw.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "proof",
                    "(Ljava/lang/String;)V", null, null)
            // @org.bmc4j.BmcProof — what KotlinParam and Reachability key on.
            m.visitAnnotation("Lorg/bmc4j/BmcProof;", true).visitEnd()
            m.visitCode()
            // checkNotNullParameter(p, "p") prologue.
            m.visitVarInsn(Opcodes.ALOAD, 0)
            m.visitLdcInsn("p")
            m.visitMethodInsn(Opcodes.INVOKESTATIC, "kotlin/jvm/internal/Intrinsics",
                    "checkNotNullParameter", "(Ljava/lang/Object;Ljava/lang/String;)V", false)
            m.visitInsn(Opcodes.RETURN)
            m.visitMaxs(0, 0)
            m.visitEnd()
            cw.visitEnd()
            return cw.toByteArray()
        }

        /** A class reading `Bmc.intFromProperty("cfg.k")` — a config call site the Config bake pins to the
         *  run's value of the `cfg.k` property. */
        private fun configSample(): ByteArray {
            val cw = org.objectweb.asm.ClassWriter(
                    org.objectweb.asm.ClassWriter.COMPUTE_MAXS or org.objectweb.asm.ClassWriter.COMPUTE_FRAMES)
            cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "ConfigSample", null, "java/lang/Object", null)
            val m = cw.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "k", "()I", null, null)
            m.visitCode()
            m.visitLdcInsn("cfg.k")
            m.visitMethodInsn(Opcodes.INVOKESTATIC, "org/bmc4j/Bmc", "intFromProperty",
                    "(Ljava/lang/String;)I", false)
            m.visitInsn(Opcodes.IRETURN)
            m.visitMaxs(0, 0)
            m.visitEnd()
            cw.visitEnd()
            return cw.toByteArray()
        }

        /** Every `.class` file under [root], keyed by its path relative to [root] (sorted, stable). */
        private fun collectClasses(root: Path): Map<String, ByteArray> {
            val map = java.util.TreeMap<String, ByteArray>()
            Files.walk(root).use { walk ->
                walk.forEach { p ->
                    if (!Files.isDirectory(p) && p.toString().endsWith(".class")) {
                        map[root.relativize(p).toString().replace('\\', '/')] = Files.readAllBytes(p)
                    }
                }
            }
            return map
        }
    }
}
