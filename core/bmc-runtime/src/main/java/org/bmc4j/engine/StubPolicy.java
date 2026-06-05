package org.bmc4j.engine;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Turns the harvested nondet-stub <em>fact</em> into a <em>policy</em> decision: given the
 * stubs a proof reached, the acknowledged-stub allowlist, the user's own package prefixes, and whether
 * strict mode is on, decide what to surface.
 *
 * <p>This is deliberately a pure function of (fact, policy) so it can run identically on a fresh engine
 * result and on a cache hit — flipping {@code strictStubs} or editing {@code allowStubs} re-judges the
 * <em>stored</em> stub list without re-running the engine.
 *
 * <p>Separation of concerns:
 * <ul>
 *   <li><b>acknowledged</b> stubs (matched by an {@code allowStubs} pattern) are silent;</li>
 *   <li><b>user-owned</b> unacknowledged stubs ({@link StubFilter#isUserOwned}) are a config bug —
 *       warned loudly even in lenient mode, and they force UNKNOWN in strict mode;</li>
 *   <li><b>other</b> unacknowledged stubs (unmodeled JDK / third-party) get the default footnote, and
 *       force UNKNOWN in strict mode.</li>
 * </ul>
 */
public final class StubPolicy {

    private final List<String> unacknowledged;  // signal stubs not matched by the allowlist
    private final List<String> userOwned;       // subset of unacknowledged from the user's own packages

    private StubPolicy(List<String> unacknowledged, List<String> userOwned) {
        this.unacknowledged = unacknowledged;
        this.userOwned = userOwned;
    }

    /**
     * Judge {@code stubbed} against the {@code allowStubs} patterns and {@code userPackages}. A pattern
     * is a fully-qualified method name with an optional trailing {@code .*} or {@code *} wildcard, e.g.
     * {@code "java.util.Formatter.format"}, {@code "java.util.Formatter.*"}, or {@code "java.util.*"}.
     */
    public static StubPolicy judge(List<String> stubbed, List<String> allowStubs, String userPackages) {
        List<String> unack = new ArrayList<>();
        List<String> user = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String fqn : stubbed) {
            if (fqn == null || !seen.add(fqn) || matchesAny(fqn, allowStubs)) {
                continue;
            }
            unack.add(fqn);
            if (StubFilter.isUserOwned(fqn, userPackages)) {
                user.add(fqn);
            }
        }
        return new StubPolicy(unack, user);
    }

    /** Stubs that are NOT acknowledged by the allowlist — the ones a footnote/strict mode acts on. */
    public List<String> unacknowledged() {
        return unacknowledged;
    }

    /** The unacknowledged stubs from the user's own packages — the loud config-bug subset. */
    public List<String> userOwned() {
        return userOwned;
    }

    /** True if there's anything unacknowledged to warn about (footnote in lenient, UNKNOWN in strict). */
    public boolean hasUnacknowledged() {
        return !unacknowledged.isEmpty();
    }

    /** True if a stub from the user's own classpath was reached — a likely missing-dependency config bug. */
    public boolean hasUserOwned() {
        return !userOwned.isEmpty();
    }

    /**
     * True if {@code fqn} matches one of {@code patterns}. Each pattern is an exact method FQN, or ends
     * in {@code .*} / {@code *} to match a package or class prefix. {@code "a.b.C.*"} matches every method
     * of {@code a.b.C}; {@code "a.b.*"} matches everything under {@code a.b}; {@code "a.b.C.m"} is exact.
     */
    static boolean matchesAny(String fqn, List<String> patterns) {
        if (patterns == null) {
            return false;
        }
        for (String raw : patterns) {
            if (raw == null) {
                continue;
            }
            String p = raw.trim();
            if (p.isEmpty()) {
                continue;
            }
            if (p.endsWith(".*")) {
                String prefix = p.substring(0, p.length() - 1); // keep the dot: "a.b.C."
                if (fqn.startsWith(prefix)) {
                    return true;
                }
            } else if (p.endsWith("*")) {
                if (fqn.startsWith(p.substring(0, p.length() - 1))) {
                    return true;
                }
            } else if (fqn.equals(p)) {
                return true;
            }
        }
        return false;
    }
}
