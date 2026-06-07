package example.basics

/**
 * The basic contract shape in Kotlin. `triangle(n)` is a loop — costly to inline — so a caller at a
 * tiny `unwind` can only get through by reusing the contract's `@Ensures` instead of unrolling.
 *
 * It lives in an `object` with `@JvmStatic`, NOT as a bare top-level `fun`. That is deliberate: a
 * bare top-level function compiles into a synthetic file facade class (`TriangleKt`) that Kotlin has
 * no syntax to name — you cannot write `TriangleKt::class`, so `@BmcContractsFor(TriangleKt::class)`
 * is unexpressible from Kotlin. An `object` (or a `companion object`) with `@JvmStatic` is the
 * Kotlin-idiomatic way to expose a contractable static method: it is nameable as `Triangles::class`
 * and the predicate-free production code stays just as terse as a top-level function.
 */
object Triangles {

    @JvmStatic
    fun triangle(n: Int): Int {
        var s = 0
        for (i in 1..n) s += i
        return s
    }
}

/** Identical body with NO contract, to show the same bound is too small without the summary. */
object TrianglesNaive {

    @JvmStatic
    fun triangle(n: Int): Int {
        var s = 0
        for (i in 1..n) s += i
        return s
    }
}
