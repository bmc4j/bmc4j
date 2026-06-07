package proofs.time

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period
import org.bmc4j.Bmc
import org.bmc4j.BmcProof

/**
 * Model proofs (axis 2): laws the bounded java.time models (epoch primitives, no zones/DST)
 * must satisfy under JBMC. Inputs are bounded well inside the long range. Includes the floor-vs-
 * truncate behavior for negative sub-second values — one of the bugs the conformance work caught.
 */
class TimeLaws {

    // Bounded well inside the long range. Kept tight: time arithmetic that floors (getSeconds) divides
    // by 1000, so a smaller magnitude keeps that divider circuit small; the laws hold for any value.
    private fun anySec(): Long = Bmc.anyInt(-1_000, 1_000).toLong()
    private fun anyDay(): Long = Bmc.anyInt(-100_000, 100_000).toLong()

    // --- Instant ---

    @BmcProof
    fun instant_plus_then_minus_seconds_round_trips() {
        val t = Instant.ofEpochSecond(anySec())
        val d = anySec()
        Bmc.check(t.plusSeconds(d).minusSeconds(d) == t)
    }

    @BmcProof
    fun instant_getEpochSecond_floors_for_negative_millis() {
        Bmc.check(Instant.ofEpochMilli(-1).epochSecond == -1L)     // -0.001s floors to -1
        Bmc.check(Instant.ofEpochMilli(-1000).epochSecond == -1L)
        Bmc.check(Instant.ofEpochMilli(999).epochSecond == 0L)     // positive truncates to 0
    }

    // --- Duration ---

    @BmcProof
    fun duration_ofSeconds_getSeconds_round_trips() {
        val s = anySec()
        Bmc.check(Duration.ofSeconds(s).seconds == s)
    }

    // NOTE: Duration.between(Instant, Instant) and LocalDate.isBefore(LocalDate) are validated by the
    // differential TimeConformanceTest (vs the real JDK), not here: as @BmcProof laws they trip a JBMC
    // "Dynamic cast check" precision artifact at the call site (the model bodies are trivially correct
    // — end.toEpochMilli() - start.toEpochMilli(); this.epochDay < other.epochDay), so the differential
    // axis is the sound check for those two methods.

    @BmcProof
    fun duration_plus_then_minus_round_trips() {
        val t = anySec()
        val d = Duration.ofSeconds(anySec())
        Bmc.check(d.plus(Duration.ofSeconds(t)).minus(Duration.ofSeconds(t)) == d)
    }

    @BmcProof
    fun duration_negated_twice_round_trips() {
        val d = Duration.ofSeconds(anySec())
        Bmc.check(d.negated().negated() == d)
    }

    @BmcProof
    fun duration_getSeconds_floors_for_negative_millis() {
        Bmc.check(Duration.ofMillis(-1).seconds == -1L)
        Bmc.check(Duration.ofMillis(-1000).seconds == -1L)
        Bmc.check(Duration.ofMillis(1500).seconds == 1L)
    }

    @BmcProof
    fun duration_plusSeconds_then_minusSeconds_round_trips() {
        val d = Duration.ofSeconds(anySec())
        val n = anySec()
        Bmc.check(d.plusSeconds(n).minusSeconds(n) == d)
    }

    @BmcProof
    fun duration_plusMinutes_then_minusMinutes_round_trips() {
        val d = Duration.ofMillis(anySec())   // any millis count, small magnitude
        val n = Bmc.anyInt(-100, 100).toLong()
        Bmc.check(d.plusMinutes(n).minusMinutes(n) == d)
    }

    @BmcProof
    fun duration_multipliedBy_one_is_identity_and_negone_negates() {
        val d = Duration.ofMillis(anySec())
        Bmc.check(d.multipliedBy(1) == d)
        Bmc.check(d.multipliedBy(-1) == d.negated())
    }

