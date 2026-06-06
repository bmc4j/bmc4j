package org.bmc4j.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link JbmcOutputParser} — the pure JBMC {@code --json-ui} parser.
 * A regression here silently corrupts every proof's verdict, so the branches are
 * pinned with hand-built sample outputs.
 */
class JbmcOutputParserTest {

    private static final String ENTRY = "pkg.Tests.proof";

    @Test
    void markerless_success_output_is_UNKNOWN_not_verified() {
        // No reachability markers => the vacuity check never ran, so a green here would be unsound:
        // every real @BmcProof run carries markers. cProverStatus "success" alone must NOT pass.
        String json = """
            [
              {"messageType":"STATUS-MESSAGE","messageText":"starting"},
              {"result":[{"name":"p1","status":"SUCCESS"}]},
              {"cProverStatus":"success"}
            ]""";
        JbmcResult r = JbmcOutputParser.parse(json, ENTRY);
        assertFalse(r.isVerified());
        assertTrue(r.isUnknown());
        assertTrue(r.violations().isEmpty());
        assertEquals(json, r.rawOutput());
        assertTrue(r.undecidedReason() != null && r.undecidedReason().contains("markers missing"));
    }

    @Test
    void malformed_output_is_UNKNOWN_not_a_thrown_error_or_a_pass() {
        // unparseable engine output is undecided, not a silent pass and not a crash.
        JbmcResult r = JbmcOutputParser.parse("this is not json {{{", ENTRY);
        assertFalse(r.isVerified());
        assertTrue(r.isUnknown());
        assertTrue(r.violations().isEmpty());
        assertTrue(r.undecidedReason() != null && r.undecidedReason().contains("could not parse"));
    }

    @Test
    void truncated_json_is_UNKNOWN() {
        // A run killed mid-write leaves half a document; that's undecided, not refuted/verified.
        JbmcResult r = JbmcOutputParser.parse("[{\"result\":[{\"name\":\"p\",\"sta", ENTRY);
        assertTrue(r.isUnknown());
    }

    @Test
    void empty_array_is_UNKNOWN_no_markers_means_no_proof_ran() {
        // An empty result carries no reachability markers — the vacuity check never ran, so this
        // cannot be a sound green; it is undecided.
        JbmcResult r = JbmcOutputParser.parse("[]", ENTRY);
        assertFalse(r.isVerified());
        assertTrue(r.isUnknown());
        assertTrue(r.violations().isEmpty());
    }

    @Test
    void cProverStatus_failure_makes_it_unverified_even_with_no_failure_properties() {
        String json = """
            [
              {"result":[{"name":"p1","status":"SUCCESS"}]},
              {"cProverStatus":"failure"}
            ]""";
        JbmcResult r = JbmcOutputParser.parse(json, ENTRY);
        assertFalse(r.isVerified());
        assertTrue(r.violations().isEmpty());
    }

    @Test
    void single_failure_extracts_description_and_location() {
        String json = """
            [
              {"result":[
                {"name":"a.1","status":"FAILURE","description":"array bounds in f",
                 "sourceLocation":{"file":"Example.java","line":"12","function":"java::pkg.Example.f:(I)V"}}
              ]},
              {"cProverStatus":"failure"}
            ]""";
        JbmcResult r = JbmcOutputParser.parse(json, ENTRY);
        assertFalse(r.isVerified());
        assertEquals(1, r.violations().size());
        JbmcResult.Violation v = r.violations().get(0);
        assertEquals("array bounds in f", v.description());
        assertEquals("Example.java", v.file());
        assertEquals(12, v.line());
    }

    @Test
    void only_failure_properties_become_violations() {
        String json = """
            [
              {"result":[
                {"name":"ok","status":"SUCCESS"},
                {"name":"bad1","status":"FAILURE","description":"d1","sourceLocation":{"file":"A.java","line":"1"}},
                {"name":"bad2","status":"FAILURE","description":"d2","sourceLocation":{"file":"A.java","line":"2"}}
              ]}
            ]""";
        JbmcResult r = JbmcOutputParser.parse(json, ENTRY);
        assertFalse(r.isVerified());
        assertEquals(2, r.violations().size());
        assertEquals("d1", r.violations().get(0).description());
        assertEquals("d2", r.violations().get(1).description());
    }

