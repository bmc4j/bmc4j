package org.bmc4j.junit;

import org.bmc4j.BmcProof;
import org.bmc4j.engine.BmcUndecidedError;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link BmcProofExtension}'s configuration parsing: the effective solver /
 * maxStringLength / timeoutSeconds resolution (per-proof override then build default), and that a
 * malformed int-valued {@code bmc.*} property fails loudly (throws, naming the property + bad value)
 * instead of silently using the default.
 *
 * <p>The sample fixtures below are plain static nested classes (NOT {@code @Nested}), so JUnit does
 * not discover or run their {@code @BmcProof} methods — they exist only as reflection targets.
 */
class BmcProofExtensionTest {

    // --- Fixtures: reflection-only targets, never executed by JUnit -----------

    @Disabled("reflection-only fixture; not a runnable proof suite")
    static class MixedSolverProofs {
        @BmcProof
        void usesDefaultSolver() { }

        @BmcProof(solver = "z3")
        void usesZ3() { }

        @BmcProof
        void alsoDefaultSolver() { }
    }

    @Disabled("reflection-only fixture; not a runnable proof suite")
    static class TunedProof {
        @BmcProof(unwind = 8)
        void unwind8() { }
    }

    @Disabled("reflection-only fixture; not a runnable proof suite")
    static class StubAckProofs {
        @BmcProof(allowStubs = {"java.util.Formatter.*", " java.util.Locale.getDefault "})
        void acked() { }

        @BmcProof
        void unacked() { }
    }

    // --- Nondet-stub policy ---------------------------------------

    @Test
    void effectiveAllowStubs_mergesAnnotationAndBuildProp_trimmed() throws Exception {
        String prev = System.getProperty("bmc.allowStubs");
        System.setProperty("bmc.allowStubs", "java.time.*, com.x.Y.z");
        try {
            List<String> allow = BmcProofExtension.effectiveAllowStubs(
                    annotationOn(StubAckProofs.class, "acked"));
            assertTrue(allow.contains("java.util.Formatter.*"), allow.toString());
            assertTrue(allow.contains("java.util.Locale.getDefault"), "whitespace trimmed: " + allow);
            assertTrue(allow.contains("java.time.*") && allow.contains("com.x.Y.z"),
                    "build-wide -Dbmc.allowStubs entries are merged: " + allow);
        } finally {
            restore("bmc.allowStubs", prev);
        }
    }

    @Test
    void applyStubPolicy_lenient_isGreen_evenWithUnacknowledgedStub() throws Exception {
        String prevStrict = System.getProperty("bmc.strictStubs");
        System.clearProperty("bmc.strictStubs");
        try {
            // Lenient (default): an unacknowledged stub prints a footnote but does NOT throw.
            assertDoesNotThrow(() -> BmcProofExtension.applyStubPolicy(
                    "pkg.T.unacked", annotationOn(StubAckProofs.class, "unacked"),
                    List.of("java.util.Formatter.format")));
        } finally {
            restore("bmc.strictStubs", prevStrict);
        }
    }

    @Test
    void applyStubPolicy_strict_throwsUnknownForUnacknowledged_butNotForAcknowledged() throws Exception {
        String prevStrict = System.getProperty("bmc.strictStubs");
        System.setProperty("bmc.strictStubs", "true");
        try {
            BmcUndecidedError err = assertThrows(BmcUndecidedError.class,
                    () -> BmcProofExtension.applyStubPolicy("pkg.T.unacked",
                            annotationOn(StubAckProofs.class, "unacked"),
                            List.of("java.util.Formatter.format")));
            assertTrue(err.getMessage().contains("(UNKNOWN)"), err.getMessage());
            assertTrue(err.getMessage().contains("java.util.Formatter.format"), err.getMessage());

            // Acknowledged (allowStubs covers it) -> no throw even in strict mode.
            assertDoesNotThrow(() -> BmcProofExtension.applyStubPolicy("pkg.T.acked",
                    annotationOn(StubAckProofs.class, "acked"),
                    List.of("java.util.Formatter.format")));
        } finally {
            restore("bmc.strictStubs", prevStrict);
        }
    }

