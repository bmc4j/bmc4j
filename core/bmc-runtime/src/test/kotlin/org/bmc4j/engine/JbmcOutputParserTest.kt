package org.bmc4j.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for [JbmcOutputParser] — the pure JBMC `--json-ui` parser.
 * A regression here silently corrupts every proof's verdict, so the branches are
 * pinned with hand-built sample outputs.
 */
internal class JbmcOutputParserTest {

    @Test
    fun markerless_success_output_is_UNKNOWN_not_verified() {
        // No reachability markers => the vacuity check never ran, so a green here would be unsound:
        // every real @BmcProof run carries markers. cProverStatus "success" alone must NOT pass.
        val json = """
            [
              {"messageType":"STATUS-MESSAGE","messageText":"starting"},
              {"result":[{"name":"p1","status":"SUCCESS"}]},
              {"cProverStatus":"success"}
            ]""".trimIndent()
        val r = JbmcOutputParser.parse(json, ENTRY)
        assertFalse(r.isVerified)
        assertTrue(r.isUnknown)
        assertTrue(r.violations.isEmpty())
        assertEquals(json, r.rawOutput)
        assertTrue(r.undecidedReason != null && r.undecidedReason!!.contains("markers missing"))
    }

    @Test
    fun malformed_output_is_UNKNOWN_not_a_thrown_error_or_a_pass() {
        // unparseable engine output is undecided, not a silent pass and not a crash.
        val r = JbmcOutputParser.parse("this is not json {{{", ENTRY)
        assertFalse(r.isVerified)
        assertTrue(r.isUnknown)
        assertTrue(r.violations.isEmpty())
        assertTrue(r.undecidedReason != null && r.undecidedReason!!.contains("could not parse"))
    }

    @Test
    fun truncated_json_is_UNKNOWN() {
        // A run killed mid-write leaves half a document; that's undecided, not refuted/verified.
        val r = JbmcOutputParser.parse("[{\"result\":[{\"name\":\"p\",\"sta", ENTRY)
        assertTrue(r.isUnknown)
    }

    @Test
    fun empty_array_is_UNKNOWN_no_markers_means_no_proof_ran() {
        // An empty result carries no reachability markers — the vacuity check never ran, so this
        // cannot be a sound green; it is undecided.
        val r = JbmcOutputParser.parse("[]", ENTRY)
        assertFalse(r.isVerified)
        assertTrue(r.isUnknown)
        assertTrue(r.violations.isEmpty())
    }

    @Test
    fun cProverStatus_failure_makes_it_unverified_even_with_no_failure_properties() {
        val json = """
            [
              {"result":[{"name":"p1","status":"SUCCESS"}]},
              {"cProverStatus":"failure"}
            ]""".trimIndent()
        val r = JbmcOutputParser.parse(json, ENTRY)
        assertFalse(r.isVerified)
        assertTrue(r.violations.isEmpty())
    }

    @Test
    fun single_failure_extracts_description_and_location() {
        val json = """
            [
              {"result":[
                {"name":"a.1","status":"FAILURE","description":"array bounds in f",
                 "sourceLocation":{"file":"Example.java","line":"12","function":"java::pkg.Example.f:(I)V"}}
              ]},
              {"cProverStatus":"failure"}
            ]""".trimIndent()
        val r = JbmcOutputParser.parse(json, ENTRY)
        assertFalse(r.isVerified)
        assertEquals(1, r.violations.size)
        val v = r.violations[0]
        assertEquals("array bounds in f", v.description)
        assertEquals("Example.java", v.file)
        assertEquals(12, v.line)
    }

    @Test
    fun only_failure_properties_become_violations() {
        val json = """
            [
              {"result":[
                {"name":"ok","status":"SUCCESS"},
                {"name":"bad1","status":"FAILURE","description":"d1","sourceLocation":{"file":"A.java","line":"1"}},
                {"name":"bad2","status":"FAILURE","description":"d2","sourceLocation":{"file":"A.java","line":"2"}}
              ]}
            ]""".trimIndent()
        val r = JbmcOutputParser.parse(json, ENTRY)
        assertFalse(r.isVerified)
        assertEquals(2, r.violations.size)
        assertEquals("d1", r.violations[0].description)
        assertEquals("d2", r.violations[1].description)
    }

    @Test
    fun reconstructs_call_stack_and_counterexample_from_trace() {
        val json = """
            [
              {"result":[
                {"name":"f.1","status":"FAILURE","description":"assertion",
                 "sourceLocation":{"file":"Example.java","line":"12","function":"java::pkg.Example.f:(I)V"},
                 "trace":[
                   {"stepType":"function-call","function":{"identifier":"java::pkg.Tests.proof:()V"},
                    "sourceLocation":{"file":"Tests.java","line":"5"}},
                   {"stepType":"assignment","lhs":"score",
                    "sourceLocation":{"function":"java::pkg.Tests.proof:()V"},
                    "value":{"name":"integer","data":"100"}},
                   {"stepType":"function-call","function":{"identifier":"java::pkg.Example.f:(I)V"},
                    "sourceLocation":{"file":"Tests.java","line":"7"}},
                   {"stepType":"failure",
                    "sourceLocation":{"function":"java::pkg.Example.f:(I)V","file":"Example.java","line":"12"}}
                 ]}
              ]}
            ]""".trimIndent()
        val r = JbmcOutputParser.parse(json, ENTRY)
        val v = r.violations[0]

        assertEquals(listOf("score = 100"), v.counterexample)

        val stack = v.stack
        assertEquals(2, stack.size)
        assertEquals("pkg.Example", stack[0].className)
        assertEquals("f", stack[0].methodName)
        assertEquals("Example.java", stack[0].fileName)
        assertEquals(12, stack[0].lineNumber)
        assertEquals("pkg.Tests", stack[1].className)
        assertEquals("proof", stack[1].methodName)
        assertEquals(7, stack[1].lineNumber)   // rendered at the call site
    }

    @Test
    fun trace_also_yields_structured_bindings_for_replay() {
        // alongside the human-readable "score = 100" the parser carries a structured
        // binding (name, kind, data) the replay renderer turns into concrete Java.
        val json = """
            [
              {"result":[
                {"name":"f.1","status":"FAILURE","description":"assertion",
                 "sourceLocation":{"file":"Example.java","line":"12","function":"java::pkg.Example.f:(I)V"},
                 "trace":[
                   {"stepType":"function-call","function":{"identifier":"java::pkg.Tests.proof:()V"},
                    "sourceLocation":{"file":"Tests.java","line":"5"}},
                   {"stepType":"assignment","lhs":"score",
                    "sourceLocation":{"function":"java::pkg.Tests.proof:()V"},
                    "value":{"name":"integer","data":"100"}},
                   {"stepType":"failure",
                    "sourceLocation":{"function":"java::pkg.Tests.proof:()V","file":"Example.java","line":"12"}}
                 ]}
              ]}
            ]""".trimIndent()
        val r = JbmcOutputParser.parse(json, ENTRY)
        val v = r.violations[0]
        assertEquals(listOf("score = 100"), v.counterexample)
        assertEquals(1, v.bindings.size)
        assertEquals("score", v.bindings[0].name)
        assertEquals("integer", v.bindings[0].kind)
        assertEquals("100", v.bindings[0].data)
    }

