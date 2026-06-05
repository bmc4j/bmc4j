package org.bmc4j.engine;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Shared classpath-mirroring engine for the bytecode rewrite passes (string/concat/record, lambda,
 * switch, math, config, reachability, coroutine-LVT). Each pass is "rewrite every {@code .class} on
 * the analysis classpath with this transform, copy everything else verbatim"; this class owns the
 * common <em>where</em> (which entries, where the mirror lands, how it's cached) so each pass only
 * supplies the <em>what</em> (its {@link ClassTransform}).
 *
 * <p><b>Both directory AND jar entries are mirrored</b>. The in-repo test bed runs on
 * {@code includeBuild} class <em>directories</em>, but a published consumer gets {@code bmc-models}
 * and every third-party library as <em>jars</em>; if jars passed through unrewritten, the shipped
 * product would be silently less sound than the test bed that validates it (no String shim, no
 * concat/record/lambda/typeSwitch/Math desugaring on any jar'd class). So:
 * <ul>
 *   <li><b>Directory entries</b> are mirrored to a cache dir keyed by a full SHA-256 of the dir's
 *       <em>content</em> (every {@code .class}/resource's path + bytes), with a {@code .done} marker —
 *       the same content-hash + atomic-publish scheme as jars. Hashing the content (not
 *       just the path) means a deleted/renamed class yields a fresh key, so a fresh complete mirror,
 *       so stale phantom classes can never linger on the analysis classpath across builds; and a full
 *       hash (not a 32-bit {@code String.hashCode}) means two distinct dirs can't collide into one
 *       mirror and silently mix their classes;</li>
 *   <li><b>Jar entries</b> are mirrored to a rewritten jar under the cache keyed by the jar's
 *       <em>content hash</em>, so the (more expensive) rewrite happens once per distinct jar and is
 *       reused across runs. A finished marker makes the cache hit atomic: a half-written jar from a
 *       crashed/concurrent run is never mistaken for a complete one.</li>
 * </ul>
 *
 * <p><b>Fail LOUD on a mirror failure.</b> Mirroring is the <em>only</em> thing that
 * makes JBMC's unsound constructs (nondet String ops, unconstrained {@code invokedynamic}) sound. If
 * an entry can't be mirrored (IO error, a malformed class, a corrupt jar) we must NOT silently fall
 * back to the original entry: that would analyse unrewritten String/indy classes as "verified" when
 * they were never made sound — a silent green that is exactly the unsoundness this layer exists to
 * close. Instead we THROW. The engine-error handling catches a {@code RuntimeException}
 * out of the rewrite/run path and reclassifies it as UNKNOWN ({@code BmcUndecidedError}) — so a
 * mirror failure fails the proof toward UNKNOWN, never toward a false VERIFIED. (The verdict cache
 * is the inverse direction: it fails toward re-running. Both fail toward "we don't actually know".)
 */
final class ClasspathMirror {

    private ClasspathMirror() {
    }

    /** Transforms one {@code .class} file's bytes. May emit extra generated classes (e.g. the lambda
     *  pass synthesizes a class per lambda site); the simple owner-swap passes emit only the main. */
    @FunctionalInterface
    interface ClassTransform {
        Transformed apply(byte[] classBytes);
    }

    /** The output of a {@link ClassTransform}: the rewritten main class plus any extra classes it
     *  generated, keyed by internal name ({@code a/b/C}). Extra is usually empty. */
    static final class Transformed {
        final byte[] main;
        final Map<String, byte[]> extra;

        Transformed(byte[] main) {
            this(main, Map.of());
        }

        Transformed(byte[] main, Map<String, byte[]> extra) {
            this.main = main;
            this.extra = extra;
        }
    }

    /**
     * Mirror every entry of {@code classpath} under {@code cacheName}, rewriting each {@code .class}
     * with {@code transform} and copying every other resource verbatim. Returns the new classpath
     * (same order, same separators). Directory entries become mirror directories; jar entries become
     * rewritten jars (both content-hash cached).
     *
     * <p><b>Throws on any mirror failure</b> rather than passing the original entry
     * through — a silent fall-back to the unrewritten entry would analyse unsound String/indy classes
     * as "verified". The thrown {@link MirrorException} propagates out of the engine-run path and is
     * reclassified as UNKNOWN ({@code BmcUndecidedError}) by the engine-error handling, so a mirror
     * failure surfaces as "we couldn't decide", never as a false green.
     */
    static String mirror(String classpath, String cacheName, ClassTransform transform) {
        return mirror(classpath, cacheName, transform, "");
    }

    /**
     * As {@link #mirror(String, String, ClassTransform)}, but {@code extraKey} is folded into every
     * entry's content hash. This lets a pass whose transform is parameterized (e.g. the contract
     * rewrite, where the same source dir yields a <em>different</em> mirror per redirect set and
     * excluded caller) keep distinct configurations in distinct, complete cache entries — the same
     * content-hash + atomic-publish + {@code .done} discipline, just over (content + config) instead
     * of content alone. An empty {@code extraKey} reproduces the plain-content key, so the
     * config-free passes are unaffected.
     */
    static String mirror(String classpath, String cacheName, ClassTransform transform, String extraKey) {
        String[] entries = classpath.split(File.pathSeparator);
        List<String> out = new ArrayList<>(entries.length);
        for (String entry : entries) {
            if (entry.isEmpty()) {
                continue;
            }
            Path p = Path.of(entry);
            try {
                if (Files.isDirectory(p)) {
                    out.add(mirrorDir(p, cacheName, transform, extraKey).toString());
                } else if (isJar(p)) {
                    out.add(mirrorJar(p, cacheName, transform, extraKey).toString());
                } else {
                    out.add(entry); // not a class container we rewrite (no soundness rewrite to lose)
                }
            } catch (IOException | RuntimeException e) {
                // Fail LOUD, not open: an unmirrored String/indy class analysed as-is is silently
                // unsound. Throw so the proof fails toward UNKNOWN , never a false
                // VERIFIED. The actionable message names the entry that couldn't be mirrored.
                throw new MirrorException(
                        "Could not mirror classpath entry for sound rewriting: " + entry
                                + " (cache '" + cacheName + "'). The bytecode rewrite that makes "
                                + "String ops and invokedynamic sound for JBMC could not be applied, "
                                + "so this proof cannot be soundly decided. Cause: " + e, e);
            }
        }
        return String.join(File.pathSeparator, out);
    }

    /**
     * A failure to mirror+rewrite a classpath entry. Unchecked so it propagates out of
     * the engine-run path, where the engine-error handling reclassifies it as UNKNOWN
     * ({@code BmcUndecidedError}) — a mirror failure must fail the proof toward UNKNOWN, never let an
     * unrewritten (unsound) entry be analysed as a silent green.
     */
    static final class MirrorException extends RuntimeException {
        MirrorException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** A regular file whose name ends in {@code .jar} or {@code .zip} (the jar layout JBMC reads). */
    private static boolean isJar(Path p) {
        if (!Files.isRegularFile(p)) {
            return false;
        }
        String name = p.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        return name.endsWith(".jar") || name.endsWith(".zip");
    }

    private static Path cacheRoot(String cacheName) {
        // Unified under ~/.cache/bmc4j/ alongside the engine cache (previously
        // legacy pre-rename dir). A one-time rebuild of the rewrite mirrors is harmless.
        return Path.of(System.getProperty("user.home"), ".cache", "bmc4j", cacheName);
    }

    // ---- directories: mirror to a FRESH dir keyed by CONTENT HASH, .done-marked ----
    //
    // Previously this keyed the dest on Integer.toHexString(absPath.hashCode()) and overwrote in place.
    // Two problems: (a) a 32-bit String.hashCode can collide, silently mixing two dirs' classes into
    // one mirror; (b) overwriting never deletes, so a class removed/renamed in the source kept its
    // stale .class on the analysis classpath forever (a phantom class). Both are fixed by mirroring,
    // exactly like the jar branch, into a dir named for the FULL SHA-256 of the dir's content: a
    // collision is cryptographically infeasible, and any content change (incl. a deletion) yields a
    // fresh key -> a fresh, complete mirror with no leftovers. A .done marker makes the cache hit
    // atomic so a half-written mirror from a crashed/racing run is never reused as complete.

    private static Path mirrorDir(Path dir, String cacheName, ClassTransform transform, String extraKey)
            throws IOException {
        // Snapshot the dir's files (sorted by relative path for a stable hash) once, so the hash and
        // the mirror see the same set even if the dir changes underneath us mid-run.
        List<Path> files = new ArrayList<>();
        try (var walk = Files.walk(dir)) {
            for (Path src : (Iterable<Path>) walk::iterator) {
                if (!Files.isDirectory(src)) {
                    files.add(src);
                }
            }
        }
        files.sort(java.util.Comparator.comparing(p -> dir.relativize(p).toString()));

        String hash = dirContentHash(dir, files, extraKey);
        Path root = cacheRoot(cacheName);
        Files.createDirectories(root);
        Path dest = root.resolve(hash);
        Path done = root.resolve(hash + DONE_SUFFIX);

        // Cache hit: a completed mirror for this exact content already exists.
        if (Files.isDirectory(dest) && Files.isRegularFile(done)) {
            return dest;
        }

        // Build into a fresh unique temp dir, then atomically publish it (and the marker last). A
        // fresh dir guarantees no stale class survives; building off to the side keeps a concurrent
        // reader from ever seeing a partial mirror as complete.
        Path tmp = Files.createTempDirectory(root, hash + "-");
        try {
            for (Path src : files) {
                Path target = tmp.resolve(dir.relativize(src).toString());
                Files.createDirectories(target.getParent());
                if (src.toString().endsWith(".class")) {
                    Transformed t = transform.apply(Files.readAllBytes(src));
                    Files.write(target, t.main);
                    for (Map.Entry<String, byte[]> g : t.extra.entrySet()) {
                        Path gp = tmp.resolve(g.getKey() + ".class");
                        Files.createDirectories(gp.getParent());
                        Files.write(gp, g.getValue());
                    }
                } else {
                    Files.copy(src, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            // Publish: a racing writer may have already published the same content-hash dest; that's
            // fine (identical content), so only move if dest is absent, and tolerate a lost race.
            if (!Files.isDirectory(dest)) {
                try {
                    Files.move(tmp, dest, StandardCopyOption.ATOMIC_MOVE);
                } catch (java.nio.file.FileAlreadyExistsException raced) {
                    // another run published first — its content equals ours, so reuse it
                } catch (IOException atomicUnsupported) {
                    if (!Files.isDirectory(dest)) {
                        Files.move(tmp, dest);
                    }
                }
            }
            Files.write(done, new byte[0]); // completion marker last
        } finally {
            deleteRecursivelyIfExists(tmp); // no-op if the move consumed it
        }
        return dest;
    }

    /** SHA-256 over the dir's content: each file's relative path then its bytes (length-framed so two
     *  different splits can't hash the same). Keys the mirror so distinct content gets distinct dests
     *  (no path-collision, no staleness) — the directory analogue of {@link #contentHash(Path)}. */
    private static String dirContentHash(Path dir, List<Path> sortedFiles, String extraKey) throws IOException {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 unavailable", e);
        }
        // Fold the config key in first (length-framed), so distinct (content, config) tuples can never
        // alias the same dest — the directory analogue of the jar branch's extra-key mixing.
        byte[] ek = extraKey.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        md.update(intToBytes(ek.length));
        md.update(ek);
        byte[] buf = new byte[1 << 16];
        for (Path src : sortedFiles) {
            byte[] rel = dir.relativize(src).toString().replace('\\', '/')
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            md.update(intToBytes(rel.length));
            md.update(rel);
            long size = Files.size(src);
            md.update(longToBytes(size));
            try (InputStream is = Files.newInputStream(src)) {
                int n;
                while ((n = is.read(buf)) > 0) {
                    md.update(buf, 0, n);
                }
            }
        }
        return toHex(md.digest());
    }

    private static byte[] intToBytes(int v) {
        return new byte[]{(byte) (v >>> 24), (byte) (v >>> 16), (byte) (v >>> 8), (byte) v};
    }

    private static byte[] longToBytes(long v) {
        byte[] b = new byte[8];
        for (int i = 7; i >= 0; i--) {
            b[i] = (byte) v;
            v >>>= 8;
        }
        return b;
    }

    private static void deleteRecursivelyIfExists(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // best-effort temp cleanup; a leftover temp dir never affects correctness
                }
            });
        }
    }

    // ---- jars: mirror to a rewritten jar keyed by CONTENT HASH (one-time per jar, reused) ----

    private static final String DONE_SUFFIX = ".done";

    private static Path mirrorJar(Path jar, String cacheName, ClassTransform transform, String extraKey)
            throws IOException {
        String hash = jarContentHash(jar, extraKey);
        Path root = cacheRoot(cacheName);
        Files.createDirectories(root);
        Path dest = root.resolve(hash + ".jar");
        Path done = root.resolve(hash + DONE_SUFFIX);

        // Cache hit: a completed rewrite for this exact content already exists. The marker makes the
        // check atomic — a half-written .jar from a crashed/racing run has no .done, so it's redone.
        if (Files.isRegularFile(done) && Files.isRegularFile(dest)) {
            return dest;
        }

        // Write to a unique temp jar, then atomically publish (jar first, then the marker) so a
        // concurrent reader never sees a partial jar as complete and racing writers don't corrupt.
        Path tmp = Files.createTempFile(root, hash + "-", ".jar.tmp");
        try {
            rewriteJarTo(jar, tmp, transform);
            // Publish. A content-hashed jar is IMMUTABLE once its .done marker is published, so a
            // PUBLISHED entry must never be re-opened for writing (the Windows sharing violation:
            // moving onto a jar a concurrent reader holds open -> "used by another process"). The fast
            // path already returned on dest+.done; here we are the first publisher, the loser of a
            // publish race, or redoing a half-written jar (dest present but NO .done). Re-check the
            // marker so a winner that published between the fast path and here makes us touch nothing.
            if (!Files.isRegularFile(done)) {
                publishJar(tmp, dest, done);
            }
            // else: lost the race — the winner's identical-content jar + marker are already in place.
        } finally {
            Files.deleteIfExists(tmp);
        }
        // Whether we published or lost a publish race, a COMPLETE (.done-marked) jar must now exist —
        // fail loud if it somehow doesn't, so a swallowed race can never return a path the caller would
        // analyse as a half-written/absent mirror.
        if (!Files.isRegularFile(dest) || !Files.isRegularFile(done)) {
            throw new IOException("mirror jar not completely published: " + dest);
        }
        return dest;
    }

    /**
     * Publish the freshly-rewritten {@code tmp} jar as {@code dest}, then write the {@code done} marker
     * last. {@code dest}'s name IS its content hash, so a COMPLETE (.done-marked) {@code dest} is by
     * construction the correct content and must never be re-opened for writing (on Windows, moving onto
     * a jar a concurrent reader holds open is a "used by another process" sharing violation). The two
     * fault modes the marker disambiguates:
     * <ul>
     *   <li><b>Concurrent publisher</b> — another writer installed the identical-content {@code dest}
     *       and is about to (or did) write {@code .done}. We must NOT replace it; we wait briefly for
     *       its marker and reuse it.</li>
     *   <li><b>Stale half-write</b> — a {@code dest} from a crashed run with no live reader and no
     *       {@code .done} (and none ever coming). It must be REDONE: replaced with our real rewrite.</li>
     * </ul>
     * A first publish (no {@code dest}) uses {@code ATOMIC_MOVE} with no {@code REPLACE_EXISTING}
     * (Windows can't atomically replace). Replacement of a stale half-write tolerates a peer replacing
     * it concurrently (re-checking the marker), so racing redo-ers never fail each other.
     */
    private static void publishJar(Path tmp, Path dest, Path done) throws IOException {
        // First publish: install into an absent dest. ATOMIC_MOVE so a reader never sees partial bytes.
        if (!Files.isRegularFile(dest)) {
            try {
                Files.move(tmp, dest, StandardCopyOption.ATOMIC_MOVE);
                writeMarker(done);
                return;
            } catch (java.nio.file.FileAlreadyExistsException raced) {
                // A racer installed the identical-content dest first — fall through to the marker wait.
            } catch (IOException atomicUnsupported) {
                if (!Files.isRegularFile(dest)) {
                    Files.move(tmp, dest); // ATOMIC_MOVE unsupported here; dest absent -> no live reader.
                    writeMarker(done);
                    return;
                }
                // dest appeared (a racer) — fall through to the marker wait.
            }
        }
        // dest already exists. Either a concurrent publisher is finishing (its .done is imminent) or it
        // is a stale half-write to redo. Wait briefly for the publisher's marker before deciding.
        for (int i = 0; i < 200 && !Files.isRegularFile(done); i++) {
            sleepMillis(10, done);
        }
        if (Files.isRegularFile(done)) {
            return; // a concurrent publisher completed the identical-content jar — reuse it, write nothing.
        }
        // No marker after the wait: a stale half-write with no live reader (no consumer resolves dest
        // without .done). Replace it with our real rewrite, tolerating a peer redoing it concurrently.
        try {
            Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException peerRedoing) {
            if (!Files.isRegularFile(done) && !Files.isRegularFile(dest)) {
                throw peerRedoing; // genuinely couldn't place a mirror — fail loud.
            }
            // a peer replaced dest concurrently; its content equals ours — reuse it.
        }
        writeMarker(done);
    }

    /** Idempotently create the empty {@code .done} marker; concurrent creates of an empty file are
     *  harmless, so a racer having written it first is fine. */
    private static void writeMarker(Path done) throws IOException {
        if (!Files.isRegularFile(done)) {
            Files.write(done, new byte[0]);
        }
    }

    private static void sleepMillis(long ms, Path waitingFor) throws IOException {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted waiting for a concurrent jar publish: " + waitingFor, ie);
        }
    }

    /** Rewrite every {@code .class} entry of {@code in} into a fresh jar {@code out}; copy the rest
     *  (manifest, resources) verbatim. Generated extra classes are added as new entries. */
    private static void rewriteJarTo(Path in, Path out, ClassTransform transform) throws IOException {
        // Collect generated extras across all entries; add them after, de-duped (a given generated
        // name is deterministic per source class, so collisions across entries would be identical).
        Map<String, byte[]> generated = new LinkedHashMap<>();
        java.util.Set<String> written = new java.util.HashSet<>();
        try (ZipFile zf = new ZipFile(in.toFile());
             ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(out))) {
            var en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                if (e.isDirectory()) {
                    zos.putNextEntry(new ZipEntry(e.getName()));
                    zos.closeEntry();
                    written.add(e.getName());
                    continue;
                }
                byte[] bytes;
                try (InputStream is = zf.getInputStream(e)) {
                    bytes = is.readAllBytes();
                }
                if (e.getName().endsWith(".class")) {
                    Transformed t = transform.apply(bytes);
                    writeEntry(zos, e.getName(), t.main);
                    written.add(e.getName());
                    generated.putAll(t.extra);
                } else {
                    writeEntry(zos, e.getName(), bytes);
                    written.add(e.getName());
                }
            }
            for (Map.Entry<String, byte[]> g : generated.entrySet()) {
                String name = g.getKey() + ".class";
                if (written.add(name)) {
                    writeEntry(zos, name, g.getValue());
                }
            }
        }
    }

    private static void writeEntry(ZipOutputStream zos, String name, byte[] bytes) throws IOException {
        ZipEntry out = new ZipEntry(name);
        out.setTime(0L); // deterministic: content hash already pins identity
        zos.putNextEntry(out);
        zos.write(bytes);
        zos.closeEntry();
    }

    /** SHA-256 of the jar's bytes, hex-encoded — the cache key. Identical content reuses the same
     *  rewritten jar across runs; any change to the jar yields a fresh key (over-invalidate, never
     *  under: a stale rewrite is never reused). */
    static String contentHash(Path file) {
        return jarContentHash(file, "");
    }

    /** SHA-256 of {@code extraKey} (length-framed) followed by the jar's bytes — the config-aware jar
     *  cache key. An empty {@code extraKey} reproduces {@link #contentHash(Path)} bit-for-bit, so the
     *  config-free passes keep their existing cache entries; a non-empty key (the contract pass)
     *  separates distinct redirect/exclude configurations over the same jar into distinct mirrors. */
    private static String jarContentHash(Path file, String extraKey) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            if (!extraKey.isEmpty()) {
                byte[] ek = extraKey.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                md.update(intToBytes(ek.length));
                md.update(ek);
            }
            try (InputStream is = Files.newInputStream(file)) {
                byte[] buf = new byte[1 << 16];
                int n;
                while ((n = is.read(buf)) > 0) {
                    md.update(buf, 0, n);
                }
            }
            return toHex(md.digest());
        } catch (NoSuchAlgorithmException | IOException e) {
            throw new UncheckedIOException(e instanceof IOException io ? io
                    : new IOException("hash failed", e));
        }
    }

    /** Lowercase hex encoding of a digest — the shared cache-key spelling for jars and dirs. */
    private static String toHex(byte[] d) {
        StringBuilder sb = new StringBuilder(d.length * 2);
        for (byte b : d) {
            sb.append(Character.forDigit((b >> 4) & 0xf, 16)).append(Character.forDigit(b & 0xf, 16));
        }
        return sb.toString();
    }
}