    @Test
    void applyStubPolicy_noStubs_isNoOp() {
        assertDoesNotThrow(() -> BmcProofExtension.applyStubPolicy("pkg.T.p", null, List.of()));
        assertFalse(Boolean.parseBoolean(System.getProperty("bmc.strictStubs", "false")));
    }

    // --- User-model trust policy ----------------------------------------

    /** Write an empty .class so the model scanner counts {@code fqn} as present on the classpath. */
    private static void writeModelClass(java.nio.file.Path root, String fqn) throws Exception {
        java.nio.file.Path p = root.resolve(fqn.replace('.', '/') + ".class");
        java.nio.file.Files.createDirectories(p.getParent());
        java.nio.file.Files.write(p, new byte[] {(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE});
    }

    private static String captureModelPolicy(String entryFunction, String models, String userModelsPath,
                                             boolean strict) {
        String pModels = System.getProperty("bmc.models");
        String pUser = System.getProperty("bmc.userModels");
        String pStrict = System.getProperty("bmc.strictModels");
        java.io.PrintStream realOut = System.out;
        java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
        try {
            setOrClear("bmc.models", models);
            setOrClear("bmc.userModels", userModelsPath);
            setOrClear("bmc.strictModels", strict ? "true" : null);
            System.setOut(new java.io.PrintStream(buf, true, java.nio.charset.StandardCharsets.UTF_8));
            BmcProofExtension.applyModelPolicy(entryFunction);
            return buf.toString(java.nio.charset.StandardCharsets.UTF_8);
        } finally {
            System.setOut(realOut);
            restore("bmc.models", pModels);
            restore("bmc.userModels", pUser);
            restore("bmc.strictModels", pStrict);
        }
    }

    private static void setOrClear(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }

    @Test
    void applyModelPolicy_domainModel_footnotesRationaleOnGreenProof(@org.junit.jupiter.api.io.TempDir
                                                                     java.nio.file.Path dir) throws Exception {
        writeModelClass(dir, "acme.NoCollisionMap");
        String models = org.bmc4j.engine.ModelManifest.serialize(List.of(
                org.bmc4j.engine.UserModel.domain("acme.NoCollisionMap", "no key collisions")));
        String out = captureModelPolicy("pkg.T.p", models, dir.toString(), false);
        assertTrue(out.contains("acme.NoCollisionMap"), out);
        assertTrue(out.contains("no key collisions"),
                "a green proof resting on a domain model must footnote its rationale: " + out);
        assertTrue(out.contains("domain model"), out);
    }

    @Test
    void applyModelPolicy_undeclaredModel_isGreenFootnoteInLenient_butUnknownInStrict(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        writeModelClass(dir, "acme.Sneaky");
        // Lenient: no declaration -> loud footnote, but green (does not throw).
        String lenient = captureModelPolicy("pkg.T.p", "", dir.toString(), false);
        assertTrue(lenient.contains("UNDECLARED model acme.Sneaky"), lenient);

        // Strict: an undeclared present model -> UNKNOWN.
        BmcUndecidedError err = assertThrows(BmcUndecidedError.class,
                () -> captureModelPolicy("pkg.T.p", "", dir.toString(), true));
        assertTrue(err.getMessage().contains("(UNKNOWN)"), err.getMessage());
        assertTrue(err.getMessage().contains("acme.Sneaky"), err.getMessage());
        assertFalse(err.getMessage().contains("refuted "),
                "an undeclared override is UNKNOWN, never a refutation: " + err.getMessage());
    }

    @Test
    void applyModelPolicy_declaredModel_staysGreenEvenInStrict(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        writeModelClass(dir, "acme.FastList");
        String models = org.bmc4j.engine.ModelManifest.serialize(List.of(
                org.bmc4j.engine.UserModel.conformant("acme.FastList")));
        // Declared -> no throw even in strict mode (it's the UNDECLARED ones strictModels punishes).
        assertDoesNotThrow(() -> captureModelPolicy("pkg.T.p", models, dir.toString(), true));
    }

    @Test
    void applyModelPolicy_overrideOfBundledModel_warnsLoudly(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        writeModelClass(dir, "java.util.HashMap");
        String models = org.bmc4j.engine.ModelManifest.serialize(List.of(
                org.bmc4j.engine.UserModel.domain("java.util.HashMap", "bounded to 32 entries")));
        String out = captureModelPolicy("pkg.T.p", models, dir.toString(), false);
        assertTrue(out.contains("WARNING"), "shadowing a bundled model must warn: " + out);
        assertTrue(out.contains("java.util.HashMap"), out);
    }

    @Test
    void applyModelPolicy_noUserModels_isNoOp() {
        assertDoesNotThrow(() -> captureModelPolicy("pkg.T.p", "", "", false));
    }

    @Disabled("reflection-only fixture; not a runnable proof suite")
    static class MixedStringLengthProofs {
        @BmcProof
        void usesDefaultLength() { }

        @BmcProof(maxStringLength = 4)
        void usesLength4() { }

        @BmcProof
        void alsoDefaultLength() { }
    }

    @Disabled("reflection-only fixture; not a runnable proof suite")
    static class MixedTimeoutProofs {
        @BmcProof
        void usesDefaultTimeout() { }

        @BmcProof(timeoutSeconds = 5)
        void usesTimeout5() { }

        @BmcProof
        void alsoDefaultTimeout() { }
    }

    private static BmcProof annotationOn(Class<?> type, String method) throws Exception {
        Method m = type.getDeclaredMethod(method);
        return m.getAnnotation(BmcProof.class);
    }

    @Test
    void resolveMaxStringLength_prefersPerProofOverride_thenDefaults() throws Exception {
        assertEquals(4, BmcProofExtension.resolveMaxStringLength(
                annotationOn(MixedStringLengthProofs.class, "usesLength4")));
        assertEquals(16, BmcProofExtension.resolveMaxStringLength(
                annotationOn(MixedStringLengthProofs.class, "usesDefaultLength")),
                "maxStringLength=0 falls back to the build default (16)");
    }

    @Test
    void resolveTimeoutSeconds_prefersPerProofOverride_thenDefaults() throws Exception {
        assertEquals(5, BmcProofExtension.resolveTimeoutSeconds(
                annotationOn(MixedTimeoutProofs.class, "usesTimeout5")));
        assertEquals(0, BmcProofExtension.resolveTimeoutSeconds(
                annotationOn(MixedTimeoutProofs.class, "usesDefaultTimeout")),
                "timeoutSeconds=0 falls back to the build default (0 = no timeout when unset)");
    }

    @Test
    void resolveTimeoutSeconds_honorsBuildDefaultProp_whenNoPerProofOverride() throws Exception {
        String prev = System.getProperty("bmc.timeoutSeconds");
        System.setProperty("bmc.timeoutSeconds", "30");
        try {
            assertEquals(30, BmcProofExtension.resolveTimeoutSeconds(
                    annotationOn(MixedTimeoutProofs.class, "usesDefaultTimeout")),
                    "-Dbmc.timeoutSeconds must apply when the proof has no override");
            assertEquals(5, BmcProofExtension.resolveTimeoutSeconds(
                    annotationOn(MixedTimeoutProofs.class, "usesTimeout5")),
                    "per-proof @BmcProof(timeoutSeconds=5) overrides the build default");
        } finally {
            restore("bmc.timeoutSeconds", prev);
        }
    }

    @Test
    void effectiveSolver_prefersPerProofOverride_thenDefaults() throws Exception {
        assertEquals("z3", BmcProofExtension.effectiveSolver(annotationOn(MixedSolverProofs.class, "usesZ3")));
        assertEquals("", BmcProofExtension.effectiveSolver(annotationOn(MixedSolverProofs.class, "usesDefaultSolver")));
    }

    // --- malformed bmc.* int properties fail loudly --------------------

    @Test
    void unwind_malformedValue_throwsNamingPropAndValue() throws Exception {
        String prev = System.getProperty("bmc.unwind");
        System.setProperty("bmc.unwind", "1o");
        try {
            BmcProof cfg = annotationOn(MixedSolverProofs.class, "usesDefaultSolver"); // unwind=0 -> reads prop
            IllegalArgumentException ex =
                    assertThrows(IllegalArgumentException.class, () -> BmcProofExtension.resolveUnwind(cfg));
            assertTrue(ex.getMessage().contains("bmc.unwind"), "message must name the property: " + ex.getMessage());
            assertTrue(ex.getMessage().contains("1o"), "message must name the bad value: " + ex.getMessage());
        } finally {
            restore("bmc.unwind", prev);
        }
    }

    @Test
    void maxStringLength_malformedValue_throwsNamingPropAndValue() {
        String prev = System.getProperty("bmc.maxStringLength");
        System.setProperty("bmc.maxStringLength", "abc");
        try {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> BmcProofExtension.requestFor("C", "C.m", null));
            assertTrue(ex.getMessage().contains("bmc.maxStringLength"),
                    "message must name the property: " + ex.getMessage());
            assertTrue(ex.getMessage().contains("abc"),
                    "message must name the bad value: " + ex.getMessage());
        } finally {
            restore("bmc.maxStringLength", prev);
        }
    }