    @Test
    fun bmc_check_failure_is_repointed_to_the_user_frame_and_internal_frames_hidden() {
        val json = """
            [
              {"result":[
                {"name":"c.1","status":"FAILURE","description":"assertion failed",
                 "sourceLocation":{"file":"org/bmc4j/Bmc.java","line":"30",
                                   "function":"java::org.bmc4j.Bmc.check:(Z)V"},
                 "trace":[
                   {"stepType":"function-call","function":{"identifier":"java::pkg.Tests.proof:()V"},
                    "sourceLocation":{"file":"Tests.java","line":"5"}},
                   {"stepType":"assignment","lhs":"score",
                    "sourceLocation":{"function":"java::pkg.Tests.proof:()V"},
                    "value":{"name":"integer","data":"100"}},
                   {"stepType":"function-call","function":{"identifier":"java::org.bmc4j.Bmc.check:(Z)V"},
                    "sourceLocation":{"file":"Tests.java","line":"9"}},
                   {"stepType":"failure",
                    "sourceLocation":{"function":"java::org.bmc4j.Bmc.check:(Z)V",
                                      "file":"org/bmc4j/Bmc.java","line":"30"}}
                 ]}
              ]}
            ]""".trimIndent()
        val r = JbmcOutputParser.parse(json, ENTRY)
        val v = r.violations[0]

        // Re-pointed to the user's proof line + given a clean description.
        assertEquals("a checked property does not hold", v.description)
        assertEquals("Tests.java", v.file)
        assertEquals(9, v.line)
        // Internal Bmc frame is stripped, leaving only the user frame.
        assertEquals(1, v.stack.size)
        assertEquals("pkg.Tests", v.stack[0].className)
        assertEquals(listOf("score = 100"), v.counterexample)
    }

    @Test
    fun counterexample_filters_synthetics_other_functions_and_nonprimitives_first_wins() {
        val json = """
            [
              {"result":[
                {"name":"f.1","status":"FAILURE","description":"assertion",
                 "sourceLocation":{"file":"Example.java","line":"3","function":"java::pkg.Tests.proof:()V"},
                 "trace":[
                   {"stepType":"function-call","function":{"identifier":"java::pkg.Tests.proof:()V"},
                    "sourceLocation":{"file":"Tests.java","line":"1"}},
                   {"stepType":"assignment","lhs":"tmp1",
                    "sourceLocation":{"function":"java::pkg.Tests.proof:()V"},"value":{"name":"integer","data":"7"}},
                   {"stepType":"assignment","lhs":"this$0",
                    "sourceLocation":{"function":"java::pkg.Tests.proof:()V"},"value":{"name":"integer","data":"7"}},
                   {"stepType":"assignment","lhs":"elsewhere",
                    "sourceLocation":{"function":"java::pkg.Other.g:()V"},"value":{"name":"integer","data":"7"}},
                   {"stepType":"assignment","lhs":"ptr",
                    "sourceLocation":{"function":"java::pkg.Tests.proof:()V"},"value":{"name":"pointer","data":"0x1"}},
                   {"stepType":"assignment","lhs":"amount",
                    "sourceLocation":{"function":"java::pkg.Tests.proof:()V"},"value":{"name":"integer","data":"1"}},
                   {"stepType":"assignment","lhs":"amount",
                    "sourceLocation":{"function":"java::pkg.Tests.proof:()V"},"value":{"name":"integer","data":"42"}},
                   {"stepType":"failure",
                    "sourceLocation":{"function":"java::pkg.Tests.proof:()V","file":"Example.java","line":"3"}}
                 ]}
              ]}
            ]""".trimIndent()
        val r = JbmcOutputParser.parse(json, ENTRY)
        // Only the real, primitive, proof-local variable survives, at its FIRST value — the input
        // value, before any later same-named reassignment (a tailrec/loop or callee param mutation).
        assertEquals(listOf("amount = 1"), r.violations[0].counterexample)
    }

    @Test
    fun a_reassigned_same_named_input_renders_its_first_value_not_the_mutated_one() {
        // The tailrec shape: kotlinc lowers `tailrec fun factorial(n, acc)` to a LOOP that reassigns the
        // `n` param down to its base value. The trace assigns the proof input `n = 13` (the value that
        // triggers the overflow) and later, in the lowered loop, reassigns `n = 0` (the loop exit value).
        // Both share the proof frame. First-wins must render the INPUT (13), not the mutated exit (0); the
        // overflowed `accumulator` is unaffected (single assignment).
        val json = """
            [
              {"result":[
                {"name":"f.1","status":"FAILURE","description":"assertion",
                 "sourceLocation":{"file":"Example.java","line":"3","function":"java::pkg.Tests.proof:()V"},
                 "trace":[
                   {"stepType":"function-call","function":{"identifier":"java::pkg.Tests.proof:()V"},
                    "sourceLocation":{"file":"Tests.java","line":"1"}},
                   {"stepType":"assignment","lhs":"n",
                    "sourceLocation":{"function":"java::pkg.Tests.proof:()V"},"value":{"name":"integer","data":"13"}},
                   {"stepType":"assignment","lhs":"accumulator",
                    "sourceLocation":{"function":"java::pkg.Tests.proof:()V"},"value":{"name":"integer","data":"1932053504"}},
                   {"stepType":"assignment","lhs":"n",
                    "sourceLocation":{"function":"java::pkg.Tests.proof:()V"},"value":{"name":"integer","data":"0"}},
                   {"stepType":"failure",
                    "sourceLocation":{"function":"java::pkg.Tests.proof:()V","file":"Example.java","line":"3"}}
                 ]}
              ]}
            ]""".trimIndent()
        val r = JbmcOutputParser.parse(json, ENTRY)
        // n shows the INPUT 13 (first-wins), NOT the loop-mutated 0; accumulator keeps its overflow value.
        assertEquals(listOf("n = 13", "accumulator = 1932053504"), r.violations[0].counterexample)
        // The structured binding the replay renderer uses must agree (first value, kind preserved).
        val nBinding = r.violations[0].bindings.first { it.name == "n" }
        assertEquals("13", nBinding.data)
        assertEquals("integer", nBinding.kind)
    }

