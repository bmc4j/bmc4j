package org.bmc4j.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for [JbmcOutputParser.harvestNondetTags] and its integration into the counterexample — the
 * explicit USER-nondet witness tag read back from a refutation trace. A `Bmc.recordNondet("name", value)`
 * call surfaces as a `function-call` frame whose argument assignments carry the input NAME + VALUE; the
 * parser must read each overload's kind, name the input, and render it in the counterexample REGARDLESS
 * of where the value later flowed (the boxed-Triple acid test), while degrading to a no-op on an
 * untagged run.
 */
internal class NondetTagParseTest {

    private val entry = "pkg.Tests.proof"

    /** A FAILURE property wrapping [traceSteps] (the trace JSON, comma-joined steps). */
    private fun failureWith(traceSteps: String): String = """
        [
          {"result":[
            {"name":"f.1","status":"FAILURE","description":"assertion",
             "sourceLocation":{"file":"Example.java","line":"12","function":"java::pkg.Tests.proof:()V"},
             "trace":[
               $traceSteps,
               {"stepType":"failure",
                "sourceLocation":{"function":"java::pkg.Tests.proof:()V","file":"Example.java","line":"12"}}
             ]}
          ]}
        ]""".trimIndent()

    /** A `recordNondet` tag frame: call into the overload [desc], the name + value arg assignments,
     *  then the return. [valueStep] is the raw assignment JSON for the value argument. */
    private fun tagFrame(name: String, desc: String, valueStep: String): String = """
        {"stepType":"function-call",
         "function":{"identifier":"java::org.bmc4j.Bmc.recordNondet:$desc"}},
        {"stepType":"assignment","lhs":"arg0a",
         "value":{"name":"pointer","data":"java.lang.String.Literal.$name"}},
        $valueStep,
        {"stepType":"function-return",
         "function":{"identifier":"java::org.bmc4j.Bmc.recordNondet:$desc"}}""".trimIndent()

    @Test
    fun boxed_triple_inputs_are_named_a_b_c() {
        // The acid test: three ints minted in a helper and boxed through a carrier — the LVT/anonlocal
        // path would drop them, but the tags surface a=7, b=8, c=9 directly.
        val steps = listOf(7 to "a", 8 to "b", 9 to "c").joinToString(",\n") { (v, n) ->
            tagFrame(n, "(Ljava/lang/String;J)V",
                    """{"stepType":"assignment","lhs":"arg1l","value":{"name":"integer","data":"$v"}}""")
        }
        val r = JbmcOutputParser.parse(failureWith(steps), entry)
        val v = r.violations.single()
        assertEquals(listOf("a = 7", "b = 8", "c = 9"), v.counterexample)
        assertEquals(listOf("a", "b", "c"), v.bindings.map { it.name })
        assertTrue(v.bindings.all { it.kind == "integer" })
    }

    @Test
    fun class_qualified_model_input_renders_with_the_class() {
        // A user model's internal nondet is tagged "DbRepoModel.result" by the rewrite — the parser
        // surfaces it verbatim, so the counterexample shows where the input came from.
        val steps = tagFrame("DbRepoModel.result", "(Ljava/lang/String;J)V",
                """{"stepType":"assignment","lhs":"arg1l","value":{"name":"integer","data":"5"}}""")
        val r = JbmcOutputParser.parse(failureWith(steps), entry)
        assertEquals(listOf("DbRepoModel.result = 5"), r.violations.single().counterexample)
    }

    @Test
    fun each_overload_kind_is_decoded() {
        val steps = listOf(
                tagFrame("i", "(Ljava/lang/String;J)V",
                        """{"stepType":"assignment","lhs":"a","value":{"name":"integer","data":"3"}}"""),
                tagFrame("flag", "(Ljava/lang/String;Z)V",
                        """{"stepType":"assignment","lhs":"a","value":{"name":"boolean","data":"true"}}"""),
                tagFrame("ratio", "(Ljava/lang/String;D)V",
                        """{"stepType":"assignment","lhs":"a","value":{"name":"double","data":"1.5"}}"""),
                tagFrame("name", "(Ljava/lang/String;Ljava/lang/String;)V",
                        """{"stepType":"assignment","lhs":"a","value":{"name":"pointer","data":"java.lang.String.Literal.eu"}}"""))
                .joinToString(",\n")
        val r = JbmcOutputParser.parse(failureWith(steps), entry)
        val byName = r.violations.single().bindings.associateBy { it.name }
        assertEquals("integer", byName["i"]!!.kind); assertEquals("3", byName["i"]!!.data)
        assertEquals("boolean", byName["flag"]!!.kind); assertEquals("true", byName["flag"]!!.data)
        assertEquals("double", byName["ratio"]!!.kind); assertEquals("1.5", byName["ratio"]!!.data)
        assertEquals("string", byName["name"]!!.kind); assertEquals("eu", byName["name"]!!.data)
    }

