package org.bmc4j.engine;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The handshake between the {@code bmc-contracts} annotation processor and {@code JbmcBackend}.
 * The processor emits one line per contracted method and one line per generated enforce-proof
 * class into {@value #RESOURCE} on the analysis classpath; the backend reads it back to decide
 * how to rewrite call sites for a given proof:
 *
 * <pre>
 * contract &lt;ownerInternal&gt; &lt;name&gt; &lt;descriptor&gt; &lt;stubOwnerInternal&gt; &lt;stubName&gt;
 * enforce  &lt;proofClassInternal&gt;
 * </pre>
 *
 * <ul>
 *   <li>{@code contract} lines become {@link ContractRewriter.Redirect}s (replace direction).</li>
 *   <li>{@code enforce} lines name the generated proof classes; when one of <em>those</em> is the
 *       analysis entry, the backend excludes it as a caller so the proof sees the real body
 *       (modular enforce). Any other entry is a replace proof and is rewritten fully.</li>
 * </ul>
 */
public final class ContractManifest {

    public static final String RESOURCE = "META-INF/bmc-contracts.txt";

    private final List<ContractRewriter.Redirect> redirects;
    private final Set<String> enforceProofClasses;

    private ContractManifest(List<ContractRewriter.Redirect> redirects, Set<String> enforceProofClasses) {
        this.redirects = redirects;
        this.enforceProofClasses = enforceProofClasses;
    }

    /** Redirects for the replace direction, one per contracted method. */
    public List<ContractRewriter.Redirect> redirects() {
        return redirects;
    }

    /** Internal names of the generated enforce-proof classes. */
    public Set<String> enforceProofClasses() {
        return enforceProofClasses;
    }

    public boolean isEmpty() {
        return redirects.isEmpty() && enforceProofClasses.isEmpty();
    }

    // --- formatting (used by the processor) ---

    public static String contractLine(String ownerInternal, String name, String descriptor,
                                      String stubOwnerInternal, String stubName) {
        return String.join(" ", "contract", ownerInternal, name, descriptor, stubOwnerInternal, stubName);
    }

    public static String enforceLine(String proofClassInternal) {
        return "enforce " + proofClassInternal;
    }

    // --- parsing (used by the backend) ---

    public static ContractManifest parse(List<String> lines) {
        List<ContractRewriter.Redirect> redirects = new ArrayList<>();
        Set<String> enforce = new LinkedHashSet<>();
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] t = line.split("\\s+");
            if (t[0].equals("contract") && t.length == 6) {
                redirects.add(new ContractRewriter.Redirect(t[1], t[2], t[3], t[4], t[5]));
            } else if (t[0].equals("enforce") && t.length == 2) {
                enforce.add(t[1]);
            }
        }
        return new ContractManifest(redirects, enforce);
    }

    /** Read and merge every {@value #RESOURCE} found in the directory entries of {@code classpath}. */
    public static ContractManifest readFromClasspath(String classpath) {
        List<String> lines = new ArrayList<>();
        if (classpath != null) {
            for (String entry : classpath.split(File.pathSeparator)) {
                if (entry.isEmpty()) {
                    continue;
                }
                Path res = Path.of(entry).resolve(RESOURCE);
                if (Files.isRegularFile(res)) {
                    try {
                        lines.addAll(Files.readAllLines(res));
                    } catch (IOException ignored) {
                        // best effort: a manifest we can't read just yields no contracts
                    }
                }
            }
        }
        return parse(lines);
    }
}
