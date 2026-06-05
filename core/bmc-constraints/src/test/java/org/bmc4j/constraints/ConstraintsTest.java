package org.bmc4j.constraints;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ConstraintsTest {

    @Test
    void notNull_renders_inequality() {
        assertEquals("obj.age != null", Constraints.notNull().toExpression("obj.age"));
    }

    @Test
    void min_and_max_render_bounds() {
        assertEquals("x >= 5", Constraints.min(5).toExpression("x"));
        assertEquals("x <= 10", Constraints.max(10).toExpression("x"));
        assertEquals("x >= -3", Constraints.min(-3).toExpression("x"));
    }

    @Test
    void size_bounds_permit_null_and_use_the_size_accessor() {
        assertEquals("(s == null || s.length() >= 3)",
                Constraints.sizeAtLeast(".length()", 3).toExpression("s"));
        assertEquals("(arr == null || arr.length <= 8)",
                Constraints.sizeAtMost(".length", 8).toExpression("arr"));
        assertEquals("(c == null || c.size() >= 1)",
                Constraints.sizeAtLeast(".size()", 1).toExpression("c"));
    }
}