    @BmcProof
    fun duration_abs_is_nonnegative_and_magnitude() {
        val s = anySec()
        val d = Duration.ofSeconds(s)
        // abs() is never negative, and equals the duration or its negation.
        Bmc.check(!d.abs().isNegative)
        Bmc.check(d.abs() == (if (d.isNegative) d.negated() else d))
    }

    // isPositive (Java 18+) is proven in DurationIsPositiveLaws on the 21+ floor.
    @BmcProof
    fun duration_isZero_isNegative_iff_sign() {
        val s = anySec()
        val d = Duration.ofSeconds(s)
        Bmc.check(d.isZero == (s == 0L))
        Bmc.check(d.isNegative == (s < 0L))
    }

    @BmcProof
    fun duration_ofMinutes_toMillis_scales() {
        // ofMinutes/ofHours/ofDays scale a unit count into millis (loud past the bound). Pin the scale
        // concretely under JBMC; the wide differential axis covers the symbolic range + saturation.
        Bmc.check(Duration.ofMinutes(3).toMillis() == 180_000L)
        Bmc.check(Duration.ofHours(2).toMillis() == 7_200_000L)
        Bmc.check(Duration.ofDays(1).toMillis() == 86_400_000L)
        Bmc.check(Duration.ofMinutes(-3).toMillis() == -180_000L)
    }

    @BmcProof
    fun duration_toNanos_scales_millis() {
        // toNanos == millis * 1_000_000 (loud past the bound). Pin the scale concretely under JBMC; the
        // differential axis covers the symbolic range + the overflow boundary.
        Bmc.check(Duration.ofMillis(5).toNanos() == 5_000_000L)
        Bmc.check(Duration.ofMillis(-5).toNanos() == -5_000_000L)
        Bmc.check(Duration.ofMillis(0).toNanos() == 0L)
        Bmc.check(Duration.ofSeconds(1).toNanos() == 1_000_000_000L)
    }

    @BmcProof
    fun duration_dividedBy_pins() {
        // dividedBy(long) truncates toward zero; a zero divisor throws. Concrete pins keep the symbolic
        // divider off the proof axis (the division-cost lesson) — the wide symbolic range + the zero-
        // divisor exception are covered differentially (TimeConformanceTest).
        Bmc.check(Duration.ofMillis(100).dividedBy(4L) == Duration.ofMillis(25))
        Bmc.check(Duration.ofMillis(-100).dividedBy(4L) == Duration.ofMillis(-25))
        Bmc.check(Duration.ofMillis(7).dividedBy(2L) == Duration.ofMillis(3))   // truncates toward zero
        Bmc.check(Duration.ofMillis(-7).dividedBy(2L) == Duration.ofMillis(-3))
    }

    // NOTE: Duration.toMinutes/toHours/toDays divide the floored second count by the fixed constants
    // 60/3600/86400 — a wide-DIVISOR division inherently SAT-slow on the @BmcProof axis regardless of
    // input range (the LocalTime precedent: tightening inputs doesn't shrink a constant divisor). They
    // are validated on the differential axis (TimeConformanceTest) vs the real JDK, including the
    // floor-vs-truncate behavior for negatives (toSeconds floors, toMinutes truncates the floored
    // seconds). plusHours/plusDays + minus mirrors are likewise differential-only past the concrete
    // pins above (their scale multiply is covered by the ofX pins and the round-trip laws).

    // --- LocalDate ---

    @BmcProof
    fun localdate_epochday_round_trips() {
        val e = anyDay()
        Bmc.check(LocalDate.ofEpochDay(e).toEpochDay() == e)
    }

    @BmcProof
    fun localdate_plus_then_minus_days_round_trips() {
        val d = LocalDate.ofEpochDay(anyDay())
        val n = anyDay()
        Bmc.check(d.plusDays(n).minusDays(n) == d)
    }