    @Test
    fun array_input_is_reconstructed_to_a_bracketed_literal() {
        // A Java int[] proof input is not a flat value in the trace: the proof-local `a` is only a
        // POINTER to a heap `dynamic_object$0`, whose `data` member points to a backing store
        // (`dynamic_array`), whose elements arrive as per-index assignments. The witness collector must
        // stitch that chain back into `a = [..]`. This mirrors the exact shape jbmc 6.9.0 emits for
        // `int[] a = Bmc.anyArrayOfInts(4,-3,3); Bmc.check(a[0] <= a[3])` (refuted by [1, 0, 0, 0]).
        val json = """
            [
              {"result":[
                {"name":"c.1","status":"FAILURE","description":"assertion",
                 "sourceLocation":{"file":"Example.java","line":"3","function":"java::pkg.Tests.proof:()V"},
                 "trace":[
                   {"stepType":"function-call","function":{"identifier":"java::pkg.Tests.proof:()V"},
                    "sourceLocation":{"file":"Tests.java","line":"1"}},
                   {"stepType":"assignment","lhs":"dynamic_object${'$'}0.data",
                    "sourceLocation":{"function":"java::pkg.Tests.proof:()V"},
                    "value":{"name":"pointer","data":"dynamic_array","type":"int *"}},
                   {"stepType":"assignment","lhs":"dynamic_array[0L]",
                    "sourceLocation":{"function":"java::pkg.Tests.proof:()V"},
                    "value":{"name":"integer","data":"0","type":"int"}},
                   {"stepType":"assignment","lhs":"dynamic_array[1L]",
                    "sourceLocation":{"function":"java::pkg.Tests.proof:()V"},
                    "value":{"name":"integer","data":"0","type":"int"}},
                   {"stepType":"assignment","lhs":"dynamic_array[2L]",
                    "sourceLocation":{"function":"java::pkg.Tests.proof:()V"},
                    "value":{"name":"integer","data":"0","type":"int"}},
                   {"stepType":"assignment","lhs":"dynamic_array[3L]",
                    "sourceLocation":{"function":"java::pkg.Tests.proof:()V"},
                    "value":{"name":"integer","data":"0","type":"int"}},
                   {"stepType":"assignment","lhs":"a",
                    "sourceLocation":{"function":"java::pkg.Tests.proof:()V"},
                    "value":{"name":"pointer","data":"dynamic_object${'$'}0","type":"struct java::array[int] *"}},
                   {"stepType":"assignment","lhs":"dynamic_array[0L]",
                    "sourceLocation":{"function":"java::pkg.Tests.proof:()V"},
                    "value":{"name":"integer","data":"1","type":"int"}},
                   {"stepType":"failure",
                    "sourceLocation":{"function":"java::pkg.Tests.proof:()V","file":"Example.java","line":"3"}}
                 ]}
              ]}
            ]""".trimIndent()
        val r = JbmcOutputParser.parse(json, ENTRY)
        // The whole array renders as one bracketed binding, elements in index order, with the SETTLED
        // (last-per-index) symbolic values: index 0 = 1 (post-fill), the rest 0.
        assertEquals(listOf("a = [1, 0, 0, 0]"), r.violations[0].counterexample)
        val aBinding = r.violations[0].bindings.single { it.name == "a" }
        assertEquals("int[]", aBinding.kind)
        assertEquals("[1, 0, 0, 0]", aBinding.data)
    }

    @Test
    fun only_the_nondet_sourced_input_renders_not_a_derived_local() {
        // The witness must collapse to "the symbolic inputs you'd set to reproduce". This mirrors the
        // exact real-engine chain (verified against jbmc 6.9.0): a Bmc.anyInt() value flows through a
        // `function-return` + a synthetic `return_tmp*` and lands in the user's `key`; the DERIVED
        // `present = false` flag that follows it (a computed local, not an input) carries no nondet
        // return and must be DROPPED — even though it is a primitive, user-frame, declared local.
        val proofFn = "java::pkg.Tests.proof:()V"
        val json = """
            [
              {"result":[
                {"name":"c.1","status":"FAILURE","description":"assertion",
                 "sourceLocation":{"file":"Example.java","line":"3","function":"$proofFn"},
                 "trace":[
                   {"stepType":"function-call","function":{"identifier":"$proofFn"},
                    "sourceLocation":{"file":"Tests.java","line":"1"}},
                   {"stepType":"function-return","function":{"identifier":"java::org.bmc4j.Bmc.anyInt:(II)I"}},
                   {"stepType":"assignment","lhs":"return_tmp1",
                    "sourceLocation":{"function":"$proofFn"},"value":{"name":"integer","data":"0"}},
                   {"stepType":"assignment","lhs":"key",
                    "sourceLocation":{"function":"$proofFn"},"value":{"name":"integer","data":"0"}},
                   {"stepType":"assignment","lhs":"present",
                    "sourceLocation":{"function":"$proofFn"},"value":{"name":"integer","data":"false","type":"boolean"}},
                   {"stepType":"failure",
                    "sourceLocation":{"function":"$proofFn","file":"Example.java","line":"3"}}
                 ]}
              ]}
            ]""".trimIndent()
        val r = JbmcOutputParser.parse(json, ENTRY)
        assertEquals(listOf("key = 0"), r.violations[0].counterexample,
                "only the nondet-sourced input `key` may render; the derived `present` flag is not an input")
    }

    @Test
    fun a_nondet_sourced_array_input_renders_but_a_derived_array_local_is_dropped() {
        // The array analogue: an `anyArrayOfInts` value flows through a `function-return` + a synthetic
        // `return_tmp*` into the user's `a` (kept, reconstructed to a bracketed literal), while a SECOND
        // array variable `b` assigned with no preceding nondet return is a derived local and is dropped.
        val proofFn = "java::pkg.Tests.proof:()V"
        val json = """
            [
              {"result":[
                {"name":"c.1","status":"FAILURE","description":"assertion",
                 "sourceLocation":{"file":"Example.java","line":"3","function":"$proofFn"},
                 "trace":[
                   {"stepType":"function-call","function":{"identifier":"$proofFn"},
                    "sourceLocation":{"file":"Tests.java","line":"1"}},
                   {"stepType":"assignment","lhs":"dynamic_object${'$'}0.data",
                    "sourceLocation":{"function":"$proofFn"},
                    "value":{"name":"pointer","data":"dynamic_array","type":"int *"}},
                   {"stepType":"assignment","lhs":"dynamic_array[0L]",
                    "sourceLocation":{"function":"$proofFn"},"value":{"name":"integer","data":"5","type":"int"}},
                   {"stepType":"function-return","function":{"identifier":"java::org.bmc4j.Bmc.anyArrayOfInts:(III)[I"}},
                   {"stepType":"assignment","lhs":"return_tmp0",
                    "sourceLocation":{"function":"$proofFn"},
                    "value":{"name":"pointer","data":"dynamic_object${'$'}0","type":"struct java::array[int] *"}},
                   {"stepType":"assignment","lhs":"a",
                    "sourceLocation":{"function":"$proofFn"},
                    "value":{"name":"pointer","data":"dynamic_object${'$'}0","type":"struct java::array[int] *"}},
                   {"stepType":"assignment","lhs":"b",
                    "sourceLocation":{"function":"$proofFn"},
                    "value":{"name":"pointer","data":"dynamic_object${'$'}0","type":"struct java::array[int] *"}},
                   {"stepType":"failure",
                    "sourceLocation":{"function":"$proofFn","file":"Example.java","line":"3"}}
                 ]}
              ]}
            ]""".trimIndent()
        val r = JbmcOutputParser.parse(json, ENTRY)
        assertEquals(listOf("a = [5]"), r.violations[0].counterexample,
                "only the nondet-sourced array `a` may render; the derived array local `b` is dropped")
    }

