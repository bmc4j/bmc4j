package org.bmc4j.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link StubPolicy} + {@link StubFilter} — the read-time stub policy. */
class StubPolicyTest {

    @Test
    void unacknowledged_when_no_allowlist() {
        StubPolicy p = StubPolicy.judge(List.of("java.util.Formatter.format"), List.of(), "");
        assertTrue(p.hasUnacknowledged());
        assertEquals(List.of("java.util.Formatter.format"), p.unacknowledged());
        assertFalse(p.hasUserOwned());
    }

    @Test
    void exact_allow_pattern_acknowledges() {
        StubPolicy p = StubPolicy.judge(List.of("java.util.Formatter.format"),
                List.of("java.util.Formatter.format"), "");
        assertFalse(p.hasUnacknowledged());
    }

    @Test
    void class_wildcard_acknowledges_all_methods_of_the_class() {
        StubPolicy p = StubPolicy.judge(
                List.of("java.util.Formatter.format", "java.util.Formatter.close"),
                List.of("java.util.Formatter.*"), "");
        assertFalse(p.hasUnacknowledged());
    }

    @Test
    void package_wildcard_acknowledges_subtree_but_not_a_sibling_package() {
        StubPolicy p = StubPolicy.judge(
                List.of("java.util.Formatter.format", "java.time.LocalDate.now"),
                List.of("java.util.*"), "");
        assertEquals(List.of("java.time.LocalDate.now"), p.unacknowledged());
    }

    @Test
    void user_package_stub_is_flagged_loud_even_unacknowledged() {
        StubPolicy p = StubPolicy.judge(
                List.of("com.acme.svc.Client.call", "java.util.Formatter.format"),
                List.of(), "com.acme");
        assertTrue(p.hasUserOwned());
        assertEquals(List.of("com.acme.svc.Client.call"), p.userOwned());
        // both still count as unacknowledged
        assertEquals(2, p.unacknowledged().size());
    }

    @Test
    void user_package_match_is_prefix_bounded_not_substring() {
        // "com.acme" must not match "com.acmecorp.*" (the dotted-boundary rule).
        assertFalse(StubFilter.isUserOwned("com.acmecorp.X.m", "com.acme"));
        assertTrue(StubFilter.isUserOwned("com.acme.X.m", "com.acme"));
    }

    @Test
    void filter_drops_models_synthetics_and_cprover() {
        assertFalse(StubFilter.isSignal("org.bmc4j.engine.Bmc.anyInt"));
        assertFalse(StubFilter.isSignal("org.cprover.CProver.nondetInt"));
        assertFalse(StubFilter.isSignal("java.lang.Object.<init>"));
        assertFalse(StubFilter.isSignal("java.lang.Integer.valueOf"));
        assertFalse(StubFilter.isSignal("java.lang.AssertionError.<init>"));
        assertTrue(StubFilter.isSignal("java.util.Formatter.format"));
    }
}
