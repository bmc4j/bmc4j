package org.bmc4j.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Unit tests for [StubPolicy] + [StubFilter] — the read-time stub policy. */
internal class StubPolicyTest {

    @Test
    fun unacknowledged_when_no_allowlist() {
        val p = StubPolicy.judge(listOf("java.util.Formatter.format"), listOf(), "")
        assertTrue(p.hasUnacknowledged())
        assertEquals(listOf("java.util.Formatter.format"), p.unacknowledged)
        assertFalse(p.hasUserOwned())
    }

    @Test
    fun exact_allow_pattern_acknowledges() {
        val p = StubPolicy.judge(listOf("java.util.Formatter.format"),
                listOf("java.util.Formatter.format"), "")
        assertFalse(p.hasUnacknowledged())
    }

    @Test
    fun class_wildcard_acknowledges_all_methods_of_the_class() {
        val p = StubPolicy.judge(
                listOf("java.util.Formatter.format", "java.util.Formatter.close"),
                listOf("java.util.Formatter.*"), "")
        assertFalse(p.hasUnacknowledged())
    }

    @Test
    fun package_wildcard_acknowledges_subtree_but_not_a_sibling_package() {
        val p = StubPolicy.judge(
                listOf("java.util.Formatter.format", "java.time.LocalDate.now"),
                listOf("java.util.*"), "")
        assertEquals(listOf("java.time.LocalDate.now"), p.unacknowledged)
    }

    @Test
    fun user_package_stub_is_flagged_loud_even_unacknowledged() {
        val p = StubPolicy.judge(
                listOf("com.acme.svc.Client.call", "java.util.Formatter.format"),
                listOf(), "com.acme")
        assertTrue(p.hasUserOwned())
        assertEquals(listOf("com.acme.svc.Client.call"), p.userOwned)
        // both still count as unacknowledged
        assertEquals(2, p.unacknowledged.size)
    }

    @Test
    fun user_package_match_is_prefix_bounded_not_substring() {
        // "com.acme" must not match "com.acmecorp.*" (the dotted-boundary rule).
        assertFalse(StubFilter.isUserOwned("com.acmecorp.X.m", "com.acme"))
        assertTrue(StubFilter.isUserOwned("com.acme.X.m", "com.acme"))
    }

    @Test
    fun filter_drops_models_synthetics_and_cprover() {
        assertFalse(StubFilter.isSignal("org.bmc4j.engine.Bmc.anyInt"))
        assertFalse(StubFilter.isSignal("org.cprover.CProver.nondetInt"))
        assertFalse(StubFilter.isSignal("java.lang.Object.<init>"))
        assertFalse(StubFilter.isSignal("java.lang.Integer.valueOf"))
        assertFalse(StubFilter.isSignal("java.lang.AssertionError.<init>"))
        assertTrue(StubFilter.isSignal("java.util.Formatter.format"))
    }
}
