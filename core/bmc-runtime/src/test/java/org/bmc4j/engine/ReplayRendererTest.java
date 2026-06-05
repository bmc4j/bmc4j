package org.bmc4j.engine;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ReplayRenderer} — the bulk of the feature's validation. These
 * pin every rendering branch with hand-built bindings: a regression here silently emits wrong or
 * non-compiling "replay" code, which the feature exists to avoid.
 */
class ReplayRendererTest {

    private static final String ENTRY = "pkg.Example.proof";

    private static JbmcResult.Violation violation(JbmcResult.Binding... bindings) {
        return new JbmcResult.Violation("d", "Example.java", 1, List.of(), List.of(),
                List.of(bindings));
    }

    private static JbmcResult.Binding b(String name, String kind, String data) {
        return new JbmcResult.Binding(name, kind, data);
    }

    // --- primitives -----------------------------------------------------------

    @Test
    void renders_int_primitive_as_typed_local() {
        String out = ReplayRenderer.render(ENTRY, null, violation(b("score", "integer", "100")));
        assertTrue(out.contains("replay:"), out);
        assertTrue(out.contains("int score = 100;"), out);
    }

    @Test
    void renders_boolean_from_truefalse_and_from_01() {
        assertTrue(ReplayRenderer.render(ENTRY, null, violation(b("flag", "boolean", "true")))
                .contains("boolean flag = true;"));
        assertTrue(ReplayRenderer.render(ENTRY, null, violation(b("flag", "boolean", "0")))
                .contains("boolean flag = false;"));
    }

    @Test
    void renders_double_including_nan_and_infinity() {
        assertTrue(ReplayRenderer.render(ENTRY, null, violation(b("x", "double", "1.5")))
                .contains("double x = 1.5;"));
        assertTrue(ReplayRenderer.render(ENTRY, null, violation(b("x", "double", "NaN")))
                .contains("double x = Double.NaN;"));
        assertTrue(ReplayRenderer.render(ENTRY, null, violation(b("x", "double", "-Inf")))
                .contains("double x = Double.NEGATIVE_INFINITY;"));
    }

    @Test
    void renders_float_with_suffix_and_specials() {
        assertTrue(ReplayRenderer.floatLiteral("2.5").equals("2.5f"));
        assertTrue(ReplayRenderer.floatLiteral("NaN").equals("Float.NaN"));
        assertTrue(ReplayRenderer.floatLiteral("+Inf").equals("Float.POSITIVE_INFINITY"));
    }

    // --- strings & escaping ---------------------------------------------------

    @Test
    void string_literal_escapes_quotes_backslashes_and_control_chars() {
        assertTrue(ReplayRenderer.javaStringLiteral("a\"b").equals("\"a\\\"b\""),
                ReplayRenderer.javaStringLiteral("a\"b"));
        assertTrue(ReplayRenderer.javaStringLiteral("a\\b").equals("\"a\\\\b\""));
        assertTrue(ReplayRenderer.javaStringLiteral("a\nb").equals("\"a\\nb\""));
        assertTrue(ReplayRenderer.javaStringLiteral("\t").equals("\"\\t\""));
        // Non-printable -> \\uXXXX
        assertTrue(ReplayRenderer.javaStringLiteral("").equals("\"\\u0001\""),
                ReplayRenderer.javaStringLiteral(""));
        assertTrue(ReplayRenderer.javaStringLiteral("é").equals("\"\\u00e9\""));
    }

    @Test
    void char_literal_escapes_quote_and_control() {
        assertTrue(ReplayRenderer.charLiteral('\'').equals("'\\''"), ReplayRenderer.charLiteral('\''));
        assertTrue(ReplayRenderer.charLiteral('\n').equals("'\\n'"));
        assertTrue(ReplayRenderer.charLiteral('A').equals("'A'"));
    }

    @Test
    void renders_string_kind_binding_as_escaped_literal() {
        String out = ReplayRenderer.render(ENTRY, null,
                violation(b("region", "string", "e\"u")));
        assertTrue(out.contains("String region = \"e\\\"u\";"), out);
    }

    // --- enums / anyOf --------------------------------------------------------

    enum Suit { CLUBS, DIAMONDS, HEARTS, SPADES }

    static void enumProof(Suit suit) {
    }

    @Test
    void renders_enum_param_as_constant_not_index() throws NoSuchMethodException {
        Method m = ReplayRendererTest.class.getDeclaredMethod("enumProof", Suit.class);
        // Index 2 -> HEARTS. Requires -parameters so the name "suit" is present (see build.gradle).
        String out = ReplayRenderer.render("pkg.Example.enumProof", m,
                violation(b("suit", "integer", "2")));
        assertTrue(out.contains("Suit suit = Suit.HEARTS;"), out);
    }

    @Test
    void enum_index_out_of_range_degrades_to_comment() throws NoSuchMethodException {
        Method m = ReplayRendererTest.class.getDeclaredMethod("enumProof", Suit.class);
        String out = ReplayRenderer.render("pkg.Example.enumProof", m,
                violation(b("suit", "integer", "99")));
        assertTrue(out.contains("// suit:"), out);
        assertTrue(out.contains("could not express"), out);
    }

    // --- declared parameter types refine primitive rendering ------------------

    static void longProof(long n) {
    }

    @Test
    void long_param_renders_with_L_suffix() throws NoSuchMethodException {
        Method m = ReplayRendererTest.class.getDeclaredMethod("longProof", long.class);
        String out = ReplayRenderer.render("pkg.Example.longProof", m,
                violation(b("n", "integer", "42")));
        assertTrue(out.contains("long n = 42L;"), out);
    }

    // --- degrade path ---------------------------------------------------------

    @Test
    void pointer_object_value_degrades_to_clearly_commented_description() {
        String out = ReplayRenderer.render(ENTRY, null, violation(b("obj", "pointer", "0x1")));
        assertTrue(out.contains("// obj:"), out);
        assertTrue(out.contains("object/reference value"), out);
        // Never emits a bare uncompilable assignment for it.
        assertTrue(!out.contains("obj ="), out);
    }

    @Test
    void no_bindings_yields_no_block() {
        assertNull(ReplayRenderer.render(ENTRY, null, violation()));
        assertNull(ReplayRenderer.render(ENTRY, null, null));
    }

    @Test
    void mixed_bindings_render_each_on_its_own_line() {
        String out = ReplayRenderer.render(ENTRY, null,
                violation(b("a", "integer", "1"), b("b", "boolean", "true")));
        assertTrue(out.contains("int a = 1;"), out);
        assertTrue(out.contains("boolean b = true;"), out);
    }
}
