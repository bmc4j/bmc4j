package conformance

import io.kotest.core.spec.style.FunSpec
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.orNull
import io.kotest.property.checkAll

/**
 * Differential conformance for the {@code java.util.Enumeration} view produced by
 * {@code java.util.Collections.enumeration(Collection)}. Build identical source lists, take an
 * enumeration over each (the JDK and the relocated model), and walk them in lockstep: every
 * {@code hasMoreElements()} and {@code nextElement()} must agree element-by-element, and stepping
 * past the end must throw {@link NoSuchElementException} on both. The model iterates by index over a
 * snapshot of the collection's backing (concrete-backing pattern), so the enumerated sequence is the
 * source's iteration order — compared exactly.
 */
class EnumerationConformanceTest : FunSpec({

    test("Collections.enumeration walks the source like the JDK (hasMoreElements/nextElement)") {
        val elem: Arb<Int?> = Arb.int(-5..9).orNull(0.1)
        checkAll(Arb.list(elem, 0..30)) { items ->
            val rSrc = java.util.ArrayList<Any?>()
            val mSrc = bmcref.java.util.ArrayList<Any?>()
            for (x in items) { rSrc.add(x); mSrc.add(x) }

            val rEnum = java.util.Collections.enumeration(rSrc)
            val mEnum = staticCall(
                bmcref.java.util.Collections::class.java,
                "enumeration",
                arrayOf(bmcref.java.util.Collection::class.java),
                mSrc,
            ).getOrThrow()!!

            // Walk both enumerations in lockstep: each hasMoreElements + nextElement must agree.
            for (i in items.indices) {
                assertEquivalent("hasMoreElements[$i]",
                    runCatching { rEnum.hasMoreElements() },
                    call(mEnum, "hasMoreElements", arrayOf()))
                assertEquivalent("nextElement[$i]",
                    runCatching { rEnum.nextElement() },
                    call(mEnum, "nextElement", arrayOf()))
            }
            // Both exhausted now: hasMoreElements false, and one more nextElement throws NoSuchElementException.
            assertEquivalent("hasMoreElements@end",
                runCatching { rEnum.hasMoreElements() },
                call(mEnum, "hasMoreElements", arrayOf()))
            assertSameException(
                runCatching { rEnum.nextElement() },
                call(mEnum, "nextElement", arrayOf()))
        }
    }

    // Enumerating an empty collection: hasMoreElements is immediately false, and nextElement throws
    // NoSuchElementException on both.
    test("Collections.enumeration over an empty collection is empty like the JDK") {
        val rSrc = java.util.ArrayList<Any?>()
        val mSrc = bmcref.java.util.ArrayList<Any?>()
        val rEnum = java.util.Collections.enumeration(rSrc)
        val mEnum = staticCall(
            bmcref.java.util.Collections::class.java,
            "enumeration",
            arrayOf(bmcref.java.util.Collection::class.java),
            mSrc,
        ).getOrThrow()!!
        assertEquivalent("hasMoreElements",
            runCatching { rEnum.hasMoreElements() }, call(mEnum, "hasMoreElements", arrayOf()))
        assertSameException(
            runCatching { rEnum.nextElement() }, call(mEnum, "nextElement", arrayOf()))
    }
})
