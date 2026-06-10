package org.bmc4j.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

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
    fun renders_int_array_as_initializer_java_and_factory_kotlin() {
        // The parser tags an array input kind="int[]" with data rendered as "[e0, e1, …]".
        val out = ReplayRenderer.render(ENTRY, null, violation(b("a", "int[]", "[1, 0, 0, 0]")))
        assertTrue(out!!.contains("int[] a = {1, 0, 0, 0};"), out)
        val kt = ReplayRenderer.render(ENTRY, null, violation(b("a", "int[]", "[1, 0, 0, 0]")), KT)
        assertTrue(kt!!.contains("val a = intArrayOf(1, 0, 0, 0)"), kt)
    }

    @Test
    fun renders_long_array_with_L_suffix_and_long_factory() {
        val out = ReplayRenderer.render(ENTRY, null, violation(b("a", "long[]", "[7, -2]")))
        assertTrue(out!!.contains("long[] a = {7L, -2L};"), out)
        val kt = ReplayRenderer.render(ENTRY, null, violation(b("a", "long[]", "[7, -2]")), KT)
        assertTrue(kt!!.contains("val a = longArrayOf(7L, -2L)"), kt)
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

    // --- Kotlin rendering mode ------------------------------------------------

    private val KT = ReplayRenderer.Language.KOTLIN

    @Test
    fun kotlin_renders_int_as_val_no_semicolon() {
        val out = ReplayRenderer.render(ENTRY, null, violation(b("score", "integer", "100")), KT)
        assertTrue(out!!.contains("val score = 100"), out)
        // No Java framing leaked in.
        assertTrue(!out.contains("int score"), out)
        assertTrue(!out.contains(";"), out)
    }

    @Test
    fun kotlin_renders_long_char_boolean_carry_over() {
        val m = ReplayRendererTest::class.java.getDeclaredMethod("longProof", Long::class.javaPrimitiveType)
        assertTrue(ReplayRenderer.render("pkg.Example.longProof", m,
                violation(b("n", "integer", "42")), KT)!!.contains("val n = 42L"))
        assertTrue(ReplayRenderer.render(ENTRY, null, violation(b("flag", "boolean", "true")), KT)!!
                .contains("val flag = true"))
    }

    @Test
    fun kotlin_double_has_no_d_suffix_and_specials_carry_over() {
        assertTrue(ReplayRenderer.render(ENTRY, null, violation(b("x", "double", "1.5")), KT)!!
                .contains("val x = 1.5"))
        // Never a d/D suffix.
        val out = ReplayRenderer.render(ENTRY, null, violation(b("x", "double", "1.5")), KT)!!
        assertTrue(!out.contains("1.5d") && !out.contains("1.5D"), out)
        assertTrue(ReplayRenderer.render(ENTRY, null, violation(b("x", "double", "NaN")), KT)!!
                .contains("val x = Double.NaN"))
        assertTrue(ReplayRenderer.render(ENTRY, null, violation(b("x", "double", "-Inf")), KT)!!
                .contains("val x = Double.NEGATIVE_INFINITY"))
    }

    @Test
    fun kotlin_float_keeps_f_suffix_and_specials() {
        val m = ReplayRendererTest::class.java.getDeclaredMethod("floatProof", Float::class.javaPrimitiveType)
        assertTrue(ReplayRenderer.render("pkg.Example.floatProof", m,
                violation(b("f", "float", "2.5")), KT)!!.contains("val f = 2.5f"))
        assertTrue(ReplayRenderer.render("pkg.Example.floatProof", m,
                violation(b("f", "float", "+Inf")), KT)!!.contains("val f = Float.POSITIVE_INFINITY"))
    }

    @Test
    fun kotlin_short_and_byte_get_explicit_types_not_casts() {
        val ms = ReplayRendererTest::class.java.getDeclaredMethod("shortProof", Short::class.javaPrimitiveType)
        val outS = ReplayRenderer.render("pkg.Example.shortProof", ms,
                violation(b("s", "integer", "3")), KT)!!
        assertTrue(outS.contains("val s: Short = 3"), outS)
        assertTrue(!outS.contains("(short)"), outS)
        val mb = ReplayRendererTest::class.java.getDeclaredMethod("byteProof", Byte::class.javaPrimitiveType)
        val outB = ReplayRenderer.render("pkg.Example.byteProof", mb,
                violation(b("by", "integer", "7")), KT)!!
        assertTrue(outB.contains("val by: Byte = 7"), outB)
        assertTrue(!outB.contains("(byte)"), outB)
    }

    @Test
    fun kotlin_string_escapes_dollar_for_template_interpolation() {
        // $ must be escaped (\$) so Kotlin doesn't read it as a template; other escapes carry over.
        assertTrue(ReplayRenderer.kotlinStringLiteral("a\$b") == "\"a\\\$b\"",
                ReplayRenderer.kotlinStringLiteral("a\$b"))
        assertTrue(ReplayRenderer.kotlinStringLiteral("\${x}") == "\"\\\${x}\"",
                ReplayRenderer.kotlinStringLiteral("\${x}"))
        // Java escaping does NOT escape $ (byte-identical Java path is preserved).
        assertTrue(ReplayRenderer.javaStringLiteral("a\$b") == "\"a\$b\"",
                ReplayRenderer.javaStringLiteral("a\$b"))
        // Quotes / backslashes / control chars / \uXXXX still escaped in Kotlin.
        assertTrue(ReplayRenderer.kotlinStringLiteral("a\"b") == "\"a\\\"b\"")
        assertTrue(ReplayRenderer.kotlinStringLiteral("a\nb") == "\"a\\nb\"")
        assertTrue(ReplayRenderer.kotlinStringLiteral("é") == "\"\\u00e9\"")
    }

    @Test
    fun kotlin_string_binding_escapes_dollar() {
        val out = ReplayRenderer.render(ENTRY, null,
                violation(b("region", "string", "price=\$5")), KT)
        assertTrue(out!!.contains("val region = \"price=\\\$5\""), out)
    }

    @Test
    fun kotlin_char_literal_carries_over() {
        val m = ReplayRendererTest::class.java.getDeclaredMethod("charProof", Char::class.javaPrimitiveType)
        val out = ReplayRenderer.render("pkg.Example.charProof", m,
                violation(b("c", "integer", "65")), KT)!!
        assertTrue(out.contains("val c = 'A'"), out)
    }

    @Test
    fun kotlin_enum_renders_as_constant() {
        val m = ReplayRendererTest::class.java.getDeclaredMethod("enumProof", Suit::class.java)
        val out = ReplayRenderer.render("pkg.Example.enumProof", m,
                violation(b("suit", "integer", "2")), KT)!!
        assertTrue(out.contains("val suit = Suit.HEARTS"), out)
    }

    @Test
    fun kotlin_degraded_binding_stays_a_comment() {
        val out = ReplayRenderer.render(ENTRY, null, violation(b("obj", "pointer", "0x1")), KT)!!
        assertTrue(out.contains("// obj:"), out)
        assertTrue(out.contains("object/reference value"), out)
        assertTrue(!out.contains("val obj"), out)
    }

    @Test
    fun kotlin_backtick_needing_binding_name_is_quoted() {
        // A reserved word as a binding name must be backtick-quoted to be a legal val.
        val out = ReplayRenderer.render(ENTRY, null, violation(b("object", "integer", "1")), KT)!!
        assertTrue(out.contains("val `object` = 1"), out)
    }

    // --- Java path is byte-identical to before (no regression) ----------------

    @Test
    fun java_default_block_is_unchanged_byte_for_byte() {
        val out = ReplayRenderer.render(ENTRY, null,
                violation(b("score", "integer", "100"), b("flag", "boolean", "true")))
        val expected = "    replay:\n" +
                "      int score = 100;\n" +
                "      boolean flag = true;\n" +
                "      // then run the body of Example.proof with these value(s)"
        assertEquals(expected, out)
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

        @JvmStatic
        private fun floatProof(f: Float) {
        }

        @JvmStatic
        private fun shortProof(s: Short) {
        }

        @JvmStatic
        private fun byteProof(by: Byte) {
        }

        @JvmStatic
        private fun charProof(c: Char) {
        }
    }
}
