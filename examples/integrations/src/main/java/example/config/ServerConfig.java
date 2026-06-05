package example.config;

/**
 * Configuration consumed as typed values. Proofs read the <b>actual</b> values this run was launched
 * with via {@code Bmc.intFromProperty("app.port")} / {@code boolFromProperty(...)} / … and verify the
 * logic against that concrete config. Production code carries no bmc references.
 */
public final class ServerConfig {

    private ServerConfig() {
    }

    /** Clamp a configured port into the valid TCP range. */
    public static int clampPort(int configured) {
        if (configured < 1) {
            return 1;
        }
        if (configured > 65535) {
            return 65535;
        }
        return configured;
    }

    /** Double a configured buffer budget — overflows int for a large configured value. */
    public static int doubledBudget(int configuredKb) {
        return configuredKb * 2;
    }

    /** Resolve a verbosity level from two flags; quiet wins over debug. */
    public static int verbosity(boolean debug, boolean quiet) {
        if (quiet) {
            return 0;
        }
        return debug ? 2 : 1;
    }
}
