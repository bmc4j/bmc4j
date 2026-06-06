package example.collectionliterals

/**
 * Collection literals (experimental in Kotlin 2.4, `-Xcollection-literals`). The bracket
 * literal lowers to the stdlib's `of` factory — equivalent to `listOf(...)`, stdlib bytecode
 * JBMC analyses directly — so a literal list is as provable as any other.
 */

/** Parking fee per day, Monday..Friday — five entries. BUG: callers index by day-of-week 0..6. */
val WEEKDAY_FEES: List<Int> = [5, 10, 10, 10, 25]

fun feeFor(dayOfWeek: Int): Int = WEEKDAY_FEES[dayOfWeek]

/** Fixed: weekends are free; only weekdays index the literal. */
fun safeFeeFor(dayOfWeek: Int): Int = if (dayOfWeek in 0..4) WEEKDAY_FEES[dayOfWeek] else 0
