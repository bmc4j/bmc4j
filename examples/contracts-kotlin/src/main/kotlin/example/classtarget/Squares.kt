package example.classtarget

/**
 * Production target for the **normal-`class` static target** confirmation. `Squares` is a plain Kotlin
 * `class` (NOT an `object`); `pyramid` is a `companion object` `@JvmStatic fun`, so the JVM emits it as a
 * real `static` method on `Squares` itself — the idiomatic way to give a normal class a contractable
 * static method.
 *
 * The point of this concept is purely the orthogonality of host-kind and target-kind: the contract for
 * `pyramid` (see `contracts.classtarget.SquaresContract`) is hosted on a plain `object` with ordinary
 * `fun` predicates — the standard Kotlin shape — and it binds this normal-`class` static target exactly
 * as it binds an `object` target (see `basics`). The auto-generated enforce-proof discharges VERIFIED.
 */
class Squares {

    companion object {
        @JvmStatic
        fun pyramid(n: Int): Int {
            var s = 0
            for (i in 1..n) s += i * i
            return s
        }

        /**
         * The soundness-guard target: strictly positive for any `n >= 0`. The contract claims
         * `result <= 0` for it (a lie), so its enforce-proof passes BY refutation and publishes no
         * redirect — annotating an object-hosted contract on a class-static target is no more
         * "asserting" it than for an object target.
         */
        @JvmStatic
        fun bogus(n: Int): Int = pyramid(n) + 1
    }
}