    @BmcProof
    fun localdate_plusWeeks_is_seven_days_and_round_trips() {
        val d = LocalDate.ofEpochDay(anyDay())
        val w = Bmc.anyInt(-10_000, 10_000).toLong()
        Bmc.check(d.plusWeeks(w) == d.plusDays(w * 7L))
        Bmc.check(d.plusWeeks(w).minusWeeks(w) == d)
    }

    // --- LocalDate calendar-month arithmetic ---
    // The month-carry inlines floor-division by 12 with plain / and % (NOT Math.floorDiv, which is
    // unmodeled), so the clamp/12==1 laws are JBMC-provable when driven from a CONCRETE date. We build
    // the LocalDate from LocalDateTime.of(y,m,d,..).toLocalDate(): a fresh constructed object whose
    // calendar fields JBMC tracks through the constructor. (LocalDate.ofEpochDay(<constant>) instead
    // leaves the backing epochDay field symbolic under JBMC, which spuriously refutes/UNDECIDEs — same
    // object-field artifact family as the Math/Dynamic-cast notes elsewhere; that path is covered by
    // the differential TimeConformanceTest, which proves plusMonths/plusYears bit-for-bit vs the JDK
    // across all epoch-days, month-ends, leap days and negatives.) The symbolic-delta round-trip law
    // is differential-only (see NOTE below).

    private fun dateOf(y: Int, m: Int, d: Int): LocalDate =
        LocalDateTime.of(y, m, d, 0, 0, 0).toLocalDate()

    @BmcProof
    fun localdate_plusMonths12_equals_plusYears1() {
        // 12 months == 1 year, including the leap-day clamp (Feb 29 -> Feb 28 either way).
        val mid = dateOf(2024, 6, 15)
        Bmc.check(mid.plusMonths(12) == mid.plusYears(1))
        Bmc.check(mid.plusMonths(-12) == mid.plusYears(-1))
        val feb29 = dateOf(2024, 2, 29)
        Bmc.check(feb29.plusMonths(12) == feb29.plusYears(1))
    }

    @BmcProof
    fun localdate_plus_then_minus_months_round_trips_no_clamp() {
        // The 15th survives every month length, so plusMonths/minusMonths never clamps and is exact.
        val mid = dateOf(2024, 1, 15)
        val n = Bmc.anyInt(-24, 24).toLong()
        Bmc.check(mid.plusMonths(n).minusMonths(n) == mid)
    }

    @BmcProof
    fun localdate_month_end_clamp() {
        // 2024-01-31 plusMonths 1 = 2024-02-29 (leap); plusYears across leap clamps to 2025-02-28.
        val jan31 = dateOf(2024, 1, 31)
        val feb29 = jan31.plusMonths(1)
        Bmc.check(feb29.monthValue == 2)
        Bmc.check(feb29.dayOfMonth == 29)
        Bmc.check(feb29.plusYears(1).dayOfMonth == 28)   // 2025-02-28 (non-leap)
    }

    // --- LocalTime ---
    // Field generators kept tight: of()/getters divide/mod by NANOS_PER_*, so small magnitudes keep
    // those divider circuits small. The laws hold for any in-bounds value.

    private fun anyHour(): Int = Bmc.anyInt(0, 23)
    private fun anyMin(): Int = Bmc.anyInt(0, 59)

    // localtime_of_fields_round_trip, localtime_secondOfDay_round_trips and
    // localtime_plus_then_minus_hours_round_trips are validated on the DIFFERENTIAL axis
    // (TimeConformanceTest) only: the model is nano-of-day backed, so field extraction and
    // plus/minus arithmetic divide/mod by the fixed NANOS_PER_HOUR/SECOND/DAY constants -- a
    // wide-DIVISOR division that is inherently SAT-slow on the @BmcProof axis regardless of input
    // range (tightening the inputs doesn't shrink the constant divisor; the plusHours round trip
    // hovered right at CI's 180s budget, passing or timing out by runner luck). The differential
    // suite covers of()/getHour/getMinute/getSecond/ofSecondOfDay/toSecondOfDay and
    // plusHours/minusHours/plusMinutes/minusMinutes/plusSeconds/minusSeconds vs the real JDK.
    //
    // plusNanos/minusNanos likewise mod by the wide constant NANOS_PER_DAY (8.64e13) for the day-wrap,
    // so they are differential-only for the same constant-divisor reason — TimeConformanceTest proves
    // them (incl. the sub-second and many-wrap bands) bit-for-bit vs the real JDK.

