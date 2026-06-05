package org.bmc4j.engine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Locates the JBMC binary that is <em>bundled</em> on the test runtime classpath
 * (shipped inside a {@code bmc-engine-<platform>} jar) and extracts it to a local
 * cache so it can be executed. There is no network access: the engine arrived as
 * an ordinary, integrity-verified Gradle dependency.
 *
 * <p>Layout inside the engine jar, for platform {@code <p>}:
 * <pre>
 *   jbmc/&lt;p&gt;/files.txt      newline list of files to extract
 *   jbmc/&lt;p&gt;/version.txt    engine version (used as a cache key)
 *   jbmc/&lt;p&gt;/bin/jbmc[.exe]
 *   jbmc/&lt;p&gt;/lib/core-models.jar
 * </pre>
 */
public final class BundledEngine {

    private BundledEngine() {
    }

    /**
     * The bundled engine's version string (e.g. {@code "cbmc-6.9.0"}), or {@code null} if no engine
     * is bundled on the classpath. Used as part of the verdict-cache key: a new engine
     * version can change a verdict, so its identity must bust the cache.
     */
    public static String version() {
        Platform platform = Platform.current();
        return readResourceAsString("jbmc/" + platform.id() + "/version.txt");
    }

    /** Serializes first-use extraction across this JVM's proof worker threads. */
    private static final Object EXTRACT_LOCK = new Object();

