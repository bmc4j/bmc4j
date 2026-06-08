package org.bmc4j.engine

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import java.nio.file.Files
import java.nio.file.Path

/**
 * The safe-by-default solver resolution + the text/String guard. These run with NO engine: they pin the
 * PURE policy — precedence, named-solver registry, and (the load-bearing part) that a text-using proof is
 * never routed to the fast (text-reasoning-off) external SAT solver.
 */
internal class SolverPlanTest {

    private val touchedProps = listOf(
            SolverPlan.STRING_FALLBACK_PROP, SolverPlan.UNSAFE_TEXT_OVERRIDE_PROP, "bmc.jbmc")
    private var saved: Map<String, String?> = emptyMap()

    @org.junit.jupiter.api.BeforeEach
    fun snapshotProps() {
        saved = touchedProps.associateWith { System.getProperty(it) }
        touchedProps.forEach { System.clearProperty(it) }
    }

    @AfterEach
    fun restoreProps() {
        saved.forEach { (k, v) -> if (v != null) System.setProperty(k, v) else System.clearProperty(k) }
    }

    // --- precedence -----------------------------------------------------------

    @Test
    fun perProofSolver_winsOverGlobalExternalSat(@TempDir dir: Path) {
        val fast = stageFakeSolver(dir, "fast-explicit")
        val global = stageFakeSolver(dir, "global")
        val cp = numericClasspath(dir)
        // per-proof explicit path AND a global external-sat path: the per-proof one wins.
        val d = SolverPlan.resolve(SolverPlan.SolverRequest(fast, global, "Entry", cp))
        assertInstanceOf(SolverPlan.Decision.ExternalSat::class.java, d)
        assertEquals(fast, (d as SolverPlan.Decision.ExternalSat).path,
                "per-proof solver must win over the global external-sat property")
    }

    @Test
    fun globalExternalSat_usedWhenNoPerProofSolver(@TempDir dir: Path) {
        val global = stageFakeSolver(dir, "global")
        val cp = numericClasspath(dir)
        val d = SolverPlan.resolve(SolverPlan.SolverRequest("", global, "Entry", cp))
        assertInstanceOf(SolverPlan.Decision.ExternalSat::class.java, d)
        assertEquals(global, (d as SolverPlan.Decision.ExternalSat).path)
    }

    @Test
    fun nothingRequested_isBuiltin(@TempDir dir: Path) {
        val d = SolverPlan.resolve(SolverPlan.SolverRequest("", "", "Entry", numericClasspath(dir)))
        assertInstanceOf(SolverPlan.Decision.Builtin::class.java, d)
        assertNull((d as SolverPlan.Decision.Builtin).note, "an un-overridden default solver logs nothing")
    }

    @Test
    fun builtinAndSmtNames_neverTextGuarded(@TempDir dir: Path) {
        // A text-USING proof with a built-in/SMT name must NOT fail loud — the guard only applies to
        // external SAT. These names are handled downstream by Jbmc.addSolver, not here.
        val cp = textClasspath(dir)
        for (name in listOf("minisat", "z3", "boolector", "cvc5")) {
            val d = SolverPlan.resolve(SolverPlan.SolverRequest(name, "", "Entry", cp))
            assertInstanceOf(SolverPlan.Decision.Builtin::class.java, d,
                    "a built-in/SMT solver name ($name) must resolve to Builtin, never the text guard")
        }
    }

    // --- the text guard (the point) -------------------------------------------

    @Test
    fun textFreeProof_getsTheFastSolver(@TempDir dir: Path) {
        val fast = stageFakeSolver(dir, "fast")
        val d = SolverPlan.resolve(SolverPlan.SolverRequest(fast, "", "Entry", numericClasspath(dir)))
        assertInstanceOf(SolverPlan.Decision.ExternalSat::class.java, d,
                "a text-free proof requesting the fast solver gets it")
    }