    @Test
    fun a_proof_parameter_input_step_is_kept() {
        // jbmc treats a @BmcProof method's own parameter as nondet and emits an `input` trace step right
        // before the `param = …` assignment (no function-return). The `input` step is the nondet signal,
        // so the parameter must be kept.
        val proofFn = "java::pkg.Tests.proof:(I)V"
        val json = """
            [
              {"result":[
                {"name":"c.1","status":"FAILURE","description":"assertion",
                 "sourceLocation":{"file":"Example.java","line":"3","function":"$proofFn"},
                 "trace":[
                   {"stepType":"function-call","function":{"identifier":"$proofFn"},
                    "sourceLocation":{"file":"Tests.java","line":"1"}},
                   {"stepType":"input","inputID":"p","sourceLocation":{"function":"$proofFn"}},
                   {"stepType":"assignment","lhs":"p",
                    "sourceLocation":{"function":"$proofFn"},"value":{"name":"integer","data":"42"}},
                   {"stepType":"failure",
                    "sourceLocation":{"function":"$proofFn","file":"Example.java","line":"3"}}
                 ]}
              ]}
            ]""".trimIndent()
        val r = JbmcOutputParser.parse(json, "pkg.Tests.proof")
        assertEquals(listOf("p = 42"), r.violations[0].counterexample,
                "a @BmcProof parameter (marked by an `input` step) is a nondet input and must be kept")
    }

    @Test
    fun a_trace_with_no_nondet_evidence_keeps_every_user_local_legacy_fallback() {
        // Defensive: if a trace carries NO nondet markers at all (an engine that doesn't emit them), the
        // gate must DEGRADE to the legacy "keep every user-frame declared local" behavior rather than
        // dropping real inputs — "prefer over-showing an input to dropping one".
        val proofFn = "java::pkg.Tests.proof:()V"
        val json = """
            [
              {"result":[
                {"name":"c.1","status":"FAILURE","description":"assertion",
                 "sourceLocation":{"file":"Example.java","line":"3","function":"$proofFn"},
                 "trace":[
                   {"stepType":"function-call","function":{"identifier":"$proofFn"},
                    "sourceLocation":{"file":"Tests.java","line":"1"}},
                   {"stepType":"assignment","lhs":"x",
                    "sourceLocation":{"function":"$proofFn"},"value":{"name":"integer","data":"7"}},
                   {"stepType":"failure",
                    "sourceLocation":{"function":"$proofFn","file":"Example.java","line":"3"}}
                 ]}
              ]}
            ]""".trimIndent()
        val r = JbmcOutputParser.parse(json, ENTRY)
        assertEquals(listOf("x = 7"), r.violations[0].counterexample,
                "with no nondet evidence in the trace, the gate degrades to keeping user locals")
    }

    @Test
    fun long_array_input_renders_with_a_long_kind() {
        // The long[] analogue: a `struct java::array[long] *` handle, `long` element type. The element
        // type drives the binding kind (`long[]`) the replay renderer keys on.
        val json = """
            [
              {"result":[
                {"name":"c.1","status":"FAILURE","description":"assertion",
                 "sourceLocation":{"file":"Example.java","line":"3","function":"java::pkg.Tests.proof:()V"},
                 "trace":[
                   {"stepType":"function-call","function":{"identifier":"java::pkg.Tests.proof:()V"},
                    "sourceLocation":{"file":"Tests.java","line":"1"}},
                   {"stepType":"assignment","lhs":"dynamic_object${'$'}0.data",
                    "sourceLocation":{"function":"java::pkg.Tests.proof:()V"},
                    "value":{"name":"pointer","data":"dynamic_array","type":"long *"}},
                   {"stepType":"assignment","lhs":"dynamic_array[0L]",
                    "sourceLocation":{"function":"java::pkg.Tests.proof:()V"},
                    "value":{"name":"integer","data":"7","type":"long"}},
                   {"stepType":"assignment","lhs":"dynamic_array[1L]",
                    "sourceLocation":{"function":"java::pkg.Tests.proof:()V"},
                    "value":{"name":"integer","data":"-2","type":"long"}},
                   {"stepType":"assignment","lhs":"a",
                    "sourceLocation":{"function":"java::pkg.Tests.proof:()V"},
                    "value":{"name":"pointer","data":"dynamic_object${'$'}0","type":"struct java::array[long] *"}},
                   {"stepType":"failure",
                    "sourceLocation":{"function":"java::pkg.Tests.proof:()V","file":"Example.java","line":"3"}}
                 ]}
              ]}
            ]""".trimIndent()
        val r = JbmcOutputParser.parse(json, ENTRY)
        assertEquals(listOf("a = [7, -2]"), r.violations[0].counterexample)
        assertEquals("long[]", r.violations[0].bindings.single { it.name == "a" }.kind)
    }

    @Test
    fun failure_without_trace_falls_back_to_source_location_frame() {
        val json = """
            [
              {"result":[
                {"name":"f.1","status":"FAILURE","description":"d",
                 "sourceLocation":{"file":"Example.java","line":"8","function":"java::pkg.Example.f:(I)V"}}
              ]}
            ]""".trimIndent()
        val r = JbmcOutputParser.parse(json, ENTRY)
        val v = r.violations[0]
        assertEquals(1, v.stack.size)
        assertEquals("pkg.Example", v.stack[0].className)
        assertEquals("Example.java", v.stack[0].fileName)
        assertEquals(8, v.stack[0].lineNumber)
        assertTrue(v.counterexample.isEmpty())
    }

