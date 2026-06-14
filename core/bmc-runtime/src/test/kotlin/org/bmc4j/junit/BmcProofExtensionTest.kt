package org.bmc4j.junit

import org.bmc4j.BmcProof
import org.bmc4j.engine.BmcUndecidedError
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.lang.reflect.Method

/**
 * Unit tests for [BmcProofExtension]'s configuration parsing: the effective solver /
 * maxStringLength / timeoutSeconds resolution (per-proof override then build default), and that a
 * malformed int-valued `bmc.*` property fails loudly (throws, naming the property + bad value)
 * instead of silently using the default.
 *
 * The sample fixtures below are plain static nested classes (NOT `@Nested`), so JUnit does
 * not discover or run their `@BmcProof` methods — they exist only as reflection targets.
 */
internal class BmcProofExtensionTest {

    // --- Fixtures: reflection-only targets, never executed by JUnit -----------

    @Disabled("reflection-only fixture; not a runnable proof suite")
    internal class MixedSolverProofs {
        @BmcProof
        fun usesDefaultSolver() {}

        @BmcProof(solver = "z3")
        fun usesZ3() {}

        @BmcProof
        fun alsoDefaultSolver() {}
    }

    @Disabled("reflection-only fixture; not a runnable proof suite")
    internal class TunedProof {
        @BmcProof(unwind = 8)
        fun unwind8() {}
    }

    @Disabled("reflection-only fixture; not a runnable proof suite")
    internal class StubAckProofs {
        @BmcProof(allowStubs = ["java.util.Formatter.*", " java.util.Locale.getDefault "])
        fun acked() {}

        @BmcProof
        fun unacked() {}
    }

    @Disabled("reflection-only fixture; not a runnable proof suite")
    internal class UnmodelledAckProofs {
        @BmcProof(acknowledgeUnmodelled = ["java.util.ArrayList.sort", " java.util.HashSet.* "])
        fun acked() {}

        @BmcProof
        fun unacked() {}
    }

    // --- unmodelled-member acknowledgment (verdict honesty + opt-out) ----------

    @Test
    fun acknowledgedUnmodelled_mergesAnnotationAndBuildProp_trimmed() {
        val prev = System.getProperty("bmc.acknowledgeUnmodelled")
        System.setProperty("bmc.acknowledgeUnmodelled", "java.time.*, java.math.BigInteger.gcd")
        try {
            val ack = BmcProofExtension.acknowledgedUnmodelled(
                    annotationOn(UnmodelledAckProofs::class.java, "acked"))
            assertTrue(ack.contains("java.util.ArrayList.sort"), ack.toString())
            assertTrue(ack.contains("java.util.HashSet.*"), "whitespace trimmed: $ack")
            assertTrue(ack.contains("java.time.*") && ack.contains("java.math.BigInteger.gcd"),
                    "build-wide -Dbmc.acknowledgeUnmodelled entries are merged: $ack")
        } finally {
            restore("bmc.acknowledgeUnmodelled", prev)
        }
    }

    @Test
    fun isAcknowledged_matchesExactAndWildcard_onTheMemberName() {
        val acked = listOf("java.util.ArrayList.sort", "java.util.HashSet.*", "java.time.*")
        // exact name match (params ignored)
        assertTrue(BmcProofExtension.isAcknowledged("java.util.ArrayList.sort(Comparator)", acked))
        // class wildcard
        assertTrue(BmcProofExtension.isAcknowledged("java.util.HashSet.forEach(Consumer)", acked))
        // package wildcard
        assertTrue(BmcProofExtension.isAcknowledged("java.time.Instant.now()", acked))
        // not acknowledged
        assertFalse(BmcProofExtension.isAcknowledged("java.util.ArrayList.replaceAll(UnaryOperator)", acked))
        assertFalse(BmcProofExtension.isAcknowledged("java.math.BigInteger.gcd(BigInteger)", acked))
    }

    @Test
    fun buildWideAcknowledgeUnmodelled_participatesInTheVerdictCacheKey() {
        // The build-wide acknowledgment changes outcomes (an acknowledged reach degrades from UNKNOWN
        // to a footnoted pass), so it must be folded into the cache's engine identity — a change to it
        // invalidates cached verdicts. Unset -> empty suffix; set -> the prop appears in the suffix.
        val prev = System.getProperty("bmc.acknowledgeUnmodelled")
        try {
            System.clearProperty("bmc.acknowledgeUnmodelled")
            assertFalse(BmcProofExtension.solverEnvSuffix().contains("acknowledgeUnmodelled"),
                    "unset: the ack prop must not perturb the default cache key")
            System.setProperty("bmc.acknowledgeUnmodelled", "java.util.ArrayList.sort")
            val suffix = BmcProofExtension.solverEnvSuffix()
            assertTrue(suffix.contains("bmc.acknowledgeUnmodelled=java.util.ArrayList.sort"),
                    "set: the ack value is keyed into the engine identity: $suffix")
        } finally {
            restore("bmc.acknowledgeUnmodelled", prev)
        }
    }

    @Test
    fun unmodelledMemberUndecided_isUnknown_namesMember_and_saysWhatToDo() {
        val err = BmcProofExtension.unmodelledMemberUndecided(
                "jbmc", "pkg.T.proof", listOf("java.util.ArrayList.sort(Comparator)"))
        // Non-infrastructure: a genuine, acknowledgeable analysis limit (satisfies expect=UNKNOWN).
        assertFalse(err.isEngineInfrastructure(),
                "an unmodelled-member reach is a real model gap, not engine infrastructure failure")
        val msg = err.message!!
        assertTrue(msg.contains("(UNKNOWN)"), msg)
        assertTrue(msg.contains("java.util.ArrayList.sort"), msg)
        assertTrue(msg.contains("does not model"), msg)
        assertTrue(msg.contains("acknowledgeUnmodelled"), msg)
        assertTrue(msg.contains("model it"), msg)
    }

    // --- Deliberately out-of-scope (declared) packages ------------

    @Test
    fun matchesNotModeledPackage_isRecursive_overSubpackages() {
        val globs = listOf("java.nio.*")
        // exact package member matches
        assertTrue(BmcProofExtension.matchesNotModeledPackage("java.nio.ByteBuffer.get", globs))
        // a SUBPACKAGE member matches too — recursion is the only mode
        assertTrue(BmcProofExtension.matchesNotModeledPackage("java.nio.file.Path.resolve", globs))
        assertTrue(BmcProofExtension.matchesNotModeledPackage(
                "java.nio.file.attribute.FileTime.toMillis", globs))
        // an unrelated package does not
        assertFalse(BmcProofExtension.matchesNotModeledPackage("java.util.ArrayList.add", globs))
    }

    @Test
    fun matchesNotModeledPackage_barePrefixAndExact_recurseToo_onDottedBoundary() {
        // a bare prefix (no wildcard) recurses identically
        assertTrue(BmcProofExtension.matchesNotModeledPackage(
                "java.sql.Date.toString", listOf("java.sql")))
        assertTrue(BmcProofExtension.matchesNotModeledPackage(
                "java.sql.rowset.Predicate.evaluate", listOf("java.sql")))
        // the dotted boundary prevents java.sql spuriously matching a sibling java.sqlx package
        assertFalse(BmcProofExtension.matchesNotModeledPackage(
                "java.sqlx.Foo.bar", listOf("java.sql")))
        // a `**`-style or trailing-`*` spelling normalizes to the same recursive prefix
        assertTrue(BmcProofExtension.matchesNotModeledPackage(
                "javax.swing.JButton.doClick", listOf("javax.swing.*")))
    }

