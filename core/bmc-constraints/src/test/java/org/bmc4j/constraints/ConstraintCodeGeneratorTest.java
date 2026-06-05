package org.bmc4j.constraints;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ConstraintCodeGeneratorTest {

    @Test
    void generates_assumeValid_with_a_null_guard_then_each_constraint() {
        String code = ConstraintCodeGenerator.generate(
                "com.acme", "UserConstraints", "com.acme.User", "obj",
                List.of(new ConstraintCodeGenerator.Field("obj.age",
                            List.of(Constraints.min(0), Constraints.max(150))),
                        new ConstraintCodeGenerator.Field("obj.name",
                            List.of(Constraints.notNull()))));

        assertTrue(code.startsWith("package com.acme;\n"));
        assertTrue(code.contains("public final class UserConstraints {"));
        assertTrue(code.contains("private UserConstraints() {}"));
        assertTrue(code.contains("public static void assumeValid(com.acme.User obj) {"));
        // Null guard precedes field reads.
        int guard = code.indexOf("Bmc.assume(obj != null);");
        int firstField = code.indexOf("Bmc.assume(obj.age >= 0);");
        assertTrue(guard > 0 && firstField > guard, "null guard must come before field constraints");
        assertTrue(code.contains("org.bmc4j.Bmc.assume(obj.age <= 150);"));
        assertTrue(code.contains("org.bmc4j.Bmc.assume(obj.name != null);"));
    }

    @Test
    void omits_package_line_when_package_is_empty() {
        String code = ConstraintCodeGenerator.generate(
                "", "CConstraints", "C", "v", List.of());
        assertFalse(code.contains("package "));
        assertTrue(code.contains("public final class CConstraints {"));
        // No fields -> still emits the null guard only.
        assertTrue(code.contains("org.bmc4j.Bmc.assume(v != null);"));
    }

    @Test
    void null_package_is_treated_as_no_package() {
        String code = ConstraintCodeGenerator.generate(
                null, "CConstraints", "C", "v", List.of());
        assertFalse(code.contains("package "));
    }
}
