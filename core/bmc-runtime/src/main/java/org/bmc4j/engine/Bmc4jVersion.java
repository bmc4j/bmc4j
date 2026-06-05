package org.bmc4j.engine;

/**
 * Identity of the bmc4j runtime <em>analysis semantics</em>, for the verdict cache.
 *
 * <p>A cached green is only sound to replay if the bmc4j layer that would re-derive it is byte-for-byte
 * the same in what it tells the engine. bmc4j is not a thin launcher: before handing bytecode to JBMC it
 * <b>rewrites</b> it — String content ops, lambda/method-ref and pattern-switch {@code invokedynamic}
 * desugaring, config-constant baking, contract redirects, and the vacuity reachability marker (see
 * {@code JbmcBackend.prepareClasspath}). Those rewrites <em>are</em> the proof's semantics. If a release
 * changes any of them, a verdict computed under the old layer may no longer hold under the new one — so a
 * cache produced by an older runtime MUST NOT be trusted by a newer one.
 *
 * <p>{@link #IDENTITY} is therefore part of the cache key. It is the published artifact version (which
 * advances on every release) joined with a {@link #SEMANTICS_REVISION} that a developer bumps whenever a
 * rewriter / model / verdict-derivation change lands <em>without</em> a version bump (e.g. mid-development
 * on a branch). Bumping either one invalidates every cached verdict — the soundness-safe direction
 * (over-invalidation is always acceptable; a stale green is a soundness bug).
 */
public final class Bmc4jVersion {

    private Bmc4jVersion() {
    }

    /**
     * The published runtime artifact version. Read from the runtime jar's manifest
     * ({@code Implementation-Version}, stamped by the build from the release tag) when present, else
     * this fallback — the build's dev default, used when running straight from {@code build/classes}
     * with no manifest. Dev-build cache identity then leans on {@link #SEMANTICS_REVISION} and the
     * cache key's classpath-content digest (which hashes the runtime's own classes when they're on
     * the analysis classpath), so a constant fallback here cannot serve a stale green.
     */
    private static final String ARTIFACT_VERSION = resolveArtifactVersion("0.0.1-local");

    /**
     * A monotonically-bumped tag for analysis-semantics changes that ship without an artifact-version
     * bump. Increment this whenever a change to the bytecode rewriters, the bundled models, or the
     * verdict-derivation logic could change a proof's verdict. This session changed the rewrite layer,
     * so the cache must start fresh against any pre-existing on-disk entries.
     */
    private static final String SEMANTICS_REVISION = "r1";

    /** The runtime semantics identity baked into every verdict-cache key. */
    public static final String IDENTITY = ARTIFACT_VERSION + "+" + SEMANTICS_REVISION;

    private static String resolveArtifactVersion(String fallback) {
        try {
            String v = Bmc4jVersion.class.getPackage().getImplementationVersion();
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        } catch (RuntimeException ignored) {
            // Fail-open to the fallback — the cache key just uses the hardcoded version.
        }
        return fallback;
    }
}
