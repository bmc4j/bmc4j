package org.bmc4j.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pins [JbmcComplexity]'s parsing of jbmc's plain-text `--program-only` program-equation dump - the
 * source-attributed SSA breakdown behind `-Dbmc.complexityReport`. The dump FORMAT is not an engine
 * contract; this test is what pins it against the bundled engine (cbmc 6.9.0), the same discipline
 * [JbmcProfileTest] uses for the verbose-stream markers. The fixture lines below are real-shaped
 * `--program-only` output: a numbered `(N) <expr>` step preceded by a `// <id> file <f> line <l>
 * function <fn> ...` location comment, exactly as captured from the staged jbmc.
 */
internal class JbmcComplexityTest {

    @Test
    fun attributes_steps_to_source_and_counts_expensive_op_kinds() {
        // A real-shaped dump: two source regions in Tiny2.pick. Line 4 carries a multiply AND a divide;
        // line 5 a symbolic-distance shift and a cast; line 3 a symbolic-index array read. Plain
        // assignments and a CONSTANT-index array write are present too and must NOT inflate the op counts.
        val dump = """
            // 57 file Tiny2.java line 3 function java::Tiny2.pick:([III)I bytecode-index 3
            (97) 3i!0@1#2 == { dynamic_object${'$'}0#3[[0]], dynamic_object${'$'}0#3[[1]] }[(long)arg1i!0@1#1]
            // 18 file Tiny2.java line 3 function java::Tiny2.pick:([III)I
            (35) dynamic_array#1[[0]] == 0
            // 60 file Tiny2.java line 4 function java::Tiny2.pick:([III)I bytecode-index 11
            (100) 4i!0@1#2 == (3i!0@1#2 * arg2i!0@1#1) / (arg2i!0@1#1 + 1)
            // 61 file Tiny2.java line 5 function java::Tiny2.pick:([III)I bytecode-index 18
            (101) 5l!0@1#2 == (long)4i!0@1#2 << (arg2i!0@1#1 & 31 & 63)
            // 90 file Tiny2.java line 4 function java::Tiny2.pick:([III)I bytecode-index 6
            (167) 3i!0@1#3 == 1
            (sliced) return'!0#1 == Tiny2.pick:([III)I#return_value!0#1
        """.trimIndent()

        val c = JbmcComplexity.parse(dump)

        // Five NUMBERED steps counted; the (sliced) step is simplified out and not counted.
        assertEquals(5, c.totalSteps, "(sliced) steps carry no formula cost and must not be counted")

        val byKey = c.regions.associateBy { "${it.function}|${it.line}" }
        // Every region attributes to the normalized method FQN (java:: + signature stripped).
        assertTrue(c.regions.all { it.function == "Tiny2.pick" })
        assertTrue(c.regions.all { it.file == "Tiny2.java" })

        // Line 3: two steps (symbolic-index array read + a constant-index array write). The symbolic
        // index `[(long)...]` counts as ONE sym-array; the constant `[[0]]` does NOT.
        val l3 = byKey["Tiny2.pick|3"]!!
        assertEquals(2, l3.steps)
        assertEquals(1, l3.ops[JbmcComplexity.OpKind.SYM_ARRAY], "single-bracket symbolic index")
        assertNull(l3.ops[JbmcComplexity.OpKind.MUL])

        // Line 4: the multiply/divide step PLUS a plain `== 1` assignment. One mul, one div; no others.
        val l4 = byKey["Tiny2.pick|4"]!!
        assertEquals(2, l4.steps)
        assertEquals(1, l4.ops[JbmcComplexity.OpKind.MUL])
        assertEquals(1, l4.ops[JbmcComplexity.OpKind.DIV])

        // Line 5: a symbolic-distance shift `<< (expr)` and the `(long)` cast.
        val l5 = byKey["Tiny2.pick|5"]!!
        assertEquals(1, l5.ops[JbmcComplexity.OpKind.SHIFT], "shift distance is non-constant")
        assertEquals(1, l5.ops[JbmcComplexity.OpKind.CAST])
    }

    @Test
    fun constant_shift_and_constant_index_are_not_counted_as_expensive() {
        // A `<< 3` constant shift folds cheaply and a `[[2]]` constant index is array-theory-free; neither
        // is an expensive kind. A pointer cast `(int (*)[5])` must not be miscounted as a multiply.
        val dump = """
            // 1 file F.java line 9 function java::F.m:()V bytecode-index 0
            (10) x == y << 3
            // 2 file F.java line 9 function java::F.m:()V bytecode-index 1
            (11) z == arr[[2]]
            // 3 file F.java line 9 function java::F.m:()V bytecode-index 2
            (12) p == (int (*)[5])dynamic_object${'$'}0
        """.trimIndent()

        val c = JbmcComplexity.parse(dump)
        val r = c.regions.single()
        assertEquals(3, r.steps)
        assertNull(r.ops[JbmcComplexity.OpKind.SHIFT], "a constant shift distance is cheap")
        assertNull(r.ops[JbmcComplexity.OpKind.SYM_ARRAY], "a constant `[[2]]` index is not array-theory")
        assertNull(r.ops[JbmcComplexity.OpKind.MUL], "`(*)` in a pointer cast is not a multiply")
    }

    @Test
    fun ranks_regions_by_step_count_and_renders_the_caveat() {
        // Region B has more steps than A, so it ranks first regardless of source order.
        val dump = buildString {
            append("// 1 file A.java line 1 function java::A.a:()V\n")
            append("(1) u == v\n")
            append("// 2 file B.java line 2 function java::B.b:()V\n")
            repeat(3) { append("(${10 + it}) w == w + 1\n") }
        }
        val c = JbmcComplexity.parse(dump)
        assertEquals("B.b", c.regions.first().function, "more steps ranks first")
        assertEquals(4, c.totalSteps)

        val rendered = c.render("Demo.proof")
        assertTrue(rendered.contains("PROXY for SAT/CNF cost, NOT literal clause counts"),
                "the honesty caveat must be in the rendered header")
        assertTrue(rendered.contains("ablation"), "the caveat names ablation as the exact-attribution path")
        assertTrue(rendered.lines().all { it.startsWith("  bmc4j[complexity]:") },
                "every line carries the grep-able tag")
    }

    @Test
    fun empty_or_unparsable_dump_renders_a_note_never_throws() {
        val c = JbmcComplexity.parse("garbage with no steps\nmore garbage")
        assertEquals(0, c.totalSteps)
        assertTrue(c.regions.isEmpty())
        assertNotNull(c.render("X.y"))
        assertTrue(c.render("X.y").contains("no SSA program equation"))
    }

    @Test
    fun program_only_command_drops_ui_flags_and_appends_program_only() {
        val verdict = listOf("jbmc.exe", "Tiny", "--classpath", "cp", "--function", "Tiny.m",
                "--unwind", "4", "--json-ui", "--trace", "--verbosity", "10")
        val prog = Jbmc.programOnlyCommand(verdict)
        assertTrue("--program-only" in prog)
        assertTrue("--json-ui" !in prog)
        assertTrue("--trace" !in prog)
        assertTrue("--verbosity" !in prog, "--verbosity flag is dropped")
        assertTrue("10" !in prog, "--verbosity's value is dropped too")
        // The verdict-relevant flags survive untouched, so the dump is the SAME formula.
        assertTrue("--unwind" in prog && "4" in prog)
        assertTrue("--classpath" in prog && "cp" in prog)
        assertTrue("--function" in prog && "Tiny.m" in prog)
    }
}
