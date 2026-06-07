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

    @Test
    fun temporal_fields_share_one_symbolic_now_and_get_a_pinned_overload() {
        val now = ConstraintCodeGenerator.NowParam(
                "__now_LocalDate", "java.time.LocalDate",
                "java.time.LocalDate.ofEpochDay(org.bmc4j.Bmc.anyLong())")
        val code = ConstraintCodeGenerator.generate(
                "com.acme", "UserConstraints", "com.acme.User", "obj",
                listOf(ConstraintCodeGenerator.Field("obj.birth",
                                listOf(Constraints.past("__now_LocalDate"))),
                        ConstraintCodeGenerator.Field("obj.signup",
                                listOf(Constraints.past("__now_LocalDate")))),
                listOf(now))

        // No-arg entry point introduces ONE symbolic now and delegates.
        assertTrue(code.contains(
                "java.time.LocalDate __now_LocalDate = java.time.LocalDate.ofEpochDay(org.bmc4j.Bmc.anyLong());"))
        assertTrue(code.contains("assumeValidAt(obj, __now_LocalDate);"))
        // Pinned-now overload is public and both fields reference the SAME now (shared-now semantics).
        assertTrue(code.contains("public static void assumeValidAt(com.acme.User obj, java.time.LocalDate __now_LocalDate) {"))
        assertTrue(code.contains("org.bmc4j.Bmc.assume((obj.birth == null || obj.birth.isBefore(__now_LocalDate)));"))
        assertTrue(code.contains("org.bmc4j.Bmc.assume((obj.signup == null || obj.signup.isBefore(__now_LocalDate)));"))
    }

    @Test
    fun mixed_temporal_types_keep_the_core_private_with_no_ambiguous_overload() {
        val nowDate = ConstraintCodeGenerator.NowParam(
                "__now_LocalDate", "java.time.LocalDate", "FD")
        val nowInstant = ConstraintCodeGenerator.NowParam(
                "__now_Instant", "java.time.Instant", "FI")
        val code = ConstraintCodeGenerator.generate(
                "com.acme", "EventConstraints", "com.acme.Event", "obj",
                listOf(ConstraintCodeGenerator.Field("obj.day", listOf(Constraints.past("__now_LocalDate"))),
                        ConstraintCodeGenerator.Field("obj.at", listOf(Constraints.future("__now_Instant")))),
                listOf(nowDate, nowInstant))
        // Two distinct nows declared; the core routine is PRIVATE (no single unambiguous overload).
        assertTrue(code.contains("java.time.LocalDate __now_LocalDate = FD;"))
        assertTrue(code.contains("java.time.Instant __now_Instant = FI;"))
        assertTrue(code.contains("private static void assumeValidAt(com.acme.Event obj, " +
                "java.time.LocalDate __now_LocalDate, java.time.Instant __now_Instant) {"))
        assertFalse(code.contains("public static void assumeValidAt"))
    }

    @Test
    fun statement_constraints_are_emitted_and_indented() {
        val code = ConstraintCodeGenerator.generate(
                "com.acme", "OrderConstraints", "com.acme.Order", "obj",
                listOf(ConstraintCodeGenerator.Field("obj.address", emptyList(),
                        listOf(Constraints.validCascade("com.acme.AddressConstraints")))))
        assertTrue(code.contains(
                "        if (obj.address != null) { com.acme.AddressConstraints.assumeValid(obj.address); }"))
    }
}
