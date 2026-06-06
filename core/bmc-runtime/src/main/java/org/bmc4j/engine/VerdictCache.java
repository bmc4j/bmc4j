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
 * A <b>stale pass is a soundness bug</b> for this tool, so the cache is deliberately biased toward
 * over-invalidation (re-running) and against under-invalidation (a wrong skip):
 * <ul>
 *   <li><b>Only deterministic, expectation-matching PASSES are ever cached.</b> {@code VERIFIED} for a
 *       normal proof; {@code REFUTED} / {@code VACUOUS} for a fail-on-purpose demo whose
 *       {@code @BmcProof(expect = ...)} declares exactly that verdict — a refutation is as much a pure
 *       function of the inputs as a verification, and the demo's <em>pass</em> is the refutation.
 *       <b>Failures are never cached</b>: any expectation mismatch (the dangerous drift) always comes
 *       from a live engine run, so the counterexample is fresh and a flaky environment can't pin a
 *       stale failure.</li>
 *   <li><b>{@code TIMEOUT} and {@code UNKNOWN} are never cached, even when expected.</b> A timeout is a
 *       function of machine speed, not of the inputs — serving a cached "TIMEOUT, as expected" on a
 *       faster runner would hide the drift to VERIFIED that the expectation exists to catch. (It would
 *       also save almost nothing: a timeout costs exactly its budget.)</li>
 *   <li>The key composes every input that can change a verdict (see {@link #computeKey}): the analysis
 *       classpath <em>content</em>, the effective request, the engine identity, and the bmc4j runtime
 *       semantics identity ({@link Bmc4jVersion#IDENTITY}). Coarse on purpose — any application-class
 *       change invalidates that module's whole cache. (The {@code expect} attribute lives in the
 *       compiled test class, so changing it invalidates via classpath content too.)</li>
 *   <li><b>Fail-open.</b> Any error reading or writing the cache is swallowed and treated as a miss, so
 *       the cache can never cause a wrong or varying verdict — at worst it runs the engine. A hit whose
 *       stored verdict does not satisfy the proof's expectation is ignored the same way: live run.</li>
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

    /** {@link #lookup} narrowed to {@code VERIFIED}: a hit with any other stored verdict is a miss. */
    public static Hit lookupVerified(BmcRequest request, String engineIdentity) {
        Hit hit = lookup(request, engineIdentity);
        return hit != null && hit.verdict() == org.bmc4j.Verdict.VERIFIED ? hit : null;
    }

    /**
     * A cache hit's stored verdict <em>fact</em> (verdict + stub facts): the entry's verdict marker
     * ({@code VERIFIED}, {@code REFUTED} or {@code VACUOUS} — the only verdicts ever stored) plus the
     * nondet-stub list that was harvested when the proof verified. {@code null} on a miss, a disabled
     * cache, an unrecognized marker, or any error (fail-open → run the engine). Whether the stored
     * verdict <em>satisfies</em> the proof's expectation is the caller's judgement, made fresh at read
     * time — the cache stores the fact, never the pass. Likewise the stored stub list lets the stub
     * <em>policy</em> be re-judged at read time — flipping {@code strictStubs} or editing
     * {@code allowStubs} re-decides from the stored fact <em>without</em> an engine re-run, because
     * neither is part of the cache key.
     */
    public static Hit lookup(BmcRequest request, String engineIdentity) {
        if (disabled()) {
            return null;
        }
        try {
            String key = computeKey(request, engineIdentity);
            Path entry = cacheDir().resolve(key);
            if (!Files.isRegularFile(entry)) {
                return null;
            }
            // The entry's first line is "<VERDICT> <entryFunction>"; only the deterministic markers are
            // recognized (a truncated/scribbled file is a miss, fail-open). Remaining "STUB <fqn>" lines
            // carry the harvested stub fact for re-judgement.
            List<String> lines = Files.readAllLines(entry, StandardCharsets.UTF_8);
            if (lines.isEmpty()) {
                return null;
            }
            org.bmc4j.Verdict verdict = parseMarker(lines.get(0).trim());
            if (verdict == null) {
                return null;
            }
            List<String> stubs = new ArrayList<>();
            for (int i = 1; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                if (line.startsWith("STUB ")) {
                    stubs.add(line.substring("STUB ".length()).trim());
                }
            }
            return new Hit(verdict, stubs);
        } catch (RuntimeException | IOException e) {
            return null; // fail-open: any trouble reading the cache -> miss -> run the engine
        }
    }

    /** The deterministic verdict named by an entry's first line, or {@code null} if unrecognized. */
    private static org.bmc4j.Verdict parseMarker(String firstLine) {
        int space = firstLine.indexOf(' ');
        String token = space < 0 ? firstLine : firstLine.substring(0, space);
        for (org.bmc4j.Verdict v : DETERMINISTIC) {
            if (v.name().equals(token)) {
                return v;
            }
        }
        return null;
    }

    /**
     * The verdicts that are a pure function of the proof's inputs and may therefore be cached.
     * {@code TIMEOUT}/{@code UNKNOWN} are deliberately absent: a timeout is a function of machine speed.
     */
    private static final org.bmc4j.Verdict[] DETERMINISTIC = {
            org.bmc4j.Verdict.VERIFIED, org.bmc4j.Verdict.REFUTED, org.bmc4j.Verdict.VACUOUS,
    };

    /** A cache hit: the stored verdict fact, plus the stub list harvested when the proof verified. */
    public static final class Hit {
        private final org.bmc4j.Verdict verdict;
        private final List<String> stubbedMethods;

        Hit(org.bmc4j.Verdict verdict, List<String> stubbedMethods) {
            this.verdict = verdict;
            this.stubbedMethods = List.copyOf(stubbedMethods);
        }

        /** The stored verdict: {@code VERIFIED}, {@code REFUTED} or {@code VACUOUS} — never a failure. */
        public org.bmc4j.Verdict verdict() {
            return verdict;
        }

        /** The nondet stubs (filtered signal) recorded when this proof verified; empty for non-VERIFIED. */
        public List<String> stubbedMethods() {
            return stubbedMethods;
        }
    }

    /**
     * Record that {@code request} (under {@code engineIdentity}) verified. Equivalent to
     * {@link #storeIfExpectedMatch} with a {@code VERIFIED} expectation: stores iff the result verified.
     */
    public static void storeIfVerified(BmcRequest request, String engineIdentity, JbmcResult result) {
        storeIfExpectedMatch(request, engineIdentity, result, org.bmc4j.Verdict.VERIFIED);
    }

    /**
     * Record {@code result}'s verdict iff it is an expectation-matching <em>pass</em> with a
     * deterministic verdict: {@code VERIFIED} for a normal proof, {@code REFUTED}/{@code VACUOUS} for a
     * fail-on-purpose demo whose {@code @BmcProof(expect = ...)} declares exactly that verdict. No-ops
     * when the cache is disabled, when the actual verdict does not equal {@code expected} (failures are
     * never cached — a mismatch must always come from a live run), when the verdict is
     * {@code TIMEOUT}/{@code UNKNOWN} (machine-dependent, never cached even when expected), or on ANY
     * write error (fail-open). The marker is written atomically (temp file + move) so a concurrent
     * reader never sees a half-written entry.
     */
    public static void storeIfExpectedMatch(BmcRequest request, String engineIdentity, JbmcResult result,
                                            org.bmc4j.Verdict expected) {
        if (disabled() || result == null) {
            return;
        }
        org.bmc4j.Verdict actual = deterministicVerdictOf(result);
        if (actual == null || actual != expected) {
            return; // never cache UNKNOWN/TIMEOUT, never cache a failure — always re-run those
        }
        try {
            String key = computeKey(request, engineIdentity);
            Files.createDirectories(cacheDir());
            Path entry = cacheDir().resolve(key);
            Path tmp = cacheDir().resolve(key + ".tmp." + Long.toHexString(System.nanoTime()));
            // Store the verdict marker plus (for VERIFIED) the harvested nondet-stub fact, one
            // "STUB <fqn>" per line. The stub policy is judged at READ time, so the cache key is
            // unchanged — only this payload grows — and flipping strictStubs / allowStubs re-judges
            // without an engine re-run. (The stub policy only applies to greens, so non-VERIFIED
            // entries carry no STUB lines.)
            StringBuilder body = new StringBuilder(actual.name()).append(' ')
                    .append(request.entryFunction()).append('\n');
            if (actual == org.bmc4j.Verdict.VERIFIED) {
                for (String stub : result.stubbedMethods()) {
                    body.append("STUB ").append(stub).append('\n');
                }
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
     * The result's verdict if it is deterministic (cacheable), else {@code null}. Mirrors the
     * user-facing verdict mapping but collapses the machine-dependent verdicts ({@code TIMEOUT} and
     * other {@code UNKNOWN}s) to {@code null} — they are never cacheable regardless of expectation.
     */
    private static org.bmc4j.Verdict deterministicVerdictOf(JbmcResult result) {
        if (result.isVerified()) {
            return org.bmc4j.Verdict.VERIFIED;
        }
        if (result.isVacuous()) {
            return org.bmc4j.Verdict.VACUOUS;
        }
        if (result.isUnknown()) {
            return null; // TIMEOUT / UNKNOWN: a function of machine speed, not of the inputs
        }
        return org.bmc4j.Verdict.REFUTED;
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
        // 4) analysis-classpath content — memoized per classpath behind a (path, size, mtime)
        //    fingerprint; computeKey runs for EVERY proof and re-hashing the whole classpath
        //    dominated the cost of a cache hit (see memoized()).
        update(md, "classpath", memoized(DIGEST_MEMO, request.classpath(), VerdictCache::classpathContentDigest));
        // 5) user-model content (bmc.userModels) — spliced onto the classpath after this key is built,
        //    so editing a user model must invalidate here or a stale green is served.
        String userModels = System.getProperty("bmc.userModels", "");
        update(md, "userModels", memoized(DIGEST_MEMO, userModels, VerdictCache::classpathContentDigest));
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
            // Only the bytecode SCAN for call sites is memoized (a pure function of the classpath's
            // content, fingerprint-guarded like the digests). The VALUES are re-resolved on every key
            // (one getenv/getProperty per site — cheap), so a config flip between calls still perturbs
            // the key for unchanged bytecode.
            List<String[]> sites = memoized(SITES_MEMO, combined, ConfigBytecode::scanCallSites);
            return ConfigBytecode.resolveSites(sites);
        } catch (RuntimeException e) {
            return ""; // fail-open: scan trouble -> empty -> cache fails open to a re-run
        }
    }

    // --- Per-JVM memo of the expensive computeKey inputs --------------------------------------------

    /** Count of expensive recomputes (full digest or scan) — test hook pinning that the memo hits. */
    static final java.util.concurrent.atomic.AtomicInteger MEMO_RECOMPUTES =
            new java.util.concurrent.atomic.AtomicInteger();

    /** A memoized value plus the (path, size, mtime) fingerprint of the files it was computed from. */
    private static final class Memo<V> {
        final String fingerprint;
        final V value;

        Memo(String fingerprint, V value) {
            this.fingerprint = fingerprint;
            this.value = value;
        }
    }

    private static final java.util.concurrent.ConcurrentHashMap<String, Memo<String>> DIGEST_MEMO =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentHashMap<String, Memo<List<String[]>>> SITES_MEMO =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Memoize {@code compute} per classpath string, guarded by a cheap (path, size, mtime)
     * fingerprint of every file the computation reads. {@link #computeKey} runs for <b>every</b> proof
     * (the lookup; live runs pay it again on store), but its expensive inputs — the classpath content
     * digest and the config call-site scan — hash every class file and decompress + parse every jar on
     * the analysis classpath, which made even a cache HIT cost hundreds of milliseconds per proof.
     * Both are pure functions of the classpath's file contents, which are stable for a test JVM's
     * lifetime in any real run (Gradle compiles before the test task starts) — but stability is
     * <em>verified, not assumed</em>: any file appearing, disappearing, or changing size/mtime changes
     * the fingerprint and forces a fresh compute, so a mid-JVM edit (the soundness tests do exactly
     * that) still invalidates. Fail-open: a fingerprint error yields a never-matching value, forcing
     * the fresh compute.
     *
     * <p>Computes under a per-key lock: proofs run in parallel (the plugin defaults to one executor
     * per core), so without it the FIRST wave of proofs all miss the empty memo simultaneously and
     * each redundantly hashes the whole classpath — the herd pays the full cost as many times over as
     * there are executors. With the lock, one thread computes and the rest wait briefly and reuse it.
     */
    private static <V> V memoized(java.util.concurrent.ConcurrentHashMap<String, Memo<V>> memo,
                                  String classpath, java.util.function.Function<String, V> compute) {
        String key = classpath == null ? "" : classpath;
        String fp = fingerprint(key);
        Memo<V> m = memo.get(key);
        if (m != null && m.fingerprint.equals(fp)) {
            return m.value;
        }
        synchronized (LOCKS.computeIfAbsent(key, k -> new Object())) {
            m = memo.get(key); // re-check: another thread may have computed while we waited
            if (m != null && m.fingerprint.equals(fp)) {
                return m.value;
            }
            MEMO_RECOMPUTES.incrementAndGet();
            V value = compute.apply(key);
            memo.put(key, new Memo<>(fp, value));
            return value;
        }
    }

    /** Per-classpath compute locks for {@link #memoized} (never removed; a test JVM sees few keys). */
    private static final java.util.concurrent.ConcurrentHashMap<String, Object> LOCKS =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * A cheap identity of every file {@link #classpathContentDigest} and the config scan read: each
     * directory entry's {@code .class} files (relative path, size, mtime — the same filter the digest
     * walks) plus each regular-file entry (path, size, mtime), sorted. Reads attributes only, never
     * content; mtime carries its full filesystem precision. Distinct absent/error markers so a path
     * flipping between states never aliases a clean fingerprint.
     */
    private static String fingerprint(String classpath) {
        try {
            if (classpath.isBlank()) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            String[] entries = classpath.split(java.io.File.pathSeparator);
            List<String> sorted = new ArrayList<>(List.of(entries));
            Collections.sort(sorted);
            for (String e : sorted) {
                if (e == null || e.isBlank()) {
                    continue;
                }
                Path p = Path.of(e);
                if (Files.isDirectory(p)) {
                    List<Path> classes = new ArrayList<>();
                    try (Stream<Path> walk = Files.walk(p)) {
                        walk.filter(Files::isRegularFile)
                                .filter(f -> f.getFileName().toString().endsWith(".class"))
                                .forEach(classes::add);
                    }
                    classes.sort(java.util.Comparator.comparing(Path::toString));
                    for (Path c : classes) {
                        appendFileId(sb, e + "!" + p.relativize(c).toString().replace('\\', '/'), c);
                    }
                } else if (Files.isRegularFile(p)) {
                    appendFileId(sb, e, p);
                } else {
                    sb.append(e).append("|absent\n");
                }
            }
            return sb.toString();
        } catch (IOException | RuntimeException ex) {
            // fail-open: an unfingerprintable classpath never matches a memo entry -> fresh compute
            return "unfingerprintable:" + System.nanoTime();
        }
    }

    private static void appendFileId(StringBuilder sb, String label, Path file) throws IOException {
        sb.append(label).append('|').append(Files.size(file)).append('|')
                .append(Files.getLastModifiedTime(file).to(java.util.concurrent.TimeUnit.NANOSECONDS))
                .append('\n');
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
