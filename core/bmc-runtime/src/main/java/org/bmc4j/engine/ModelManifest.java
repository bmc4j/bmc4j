package org.bmc4j.engine;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Reads the two facts the model-trust policy needs from the test JVM's system properties, both set by
 * the Gradle plugin:
 *
 * <ul>
 *   <li>{@code bmc.models} — the user's <em>declarations</em> from {@code bmc { models { … } }},
 *       serialized one model per entry as {@code intent|fqn|rationale} (rationale present only for
 *       {@code domain}). This is the trust metadata.</li>
 *   <li>{@code bmc.userModels} — the path(s) to the compiled {@code src/bmcModel} output (the same
 *       property {@link JbmcBackend} prepends to the analysis classpath). Scanning it gives the
 *       <em>actual</em> user-model classes present, so the policy can compare declarations against what's
 *       really shadowing on the classpath.</li>
 * </ul>
 *
 * <p>Deliberately a pure reader of (declarations, present-classes) — the {@link ModelPolicy} turns those
 * into footnote / override-warning / strict-UNKNOWN, exactly as {@link StubPolicy} consumes the stub
 * fact. Parsing is fail-loud on a malformed declaration entry (a typo'd intent must break the build, not
 * silently drop a model from the trust layer), mirroring the runtime's visible-over-silent ethos.
 */
public final class ModelManifest {

    /** Serialized declarations from {@code bmc { models { … } }}. */
    public static final String MODELS_PROP = "bmc.models";
    /** Compiled {@code src/bmcModel} output dir(s); same property the backend prepends to the classpath. */
    public static final String USER_MODELS_PROP = "bmc.userModels";

    /** Entry separator within {@code bmc.models} (newline-free so it survives a {@code -D} flag). */
    static final String ENTRY_SEP = ";;";
    /** Field separator within one declaration entry: {@code intent|fqn|rationale}. */
    static final String FIELD_SEP = "|";

    private final List<UserModel> declared;
    private final Set<String> presentClasses; // FQNs actually compiled under src/bmcModel

    private ModelManifest(List<UserModel> declared, Set<String> presentClasses) {
        this.declared = declared;
        this.presentClasses = presentClasses;
    }

    /** Read both facts from system properties (the live runtime path). */
    public static ModelManifest fromSystemProperties() {
        return of(System.getProperty(MODELS_PROP, ""), System.getProperty(USER_MODELS_PROP, ""));
    }

    /**
     * Build a manifest from a raw {@code bmc.models} string and a path-separator-delimited
     * {@code userModels} class-dir list. Visible for unit testing; the production path is
     * {@link #fromSystemProperties()}.
     */
    public static ModelManifest of(String modelsProp, String userModelsPath) {
        return new ModelManifest(parseDeclarations(modelsProp), scanPresentClasses(userModelsPath));
    }

    /** The declared models from the DSL (may reference classes not actually present — that's a config bug). */
    public List<UserModel> declared() {
        return declared;
    }

    /** Fully-qualified names of the model classes actually compiled under {@code src/bmcModel}. */
    public Set<String> presentClasses() {
        return presentClasses;
    }

    public boolean isEmpty() {
        return declared.isEmpty() && presentClasses.isEmpty();
    }

    /** Serialize declarations for the {@code bmc.models} sysprop (used by the plugin / tests). */
    public static String serialize(List<UserModel> models) {
        StringBuilder sb = new StringBuilder();
        for (UserModel m : models) {
            if (sb.length() > 0) {
                sb.append(ENTRY_SEP);
            }
            sb.append(m.intent().name().toLowerCase(java.util.Locale.ROOT))
                    .append(FIELD_SEP).append(m.className())
                    .append(FIELD_SEP).append(m.rationale() == null ? "" : m.rationale());
        }
        return sb.toString();
    }

    private static List<UserModel> parseDeclarations(String prop) {
        List<UserModel> out = new ArrayList<>();
        if (prop == null || prop.isBlank()) {
            return out;
        }
        for (String entry : prop.split(java.util.regex.Pattern.quote(ENTRY_SEP))) {
            if (entry.isBlank()) {
                continue;
            }
            // intent|fqn|rationale — split into at most 3 so a rationale may contain a '|'.
            String[] f = entry.split(java.util.regex.Pattern.quote(FIELD_SEP), 3);
            String intent = f.length > 0 ? f[0].trim() : "";
            String fqn = f.length > 1 ? f[1].trim() : "";
            String rationale = f.length > 2 ? f[2].trim() : "";
            if (fqn.isEmpty()) {
                throw new IllegalArgumentException(
                        "malformed bmc { models } entry (no class name): \"" + entry + "\"");
            }
            switch (intent) {
                case "conformant" -> out.add(UserModel.conformant(fqn));
                case "domain" -> out.add(UserModel.domain(fqn, rationale));
                default -> throw new IllegalArgumentException(
                        "unknown model intent \"" + intent + "\" for " + fqn
                                + " (expected \"conformant\" or \"domain\")");
            }
        }
        return out;
    }

    /** Walk the {@code src/bmcModel} class dirs and collect every top-level {@code .class}'s FQN. */
    private static Set<String> scanPresentClasses(String userModelsPath) {
        Set<String> present = new LinkedHashSet<>();
        if (userModelsPath == null || userModelsPath.isBlank()) {
            return present;
        }
        for (String entry : userModelsPath.split(File.pathSeparator)) {
            if (entry.isBlank()) {
                continue;
            }
            Path dir = Path.of(entry.trim());
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(dir)) {
                walk.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().endsWith(".class"))
                        .forEach(p -> {
                            String rel = dir.relativize(p).toString()
                                    .replace('\\', '/').replace('/', '.');
                            rel = rel.substring(0, rel.length() - ".class".length());
                            // Skip nested/synthetic classes ('$'): a top-level FQN is what a user declares.
                            if (rel.indexOf('$') < 0) {
                                present.add(rel);
                            }
                        });
            } catch (IOException | RuntimeException e) {
                // Fail-open on a scan error: a missing present-class only weakens override/strict
                // detection, never produces a false green (the policy errs toward "undeclared").
            }
        }
        return present;
    }
}