    // --- unmodelled-member harvest (verdict honesty) ----------
    // A FAILURE whose violated function is the BmcUnmodelledReached sentinel is a model gap, not a
    // counterexample: the parser harvests the offending member (the sentinel's CALLER) so the
    // interpreter can demote the would-be REFUTED to a member-named UNKNOWN.

    @Test
    fun reaching_the_unmodelled_sentinel_harvests_the_caller_member() {
        val json = """
            [
              {"result":[
                {"name":"s.1","status":"FAILURE","description":"assertion",
                 "sourceLocation":{"file":"BmcUnmodelledReached.java","line":"40",
                   "function":"java::org.bmc4j.analysis.BmcUnmodelledReached.reached:(Ljava/lang/String;)V"},
                 "trace":[
                   {"stepType":"function-call","function":{"identifier":"java::pkg.Tests.proof:()V"},
                    "sourceLocation":{"file":"Tests.java","line":"5"}},
                   {"stepType":"function-call","function":{"identifier":"java::java.util.ArrayList.sort:(Ljava/util/Comparator;)V"},
                    "sourceLocation":{"file":"Tests.java","line":"7"}},
                   {"stepType":"function-call","function":{"identifier":"java::org.bmc4j.analysis.BmcUnmodelledReached.reached:(Ljava/lang/String;)V"},
                    "sourceLocation":{"file":"ArrayList.java","line":"1"}},
                   {"stepType":"failure",
                    "sourceLocation":{"function":"java::org.bmc4j.analysis.BmcUnmodelledReached.reached:(Ljava/lang/String;)V","file":"BmcUnmodelledReached.java","line":"40"}}
                 ]}
              ]},
              {"cProverStatus":"failure"}
            ]""".trimIndent()
        val r = JbmcOutputParser.parse(json, ENTRY)
        // The engine reports a would-be refutation, but the harvested fact names the reached member.
        assertFalse(r.isVerified)
        assertEquals(listOf("java.util.ArrayList.sort(Comparator)"), r.unmodelledMembers)
    }

    @Test
    fun a_plain_assertion_not_via_the_sentinel_harvests_nothing() {
        val json = """
            [
              {"result":[
                {"name":"f.1","status":"FAILURE","description":"assertion",
                 "sourceLocation":{"file":"Example.java","line":"12","function":"java::pkg.Example.f:(I)V"}}
              ]}
            ]""".trimIndent()
        val r = JbmcOutputParser.parse(json, ENTRY)
        assertTrue(r.unmodelledMembers.isEmpty())
    }

    // --- link-failure stub harvest (verdict honesty) ----------
    // A REFUTED whose counterexample ran THROUGH a nondet stub leaves a stub_ignored_arg* assignment
    // in the trace (JBMC names a synthesized stub body's ignored params that way). The parser harvests
    // the stubbed CALLEE — the innermost OPEN call frame at that assignment (the engine mislabels the
    // assignment's own sourceLocation.function with the CALLER) — so the interpreter can demote the
    // would-be REFUTED to a member-named UNKNOWN when that member's class is nonetheless on the classpath.

    @Test
    fun refuted_trace_with_stub_ignored_arg_harvests_the_stubbed_callee_not_the_caller() {
        // REGENERATED from a real `jbmc --json-ui --trace` run of LateinitProofs.read_before_init_is_a_defect
        // (fundamentals-kotlin, kotlinc 2.4.0 / JVM 21), trimmed to the load-bearing steps. The lateinit
        // pre-init read calls Session.getUser(), which calls the (nondet-stubbed)
        // Intrinsics.throwUninitializedPropertyAccessException; its synthesized stub body assigns the
        // ignored param stub_ignored_arg0.
        //
        // CRITICAL — the bug this regen fixes: the engine stamps the stub_ignored_arg0 assignment's
        // sourceLocation.function with the CALLER (Session.getUser), NOT the stubbed callee. The earlier
        // hand-built fixture wrongly placed the callee there, so the (caller-class-based) present-on-
        // classpath demotion always tripped on the wrong, ever-present class. The harvested member is the
        // INNERMOST OPEN FRAME (the callee), reconstructed from the function-call/return steps.
        val json = """
            [
              {"result":[
                {"status":"FAILURE","description":"Null pointer check",
                 "sourceLocation":{"file":"example/lateinitprops/Sessions.kt","line":"13",
                   "function":"java::example.lateinitprops.Session.greetLength:()I"},
                 "trace":[
                   {"stepType":"function-call","function":{"identifier":"java::proofs.lateinitprops.LateinitProofs.read_before_init_is_a_defect:()V"},
                    "sourceLocation":{"file":"proofs/lateinitprops/LateinitProofs.kt","line":"20"}},
                   {"stepType":"function-call","function":{"identifier":"java::example.lateinitprops.Session.greetLength:()I"},
                    "sourceLocation":{"file":"proofs/lateinitprops/LateinitProofs.kt","line":"21"}},
                   {"stepType":"function-call","function":{"identifier":"java::example.lateinitprops.Session.getUser:()Ljava/lang/String;"},
                    "sourceLocation":{"file":"example/lateinitprops/Sessions.kt","line":"13"}},
                   {"stepType":"function-call","function":{"identifier":"java::kotlin.jvm.internal.Intrinsics.throwUninitializedPropertyAccessException:(Ljava/lang/String;)V"},
                    "sourceLocation":{"file":"example/lateinitprops/Sessions.kt","line":"10"}},
                   {"stepType":"assignment","lhs":"stub_ignored_arg0",
                    "sourceLocation":{"function":"java::example.lateinitprops.Session.getUser:()Ljava/lang/String;"},
                    "value":{"name":"pointer","data":"java.lang.String.Literal.user","type":"struct java.lang.String *"}},
                   {"stepType":"function-return","function":{"identifier":"java::kotlin.jvm.internal.Intrinsics.throwUninitializedPropertyAccessException:(Ljava/lang/String;)V"}},
                   {"stepType":"function-return","function":{"identifier":"java::example.lateinitprops.Session.getUser:()Ljava/lang/String;"}},
                   {"stepType":"failure",
                    "sourceLocation":{"function":"java::example.lateinitprops.Session.greetLength:()I","file":"example/lateinitprops/Sessions.kt","line":"13"}}
                 ]}
              ]},
              {"cProverStatus":"failure"}
            ]""".trimIndent()
        val r = JbmcOutputParser.parse(json, "proofs.lateinitprops.LateinitProofs.read_before_init_is_a_defect")
        // The verdict FACT is still a refutation here — the parser only attaches the stub member; the
        // demote-to-UNKNOWN policy (which needs the classpath, and which leaves an expect=REFUTED demo
        // alone) lives in BmcProofExtension.
        assertFalse(r.isVerified)
        // The harvested member is the STUBBED CALLEE (the innermost open frame), never the caller the
        // engine mislabels on the assignment's source location.
        assertEquals(
                listOf("kotlin.jvm.internal.Intrinsics.throwUninitializedPropertyAccessException(String)"),
                r.linkFailureStubs)
    }