    @Test
    fun outOfScopePackageUndecided_isUnknown_namesMember_distinctText_saysWhatToDo() {
        val err = BmcProofExtension.outOfScopePackageUndecided(
                "jbmc", "pkg.T.proof", listOf("java.sql.Date.toString"))
        // A declared waiver is a real, acknowledgeable boundary (satisfies expect=UNKNOWN), not infra.
        assertFalse(err.isEngineInfrastructure(),
                "a declared out-of-scope reach is a deliberate boundary, not engine infrastructure")
        // Typed kind: OUT_OF_SCOPE, and (a declared decline is deterministic) NOT retryable.
        assertEquals(org.bmc4j.engine.UnknownKind.OUT_OF_SCOPE, err.kind,
                "a declared out-of-scope reach carries the OUT_OF_SCOPE kind")
        assertFalse(err.kind!!.retryable,
                "OUT_OF_SCOPE is deterministic (a declared decline), so it must not be retryable")
        val msg = err.message!!
        assertTrue(msg.contains("(UNKNOWN)"), msg)
        assertTrue(msg.contains("java.sql.Date.toString"), msg)
        // DISTINCT from the generic unmodelled-member text so a reviewer can tell the two apart.
        assertTrue(msg.contains("out-of-scope (declared)"), msg)
        assertTrue(msg.contains("notModeledPackages"), msg)
        assertTrue(msg.contains("acknowledgeUnmodelled"), msg)
    }

    @Test
    fun outOfScopeStubsToDemote_flagsUnmodeledDeclaredStub_butRegistryWins_forModeledClass() {
        val globs = listOf("java.util.*", "java.sql.*")
        val acked = emptyList<String>()
        // A MODELED class (ArrayList) has a body -> it is NEVER nondet-stubbed -> it never appears in the
        // harvested stub stream the waiver inspects. So even with java.util.* declared, a stub stream that
        // contains only the modeled-class members yields NO demotion: the registry wins over the waiver.
        // (Here the stub stream simulates a reach where ONLY an unmodeled declared-package member stubbed.)
        val stubbed = listOf("java.sql.Date.getTime", "java.lang.System.nanoTime")
        val demoted = BmcProofExtension.outOfScopeStubsToDemote(stubbed, globs, acked)
        // java.sql.Date is under a declared glob and unmodeled -> flagged; java.lang.System is not -> not.
        assertEquals(listOf("java.sql.Date.getTime"), demoted)
    }

    @Test
    fun outOfScopeStubsToDemote_isEmpty_whenNoStubUnderDeclaredPackage() {
        // A modeled class never stubs, so this models the registry-wins case directly: the only stubs are
        // outside every declared package -> nothing to demote, the proof keeps its (modeled) verdict.
        val demoted = BmcProofExtension.outOfScopeStubsToDemote(
                listOf("java.util.ArrayList.trimToSize"), listOf("java.sql.*"), emptyList())
        assertTrue(demoted.isEmpty(), "no stub under a declared package -> no out-of-scope demotion")
    }

    @Test
    fun outOfScopeStubsToDemote_acknowledgedMember_optsOut() {
        // The same acknowledgeUnmodelled opt-out as the per-member tail: an acknowledged member degrades
        // to footnoted-nondet (handled by the stub policy), so it is NOT in the demote-to-UNKNOWN set.
        val demoted = BmcProofExtension.outOfScopeStubsToDemote(
                listOf("java.sql.Date.getTime"), listOf("java.sql.*"), listOf("java.sql.Date.getTime"))
        assertTrue(demoted.isEmpty(), "an acknowledged out-of-scope member is not demoted to UNKNOWN")
    }

    @Test
    fun notModeledPackageGlobs_parsesCommaSeparated_trimmed() {
        val prev = System.getProperty("bmc.notModeledPackages")
        try {
            System.setProperty("bmc.notModeledPackages", " javax.swing.* , java.sql.* ,, ")
            assertEquals(listOf("javax.swing.*", "java.sql.*"),
                    BmcProofExtension.notModeledPackageGlobs())
        } finally {
            if (prev == null) System.clearProperty("bmc.notModeledPackages")
            else System.setProperty("bmc.notModeledPackages", prev)
        }
    }

    // --- Nondet-stub policy ---------------------------------------

    @Test
    fun effectiveAllowStubs_mergesAnnotationAndBuildProp_trimmed() {
        val prev = System.getProperty("bmc.allowStubs")
        System.setProperty("bmc.allowStubs", "java.time.*, com.x.Y.z")
        try {
            val allow = BmcProofExtension.effectiveAllowStubs(
                    annotationOn(StubAckProofs::class.java, "acked"))
            assertTrue(allow.contains("java.util.Formatter.*"), allow.toString())
            assertTrue(allow.contains("java.util.Locale.getDefault"), "whitespace trimmed: $allow")
            assertTrue(allow.contains("java.time.*") && allow.contains("com.x.Y.z"),
                    "build-wide -Dbmc.allowStubs entries are merged: $allow")
        } finally {
            restore("bmc.allowStubs", prev)
        }
    }

    @Test
    fun applyStubPolicy_lenient_isGreen_evenWithUnacknowledgedStub() {
        val prevStrict = System.getProperty("bmc.strictStubs")
        System.clearProperty("bmc.strictStubs")
        try {
            // Lenient (default): an unacknowledged stub prints a footnote but does NOT throw.
            assertDoesNotThrow {
                BmcProofExtension.applyStubPolicy(
                        "pkg.T.unacked", annotationOn(StubAckProofs::class.java, "unacked"),
                        listOf("java.util.Formatter.format"))
            }
        } finally {
            restore("bmc.strictStubs", prevStrict)
        }
    }

    @Test
    fun applyStubPolicy_strict_throwsUnknownForUnacknowledged_butNotForAcknowledged() {
        val prevStrict = System.getProperty("bmc.strictStubs")
        System.setProperty("bmc.strictStubs", "true")
        try {
            val err = assertThrows(BmcUndecidedError::class.java) {
                BmcProofExtension.applyStubPolicy("pkg.T.unacked",
                        annotationOn(StubAckProofs::class.java, "unacked"),
                        listOf("java.util.Formatter.format"))
            }
            assertTrue(err.message!!.contains("(UNKNOWN)"), err.message)
            assertTrue(err.message!!.contains("java.util.Formatter.format"), err.message)

            // Acknowledged (allowStubs covers it) -> no throw even in strict mode.
            assertDoesNotThrow {
                BmcProofExtension.applyStubPolicy("pkg.T.acked",
                        annotationOn(StubAckProofs::class.java, "acked"),
                        listOf("java.util.Formatter.format"))
            }
        } finally {
            restore("bmc.strictStubs", prevStrict)
        }
    }

