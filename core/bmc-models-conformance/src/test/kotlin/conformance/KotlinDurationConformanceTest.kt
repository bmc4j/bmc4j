package conformance

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toDuration

/**
 * Differential conformance for the `kotlin.time.Duration` value-class model vs the real
 * `kotlin.time.Duration` on the JVM. The model is relocated to `bmcref.kotlin.time.Duration`; its public
 * surface is the value-class ABI (static methods over the bit-packed `long rawValue`, with mangled names
 * like `plus-LRDsOJo` that aren't legal Kotlin identifiers), so the model is driven by reflection on those
 * methods and compared against the real Duration built/observed through normal Kotlin syntax.
 *
 * Because the model reproduces the real bit-packing exactly, the comparison is two-level: (1) the packed
 * `rawValue` itself matches (read from the real Duration via reflection on its private `rawValue` field),
 * and (2) every public observable (`inWhole*`, `compareTo`, `isNegative`) matches. Covers construction from
 * units, `+`/`-`, comparison, `inWhole*`, negatives, and the nanos/millis saturation boundary.
 */
class KotlinDurationConformanceTest : FunSpec({

    val modelDuration = Class.forName("bmcref.kotlin.time.Duration")
    val modelUnit = Class.forName("bmcref.kotlin.time.DurationUnit")

    fun modelUnitValue(name: String): Any =
        modelUnit.getMethod("valueOf", String::class.java).invoke(null, name)

    fun mGet(name: String, vararg params: Class<*>) = modelDuration.getMethod(name, *params)

    // Static ABI methods (renamed in the model build to match the stdlib's mangled names).
    val mToDuration = mGet("toDuration", java.lang.Long.TYPE, modelUnit)        // (long, DurationUnit) -> long rawValue
    val mPlus = mGet("plus-LRDsOJo", java.lang.Long.TYPE, java.lang.Long.TYPE)
    val mMinus = mGet("minus-LRDsOJo", java.lang.Long.TYPE, java.lang.Long.TYPE)
    val mUnaryMinus = mGet("unaryMinus-UwyO8pc", java.lang.Long.TYPE)
    val mCompareTo = mGet("compareTo-LRDsOJo", java.lang.Long.TYPE, java.lang.Long.TYPE)
    val mInWholeSeconds = mGet("getInWholeSeconds-impl", java.lang.Long.TYPE)
    val mInWholeMillis = mGet("getInWholeMilliseconds-impl", java.lang.Long.TYPE)
    val mInWholeNanos = mGet("getInWholeNanoseconds-impl", java.lang.Long.TYPE)
    val mInWholeMinutes = mGet("getInWholeMinutes-impl", java.lang.Long.TYPE)
    val mInWholeHours = mGet("getInWholeHours-impl", java.lang.Long.TYPE)
    val mIsNegative = mGet("isNegative-impl", java.lang.Long.TYPE)

    // The real Duration's packed rawValue (private field) — to confirm the model's packing matches bit-for-bit.
    val rawField = Duration::class.java.getDeclaredField("rawValue").apply { isAccessible = true }
    fun realRaw(d: Duration): Long = rawField.getLong(d)

    fun modelFromSeconds(s: Long): Long = mToDuration.invoke(null, s, modelUnitValue("SECONDS")) as Long
    fun modelFromNanos(n: Long): Long = mToDuration.invoke(null, n, modelUnitValue("NANOSECONDS")) as Long
    fun modelFromMillis(m: Long): Long = mToDuration.invoke(null, m, modelUnitValue("MILLISECONDS")) as Long

    val secs = Arb.long(-1_000_000L..1_000_000L)
    val nanos = Arb.long(-5_000_000_000L..5_000_000_000L)
    val units = Arb.int(0..6)
    fun unitName(i: Int) = listOf(
        "NANOSECONDS", "MICROSECONDS", "MILLISECONDS", "SECONDS", "MINUTES", "HOURS", "DAYS",
    )[i]
    fun realFrom(v: Long, unit: kotlin.time.DurationUnit) = v.toDuration(unit)

    test("construction from units packs identically + same observables") {
        checkAll(Arb.long(-1_000_000L..1_000_000L), units) { v, ui ->
            val unitName = unitName(ui)
            val real = realFrom(v, kotlin.time.DurationUnit.valueOf(unitName))
            val model = mToDuration.invoke(null, v, modelUnitValue(unitName)) as Long
            withClue("rawValue for $v $unitName") { model shouldBe realRaw(real) }
            (mInWholeSeconds.invoke(null, model) as Long) shouldBe real.inWholeSeconds
            (mInWholeMillis.invoke(null, model) as Long) shouldBe real.inWholeMilliseconds
            (mInWholeNanos.invoke(null, model) as Long) shouldBe real.inWholeNanoseconds
            (mInWholeMinutes.invoke(null, model) as Long) shouldBe real.inWholeMinutes
            (mInWholeHours.invoke(null, model) as Long) shouldBe real.inWholeHours
            (mIsNegative.invoke(null, model) as Boolean) shouldBe real.isNegative()
        }
    }

    test("plus / minus / negate conform (seconds, incl. negatives)") {
        checkAll(secs, secs) { a, b ->
            val ra = a.seconds; val rb = b.seconds
            val ma = modelFromSeconds(a); val mb = modelFromSeconds(b)
            (mPlus.invoke(null, ma, mb) as Long) shouldBe realRaw(ra + rb)
            (mMinus.invoke(null, ma, mb) as Long) shouldBe realRaw(ra - rb)
            (mUnaryMinus.invoke(null, ma) as Long) shouldBe realRaw(-ra)
        }
    }

    test("mixed-range plus conforms (nanos + seconds)") {
        checkAll(nanos, secs) { n, s ->
            val real = n.nanoseconds + s.seconds
            val model = mPlus.invoke(null, modelFromNanos(n), modelFromSeconds(s)) as Long
            model shouldBe realRaw(real)
            (mInWholeNanos.invoke(null, model) as Long) shouldBe real.inWholeNanoseconds
        }
    }

    test("compareTo conforms by sign across units") {
        checkAll(secs, nanos) { a, b ->
            val real = Integer.signum(a.seconds.compareTo(b.nanoseconds))
            val model = Integer.signum(mCompareTo.invoke(null, modelFromSeconds(a), modelFromNanos(b)) as Int)
            model shouldBe real
        }
    }

    // --- saturation boundary: the nanos range tops out at MAX_NANOS (~146 years); beyond it Duration
    // stores milliseconds. Drive second/milli counts that straddle the boundary and confirm the model
    // packs into the same range (nanos vs millis discriminator) and reads back the same whole-unit values.
    test("nanos/millis saturation boundary conforms") {
        // MAX_NANOS / 1e9 seconds ~= 4.6e9; pick second counts around and well past the nanos boundary.
        val boundarySecs = Arb.long(4_000_000_000L..9_000_000_000L)
        checkAll(boundarySecs) { s ->
            val real = s.seconds
            val model = modelFromSeconds(s)
            withClue("rawValue at boundary for ${s}s") { model shouldBe realRaw(real) }
            (mInWholeSeconds.invoke(null, model) as Long) shouldBe real.inWholeSeconds
            (mInWholeMillis.invoke(null, model) as Long) shouldBe real.inWholeMilliseconds
        }
        // millis-range construction straddling the same boundary.
        val boundaryMillis = Arb.long(4_000_000_000_000L..9_000_000_000_000L)
        checkAll(boundaryMillis) { m ->
            val real = m.toDuration(kotlin.time.DurationUnit.MILLISECONDS)
            val model = modelFromMillis(m)
            withClue("rawValue at millis boundary for ${m}ms") { model shouldBe realRaw(real) }
            (mInWholeMillis.invoke(null, model) as Long) shouldBe real.inWholeMilliseconds
        }
    }
})