    @Test
    fun a_genuine_refutation_with_no_stub_in_the_trace_harvests_no_link_failure() {
        // No stub_ignored_arg* anywhere -> a real counterexample, nothing to demote: stays REFUTED.
        val json = """
            [
              {"result":[
                {"name":"f.1","status":"FAILURE","description":"assertion",
                 "sourceLocation":{"file":"Example.java","line":"12","function":"java::pkg.Example.f:(I)V"},
                 "trace":[
                   {"stepType":"function-call","function":{"identifier":"java::pkg.Tests.proof:()V"},
                    "sourceLocation":{"file":"Tests.java","line":"5"}},
                   {"stepType":"assignment","lhs":"score",
                    "sourceLocation":{"function":"java::pkg.Tests.proof:()V"},
                    "value":{"name":"integer","data":"100"}},
                   {"stepType":"failure",
                    "sourceLocation":{"function":"java::pkg.Tests.proof:()V","file":"Example.java","line":"12"}}
                 ]}
              ]},
              {"cProverStatus":"failure"}
            ]""".trimIndent()
        val r = JbmcOutputParser.parse(json, ENTRY)
        assertFalse(r.isVerified)
        assertEquals(1, r.violations.size)
        assertTrue(r.linkFailureStubs.isEmpty())
    }

    // --- "no body for callee" devirtualization-failure harvest (lever a safety net) ----------
    // When JBMC cannot bind an invokeinterface/abstract call to its present concrete override it emits a
    // FAILURE property whose description is "no body for callee <pkg.Class.method(params)>". The parser
    // folds that member into linkFailureStubs so the interpreter demotes the would-be REFUTED to a
    // member-named UNKNOWN (the owning interface IS on the classpath) instead of a false refutation.

    @Test
    fun no_body_for_callee_description_harvests_the_interface_member() {
        // A user subclass of (unmodelled) AbstractList held through java.util.List: the size() call has
        // "no body for callee", and the Bmc.check fails on the havoc'd result. Both surface as FAILUREs.
        val json = """
            [
              {"result":[
                {"name":"p.1","status":"FAILURE","description":"a checked property does not hold",
                 "sourceLocation":{"file":"Proof.java","line":"49","function":"java::pkg.Proof.p:()V"}},
                {"name":"p.2","status":"FAILURE","description":"no body for callee java.util.List.size()",
                 "sourceLocation":{"file":"Proof.java","line":"49","function":"java::pkg.Proof.p:()V"}}
              ]},
              {"cProverStatus":"failure"}
            ]""".trimIndent()
        val r = JbmcOutputParser.parse(json, ENTRY)
        assertFalse(r.isVerified)
        // The harvested member is exactly the interface method JBMC could not resolve, dot/erased form.
        assertEquals(listOf("java.util.List.size()"), r.linkFailureStubs)
    }

    @Test
    fun no_body_for_callee_harvests_each_distinct_member_first_seen_order() {
        val json = """
            [
              {"result":[
                {"name":"a","status":"FAILURE","description":"no body for callee java.util.List.contains(java.lang.Object)",
                 "sourceLocation":{"file":"Proof.java","line":"70","function":"java::pkg.Proof.p:()V"}},
                {"name":"b","status":"FAILURE","description":"no body for callee java.util.List.indexOf(java.lang.Object)",
                 "sourceLocation":{"file":"Proof.java","line":"70","function":"java::pkg.Proof.p:()V"}},
                {"name":"c","status":"FAILURE","description":"no body for callee java.util.List.contains(java.lang.Object)",
                 "sourceLocation":{"file":"Proof.java","line":"70","function":"java::pkg.Proof.p:()V"}}
              ]},
              {"cProverStatus":"failure"}
            ]""".trimIndent()
        val r = JbmcOutputParser.parse(json, ENTRY)
        assertEquals(
                listOf("java.util.List.contains(java.lang.Object)", "java.util.List.indexOf(java.lang.Object)"),
                r.linkFailureStubs)
    }

    @Test
    fun a_plain_assertion_without_no_body_description_harvests_no_link_failure() {
        // A genuine assertion failure (not a "no body for callee") leaves linkFailureStubs empty, so a
        // real refutation is never masked.
        val json = """
            [
              {"result":[
                {"name":"f.1","status":"FAILURE","description":"a checked property does not hold",
                 "sourceLocation":{"file":"Example.java","line":"12","function":"java::pkg.Example.f:(I)V"}}
              ]},
              {"cProverStatus":"failure"}
            ]""".trimIndent()
        val r = JbmcOutputParser.parse(json, ENTRY)
        assertFalse(r.isVerified)
        assertTrue(r.linkFailureStubs.isEmpty())
    }

    // --- vacuity check: the injected reachability marker ----------
    // The marker is identified by the sentinel source line BmcReachability.SENTINEL_LINE.

    @Test
    fun marker_FAILED_and_props_PASS_is_verified_and_marker_not_a_violation() {
        // Reachable end: the marker assertion FAILED. cProverStatus is "failure" *because of* the
        // marker, but the proof is verified (no user failure) and the marker is never a violation.
        val json = """
            [
              {"result":[
                {"name":"u","status":"SUCCESS","description":"a checked property",
                 "sourceLocation":{"file":"V.java","line":"5","function":"java::pkg.Tests.proof:()V"}},
                {"name":"m","status":"FAILURE","description":"assertion at file V.java line %d ...",
                 "sourceLocation":{"file":"V.java","line":"%d","function":"java::pkg.Tests.proof:()V"}}
              ]},
              {"cProverStatus":"failure"}
            ]""".trimIndent().format(SENTINEL, SENTINEL)
        val r = JbmcOutputParser.parse(json, ENTRY)
        assertTrue(r.isVerified)
        assertFalse(r.isVacuous)
        assertTrue(r.violations.isEmpty())
    }

    @Test
    fun marker_SUCCESS_with_no_user_failure_is_vacuous_with_dedicated_message() {
        // Unreachable end: the only marker is SUCCESS -> assumptions unsatisfiable -> vacuous.
        val json = """
            [
              {"result":[
                {"name":"u","status":"SUCCESS","sourceLocation":{"line":"5","function":"java::pkg.Tests.proof:()V"}},
                {"name":"m","status":"SUCCESS","description":"assertion ...",
                 "sourceLocation":{"file":"V.java","line":"%d","function":"java::pkg.Tests.proof:()V"}}
              ]},
              {"cProverStatus":"success"}
            ]""".trimIndent().format(SENTINEL)
        val r = JbmcOutputParser.parse(json, ENTRY)
        assertFalse(r.isVerified)
        assertTrue(r.isVacuous)
        assertEquals(1, r.violations.size)
        assertEquals(BmcReachability.VACUOUS_MESSAGE, r.violations[0].description)
    }