    @Test
    fun applyStubPolicy_noStubs_isNoOp() {
        assertDoesNotThrow { BmcProofExtension.applyStubPolicy("pkg.T.p", null, listOf()) }
        assertFalse(java.lang.Boolean.parseBoolean(System.getProperty("bmc.strictStubs", "false")))
    }

    // --- User-model trust policy ----------------------------------------

    @Test
    fun applyModelPolicy_domainModel_footnotesRationaleOnGreenProof(
            @org.junit.jupiter.api.io.TempDir dir: java.nio.file.Path) {
        writeModelClass(dir, "acme.NoCollisionMap")
        val models = org.bmc4j.engine.ModelManifest.serialize(listOf(
                org.bmc4j.engine.UserModel.domain("acme.NoCollisionMap", "no key collisions")))
        val out = captureModelPolicy("pkg.T.p", models, dir.toString(), false)
        assertTrue(out.contains("acme.NoCollisionMap"), out)
        assertTrue(out.contains("no key collisions"),
                "a green proof resting on a domain model must footnote its rationale: $out")
        assertTrue(out.contains("domain model"), out)
    }

    @Test
    fun applyModelPolicy_undeclaredModel_isGreenFootnoteInLenient_butUnknownInStrict(
            @org.junit.jupiter.api.io.TempDir dir: java.nio.file.Path) {
        writeModelClass(dir, "acme.Sneaky")
        // Lenient: no declaration -> loud footnote, but green (does not throw).
        val lenient = captureModelPolicy("pkg.T.p", "", dir.toString(), false)
        assertTrue(lenient.contains("UNDECLARED model acme.Sneaky"), lenient)

        // Strict: an undeclared present model -> UNKNOWN.
        val err = assertThrows(BmcUndecidedError::class.java) {
            captureModelPolicy("pkg.T.p", "", dir.toString(), true)
        }
        assertTrue(err.message!!.contains("(UNKNOWN)"), err.message)
        assertTrue(err.message!!.contains("acme.Sneaky"), err.message)
        assertFalse(err.message!!.contains("refuted "),
                "an undeclared override is UNKNOWN, never a refutation: " + err.message)
    }

    @Test
    fun applyModelPolicy_declaredModel_staysGreenEvenInStrict(
            @org.junit.jupiter.api.io.TempDir dir: java.nio.file.Path) {
        writeModelClass(dir, "acme.FastList")
        val models = org.bmc4j.engine.ModelManifest.serialize(listOf(
                org.bmc4j.engine.UserModel.conformant("acme.FastList")))
        // Declared -> no throw even in strict mode (it's the UNDECLARED ones strictModels punishes).
        assertDoesNotThrow { captureModelPolicy("pkg.T.p", models, dir.toString(), true) }
    }

    @Test
    fun applyModelPolicy_overrideOfBundledModel_warnsLoudly(
            @org.junit.jupiter.api.io.TempDir dir: java.nio.file.Path) {
        writeModelClass(dir, "java.util.HashMap")
        val models = org.bmc4j.engine.ModelManifest.serialize(listOf(
                org.bmc4j.engine.UserModel.domain("java.util.HashMap", "bounded to 32 entries")))
        val out = captureModelPolicy("pkg.T.p", models, dir.toString(), false)
        assertTrue(out.contains("WARNING"), "shadowing a bundled model must warn: $out")
        assertTrue(out.contains("java.util.HashMap"), out)
    }

    @Test
    fun applyModelPolicy_noUserModels_isNoOp() {
        assertDoesNotThrow { captureModelPolicy("pkg.T.p", "", "", false) }
    }

    @Disabled("reflection-only fixture; not a runnable proof suite")
    internal class MixedStringLengthProofs {
        @BmcProof
        fun usesDefaultLength() {}

        @BmcProof(maxStringLength = 4)
        fun usesLength4() {}

        @BmcProof
        fun alsoDefaultLength() {}
    }

    @Disabled("reflection-only fixture; not a runnable proof suite")
    internal class MixedTimeoutProofs {
        @BmcProof
        fun usesDefaultTimeout() {}

        @BmcProof(timeoutSeconds = 5)
        fun usesTimeout5() {}

        @BmcProof
        fun alsoDefaultTimeout() {}
    }

    @Disabled("reflection-only fixture; not a runnable proof suite")
    internal class MixedStringModeProofs {
        @BmcProof
        fun usesDefaultMode() {}

        @BmcProof(stringMode = org.bmc4j.StringMode.CHAR_ARRAY_MODEL)
        fun usesNone() {}

        @BmcProof(stringMode = org.bmc4j.StringMode.REFINEMENT)
        fun usesRefinementExplicit() {}
    }

    @Test
    fun resolveStringMode_prefersPerProofOverride_thenDefaults() {
        assertEquals(org.bmc4j.StringMode.CHAR_ARRAY_MODEL, BmcProofExtension.resolveStringMode(
                annotationOn(MixedStringModeProofs::class.java, "usesNone")),
                "per-proof @BmcProof(stringMode = CHAR_ARRAY_MODEL) wins")
        assertEquals(org.bmc4j.StringMode.REFINEMENT, BmcProofExtension.resolveStringMode(
                annotationOn(MixedStringModeProofs::class.java, "usesDefaultMode")),
                "the annotation default REFINEMENT resolves to REFINEMENT when no build default is set")
    }

    @Test
    fun resolveStringMode_honorsBuildDefaultProp_andPerProofOverrides() {
        val prev = System.getProperty("bmc.stringMode")
        System.setProperty("bmc.stringMode", "char_array_model")
        try {
            assertEquals(org.bmc4j.StringMode.CHAR_ARRAY_MODEL, BmcProofExtension.resolveStringMode(
                    annotationOn(MixedStringModeProofs::class.java, "usesDefaultMode")),
                    "-Dbmc.stringMode=char_array_model must apply when the proof leaves stringMode on the default")
            assertEquals(org.bmc4j.StringMode.CHAR_ARRAY_MODEL, BmcProofExtension.resolveStringMode(
                    annotationOn(MixedStringModeProofs::class.java, "usesRefinementExplicit")),
                    "a per-proof REFINEMENT (the annotation default) yields to the build default, " +
                            "exactly like AUTO does for removeExceptionMessages - so the module-wide " +
                            "bmc.stringMode=char_array_model wins; only a non-default per-proof value (CHAR_ARRAY_MODEL) overrides")
            assertEquals(org.bmc4j.StringMode.CHAR_ARRAY_MODEL, BmcProofExtension.resolveStringMode(
                    annotationOn(MixedStringModeProofs::class.java, "usesNone")),
                    "a per-proof CHAR_ARRAY_MODEL override stands even under a different build default")
        } finally {
            restore("bmc.stringMode", prev)
        }
    }

    @Test
    fun resolveStringMode_unrecognizedProp_fallsBackToRefinement() {
        val prev = System.getProperty("bmc.stringMode")
        System.setProperty("bmc.stringMode", "banana")
        try {
            assertEquals(org.bmc4j.StringMode.REFINEMENT, BmcProofExtension.resolveStringMode(
                    annotationOn(MixedStringModeProofs::class.java, "usesDefaultMode")),
                    "an unrecognized -Dbmc.stringMode is fail-safe: falls back to REFINEMENT")
        } finally {
            restore("bmc.stringMode", prev)
        }
    }

