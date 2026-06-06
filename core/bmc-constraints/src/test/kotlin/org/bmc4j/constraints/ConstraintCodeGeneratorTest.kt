package org.bmc4j.constraints

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConstraintCodeGeneratorTest {

    @Test
    fun generates_assumeValid_with_a_null_guard_then_each_constraint() {
        val code = ConstraintCodeGenerator.generate(
                "com.acme", "UserConstraints", "com.acme.User", "obj",
                listOf(ConstraintCodeGenerator.Field("obj.age",
                                listOf(Constraints.min(0), Constraints.max(150))),
                        ConstraintCodeGenerator.Field("obj.name",
                                listOf(Constraints.notNull()))))

        assertTrue(code.startsWith("package com.acme;\n"))
        assertTrue(code.contains("public final class UserConstraints {"))
        assertTrue(code.contains("private UserConstraints() {}"))
        assertTrue(code.contains("public static void assumeValid(com.acme.User obj) {"))
        // Null guard precedes field reads.
        val guard = code.indexOf("Bmc.assume(obj != null);")
        val firstField = code.indexOf("Bmc.assume(obj.age >= 0);")
        assertTrue(guard > 0 && firstField > guard, "null guard must come before field constraints")
        assertTrue(code.contains("org.bmc4j.Bmc.assume(obj.age <= 150);"))
        assertTrue(code.contains("org.bmc4j.Bmc.assume(obj.name != null);"))
    }

    @Test
    fun omits_package_line_when_package_is_empty() {
        val code = ConstraintCodeGenerator.generate(
                "", "CConstraints", "C", "v", emptyList())
        assertFalse(code.contains("package "))
        assertTrue(code.contains("public final class CConstraints {"))
        // No fields -> still emits the null guard only.
        assertTrue(code.contains("org.bmc4j.Bmc.assume(v != null);"))
    }

    @Test
    fun null_package_is_treated_as_no_package() {
        val code = ConstraintCodeGenerator.generate(
                null, "CConstraints", "C", "v", emptyList())
        assertFalse(code.contains("package "))
    }
}