    @Test
    void reconstructs_call_stack_and_counterexample_from_trace() {
        String json = """
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
            ]""";
        JbmcResult r = JbmcOutputParser.parse(json, ENTRY);
        JbmcResult.Violation v = r.violations().get(0);

        assertEquals(List.of("score = 100"), v.counterexample());

        List<StackTraceElement> stack = v.stack();
        assertEquals(2, stack.size());
        assertEquals("pkg.Example", stack.get(0).getClassName());
        assertEquals("f", stack.get(0).getMethodName());
        assertEquals("Example.java", stack.get(0).getFileName());
        assertEquals(12, stack.get(0).getLineNumber());
        assertEquals("pkg.Tests", stack.get(1).getClassName());
        assertEquals("proof", stack.get(1).getMethodName());
        assertEquals(7, stack.get(1).getLineNumber());   // rendered at the call site
    }

    @Test
    void trace_also_yields_structured_bindings_for_replay() {
        // alongside the human-readable "score = 100" the parser carries a structured
        // binding (name, kind, data) the replay renderer turns into concrete Java.
        String json = """
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
            ]""";
        JbmcResult r = JbmcOutputParser.parse(json, ENTRY);
        JbmcResult.Violation v = r.violations().get(0);
        assertEquals(List.of("score = 100"), v.counterexample());
        assertEquals(1, v.bindings().size());
        assertEquals("score", v.bindings().get(0).name());
        assertEquals("integer", v.bindings().get(0).kind());
        assertEquals("100", v.bindings().get(0).data());
    }

    @Test
    void bmc_check_failure_is_repointed_to_the_user_frame_and_internal_frames_hidden() {
        String json = """
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
            ]""";
        JbmcResult r = JbmcOutputParser.parse(json, ENTRY);
        JbmcResult.Violation v = r.violations().get(0);

        // Re-pointed to the user's proof line + given a clean description.
        assertEquals("a checked property does not hold", v.description());
        assertEquals("Tests.java", v.file());
        assertEquals(9, v.line());
        // Internal Bmc frame is stripped, leaving only the user frame.
        assertEquals(1, v.stack().size());
        assertEquals("pkg.Tests", v.stack().get(0).getClassName());
        assertEquals(List.of("score = 100"), v.counterexample());
    }

    @Test
    void counterexample_filters_synthetics_other_functions_and_nonprimitives_last_wins() {
        String json = """
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
            ]""";
        JbmcResult r = JbmcOutputParser.parse(json, ENTRY);
        // Only the real, primitive, proof-local variable survives, at its LAST value.
        assertEquals(List.of("amount = 42"), r.violations().get(0).counterexample());
    }

    @Test
    void failure_without_trace_falls_back_to_source_location_frame() {
        String json = """
            [
              {"result":[
                {"name":"f.1","status":"FAILURE","description":"d",
                 "sourceLocation":{"file":"Example.java","line":"8","function":"java::pkg.Example.f:(I)V"}}
              ]}
            ]""";
        JbmcResult r = JbmcOutputParser.parse(json, ENTRY);
        JbmcResult.Violation v = r.violations().get(0);
        assertEquals(1, v.stack().size());
        assertEquals("pkg.Example", v.stack().get(0).getClassName());
        assertEquals("Example.java", v.stack().get(0).getFileName());
        assertEquals(8, v.stack().get(0).getLineNumber());
        assertTrue(v.counterexample().isEmpty());
    }

    // --- vacuity check: the injected reachability marker ----------
    // The marker is identified by the sentinel source line BmcReachability.SENTINEL_LINE.

    private static final int SENTINEL = BmcReachability.SENTINEL_LINE;