    @Test
    void unwind_validPropValue_isHonored() throws Exception {
        String prev = System.getProperty("bmc.unwind");
        System.setProperty("bmc.unwind", "8");
        try {
            BmcProof cfg = annotationOn(MixedSolverProofs.class, "usesDefaultSolver"); // unwind=0 -> reads prop
            assertEquals(8, BmcProofExtension.resolveUnwind(cfg), "valid -Dbmc.unwind=8 must be honored");
        } finally {
            restore("bmc.unwind", prev);
        }
    }

    @Test
    void unwind_perProofAnnotationWins_overProp() throws Exception {
        String prev = System.getProperty("bmc.unwind");
        System.setProperty("bmc.unwind", "16");
        try {
            BmcProof cfg = annotationOn(TunedProof.class, "unwind8"); // explicit unwind=8
            assertEquals(8, BmcProofExtension.resolveUnwind(cfg));
        } finally {
            restore("bmc.unwind", prev);
        }
    }

    // --- engine-infrastructure failure classifies as UNKNOWN, not REFUTED -----------------

    @Test
    void engineInfraFailure_classifiesAsUndecidedUnknown_notRefuted() {
        // The exact engine-infrastructure failure shape: BundledEngine.extract() / process start throws a
        // non-verdict IllegalStateException out of the engine-run path. That MUST become UNKNOWN
        // (BmcUndecidedError) — there is no counterexample, the engine simply couldn't run.
        IllegalStateException cause = new IllegalStateException(
                "Could not start JBMC process: jbmc --function P.p");
        BmcUndecidedError err =
                BmcProofExtension.engineInfraUndecided("jbmc", "pkg.P.p", cause);

        // BmcUndecidedError IS-A BmcVerificationError, but the (UNKNOWN) tag is what the runner line
        // keys on to print UNKNOWN instead of REFUTED (BmcPlugin.isUndecided).
        assertTrue(err instanceof org.bmc4j.engine.BmcUndecidedError);
        assertTrue(err.getMessage().contains("(UNKNOWN)"),
                "message must carry the (UNKNOWN) verdict tag so the runner prints UNKNOWN: " + err.getMessage());
        assertTrue(err.getMessage().contains("NOT a refutation"),
                "must frame it as not-a-refutation: " + err.getMessage());
        assertTrue(err.getMessage().contains("pkg.P.p"), err.getMessage());
        assertEquals(cause, err.getCause(), "the original infrastructure cause is preserved for diagnosis");
        // Crucially it does NOT read as a refutation.
        assertFalse(err.getMessage().contains("refuted "), err.getMessage());
    }