    @Test
    fun resolveMaxStringLength_prefersPerProofOverride_thenDefaults() {
        assertEquals(4, BmcProofExtension.resolveMaxStringLength(
                annotationOn(MixedStringLengthProofs::class.java, "usesLength4")))
        assertEquals(16, BmcProofExtension.resolveMaxStringLength(
                annotationOn(MixedStringLengthProofs::class.java, "usesDefaultLength")),
                "maxStringLength=0 falls back to the build default (16)")
    }

    @Test
    fun resolveTimeoutSeconds_prefersPerProofOverride_thenDefaults() {
        assertEquals(5, BmcProofExtension.resolveTimeoutSeconds(
                annotationOn(MixedTimeoutProofs::class.java, "usesTimeout5")))
        assertEquals(0, BmcProofExtension.resolveTimeoutSeconds(
                annotationOn(MixedTimeoutProofs::class.java, "usesDefaultTimeout")),
                "timeoutSeconds=0 falls back to the build default (0 = no timeout when unset)")
    }

    @Test
    fun resolveTimeoutSeconds_honorsBuildDefaultProp_whenNoPerProofOverride() {
        val prev = System.getProperty("bmc.timeoutSeconds")
        System.setProperty("bmc.timeoutSeconds", "30")
        try {
            assertEquals(30, BmcProofExtension.resolveTimeoutSeconds(
                    annotationOn(MixedTimeoutProofs::class.java, "usesDefaultTimeout")),
                    "-Dbmc.timeoutSeconds must apply when the proof has no override")
            assertEquals(5, BmcProofExtension.resolveTimeoutSeconds(
                    annotationOn(MixedTimeoutProofs::class.java, "usesTimeout5")),
                    "per-proof @BmcProof(timeoutSeconds=5) overrides the build default")
        } finally {
            restore("bmc.timeoutSeconds", prev)
        }
    }

    @Test
    fun effectiveSolver_prefersPerProofOverride_thenDefaults() {
        assertEquals("z3", BmcProofExtension.effectiveSolver(annotationOn(MixedSolverProofs::class.java, "usesZ3")))
        assertEquals("", BmcProofExtension.effectiveSolver(annotationOn(MixedSolverProofs::class.java, "usesDefaultSolver")))
    }

    // --- malformed bmc.* int properties fail loudly --------------------

    @Test
    fun unwind_malformedValue_throwsNamingPropAndValue() {
        val prev = System.getProperty("bmc.unwind")
        System.setProperty("bmc.unwind", "1o")
        try {
            val cfg = annotationOn(MixedSolverProofs::class.java, "usesDefaultSolver") // unwind=0 -> reads prop
            val ex = assertThrows(IllegalArgumentException::class.java) { BmcProofExtension.resolveUnwind(cfg) }
            assertTrue(ex.message!!.contains("bmc.unwind"), "message must name the property: " + ex.message)
            assertTrue(ex.message!!.contains("1o"), "message must name the bad value: " + ex.message)
        } finally {
            restore("bmc.unwind", prev)
        }
    }

    @Test
    fun maxStringLength_malformedValue_throwsNamingPropAndValue() {
        val prev = System.getProperty("bmc.maxStringLength")
        System.setProperty("bmc.maxStringLength", "abc")
        try {
            val ex = assertThrows(IllegalArgumentException::class.java) {
                BmcProofExtension.requestFor("C", "C.m", null)
            }
            assertTrue(ex.message!!.contains("bmc.maxStringLength"),
                    "message must name the property: " + ex.message)
            assertTrue(ex.message!!.contains("abc"),
                    "message must name the bad value: " + ex.message)
        } finally {
            restore("bmc.maxStringLength", prev)
        }
    }

    @Test
    fun unwind_validPropValue_isHonored() {
        val prev = System.getProperty("bmc.unwind")
        System.setProperty("bmc.unwind", "8")
        try {
            val cfg = annotationOn(MixedSolverProofs::class.java, "usesDefaultSolver") // unwind=0 -> reads prop
            assertEquals(8, BmcProofExtension.resolveUnwind(cfg), "valid -Dbmc.unwind=8 must be honored")
        } finally {
            restore("bmc.unwind", prev)
        }
    }

    @Test
    fun unwind_perProofAnnotationWins_overProp() {
        val prev = System.getProperty("bmc.unwind")
        System.setProperty("bmc.unwind", "16")
        try {
            val cfg = annotationOn(TunedProof::class.java, "unwind8") // explicit unwind=8
            assertEquals(8, BmcProofExtension.resolveUnwind(cfg))
        } finally {
            restore("bmc.unwind", prev)
        }
    }

    // --- engine-infrastructure failure classifies as UNKNOWN, not REFUTED -----------------

    @Test
    fun engineInfraFailure_classifiesAsUndecidedUnknown_notRefuted() {
        // The exact engine-infrastructure failure shape: BundledEngine.extract() / process start throws a
        // non-verdict IllegalStateException out of the engine-run path. That MUST become UNKNOWN
        // (BmcUndecidedError) — there is no counterexample, the engine simply couldn't run.
        val cause = IllegalStateException(
                "Could not start JBMC process: jbmc --function P.p")
        val err = BmcProofExtension.engineInfraUndecided("jbmc", "pkg.P.p", cause)

        // BmcUndecidedError IS-A BmcVerificationError, but the (UNKNOWN) tag is what the runner line
        // keys on to print UNKNOWN instead of REFUTED (BmcPlugin.isUndecided).
        assertTrue(err is org.bmc4j.engine.BmcUndecidedError)
        assertTrue(err.message!!.contains("(UNKNOWN)"),
                "message must carry the (UNKNOWN) verdict tag so the runner prints UNKNOWN: " + err.message)
        assertTrue(err.message!!.contains("NOT a refutation"),
                "must frame it as not-a-refutation: " + err.message)
        assertTrue(err.message!!.contains("pkg.P.p"), err.message)
        assertEquals(cause, err.cause, "the original infrastructure cause is preserved for diagnosis")
        // Crucially it does NOT read as a refutation.
        assertFalse(err.message!!.contains("refuted "), err.message)
    }

    @Test
    fun parsedFailureProperty_staysRefuted_notUndecided() {
        // The other side of the classification split: a real parsed JBMC counterexample (a FAILURE property) comes
        // back as a refuted JbmcResult and must stay REFUTED — a BmcVerificationError that is NOT the
        // UNKNOWN subtype and does NOT carry the (UNKNOWN) tag.
        val v = org.bmc4j.engine.JbmcResult.Violation(
                "assertion failed: x > 0", null, 0, listOf(), listOf())
        val refuted = org.bmc4j.engine.JbmcResult(false, listOf(v), "{}")
        val proofMethod = MixedSolverProofs::class.java.getDeclaredMethod("usesDefaultSolver")

        val err = BmcProofExtension.toError("jbmc", "pkg.P.p", refuted, proofMethod)

        assertFalse(err is org.bmc4j.engine.BmcUndecidedError,
                "a real counterexample is REFUTED, never the UNKNOWN subtype")
        assertFalse(err.message!!.contains("(UNKNOWN)"),
                "a refutation must not carry the UNKNOWN tag: " + err.message)
        assertTrue(err.message!!.lowercase().contains("refuted"),
                "a real counterexample reads as refuted: " + err.message)
    }

