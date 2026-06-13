package example.env

/**
 * The "environment as a fixed value" case (the okio HASH_BUCKET_COUNT shape). A static initializer
 * reads a deterministic, unanalyzable query - [Environment.bucketCount] on the process-wide [ENV] -
 * into a final bound that sizes an array and a fill loop.
 *
 * Without help that bound is SYMBOLIC (the call has no analyzed body, so JBMC nondet-stubs it), so
 * count() can be anything and the array bound can't be closed.
 * `Bmc.assumeStable(env::bucketCount) { it == 8 }` pins it to one fixed value for the whole run -
 * including this static initializer, a call site a local assume can't reach - so the bound is the
 * concrete 8 and the proof verifies.
 */
class Buckets {

    private val slots = IntArray(COUNT)

    init {
        for (i in 0 until COUNT) {
            slots[i] = i
        }
    }

    /** The bound the static initializer captured. */
    fun count(): Int = COUNT

    /**
     * The last filled slot's value, slots[COUNT - 1] (require COUNT >= 1). By construction the fill
     * loop sets it to COUNT - 1 - proving that requires the loop fully unwound, which needs the bound
     * pinned to a fixed value.
     */
    fun lastSlot(): Int = slots[COUNT - 1]

    companion object {
        /** The process-wide environment. Non-null (a benign default the proof overrides) so the COUNT
         *  initializer below reads it with a plain interface call and NO Kotlin null-check - the proof
         *  pins ENV.bucketCount() via assumeStable, which redirects that call regardless of the receiver. */
        @JvmField
        var ENV: Environment = DefaultEnvironment

        /** Read once in the static initializer from the (unanalyzable) environment query. */
        private val COUNT: Int = ENV.bucketCount()
    }
}