    @Test
    void parsedFailureProperty_staysRefuted_notUndecided() throws Exception {
        // The other side of the classification split: a real parsed JBMC counterexample (a FAILURE property) comes
        // back as a refuted JbmcResult and must stay REFUTED — a BmcVerificationError that is NOT the
        // UNKNOWN subtype and does NOT carry the (UNKNOWN) tag.
        org.bmc4j.engine.JbmcResult.Violation v = new org.bmc4j.engine.JbmcResult.Violation(
                "assertion failed: x > 0", null, 0, List.of(), List.of());
        org.bmc4j.engine.JbmcResult refuted =
                new org.bmc4j.engine.JbmcResult(false, List.of(v), "{}");
        Method proofMethod = MixedSolverProofs.class.getDeclaredMethod("usesDefaultSolver");

        org.bmc4j.engine.BmcVerificationError err =
                BmcProofExtension.toError("jbmc", "pkg.P.p", refuted, proofMethod);

        assertFalse(err instanceof org.bmc4j.engine.BmcUndecidedError,
                "a real counterexample is REFUTED, never the UNKNOWN subtype");
        assertFalse(err.getMessage().contains("(UNKNOWN)"),
                "a refutation must not carry the UNKNOWN tag: " + err.getMessage());
        assertTrue(err.getMessage().toLowerCase().contains("refuted"),
                "a real counterexample reads as refuted: " + err.getMessage());
    }