    @Test
    fun pinnedUnwindingFiring_getsTentativeDataDependentHint_namingTheLoop() {
        // A PINNED under-unwind that names a loop can't be told apart from a symbolic bound without the
        // AUTO climb, so toError hedges: it names the loop and says raising unwind MIGHT help, but if the
        // trip count is data-dependent no unwind ever will — drop the pin and let auto-unwind decide.
        val pinned = org.bmc4j.engine.JbmcResult.unknown(
                org.bmc4j.engine.UnknownKind.UNWINDING_ASSERTION, "unwind bound is too small ...", "{}")
                .withUnwindingLoops(listOf(org.bmc4j.engine.JbmcResult.UnwindingLoop(
                        "okio.Buffer.readDecimalLong", "Buffer.kt", 882)))
        val err = BmcProofExtension.toError("jbmc", "pkg.P.p", pinned, null)
        val msg = err.message!!
        assertTrue(msg.contains("okio.Buffer.readDecimalLong (Buffer.kt:882)"), "names the loop: $msg")
        assertTrue(msg.contains("DATA-DEPENDENT"), "raises the data-dependent possibility: $msg")
        assertTrue(msg.contains("auto-unwind"), "advises dropping the pin: $msg")
    }

    @Test
    fun cappedAutoDiagnostic_isNotDoubledByTheTentativeHint() {
        // When the reason is ALREADY the decisive AUTO diagnostic (it says DATA-DEPENDENT), the tentative
        // pinned hint must NOT be appended on top of it — no double "DATA-DEPENDENT" block.
        val capped = org.bmc4j.engine.AutoUnwind.cappedUnknown(16, listOf(
                org.bmc4j.engine.JbmcResult.UnwindingLoop("okio.Buffer.readDecimalLong", "Buffer.kt", 882)))
        assertEquals(null, BmcProofExtension.tentativeDataDependentNote(capped, capped.undecidedReason),
                "the decisive AUTO diagnostic already covers it — no tentative double-up")
    }

    @Test
    fun nonUnwindingUnknown_getsNoDataDependentHint() {
        val timeout = org.bmc4j.engine.JbmcResult.unknownTimeout("out of time", "{}")
        assertEquals(null, BmcProofExtension.tentativeDataDependentNote(timeout, timeout.undecidedReason),
                "a timeout is not an unwinding firing — no loop to name")
    }

    // --- expected-verdict assertions (expect = REFUTED/UNKNOWN/VACUOUS) -----------------------

    @Test
    fun actualVerdict_mapsAllFourOutcomes() {
        assertEquals(org.bmc4j.Verdict.VERIFIED, BmcProofExtension.actualVerdict(
                org.bmc4j.engine.JbmcResult(true, listOf(), "{}")))
        assertEquals(org.bmc4j.Verdict.REFUTED, BmcProofExtension.actualVerdict(refutedResult()))
        assertEquals(org.bmc4j.Verdict.UNKNOWN, BmcProofExtension.actualVerdict(
                org.bmc4j.engine.JbmcResult.unknown(
                        org.bmc4j.engine.UnknownKind.SOLVER_GAVE_UP, "the solver returned undecided", "{}")))
        // Vacuity is carried as a flavour of REFUTED internally but is its own expectation.
        val vacuous = org.bmc4j.engine.JbmcResult(false, listOf(), "{}", true)
        assertEquals(org.bmc4j.Verdict.VACUOUS, BmcProofExtension.actualVerdict(vacuous))
    }

    @Test
    fun expectedVerdictMatch_swallowsTheFramedError() {
        val framed = org.bmc4j.engine.BmcVerificationError("JBMC refuted pkg.P.p")
        assertDoesNotThrow {
            BmcProofExtension.enforceExpectation(
                    "pkg.P.p", org.bmc4j.Verdict.REFUTED, org.bmc4j.Verdict.REFUTED, framed)
        }
    }

    // --- Observability: the counterexample reaches the summary on the EXPECTED-match (pass) path ----
    // The `enforce` seam captures the framed counterexample onto ProofOutcome.detail BEFORE
    // enforcement swallows (pass) or rethrows (fail) the error — so a pinned expect=REFUTED/UNKNOWN
    // proof that PASSES still records its counterexample, instead of the old `detail = null`. This is
    // pure record data: it must never alter the swallow/rethrow (verdict) behavior.

    @Test
    fun enforce_recordsCounterexampleDetail_onExpectedRefutedMatch_andStillSwallows() {
        val ext = BmcProofExtension()
        val outcome = BmcProofExtension.ProofOutcome()
        val framed = org.bmc4j.engine.BmcVerificationError("score = 100 (Foo.java:42)")
        // expect=REFUTED, actual=REFUTED -> the proof PASSES (no throw)...
        assertDoesNotThrow {
            ext.enforce(outcome, "pkg.P.p", org.bmc4j.Verdict.REFUTED, org.bmc4j.Verdict.REFUTED, framed)
        }
        // ...yet the counterexample is now captured for the summary's `detail`.
        assertEquals("score = 100 (Foo.java:42)", outcome.detail,
                "a pinned expect=REFUTED pass must still record its counterexample, not lose it")
    }

    @Test
    fun enforce_recordsDetail_onExpectedUnknownMatch_andStillSwallows() {
        val ext = BmcProofExtension()
        val outcome = BmcProofExtension.ProofOutcome()
        val genuine = BmcUndecidedError("JBMC could not decide pkg.P.p (UNKNOWN)")
        assertFalse(genuine.isEngineInfrastructure())
        assertDoesNotThrow {
            ext.enforce(outcome, "pkg.P.p", org.bmc4j.Verdict.UNKNOWN, org.bmc4j.Verdict.UNKNOWN, genuine)
        }
        assertEquals("JBMC could not decide pkg.P.p (UNKNOWN)", outcome.detail,
                "a pinned expect=UNKNOWN pass must record its undecided-reason detail too")
    }

    @Test
    fun enforce_recordsDetail_onMismatchRethrow_too_butIsObservabilityOnly() {
        // On a MISMATCH (rethrow) the detail is still captured (it feeds the FAIL-path record), and the
        // throw/verdict behavior is unchanged from the static enforceExpectation. Observability-only:
        // setting detail did not suppress the rethrow.
        val ext = BmcProofExtension()
        val outcome = BmcProofExtension.ProofOutcome()
        val framed = org.bmc4j.engine.BmcVerificationError("JBMC refuted pkg.P.p")
        assertThrows(org.bmc4j.engine.BmcVerificationError::class.java) {
            ext.enforce(outcome, "pkg.P.p", org.bmc4j.Verdict.VERIFIED, org.bmc4j.Verdict.REFUTED, framed)
        }
        assertEquals("JBMC refuted pkg.P.p", outcome.detail)
    }