    // --- LocalDateTime ---
    // The y/m/d conversion divides/mods, so keep the year tight; arithmetic uses a fixed valid date.

    private fun anyYear(): Int = Bmc.anyInt(2000, 2030)

    @BmcProof
    fun localdatetime_time_fields_round_trip() {
        val h = anyHour(); val mi = anyMin()
        val dt = LocalDateTime.of(2020, 6, 15, h, mi, 0)
        Bmc.check(dt.hour == h)
        Bmc.check(dt.minute == mi)
        Bmc.check(dt.monthValue == 6)
        Bmc.check(dt.dayOfMonth == 15)
    }

    @BmcProof
    fun localdatetime_plus_then_minus_days_round_trips() {
        val dt = LocalDateTime.of(2020, 6, 15, 10, 30, 0)
        val n = anyDay()
        Bmc.check(dt.plusDays(n).minusDays(n) == dt)
    }

    @BmcProof
    fun localdatetime_plusMonths12_equals_plusYears1_time_unchanged() {
        val dt = LocalDateTime.of(2024, 1, 15, 10, 30, 0)
        Bmc.check(dt.plusMonths(12) == dt.plusYears(1))
        // time part is carried untouched by month arithmetic
        Bmc.check(dt.plusMonths(7).hour == 10)
        Bmc.check(dt.plusMonths(7).minute == 30)
    }

    @BmcProof
    fun localdatetime_month_end_clamp() {
        // 2024-01-31T08:00 plusMonths 1 -> 2024-02-29T08:00 (date clamps, time unchanged).
        val dt = LocalDateTime.of(2024, 1, 31, 8, 0, 0)
        val r = dt.plusMonths(1)
        Bmc.check(r.monthValue == 2)
        Bmc.check(r.dayOfMonth == 29)
        Bmc.check(r.hour == 8)
    }

    // NOTE: Period.between(LocalDate, LocalDate) is validated by the differential TimeConformanceTest
    // (vs the real JDK), not here: its body uses Math.toIntExact for loud int overflow (unmodeled by
    // JBMC, so it refutes spuriously) — same family as the Math.addExact NOTE above. The differential
    // axis exhaustively checks its y/m/d decomposition across month-ends, leap days and negatives.

    // NOTE: LocalDateTime.plusMinutes/plusHours/plusSeconds round-trips, Period.plusDays round-trips
    // and Period.normalized()'s total-months law are validated by the differential TimeConformanceTest
    // (vs the real JDK), not here. As @BmcProof laws they refute spuriously: their bodies route through
    // java.lang.Math (floorDiv/floorMod for the sub-day carry; addExact/toIntExact for loud int
    // overflow), which is unmodeled, so JBMC can't reason about those intrinsics and reports a
    // counterexample (e.g. n = 0) that the differential axis proves correct on a real JVM. Same family
    // as the Duration.between/LocalDate.isBefore "Dynamic cast check" artifact above — the differential
    // axis is the sound check for those methods.

    // --- Period ---

    private fun anyPField(): Int = Bmc.anyInt(-1_000, 1_000)

    @BmcProof
    fun period_of_fields_round_trip() {
        val y = anyPField(); val mo = anyPField(); val d = anyPField()
        val p = Period.of(y, mo, d)
        Bmc.check(p.years == y)
        Bmc.check(p.months == mo)
        Bmc.check(p.days == d)
    }

    // period_plus_then_minus_days_round_trips and period_normalized_preserves_total_months: see the
    // NOTE above — differential-axis only (their bodies use Math.addExact/toIntExact, unmodeled).

}
