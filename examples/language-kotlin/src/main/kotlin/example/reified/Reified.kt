package example.reified

/**
 * Reified type parameters (`inline fun <reified T>`). The compiler INLINES the function at every
 * call site and substitutes the concrete type for `T`, so `x is T` and `T::class` become ordinary
 * `instanceof`/class-literal bytecode against a concrete type — no `invokedynamic`, no reflection
 * over an erased parameter. JBMC therefore sees a plain type check and analyses it soundly.
 */

/** Reified type test: `x is T` at the call site becomes a concrete `instanceof`. */
inline fun <reified T> isType(x: Any?): Boolean = x is T

/** Reified safe cast: returns the value typed as T, or null when it isn't a T. */
inline fun <reified T> asType(x: Any?): T? = x as? T

/**
 * Classify a value by trying reified type tests in order. Demonstrates several reified call sites
 * inlined into one function.
 */
fun classify(x: Any?): String = when {
    isType<Int>(x) -> "int"
    isType<String>(x) -> "string"
    isType<Boolean>(x) -> "boolean"
    else -> "other"
}
