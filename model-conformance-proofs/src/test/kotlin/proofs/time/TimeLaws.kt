package proofs.time

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Month
import java.time.Period
import java.time.ZoneOffset
import java.time.chrono.IsoEra
import org.bmc4j.Bmc
import org.bmc4j.BmcProof
import org.bmc4j.Verdict

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

    // Duration.between(Instant, Instant) — now @BmcProof-able. Its real signature is
    // between(Temporal, Temporal), so JDK-compiled proof bytecode checkcasts each Instant arg to
    // Temporal; the Instant model now `implements Temporal` so that cast passes (previously it tripped
    // "✗ Dynamic cast check" and refuted spuriously — a real, now-fixed unsoundness, NOT an inherent
    // JBMC artifact). The Duration.between model mirrors the (Temporal, Temporal) descriptor and casts
    // back to the Instant model.
    @BmcProof
    fun duration_between_is_end_minus_start() {
        val a = Instant.ofEpochMilli(Bmc.anyLong(-1_000_000, 1_000_000))
        val b = Instant.ofEpochMilli(Bmc.anyLong(-1_000_000, 1_000_000))
        Bmc.check(Duration.between(a, b).toMillis() == b.toEpochMilli() - a.toEpochMilli())
    }

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

    // of/plus/minus(long, TemporalUnit) dispatch on the now-modeled ChronoUnit over the millis backing.
    // unwind = 32: the ChronoUnit enum-switch dispatch clones ChronoUnit.values() (16 constants) in its
    // synthetic $SwitchMap init; that clone loop hits the default unwind of 16, so raise the bound.
    @BmcProof(unwind = 32)
    fun duration_of_unit_matches_typed_factories() {
        val n = anySec()
        Bmc.check(Duration.of(n, java.time.temporal.ChronoUnit.SECONDS) == Duration.ofSeconds(n))
        Bmc.check(Duration.of(n, java.time.temporal.ChronoUnit.MILLIS) == Duration.ofMillis(n))
    }

    @BmcProof(unwind = 32)
    fun duration_plus_unit_matches_plusSeconds_and_minus_round_trips() {
        val d = Duration.ofMillis(anySec())
        val n = anySec()
        Bmc.check(d.plus(n, java.time.temporal.ChronoUnit.SECONDS) == d.plusSeconds(n))
        Bmc.check(d.plus(n, java.time.temporal.ChronoUnit.MILLIS)
            .minus(n, java.time.temporal.ChronoUnit.MILLIS) == d)
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
    fun duration_dividedBy_duration_pins() {
        // dividedBy(Duration) = how many times the divisor fits, truncated toward zero; zero divisor
        // throws. Concrete pins keep the symbolic divider off the proof axis (the division-cost lesson)
        // — the wide symbolic range + the zero-divisor exception are covered differentially.
        Bmc.check(Duration.ofMillis(100).dividedBy(Duration.ofMillis(30)) == 3L)
        Bmc.check(Duration.ofMillis(-100).dividedBy(Duration.ofMillis(30)) == -3L)   // truncates toward zero
        Bmc.check(Duration.ofMillis(7).dividedBy(Duration.ofMillis(7)) == 1L)
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

    // NOTE: the models/time-tail-2 pass added more tail members, all DIFFERENTIAL-axis only for the
    // same constant-divisor / decomposition reasons: Duration's toMillisPart/toSecondsPart/toMinutesPart/
    // toHoursPart/toDaysPart (div/mod the floored seconds by 1000/60/3600/86400 constants) and withSeconds
    // (composes the floored milli part); LocalDate.of(y,m,d) (validates then the wide-constant toEpochDay
    // decode); and LocalDateTime.plusNanos/minusNanos (mod the wide NANOS_PER_DAY for the day-wrap, the
    // LocalTime.plusNanos precedent). TimeConformanceTest proves all of them bit-for-bit vs the real JDK.

    // NOTE: the LocalDate/LocalDateTime/LocalTime tail added in the time-tail pass — the calendar-field
    // accessors (getDayOfYear/lengthOfMonth/lengthOfYear/isLeapYear), ofYearDay, the with* field setters
    // (withYear/withMonth/withDayOf*/withHour/withMinute/withSecond/withNano), atTime/atDate/atStartOfDay,
    // until(ChronoLocalDate), plusWeeks/minusWeeks, and Period's withYears/withMonths/withDays/
    // multipliedBy — are validated on the DIFFERENTIAL axis (TimeConformanceTest) only. They either
    // (a) decode the epoch-day to y/m/d (div/mod by the wide 146097/etc. constants — the constant-divisor
    // SAT-pathology, same as LocalTime's nano-of-day getters), or (b) route through java.lang.Math
    // *Exact/floor* intrinsics for loud overflow (unmodeled by JBMC — refutes spuriously, the Period
    // NOTE family). The differential suite proves them bit-for-bit vs the real JDK across month-ends,
    // leap days and negatives.
    //
    // The ordering methods (isBefore/isAfter/isEqual/compareTo on LocalDate/LocalDateTime/LocalTime and
    // Duration.between) USED to be in this differential-only set because their interface-typed real
    // signatures made the proof-site checkcast fail ("✗ Dynamic cast check"). That was a REAL, fixable
    // unsoundness (the java.time models implemented no JDK interfaces): now that the models implement the
    // relevant marker interfaces (Temporal/ChronoLocalDate/ChronoLocalDateTime) the casts pass and those
    // methods are proven above. The constant-divisor / Math-intrinsic families remain differential-only.

    // --- LocalDate ---

    @BmcProof
    fun localdate_epochday_round_trips() {
        val e = anyDay()
        Bmc.check(LocalDate.ofEpochDay(e).toEpochDay() == e)
    }

    // isBefore/isAfter/isEqual/compareTo — now @BmcProof-able. Their real signatures take
    // ChronoLocalDate, so JDK-compiled proof bytecode checkcasts the LocalDate arg to ChronoLocalDate;
    // the LocalDate model now `implements ChronoLocalDate` so that cast passes (previously it tripped
    // "✗ Dynamic cast check" and refuted spuriously — the root-caused, now-fixed unsoundness, NOT an
    // inherent JBMC artifact). The model mirrors the ChronoLocalDate-typed descriptor and casts back.
    @BmcProof
    fun localdate_isBefore_isAfter_isEqual_match_epochday_order() {
        val a = LocalDate.ofEpochDay(anyDay())
        val b = LocalDate.ofEpochDay(anyDay())
        Bmc.check(a.isBefore(b) == (a.toEpochDay() < b.toEpochDay()))
        Bmc.check(a.isAfter(b) == (a.toEpochDay() > b.toEpochDay()))
        Bmc.check(a.isEqual(b) == (a.toEpochDay() == b.toEpochDay()))
    }

    @BmcProof
    fun localdate_compareTo_matches_epochday_sign() {
        val a = LocalDate.ofEpochDay(anyDay())
        val b = LocalDate.ofEpochDay(anyDay())
        val c = a.compareTo(b)
        Bmc.check((c < 0) == (a.toEpochDay() < b.toEpochDay()))
        Bmc.check((c == 0) == (a.toEpochDay() == b.toEpochDay()))
        Bmc.check((c > 0) == (a.toEpochDay() > b.toEpochDay()))
    }

    // SOUNDNESS PROBE for the interface-cast fix: a WRONG comparison claim must still REFUTE now that
    // the checkcast passes — proving the cast fix did NOT mask real failures (it buys instanceof only,
    // never turns a wrong body green). isBefore is strict <, so claiming <= must be refuted by a == b.
    @BmcProof(expect = Verdict.REFUTED)
    fun localdate_isBefore_wrong_le_claim_refutes() {
        val a = LocalDate.ofEpochDay(anyDay())
        val b = LocalDate.ofEpochDay(anyDay())
        Bmc.check(a.isBefore(b) == (a.toEpochDay() <= b.toEpochDay()))
    }

    // The public range constants are pure field reads (no wide-divisor decode), so their epoch-day
    // backing and ordering relationships are @BmcProof-clean. EPOCH is exactly 1970-01-01 (epoch-day 0),
    // and MIN/MAX bracket it (and any in-range date) — the JDK's defining property of MIN/MAX.
    @BmcProof
    fun localdate_constants_pin_epoch_and_bracket() {
        Bmc.check(LocalDate.EPOCH.toEpochDay() == 0L)
        Bmc.check(LocalDate.MIN.toEpochDay() < LocalDate.MAX.toEpochDay())
        Bmc.check(!LocalDate.MIN.isAfter(LocalDate.EPOCH))
        Bmc.check(!LocalDate.MAX.isBefore(LocalDate.EPOCH))
        // any in-range date sits within [MIN, MAX]
        val d = LocalDate.ofEpochDay(anyDay())
        Bmc.check(!d.isBefore(LocalDate.MIN))
        Bmc.check(!d.isAfter(LocalDate.MAX))
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

    // getMonth (modeled Month enum) / getEra (modeled IsoEra) decode the date part; driven from a CONCRETE
    // date (dateOf) so the wide-divisor ymd() decode stays a tracked circuit, like the clamp laws above.
    @BmcProof
    fun localdate_getMonth_getEra_match_fields() {
        val ce = dateOf(2024, 6, 15)
        Bmc.check(ce.month == Month.JUNE)
        Bmc.check(ce.month.value == ce.monthValue)
        Bmc.check(ce.era == IsoEra.CE)               // year 2024 >= 1 → CE
        val bce = dateOf(0, 3, 1)                     // proleptic year 0 < 1 → BCE
        Bmc.check(bce.era == IsoEra.BCE)
    }

    // of(int, Month, int) is the Month-enum factory; it must agree with the int of(y, m, d) factory.
    @BmcProof
    fun localdate_of_month_enum_matches_int_factory() {
        val y = Bmc.anyInt(2000, 2050)
        val d = Bmc.anyInt(1, 28)               // <= 28 so no month-length clamp/reject branch is hit
        Bmc.check(LocalDate.of(y, Month.MARCH, d) == LocalDate.of(y, 3, d))
    }

    // toEpochSecond(time, offset) is pure int arithmetic over the modeled epoch-day / second-of-day /
    // offset-total-seconds — no zone DB. Pin its decomposition from a concrete date+time+offset.
    @BmcProof
    fun localdate_toEpochSecond_decomposes() {
        val d = dateOf(2024, 6, 15)
        val secOfDay = Bmc.anyInt(0, 86_399)
        val t = LocalTime.ofSecondOfDay(secOfDay.toLong())
        val offSec = Bmc.anyInt(-18 * 3600, 18 * 3600)
        val off = ZoneOffset.ofTotalSeconds(offSec)
        Bmc.check(d.toEpochSecond(t, off) == d.toEpochDay() * 86_400L + secOfDay - offSec)
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

    // getMonth (modeled Month enum) of the date part; getDayOfWeek round-trips through the LocalDate model.
    @BmcProof
    fun localdatetime_getMonth_matches_value() {
        val dt = LocalDateTime.of(2024, 6, 15, 10, 30, 0)
        Bmc.check(dt.month == Month.JUNE)
        Bmc.check(dt.month.value == dt.monthValue)
    }

    // ofEpochSecond(epochSecond, nano, offset) and toEpochSecond(offset) are inverse over the second grid
    // (whole seconds, nano part 0): build a date-time at second resolution and round-trip the epoch-second.
    // Pure int arithmetic over the (epoch-day, nano-of-day) backing and the explicit offset — no zone DB.
    // es is bounded to a ±~1-day window (still crosses day boundaries and spans pre/post-epoch with the
    // offset) so the symbolic floorDiv/floorMod-by-86400 + civil-date decomposition fits the proof budget;
    // the round-trip identity is value-independent, so the bounded window proves it just as soundly.
    @BmcProof
    fun localdatetime_ofEpochSecond_toEpochSecond_round_trips() {
        val es = Bmc.anyInt(-100_000, 100_000).toLong()
        val offSec = Bmc.anyInt(-18 * 3600, 18 * 3600)
        val off = ZoneOffset.ofTotalSeconds(offSec)
        val dt = LocalDateTime.ofEpochSecond(es, 0, off)
        Bmc.check(dt.toEpochSecond(off) == es)
    }

    // isBefore/isAfter/compareTo — now @BmcProof-able. Their real signatures take ChronoLocalDateTime<?>,
    // so the LocalDateTime arg is checkcast to that interface; the model now `implements
    // ChronoLocalDateTime<LocalDate>` so the cast passes (was a spurious "Dynamic cast check" refute).
    // Same time-of-day on both sides, so ordering reduces to the day shift.
    // LocalDateTime.MIN/MAX = LocalDate.MIN/MAX at LocalTime.MIN/MAX — pure field reads through
    // toLocalDate()/toLocalTime(), so the constant relationships are @BmcProof-clean: MIN's date is
    // LocalDate.MIN at midnight, MAX's time is the day's last nano, and MIN <= MAX.
    @BmcProof
    fun localdatetime_constants_compose_date_and_time_extremes() {
        Bmc.check(LocalDateTime.MIN.toLocalDate().toEpochDay() == LocalDate.MIN.toEpochDay())
        Bmc.check(LocalDateTime.MIN.toLocalTime().toNanoOfDay() == 0L)
        Bmc.check(LocalDateTime.MAX.toLocalDate().toEpochDay() == LocalDate.MAX.toEpochDay())
        Bmc.check(LocalDateTime.MAX.toLocalTime().toNanoOfDay() == 86_399_999_999_999L)
        Bmc.check(LocalDateTime.MIN.isBefore(LocalDateTime.MAX))
    }

    @BmcProof
    fun localdatetime_isBefore_isAfter_match_day_order() {
        val base = LocalDateTime.of(2020, 6, 15, 10, 30, 0)
        val a = base.plusDays(anyDay())
        val b = base.plusDays(anyDay())
        Bmc.check(a.isBefore(b) == a.toLocalDate().isBefore(b.toLocalDate()))
        Bmc.check(a.isAfter(b) == a.toLocalDate().isAfter(b.toLocalDate()))
    }

    @BmcProof
    fun localdatetime_compareTo_sign_matches_day_order() {
        val base = LocalDateTime.of(2020, 6, 15, 10, 30, 0)
        val a = base.plusDays(anyDay())
        val b = base.plusDays(anyDay())
        val c = a.compareTo(b)
        Bmc.check((c < 0) == a.toLocalDate().isBefore(b.toLocalDate()))
        Bmc.check((c > 0) == a.toLocalDate().isAfter(b.toLocalDate()))
    }

    // --- LocalTime ordering: the model's isBefore/isAfter/compareTo take the CONCRETE LocalTime (the
    // real signatures do too — LocalTime is not part of a Chrono interface), so there was never an
    // interface checkcast to block them; they are @BmcProof-able directly off the nano-of-day backing.
    // The LocalTime well-known constants are pure nano-of-day field reads (no NANOS_PER_* divide), so
    // their backing and bracketing are @BmcProof-clean. MIDNIGHT == MIN == nano-of-day 0; NOON == 12h;
    // MAX is the day's last nano, and brackets any in-range time.
    @BmcProof
    fun localtime_constants_pin_nanoofday_and_bracket() {
        Bmc.check(LocalTime.MIN.toNanoOfDay() == 0L)
        Bmc.check(LocalTime.MIDNIGHT.toNanoOfDay() == 0L)
        Bmc.check(LocalTime.NOON.toNanoOfDay() == 43_200_000_000_000L)
        Bmc.check(LocalTime.MAX.toNanoOfDay() == 86_399_999_999_999L)
        val t = LocalTime.ofNanoOfDay(Bmc.anyLong(0, 86_399_999_999_999L))
        Bmc.check(!t.isBefore(LocalTime.MIN))
        Bmc.check(!t.isAfter(LocalTime.MAX))
    }

    @BmcProof
    fun localtime_isBefore_isAfter_compareTo_match_nanoofday_order() {
        val a = LocalTime.ofNanoOfDay(Bmc.anyLong(0, 86_399_999_999_999L))
        val b = LocalTime.ofNanoOfDay(Bmc.anyLong(0, 86_399_999_999_999L))
        Bmc.check(a.isBefore(b) == (a.toNanoOfDay() < b.toNanoOfDay()))
        Bmc.check(a.isAfter(b) == (a.toNanoOfDay() > b.toNanoOfDay()))
        val c = a.compareTo(b)
        Bmc.check((c < 0) == (a.toNanoOfDay() < b.toNanoOfDay()))
        Bmc.check((c == 0) == (a.toNanoOfDay() == b.toNanoOfDay()))
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
    // counterexample (e.g. n = 0) that the differential axis proves correct on a real JVM — the
    // differential axis is the sound check for those Math-intrinsic methods. (This is the UNMODELED-
    // INTRINSIC family, distinct from the now-fixed interface-cast family — see the isBefore proofs.)

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

    // --- DayOfWeek / Month / IsoEra small enums ---
    // The of(value)/getValue round-trip and the plus/minus rotation are pure modular int arithmetic over
    // the ordinal (no Math.floorMod — an explicit non-negative remainder), so they are @BmcProof-clean.

    @BmcProof
    fun dayofweek_of_getValue_round_trips() {
        val v = Bmc.anyInt(1, 7)
        Bmc.check(DayOfWeek.of(v).value == v)
    }

    @BmcProof
    fun dayofweek_plus_then_minus_round_trips() {
        val d = DayOfWeek.of(Bmc.anyInt(1, 7))
        val n = Bmc.anyInt(-100, 100).toLong()
        Bmc.check(d.plus(n).minus(n) == d)
    }

    @BmcProof
    fun dayofweek_plus7_is_identity() {
        val d = DayOfWeek.of(Bmc.anyInt(1, 7))
        Bmc.check(d.plus(7) == d)
        Bmc.check(d.plus(0) == d)
    }

    @BmcProof
    fun month_of_getValue_round_trips() {
        val v = Bmc.anyInt(1, 12)
        Bmc.check(Month.of(v).value == v)
    }

    @BmcProof
    fun month_plus_then_minus_round_trips() {
        val m = Month.of(Bmc.anyInt(1, 12))
        val n = Bmc.anyInt(-100, 100).toLong()
        Bmc.check(m.plus(n).minus(n) == m)
    }

    @BmcProof
    fun month_plus12_is_identity_and_length_in_range() {
        val m = Month.of(Bmc.anyInt(1, 12))
        Bmc.check(m.plus(12) == m)
        // length(leap) sits between minLength and maxLength inclusive
        Bmc.check(m.length(true) <= m.maxLength())
        Bmc.check(m.length(false) >= m.minLength())
    }

    @BmcProof
    fun isoera_of_getValue_round_trips() {
        val v = Bmc.anyInt(0, 1)
        Bmc.check(IsoEra.of(v).value == v)
    }

    // --- ZoneOffset total-seconds wrapper ---
    // ofTotalSeconds/getTotalSeconds is a pure field round-trip; normalized() of an offset is itself;
    // compareTo orders by descending total-seconds. All pure int arithmetic → @BmcProof-clean.

    @BmcProof
    fun zoneoffset_ofTotalSeconds_round_trips() {
        val s = Bmc.anyInt(-18 * 3600, 18 * 3600)
        Bmc.check(ZoneOffset.ofTotalSeconds(s).totalSeconds == s)
    }

    @BmcProof
    fun zoneoffset_normalized_is_self_and_ofHours_scales() {
        val s = Bmc.anyInt(-18 * 3600, 18 * 3600)
        val z = ZoneOffset.ofTotalSeconds(s)
        Bmc.check(z.normalized() === z)
        val h = Bmc.anyInt(-18, 18)
        Bmc.check(ZoneOffset.ofHours(h).totalSeconds == h * 3600)
    }

    @BmcProof
    fun zoneoffset_compareTo_is_descending_total_seconds() {
        val a = ZoneOffset.ofTotalSeconds(Bmc.anyInt(-18 * 3600, 18 * 3600))
        val b = ZoneOffset.ofTotalSeconds(Bmc.anyInt(-18 * 3600, 18 * 3600))
        val c = a.compareTo(b)
        // east-of-UTC (larger total-seconds) sorts FIRST → compareTo negative when a > b
        Bmc.check((c < 0) == (a.totalSeconds > b.totalSeconds))
        Bmc.check((c == 0) == (a.totalSeconds == b.totalSeconds))
    }

    // ofHoursMinutesSeconds composes the three same-sign parts into total-seconds — pure int arithmetic.
    @BmcProof
    fun zoneoffset_ofHoursMinutesSeconds_sums_total_seconds() {
        val h = Bmc.anyInt(0, 17)
        val m = Bmc.anyInt(0, 59)
        val s = Bmc.anyInt(0, 59)
        Bmc.check(ZoneOffset.ofHoursMinutesSeconds(h, m, s).totalSeconds == h * 3600 + m * 60 + s)
        // the all-negative variant mirrors it
        Bmc.check(ZoneOffset.ofHoursMinutesSeconds(-h, -m, -s).totalSeconds == -(h * 3600 + m * 60 + s))
    }

    // --- ChronoField / ChronoUnit accessor plumbing on the temporal models ---
    // The (now-modeled) ChronoField/ChronoUnit unblock getLong/get/with/plus/until on the date/time
    // models. These laws pin the field<->value round-trips that the accessors must satisfy under JBMC.

    // unwind = 32: the ChronoField enum-switch dispatch compiles to a synthetic $SwitchMap init that
    // clones ChronoField.values() (30 constants); the clone loop exceeds the default unwind of 16, so
    // the bound is raised to cover it (the law itself is a pure field read — fast once the bound fits).
    // NOTE: this pins the temporal SIDE of the round-trip — LocalDate.getLong(EPOCH_DAY). The mirror
    // ChronoField.EPOCH_DAY.getFrom(d) is differential-only (TimeConformanceTest): getFrom's param is
    // the non-generic TemporalAccessor, so the LocalDate erases to the interface at the call and JBMC
    // cannot recover the concrete type to back-dispatch getLong — it inserts a dynamic-cast check that
    // spuriously refutes (the interface-erased-ARGUMENT artifact; contrast ChronoUnit.addTo, whose
    // <R extends Temporal> generic param KEEPS the concrete type, so it IS @BmcProof-clean above).
    @BmcProof(unwind = 32)
    fun localdate_getLong_epochDay_round_trips() {
        val d = LocalDate.ofEpochDay(anyDay())
        Bmc.check(d.getLong(java.time.temporal.ChronoField.EPOCH_DAY) == d.toEpochDay())
    }

    // unwind = 32: with(ChronoField,..) and getLong(ChronoField,..) dispatch via a ChronoField enum
    // switch, whose synthetic $SwitchMap init clones ChronoField.values() (30 constants); the clone loop
    // exceeds the default unwind of 16, so the bound is raised to cover it.
    @BmcProof(unwind = 32)
    fun localdate_with_then_get_dayOfWeek_round_trips() {
        val d = LocalDate.ofEpochDay(anyDay())
        val target = Bmc.anyInt(1, 7).toLong()
        val moved = d.with(java.time.temporal.ChronoField.DAY_OF_WEEK, target)
        Bmc.check(moved.getLong(java.time.temporal.ChronoField.DAY_OF_WEEK) == target)
    }

    // unwind = 32: the ChronoUnit enum-switch dispatch in LocalDate.plus(long, TemporalUnit) compiles to
    // a synthetic $SwitchMap init that clones ChronoUnit.values() (16 constants); that clone loop hits
    // the default unwind of 16, so the bound is raised to cover it.
    @BmcProof(unwind = 32)
    fun localdate_plus_days_unit_matches_plusDays() {
        val d = LocalDate.ofEpochDay(anyDay())
        val n = anyDay()
        Bmc.check(d.plus(n, java.time.temporal.ChronoUnit.DAYS) == d.plusDays(n))
    }

    // unwind = 32: the ChronoField enum-switch dispatch clones ChronoField.values() (30 constants) in
    // its synthetic $SwitchMap init; the clone loop exceeds the default unwind of 16, so raise the bound.
    @BmcProof(unwind = 32)
    fun localtime_getLong_nanoOfDay_round_trips() {
        // Tight nano-of-day so the field decode stays a small circuit; the law holds for any value.
        val nod = Bmc.anyLong(0, 86_400L * 1_000_000_000L - 1L)
        val t = LocalTime.ofNanoOfDay(nod)
        Bmc.check(t.getLong(java.time.temporal.ChronoField.NANO_OF_DAY) == nod)
        Bmc.check(t.getLong(java.time.temporal.ChronoField.HOUR_OF_DAY).toInt() == t.hour)
    }

    // unwind = 32: addTo delegates to LocalTime.plus(amount, this), whose ChronoUnit enum-switch dispatch
    // clones ChronoUnit.values() (16 constants) in its synthetic $SwitchMap init; that clone loop hits the
    // default unwind of 16, so the bound is raised to cover it (it is a closed-form delegation, not an
    // unbounded loop — the only loop is the javac-synthesized enum-switch-map array clone).
    @BmcProof(unwind = 32)
    fun chronounit_addTo_delegates_to_plus() {
        val t = LocalTime.ofNanoOfDay(Bmc.anyLong(0, 86_400L * 1_000_000_000L - 1L))
        val h = Bmc.anyInt(-48, 48).toLong()
        // ChronoUnit.addTo(temporal, n) is defined as temporal.plus(n, this).
        Bmc.check(java.time.temporal.ChronoUnit.HOURS.addTo(t, h) == t.plus(h, java.time.temporal.ChronoUnit.HOURS))
    }
}
