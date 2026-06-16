package org.bmc4j.junit

import org.bmc4j.BmcProof
import org.bmc4j.ConformProofsAgainstModel
import org.bmc4j.Verdict
import org.bmc4j.engine.BmcRequest
import org.bmc4j.engine.BmcUndecidedError
import org.bmc4j.engine.BmcVerificationError
import org.bmc4j.engine.VerdictCache
import org.bmc4j.engine.excludeFromUserModels
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * Unit tests for the @ConformProofsAgainstModel surface and its real-leg exclusion primitive: the
 * annotation merge, the per-request exclusion handle, the model-overlay class drop, the distinct cache
 * key per leg, and the leg-failure framing. These are pure (no engine run): the full two-leg behaviour
 * (both legs must pass; an unsound model fails the real leg) rides on the existing per-leg [runProof],
 * which these inputs feed.
 */
internal class ConformProofsAgainstModelTest {

    // Model classes named only for their FQNs (reflection targets, never instantiated).
    internal class FastList
    internal class NoCollisionMap

    @Disabled("reflection-only fixture; not a runnable proof suite")
    @ConformProofsAgainstModel(FastList::class)
    internal class ClassLevelConform {
        @BmcProof
        fun inheritsClassConform() {}

        @BmcProof
        @ConformProofsAgainstModel(NoCollisionMap::class)
        fun mergesMethodAndClass() {}
    }

    @Disabled("reflection-only fixture; not a runnable proof suite")
    internal class PlainProofs {
        @BmcProof
        fun notConformed() {}
    }

    // --- annotation merge --------------------------------------------------------

    @Test
    fun conformedModels_emptyWhenNotAnnotated() {
        val m = PlainProofs::class.java.getDeclaredMethod("notConformed")
        assertTrue(BmcProofExtension.conformedModels(m).isEmpty(),
                "a proof with no @ConformProofsAgainstModel conforms nothing")
    }

    @Test
    fun conformedModels_inheritsClassLevelValue() {
        val m = ClassLevelConform::class.java.getDeclaredMethod("inheritsClassConform")
        assertEquals(setOf(FastList::class.java.name), BmcProofExtension.conformedModels(m),
                "a class-level @ConformProofsAgainstModel applies to every proof in the class")
    }

    @Test
    fun conformedModels_mergesMethodAndClass() {
        val m = ClassLevelConform::class.java.getDeclaredMethod("mergesMethodAndClass")
        assertEquals(setOf(FastList::class.java.name, NoCollisionMap::class.java.name),
                BmcProofExtension.conformedModels(m),
                "the method value is merged with the class value")
    }

    // --- per-request exclusion handle -------------------------------------------

    @Test
    fun requestExclusionSet_ridesTheSharedExcludeModelsPrimitive_leavingOtherFieldsUnchanged() {
        val base = BmcProofExtension.requestFor("acme.T", "acme.T.p", null)
        assertTrue(base.excludeModels.isEmpty(), "an ordinary request excludes nothing")
        val real = BmcProofExtension.requestFor(
                "acme.T", "acme.T.p", null, excludeModels = setOf("acme.FastList"))
        assertEquals(setOf("acme.FastList"), real.excludeModels)
        assertEquals(base.entryFunction, real.entryFunction)
        assertEquals(base.unwind, real.unwind)
    }

    // --- model-overlay class drop (the shared real-leg primitive) ---------------

    @Test
    fun excludeFromUserModels_excludesNamedClassFromADirectoryEntry(
            @org.junit.jupiter.api.io.TempDir dir: Path) {
        writeClassFile(dir, "acme.FastList")
        writeClassFile(dir, "acme.Kept")
        val filtered = excludeFromUserModels(dir.toString(), setOf("acme.FastList"))
        // A fresh dir was materialized (not the original) because it held the excluded class.
        assertNotEquals(dir.toString(), filtered, "the dir held the excluded class -> a filtered copy")
        val out = Path.of(filtered)
        assertFalse(Files.exists(out.resolve("acme/FastList.class")), "the excluded class is dropped")
        assertTrue(Files.exists(out.resolve("acme/Kept.class")), "every other model class is kept")
    }