    @Test
    void marker_FAILED_and_props_PASS_is_verified_and_marker_not_a_violation() {
        // Reachable end: the marker assertion FAILED. cProverStatus is "failure" *because of* the
        // marker, but the proof is verified (no user failure) and the marker is never a violation.
        String json = """
            [
              {"result":[
                {"name":"u","status":"SUCCESS","description":"a checked property",
                 "sourceLocation":{"file":"V.java","line":"5","function":"java::pkg.Tests.proof:()V"}},
                {"name":"m","status":"FAILURE","description":"assertion at file V.java line %d ...",
                 "sourceLocation":{"file":"V.java","line":"%d","function":"java::pkg.Tests.proof:()V"}}
              ]},
              {"cProverStatus":"failure"}
            ]""".formatted(SENTINEL, SENTINEL);
        JbmcResult r = JbmcOutputParser.parse(json, ENTRY);
        assertTrue(r.isVerified());
        assertFalse(r.isVacuous());
        assertTrue(r.violations().isEmpty());
    }

    @Test
    void marker_SUCCESS_with_no_user_failure_is_vacuous_with_dedicated_message() {
        // Unreachable end: the only marker is SUCCESS -> assumptions unsatisfiable -> vacuous.
        String json = """
            [
              {"result":[
                {"name":"u","status":"SUCCESS","sourceLocation":{"line":"5","function":"java::pkg.Tests.proof:()V"}},
                {"name":"m","status":"SUCCESS","description":"assertion ...",
                 "sourceLocation":{"file":"V.java","line":"%d","function":"java::pkg.Tests.proof:()V"}}
              ]},
              {"cProverStatus":"success"}
            ]""".formatted(SENTINEL);
        JbmcResult r = JbmcOutputParser.parse(json, ENTRY);
        assertFalse(r.isVerified());
        assertTrue(r.isVacuous());
        assertEquals(1, r.violations().size());
        assertEquals(BmcReachability.VACUOUS_MESSAGE, r.violations().get(0).description());
    }

    @Test
    void at_least_one_reachable_marker_means_not_vacuous_even_if_another_is_unreachable() {
        // Two normal exits; one reachable (FAILURE), one pruned by assumeUnreachable (SUCCESS).
        // This is the early-return / expected-exception shape: NOT vacuous.
        String json = """
            [
              {"result":[
                {"name":"m1","status":"FAILURE","sourceLocation":{"line":"%d","function":"java::pkg.Tests.proof:()V"}},
                {"name":"m2","status":"SUCCESS","sourceLocation":{"line":"%d","function":"java::pkg.Tests.proof:()V"}}
              ]},
              {"cProverStatus":"failure"}
            ]""".formatted(SENTINEL, SENTINEL);
        JbmcResult r = JbmcOutputParser.parse(json, ENTRY);
        assertTrue(r.isVerified());
        assertFalse(r.isVacuous());
    }

    @Test
    void a_real_user_failure_is_refuted_not_vacuous_even_if_a_marker_is_unreachable() {
        // A genuine bug refutes the proof; vacuity must not mask or replace that verdict.
        String json = """
            [
              {"result":[
                {"name":"bug","status":"FAILURE","description":"a checked property does not hold",
                 "sourceLocation":{"file":"V.java","line":"7","function":"java::pkg.Tests.proof:()V"}},
                {"name":"m","status":"SUCCESS","sourceLocation":{"line":"%d","function":"java::pkg.Tests.proof:()V"}}
              ]},
              {"cProverStatus":"failure"}
            ]""".formatted(SENTINEL);
        JbmcResult r = JbmcOutputParser.parse(json, ENTRY);
        assertFalse(r.isVerified());
        assertFalse(r.isVacuous());
        assertEquals(1, r.violations().size());
        assertEquals("a checked property does not hold", r.violations().get(0).description());
    }

    @Test
    void returnless_proof_with_no_markers_is_not_verified() {
        // A return-less proof (while(true){...} / always-throws) emits NO reachability markers, so the
        // vacuity check can't run and assume(false) could otherwise "pass". With no user FAILURE and no
        // markers the verdict must be UNKNOWN (undecided), never a silent VERIFIED.
        String json = """
            [
              {"messageType":"STATUS-MESSAGE","messageText":"Starting Bounded Model Checking"},
              {"result":[]},
              {"cProverStatus":"success"}
            ]""";
        JbmcResult r = JbmcOutputParser.parse(json, ENTRY);
        assertFalse(r.isVerified());
        assertTrue(r.isUnknown());
        assertFalse(r.isVacuous());
    }

