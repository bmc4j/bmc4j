package org.bmc4j.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files

/**
 * Pins the STREAMING [JbmcOutputParser.parse] (File overload) — the production output path that reads
 * jbmc's `--json-ui` stdout straight from the spill file without buffering it whole in heap. The bar:
 * it must extract EVERYTHING the in-heap String parser does (verdict, counterexample/witness, nondet
 * stub footnotes, unmodelled members, unwinding info) while discarding the STATUS-MESSAGE flood, even
 * when that flood is far larger than heap-friendly.
 */
internal class JbmcStreamingParseTest {

    @Test
    fun streaming_parse_of_a_refutation_preserves_verdict_witness_and_stubs() {
        // A refuting result element (with a counterexample binding) PLUS one opaque-symbol stub message,
        // surrounded by a LARGE flood of irrelevant STATUS-MESSAGEs the streamer must read-and-drop.
        val result = """
            {"result":[
              {"name":"f.1","status":"FAILURE","description":"assertion",
               "sourceLocation":{"file":"Example.java","line":"12","function":"java::pkg.Tests.proof:()V"},
               "trace":[
                 {"stepType":"function-call","function":{"identifier":"java::pkg.Tests.proof:()V"},
                  "sourceLocation":{"file":"Tests.java","line":"5"}},
                 {"stepType":"assignment","lhs":"score",
                  "sourceLocation":{"function":"java::pkg.Tests.proof:()V"},
                  "value":{"name":"integer","data":"100"}},
                 {"stepType":"failure",
                  "sourceLocation":{"function":"java::pkg.Tests.proof:()V","file":"Example.java","line":"12"}}
               ]}
            ]}""".trimIndent()
        val stub = "{\"messageType\":\"STATUS-MESSAGE\",\"messageText\":\"Generating codet:  new " +
                "opaque symbol: method 'java::java.util.List.stream:()Ljava/util/stream/Stream;'\"}"

        val file = floodFile(result, stub, floodBytesAtLeast = 4 shl 20) // >=4 MiB of flood
        try {
            val r = JbmcOutputParser.parse(file, "pkg.Tests.proof")
            // Verdict preserved: a real refutation with its violation.
            assertFalse(r.isVerified)
            assertEquals(1, r.violations.size)
            // Witness/counterexample input preserved — the load-bearing correctness check.
            assertEquals(listOf("score = 100"), r.violations[0].counterexample)
            assertEquals("100", r.violations[0].bindings.single { it.name == "score" }.data)
            // Nondet-stub footnote preserved (harvested from the opaque-symbol message in the flood).
            assertEquals(listOf("java.util.List.stream"), r.stubbedMethods)
            // rawOutput is the BOUNDED summary, never the whole (multi-MB) stream.
            assertTrue((r.rawOutput?.length ?: 0) < 4096,
                    "rawOutput must be a bounded summary, was ${r.rawOutput?.length} chars")
        } finally {
            file.delete()
        }
    }

    @Test
    fun streaming_parse_matches_the_in_heap_parser_on_a_verified_run() {
        // The same bytes through both entry points yield the same verdict + facts (verdict-neutral).
        val json = """
            [
              {"messageType":"STATUS-MESSAGE","messageText":"starting"},
              {"messageText":"Generating codet:  new opaque symbol: method 'java::java.util.Formatter.format:(Ljava/lang/String;[Ljava/lang/Object;)Ljava/util/Formatter;'"},
              {"result":[
                {"name":"m","status":"FAILURE","description":"assertion ...",
                 "sourceLocation":{"file":"V.java","line":"%d","function":"java::pkg.Tests.proof:()V"}}
              ]},
              {"cProverStatus":"failure"}
            ]""".trimIndent().format(BmcReachability.SENTINEL_LINE)
        val file = Files.createTempFile("bmc4j-stream", ".json").toFile()
        try {
            file.writeText(json, StandardCharsets.UTF_8)
            val streamed = JbmcOutputParser.parse(file, "pkg.Tests.proof")
            val inHeap = JbmcOutputParser.parse(json, "pkg.Tests.proof")
            assertEquals(inHeap.verdict, streamed.verdict)
            assertTrue(streamed.isVerified, "marker reachable, no user failure -> VERIFIED")
            assertEquals(inHeap.stubbedMethods, streamed.stubbedMethods)
            assertEquals(listOf("java.util.Formatter.format"), streamed.stubbedMethods)
        } finally {
            file.delete()
        }
    }

    @Test
    fun streaming_parse_of_garbage_is_PARSE_FAILURE_not_a_crash() {
        val file = Files.createTempFile("bmc4j-stream", ".txt").toFile()
        try {
            file.writeText("this is not the json-ui array {{{", StandardCharsets.UTF_8)
            val r = JbmcOutputParser.parse(file, "pkg.Tests.proof")
            assertTrue(r.isUnknown)
            assertEquals(UnknownKind.PARSE_FAILURE, r.undecidedKind)
            assertTrue(r.undecidedReason!!.contains("could not parse"))
        } finally {
            file.delete()
        }
    }

    @Test
    fun streaming_parse_of_empty_output_is_PARSE_FAILURE() {
        val file = Files.createTempFile("bmc4j-stream", ".txt").toFile()
        try {
            // empty file: no array at all -> undecided, never a silent pass.
            val r = JbmcOutputParser.parse(file, "pkg.Tests.proof")
            assertTrue(r.isUnknown)
            assertEquals(UnknownKind.PARSE_FAILURE, r.undecidedKind)
        } finally {
            file.delete()
        }
    }

    private companion object {
        /** Write a `--json-ui` array to a temp file: [stub] message, a large flood of irrelevant
         *  STATUS-MESSAGEs (>= [floodBytesAtLeast] bytes), then the [result] element last. */
        fun floodFile(result: String, stub: String, floodBytesAtLeast: Int): File {
            val file = Files.createTempFile("bmc4j-flood", ".json").toFile()
            file.outputStream().bufferedWriter(StandardCharsets.UTF_8).use { w ->
                w.append("[\n")
                w.append(stub).append(",\n")
                var written = 0
                val noise = "{\"messageType\":\"STATUS-MESSAGE\",\"messageText\":\"" +
                        "Symex assignment ".repeat(40) + "\"},\n"
                while (written < floodBytesAtLeast) {
                    w.append(noise)
                    written += noise.length
                }
                w.append(result).append("\n]")
            }
            return file
        }
    }
}