    @Test
    fun textProof_failsLoudByDefault(@TempDir dir: Path) {
        val fast = stageFakeSolver(dir, "fast")
        val d = SolverPlan.resolve(SolverPlan.SolverRequest(fast, "", "Entry", textClasspath(dir)))
        assertInstanceOf(SolverPlan.Decision.FailLoud::class.java, d,
                "a text-using proof requesting the fast solver FAILS LOUD by default")
        val msg = (d as SolverPlan.Decision.FailLoud).message
        assertTrue(msg.contains("text/String"), "the fail message is plain-language: $msg")
    }

    @Test
    fun textProof_withOptOut_fallsBackToDefaultSolver_neverRunsRefinementOff(@TempDir dir: Path) {
        System.setProperty(SolverPlan.STRING_FALLBACK_PROP, "true")
        val fast = stageFakeSolver(dir, "fast")
        val d = SolverPlan.resolve(SolverPlan.SolverRequest(fast, "", "Entry", textClasspath(dir)))
        // The opt-out means "don't fail" — but it must STILL use the sound default solver, never the
        // text-reasoning-off external SAT solver. So the decision is Builtin (refinement ON), not
        // ExternalSat: there is NO path that runs refinement-off on a text proof.
        assertInstanceOf(SolverPlan.Decision.Builtin::class.java, d,
                "with the opt-out, a text proof falls back to the SOUND default solver, not refinement-off")
    }

    @Test
    fun textProof_expertUnsafeOverride_runsExternalSat(@TempDir dir: Path) {
        System.setProperty(SolverPlan.UNSAFE_TEXT_OVERRIDE_PROP, "true")
        val fast = stageFakeSolver(dir, "fast")
        val d = SolverPlan.resolve(SolverPlan.SolverRequest(fast, "", "Entry", textClasspath(dir)))
        assertInstanceOf(SolverPlan.Decision.ExternalSat::class.java, d,
                "the explicit expert override (off by default) runs external SAT on a text proof")
    }

    @Test
    fun unknownSolverPathThatDoesNotExist_declinesToDefault(@TempDir dir: Path) {
        // An external-sat path that isn't a real file can't be a binary: decline to the default solver,
        // never an error (mirrors the kissatPath()==null platform case).
        val d = SolverPlan.resolve(SolverPlan.SolverRequest(
                dir.resolve("does-not-exist").toString(), "", "Entry", numericClasspath(dir)))
        assertInstanceOf(SolverPlan.Decision.Builtin::class.java, d)
        assertTrue((d as SolverPlan.Decision.Builtin).note != null,
                "a requested-but-unavailable solver declines with a plain-language note")
    }

    // --- helpers ---------------------------------------------------------------

    private fun stageFakeSolver(dir: Path, name: String): String {
        val f = dir.resolve("$name.bin")
        Files.writeString(f, "#!fake-solver\n")
        return f.toString()
    }

    /** A classpath dir with an Entry whose cone is purely numeric (text-free). */
    private fun numericClasspath(dir: Path): String {
        val classes = Files.createDirectories(dir.resolve("numeric-classes"))
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "Entry", null, "java/lang/Object", null)
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "run", "()I", null, null)
        mv.visitCode()
        mv.visitInsn(Opcodes.ICONST_1); mv.visitInsn(Opcodes.ICONST_1); mv.visitInsn(Opcodes.IADD)
        mv.visitInsn(Opcodes.IRETURN)
        mv.visitMaxs(0, 0); mv.visitEnd(); cw.visitEnd()
        Files.write(classes.resolve("Entry.class"), cw.toByteArray())
        return classes.toString()
    }

    /** A classpath dir with an Entry whose cone references java.lang.String (text-using). */
    private fun textClasspath(dir: Path): String {
        val classes = Files.createDirectories(dir.resolve("text-classes"))
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "Entry", null, "java/lang/Object", null)
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "run", "()V", null, null)
        mv.visitCode()
        mv.visitInsn(Opcodes.ACONST_NULL)
        mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/String")
        mv.visitInsn(Opcodes.POP)
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(0, 0); mv.visitEnd(); cw.visitEnd()
        Files.write(classes.resolve("Entry.class"), cw.toByteArray())
        return classes.toString()
    }
}