    @Test
    void arrayvalid_output_with_no_result_or_status_is_UNKNOWN_not_verified() {
        // Truncated/partial output: a well-formed JSON array with no result property and no
        // cProverStatus. The old fallback defaulted a null status to "ok" and read this as VERIFIED;
        // it must be UNKNOWN.
        String json = """
            [
              {"messageType":"STATUS-MESSAGE","messageText":"Generated 12 VCC(s), 3 remaining after simplification"}
            ]""";
        JbmcResult r = JbmcOutputParser.parse(json, ENTRY);
        assertFalse(r.isVerified());
        assertTrue(r.isUnknown());
        assertTrue(r.violations().isEmpty());
    }

    // --- nondet-stub harvesting ----------------------------------
    // Pin the bundled engine's "opaque symbol" message format: cbmc 6.9.0 emits, at --verbosity 10,
    // STATUS-MESSAGEs "Generating codet:  new opaque symbol: method 'java::FQN:(sig)'" for every callee
    // it stubbed to nondet. (Captured from a live jbmc run against an unmodeled JDK call.) The engine
    // identity is in the verdict-cache key, so an engine bump that changes this format forces a re-prove.

    @Test
    void harvests_opaque_stub_methods_and_strips_signature_and_prefix() {
        String json = """
            [
              {"messageType":"STATUS-MESSAGE",
               "messageText":"Generating codet:  new opaque symbol: method 'java::java.util.List.stream:()Ljava/util/stream/Stream;'"},
              {"messageType":"STATUS-MESSAGE",
               "messageText":"Generating codet:  new opaque symbol: method 'java::java.util.stream.Stream.count:()J'"},
              {"result":[{"name":"p1","status":"SUCCESS"}]},
              {"cProverStatus":"success"}
            ]""";
        JbmcResult r = JbmcOutputParser.parse(json, ENTRY);
        // Stub harvesting is independent of the verdict; the marker-less verdict itself is UNKNOWN.
        assertTrue(r.isUnknown());
        assertEquals(List.of("java.util.List.stream", "java.util.stream.Stream.count"),
                r.stubbedMethods());
    }

    @Test
    void harvest_filters_synthetics_boxing_and_assertion_plumbing() {
        // From a real run: boxing (Integer.valueOf), <init>/<clinit>, AssertionError, and
        // desiredAssertionStatus are JBMC noise, not user-meaningful modeling gaps.
        String json = """
            [
              {"messageText":"Generating codet:  new opaque symbol: method 'java::java.lang.Integer.valueOf:(I)Ljava/lang/Integer;'"},
              {"messageText":"Generating codet:  new opaque symbol: method 'java::java.lang.AssertionError.<init>:()V'"},
              {"messageText":"Generating codet:  new opaque symbol: method 'java::java.lang.Class.desiredAssertionStatus:()Z'"},
              {"messageText":"Generating codet:  new opaque symbol: method 'java::java.util.Formatter.format:(Ljava/lang/String;[Ljava/lang/Object;)Ljava/util/Formatter;'"},
              {"result":[{"name":"p1","status":"SUCCESS"}]},
              {"cProverStatus":"success"}
            ]""";
        JbmcResult r = JbmcOutputParser.parse(json, ENTRY);
        assertEquals(List.of("java.util.Formatter.format"), r.stubbedMethods());
    }

    @Test
    void harvest_dedupes_and_is_empty_when_fully_modeled() {
        String dup = """
            [
              {"messageText":"Generating codet:  new opaque symbol: method 'java::java.util.List.stream:()Ljava/util/stream/Stream;'"},
              {"messageText":"Generating codet:  new opaque symbol: method 'java::java.util.List.stream:()Ljava/util/stream/Stream;'"},
              {"result":[{"name":"p1","status":"SUCCESS"}]}
            ]""";
        assertEquals(List.of("java.util.List.stream"), JbmcOutputParser.parse(dup, ENTRY).stubbedMethods());

        String clean = """
            [
              {"messageText":"VERIFICATION SUCCESSFUL"},
              {"result":[{"name":"p1","status":"SUCCESS"}]},
              {"cProverStatus":"success"}
            ]""";
        assertTrue(JbmcOutputParser.parse(clean, ENTRY).stubbedMethods().isEmpty());
    }

