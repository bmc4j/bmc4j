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
            // toSeconds FLOORS (== getSeconds); toMinutes/toHours/toDays then TRUNCATE the floored
            // seconds toward zero — the exact floor-vs-truncate split the harness caught on getSeconds.
            ra.toSeconds() shouldBe ma.toSeconds()
            ra.toMinutes() shouldBe ma.toMinutes()
            ra.toHours() shouldBe ma.toHours()
            ra.toDays() shouldBe ma.toDays()
            ra.isNegative shouldBe ma.isNegative()
            ra.isPositive shouldBe ma.isPositive()
            ra.isZero shouldBe ma.isZero()
            ra.plus(rb).toMillis() shouldBe ma.plus(mb).toMillis()
            ra.minus(rb).toMillis() shouldBe ma.minus(mb).toMillis()
            ra.plusMillis(b).toMillis() shouldBe ma.plusMillis(b).toMillis()
            ra.minusMillis(b).toMillis() shouldBe ma.minusMillis(b).toMillis()
            ra.plusSeconds(b / 1000).toMillis() shouldBe ma.plusSeconds(b / 1000).toMillis()
            ra.minusSeconds(b / 1000).toMillis() shouldBe ma.minusSeconds(b / 1000).toMillis()
            ra.plusMinutes(b / 60_000).toMillis() shouldBe ma.plusMinutes(b / 60_000).toMillis()
            ra.minusMinutes(b / 60_000).toMillis() shouldBe ma.minusMinutes(b / 60_000).toMillis()
            ra.plusHours(b / 3_600_000).toMillis() shouldBe ma.plusHours(b / 3_600_000).toMillis()
            ra.minusHours(b / 3_600_000).toMillis() shouldBe ma.minusHours(b / 3_600_000).toMillis()
            ra.plusDays(b / 86_400_000).toMillis() shouldBe ma.plusDays(b / 86_400_000).toMillis()
            ra.minusDays(b / 86_400_000).toMillis() shouldBe ma.minusDays(b / 86_400_000).toMillis()
            // multipliedBy: keep the multiplier small so the *millis result stays in range (the
            // out-of-bound saturation path is the loud-failure test below).
            ra.multipliedBy(b % 1000).toMillis() shouldBe ma.multipliedBy(b % 1000).toMillis()
            // toNanos: millis * 1e6 (in-bound here: |millis| <= 5e6 so the product stays well inside a long).
            ra.toNanos() shouldBe ma.toNanos()
            // dividedBy(long): truncates toward zero; use a nonzero divisor derived from b.
            val divisor = (b % 1000) + (if (b % 1000 == 0L) 1L else 0L)   // never zero
            ra.dividedBy(divisor).toMillis() shouldBe ma.dividedBy(divisor).toMillis()
            // dividedBy(Duration): how many times rb fits in ra, truncated toward zero (rb nonzero)
            val rbNz = java.time.Duration.ofMillis(divisor); val mbNz = bmcref.java.time.Duration.ofMillis(divisor)
            ra.dividedBy(rbNz) shouldBe ma.dividedBy(mbNz)
            ra.negated().toMillis() shouldBe ma.negated().toMillis()
            ra.abs().toMillis() shouldBe ma.abs().toMillis()
            Integer.signum(ra.compareTo(rb)) shouldBe Integer.signum(ma.compareTo(mb))   // contract is sign
            ra.equals(rb) shouldBe ma.equals(mb)
            // factories that scale a unit count into millis
            java.time.Duration.ofSeconds(a).toMillis() shouldBe bmcref.java.time.Duration.ofSeconds(a).toMillis()
            java.time.Duration.ofMinutes(a / 60).toMillis() shouldBe bmcref.java.time.Duration.ofMinutes(a / 60).toMillis()
            java.time.Duration.ofHours(a / 3600).toMillis() shouldBe bmcref.java.time.Duration.ofHours(a / 3600).toMillis()
            java.time.Duration.ofDays(a / 86400).toMillis() shouldBe bmcref.java.time.Duration.ofDays(a / 86400).toMillis()
            // between(start, end)
            val rBetween = java.time.Duration.between(java.time.Instant.ofEpochMilli(a), java.time.Instant.ofEpochMilli(b))
            val mBetween = bmcref.java.time.Duration.between(bmcref.java.time.Instant.ofEpochMilli(a), bmcref.java.time.Instant.ofEpochMilli(b))
            rBetween.toMillis() shouldBe mBetween.toMillis()
        }
    }

    // --- Duration OUT-OF-DOMAIN: unit→millis scaling and multipliedBy past the millis bound --------
    //
    // ofMinutes/ofHours/ofDays/plus*/multipliedBy route their scale through Math.multiplyExact, so a
    // count whose *millis leaves the long range fails LOUDLY (the millis backing is narrower than the
    // real seconds+nanos Duration). This is bounded-model loud-failure, NOT JDK parity.
    test("Duration unit factories fail LOUDLY past the millis bound") {
        // ofDays: *86_400_000 overflows a long for day counts past ~1.067e11; the seconds-backed JDK
        // accepts these (Duration.ofDays(Long.MAX/86400) is fine). Stay below the JDK's own
        // ofSeconds-overflow so this is a MODEL-only out-of-bound band.
        val bigDays = Arb.long(Long.MAX_VALUE / 86_400_000L + 1L..Long.MAX_VALUE / 86_400L)
        checkAll(bigDays) { d ->
            java.time.Duration.ofDays(d)   // JDK (seconds-backed) succeeds
            runCatching { bmcref.java.time.Duration.ofDays(d) }
                .exceptionOrNull().shouldBeInstanceOf<ArithmeticException>()
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

    test("Duration.dividedBy(0) throws ArithmeticException (JDK parity)") {
        checkAll(ms) { a ->
            val real = runCatching { java.time.Duration.ofMillis(a).dividedBy(0L) }
            val model = runCatching { bmcref.java.time.Duration.ofMillis(a).dividedBy(0L) }
            real.exceptionOrNull().shouldBeInstanceOf<ArithmeticException>()
            model.exceptionOrNull().shouldBeInstanceOf<ArithmeticException>()
        }
    }

    test("Duration.dividedBy(ZERO Duration) throws ArithmeticException (JDK parity)") {
        checkAll(ms) { a ->
            val real = runCatching { java.time.Duration.ofMillis(a).dividedBy(java.time.Duration.ZERO) }
            val model = runCatching { bmcref.java.time.Duration.ofMillis(a).dividedBy(bmcref.java.time.Duration.ofMillis(0L)) }
            real.exceptionOrNull().shouldBeInstanceOf<ArithmeticException>()
            model.exceptionOrNull().shouldBeInstanceOf<ArithmeticException>()
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
            // weeks = 7 days; keep the week count small enough that *7 stays in range (the loud
            // overflow path past long/7 is a separate bounded-model concern, not JDK parity).
            ra.plusWeeks(b / 7).toEpochDay() shouldBe ma.plusWeeks(b / 7).toEpochDay()
            ra.minusWeeks(b / 7).toEpochDay() shouldBe ma.minusWeeks(b / 7).toEpochDay()
            ra.equals(rb) shouldBe ma.equals(mb)
            ra.isEqual(rb) shouldBe ma.isEqual(mb)
            // calendar fields decoded from the epoch-day (day-of-year, month/year lengths, leap flag)
            ra.dayOfYear shouldBe ma.getDayOfYear()
            ra.lengthOfMonth() shouldBe ma.lengthOfMonth()
            ra.lengthOfYear() shouldBe ma.lengthOfYear()
            ra.isLeapYear shouldBe ma.isLeapYear()
        }
    }

    // --- LocalDate.ofYearDay + with* field setters (clamp on with{Year,Month}, strict elsewhere) -----
    //
    // ofYearDay decomposes a 1-based day-of-year (incl. day 366 in leap years) into y/m/d; the with*
    // setters clamp the day for withYear/withMonth (resolvePreviousValid) but validate strictly for
    // withDayOfMonth/withDayOfYear. Drive symbolic years (covering leap cycles) + out-of-range fields so
    // both the value parity AND the loud-exception parity are tested vs the real JDK.
    val yearAny = Arb.int(1900..2100)
    val doyLike = Arb.int(-1..367)          // out of bounds to test exception parity
    val monthSet = Arb.int(0..13)
    val domSet = Arb.int(0..32)

    test("LocalDate.ofYearDay exception parity + value (leap day 366)") {
        checkAll(yearAny, doyLike) { y, doy ->
            val real = runCatching { java.time.LocalDate.ofYearDay(y, doy) }
            val model = runCatching { bmcref.java.time.LocalDate.ofYearDay(y, doy) }
            assertSameException(real, model)
            if (real.isSuccess && model.isSuccess) {
                real.getOrThrow().toEpochDay() shouldBe (model.getOrThrow() as bmcref.java.time.LocalDate).toEpochDay()
            }
        }
    }

    test("LocalDate.with* conforms (clamp + strict validation parity)") {
        checkAll(days, yearAny, monthSet, domSet) { e, y, mo, dom ->
            val ra = java.time.LocalDate.ofEpochDay(e)
            val ma = bmcref.java.time.LocalDate.ofEpochDay(e)
            // withYear / withMonth clamp the day-of-month; compare resulting epoch-day, with exception parity
            assertEquivalent("withYear", runCatching { ra.withYear(y).toEpochDay() }, runCatching { ma.withYear(y).toEpochDay() })
            assertEquivalent("withMonth", runCatching { ra.withMonth(mo).toEpochDay() }, runCatching { ma.withMonth(mo).toEpochDay() })
            // withDayOfMonth / withDayOfYear validate strictly (throw on a day past the month/year length)
            assertEquivalent("withDayOfMonth", runCatching { ra.withDayOfMonth(dom).toEpochDay() }, runCatching { ma.withDayOfMonth(dom).toEpochDay() })
        }
    }

    test("LocalDate.withDayOfYear conforms (strict; 366 only in leap years)") {
        checkAll(days, doyLike) { e, doy ->
            val ra = java.time.LocalDate.ofEpochDay(e)
            val ma = bmcref.java.time.LocalDate.ofEpochDay(e)
            assertEquivalent("withDayOfYear", runCatching { ra.withDayOfYear(doy).toEpochDay() }, runCatching { ma.withDayOfYear(doy).toEpochDay() })
        }
    }

    test("LocalDate.atTime / atStartOfDay / until composition conforms") {
        val validH = Arb.int(0..23); val validM = Arb.int(0..59); val validS = Arb.int(0..59)
        checkAll(days, days, validH, validM) { a, b, h, mi ->
            val ra = java.time.LocalDate.ofEpochDay(a); val rb = java.time.LocalDate.ofEpochDay(b)
            val ma = bmcref.java.time.LocalDate.ofEpochDay(a); val mb = bmcref.java.time.LocalDate.ofEpochDay(b)
            // atStartOfDay: same date, time = 00:00
            ra.atStartOfDay().let { r -> ma.atStartOfDay().let { m ->
                r.toLocalDate().toEpochDay() shouldBe m.toLocalDate().toEpochDay()
                r.toLocalTime().toNanoOfDay() shouldBe m.toLocalTime().toNanoOfDay()
            } }
            // atTime(h, mi)
            ra.atTime(h, mi).let { r -> ma.atTime(h, mi).let { m ->
                r.toLocalDate().toEpochDay() shouldBe m.toLocalDate().toEpochDay()
                r.toLocalTime().toNanoOfDay() shouldBe m.toLocalTime().toNanoOfDay()
            } }
            // atTime(LocalTime)
            val rt = java.time.LocalTime.of(h, mi); val mt = bmcref.java.time.LocalTime.of(h, mi)
            ra.atTime(rt).toLocalTime().toNanoOfDay() shouldBe ma.atTime(mt).toLocalTime().toNanoOfDay()
            // until(endExclusive) -> Period (delegates to Period.between)
            val rp = ra.until(rb); val mp = ma.until(mb)
            rp.years shouldBe mp.getYears()
            rp.months shouldBe mp.getMonths()
            rp.days shouldBe mp.getDays()
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
            // plusNanos/minusNanos wrap within the day; scale the shift into nanos so the symbolic
            // count spans many wraps (the day is ~8.64e13 nanos; *1e9 keeps it well inside a long).
            rt.plusNanos(sh * 1_000_000_000L).toNanoOfDay() shouldBe mt.plusNanos(sh * 1_000_000_000L).toNanoOfDay()
            rt.minusNanos(sh * 1_000_000_000L).toNanoOfDay() shouldBe mt.minusNanos(sh * 1_000_000_000L).toNanoOfDay()
            rt.plusNanos(sh).toNanoOfDay() shouldBe mt.plusNanos(sh).toNanoOfDay()   // sub-second nanos too
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

    test("LocalTime.with* conforms (field validation parity) + atDate") {
        val validH = Arb.int(0..23); val validM = Arb.int(0..59); val validS = Arb.int(0..59)
        val nanoValid = Arb.int(0..999_999_999)
        checkAll(validH, validM, validS, hourLike) { h, mi, s, newH ->
            val rt = java.time.LocalTime.of(h, mi, s, 123)
            val mt = bmcref.java.time.LocalTime.of(h, mi, s, 123)
            // withHour ranges out of bounds -> exception parity; the others stay valid
            assertEquivalent("withHour", runCatching { rt.withHour(newH).toNanoOfDay() }, runCatching { mt.withHour(newH).toNanoOfDay() })
            rt.withMinute(mi).toNanoOfDay() shouldBe mt.withMinute(mi).toNanoOfDay()
            rt.withSecond(s).toNanoOfDay() shouldBe mt.withSecond(s).toNanoOfDay()
            // atDate composes with a fixed date
            val rd = java.time.LocalDate.ofEpochDay(0L); val md = bmcref.java.time.LocalDate.ofEpochDay(0L)
            rt.atDate(rd).toLocalTime().toNanoOfDay() shouldBe mt.atDate(md).toLocalTime().toNanoOfDay()
        }
        checkAll(validH, validM, validS, nanoValid) { h, mi, s, n ->
            val rt = java.time.LocalTime.of(h, mi, s)
            val mt = bmcref.java.time.LocalTime.of(h, mi, s)
            rt.withNano(n).toNanoOfDay() shouldBe mt.withNano(n).toNanoOfDay()
        }
        // out-of-range nano -> loud exception parity
        checkAll(Arb.int(1_000_000_000..1_000_000_010)) { n ->
            val r = runCatching { java.time.LocalTime.of(0, 0).withNano(n) }
            val m = runCatching { bmcref.java.time.LocalTime.of(0, 0).withNano(n) }
            assertSameException(r, m)
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
                r.dayOfYear shouldBe m.getDayOfYear()
            }
        }
    }

    test("LocalDateTime.plusWeeks/minusWeeks + isEqual + with* conforms") {
        val validH = Arb.int(0..23); val validM = Arb.int(0..59)
        val newDom = Arb.int(0..32)
        checkAll(yearLike, validH, validM, shift) { y, h, mi, sh ->
            val r = java.time.LocalDateTime.of(y, 6, 15, h, mi, 30)
            val m = bmcref.java.time.LocalDateTime.of(y, 6, 15, h, mi, 30)
            // plusWeeks/minusWeeks == plusDays(*7) on the date part, time unchanged
            r.plusWeeks(sh / 7).toLocalDate().toEpochDay() shouldBe m.plusWeeks(sh / 7).toLocalDate().toEpochDay()
            r.minusWeeks(sh / 7).toLocalDate().toEpochDay() shouldBe m.minusWeeks(sh / 7).toLocalDate().toEpochDay()
            r.plusWeeks(sh / 7).toLocalTime().toNanoOfDay() shouldBe m.plusWeeks(sh / 7).toLocalTime().toNanoOfDay()
            // isEqual against a shifted instance
            val r2 = r.plusMinutes(sh % 1440); val m2 = m.plusMinutes(sh % 1440)
            r.isEqual(r2) shouldBe m.isEqual(m2)
            // time-part with* (validated; keep in range here)
            r.withHour(h).toLocalTime().toNanoOfDay() shouldBe m.withHour(h).toLocalTime().toNanoOfDay()
            r.withMinute(mi).toLocalTime().toNanoOfDay() shouldBe m.withMinute(mi).toLocalTime().toNanoOfDay()
            r.withSecond(0).toLocalTime().toNanoOfDay() shouldBe m.withSecond(0).toLocalTime().toNanoOfDay()
            r.withNano(7).toLocalTime().toNanoOfDay() shouldBe m.withNano(7).toLocalTime().toNanoOfDay()
            // date-part with* (clamp on year/month, strict on day) — compare resulting epoch-day + exception parity
            r.withYear(y).toLocalDate().toEpochDay() shouldBe m.withYear(y).toLocalDate().toEpochDay()
        }
        // date-part with* clamp + strict validation across symbolic month/day, driven from Jan 31 (clamp source)
        checkAll(Arb.int(1..12), newDom) { mo, dom ->
            val r = java.time.LocalDateTime.of(2024, 1, 31, 8, 0, 0)
            val m = bmcref.java.time.LocalDateTime.of(2024, 1, 31, 8, 0, 0)
            assertEquivalent("ldt.withMonth", runCatching { r.withMonth(mo).toLocalDate().toEpochDay() }, runCatching { m.withMonth(mo).toLocalDate().toEpochDay() })
            assertEquivalent("ldt.withDayOfMonth", runCatching { r.withDayOfMonth(dom).toLocalDate().toEpochDay() }, runCatching { m.withDayOfMonth(dom).toLocalDate().toEpochDay() })
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
            // with* field setters (replace one field, keep the others)
            rp.withYears(k % 1000).years shouldBe mp.withYears(k % 1000).getYears()
            rp.withMonths(k % 1000).months shouldBe mp.withMonths(k % 1000).getMonths()
            rp.withDays(k % 1000).days shouldBe mp.withDays(k % 1000).getDays()
            // multipliedBy a small scalar (keep the product well inside int so this is JDK parity, not
            // the loud-overflow path); each field scales
            val scalar = (k % 10).toInt()
            rp.multipliedBy(scalar).years shouldBe mp.multipliedBy(scalar).getYears()
            rp.multipliedBy(scalar).months shouldBe mp.multipliedBy(scalar).getMonths()
            rp.multipliedBy(scalar).days shouldBe mp.multipliedBy(scalar).getDays()
            // equals + factories
            java.time.Period.ofDays(d).days shouldBe bmcref.java.time.Period.ofDays(d).getDays()
            java.time.Period.ofWeeks(k % 1000).days shouldBe bmcref.java.time.Period.ofWeeks(k % 1000).getDays()
        }
    }

    // Period.multipliedBy loud-overflow parity: a field * scalar that leaves the int range must throw
    // ArithmeticException on BOTH sides (the JDK uses Math.multiplyExact too).
    test("Period.multipliedBy fails LOUDLY on int overflow (JDK parity)") {
        checkAll(Arb.int(2..1000)) { s ->
            val big = Integer.MAX_VALUE / s + 1
            val real = runCatching { java.time.Period.ofDays(big).multipliedBy(s) }
            val model = runCatching { bmcref.java.time.Period.ofDays(big).multipliedBy(s) }
            assertSameException(real, model)
            real.exceptionOrNull().shouldBeInstanceOf<ArithmeticException>()
        }
    }
})
