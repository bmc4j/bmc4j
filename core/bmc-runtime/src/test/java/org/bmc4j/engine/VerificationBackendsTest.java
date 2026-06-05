package org.bmc4j.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class VerificationBackendsTest {

    private static final String PROP = "bmc.backend";

    @AfterEach
    void clearProp() {
        System.clearProperty(PROP);
    }

    private static BmcRequest dummy() {
        return new BmcRequest("Pkg.C", "Pkg.C.m", "", 16, true, 0, false);
    }

    @Test
    void defaults_to_jbmc() {
        System.clearProperty(PROP);
        VerificationBackend b = VerificationBackends.select(dummy());
        assertInstanceOf(JbmcBackend.class, b);
        assertEquals("jbmc", b.id());
    }

    @Test
    void explicit_jbmc_is_case_insensitive_and_trimmed() {
        System.setProperty(PROP, "  JBMC ");
        assertInstanceOf(JbmcBackend.class, VerificationBackends.select(dummy()));
    }

    @Test
    void esbmc_is_rejected_with_a_helpful_message() {
        System.setProperty(PROP, "esbmc");
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> VerificationBackends.select(dummy()));
        assertTrue(ex.getMessage().contains("ESBMC backend was removed"));
        assertTrue(ex.getMessage().toLowerCase().contains("lincheck"));
    }

    @Test
    void unknown_backend_is_rejected() {
        System.setProperty(PROP, "z3");
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> VerificationBackends.select(dummy()));
        assertTrue(ex.getMessage().contains("z3"));
    }
}