    // --- expected-verdict assertions (expect = REFUTED/UNKNOWN/VACUOUS) -----------------------

    private static org.bmc4j.engine.JbmcResult refutedResult() {
        org.bmc4j.engine.JbmcResult.Violation v = new org.bmc4j.engine.JbmcResult.Violation(
                "assertion failed: x > 0", null, 0, List.of(), List.of());
        return new org.bmc4j.engine.JbmcResult(false, List.of(v), "{}");
    }

    @Test
    void actualVerdict_mapsAllFourOutcomes() {
        assertEquals(org.bmc4j.Verdict.VERIFIED, BmcProofExtension.actualVerdict(
                new org.bmc4j.engine.JbmcResult(true, List.of(), "{}")));
        assertEquals(org.bmc4j.Verdict.REFUTED, BmcProofExtension.actualVerdict(refutedResult()));
        assertEquals(org.bmc4j.Verdict.UNKNOWN, BmcProofExtension.actualVerdict(
                org.bmc4j.engine.JbmcResult.unknown("timed out after 1s", "{}")));
        // Vacuity is carried as a flavour of REFUTED internally but is its own expectation.
        org.bmc4j.engine.JbmcResult vacuous =
                new org.bmc4j.engine.JbmcResult(false, List.of(), "{}", true);
        assertEquals(org.bmc4j.Verdict.VACUOUS, BmcProofExtension.actualVerdict(vacuous));
    }

    @Test
    void expectedVerdictMatch_swallowsTheFramedError() {
        org.bmc4j.engine.BmcVerificationError framed =
                new org.bmc4j.engine.BmcVerificationError("JBMC refuted pkg.P.p");
        assertDoesNotThrow(() -> BmcProofExtension.enforceExpectation(
                "pkg.P.p", org.bmc4j.Verdict.REFUTED, org.bmc4j.Verdict.REFUTED, framed));
    }

    @Test
    void defaultExpectation_rethrowsTheFramedErrorUnchanged() {
        org.bmc4j.engine.BmcVerificationError framed =
                new org.bmc4j.engine.BmcVerificationError("JBMC refuted pkg.P.p");
        org.bmc4j.engine.BmcVerificationError thrown = assertThrows(
                org.bmc4j.engine.BmcVerificationError.class,
                () -> BmcProofExtension.enforceExpectation(
                        "pkg.P.p", org.bmc4j.Verdict.VERIFIED, org.bmc4j.Verdict.REFUTED, framed));
        assertEquals(framed, thrown, "expect=VERIFIED must behave exactly as before: the framed error");
    }

    @Test
    void verdictMismatch_namesBothVerdicts_andKeepsTheCause() {
        org.bmc4j.engine.BmcVerificationError framed =
                new org.bmc4j.engine.BmcVerificationError("JBMC refuted pkg.P.p");
        org.bmc4j.engine.BmcVerificationError err = assertThrows(
                org.bmc4j.engine.BmcVerificationError.class,
                () -> BmcProofExtension.enforceExpectation(
                        "pkg.P.p", org.bmc4j.Verdict.VACUOUS, org.bmc4j.Verdict.REFUTED, framed));
        assertTrue(err.getMessage().contains("expected VACUOUS"), err.getMessage());
        assertTrue(err.getMessage().contains("got REFUTED"), err.getMessage());
        assertEquals(framed, err.getCause(), "the real verdict's framing is preserved as the cause");
    }