    @Test
    fun excludeFromUserModels_passesThroughEntriesThatHoldNoneOfTheExcludedClasses(
            @org.junit.jupiter.api.io.TempDir dir: Path) {
        writeClassFile(dir, "acme.Kept")
        val filtered = excludeFromUserModels(dir.toString(), setOf("acme.NotHere"))
        assertEquals(dir.toString(), filtered,
                "a dir that holds none of the excluded classes is reused untouched (no copy)")
    }

    @Test
    fun excludeFromUserModels_emptyExclusionIsANoOp(@org.junit.jupiter.api.io.TempDir dir: Path) {
        writeClassFile(dir, "acme.FastList")
        assertEquals(dir.toString(), excludeFromUserModels(dir.toString(), emptySet()))
    }

    // --- distinct cache key per leg ---------------------------------------------

    @Test
    fun verdictCacheKey_differsBetweenModelAndRealLeg() {
        val modelLeg = BmcProofExtension.requestFor("acme.T", "acme.T.p", null)
        val realLeg = BmcProofExtension.requestFor(
                "acme.T", "acme.T.p", null, excludeModels = setOf("acme.FastList"))
        val modelKey = VerdictCache.computeKey(modelLeg, "engine-x")
        val realKey = VerdictCache.computeKey(realLeg, "engine-x")
        assertNotEquals(modelKey, realKey,
                "the real leg analyses a different classpath -> it must key distinctly from the model leg")
        // An identical exclusion keys identically (a re-run of the same leg still short-circuits).
        assertEquals(realKey, VerdictCache.computeKey(
                BmcProofExtension.requestFor(
                        "acme.T", "acme.T.p", null, excludeModels = setOf("acme.FastList")), "engine-x"))
    }

    // --- leg-failure framing -----------------------------------------------------

    @Test
    fun aRealLegFailure_namesTheLeg_andCallsOutAnUnsoundModel() {
        val ext = BmcProofExtension()
        // The real leg refuted: the model verified the property but the real impl does not.
        val framed = BmcVerificationError("JBMC refuted acme.T.p")
        val reframed = legFailure(ext, "real", Verdict.REFUTED, framed)
        val msg = reframed.message!!
        assertTrue(msg.contains("real leg"), "names the failing leg: $msg")
        assertTrue(msg.contains("UNSOUND"), "calls out the model unsoundness on a real-leg failure: $msg")
        assertTrue(msg.contains("JBMC refuted acme.T.p"), "keeps the underlying counterexample: $msg")
        assertEquals(framed, reframed.cause, "the original framed failure is preserved as the cause")
    }

    @Test
    fun aModelLegFailure_namesTheModelLeg_withoutTheUnsoundFraming() {
        val ext = BmcProofExtension()
        val framed = BmcVerificationError("JBMC refuted acme.T.p")
        val msg = legFailure(ext, "model", Verdict.REFUTED, framed).message!!
        assertTrue(msg.contains("model leg"), "names the model leg: $msg")
        assertFalse(msg.contains("UNSOUND"),
                "a model-leg failure is a plain proof failure, not a model-soundness verdict: $msg")
    }

    @Test
    fun aRealLegUnknown_staysUnknownTyped() {
        val ext = BmcProofExtension()
        val undecided = BmcUndecidedError("JBMC could not decide acme.T.p (UNKNOWN)")
        val reframed = legFailure(ext, "real", Verdict.UNKNOWN, undecided)
        assertTrue(reframed is BmcUndecidedError,
                "a leg UNKNOWN stays an UNKNOWN so the runner prints UNKNOWN")
    }

    companion object {
        private fun writeClassFile(root: Path, fqn: String) {
            val p = root.resolve(fqn.replace('.', '/') + ".class")
            Files.createDirectories(p.parent)
            Files.write(p, byteArrayOf(0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte()))
        }

        /** Reflect into the private `legFailure` reframing (a real engine run is not needed to test how a
         *  leg's framed failure is re-thrown — the framing is pure). */
        private fun legFailure(ext: BmcProofExtension, leg: String, verdict: Verdict,
                               framed: BmcVerificationError): BmcVerificationError {
            val m = BmcProofExtension::class.java.getDeclaredMethod(
                    "legFailure", String::class.java, String::class.java, Verdict::class.java,
                    BmcVerificationError::class.java, java.lang.Boolean.TYPE)
            m.isAccessible = true
            return m.invoke(ext, leg, "acme.T.p", verdict, framed,
                    framed is BmcUndecidedError) as BmcVerificationError
        }
    }
}
