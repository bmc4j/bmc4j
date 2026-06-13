package example.env

/**
 * An external, unanalyzable source of a deterministic configuration value - a stand-in for a runtime
 * query (an environment lookup, a config service) whose result the proof can't predict but which is
 * FIXED for the whole run. `Bmc.assumeStable(env::bucketCount) { it == 8 }` pins it to one value
 * reused at every call site, including the static initializer of [Buckets].
 */
interface Environment {

    /** A deterministic query - one value for the whole run. No analyzed body exists. */
    fun bucketCount(): Int
}

/**
 * A benign default so [Buckets.ENV] is non-null at class-init time (the proof overrides it). Its body is
 * never the analyzed one in a proof: assumeStable redirects every Environment.bucketCount call site -
 * including the Buckets static initializer - to the constrained-nondet stub.
 */
object DefaultEnvironment : Environment {
    override fun bucketCount(): Int = 0
}
