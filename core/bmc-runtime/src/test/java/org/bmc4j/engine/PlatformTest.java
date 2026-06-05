package org.bmc4j.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PlatformTest {

    @Test
    void maps_windows_x64() {
        assertEquals(Platform.WINDOWS_X64, Platform.of("Windows 10", "amd64"));
        assertEquals(Platform.WINDOWS_X64, Platform.of("Windows 11", "x86_64"));
    }

    @Test
    void windows_arm64_fails_fast_with_clear_message() {
        // There is no windows-arm64 engine; selecting WINDOWS_X64 would run under unverified
        // emulation, so the mapper must reject it with an actionable message naming os/arch.
        UnsupportedOperationException ex = assertThrows(UnsupportedOperationException.class,
                () -> Platform.of("Windows 11", "aarch64"));
        assertTrue(ex.getMessage().contains("windows-arm64"), ex.getMessage());
        assertTrue(ex.getMessage().contains("os.arch=aarch64"), ex.getMessage());
        assertTrue(ex.getMessage().contains("Supported"), ex.getMessage());
        // arm64 spelling is rejected too.
        assertThrows(UnsupportedOperationException.class,
                () -> Platform.of("WINDOWS", "arm64"));
    }

    @Test
    void maps_mac_by_arch() {
        assertEquals(Platform.MACOS_X64, Platform.of("Mac OS X", "x86_64"));
        assertEquals(Platform.MACOS_ARM64, Platform.of("Mac OS X", "aarch64"));
        assertEquals(Platform.MACOS_ARM64, Platform.of("Darwin", "arm64"));
    }

    @Test
    void maps_linux_by_arch_as_the_default_family() {
        assertEquals(Platform.LINUX_X64, Platform.of("Linux", "amd64"));
        assertEquals(Platform.LINUX_ARM64, Platform.of("Linux", "aarch64"));
        assertEquals(Platform.LINUX_X64, Platform.of("FreeBSD", "x86_64")); // unknown OS -> linux family
    }

    @Test
    void is_case_insensitive() {
        assertEquals(Platform.WINDOWS_X64, Platform.of("WINDOWS", "AMD64"));
        assertEquals(Platform.MACOS_ARM64, Platform.of("MAC OS X", "ARM64"));
    }

    @Test
    void flags_and_id_are_consistent() {
        assertTrue(Platform.WINDOWS_X64.isWindows());
        assertFalse(Platform.WINDOWS_X64.isMac());
        assertTrue(Platform.MACOS_ARM64.isMac());
        assertFalse(Platform.MACOS_ARM64.isWindows());
        assertFalse(Platform.LINUX_X64.isWindows());
        assertFalse(Platform.LINUX_X64.isMac());
        assertEquals("windows-x64", Platform.WINDOWS_X64.id());
        assertEquals("macos-arm64", Platform.MACOS_ARM64.id());
        assertEquals("linux-arm64", Platform.LINUX_ARM64.id());
    }

    @Test
    void current_returns_a_platform() {
        // Smoke: on this host it must resolve to one of the known platforms.
        assertTrue(Platform.current().id().matches("(windows|linux|macos)-(x64|arm64)"));
    }
}
