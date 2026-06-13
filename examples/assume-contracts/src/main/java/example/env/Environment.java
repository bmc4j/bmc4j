package example.env;

/**
 * An external, unanalyzable source of a deterministic configuration value - a stand-in for a runtime
 * query (an environment lookup, a config service) whose result the proof can't predict but which is
 * FIXED for the whole run. {@code Bmc.assumeStable(env::bucketCount, n -> n == 8)} pins it to one value
 * reused at every call site, including the {@code <clinit>} of {@link Buckets}.
 */
public interface Environment {

    /** A deterministic query - one value for the whole run. No analyzed body exists. */
    int bucketCount();
}
