package org.bmc4j;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a STATIC method as a MODE-CONDITIONAL override that lives BESIDE the default it replaces, swapped
 * in at analysis-prep time when its {@link #condition()} holds for the proof's resolved configuration.
 *
 * <p>The override and its {@link #target()} live in the SAME class. {@code target} names the method the
 * override replaces; the override's OWN descriptor must EQUAL the target's, so overloads stay distinct
 * (e.g. an override of {@code toString(int)} does not affect {@code toString(long)}). At prep time, for
 * each {@code @ConditionalOn} method whose condition holds, bmc4j REDIRECTS every call to the target to
 * the annotated override (the proven call-site redirect, the same machinery {@code StringBytecode} /
 * {@code StringLengthBytecode} use). When the condition does NOT hold, nothing happens — the target keeps
 * its default body, so the unaffected path (e.g. string refinement) is untouched.
 *
 * <pre>{@code
 * public static String toString(int i) {            // default — delegates to the refinement intrinsic
 *     return org.cprover.CProverString.toString(i);
 * }
 *
 * @ConditionalOn(condition = BmcCondition.STRING_REFINEMENT_OFF, target = "toString")
 * static String toStringNoRefine(int i) {           // swapped in under CHAR_ARRAY_MODEL
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
     * The simple NAME of the method (in the same class) this override replaces. The override's own
     * descriptor must equal the target's, so the redirect is overload-precise.
     */
    String target();
}