    @Test
    fun proofOutcome_detailIsNullByDefault_soAVerifiedPassRecordsEmptyDetail() {
        // A VERIFIED pass never goes through `enforce` (only non-VERIFIED verdicts do), so detail stays
        // null and the summary's `detail` is empty — the pre-change behavior on the green path.
        val outcome = BmcProofExtension.ProofOutcome()
        assertEquals(org.bmc4j.Verdict.VERIFIED, outcome.verdict)
        assertEquals(null, outcome.detail, "a VERIFIED pass leaves detail null (empty in the summary)")
    }

    @Test
    fun defaultExpectation_rethrowsTheFramedErrorUnchanged() {
        val framed = org.bmc4j.engine.BmcVerificationError("JBMC refuted pkg.P.p")
        val thrown = assertThrows(org.bmc4j.engine.BmcVerificationError::class.java) {
            BmcProofExtension.enforceExpectation(
                    "pkg.P.p", org.bmc4j.Verdict.VERIFIED, org.bmc4j.Verdict.REFUTED, framed)
        }
        assertEquals(framed, thrown, "expect=VERIFIED must behave exactly as before: the framed error")
    }

    @Test
    fun verdictMismatch_namesBothVerdicts_andKeepsTheCause() {
        val framed = org.bmc4j.engine.BmcVerificationError("JBMC refuted pkg.P.p")
        val err = assertThrows(org.bmc4j.engine.BmcVerificationError::class.java) {
            BmcProofExtension.enforceExpectation(
                    "pkg.P.p", org.bmc4j.Verdict.VACUOUS, org.bmc4j.Verdict.REFUTED, framed)
        }
        assertTrue(err.message!!.contains("expected VACUOUS"), err.message)
        assertTrue(err.message!!.contains("got REFUTED"), err.message)
        assertEquals(framed, err.cause, "the real verdict's framing is preserved as the cause")
    }

    @Test
    fun failOnPurposeProofGoingGreen_failsLoudly() {
        val err = assertThrows(org.bmc4j.engine.BmcVerificationError::class.java) {
            throw BmcProofExtension.expectationMismatch(
                    "pkg.P.p", org.bmc4j.Verdict.REFUTED, org.bmc4j.Verdict.VERIFIED, null)
        }
        assertTrue(err.message!!.contains("expected REFUTED"), err.message)
        assertTrue(err.message!!.contains("got VERIFIED"), err.message)
        assertTrue(err.message!!.contains("stopped being"),
                "the dangerous drift (false claim no longer refutable) is called out: " + err.message)
    }

    @Test
    fun engineInfrastructureUnknown_neverSatisfiesExpectedUnknown() {
        // A broken engine must not masquerade as an undecidability demo.
        val infra = BmcProofExtension.engineInfraUndecided(
                "jbmc", "pkg.P.p", IllegalStateException("could not extract engine"))
        assertTrue(infra.isEngineInfrastructure())
        val err = assertThrows(org.bmc4j.engine.BmcVerificationError::class.java) {
            BmcProofExtension.enforceExpectation(
                    "pkg.P.p", org.bmc4j.Verdict.UNKNOWN, org.bmc4j.Verdict.UNKNOWN, infra)
        }
        assertTrue(err.message!!.contains("not a real UNKNOWN"), err.message)
        assertEquals(infra, err.cause)
    }

    @Test
    fun genuineUnknown_satisfiesExpectedUnknown() {
        // A real undecided verdict (timeout / solver gave up) is exactly what expect=UNKNOWN declares.
        val genuine = BmcUndecidedError("JBMC could not decide pkg.P.p (UNKNOWN)")
        assertFalse(genuine.isEngineInfrastructure())
        assertDoesNotThrow {
            BmcProofExtension.enforceExpectation(
                    "pkg.P.p", org.bmc4j.Verdict.UNKNOWN, org.bmc4j.Verdict.UNKNOWN, genuine)
        }
    }

    // --- TIMEOUT: the structured subtype of UNKNOWN -------------------------------------------

    @Test
    fun timedOutResult_mapsToTimeoutVerdict_otherUnknownsStayUnknown() {
        assertEquals(org.bmc4j.Verdict.TIMEOUT, BmcProofExtension.actualVerdict(
                org.bmc4j.engine.JbmcResult.unknownTimeout("timed out after 1s", "{}")))
        assertEquals(org.bmc4j.Verdict.UNKNOWN, BmcProofExtension.actualVerdict(
                org.bmc4j.engine.JbmcResult.unknown(
                        org.bmc4j.engine.UnknownKind.ENGINE_CRASH, "engine exited 6", "{}")))
    }

    @Test
    fun expectedTimeout_passesOnTimeout_butRejectsOtherUnknowns() {
        val framed = BmcUndecidedError("JBMC could not decide pkg.P.p (UNKNOWN)")
        // The budget actually fired -> expect=TIMEOUT is satisfied.
        assertDoesNotThrow {
            BmcProofExtension.enforceExpectation(
                    "pkg.P.p", org.bmc4j.Verdict.TIMEOUT, org.bmc4j.Verdict.TIMEOUT, framed)
        }
        // A non-timeout undecided (solver crash, unparseable output) must NOT satisfy expect=TIMEOUT.
        val err = assertThrows(org.bmc4j.engine.BmcVerificationError::class.java) {
            BmcProofExtension.enforceExpectation(
                    "pkg.P.p", org.bmc4j.Verdict.TIMEOUT, org.bmc4j.Verdict.UNKNOWN, framed)
        }
        assertTrue(err.message!!.contains("expected TIMEOUT"), err.message)
        assertTrue(err.message!!.contains("got UNKNOWN"), err.message)
    }

    @Test
    fun expectedUnknown_subsumesTimeout() {
        // UNKNOWN is the umbrella: a timeout is one way to be undecided, so expect=UNKNOWN accepts it.
        val framed = BmcUndecidedError("JBMC could not decide pkg.P.p (UNKNOWN)")
        assertDoesNotThrow {
            BmcProofExtension.enforceExpectation(
                    "pkg.P.p", org.bmc4j.Verdict.UNKNOWN, org.bmc4j.Verdict.TIMEOUT, framed)
        }
    }

    // --- Residual-invokedynamic demotion (REFUTED + havoc'd marker -> UNKNOWN) ---

    @Test
    fun residualIndyMarkers_extractsAndDedupesOnlyMarkerStubs() {
        val refuted = org.bmc4j.engine.JbmcResult(false, listOf(
                org.bmc4j.engine.JbmcResult.Violation("boom", "C.java", 1, listOf(), listOf())), "raw")
                .withStubbedMethods(listOf(
                        "java.util.Formatter.format",
                        "org.bmc4j.analysis.ResidualInvokedynamic.enumSwitch__SwitchBootstraps",
                        "org.bmc4j.analysis.ResidualInvokedynamic.enumSwitch__SwitchBootstraps",
                        "org.bmc4j.analysis.ResidualInvokedynamic.toString__ObjectMethods"))
        assertEquals(listOf(
                "org.bmc4j.analysis.ResidualInvokedynamic.enumSwitch__SwitchBootstraps",
                "org.bmc4j.analysis.ResidualInvokedynamic.toString__ObjectMethods"),
                BmcProofExtension.residualIndyMarkers(refuted),
                "marker stubs only, deduped, order-stable; ordinary stubs are not markers")
    }

