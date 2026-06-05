package conformance

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * De-risks the whole approach: the relocated model (`bmcref.java.util.ArrayList`) must load on a
 * real JVM side by side with the real `java.util.ArrayList`. If this passes, differential
 * conformance testing is viable.
 */
class RelocationSmokeTest : FunSpec({
    test("relocated model loads next to the real JDK class and behaves the same") {
        val model = bmcref.java.util.ArrayList<Int>()
        val real = java.util.ArrayList<Int>()
        model.add(7)
        real.add(7)
        model.get(0) shouldBe 7
        model.size() shouldBe real.size
        // They really are distinct classes from distinct packages.
        model.javaClass.name shouldBe "bmcref.java.util.ArrayList"
        real.javaClass.name shouldBe "java.util.ArrayList"
    }
})