    @Test
    fun object_handle_tag_names_the_input_but_carries_no_scalar() {
        // An anyOf/object tag (Object overload) records the NAME with no displayable value -> it does not
        // appear as a "name = value" line (a bare object handle is shown by name only / left to the heap).
        val tag = tagFrame("choice", "(Ljava/lang/String;Ljava/lang/Object;)V",
                """{"stepType":"assignment","lhs":"arg1a","value":{"name":"pointer","data":"dynamic_object${'$'}3"}}""")
        val arr = com.google.gson.JsonParser.parseString("[$tag]").asJsonArray
        val tags = JbmcOutputParser.harvestNondetTags(arr)
        // Direct harvest: the name is present with kind=null, value=null (an object handle).
        assertTrue(tags.containsKey("choice"))
        assertEquals(null, tags["choice"]!!.kind)
        assertEquals(null, tags["choice"]!!.value)
    }

    @Test
    fun array_tag_leaves_the_name_free_for_the_heap_reconstruction() {
        // A symbolic int[] gets BOTH an Object tag (name "a", no value) AND the heap chain. The tag must
        // not occupy the name, so the heap path renders a = [1, 2].
        val tag = tagFrame("a", "(Ljava/lang/String;Ljava/lang/Object;)V",
                """{"stepType":"assignment","lhs":"arg1a","value":{"name":"pointer","data":"dynamic_object${'$'}0"}}""")
        val heap = """
            {"stepType":"function-call","function":{"identifier":"java::pkg.Tests.proof:()V"},
             "sourceLocation":{"file":"Tests.java","line":"5"}},
            {"stepType":"assignment","lhs":"a",
             "sourceLocation":{"function":"java::pkg.Tests.proof:()V"},
             "value":{"name":"pointer","type":"struct java::array[int] *","data":"dynamic_object${'$'}0"}},
            {"stepType":"assignment","lhs":"dynamic_object${'$'}0.data",
             "value":{"name":"pointer","data":"dynamic_array"}},
            {"stepType":"assignment","lhs":"dynamic_array[0L]",
             "value":{"name":"integer","type":"int","data":"1"}},
            {"stepType":"assignment","lhs":"dynamic_array[1L]",
             "value":{"name":"integer","type":"int","data":"2"}}""".trimIndent()
        val r = JbmcOutputParser.parse(failureWith("$tag,\n$heap"), entry)
        assertEquals(listOf("a = [1, 2]"), r.violations.single().counterexample)
        assertEquals("int[]", r.violations.single().bindings.single { it.name == "a" }.kind)
    }

    @Test
    fun untagged_run_degrades_to_the_scalar_path() {
        // No recordNondet frames at all (an older snapshot): the scalar LVT path still yields the input,
        // and harvestNondetTags is an empty no-op.
        val steps = """
            {"stepType":"function-call","function":{"identifier":"java::pkg.Tests.proof:()V"},
             "sourceLocation":{"file":"Tests.java","line":"5"}},
            {"stepType":"assignment","lhs":"score",
             "sourceLocation":{"function":"java::pkg.Tests.proof:()V"},
             "value":{"name":"integer","data":"100"}}""".trimIndent()
        val r = JbmcOutputParser.parse(failureWith(steps), entry)
        assertEquals(listOf("score = 100"), r.violations.single().counterexample)
    }

    @Test
    fun a_tagged_input_wins_its_name_over_a_same_named_scalar() {
        // The tag is harvested FIRST, so a same-named scalar assignment doesn't double-add. The tag's
        // value wins. (Models the proof-param case where the scalar path would also see "x".)
        val steps = """
            {"stepType":"function-call","function":{"identifier":"java::pkg.Tests.proof:()V"},
             "sourceLocation":{"file":"Tests.java","line":"5"}},
            ${tagFrame("x", "(Ljava/lang/String;J)V",
                """{"stepType":"assignment","lhs":"arg1l","value":{"name":"integer","data":"42"}}""")},
            {"stepType":"assignment","lhs":"x",
             "sourceLocation":{"function":"java::pkg.Tests.proof:()V"},
             "value":{"name":"integer","data":"999"}}""".trimIndent()
        val r = JbmcOutputParser.parse(failureWith(steps), entry)
        // First-wins: the tag (42) is harvested first; the scalar 999 is skipped as a same-named local.
        assertEquals(listOf("x = 42"), r.violations.single().counterexample)
    }

    @Test
    fun harvest_is_empty_on_a_trace_with_no_tags() {
        val arr = com.google.gson.JsonParser.parseString(
                """[{"stepType":"assignment","lhs":"y","value":{"name":"integer","data":"1"}}]""")
                .asJsonArray
        assertTrue(JbmcOutputParser.harvestNondetTags(arr).isEmpty())
        assertFalse(JbmcOutputParser.harvestNondetTags(arr).containsKey("y"))
    }
}
