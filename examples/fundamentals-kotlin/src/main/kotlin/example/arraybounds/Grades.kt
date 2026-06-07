package example.arraybounds

object Grades {
    private val bands = arrayOf("F", "D", "C", "B", "A") // 5 bands

    // BUG: score / 20 == 5 when score == 100 -> out of bounds.
    fun label(score: Int): Int = bands[score / 20].length

    // The fix: clamp the index into range.
    fun labelSafe(score: Int): Int {
        val raw = score / 20
        val index = if (raw >= bands.size) bands.size - 1 else raw
        return bands[index].length
    }
}

/**
 * A Kotlin value class whose domain invariant lives in the constructor (`init { require(...) }`).
 * JBMC runs that `init {}` during analysis, so the 1..100 range is verified, not assumed — and
 * `assumeValid { Score(anyInt()) }` reuses it to fold the same range straight into the proof
 * domain, with no duplicated `assume`.
 */
@JvmInline
value class Score(val value: Int) {
    init {
        require(value in 1..100) { "score out of range: $value" }
    }
}

/**
 * Maps a 1..100 score to one of ten letter bands. The index `(value - 1) / 10` is 0..9 for every
 * valid [Score], so the lookup is in range for the whole domain the value class admits.
 */
fun gradeBand(score: Int): String {
    val labels = arrayOf("F", "E", "D", "C-", "C", "C+", "B", "B+", "A-", "A")
    return labels[(score - 1) / 10]
}
