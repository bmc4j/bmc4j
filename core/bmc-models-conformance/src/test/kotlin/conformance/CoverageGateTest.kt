package conformance

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.io.File
import java.util.jar.JarFile

/**
 * The fail-fast coverage gate. Enumerates every bmc-model class and requires each to be
 * either differential-tested here, validated by a model proof, or explicitly waived with a reason.
 * A NEW model added to bmc-models/bmc-kotlin-models with no coverage makes this fail — so model
 * soundness can't silently erode as the library grows. Update COVERED/WAIVED when adding a model.
 */
class CoverageGateTest : FunSpec({

    test("every model class has a differential suite, a model proof, or an explicit waiver") {
        val jarPath = System.getProperty("java.class.path").split(File.pathSeparatorChar)
            .firstOrNull { it.replace('\\', '/').endsWith("bmcref-models.jar") }
            ?: error("relocated models jar not found on the test classpath")

        val models = JarFile(jarPath).use { jar ->
            jar.entries().asSequence()
                .map { it.name }
                .filter { it.endsWith(".class") && !it.contains('$') && !it.endsWith("package-info.class") }
                .map { it.removeSuffix(".class").removePrefix("bmcref/").replace('/', '.') }
                .toSortedSet()
        }

        val registered = COVERED + WAIVED.keys
        withClue("Model(s) with no conformance suite/proof or waiver — add one, or a WAIVED entry:\n  ${(models - registered).toSortedSet()}") {
            (models - registered).isEmpty() shouldBe true
        }
        withClue("Registry entries that are no longer models — remove them:\n  ${(registered - models).toSortedSet()}") {
            (registered - models).isEmpty() shouldBe true
        }
    }
})
