package org.bmc4j;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a STATIC method as a MODE-CONDITIONAL override that lives BESIDE the default it replaces, swapped
 * in at analysis-prep time when its {@link #condition()} holds for the proof's resolved configuration.
 *
 * <p>{@code target} names the method the override replaces; the override's OWN descriptor must EQUAL the
 * target's, so overloads stay distinct (e.g. an override of {@code toString(int)} does not affect
 * {@code toString(long)}). At prep time, for each {@code @ConditionalOn} method whose condition holds,
 * bmc4j REDIRECTS every call to the target to the annotated override (the proven call-site redirect, the
 * same machinery {@code StringBytecode} / {@code StringLengthBytecode} use). When the condition does NOT
 * hold, nothing happens — the target keeps its default body, so the unaffected path (e.g. string
 * refinement) is untouched.
 *
 * <h2>Same-class vs cross-class target</h2>
 * By default ({@link #targetClass()} empty) the target is a method of the SAME class as the override. Set
 * {@code targetClass} to redirect a method of ANOTHER class — letting the override live in a bmc4j HELPER
 * instead of a JDK shadow. This is how the {@code int}/{@code long -> String} bounding works WITHOUT
 * shadowing {@code java.lang.Integer}/{@code Long} (too pervasive to shadow safely): the override lives in
 * {@code org.bmc4j.engine.BmcStrings} and targets the single refinement primitive every {@code int}/
 * {@code long -> String} funnel bottoms out in, {@code org.cprover.CProverString.toString}.
 *
 * <pre>{@code
 * // In a bmc4j helper (NOT a JDK shadow): redirect the cross-class choke point under no-refine.
 * @ConditionalOn(condition = BmcCondition.STRING_REFINEMENT_OFF,
 *                targetClass = "org.cprover.CProverString", target = "toString")
 * public static String ofInt(int i) {               // descriptor (I)Ljava/lang/String; == the target's
 *     ... bounded digit build into a fixed char[] ...
 * }
 * }</pre>
 *
 * <h2>Soundness</h2>
 * This is a COMPLETENESS/correctness mechanism for selecting between two bodies that are each meant to be
 * sound on their own path; it never relaxes an assertion. A mis-targeted override (wrong descriptor) is
 * simply not applied (no matching call site), never a false VERIFIED.
 *
 * <h2>Scope (MVP)</h2>
 * The override MUST be {@code static} (the current redirect is a call-site retarget to a static method).
 * This covers the motivating cases ({@code Integer}/{@code Long.toString}). Instance-method body-swap is a
 * future expansion and is intentionally NOT supported yet.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ConditionalOn {

    /** The prep-time condition under which this override replaces its {@link #target()}. */
    BmcCondition condition();

    /**
     * The simple NAME of the method this override replaces. The override's own descriptor must equal the
     * target's, so the redirect is overload-precise. The target's owner is {@link #targetClass()} when set,
     * else the override's own class.
     */
    String target();

    /**
     * The binary name (e.g. {@code "org.cprover.CProverString"}) of the class that DECLARES the
     * {@link #target()} to redirect. EMPTY (the default) means the same class as the override (same-class
     * MVP behavior). When set, the redirect retargets {@code targetClass.target(desc)} call sites to the
     * override — the override lives in a bmc4j helper rather than shadowing the target's owner.
     */
    String targetClass() default "";
}
