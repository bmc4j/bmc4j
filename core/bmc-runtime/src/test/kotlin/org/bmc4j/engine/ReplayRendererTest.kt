package org.bmc4j.engine

import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.Method

/**
 * Unit tests for [ReplayRenderer] — the bulk of the feature's validation. These
 * pin every rendering branch with hand-built bindings: a regression here silently emits wrong or
 * non-compiling "replay" code, which the feature exists to avoid.
 */
internal class ReplayRendererTest {

    // --- primitives -----------------------------------------------------------

    @Test
    fun renders_int_primitive_as_typed_local() {
        val out = ReplayRenderer.render(ENTRY, null, violation(b("score", "integer", "100")))
        assertTrue(out!!.contains("replay:"), out)
        assertTrue(out.contains("int score = 100;"), out)
    }

    @Test
    fun renders_boolean_from_truefalse_and_from_01() {
        assertTrue(ReplayRenderer.render(ENTRY, null, violation(b("flag", "boolean", "true")))!!
                .contains("boolean flag = true;"))
        assertTrue(ReplayRenderer.render(ENTRY, null, violation(b("flag", "boolean", "0")))!!
                .contains("boolean flag = false;"))
    }

    @Test
    fun renders_double_including_nan_and_infinity() {
        assertTrue(ReplayRenderer.render(ENTRY, null, violation(b("x", "double", "1.5")))!!
                .contains("double x = 1.5;"))
        assertTrue(ReplayRenderer.render(ENTRY, null, violation(b("x", "double", "NaN")))!!
                .contains("double x = Double.NaN;"))
        assertTrue(ReplayRenderer.render(ENTRY, null, violation(b("x", "double", "-Inf")))!!
                .contains("double x = Double.NEGATIVE_INFINITY;"))
    }

    @Test
    fun renders_float_with_suffix_and_specials() {
        assertTrue(ReplayRenderer.floatLiteral("2.5") == "2.5f")
        assertTrue(ReplayRenderer.floatLiteral("NaN") == "Float.NaN")
        assertTrue(ReplayRenderer.floatLiteral("+Inf") == "Float.POSITIVE_INFINITY")
    }

    // --- strings & escaping ---------------------------------------------------

    @Test
    fun string_literal_escapes_quotes_backslashes_and_control_chars() {
        assertTrue(ReplayRenderer.javaStringLiteral("a\"b") == "\"a\\\"b\"",
                ReplayRenderer.javaStringLiteral("a\"b"))
        assertTrue(ReplayRenderer.javaStringLiteral("a\\b") == "\"a\\\\b\"")
        assertTrue(ReplayRenderer.javaStringLiteral("a\nb") == "\"a\\nb\"")
        assertTrue(ReplayRenderer.javaStringLiteral("\t") == "\"\\t\"")
        // Non-printable -> \\uXXXX
        assertTrue(ReplayRenderer.javaStringLiteral("") == "\"\\u0001\"",
                ReplayRenderer.javaStringLiteral(""))
        assertTrue(ReplayRenderer.javaStringLiteral("é") == "\"\\u00e9\"")
    }

    @Test
    fun char_literal_escapes_quote_and_control() {
        assertTrue(ReplayRenderer.charLiteral('\'') == "'\\''", ReplayRenderer.charLiteral('\''))
        assertTrue(ReplayRenderer.charLiteral('\n') == "'\\n'")
        assertTrue(ReplayRenderer.charLiteral('A') == "'A'")
    }

    @Test
    fun renders_string_kind_binding_as_escaped_literal() {
        val out = ReplayRenderer.render(ENTRY, null,
                violation(b("region", "string", "e\"u")))
        assertTrue(out!!.contains("String region = \"e\\\"u\";"), out)
    }

    // --- enums / anyOf --------------------------------------------------------

    enum class Suit { CLUBS, DIAMONDS, HEARTS, SPADES }

    @Test
    fun renders_enum_param_as_constant_not_index() {
        val m = ReplayRendererTest::class.java.getDeclaredMethod("enumProof", Suit::class.java)
        // Index 2 -> HEARTS. Requires -parameters so the name "suit" is present (see build.gradle).
        val out = ReplayRenderer.render("pkg.Example.enumProof", m,
                violation(b("suit", "integer", "2")))
        assertTrue(out!!.contains("Suit suit = Suit.HEARTS;"), out)
    }

    @Test
    fun enum_index_out_of_range_degrades_to_comment() {
        val m = ReplayRendererTest::class.java.getDeclaredMethod("enumProof", Suit::class.java)
        val out = ReplayRenderer.render("pkg.Example.enumProof", m,
                violation(b("suit", "integer", "99")))
        assertTrue(out!!.contains("// suit:"), out)
        assertTrue(out.contains("could not express"), out)
    }

    // --- declared parameter types refine primitive rendering ------------------

    @Test
    fun long_param_renders_with_L_suffix() {
        val m = ReplayRendererTest::class.java.getDeclaredMethod("longProof", Long::class.javaPrimitiveType)
        val out = ReplayRenderer.render("pkg.Example.longProof", m,
                violation(b("n", "integer", "42")))
        assertTrue(out!!.contains("long n = 42L;"), out)
    }

    // --- degrade path ---------------------------------------------------------

    @Test
    fun pointer_object_value_degrades_to_clearly_commented_description() {
        val out = ReplayRenderer.render(ENTRY, null, violation(b("obj", "pointer", "0x1")))
        assertTrue(out!!.contains("// obj:"), out)
        assertTrue(out.contains("object/reference value"), out)
        // Never emits a bare uncompilable assignment for it.
        assertTrue(!out.contains("obj ="), out)
    }

    @Test
    fun no_bindings_yields_no_block() {
        assertNull(ReplayRenderer.render(ENTRY, null, violation()))
        assertNull(ReplayRenderer.render(ENTRY, null, null))
    }

    @Test
    fun mixed_bindings_render_each_on_its_own_line() {
        val out = ReplayRenderer.render(ENTRY, null,
                violation(b("a", "integer", "1"), b("b", "boolean", "true")))
        assertTrue(out!!.contains("int a = 1;"), out)
        assertTrue(out.contains("boolean b = true;"), out)
    }

    companion object {
        private const val ENTRY = "pkg.Example.proof"

        private fun violation(vararg bindings: JbmcResult.Binding): JbmcResult.Violation {
            return JbmcResult.Violation("d", "Example.java", 1, listOf(), listOf(),
                    listOf(*bindings))
        }

        private fun b(name: String, kind: String, data: String): JbmcResult.Binding {
            return JbmcResult.Binding(name, kind, data)
        }

        @JvmStatic
        private fun enumProof(suit: Suit) {
        }

        @JvmStatic
        private fun longProof(n: Long) {
        }
    }
}
