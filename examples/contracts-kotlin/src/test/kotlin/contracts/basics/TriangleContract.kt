package contracts.basics

import example.basics.Triangles
import org.bmc4j.BmcContractsFor
import org.bmc4j.Ensures
import org.bmc4j.Requires

/**
 * The contract for [Triangles.triangle], declared test-side from Kotlin. The mirror's signature
 * binds to the `@JvmStatic` method on the `Triangles` object — exactly the static-target case, named
 * from Kotlin as `Triangles::class`.
 *
 * Predicates are `@JvmStatic` functions in the companion object: `@JvmStatic` lifts them onto the
 * contract interface as `static boolean` methods, which is what the generated stub and enforce-proof
 * call (`TriangleContract.bounded(n)`). Parameter names survive into bytecode because the bmc4j
 * plugin sets `javaParameters = true` on the Kotlin compile.
 */
@BmcContractsFor(Triangles::class)
interface TriangleContract {

    @Requires("bounded")
    @Ensures("nonNeg")
    fun triangle(n: Int): Int

    companion object {
        @JvmStatic fun bounded(n: Int): Boolean = n in 0..8
        @JvmStatic fun nonNeg(result: Int, n: Int): Boolean = result >= 0
    }
}
