package conformance

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll

/**
 * Differential conformance for the java.time epoch-primitive models. Values include negatives
 * (before the epoch), which is where floor-vs-truncate division bugs hide. Both sides are typed, so
 * methods are called directly and observables (the backing long, booleans) compared.
 *
 * compareTo is compared by SIGN, not exact value: that's all Comparable guarantees, and real
 * LocalDate.compareTo returns a calendar-field difference (e.g. -2 for two days apart) that an
 * epoch-day model can't and shouldn't reproduce. Comparing exact compareTo magnitudes would test a
 * contract neither implementation makes.
 */
class TimeConformanceTest : FunSpec({

    val ms = Arb.long(-5_000_000L..5_000_000L)
    val days = Arb.long(-100_000L..100_000L)

    test("Instant conforms") {
        checkAll(ms, ms) { a, b ->
            val ra = java.time.Instant.ofEpochMilli(a); val rb = java.time.Instant.ofEpochMilli(b)
            val ma = bmcref.java.time.Instant.ofEpochMilli(a); val mb = bmcref.java.time.Instant.ofEpochMilli(b)
            ra.toEpochMilli() shouldBe ma.toEpochMilli()
            ra.epochSecond shouldBe ma.getEpochSecond()        // floor vs truncate for negatives
            ra.isBefore(rb) shouldBe ma.isBefore(mb)
            ra.isAfter(rb) shouldBe ma.isAfter(mb)
            Integer.signum(ra.compareTo(rb)) shouldBe Integer.signum(ma.compareTo(mb))   // contract is sign
            ra.plusMillis(b).toEpochMilli() shouldBe ma.plusMillis(b).toEpochMilli()
            ra.minusMillis(b).toEpochMilli() shouldBe ma.minusMillis(b).toEpochMilli()
            ra.plusSeconds(b / 1000).toEpochMilli() shouldBe ma.plusSeconds(b / 1000).toEpochMilli()
            ra.equals(rb) shouldBe ma.equals(mb)
        }
    }

    test("Duration conforms") {
        checkAll(ms, ms) { a, b ->
            val ra = java.time.Duration.ofMillis(a); val rb = java.time.Duration.ofMillis(b)
            val ma = bmcref.java.time.Duration.ofMillis(a); val mb = bmcref.java.time.Duration.ofMillis(b)
            ra.toMillis() shouldBe ma.toMillis()
            ra.seconds shouldBe ma.getSeconds()                // floor vs truncate for negatives
            ra.isNegative shouldBe ma.isNegative()
            ra.isZero shouldBe ma.isZero()
            ra.plus(rb).toMillis() shouldBe ma.plus(mb).toMillis()
            ra.minus(rb).toMillis() shouldBe ma.minus(mb).toMillis()
            ra.negated().toMillis() shouldBe ma.negated().toMillis()
            Integer.signum(ra.compareTo(rb)) shouldBe Integer.signum(ma.compareTo(mb))   // contract is sign
            // between(start, end)
            val rBetween = java.time.Duration.between(java.time.Instant.ofEpochMilli(a), java.time.Instant.ofEpochMilli(b))
            val mBetween = bmcref.java.time.Duration.between(bmcref.java.time.Instant.ofEpochMilli(a), bmcref.java.time.Instant.ofEpochMilli(b))
            rBetween.toMillis() shouldBe mBetween.toMillis()
        }
    }

    // --- OUT-OF-DOMAIN: seconds->millis scaling (Instant.ofEpochSecond / Duration.ofSeconds) -------
    //
    // These models are DELIBERATELY millis-bounded (narrower than the real Instant/Duration, which are
    // seconds-backed). Within the bound, ofEpochSecond/ofSeconds must match the JDK exactly. BEYOND it
    // (a second count whose *1000 millis leaves the long range), the model must FAIL LOUDLY via the
    // checked multiply rather than silently wrapping. This is bounded-model loud-failure, NOT JDK
    // parity: the seconds-backed JDK SUCCEEDS on the same large second count.
    val secInBound = Arb.long(-5_000_000L..5_000_000L)   // *1000 stays well inside long
    test("Instant.ofEpochSecond / Duration.ofSeconds conform in-bound") {
        checkAll(secInBound) { s ->
            java.time.Instant.ofEpochSecond(s).toEpochMilli() shouldBe
                bmcref.java.time.Instant.ofEpochSecond(s).toEpochMilli()
            java.time.Duration.ofSeconds(s).toMillis() shouldBe
                bmcref.java.time.Duration.ofSeconds(s).toMillis()
        }
    }

    test("Instant.ofEpochSecond / Duration.ofSeconds fail LOUDLY past the millis bound") {
        // Second counts whose *1000 overflows a long but stay within the real JDK's (seconds-backed)
        // range: Long.MAX/1000 < s <= Instant.MAX.getEpochSecond() (31_556_889_864_403_199). The
        // seconds-backed JDK accepts these; the millis-bounded model must throw (checked multiply)
        // instead of silently wrapping. (Above Instant.MAX the JDK itself throws DateTimeException,
        // so we cap the band at Instant.MAX to keep this a MODEL-only out-of-bound region.)
        val secOverflow = Arb.long(Long.MAX_VALUE / 1000L + 1L..31_556_889_864_403_199L)
        checkAll(secOverflow) { s ->
            // JDK succeeds (seconds-backed) — confirms we're genuinely past the MODEL's bound only.
            java.time.Instant.ofEpochSecond(s)
            java.time.Duration.ofSeconds(s)
            val mi = runCatching { bmcref.java.time.Instant.ofEpochSecond(s) }
            val md = runCatching { bmcref.java.time.Duration.ofSeconds(s) }
            withClue("model Instant.ofEpochSecond overflow should throw") {
                mi.exceptionOrNull().shouldBeInstanceOf<ArithmeticException>()
            }
            withClue("model Duration.ofSeconds overflow should throw") {
                md.exceptionOrNull().shouldBeInstanceOf<ArithmeticException>()
            }
        }
    }

    test("LocalDate conforms") {
        checkAll(days, days) { a, b ->
            val ra = java.time.LocalDate.ofEpochDay(a); val rb = java.time.LocalDate.ofEpochDay(b)
            val ma = bmcref.java.time.LocalDate.ofEpochDay(a); val mb = bmcref.java.time.LocalDate.ofEpochDay(b)
            ra.toEpochDay() shouldBe ma.toEpochDay()
            ra.isBefore(rb) shouldBe ma.isBefore(mb)
            ra.isAfter(rb) shouldBe ma.isAfter(mb)
            Integer.signum(ra.compareTo(rb)) shouldBe Integer.signum(ma.compareTo(mb))   // contract is sign
            ra.plusDays(b).toEpochDay() shouldBe ma.plusDays(b).toEpochDay()
            ra.minusDays(b).toEpochDay() shouldBe ma.minusDays(b).toEpochDay()
            ra.equals(rb) shouldBe ma.equals(mb)
        }
    }

    // --- LocalDate calendar-month arithmetic (the day-of-month CLAMP rule) ---
    //
    // The clamp (2024-01-31 plusMonths 1 = 2024-02-29; 2024-02-29 plusYears 1 = 2025-02-28) is the
    // easy thing to get subtly wrong, so we drive symbolic start dates (incl. month-ends, leap days,
    // negatives = before the epoch) with large +/- month/year deltas and compare the resulting
    // epoch-day vs the real JDK. ANY divergence here = the model is unsound.
    val monthDelta = Arb.long(-4000L..4000L)   // +/- a few thousand months covers many leap cycles
    val yearDelta = Arb.long(-400L..400L)

    test("LocalDate.plusMonths/minusMonths conforms (day-of-month clamp)") {
        checkAll(days, monthDelta) { e, n ->
            val ra = java.time.LocalDate.ofEpochDay(e)
            val ma = bmcref.java.time.LocalDate.ofEpochDay(e)
            ra.plusMonths(n).toEpochDay() shouldBe ma.plusMonths(n).toEpochDay()
            ra.minusMonths(n).toEpochDay() shouldBe ma.minusMonths(n).toEpochDay()
            // calendar field accessors round-trip too
            ra.year shouldBe ma.getYear()
            ra.monthValue shouldBe ma.getMonthValue()
            ra.dayOfMonth shouldBe ma.getDayOfMonth()
        }
    }

    test("LocalDate.plusYears/minusYears conforms (leap-day clamp)") {
        checkAll(days, yearDelta) { e, n ->
            val ra = java.time.LocalDate.ofEpochDay(e)
            val ma = bmcref.java.time.LocalDate.ofEpochDay(e)
            ra.plusYears(n).toEpochDay() shouldBe ma.plusYears(n).toEpochDay()
            ra.minusYears(n).toEpochDay() shouldBe ma.minusYears(n).toEpochDay()
        }
    }

    test("Period.between(LocalDate, LocalDate) conforms (y/m/d decomposition)") {
        checkAll(days, days) { a, b ->
            val rStart = java.time.LocalDate.ofEpochDay(a); val rEnd = java.time.LocalDate.ofEpochDay(b)
            val mStart = bmcref.java.time.LocalDate.ofEpochDay(a); val mEnd = bmcref.java.time.LocalDate.ofEpochDay(b)
            val rp = java.time.Period.between(rStart, rEnd)
            val mp = bmcref.java.time.Period.between(mStart, mEnd)
            rp.years shouldBe mp.getYears()
            rp.months shouldBe mp.getMonths()
            rp.days shouldBe mp.getDays()
        }
    }

    // --- LocalTime ---

    // Field generators that range slightly OUT of valid bounds so of(...) exception parity is tested.
    val hourLike = Arb.int(-2..25)
    val minSecLike = Arb.int(-2..61)
    val nanoLike = Arb.int(-2..1_000_000_001)
    val shift = Arb.long(-200_000L..200_000L)   // hours/minutes/seconds to add (wraps within the day)

    test("LocalTime.of exception parity (field validation)") {
        checkAll(hourLike, minSecLike, minSecLike, nanoLike) { h, mi, s, n ->
            val real = runCatching { java.time.LocalTime.of(h, mi, s, n) }
            val model = runCatching { bmcref.java.time.LocalTime.of(h, mi, s, n) }
            assertSameException(real, model)
            if (real.isSuccess && model.isSuccess) {
                val rt = real.getOrThrow(); val mt = model.getOrThrow()
                rt.hour shouldBe mt.getHour()
                rt.minute shouldBe mt.getMinute()
                rt.second shouldBe mt.getSecond()
                rt.nano shouldBe mt.getNano()
                rt.toSecondOfDay() shouldBe mt.toSecondOfDay()
                rt.toNanoOfDay() shouldBe mt.toNanoOfDay()
            }
        }
    }

    test("LocalTime arithmetic + ordering conforms") {
        val validH = Arb.int(0..23); val validM = Arb.int(0..59); val validS = Arb.int(0..59)
        checkAll(validH, validM, validS, shift) { h, mi, s, sh ->
            val rt = java.time.LocalTime.of(h, mi, s)
            val mt = bmcref.java.time.LocalTime.of(h, mi, s)
            rt.plusHours(sh).toNanoOfDay() shouldBe mt.plusHours(sh).toNanoOfDay()
            rt.minusHours(sh).toNanoOfDay() shouldBe mt.minusHours(sh).toNanoOfDay()
            rt.plusMinutes(sh).toNanoOfDay() shouldBe mt.plusMinutes(sh).toNanoOfDay()
            rt.minusMinutes(sh).toNanoOfDay() shouldBe mt.minusMinutes(sh).toNanoOfDay()
            rt.plusSeconds(sh).toNanoOfDay() shouldBe mt.plusSeconds(sh).toNanoOfDay()
            rt.minusSeconds(sh).toNanoOfDay() shouldBe mt.minusSeconds(sh).toNanoOfDay()
            // ordering vs a second time built from the shift
            val sec2 = ((sh % 86400) + 86400) % 86400
            val rt2 = java.time.LocalTime.ofSecondOfDay(sec2)
            val mt2 = bmcref.java.time.LocalTime.ofSecondOfDay(sec2)
            rt.isBefore(rt2) shouldBe mt.isBefore(mt2)
            rt.isAfter(rt2) shouldBe mt.isAfter(mt2)
            Integer.signum(rt.compareTo(rt2)) shouldBe Integer.signum(mt.compareTo(mt2))
            rt.equals(rt2) shouldBe mt.equals(mt2)
        }
    }

    // --- LocalDateTime ---

    val yearLike = Arb.int(1900..2100)          // a wide-but-fast range covering leap-year edge cases
    val monthLike = Arb.int(-1..14)             // out of bounds to test of() exception parity
    val domLike = Arb.int(-1..32)

    test("LocalDateTime.of exception parity + field round-trip (calendar conversion)") {
        val validH = Arb.int(0..23); val validM = Arb.int(0..59); val validS = Arb.int(0..59)
        checkAll(yearLike, monthLike, domLike, validH, validM) { y, mo, d, h, mi ->
            val real = runCatching { java.time.LocalDateTime.of(y, mo, d, h, mi) }
            val model = runCatching { bmcref.java.time.LocalDateTime.of(y, mo, d, h, mi) }
            assertSameException(real, model)
            if (real.isSuccess && model.isSuccess) {
                val r = real.getOrThrow(); val m = model.getOrThrow()
                r.year shouldBe m.getYear()
                r.monthValue shouldBe m.getMonthValue()
                r.dayOfMonth shouldBe m.getDayOfMonth()
                r.hour shouldBe m.getHour()
                r.minute shouldBe m.getMinute()
                r.second shouldBe m.getSecond()
                r.toLocalDate().toEpochDay() shouldBe m.toLocalDate().toEpochDay()
                r.toLocalTime().toNanoOfDay() shouldBe m.toLocalTime().toNanoOfDay()
            }
        }
    }

    test("LocalDateTime day/time arithmetic + ordering conforms") {
        val validH = Arb.int(0..23); val validM = Arb.int(0..59); val validS = Arb.int(0..59)
        checkAll(yearLike, validH, validM, shift) { y, h, mi, sh ->
            // Use a fixed valid date (15th of a mid-year month) + symbolic year so conversion is exercised.
            val r = java.time.LocalDateTime.of(y, 6, 15, h, mi, 0)
            val m = bmcref.java.time.LocalDateTime.of(y, 6, 15, h, mi, 0)
            r.plusDays(sh).let { rr -> m.plusDays(sh).let { mm ->
                rr.toLocalDate().toEpochDay() shouldBe mm.toLocalDate().toEpochDay()
                rr.toLocalTime().toNanoOfDay() shouldBe mm.toLocalTime().toNanoOfDay()
            } }
            r.plusHours(sh).toLocalTime().toNanoOfDay() shouldBe m.plusHours(sh).toLocalTime().toNanoOfDay()
            r.plusHours(sh).toLocalDate().toEpochDay() shouldBe m.plusHours(sh).toLocalDate().toEpochDay()
            r.plusMinutes(sh).toLocalTime().toNanoOfDay() shouldBe m.plusMinutes(sh).toLocalTime().toNanoOfDay()
            r.plusMinutes(sh).toLocalDate().toEpochDay() shouldBe m.plusMinutes(sh).toLocalDate().toEpochDay()
            r.plusSeconds(sh).toLocalTime().toNanoOfDay() shouldBe m.plusSeconds(sh).toLocalTime().toNanoOfDay()
            r.plusSeconds(sh).toLocalDate().toEpochDay() shouldBe m.plusSeconds(sh).toLocalDate().toEpochDay()
            // ordering against a shifted instance
            val r2 = r.plusMinutes(sh); val m2 = m.plusMinutes(sh)
            r.isBefore(r2) shouldBe m.isBefore(m2)
            r.isAfter(r2) shouldBe m.isAfter(m2)
            Integer.signum(r.compareTo(r2)) shouldBe Integer.signum(m.compareTo(m2))
            r.equals(r2) shouldBe m.equals(m2)
        }
    }

    test("LocalDateTime.plusMonths/plusYears conforms (clamp; time part unchanged)") {
        val validH = Arb.int(0..23); val validM = Arb.int(0..59)
        // Drive day-of-month including month-ends so the clamp is exercised; year covers leap cycles.
        val domAny = Arb.int(1..28)   // valid in every month; the clamp boundary is hit via month-ends below
        checkAll(yearLike, validH, validM, monthDelta) { y, h, mi, n ->
            // Use Jan 31 (a clamp source for short target months) and Feb 29 in a leap year.
            val rJan = java.time.LocalDateTime.of(2024, 1, 31, h, mi, 0)
            val mJan = bmcref.java.time.LocalDateTime.of(2024, 1, 31, h, mi, 0)
            rJan.plusMonths(n).toLocalDate().toEpochDay() shouldBe mJan.plusMonths(n).toLocalDate().toEpochDay()
            rJan.plusMonths(n).toLocalTime().toNanoOfDay() shouldBe mJan.plusMonths(n).toLocalTime().toNanoOfDay()
            rJan.minusMonths(n).toLocalDate().toEpochDay() shouldBe mJan.minusMonths(n).toLocalDate().toEpochDay()

            val rFeb = java.time.LocalDateTime.of(2024, 2, 29, h, mi, 0)
            val mFeb = bmcref.java.time.LocalDateTime.of(2024, 2, 29, h, mi, 0)
            val yd = (n % 400)
            rFeb.plusYears(yd).toLocalDate().toEpochDay() shouldBe mFeb.plusYears(yd).toLocalDate().toEpochDay()
            rFeb.plusYears(yd).toLocalTime().toNanoOfDay() shouldBe mFeb.plusYears(yd).toLocalTime().toNanoOfDay()
            rFeb.minusYears(yd).toLocalDate().toEpochDay() shouldBe mFeb.minusYears(yd).toLocalDate().toEpochDay()

            // symbolic year/time, fixed mid-month date, mixed deltas
            val r = java.time.LocalDateTime.of(y, 6, 15, h, mi, 0)
            val m = bmcref.java.time.LocalDateTime.of(y, 6, 15, h, mi, 0)
            r.plusMonths(n).toLocalDate().toEpochDay() shouldBe m.plusMonths(n).toLocalDate().toEpochDay()
            r.plusYears(yd).toLocalDate().toEpochDay() shouldBe m.plusYears(yd).toLocalDate().toEpochDay()
        }
    }

    // --- Period ---

    val pField = Arb.int(-10_000..10_000)

    test("Period accessors + arithmetic + normalized conforms") {
        checkAll(pField, pField, pField, pField) { y, mo, d, k ->
            val rp = java.time.Period.of(y, mo, d)
            val mp = bmcref.java.time.Period.of(y, mo, d)
            rp.years shouldBe mp.getYears()
            rp.months shouldBe mp.getMonths()
            rp.days shouldBe mp.getDays()
            rp.toTotalMonths() shouldBe mp.toTotalMonths()
            rp.isZero shouldBe mp.isZero()
            rp.isNegative shouldBe mp.isNegative()
            // plus/minus (use a bounded delta to avoid contrived int-overflow noise)
            val delta = (k % 1000).toLong()
            rp.plusDays(delta).days shouldBe mp.plusDays(delta).getDays()
            rp.plusMonths(delta).months shouldBe mp.plusMonths(delta).getMonths()
            rp.plusYears(delta).years shouldBe mp.plusYears(delta).getYears()
            rp.minusDays(delta).days shouldBe mp.minusDays(delta).getDays()
            // negated
            rp.negated().years shouldBe mp.negated().getYears()
            rp.negated().months shouldBe mp.negated().getMonths()
            rp.negated().days shouldBe mp.negated().getDays()
            // normalized: years + months in [-11,11], same total months
            val rn = rp.normalized(); val mn = mp.normalized()
            rn.years shouldBe mn.getYears()
            rn.months shouldBe mn.getMonths()
            rn.days shouldBe mn.getDays()
            // equals + factories
            java.time.Period.ofDays(d).days shouldBe bmcref.java.time.Period.ofDays(d).getDays()
            java.time.Period.ofWeeks(k % 1000).days shouldBe bmcref.java.time.Period.ofWeeks(k % 1000).getDays()
        }
    }
})
