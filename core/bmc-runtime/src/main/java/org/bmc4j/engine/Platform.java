package org.bmc4j.engine;

/** Host OS/arch identity, used to pick the matching bundled engine. */
public enum Platform {

    WINDOWS_X64("windows-x64", true, false),
    LINUX_X64("linux-x64", false, false),
    LINUX_ARM64("linux-arm64", false, false),
    MACOS_X64("macos-x64", false, true),
    MACOS_ARM64("macos-arm64", false, true);

    private final String id;
    private final boolean windows;
    private final boolean mac;

    Platform(String id, boolean windows, boolean mac) {
        this.id = id;
        this.windows = windows;
        this.mac = mac;
    }

    /** Stable identifier used in artifact names and resource paths (e.g. {@code windows-x64}). */
    public String id() {
        return id;
    }

    public boolean isWindows() {
        return windows;
    }

    public boolean isMac() {
        return mac;
    }

    public static Platform current() {
        return of(System.getProperty("os.name", ""), System.getProperty("os.arch", ""));
    }

    /** Supported engine platforms, named in fail-fast messages so the error is actionable. */
    private static final String SUPPORTED =
            "windows-x64, linux-x64, linux-arm64, macos-x64, macos-arm64";

    /**
     * Map raw {@code os.name}/{@code os.arch} strings to a platform (package-private for tests).
     *
     * <p>Fails fast on platforms with no bundled engine instead of silently selecting a wrong
     * binary. The only such case here is windows-arm64: there is no windows-arm64 engine module,
     * and handing back {@code WINDOWS_X64} would run the x64 binary under unverified emulation.
     * (musl/Alpine on Linux can't be told apart from glibc by name/arch alone, so that check
     * lives at the extraction site in {@link BundledEngine}, not in this pure mapper.)
     *
     * @throws UnsupportedOperationException if the OS/arch has no bundled engine
     */
    static Platform of(String osName, String osArch) {
        String os = osName.toLowerCase();
        String arch = osArch.toLowerCase();
        boolean arm = arch.contains("aarch64") || arch.contains("arm");
        // Match "windows", not "win": "darwin" contains "win" and must fall through to mac.
        if (os.contains("windows")) {
            if (arm) {
                throw new UnsupportedOperationException(
                        "bmc4j has no engine for windows-arm64 (os.name=" + osName
                                + ", os.arch=" + osArch + "). Supported: " + SUPPORTED + ".");
            }
            return WINDOWS_X64;
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return arm ? MACOS_ARM64 : MACOS_X64;
        }
        return arm ? LINUX_ARM64 : LINUX_X64;
    }
}