    @Test
    fun residualIndyUndecided_isNonInfraUnknown_namingTheSites_andSatisfiesExpectUnknown() {
        val err = BmcProofExtension.residualIndyUndecided("jbmc", "pkg.P.p",
                listOf("org.bmc4j.analysis.ResidualInvokedynamic.enumSwitch__SwitchBootstraps"))
        assertTrue(err.message!!.contains("(UNKNOWN)"), err.message)
        assertTrue(err.message!!.contains("enumSwitch__SwitchBootstraps"), err.message)
        assertTrue(err.message!!.contains("NOT reported as a refutation"), err.message)
        assertFalse(err.isEngineInfrastructure(),
                "an analysis-limit UNKNOWN must satisfy expect=UNKNOWN (infra must not)")
        // The supported pin: a proof deliberately exercising a residual site declares expect=UNKNOWN.
        assertDoesNotThrow {
            BmcProofExtension.enforceExpectation(
                    "pkg.P.p", org.bmc4j.Verdict.UNKNOWN, org.bmc4j.Verdict.UNKNOWN, err)
        }
    }

    // --- Link-failure demotion (REFUTED + nondet stub of a PRESENT class -> UNKNOWN) ---

    @Test
    fun ownerClassOf_dropsParamsAndTrailingMethod() {
        assertEquals("kotlin.ranges.RangesKt",
                BmcProofExtension.ownerClassOf("kotlin.ranges.RangesKt.coerceAtMost(long, long)"))
        assertEquals("pkg.C", BmcProofExtension.ownerClassOf("pkg.C.m()"))
        // No method part -> null (can't resolve an owner).
        assertEquals(null, BmcProofExtension.ownerClassOf("bare"))
    }

    @Test
    fun classIsPresentOnClasspath_findsClassInADirectoryEntry_butNotAMissingOne(
            @org.junit.jupiter.api.io.TempDir dir: java.nio.file.Path) {
        writeModelClass(dir, "kotlin.ranges.RangesKt")
        assertTrue(BmcProofExtension.classIsPresentOnClasspath("kotlin.ranges.RangesKt", dir.toString()))
        assertFalse(BmcProofExtension.classIsPresentOnClasspath("kotlin.ranges.Absent", dir.toString()))
    }

    @Test
    fun classIsPresentOnClasspath_findsClassInsideAJarEntry(
            @org.junit.jupiter.api.io.TempDir dir: java.nio.file.Path) {
        val jar = dir.resolve("lib.jar")
        java.util.zip.ZipOutputStream(java.nio.file.Files.newOutputStream(jar)).use { zos ->
            zos.putNextEntry(java.util.zip.ZipEntry("kotlin/ranges/RangesKt.class"))
            zos.write(byteArrayOf(0xCA.toByte(), 0xFE.toByte()))
            zos.closeEntry()
        }
        assertTrue(BmcProofExtension.classIsPresentOnClasspath("kotlin.ranges.RangesKt", jar.toString()))
        assertFalse(BmcProofExtension.classIsPresentOnClasspath("kotlin.ranges.Absent", jar.toString()))
    }

    @Test
    fun linkFailuresPresentOnClasspath_keepsPresentClasses_dropsAbsentOnes(
            @org.junit.jupiter.api.io.TempDir dir: java.nio.file.Path) {
        writeModelClass(dir, "kotlin.ranges.RangesKt")
        // The harvested fact: a refutation ran through stubs of two members. Only the one whose class
        // is on the classpath is a (demotable) link failure; the absent one stays an ordinary stub.
        val refuted = refutedResult().withLinkFailureStubs(listOf(
                "kotlin.ranges.RangesKt.coerceAtMost(long, long)",
                "com.absent.Gone.compute(int)"))
        assertEquals(listOf("kotlin.ranges.RangesKt.coerceAtMost(long, long)"),
                BmcProofExtension.linkFailuresPresentOnClasspath(refuted, dir.toString()),
                "only the present-on-classpath stub member demotes the refutation")
    }

    @Test
    fun linkFailuresPresentOnClasspath_isEmptyForAGenuineRefutationWithNoStub(
            @org.junit.jupiter.api.io.TempDir dir: java.nio.file.Path) {
        // A genuine refutation has no harvested link-failure stubs -> nothing to demote, stays REFUTED.
        assertTrue(BmcProofExtension.linkFailuresPresentOnClasspath(refutedResult(), dir.toString()).isEmpty())
    }

    @Test
    fun linkFailureUndecided_isInfraUnknown_namesMember_andDoesNotSatisfyExpectUnknown() {
        val err = BmcProofExtension.linkFailureUndecided("jbmc", "proofs.kotlinranges.RangeLaws.coerceAtMost_long_is_min",
                listOf("kotlin.ranges.RangesKt.coerceAtMost(long, long)"))
        assertTrue(err.message!!.contains("(UNKNOWN)"), err.message)
        assertTrue(err.message!!.contains("kotlin.ranges.RangesKt.coerceAtMost"), err.message)
        assertTrue(err.message!!.contains("link failure"), err.message)
        assertFalse(err.message!!.contains("refuted "),
                "a link failure is UNKNOWN, never a refutation: " + err.message)
        // A transient link failure is engine infrastructure: it must NOT satisfy expect=UNKNOWN.
        assertTrue(err.isEngineInfrastructure())
        val rejected = assertThrows(org.bmc4j.engine.BmcVerificationError::class.java) {
            BmcProofExtension.enforceExpectation(
                    "pkg.P.p", org.bmc4j.Verdict.UNKNOWN, org.bmc4j.Verdict.UNKNOWN, err)
        }
        assertTrue(rejected.message!!.contains("not a real UNKNOWN"), rejected.message)
    }

    @Test
    fun linkFailuresToDemote_keepsAnExpectedRefutationEvenWithAStubInTrace(
            @org.junit.jupiter.api.io.TempDir dir: java.nio.file.Path) {
        // A demo that PINS expect=REFUTED and matches, whose trace happens to run through a nondet stub
        // of a PRESENT class (e.g. a lateinit getter), is getting its intended verdict — the link-failure
        // demotion must NOT fire and steal that pass. Nothing to demote -> stays REFUTED/passes.
        writeModelClass(dir, "example.lateinitprops.Session")
        val refuted = refutedResult().withLinkFailureStubs(listOf("example.lateinitprops.Session.getUser()"))
        assertTrue(BmcProofExtension.linkFailuresToDemote(org.bmc4j.Verdict.REFUTED, refuted, dir.toString()).isEmpty(),
                "an expect=REFUTED match must not be demoted by a stub in its trace")
    }

    @Test
    fun linkFailuresToDemote_demotesAnUNEXPECTEDRefutationWithAPresentStub(
            @org.junit.jupiter.api.io.TempDir dir: java.nio.file.Path) {
        // The transient-flake case this guards: a refutation surfacing where none was expected
        // (expect=VERIFIED), running through a nondet stub of a PRESENT class, IS demoted to UNKNOWN so
        // a clean proof doesn't go red on a transient link failure (existing behavior).
        writeModelClass(dir, "example.lateinitprops.Session")
        val refuted = refutedResult().withLinkFailureStubs(listOf("example.lateinitprops.Session.getUser()"))
        assertEquals(listOf("example.lateinitprops.Session.getUser()"),
                BmcProofExtension.linkFailuresToDemote(org.bmc4j.Verdict.VERIFIED, refuted, dir.toString()),
                "an unexpected refutation through a present-class stub still demotes to UNKNOWN")
    }

