package org.bmc4j.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import javax.tools.ToolProvider

/**
 * Unit tests for the witness collector's user-vs-library discrimination and the LocalVariableTable
 * declared-local check (see [WitnessUserCode]). Each test compiles a tiny real fixture class into a
 * temp DIRECTORY (the consumer's own-code origin), passes that dir as the `userClasspath`, and drives
 * [JbmcOutputParser.parse] with a hand-built `--json-ui` trace — so the classpath-origin filter and the
 * ASM LocalVariableTable read both exercise real bytecode, not a stub.
 *
 * The four pinned cases mirror the four behaviors the feature must guarantee:
 *  - a DECLARED local of a user method is KEPT;
 *  - an UNDECLARED engine symbol (shares a user frame, declared nowhere) is DROPPED;
 *  - a local declared in a HELPER method (not the proof entry) is KEPT (decomposition support);
 *  - a local in a LIBRARY frame (not a directory-origin / reserved namespace) is DROPPED.
 * Plus the `-g:none` graceful-fallback case.
 */
internal class WitnessUserCodeTest {

    @Test
    fun a_declared_local_of_the_proof_method_is_kept() {
        val cp = compile(
                "Proofs",
                "package proofs.demo;",
                "public class Proofs {",
                "  public void proof() { int x = 0; if (x < 0) {} }",
                "}")
        val json = traceWithAssignment(
                proofFn = "java::proofs.demo.Proofs.proof:()V",
                assignFn = "java::proofs.demo.Proofs.proof:()V",
                lhs = "x", data = "15")
        val r = JbmcOutputParser.parse(json, "proofs.demo.Proofs.proof", cp)
        assertEquals(listOf("x = 15"), r.violations[0].counterexample)
    }

    @Test
    fun an_undeclared_engine_symbol_in_a_user_frame_is_dropped() {
        // `x` is the developer's declared local; `i` is the jbmc-synthesized nondet symbol for a
        // Bmc.anyInt() call — attributed to the SAME user proof frame yet declared in no
        // LocalVariableTable. Only `x` must survive; the engine `i` is dropped.
        val cp = compile(
                "Proofs",
                "package proofs.demo;",
                "public class Proofs {",
                "  public void proof() { int x = 0; if (x < 0) {} }",
                "}")
        val proofFn = "java::proofs.demo.Proofs.proof:()V"
        val json = traceWith(
                proofFn,
                assignment(proofFn, "x", "15"),
                assignment(proofFn, "i", "15")) // synthetic: declared nowhere in proof()
        val r = JbmcOutputParser.parse(json, "proofs.demo.Proofs.proof", cp)
        assertEquals(listOf("x = 15"), r.violations[0].counterexample,
                "the engine synthetic `i` (declared in no LocalVariableTable) must be dropped")
    }

    @Test
    fun a_local_declared_in_a_helper_method_is_kept_decomposition() {
        // The symbolic input is declared in a HELPER (makeInput), not the proof entry. The widened
        // frame filter + the helper's own LocalVariableTable must keep it.
        val cp = compile(
                "Proofs",
                "package proofs.demo;",
                "public class Proofs {",
                "  public void proof() { int v = makeInput(); if (v < 0) {} }",
                "  public int makeInput() { int a = 7; return a; }",
                "}")
        val proofFn = "java::proofs.demo.Proofs.proof:()V"
        val helperFn = "java::proofs.demo.Proofs.makeInput:()I"
        val json = traceWith(
                proofFn,
                functionCall(helperFn),
                assignment(helperFn, "a", "42"))
        val r = JbmcOutputParser.parse(json, "proofs.demo.Proofs.proof", cp)
        assertTrue(r.violations[0].counterexample.contains("a = 42"),
                "a helper-declared input must appear in the witness: " + r.violations[0].counterexample)
    }

    @Test
    fun a_local_in_a_library_frame_is_dropped() {
        // The assignment is attributed to a frame in a class that is NOT a directory-origin user class
        // (here a reserved java.* namespace, the shape the bundled models take). It must be dropped even
        // though its name passes the cheap synthetic pre-filter and would have a value.
        val cp = compile(
                "Proofs",
                "package proofs.demo;",
                "public class Proofs {",
                "  public void proof() { int x = 0; if (x < 0) {} }",
                "}")
        val proofFn = "java::proofs.demo.Proofs.proof:()V"
        val libFn = "java::java.util.ArrayList.add:(Ljava/lang/Object;)Z"
        val json = traceWith(
                proofFn,
                assignment(proofFn, "x", "15"),
                functionCall(libFn),
                assignment(libFn, "element", "99"))
        val r = JbmcOutputParser.parse(json, "proofs.demo.Proofs.proof", cp)
        assertEquals(listOf("x = 15"), r.violations[0].counterexample,
                "a library-frame local must never leak into the witness")
    }

