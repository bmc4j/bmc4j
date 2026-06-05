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