    /**
     * Extract (once) and return the path to the bundled jbmc executable.
     *
     * <p>Concurrency-safe: proofs verify in parallel, so first use races N workers here
     * (observed on CI as a {@code FileAlreadyExistsException} mid-{@code Files.copy}).
     * In-JVM racers are serialized by a lock; cross-process racers (parallel Gradle test
     * JVMs sharing the user-level cache) are handled by extracting into a unique temp
     * dir and atomically renaming it into place — the cache dir is only ever observed
     * complete, and the losing extractor just uses the winner's copy.
     */
    public static String extract() {
        Platform platform = Platform.current();
        // The bundled Linux engine is a glibc build (from CBMC's `.deb`); on a musl C library
        // (Alpine) it can't exec and the dynamic linker emits a confusing "not found" error. The
        // name/arch mapper in Platform.of can't see the C library, so detect musl here — at the
        // actual selection/extraction site — and fail fast with an actionable message instead.
        if (!platform.isWindows() && !platform.isMac() && isMuslLibc()) {
            throw new UnsupportedOperationException(
                    "bmc4j's bundled Linux engine is glibc-only and cannot run on a musl/Alpine "
                            + "system (detected " + muslEvidence() + "). Supported Linux: glibc x64/arm64. "
                            + "Set -Dbmc.jbmc=<path to a musl-compatible jbmc> to use a local binary.");
        }
        String root = "jbmc/" + platform.id();
        List<String> files = readManifest(root + "/files.txt", platform);
        String version = readResourceAsString(root + "/version.txt");

        Path cacheDir = baseCacheDir().resolve(platform.id() + (version != null ? "-" + version : ""));
        String exeRel = "bin/jbmc" + (platform.isWindows() ? ".exe" : "");
        Path exe = cacheDir.resolve(exeRel);
        if (Files.isRegularFile(exe)) {
            return exe.toString();
        }

        synchronized (EXTRACT_LOCK) {
            if (Files.isRegularFile(exe)) {
                return exe.toString(); // an in-JVM racer extracted while we waited
            }
            try {
                Path tmp = Files.createTempDirectory(
                        Files.createDirectories(cacheDir.getParent()),
                        cacheDir.getFileName() + ".tmp-");
                for (String rel : files) {
                    Path target = tmp.resolve(rel);
                    Files.createDirectories(target.getParent());
                    try (InputStream in = resource(root + "/" + rel)) {
                        if (in == null) {
                            throw new IllegalStateException("Bundled engine is missing " + rel);
                        }
                        Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
                Path tmpExe = tmp.resolve(exeRel);
                tmpExe.toFile().setExecutable(true);
                if (platform.isMac()) {
                    // Clear Gatekeeper quarantine and ad-hoc sign so a relocated binary runs.
                    bestEffort("xattr", "-dr", "com.apple.quarantine", tmp.toString());
                    bestEffort("codesign", "--force", "--sign", "-", tmpExe.toString());
                }
                try {
                    Files.move(tmp, cacheDir, StandardCopyOption.ATOMIC_MOVE);
                } catch (IOException raced) {
                    if (Files.isRegularFile(exe)) {
                        // A concurrent process won and its copy is complete — use it.
                        deleteRecursively(tmp);
                    } else {
                        // cacheDir exists but has no executable: a stale partial from a
                        // pre-fix crash. Replace it with our complete copy.
                        deleteRecursively(cacheDir);
                        Files.move(tmp, cacheDir, StandardCopyOption.ATOMIC_MOVE);
                    }
                }
                return exe.toString();
            } catch (IOException e) {
                throw new IllegalStateException("Failed to extract bundled JBMC engine to " + cacheDir, e);
            }
        }
    }

    /**
     * True if this host uses the musl C library (Alpine) rather than glibc. Checked only on
     * Linux. Reads the real filesystem root; {@link #isMuslLibc(Path)} is the testable core.
     */
    static boolean isMuslLibc() {
        return isMuslLibc(Path.of("/"));
    }

    /**
     * True if {@code root} looks like a musl/Alpine system. Two independent, reliable signals:
     * the Alpine release marker, or a musl dynamic loader under {@code /lib}. Either is sufficient;
     * a glibc system has neither. Package-private + root-injecting so tests can stage the markers.
     */
    static boolean isMuslLibc(Path root) {
        if (Files.exists(root.resolve("etc/alpine-release"))) {
            return true;
        }
        return hasMuslLoader(root.resolve("lib")) || hasMuslLoader(root.resolve("usr/lib"));
    }

    /** True if {@code dir} contains a musl dynamic loader ({@code ld-musl-<arch>.so.1}). */
    private static boolean hasMuslLoader(Path dir) {
        if (!Files.isDirectory(dir)) {
            return false;
        }
        try (var entries = Files.newDirectoryStream(dir, "ld-musl-*")) {
            return entries.iterator().hasNext();
        } catch (IOException e) {
            return false;
        }
    }

    /** Short description of which musl signal fired, for the failure message. */
    private static String muslEvidence() {
        return Files.exists(Path.of("/etc/alpine-release")) ? "/etc/alpine-release" : "a musl ld loader";
    }

    /** Best-effort recursive delete (cleanup of temp/partial extraction dirs). */
    private static void deleteRecursively(Path dir) {
        try (var walk = Files.walk(dir)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException ignored) {
            // Non-fatal: a leftover temp dir is harmless.
        }
    }

    private static List<String> readManifest(String resourcePath, Platform platform) {
        try (InputStream in = resource(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException(
                        "No bundled JBMC engine for platform '" + platform.id() + "' on the test classpath.\n"
                                + "Add the matching engine dependency (the 'org.bmc4j' plugin does this "
                                + "automatically), or set -Dbmc.jbmc=<path to a local jbmc>.");
            }
            List<String> out = new ArrayList<>();
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    out.add(trimmed);
                }
            }
            return out;
        } catch (IOException e) {
            throw new IllegalStateException("Could not read engine manifest " + resourcePath, e);
        }
    }

    private static String readResourceAsString(String resourcePath) {
        try (InputStream in = resource(resourcePath)) {
            if (in == null) {
                return null;
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            return null;
        }
    }

    private static InputStream resource(String path) {
        return BundledEngine.class.getClassLoader().getResourceAsStream(path);
    }

    private static Path baseCacheDir() {
        String home = System.getProperty("user.home");
        return Path.of(home, ".cache", "bmc4j", "engine");
    }

    private static void bestEffort(String... cmd) {
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            p.getInputStream().readAllBytes();
            p.waitFor();
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            // Non-fatal: if signing/xattr is unavailable the binary may still run.
        }
    }
}