    @Test
    void harvest_attaches_stubs_even_on_a_refuted_verdict() {
        // The stub FACT is harvested regardless of verdict — policy is applied later.
        String json = """
            [
              {"messageText":"Generating codet:  new opaque symbol: method 'java::java.util.List.stream:()Ljava/util/stream/Stream;'"},
              {"result":[
                {"name":"bad","status":"FAILURE","description":"d","sourceLocation":{"file":"A.java","line":"1"}}
              ]},
              {"cProverStatus":"failure"}
            ]""";
        JbmcResult r = JbmcOutputParser.parse(json, ENTRY);
        assertFalse(r.isVerified());
        assertEquals(List.of("java.util.List.stream"), r.stubbedMethods());
    }

    // --- Unwinding assertions: bound-too-small is UNKNOWN, never REFUTED -----------------------

    @Test
    void unwinding_assertion_failure_alone_is_UNKNOWN_not_refuted() {
        // --unwinding-assertions firing says the LOOP BOUND truncated exploration: incompleteness,
        // not a counterexample. Mislabeling it REFUTED let expect = REFUTED demos pass for the
        // wrong reason. Marker reachable (the proof body itself is fine within the bound).
        String json = """
            [
              {"result":[
                {"name":"u","status":"FAILURE","property":"java::pkg.Tests.proof.unwind.0",
                 "description":"unwinding assertion loop 0",
                 "sourceLocation":{"file":"T.java","line":"9","function":"java::pkg.Tests.proof:()V"}},
                {"name":"m","status":"FAILURE","description":"assertion ...",
                 "sourceLocation":{"file":"V.java","line":"%d","function":"java::pkg.Tests.proof:()V"}}
              ]},
              {"cProverStatus":"failure"}
            ]""".formatted(SENTINEL);
        JbmcResult r = JbmcOutputParser.parse(json, ENTRY);
        assertTrue(r.isUnknown(), "bound-too-small must be UNKNOWN, got " + r.verdict());
        assertFalse(r.isVacuous());
        assertTrue(r.violations().isEmpty(), "an unwinding assertion is not a counterexample");
        assertTrue(r.undecidedReason().contains("loop bound is too small"), r.undecidedReason());
    }

    @Test
    void real_failure_stays_REFUTED_even_with_an_unwinding_assertion_alongside() {
        // A trace within the bound is a REAL trace (under-approximation): a genuine user FAILURE
        // is a counterexample even if the bound ALSO truncated deeper paths. Only the real
        // violation is reported; the unwinding firing is not listed as a violation.
        String json = """
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
            ]""".formatted(SENTINEL);
        JbmcResult r = JbmcOutputParser.parse(json, ENTRY);
        assertFalse(r.isVerified());
        assertFalse(r.isUnknown(), "a real counterexample within the bound is a refutation");
        assertEquals(1, r.violations().size(), "only the real violation is reported");
        assertEquals("array index out of bounds", r.violations().get(0).description());
    }

    @Test
    void unwinding_assertion_with_unreachable_markers_is_UNKNOWN_not_vacuous() {
        // With the bound truncating exploration, unreachable markers may just be cut off — claiming
        // VACUOUS (assumptions unsatisfiable) would be wrong. Bound-too-small wins.
        String json = """
            [
              {"result":[
                {"name":"u","status":"FAILURE","property":"java::pkg.Tests.proof.unwind.1",
                 "description":"unwinding assertion loop 1",
                 "sourceLocation":{"file":"T.java","line":"9","function":"java::pkg.Tests.proof:()V"}},
                {"name":"m","status":"SUCCESS","description":"assertion ...",
                 "sourceLocation":{"file":"V.java","line":"%d","function":"java::pkg.Tests.proof:()V"}}
              ]},
              {"cProverStatus":"failure"}
            ]""".formatted(SENTINEL);
        JbmcResult r = JbmcOutputParser.parse(json, ENTRY);
        assertTrue(r.isUnknown(), "bound truncation must not be mistaken for vacuity");
        assertFalse(r.isVacuous());
        assertTrue(r.undecidedReason().contains("loop bound is too small"), r.undecidedReason());
    }
}