    @Test
    void failOnPurposeProofGoingGreen_failsLoudly() {
        org.bmc4j.engine.BmcVerificationError err = assertThrows(
                org.bmc4j.engine.BmcVerificationError.class,
                () -> { throw BmcProofExtension.expectationMismatch(
                        "pkg.P.p", org.bmc4j.Verdict.REFUTED, org.bmc4j.Verdict.VERIFIED, null); });
        assertTrue(err.getMessage().contains("expected REFUTED"), err.getMessage());
        assertTrue(err.getMessage().contains("got VERIFIED"), err.getMessage());
        assertTrue(err.getMessage().contains("stopped being"),
                "the dangerous drift (false claim no longer refutable) is called out: " + err.getMessage());
    }

    @Test
    void engineInfrastructureUnknown_neverSatisfiesExpectedUnknown() {
        // A broken engine must not masquerade as an undecidability demo.
        BmcUndecidedError infra = BmcProofExtension.engineInfraUndecided(
                "jbmc", "pkg.P.p", new IllegalStateException("could not extract engine"));
        assertTrue(infra.isEngineInfrastructure());
        org.bmc4j.engine.BmcVerificationError err = assertThrows(
                org.bmc4j.engine.BmcVerificationError.class,
                () -> BmcProofExtension.enforceExpectation(
                        "pkg.P.p", org.bmc4j.Verdict.UNKNOWN, org.bmc4j.Verdict.UNKNOWN, infra));
        assertTrue(err.getMessage().contains("not a real UNKNOWN"), err.getMessage());
        assertEquals(infra, err.getCause());
    }

    @Test
    void genuineUnknown_satisfiesExpectedUnknown() {
        // A real undecided verdict (timeout / solver gave up) is exactly what expect=UNKNOWN declares.
        BmcUndecidedError genuine = new BmcUndecidedError("JBMC could not decide pkg.P.p (UNKNOWN)");
        assertFalse(genuine.isEngineInfrastructure());
        assertDoesNotThrow(() -> BmcProofExtension.enforceExpectation(
                "pkg.P.p", org.bmc4j.Verdict.UNKNOWN, org.bmc4j.Verdict.UNKNOWN, genuine));
    }

    // --- TIMEOUT: the structured subtype of UNKNOWN -------------------------------------------

    @Test
    void timedOutResult_mapsToTimeoutVerdict_otherUnknownsStayUnknown() {
        assertEquals(org.bmc4j.Verdict.TIMEOUT, BmcProofExtension.actualVerdict(
                org.bmc4j.engine.JbmcResult.unknownTimeout("timed out after 1s", "{}")));
        assertEquals(org.bmc4j.Verdict.UNKNOWN, BmcProofExtension.actualVerdict(
                org.bmc4j.engine.JbmcResult.unknown("engine exited 6", "{}")));
    }

    @Test
    void expectedTimeout_passesOnTimeout_butRejectsOtherUnknowns() {
        BmcUndecidedError framed = new BmcUndecidedError("JBMC could not decide pkg.P.p (UNKNOWN)");
        // The budget actually fired -> expect=TIMEOUT is satisfied.
        assertDoesNotThrow(() -> BmcProofExtension.enforceExpectation(
                "pkg.P.p", org.bmc4j.Verdict.TIMEOUT, org.bmc4j.Verdict.TIMEOUT, framed));
        // A non-timeout undecided (solver crash, unparseable output) must NOT satisfy expect=TIMEOUT.
        org.bmc4j.engine.BmcVerificationError err = assertThrows(
                org.bmc4j.engine.BmcVerificationError.class,
                () -> BmcProofExtension.enforceExpectation(
                        "pkg.P.p", org.bmc4j.Verdict.TIMEOUT, org.bmc4j.Verdict.UNKNOWN, framed));
        assertTrue(err.getMessage().contains("expected TIMEOUT"), err.getMessage());
        assertTrue(err.getMessage().contains("got UNKNOWN"), err.getMessage());
    }

    @Test
    void expectedUnknown_subsumesTimeout() {
        // UNKNOWN is the umbrella: a timeout is one way to be undecided, so expect=UNKNOWN accepts it.
        BmcUndecidedError framed = new BmcUndecidedError("JBMC could not decide pkg.P.p (UNKNOWN)");
        assertDoesNotThrow(() -> BmcProofExtension.enforceExpectation(
                "pkg.P.p", org.bmc4j.Verdict.UNKNOWN, org.bmc4j.Verdict.TIMEOUT, framed));
    }

    private static void restore(String key, String prev) {
        if (prev == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, prev);
        }
    }
}
