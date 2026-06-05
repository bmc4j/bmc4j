package org.bmc4j.engine;

/**
 * Targets that {@link ConfigBytecode} redirects a {@code Bmc.*From{Env,Property}} call to when the
 * named variable is <b>unset or unparseable</b> for the proof run — each throws, so the proof fails
 * with a clear "required config not set" rather than silently passing. (When the variable IS set,
 * the call is replaced with the concrete value instead and these are never reached.)
 */
public final class ConfigSupport {

    private static final String MSG = "bmc4j: required config is not set (or does not parse)";

    private ConfigSupport() {
    }

    public static int missingInt(String key) {
        throw new AssertionError(MSG);
    }

    public static long missingLong(String key) {
        throw new AssertionError(MSG);
    }

    public static boolean missingBool(String key) {
        throw new AssertionError(MSG);
    }

    public static double missingDouble(String key) {
        throw new AssertionError(MSG);
    }

    public static String missingString(String key) {
        throw new AssertionError(MSG);
    }
}
