package org.bmc4j.constraints

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ConstraintsTest {

    @Test
    fun notNull_renders_inequality() {
        assertEquals("obj.age != null", Constraints.notNull().toExpression("obj.age"))
    }

    @Test
    fun min_and_max_render_bounds() {
        assertEquals("x >= 5", Constraints.min(5).toExpression("x"))
        assertEquals("x <= 10", Constraints.max(10).toExpression("x"))
        assertEquals("x >= -3", Constraints.min(-3).toExpression("x"))
    }

    @Test
    fun size_bounds_permit_null_and_use_the_size_accessor() {
        assertEquals("(s == null || s.length() >= 3)",
                Constraints.sizeAtLeast(".length()", 3).toExpression("s"))
        assertEquals("(arr == null || arr.length <= 8)",
                Constraints.sizeAtMost(".length", 8).toExpression("arr"))
        assertEquals("(c == null || c.size() >= 1)",
                Constraints.sizeAtLeast(".size()", 1).toExpression("c"))
    }

    @Test
    fun nullable_numeric_bounds_permit_null_per_jakarta_semantics() {
        // Boxed numerics: null PASSES every constraint except @NotNull. An unguarded compare
        // would NPE in the assume or silently EXCLUDE valid-null objects from the proof domain.
        assertEquals("(obj.points == null || obj.points >= 0)",
                Constraints.minNullable(0).toExpression("obj.points"))
        assertEquals("(obj.points == null || obj.points <= 99)",
                Constraints.maxNullable(99).toExpression("obj.points"))
    }

    @Test
    fun null_and_boolean_constraints_render() {
        assertEquals("obj.legacy == null", Constraints.isNull().toExpression("obj.legacy"))
        assertEquals("obj.active", Constraints.isTrue().toExpression("obj.active"))
        assertEquals("!obj.banned", Constraints.isFalse().toExpression("obj.banned"))
        assertEquals("(obj.opt == null || obj.opt)",
                Constraints.isTrueNullable().toExpression("obj.opt"))
        assertEquals("(obj.opt == null || !obj.opt)",
                Constraints.isFalseNullable().toExpression("obj.opt"))
    }

    @Test
    fun temporal_constraints_compare_against_the_shared_now_and_pass_null() {
        // All four reference the SAME `now` var; null passes (only @NotNull rejects null).
        assertEquals("(obj.d == null || obj.d.isBefore(__now))",
                Constraints.past("__now").toExpression("obj.d"))
        assertEquals("(obj.d == null || !obj.d.isAfter(__now))",
                Constraints.pastOrPresent("__now").toExpression("obj.d"))
        assertEquals("(obj.d == null || obj.d.isAfter(__now))",
                Constraints.future("__now").toExpression("obj.d"))
        assertEquals("(obj.d == null || !obj.d.isBefore(__now))",
                Constraints.futureOrPresent("__now").toExpression("obj.d"))
    }

    @Test
    fun decimal_bounds_honor_the_inclusive_flag_and_pass_null() {
        assertEquals("(p == null || p.compareTo(new java.math.BigDecimal(\"0.01\")) >= 0)",
                Constraints.decimalMin("0.01", true).toExpression("p"))
        assertEquals("(p == null || p.compareTo(new java.math.BigDecimal(\"0.01\")) > 0)",
                Constraints.decimalMin("0.01", false).toExpression("p"))
        assertEquals("(p == null || p.compareTo(new java.math.BigDecimal(\"100\")) <= 0)",
                Constraints.decimalMax("100", true).toExpression("p"))
        assertEquals("(p == null || p.compareTo(new java.math.BigDecimal(\"100\")) < 0)",
                Constraints.decimalMax("100", false).toExpression("p"))
    }

    @Test
    fun digits_bounds_both_the_scale_and_the_integer_magnitude() {
        // integer=5, fraction=2 -> scale<=2 AND |truncated integer part| < 10^5.
        assertEquals("(p == null || (p.scale() <= 2 && " +
                "p.toBigInteger().abs().compareTo(java.math.BigInteger.valueOf(100000L)) < 0))",
                Constraints.digits(5, 2).toExpression("p"))
        // fraction-only when integer is 0.
        assertEquals("(p == null || (p.scale() <= 3))",
                Constraints.digits(0, 3).toExpression("p"))
    }

    @Test
    fun notBlank_rejects_null_and_finds_a_non_whitespace_char() {
        // No null-guard: a null prefix makes the whole expression false (jakarta @NotBlank rejects null).
        assertEquals("(s != null && !s.trim().isEmpty())",
                Constraints.notBlankTrim().toExpression("s"))
        val loop = Constraints.notBlankCharAtLoop(2).toExpression("s")
        assertEquals("(s != null && ((0 < s.length() && s.charAt(0) > ' ') || " +
                "(1 < s.length() && s.charAt(1) > ' ')))", loop)
    }

    @Test
    fun valid_cascade_is_a_null_guarded_recursive_call() {
        assertEquals("if (obj.addr != null) { com.acme.AddressConstraints.assumeValid(obj.addr); }",
                Constraints.validCascade("com.acme.AddressConstraints").toStatements("obj.addr"))
    }

    @Test
    fun container_element_loop_is_bounded_and_null_guarded() {
        val stmt = Constraints.containerElements(".size()", ".get", 3, listOf(Constraints.minNullable(1)))
                .toStatements("obj.scores")
        assertEquals(
                "if (obj.scores != null) {\n" +
                "    for (int __i = 0; __i < 3 && __i < obj.scores.size(); __i++) {\n" +
                "        var __e = obj.scores.get(__i);\n" +
                "        org.bmc4j.Bmc.assume((__e == null || __e >= 1));\n" +
                "    }\n" +
                "}", stmt)
    }

    @Test
    fun container_valid_cascade_loop_recurses_into_each_non_null_element() {
        val stmt = Constraints.containerValidCascade(".size()", ".get", 4, "com.acme.LineConstraints")
                .toStatements("obj.lines")
        assertEquals(
                "if (obj.lines != null) {\n" +
                "    for (int __i = 0; __i < 4 && __i < obj.lines.size(); __i++) {\n" +
                "        var __e = obj.lines.get(__i);\n" +
                "        if (__e != null) { com.acme.LineConstraints.assumeValid(__e); }\n" +
                "    }\n" +
                "}", stmt)
    }
}
