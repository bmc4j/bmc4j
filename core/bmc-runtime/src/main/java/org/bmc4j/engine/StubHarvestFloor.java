package org.bmc4j.engine;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Positive floor for nondet-stub detection: prove the {@link JbmcOutputParser} stub harvest
 * actually <em>works</em> against the engine in use before trusting an <b>empty</b> harvest.
 *
 * <p>The harvest keys on a literal engine string ({@code "new opaque symbol: method '"} in the
 * {@code --verbosity 10 --json-ui} stream). Against the bundled engine that format is pinned by the
 * parser tests, but a consumer pointing {@code -Dbmc.jbmc} at another build has no such guarantee:
 * a format drift <b>silently empties</b> the harvest — greens lose their honesty footnotes and
 * {@code strictStubs} stops gating, with nothing visible anywhere. That silent-open failure is the
 * hole this floor closes.
 *
 * <p><b>Mechanism.</b> The first time a VERIFIED result with an empty harvest would be trusted
 * (a non-empty harvest needs no floor — the signal's presence proves the parse), run a canary:
 * a generated class whose entry calls a method of a class that does not exist — the one situation
 * that MUST produce a stub — through the same executable, flag shape and parser as real proofs.
 * If the canary's harvest does not surface that stub, stub detection demonstrably does not work
 * against this engine, and the green is reported as an engine-infrastructure UNKNOWN (loud,
 * actionable), never passed.
 *
 * <p><b>Cost.</b> The outcome is a pure function of the engine binary and the bmc4j parser, so it
 * is memoized in-process per engine identity and as a disk marker under
 * {@code ~/.cache/bmc4j/stub-floor/} keyed by (engine identity, {@link Bmc4jVersion#IDENTITY}) —
 * one small engine run per machine per engine+runtime version. Marker IO fails open to re-running
 * the canary (toward re-verification); the floor itself never fails open.
 */
final class StubHarvestFloor {

    /**
     * The stub the canary MUST harvest: a method of a deliberately-missing class. The package is
     * chosen to be plainly user-shaped — outside every {@link StubFilter} noise rule (pinned by
     * {@code StubHarvestFloorTest}) — so the floor exercises the full pipeline the real harvest
     * uses: opaque-marker parse, FQN extraction, signal filter.
     */
    static final String CANARY_STUB_FQN = "bmc.canary.Missing.gone";

    /** Wall-clock budget for the canary run — trivial work; a healthy engine takes seconds. */
    private static final int CANARY_TIMEOUT_SECONDS = 120;

    /** Canary runs actually executed by this JVM — test hook pinning the memo/marker behavior. */
    static final java.util.concurrent.atomic.AtomicInteger CANARY_RUNS =
            new java.util.concurrent.atomic.AtomicInteger();

    /** Floor verdict per engine identity (in-process memo; the disk marker spans JVMs). */
    private static final java.util.concurrent.ConcurrentHashMap<String, Boolean> RESULTS =
            new java.util.concurrent.ConcurrentHashMap<>();

    private StubHarvestFloor() {
    }

    /**
     * Throws an engine-infrastructure {@link BmcUndecidedError} unless stub detection provably works
     * against {@code jbmcPath}. Call only when an empty harvest is about to be trusted on a green.
     */
    static void ensure(String jbmcPath, String engineIdentity) {
        String key = (engineIdentity == null ? "" : engineIdentity) + '|' + Bmc4jVersion.IDENTITY;
        Boolean ok = RESULTS.computeIfAbsent(key, k -> holds(jbmcPath, k));
        if (!ok) {
            // Engine-infrastructure UNKNOWN: never a pass, and never satisfies expect = UNKNOWN.
            throw new BmcUndecidedError(
                    "stub detection could not be verified against this engine (" + jbmcPath + "): a"
                    + " canary proof calling a deliberately-missing method harvested no nondet stub,"
                    + " so an empty harvest cannot be trusted — a green might silently rest on"
                    + " unmodeled methods. Use the bundled engine, or an engine whose"
                    + " --verbosity 10 --json-ui output reports stubbed methods with the"
                    + " \"new opaque symbol\" message.", true);
        }
    }

    /**
     * Disk-marker fast path around {@link #canaryHarvests}: a marker for this (engine, runtime)
     * key means a prior JVM already proved the floor — same binary + same parser, same outcome.
     * Writes the marker on a fresh success; never writes a negative (a broken setup may be fixed
     * between JVMs, so failure re-probes next run). Marker IO fails open to re-running the canary.
     */
    private static boolean holds(String jbmcPath, String key) {
        Path marker = markerPath(key);
        try {
            if (Files.isRegularFile(marker)) {
                return true;
            }
        } catch (RuntimeException ignored) {
            // unreadable marker -> just re-run the canary
        }
        boolean ok = canaryHarvests(jbmcPath);
        if (ok) {
            try {
                Files.createDirectories(marker.getParent());
                Files.writeString(marker, "ok\n", StandardCharsets.UTF_8);
            } catch (IOException | RuntimeException ignored) {
                // best effort: next JVM re-proves the floor
            }
        }
        return ok;
    }

    /** SHA-256 hex of {@code key} — the disk marker's file name. */
    private static String digest(String key) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(key.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e); // present on every JVM
        }
    }

    /** {@code ~/.cache/bmc4j/stub-floor/<digest>} — sibling of the engine extraction cache.
     *  Package-private so the test can plant/inspect markers under a redirected {@code user.home}. */
    static Path markerPath(String key) {
        return Path.of(System.getProperty("user.home"), ".cache", "bmc4j", "stub-floor", digest(key));
    }

    /**
     * Run the canary: write the generated probe class to a temp dir, verify it with the SAME
     * executable, flag shape and parser as a real proof, and check the harvest surfaced
     * {@link #CANARY_STUB_FQN}. Any failure to run is {@code false} — an engine that cannot run a
     * trivial probe cannot vouch for its own stub reporting (loud, never silent).
     */
    private static boolean canaryHarvests(String jbmcPath) {
        CANARY_RUNS.incrementAndGet();
        // One-time per machine/engine/version: explain the pause instead of stalling silently.
        System.out.println("bmc4j: verifying stub detection against this engine (one-time canary)");
        Path dir = null;
        try {
            dir = Files.createTempDirectory("bmc4j-stub-canary");
            Path probe = dir.resolve("bmc").resolve("canary").resolve("Probe.class");
            Files.createDirectories(probe.getParent());
            Files.write(probe, probeClass());
            JbmcResult result = new Jbmc(jbmcPath).run(
                    "bmc.canary.Probe", "bmc.canary.Probe.probe", dir.toString(),
                    2, false, 0, false, "", CANARY_TIMEOUT_SECONDS);
            return result.stubbedMethods().contains(CANARY_STUB_FQN);
        } catch (IOException | RuntimeException e) {
            return false; // can't run the canary -> can't trust an empty harvest
        } finally {
            deleteQuietly(dir);
        }
    }

    /**
     * Bytecode for {@code public class bmc.canary.Probe { public static void probe() {
     * bmc.canary.Missing.gone(); } }} — {@code bmc.canary.Missing} deliberately does not exist, so
     * the engine MUST stub the call and report it. (Shape pinned by {@code StubHarvestFloorTest}.)
     */
    static byte[] probeClass() {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "bmc/canary/Probe", null, "java/lang/Object", null);
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "probe", "()V",
                null, null);
        mv.visitCode();
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "bmc/canary/Missing", "gone", "()V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static void deleteQuietly(Path dir) {
        if (dir == null) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // best effort
                }
            });
        } catch (IOException | RuntimeException ignored) {
            // best effort
        }
    }
}
