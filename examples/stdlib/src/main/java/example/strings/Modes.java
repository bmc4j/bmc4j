package example.strings;

/**
 * Ordinary code that branches on {@code String.equals}. JBMC's own {@code String.equals} is
 * unsound (it can't even prove {@code "x".equals("x")}), so bmc4j rewrites {@code equals} call
 * sites to a sound stand-in during analysis — these proofs go through with the code unchanged.
 */
public final class Modes {

    private Modes() {
    }

    /** Null-safe production check. */
    public static boolean isProd(String mode) {
        return "prod".equals(mode);
    }

    /** BUG: NPEs when the mode is unset, because {@code null.equals(...)} throws. */
    public static String banner(String mode) {
        if (mode.equals("prod")) {
            return "PRODUCTION";
        }
        return "development";
    }

    /** EU region by host prefix — uses {@code startsWith}. */
    public static boolean isEuHost(String host) {
        return host.startsWith("eu-");
    }

    /** Whether a connection string points at prod — uses {@code contains}. */
    public static boolean targetsProd(String url) {
        return url.contains("prod");
    }

    /**
     * {@code contains} with a {@link StringBuilder} (a non-String {@link CharSequence}) needle. The
     * call site's descriptor is {@code contains(CharSequence)}, which bmc4j redirects to its sound
     * stand-in. Previously the stand-in hard-cast the needle to String and threw a spurious
     * ClassCastException for a StringBuilder; now it degrades gracefully. This exercises that path.
     */
    public static boolean containsBuilt(String url, String fragment) {
        StringBuilder needle = new StringBuilder(fragment);
        return url.contains(needle);
    }

    /** Builds a qualified name by concatenation, e.g. {@code "prod" + "-" + "eu"}. */
    public static String qualify(String env, String region) {
        return env + "-" + region;
    }
}