    @Test
    fun no_localvariabletable_degrades_to_legacy_keep_for_that_frame() {
        // Compiled -g:none: the proof method carries NO LocalVariableTable. The declared-local check
        // can't run, so it must degrade to the legacy behavior (keep the user-frame primitive input)
        // rather than dropping everything.
        val cp = compile(
                "Proofs", debug = false,
                lines = arrayOf(
                        "package proofs.demo;",
                        "public class Proofs {",
                        "  public void proof() { int x = 0; if (x < 0) {} }",
                        "}"))
        val proofFn = "java::proofs.demo.Proofs.proof:()V"
        val json = traceWith(proofFn, assignment(proofFn, "x", "15"))
        val r = JbmcOutputParser.parse(json, "proofs.demo.Proofs.proof", cp)
        assertEquals(listOf("x = 15"), r.violations[0].counterexample,
                "with no LocalVariableTable the frame must keep its inputs (legacy fallback), not drop them")
    }

    @Test
    fun a_null_classpath_preserves_the_legacy_proof_frame_only_behavior() {
        // Sanity: the pure-parser path (no classpath, e.g. the engine canary) is unchanged — a value
        // attributed to a non-entry frame is filtered by the legacy entryPrefix rule.
        val proofFn = "java::proofs.demo.Proofs.proof:()V"
        val otherFn = "java::proofs.demo.Other.g:()V"
        val json = traceWith(
                proofFn,
                assignment(proofFn, "x", "15"),
                assignment(otherFn, "y", "99"))
        val r = JbmcOutputParser.parse(json, "proofs.demo.Proofs.proof") // no classpath
        assertEquals(listOf("x = 15"), r.violations[0].counterexample)
    }

    companion object {

        /** Compile a one-class fixture into a fresh temp dir (debug info on by default) and return its path. */
        private fun compile(className: String, vararg lines: String): String =
                compile(className, debug = true, lines = lines)

        private fun compile(className: String, debug: Boolean, lines: Array<out String>): String {
            val javac = ToolProvider.getSystemJavaCompiler()
                    ?: throw IllegalStateException("no system Java compiler available for the test")
            val srcDir = Files.createTempDirectory("bmc4j-witness-src")
            val outDir = Files.createTempDirectory("bmc4j-witness-out")
            val src = srcDir.resolve("$className.java")
            Files.writeString(src, lines.joinToString("\n"))
            val opts = mutableListOf("-d", outDir.toString())
            opts.add(if (debug) "-g" else "-g:none")
            val ok = javac.run(null, null, null, *(opts + listOf(src.toString())).toTypedArray())
            check(ok == 0) { "fixture compilation failed for $className" }
            deleteOnExit(srcDir)
            deleteOnExit(outDir)
            return outDir.toString()
        }

        private fun deleteOnExit(dir: Path) {
            Runtime.getRuntime().addShutdownHook(Thread {
                Files.walk(dir).use { w ->
                    w.sorted(Comparator.reverseOrder()).forEach { try { Files.deleteIfExists(it) } catch (e: Exception) {} }
                }
            })
        }

        // --- synthetic --json-ui trace builders ---------------------------------

        private fun functionCall(fn: String): String =
                """{"stepType":"function-call","function":{"identifier":"$fn"},
                   "sourceLocation":{"file":"Proofs.java","line":"3"}}"""

        private fun assignment(fn: String, lhs: String, data: String): String =
                """{"stepType":"assignment","lhs":"$lhs",
                   "sourceLocation":{"function":"$fn"},
                   "value":{"name":"integer","data":"$data"}}"""

        /** A FAILURE property whose trace opens the proof frame then runs [steps], then fails. */
        private fun traceWith(proofFn: String, vararg steps: String): String {
            val open = """{"stepType":"function-call","function":{"identifier":"$proofFn"},
                          "sourceLocation":{"file":"Proofs.java","line":"3"}}"""
            val fail = """{"stepType":"failure",
                          "sourceLocation":{"function":"$proofFn","file":"Proofs.java","line":"3"}}"""
            val body = (listOf(open) + steps.toList() + listOf(fail)).joinToString(",")
            return """
                [
                  {"result":[
                    {"name":"f.1","status":"FAILURE","description":"assertion",
                     "sourceLocation":{"file":"Proofs.java","line":"3","function":"$proofFn"},
                     "trace":[$body]}
                  ]},
                  {"cProverStatus":"failure"}
                ]""".trimIndent()
        }

        /** Convenience for the single-assignment cases. */
        private fun traceWithAssignment(proofFn: String, assignFn: String, lhs: String, data: String): String =
                traceWith(proofFn, assignment(assignFn, lhs, data))
    }
}