    @Test
    fun at_least_one_reachable_marker_means_not_vacuous_even_if_another_is_unreachable() {
        // Two normal exits; one reachable (FAILURE), one pruned by assumeUnreachable (SUCCESS).
        // This is the early-return / expected-exception shape: NOT vacuous.
        val json = """
            [
              {"result":[
                {"name":"m1","status":"FAILURE","sourceLocation":{"line":"%d","function":"java::pkg.Tests.proof:()V"}},
                {"name":"m2","status":"SUCCESS","sourceLocation":{"line":"%d","function":"java::pkg.Tests.proof:()V"}}
              ]},
              {"cProverStatus":"failure"}
            ]""".trimIndent().format(SENTINEL, SENTINEL)
        val r = JbmcOutputParser.parse(json, ENTRY)
        assertTrue(r.isVerified)
        assertFalse(r.isVacuous)
    }

    @Test
    fun a_real_user_failure_is_refuted_not_vacuous_even_if_a_marker_is_unreachable() {
        // A genuine bug refutes the proof; vacuity must not mask or replace that verdict.
        val json = """
            [
              {"result":[
                {"name":"bug","status":"FAILURE","description":"a checked property does not hold",
                 "sourceLocation":{"file":"V.java","line":"7","function":"java::pkg.Tests.proof:()V"}},
                {"name":"m","status":"SUCCESS","sourceLocation":{"line":"%d","function":"java::pkg.Tests.proof:()V"}}
              ]},
              {"cProverStatus":"failure"}
            ]""".trimIndent().format(SENTINEL)
        val r = JbmcOutputParser.parse(json, ENTRY)
        assertFalse(r.isVerified)
        assertFalse(r.isVacuous)
        assertEquals(1, r.violations.size)
        assertEquals("a checked property does not hold", r.violations[0].description)
    }

    @Test
    fun returnless_proof_with_no_markers_is_not_verified() {
        // A return-less proof (while(true){...} / always-throws) emits NO reachability markers, so the
        // vacuity check can't run and assume(false) could otherwise "pass". With no user FAILURE and no
        // markers the verdict must be UNKNOWN (undecided), never a silent VERIFIED.
        val json = """
            [
              {"messageType":"STATUS-MESSAGE","messageText":"Starting Bounded Model Checking"},
              {"result":[]},
              {"cProverStatus":"success"}
            ]""".trimIndent()
        val r = JbmcOutputParser.parse(json, ENTRY)
        assertFalse(r.isVerified)
        assertTrue(r.isUnknown)
        assertFalse(r.isVacuous)
    }

    @Test
    fun arrayvalid_output_with_no_result_or_status_is_UNKNOWN_not_verified() {
        // Truncated/partial output: a well-formed JSON array with no result property and no
        // cProverStatus. The old fallback defaulted a null status to "ok" and read this as VERIFIED;
        // it must be UNKNOWN.
        val json = """
            [
              {"messageType":"STATUS-MESSAGE","messageText":"Generated 12 VCC(s), 3 remaining after simplification"}
            ]""".trimIndent()
        val r = JbmcOutputParser.parse(json, ENTRY)
        assertFalse(r.isVerified)
        assertTrue(r.isUnknown)
        assertTrue(r.violations.isEmpty())
    }

    // --- nondet-stub harvesting ----------------------------------
    // Pin the bundled engine's "opaque symbol" message format: cbmc 6.9.0 emits, at --verbosity 10,
    // STATUS-MESSAGEs "Generating codet:  new opaque symbol: method 'java::FQN:(sig)'" for every callee
    // it stubbed to nondet. (Captured from a live jbmc run against an unmodeled JDK call.) The engine
    // identity is in the verdict-cache key, so an engine bump that changes this format forces a re-prove.

    @Test
    fun harvests_opaque_stub_methods_and_strips_signature_and_prefix() {
        val json = """
            [
              {"messageType":"STATUS-MESSAGE",
               "messageText":"Generating codet:  new opaque symbol: method 'java::java.util.List.stream:()Ljava/util/stream/Stream;'"},
              {"messageType":"STATUS-MESSAGE",
               "messageText":"Generating codet:  new opaque symbol: method 'java::java.util.stream.Stream.count:()J'"},
              {"result":[{"name":"p1","status":"SUCCESS"}]},
              {"cProverStatus":"success"}
            ]""".trimIndent()
        val r = JbmcOutputParser.parse(json, ENTRY)
        // Stub harvesting is independent of the verdict; the marker-less verdict itself is UNKNOWN.
        assertTrue(r.isUnknown)
        assertEquals(listOf("java.util.List.stream", "java.util.stream.Stream.count"),
                r.stubbedMethods)
    }

    @Test
    fun harvest_filters_synthetics_boxing_and_assertion_plumbing() {
        // From a real run: boxing (Integer.valueOf), <init>/<clinit>, AssertionError, and
        // desiredAssertionStatus are JBMC noise, not user-meaningful modeling gaps.
        val json = """
            [
              {"messageText":"Generating codet:  new opaque symbol: method 'java::java.lang.Integer.valueOf:(I)Ljava/lang/Integer;'"},
              {"messageText":"Generating codet:  new opaque symbol: method 'java::java.lang.AssertionError.<init>:()V'"},
              {"messageText":"Generating codet:  new opaque symbol: method 'java::java.lang.Class.desiredAssertionStatus:()Z'"},
              {"messageText":"Generating codet:  new opaque symbol: method 'java::java.util.Formatter.format:(Ljava/lang/String;[Ljava/lang/Object;)Ljava/util/Formatter;'"},
              {"result":[{"name":"p1","status":"SUCCESS"}]},
              {"cProverStatus":"success"}
            ]""".trimIndent()
        val r = JbmcOutputParser.parse(json, ENTRY)
        assertEquals(listOf("java.util.Formatter.format"), r.stubbedMethods)
    }

    @Test
    fun harvest_dedupes_and_is_empty_when_fully_modeled() {
        val dup = """
            [
              {"messageText":"Generating codet:  new opaque symbol: method 'java::java.util.List.stream:()Ljava/util/stream/Stream;'"},
              {"messageText":"Generating codet:  new opaque symbol: method 'java::java.util.List.stream:()Ljava/util/stream/Stream;'"},
              {"result":[{"name":"p1","status":"SUCCESS"}]}
            ]""".trimIndent()
        assertEquals(listOf("java.util.List.stream"), JbmcOutputParser.parse(dup, ENTRY).stubbedMethods)

        val clean = """
            [
              {"messageText":"VERIFICATION SUCCESSFUL"},
              {"result":[{"name":"p1","status":"SUCCESS"}]},
              {"cProverStatus":"success"}
            ]""".trimIndent()
        assertTrue(JbmcOutputParser.parse(clean, ENTRY).stubbedMethods.isEmpty())
    }

