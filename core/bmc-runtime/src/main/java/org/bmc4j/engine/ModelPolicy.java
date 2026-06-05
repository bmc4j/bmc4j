package org.bmc4j.engine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns the model <em>facts</em> ({@link ModelManifest}: the user's intent declarations + the model
 * classes actually compiled under {@code src/bmcModel}) into a <em>policy</em> decision for a green
 * proof — exactly the fact-vs-policy split {@link StubPolicy} does for nondet stubs.
 *
 * <p>A user model is {@code Bmc.assume()} at classpath altitude: it can quietly change what a proof
 * means, and unlike a stub it isn't visible at the proof site at all. The trust layer therefore mirrors
 * the stub ladder (footnote → warn → strict):
 *
 * <ul>
 *   <li><b>declared</b> models present on the classpath get a provenance footnote on every green proof
 *       — naming the model, and for {@code domain} models appending the declared rationale ("proven
 *       under domain model {@code acme.NoCollisionMap} (assumes no key collisions)");</li>
 *   <li><b>undeclared</b> present user models (a class shadowing on the analysis classpath with no
 *       {@code bmc { models { … } }} declaration) are a trust gap — footnoted loudly in lenient mode and
 *       forced to UNKNOWN under {@code strictModels}, so no proof silently rests on an undeclared
 *       override;</li>
 *   <li><b>overriding</b> models — a present user model whose name shadows a bundled/JDK model bmc4j
 *       already ships a verified stand-in for — warn loudly even in lenient mode (you're replacing a
 *       checked model with an unchecked one).</li>
 * </ul>
 *
 * <p><b>Granularity.</b> "Present on the classpath" is the relevance signal, not "provably exercised by
 * this specific proof": JBMC gives no per-proof which-model-bodies-were-linked report (unlike the
 * nondet-stub messages it emits), so the honest unit is "this proof's analysis classpath included the
 * user model". That can over-attribute a model to a proof that didn't actually call it; the footnote
 * wording stays deliberately classpath-scoped ("relied on the model(s) on this analysis classpath")
 * rather than claiming per-call precision. This is the same granularity {@code src/bmcModel} shadowing
 * already has — every proof in the module shares that classpath.
 */
public final class ModelPolicy {

    private final List<UserModel> declaredPresent;   // declarations whose class is actually present
    private final List<String> undeclaredPresent;    // present user-model classes with no declaration
    private final List<String> overriding;           // present user models shadowing a bundled/JDK model

    private ModelPolicy(List<UserModel> declaredPresent, List<String> undeclaredPresent,
                        List<String> overriding) {
        this.declaredPresent = declaredPresent;
        this.undeclaredPresent = undeclaredPresent;
        this.overriding = overriding;
    }

    /**
     * Judge a manifest: cross the declared models against the classes actually present under
     * {@code src/bmcModel}. A declared model whose class isn't present is dropped (it can't affect a
     * verdict — likely a typo, but harmless to the trust layer); a present class with no declaration is
     * an undeclared override.
     */
    public static ModelPolicy judge(ModelManifest manifest) {
        Map<String, UserModel> byName = new LinkedHashMap<>();
        for (UserModel m : manifest.declared()) {
            byName.put(m.className(), m); // last declaration of a name wins
        }
        List<UserModel> declaredPresent = new ArrayList<>();
        List<String> undeclaredPresent = new ArrayList<>();
        List<String> overriding = new ArrayList<>();
        for (String present : manifest.presentClasses()) {
            UserModel decl = byName.get(present);
            if (decl != null) {
                declaredPresent.add(decl);
            } else {
                undeclaredPresent.add(present);
            }
            if (shadowsBundledModel(present)) {
                overriding.add(present);
            }
        }
        return new ModelPolicy(declaredPresent, undeclaredPresent, overriding);
    }

    /** Declared models actually present on the classpath — the ones a green proof footnotes for provenance. */
    public List<UserModel> declaredPresent() {
        return declaredPresent;
    }

    /** Present user-model classes with no intent declaration — the strict-UNKNOWN / loud-footnote subset. */
    public List<String> undeclaredPresent() {
        return undeclaredPresent;
    }

    /** Present user models shadowing a bundled/JDK model bmc4j already verifies — warned loud always. */
    public List<String> overriding() {
        return overriding;
    }

    /** True if any user model is present (declared or not) — i.e. there is provenance to surface. */
    public boolean hasAnyPresent() {
        return !declaredPresent.isEmpty() || !undeclaredPresent.isEmpty();
    }

    /** True if a present user model has no declaration (footnote-loud in lenient, UNKNOWN in strict). */
    public boolean hasUndeclared() {
        return !undeclaredPresent.isEmpty();
    }

    /** True if a present user model shadows a bundled/JDK verified model (always a loud warning). */
    public boolean hasOverriding() {
        return !overriding.isEmpty();
    }

    /**
     * True if {@code fqn} names a class bmc4j already ships a (verified) model for — the JDK ({@code java.}
     * / {@code javax.} / {@code jdk.}) and the Kotlin runtime ({@code kotlin.} / {@code kotlinx.}).
     * Shadowing one of these replaces a checked stand-in with an unchecked user one, so it's worth a loud
     * warning. Coarse on purpose: it's keyed on the package roots bmc4j models, not an enumeration of the
     * bundled jar's exact classes (which would couple this to the jar's build layout) — and it errs only
     * toward warning, never toward a false green.
     */
    static boolean shadowsBundledModel(String fqn) {
        if (fqn == null) {
            return false;
        }
        return fqn.startsWith("java.") || fqn.startsWith("javax.") || fqn.startsWith("jdk.")
                || fqn.startsWith("kotlin.") || fqn.startsWith("kotlinx.");
    }
}