    @Test
    fun linkFailuresToDemote_demotesAnUnresolvedInterfaceDevirtualization(
            @org.junit.jupiter.api.io.TempDir dir: java.nio.file.Path) {
        // Lever (a) safety net: a "no body for callee java.util.List.size()" the parser folds into
        // linkFailureStubs (an invokeinterface the engine could not bind to its present concrete override)
        // has owner java.util.List, which IS present (a model) -> the would-be REFUTED demotes to a
        // member-named UNKNOWN rather than leaking a false refutation on the havoc artifact.
        writeModelClass(dir, "java.util.List")
        val refuted = refutedResult().withLinkFailureStubs(listOf("java.util.List.size()"))
        assertEquals(listOf("java.util.List.size()"),
                BmcProofExtension.linkFailuresToDemote(org.bmc4j.Verdict.VERIFIED, refuted, dir.toString()),
                "an unresolved interface devirtualization (owner interface present) demotes to UNKNOWN")
    }

    // --- branch-decompose localized-cost report (pure rendering) --------------

    /** A leaf that dominates the SAT-clause metric (the preferred signal) yields the
     *  "cost-follows-extraction = localized" headline naming the leaf as the cold branch's hot spot. */
    @Test
    fun localized_cost_report_localizes_to_the_dominant_leaf_on_sat_clauses() {
        val rows = listOf(
                BmcProofExtension.CostRow("parent (assume(!cond))", -1, 100L, 2L, 3000L, 5.0, "VERIFIED"),
                BmcProofExtension.CostRow("leaf (assume(cond))", 0, 9000L, 40L, 3000L, 6.0, "VERIFIED"))
        val report = BmcProofExtension.buildLocalizedCostReport("Demo.proof", rows)
        assertTrue(report.contains("dominance metric: SAT clauses"),
                "SAT clauses is the preferred discriminating metric: $report")
        assertTrue(report.contains("cost-follows-extraction = localized"),
                "a dominant leaf must localize: $report")
        assertTrue(report.contains("leaf (assume(cond))") && report.contains("the cold branch"),
                "the headline must name the leaf as the cold-branch hot spot: $report")
    }

    /** When SAT clauses are equal but program-steps differ, the report falls THROUGH to steps (clauses
     *  don't discriminate) and still localizes. */
    @Test
    fun localized_cost_report_falls_through_to_a_discriminating_metric() {
        val rows = listOf(
                BmcProofExtension.CostRow("parent", -1, 500L, 5L, 200L, 5.0, "VERIFIED"),
                BmcProofExtension.CostRow("leaf", 0, 500L, 5L, 4000L, 5.1, "VERIFIED"))
        val report = BmcProofExtension.buildLocalizedCostReport("Demo.proof", rows)
        assertTrue(report.contains("dominance metric: formula size (program steps)"),
                "equal clauses/VCCs must fall through to the discriminating steps metric: $report")
        assertTrue(report.contains("cost-follows-extraction = localized"), report)
    }

    /** Equal metrics across the board: no hot spot, the report says cost is SPREAD (and never fabricates
     *  a localization). The fixed-floor wall-clock-equal case real proofs hit. */
    @Test
    fun localized_cost_report_reports_spread_when_no_metric_discriminates() {
        val rows = listOf(
                BmcProofExtension.CostRow("parent", -1, 720L, 2L, 2493L, 5.0, "VERIFIED"),
                BmcProofExtension.CostRow("leaf", 0, 720L, 2L, 2493L, 5.0, "VERIFIED"))
        val report = BmcProofExtension.buildLocalizedCostReport("Demo.proof", rows)
        assertTrue(report.contains("cost is spread across both obligations"),
                "equal costs must report spread, never a fabricated hot spot: $report")
        assertFalse(report.contains("cost-follows-extraction = localized"), report)
        // With no engine signal discriminating, the dominance metric falls back to wall-clock.
        assertTrue(report.contains("dominance metric: wall-clock"), report)
    }

    /** A run with NO engine size signal (a refuting/UNKNOWN leaf) still renders: its line shows the
     *  verdict in place of a size, and the dominance metric falls back to wall-clock. */
    @Test
    fun localized_cost_report_handles_a_run_with_no_engine_size() {
        val rows = listOf(
                BmcProofExtension.CostRow("parent", -1, 800L, 3L, 2500L, 5.0, "VERIFIED"),
                BmcProofExtension.CostRow("leaf", 0, null, null, null, 12.0, "REFUTED"))
        val report = BmcProofExtension.buildLocalizedCostReport("Demo.proof", rows)
        assertTrue(report.contains("(no engine size: REFUTED)"),
                "a run with no size signal must show its verdict in the size column: $report")
        assertTrue(report.contains("dominance metric: wall-clock"),
                "mixed size availability falls back to wall-clock: $report")
    }

    companion object {
        /** Write an empty .class so the model scanner counts `fqn` as present on the classpath. */
        private fun writeModelClass(root: java.nio.file.Path, fqn: String) {
            val p = root.resolve(fqn.replace('.', '/') + ".class")
            java.nio.file.Files.createDirectories(p.parent)
            java.nio.file.Files.write(p, byteArrayOf(0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte()))
        }

        private fun captureModelPolicy(entryFunction: String, models: String, userModelsPath: String,
                                       strict: Boolean): String {
            val pModels = System.getProperty("bmc.models")
            val pUser = System.getProperty("bmc.userModels")
            val pStrict = System.getProperty("bmc.strictModels")
            val realOut = System.out
            val buf = java.io.ByteArrayOutputStream()
            try {
                setOrClear("bmc.models", models)
                setOrClear("bmc.userModels", userModelsPath)
                setOrClear("bmc.strictModels", if (strict) "true" else null)
                System.setOut(java.io.PrintStream(buf, true, java.nio.charset.StandardCharsets.UTF_8))
                BmcProofExtension.applyModelPolicy(entryFunction)
                return buf.toString(java.nio.charset.StandardCharsets.UTF_8)
            } finally {
                System.setOut(realOut)
                restore("bmc.models", pModels)
                restore("bmc.userModels", pUser)
                restore("bmc.strictModels", pStrict)
            }
        }

        private fun setOrClear(key: String, value: String?) {
            if (value == null) {
                System.clearProperty(key)
            } else {
                System.setProperty(key, value)
            }
        }

        private fun refutedResult(): org.bmc4j.engine.JbmcResult {
            val v = org.bmc4j.engine.JbmcResult.Violation(
                    "assertion failed: x > 0", null, 0, listOf(), listOf())
            return org.bmc4j.engine.JbmcResult(false, listOf(v), "{}")
        }

        private fun annotationOn(type: Class<*>, method: String): BmcProof {
            val m = type.getDeclaredMethod(method)
            return m.getAnnotation(BmcProof::class.java)
        }

        private fun restore(key: String, prev: String?) {
            if (prev == null) {
                System.clearProperty(key)
            } else {
                System.setProperty(key, prev)
            }
        }
    }
}
