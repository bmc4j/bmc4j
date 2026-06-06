package org.bmc4j.engine

/** Host OS/arch identity, used to pick the matching bundled engine. */
enum class Platform(
        /** Stable identifier used in artifact names and resource paths (e.g. `windows-x64`).
         *  `@get:JvmName("id")` keeps the original Java accessor name for the Java call sites. */
        @get:JvmName("id") val id: String,
        val isWindows: Boolean,
        val isMac: Boolean) {

    WINDOWS_X64("windows-x64", true, false),
    LINUX_X64("linux-x64", false, false),
    LINUX_X64_MUSL("linux-x64-musl", false, false),
    LINUX_ARM64("linux-arm64", false, false),
    MACOS_X64("macos-x64", false, true),
    MACOS_ARM64("macos-arm64", false, true);

    companion object {

        /**
         * The host's bundled-engine platform.
         *
         * `os.name`/`os.arch` alone can't tell a musl/Alpine x64 host from a glibc one (both report
         * `Linux`/`amd64`), but the bundled glibc jbmc cannot exec under musl. So after the pure
         * name/arch mapping ([of]), a musl x64 host is REDIRECTED to [LINUX_X64_MUSL] — the engine jar
         * built against musl. The C-library probe reads the real filesystem root and so lives here, not
         * in the pure, host-independent [of] mapper (which the unit tests pin).
         */
        @JvmStatic
        fun current(): Platform {
            val base = of(System.getProperty("os.name", ""), System.getProperty("os.arch", ""))
            return if (base == LINUX_X64 && BundledEngine.isMuslLibc()) LINUX_X64_MUSL else base
        }

        /** Supported engine platforms, named in fail-fast messages so the error is actionable. */
        private const val SUPPORTED =
                "windows-x64, linux-x64, linux-x64-musl, linux-arm64, macos-x64, macos-arm64"

        /**
         * Map raw `os.name`/`os.arch` strings to a platform (package-private for tests).
         *
         * Fails fast on platforms with no bundled engine instead of silently selecting a wrong
         * binary. The only such case here is windows-arm64: there is no windows-arm64 engine module,
         * and handing back [WINDOWS_X64] would run the x64 binary under unverified emulation.
         *
         * This is the PURE name/arch mapper and is host-independent: it returns [LINUX_X64] for any
         * Linux x64, glibc or musl, because it cannot see the C library. The musl redirect to
         * [LINUX_X64_MUSL] is applied by [current] (which can probe the live filesystem). musl arm64
         * has no engine yet, so arm64 always maps to [LINUX_ARM64].
         *
         * @throws UnsupportedOperationException if the OS/arch has no bundled engine
         */
        internal fun of(osName: String, osArch: String): Platform {
            val os = osName.lowercase()
            val arch = osArch.lowercase()
            val arm = arch.contains("aarch64") || arch.contains("arm")
            // Match "windows", not "win": "darwin" contains "win" and must fall through to mac.
            return when {
                os.contains("windows") -> {
                    if (arm) {
                        throw UnsupportedOperationException(
                                "bmc4j has no engine for windows-arm64 (os.name=$osName, " +
                                        "os.arch=$osArch). Supported: $SUPPORTED.")
                    }
                    WINDOWS_X64
                }
                os.contains("mac") || os.contains("darwin") -> if (arm) MACOS_ARM64 else MACOS_X64
                else -> if (arm) LINUX_ARM64 else LINUX_X64
            }
        }
    }
}
