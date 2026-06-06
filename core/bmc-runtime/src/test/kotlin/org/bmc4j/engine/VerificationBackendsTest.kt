package org.bmc4j.engine

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class VerificationBackendsTest {

    @AfterEach
    fun clearProp() {
        System.clearProperty(PROP)
    }

    @Test
    fun defaults_to_jbmc() {
        System.clearProperty(PROP)
        val b = VerificationBackends.select(dummy())
        assertInstanceOf(JbmcBackend::class.java, b)
        assertEquals("jbmc", b.id())
    }

    @Test
    fun explicit_jbmc_is_case_insensitive_and_trimmed() {
        System.setProperty(PROP, "  JBMC ")
        assertInstanceOf(JbmcBackend::class.java, VerificationBackends.select(dummy()))
    }

    @Test
    fun esbmc_is_rejected_with_a_helpful_message() {
        System.setProperty(PROP, "esbmc")
        val ex = assertThrows(IllegalArgumentException::class.java) { VerificationBackends.select(dummy()) }
        assertTrue(ex.message!!.contains("ESBMC backend was removed"))
        assertTrue(ex.message!!.lowercase().contains("lincheck"))
    }

    @Test
    fun unknown_backend_is_rejected() {
        System.setProperty(PROP, "z3")
        val ex = assertThrows(IllegalArgumentException::class.java) { VerificationBackends.select(dummy()) }
        assertTrue(ex.message!!.contains("z3"))
    }

    companion object {
        private const val PROP = "bmc.backend"

        private fun dummy(): BmcRequest =
                BmcRequest("Pkg.C", "Pkg.C.m", "", 16, true, 0, false)
    }
}
