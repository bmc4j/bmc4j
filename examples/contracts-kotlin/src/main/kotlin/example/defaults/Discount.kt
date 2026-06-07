package example.defaults

/**
 * A Kotlin-specific shape: a method with a **default parameter**. The Kotlin compiler emits two JVM
 * methods — the real `price(qty, rebate)` and a synthetic `price$default(qty, rebate, mask, marker)`
 * that fills in defaults and then calls the real one. A contract mirror binds to the REAL method; the
 * interesting question this concept settles is whether the call-site redirect also covers the
 * `$default` path (a caller that omits the argument). It does — see the proofs.
 *
 * The loop makes `price` costly to inline, so a caller at a tiny `unwind` only passes by reusing the
 * contract — whether or not it supplied the defaulted argument.
 */
class Discount(val base: Int) {

    fun price(qty: Int, rebate: Int = 0): Int {
        var total = base - rebate
        for (i in 0 until qty) total += 1
        return total
    }
}