    @Test
    fun harvest_attaches_stubs_even_on_a_refuted_verdict() {
        // The stub FACT is harvested regardless of verdict — policy is applied later.
        val json = """
            [
              {"messageText":"Generating codet:  new opaque symbol: method 'java::java.util.List.stream:()Ljava/util/stream/Stream;'"},
              {"result":[
                {"name":"bad","status":"FAILURE","description":"d","sourceLocation":{"file":"A.java","line":"1"}}
              ]},
              {"cProverStatus":"failure"}
            ]""".trimIndent()
        val r = JbmcOutputParser.parse(json, ENTRY)
        assertFalse(r.isVerified)
        assertEquals(listOf("java.util.List.stream"), r.stubbedMethods)
    }

    // --- Unwinding assertions: bound-too-small is UNKNOWN, never REFUTED -----------------------

    @Test
    fun unwinding_assertion_failure_alone_is_UNKNOWN_not_refuted() {
        // --unwinding-assertions firing says the LOOP BOUND truncated exploration: incompleteness,
        // not a counterexample. Mislabeling it REFUTED let expect = REFUTED demos pass for the
        // wrong reason. Marker reachable (the proof body itself is fine within the bound).
        val json = """
            [
              {"result":[
                {"name":"u","status":"FAILURE","property":"java::pkg.Tests.proof.unwind.0",
                 "description":"unwinding assertion loop 0",
                 "sourceLocation":{"file":"T.java","line":"9","function":"java::pkg.Tests.proof:()V"}},
                {"name":"m","status":"FAILURE","description":"assertion ...",
                 "sourceLocation":{"file":"V.java","line":"%d","function":"java::pkg.Tests.proof:()V"}}
              ]},
              {"cProverStatus":"failure"}
            ]""".trimIndent().format(SENTINEL)
        val r = JbmcOutputParser.parse(json, ENTRY)
        assertTrue(r.isUnknown, "bound-too-small must be UNKNOWN, got " + r.verdict)
        assertFalse(r.isVacuous)
        assertTrue(r.violations.isEmpty(), "an unwinding assertion is not a counterexample")
        assertTrue(r.undecidedReason!!.contains("unwind bound is too small"), r.undecidedReason)
    }

    @Test
    fun recursion_unwinding_assertion_is_UNKNOWN_not_refuted() {
        // The RECURSION flavour of the same incompleteness: cbmc names a recursive-depth overrun
        // "<function>.recursion" with description "recursion unwinding assertion" (pinned against
        // the bundled engine: jbmc 6.9.0 emits exactly this for a depth-10 callee at --unwind 2).
        // It must be UNKNOWN for the same reason as the loop shape — nothing was proven wrong.
        val json = """
            [
              {"result":[
                {"name":"u","status":"FAILURE","property":"java::pkg.Deep.down:(I)I.recursion",
                 "description":"recursion unwinding assertion",
                 "sourceLocation":{"file":"D.java","line":"4","function":"java::pkg.Deep.down:(I)I"}},
                {"name":"m","status":"FAILURE","description":"assertion ...",
                 "sourceLocation":{"file":"V.java","line":"%d","function":"java::pkg.Tests.proof:()V"}}
              ]},
              {"cProverStatus":"failure"}
            ]""".trimIndent().format(SENTINEL)
        val r = JbmcOutputParser.parse(json, ENTRY)
        assertTrue(r.isUnknown, "recursion bound-too-small must be UNKNOWN, got " + r.verdict)
        assertFalse(r.isVacuous)
        assertTrue(r.violations.isEmpty(), "a recursion unwinding assertion is not a counterexample")
        assertTrue(r.undecidedReason!!.contains("unwind bound is too small"), r.undecidedReason)
    }

    @Test
    fun real_failure_stays_REFUTED_even_with_an_unwinding_assertion_alongside() {
        // A trace within the bound is a REAL trace (under-approximation): a genuine user FAILURE
        // is a counterexample even if the bound ALSO truncated deeper paths. Only the real
        // violation is reported; the unwinding firing is not listed as a violation.
        val json = """
            [
              {"result":[
                {"name":"u1","status":"FAILURE","property":"java::pkg.Tests.proof.unwind.0",
                 "description":"unwinding assertion loop 0",
                 "sourceLocation":{"file":"T.java","line":"9","function":"java::pkg.Tests.proof:()V"}},
                {"name":"u2","status":"FAILURE","description":"array index out of bounds",
                 "sourceLocation":{"file":"T.java","line":"12","function":"java::pkg.Tests.proof:()V"}},
                {"name":"m","status":"FAILURE","description":"assertion ...",
                 "sourceLocation":{"file":"V.java","line":"%d","function":"java::pkg.Tests.proof:()V"}}
              ]},
              {"cProverStatus":"failure"}
            ]""".trimIndent().format(SENTINEL)
        val r = JbmcOutputParser.parse(json, ENTRY)
        assertFalse(r.isVerified)
        assertFalse(r.isUnknown, "a real counterexample within the bound is a refutation")
        assertEquals(1, r.violations.size, "only the real violation is reported")
        assertEquals("array index out of bounds", r.violations[0].description)
    }

    @Test
    fun unwinding_assertion_with_unreachable_markers_is_UNKNOWN_not_vacuous() {
        // With the bound truncating exploration, unreachable markers may just be cut off — claiming
        // VACUOUS (assumptions unsatisfiable) would be wrong. Bound-too-small wins.
        val json = """
            [
              {"result":[
                {"name":"u","status":"FAILURE","property":"java::pkg.Tests.proof.unwind.1",
                 "description":"unwinding assertion loop 1",
                 "sourceLocation":{"file":"T.java","line":"9","function":"java::pkg.Tests.proof:()V"}},
                {"name":"m","status":"SUCCESS","description":"assertion ...",
                 "sourceLocation":{"file":"V.java","line":"%d","function":"java::pkg.Tests.proof:()V"}}
              ]},
              {"cProverStatus":"failure"}
            ]""".trimIndent().format(SENTINEL)
        val r = JbmcOutputParser.parse(json, ENTRY)
        assertTrue(r.isUnknown, "bound truncation must not be mistaken for vacuity")
        assertFalse(r.isVacuous)
        assertTrue(r.undecidedReason!!.contains("unwind bound is too small"), r.undecidedReason)
    }

    companion object {
        private const val ENTRY = "pkg.Tests.proof"

        private val SENTINEL = BmcReachability.SENTINEL_LINE
    }
}
