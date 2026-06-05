package example.whenexpr

/** A sealed hierarchy — `when (shape)` over it is **exhaustive**: the compiler guarantees every
 *  variant is handled, so no `else` is needed. This is the headline use of `when`. */
sealed interface Shape
data class Circle(val r: Int) : Shape
data class Square(val s: Int) : Shape
data class Rect(val w: Int, val h: Int) : Shape

/** Exhaustive `when` with `is` branches that read the matched variant's properties. */
fun area(shape: Shape): Int = when (shape) {
    is Circle -> 3 * shape.r * shape.r
    is Square -> shape.s * shape.s
    is Rect -> shape.w * shape.h
}

/** A letter grade by score range — a range `when`. BUG: score 79 falls through a gap to 'F'. */
fun grade(score: Int): Char = when (score) {
    in 90..100 -> 'A'
    in 80..89 -> 'B'
    in 0..78 -> 'C'      // off-by-one: 79 is not covered by any branch
    else -> 'F'
}

/** A string `when` (Kotlin lowers it to sound character-wise equality). */
fun statusCode(s: String): Int = when (s) {
    "ok" -> 200
    "missing" -> 404
    else -> 500
}
