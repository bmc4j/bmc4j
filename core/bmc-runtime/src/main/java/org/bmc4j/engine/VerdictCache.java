package org.bmc4j.engine;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * Per-proof verdict cache: skip re-verifying a proof whose inputs haven't changed.
 *
 * <p>A proof's verdict is a pure function of its inputs — the reachable bytecode, the request flags,
 * and the engine + bmc4j-runtime semantics. Re-running a green proof whose inputs are unchanged buys
 * nothing, and BMC is the expensive kind of test. This cache turns "nothing changed" into "proofs are
 * free", which is what lets {@code @BmcProof} stay in the default {@code test} task.
 *
 * <h2>Soundness</h2>
 * A <b>stale green is a soundness bug</b> for this tool, so the cache is deliberately biased toward
 * over-invalidation (re-running) and against under-invalidation (a wrong skip):
 * <ul>
 *   <li><b>Only {@code VERIFIED} verdicts are ever cached.</b> A {@code REFUTED} or {@code UNKNOWN}
 *       proof is never written and never served — reds always re-run, so the counterexample is fresh
 *       and a flaky environment can't pin a stale failure.</li>
 *   <li>The key composes every input that can change a verdict (see {@link #computeKey}): the analysis
 *       classpath <em>content</em>, the effective request, the engine identity, and the bmc4j runtime
 *       semantics identity ({@link Bmc4jVersion#IDENTITY}). Coarse on purpose — any application-class
 *       change invalidates that module's whole cache.</li>
 *   <li><b>Fail-open.</b> Any error reading or writing the cache is swallowed and treated as a miss, so
 *       the cache can never cause a wrong or varying verdict — at worst it runs the engine.</li>
 * </ul>
 */
public final class VerdictCache {

    /** Bypass the cache entirely (always run the engine, never read or write). */
    private static final String NO_CACHE_PROP = "bmc.noCache";

    private VerdictCache() {
    }

    /**
     * Cache directory: {@code <module>/build/bmc4j/verdict-cache/}. Resolved against {@code user.dir}
     * (the test JVM's working directory is the module dir) on every access rather than cached in a
     * static, so it tracks the working directory — keeps the cache per-module under {@code build/} (so
     * {@code gradlew clean} removes it) and lets tests redirect it.
     */
    private static Path cacheDir() {
        return Path.of(System.getProperty("user.dir", "."), "build", "bmc4j", "verdict-cache");
    }

    /** True when caching is disabled via {@code -Dbmc.noCache=true} (or the {@code bmc { cache=false }} flag). */
    public static boolean disabled() {
        return Boolean.parseBoolean(System.getProperty(NO_CACHE_PROP, "false"));
    }

    /**
     * Look up a previously cached <b>verified</b> verdict for {@code request} under {@code engineIdentity}.
     * Returns {@code true} only on a hit whose key matches exactly; {@code false} on a miss, on a disabled
     * cache, or on ANY error (fail-open). A {@code true} here means the proof was verified and the engine
     * run can be skipped.
     */
    public static boolean isVerified(BmcRequest request, String engineIdentity) {
        return lookupVerified(request, engineIdentity) != null;
    }

    /**
     * A cache hit's stored verified verdict (verdict + stub facts): the entry marker plus the nondet-stub
     * list that was harvested when the proof verified. {@code null} on a miss, a disabled cache, or any
     * error (fail-open → run the engine). The stored stub list lets the stub <em>policy</em> be re-judged
     * at read time — flipping {@code strictStubs} or editing {@code allowStubs} re-decides from the stored
     * fact <em>without</em> an engine re-run, because neither is part of the cache key.
     */
    public static Hit lookupVerified(BmcRequest request, String engineIdentity) {
        if (disabled()) {
            return null;
        }
        try {
            String key = computeKey(request, engineIdentity);
            Path entry = cacheDir().resolve(key);
            if (!Files.isRegularFile(entry)) {
                return null;
            }
            // The entry's first line must start with VERIFIED (a truncated/scribbled file is a miss,
            // fail-open). Remaining "STUB <fqn>" lines carry the harvested stub fact for re-judgement.
            List<String> lines = Files.readAllLines(entry, StandardCharsets.UTF_8);
            if (lines.isEmpty() || !lines.get(0).trim().startsWith("VERIFIED")) {
                return null;
            }
            List<String> stubs = new ArrayList<>();
            for (int i = 1; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                if (line.startsWith("STUB ")) {
                    stubs.add(line.substring("STUB ".length()).trim());
                }
            }
            return new Hit(stubs);
        } catch (RuntimeException | IOException e) {
            return null; // fail-open: any trouble reading the cache -> miss -> run the engine
        }
    }

    /** A verified cache hit: carries the stub list stored when the proof verified. */
    public static final class Hit {
        private final List<String> stubbedMethods;

        Hit(List<String> stubbedMethods) {
            this.stubbedMethods = List.copyOf(stubbedMethods);
        }

        /** The nondet stubs (filtered signal) recorded when this proof verified. */
        public List<String> stubbedMethods() {
            return stubbedMethods;
        }
    }

    /**
     * Record that {@code request} (under {@code engineIdentity}) verified. No-ops when the cache is
     * disabled, when the result is not {@code VERIFIED} (reds are never cached), or on ANY write error
     * (fail-open). The marker is written atomically (temp file + move) so a concurrent reader never sees
     * a half-written entry.
     */
    public static void storeIfVerified(BmcRequest request, String engineIdentity, JbmcResult result) {
        if (disabled() || result == null || !result.isVerified()) {
            return; // never cache REFUTED / UNKNOWN / vacuous — always re-run reds
        }
        try {
            String key = computeKey(request, engineIdentity);
            Files.createDirectories(cacheDir());
            Path entry = cacheDir().resolve(key);
            Path tmp = cacheDir().resolve(key + ".tmp." + Long.toHexString(System.nanoTime()));
            // Store the verdict marker plus the harvested nondet-stub fact, one "STUB <fqn>"
            // per line. The stub policy is judged at READ time, so the cache key is unchanged — only this
            // payload grows — and flipping strictStubs / allowStubs re-judges without an engine re-run.
            StringBuilder body = new StringBuilder("VERIFIED ").append(request.entryFunction()).append('\n');
            for (String stub : result.stubbedMethods()) {
                body.append("STUB ").append(stub).append('\n');
            }
            Files.writeString(tmp, body.toString(), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, entry, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicUnsupported) {
                Files.move(tmp, entry, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (RuntimeException | IOException e) {
            // fail-open: a write failure must never break the build or vary a verdict.
            cleanupTemp();
        }
    }

    /**
     * The cache key: a hex digest over everything that can change this proof's verdict —
     * <ol>
     *   <li>the bmc4j runtime semantics identity ({@link Bmc4jVersion#IDENTITY}) — the rewrite layer
     *       defines the proof's semantics, so a layer change must bust the cache;</li>
     *   <li>the engine identity (bundled engine version, or a hash of an explicit {@code jbmcPath}
     *       binary);</li>
     *   <li>the effective request: entry function, unwind, unwinding-assertions, solver,
     *       maxStringLength, timeoutSeconds, concurrent;</li>
     *   <li>the analysis-classpath <em>content</em>: every application {@code .class} file and every
     *       model jar on the classpath, by path + content (coarse v1 — any app-class change invalidates
     *       the whole module's cache);</li>
     *   <li>the user-model directory content ({@code bmc.userModels}): user {@code src/bmcModel} classes
     *       are spliced onto the analysis classpath inside {@link JbmcBackend#prepareClasspath}, AFTER
     *       this key is built, so they aren't in {@code request.classpath()} — fold them in explicitly or
     *       editing a user model serves a stale green;</li>
     *   <li>the resolved config inputs: {@link ConfigBytecode} bakes the real
     *       {@code System.getenv}/{@code getProperty("KEY")} value into the analysed bytecode at analysis
     *       time (AFTER this key is built), and the app {@code .class} files don't change when an env var
     *       or property changes — so scan the reachable classpath for literal-keyed {@code Bmc.*From*}
     *       call sites, resolve each {@code KEY=value} the same way the bake does, and fold them in, or a
     *       config-pinned proof keeps its cached green after its config flips to a violating value.</li>
     * </ol>
     * Visible for unit testing (each input must perturb the digest).
     */
    public static String computeKey(BmcRequest request, String engineIdentity) {
        MessageDigest md = sha256();
        // 1) runtime semantics identity
        update(md, "runtime", Bmc4jVersion.IDENTITY);
        // 2) engine identity
        update(md, "engine", engineIdentity == null ? "" : engineIdentity);
        // 3) effective request
        update(md, "entry", request.entryFunction());
        update(md, "unwind", Integer.toString(request.unwind()));
        update(md, "ua", Boolean.toString(request.unwindingAssertions()));
        update(md, "solver", request.solver());
        update(md, "msl", Integer.toString(request.maxStringLength()));
        update(md, "timeout", Integer.toString(request.timeoutSeconds()));
        update(md, "concurrent", Boolean.toString(request.concurrent()));
        // 4) analysis-classpath content
        update(md, "classpath", classpathContentDigest(request.classpath()));
        // 5) user-model content (bmc.userModels) — spliced onto the classpath after this key is built,
        //    so editing a user model must invalidate here or a stale green is served.
        String userModels = System.getProperty("bmc.userModels", "");
        update(md, "userModels", classpathContentDigest(userModels));
        // 6) resolved config inputs — ConfigBytecode bakes the REAL System.getenv/getProperty("KEY")
        //    value into the analysed bytecode at analysis time, AFTER this key is built, and the app
        //    .class files don't change when an env var / property changes. So fold the resolved KEY=value
        //    pairs (scanned from the same reachable classpath, incl. user models) in here, or a
        //    config-pinned proof keeps its cached green after its config flips to a violating value.
        update(md, "config", resolvedConfig(request.classpath(), userModels));
        return toHex(md.digest());
    }

    /**
     * The resolved config inputs ({@code KEY=value} pairs) for every literal-keyed {@code Bmc.*From*}
     * call site reachable on the analysis classpath and the user-model dir — the values
     * {@link ConfigBytecode} bakes at analysis time. Folded into {@link #computeKey} so a config-pinned
     * proof's cached green is invalidated when its config flips to a violating value (the app
     * {@code .class} files are unchanged, so {@link #classpathContentDigest} alone can't catch it).
     * Fail-open: any error scanning yields the empty string (a miss-toward-re-run, never a wrong hit).
     */
    static String resolvedConfig(String classpath, String userModels) {
        try {
            String cp = classpath == null ? "" : classpath;
            String um = userModels == null ? "" : userModels;
            String combined = um.isBlank() ? cp
                    : (cp.isBlank() ? um : cp + java.io.File.pathSeparator + um);
            return ConfigBytecode.resolvedConfig(combined);
        } catch (RuntimeException e) {
            return ""; // fail-open: scan trouble -> empty -> cache fails open to a re-run
        }
    }

    /**
     * A digest of the verdict-relevant <em>content</em> of the analysis classpath: every application
     * {@code .class} file (directory entries, recursed) and every jar, by relative path + bytes.
     * Library jars are folded in too — a consumer proof can reach into an unmodeled/un-stubbed jar's
     * actual bytecode, so upgrading that jar (without recompiling app {@code .class} files) must
     * invalidate, or a stale VERIFIED is served across the dependency change. {@link #jarContentDigest}
     * ignores zip timestamps, so a non-reproducible rebuild of the same classes doesn't spuriously
     * invalidate. Coarse but correct: any content change on the classpath invalidates.
     */
    static String classpathContentDigest(String classpath) {
        MessageDigest md = sha256();
        if (classpath == null || classpath.isBlank()) {
            return toHex(md.digest());
        }
        String[] entries = classpath.split(java.io.File.pathSeparator);
        // Sort so classpath ordering doesn't change the digest (the reachable set is order-independent).
        List<String> sorted = new ArrayList<>(List.of(entries));
        Collections.sort(sorted);
        for (String e : sorted) {
            if (e == null || e.isBlank()) {
                continue;
            }
            Path p = Path.of(e);
            try {
                if (Files.isDirectory(p)) {
                    digestClassDir(md, p);
                } else if (Files.isRegularFile(p) && isJar(p)) {
                    update(md, "jar:" + p.getFileName(), jarContentDigest(p));
                }
            } catch (RuntimeException | IOException ex) {
                // Fail-open per-entry: if one entry can't be read, fold a marker so a later read failure
                // can't silently equal a clean run, and keep going. The overall lookup still fails open.
                update(md, "unreadable:" + e, ex.getClass().getSimpleName());
            }
        }
        return toHex(md.digest());
    }

    /** Hash every {@code .class} file under {@code dir}, by path-relative-to-dir + content, sorted. */
    private static void digestClassDir(MessageDigest md, Path dir) throws IOException {
        List<Path> classes = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.filter(Files::isRegularFile)
                    .filter(f -> f.getFileName().toString().endsWith(".class"))
                    .forEach(classes::add);
        }
        classes.sort(java.util.Comparator.comparing(Path::toString));
        for (Path c : classes) {
            String rel = dir.relativize(c).toString().replace('\\', '/');
            update(md, "class:" + rel, fileDigest(c));
        }
    }

    /** A classpath entry is a jar (verdict-relevant content) if its name ends in {@code .jar}. */
    private static boolean isJar(Path jar) {
        return jar.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".jar");
    }

    /**
     * Digest of a jar's <em>logical content</em>: each entry's name + uncompressed bytes, sorted by
     * name. Deliberately ignores zip metadata (timestamps, ordering, compression) so a non-reproducible
     * rebuild of the same classes — Gradle stamps jars with build timestamps, so the raw bytes differ
     * every build — does NOT spuriously invalidate the cache. The model jars' actual class content is
     * what affects a verdict, and that's exactly what this hashes.
     */
    static String jarContentDigest(Path jar) {
        MessageDigest md = sha256();
        java.util.TreeMap<String, byte[]> entries = new java.util.TreeMap<>();
        try (java.util.zip.ZipInputStream zin =
                     new java.util.zip.ZipInputStream(Files.newInputStream(jar))) {
            java.util.zip.ZipEntry ze;
            while ((ze = zin.getNextEntry()) != null) {
                if (ze.isDirectory()) {
                    continue;
                }
                entries.put(ze.getName(), zin.readAllBytes());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        for (java.util.Map.Entry<String, byte[]> en : entries.entrySet()) {
            md.update(en.getKey().getBytes(StandardCharsets.UTF_8));
            md.update((byte) 0);
            md.update(en.getValue());
            md.update((byte) 0);
        }
        return toHex(md.digest());
    }

    /** SHA-256 of a file's bytes, hex-encoded. */
    static String fileDigest(Path file) {
        try {
            MessageDigest md = sha256();
            md.update(Files.readAllBytes(file));
            return toHex(md.digest());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void update(MessageDigest md, String label, String value) {
        md.update(label.getBytes(StandardCharsets.UTF_8));
        md.update((byte) 0);
        md.update((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
        md.update((byte) 0);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e); // present on every JVM
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    /** Best-effort sweep of any leftover temp files from a failed write (fail-open housekeeping). */
    private static void cleanupTemp() {
        try (Stream<Path> s = Files.list(cacheDir())) {
            s.filter(p -> p.getFileName().toString().contains(".tmp."))
                    .forEach(p -> {
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
