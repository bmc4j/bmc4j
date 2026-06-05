package org.bmc4j;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that a test-side type carries the method contracts for a production class, keeping
 * {@code @Requires}/{@code @Ensures} (and their predicates) <b>out of production code</b>.
 *
 * <p>The annotated type — typically an interface in {@code src/test} — mirrors the contracted
 * methods by signature and holds the predicate methods. The {@code bmc-contracts} processor
 * binds each mirror method to the same-named, same-parameter {@code static} method on
 * {@link #value()} and generates the replace-stub, the enforce-proof, and the manifest entry,
 * exactly as if the annotations sat on the production method.
 *
 * <pre>{@code
 * // src/main — plain, no bmc references
 * public final class Triangle {
 *     public static int triangle(int n) { int s = 0; for (int i = 1; i <= n; i++) s += i; return s; }
 * }
 *
 * // src/test — the contract lives here
 * @BmcContractsFor(Triangle.class)
 * interface TriangleContract {
 *     @Requires("bounded") @Ensures("nonNeg") int triangle(int n);   // signature mirrors the target
 *     static boolean bounded(int n)            { return n >= 0 && n <= 8; }
 *     static boolean nonNeg(int result, int n) { return result >= 0; }
 * }
 * }</pre>
 *
 * <p>Binding is by signature (like a {@code src/bmcModel} model binds to the real class), so a
 * production rename can orphan a contract; the processor errors if a mirror method has no
 * matching target. v1: {@code static}, value-returning target methods.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface BmcContractsFor {

    /** The production class whose methods these contracts describe. */
    Class<?> value();

    /**
     * The verdict every generated <b>enforce-proof</b> of this contract type is expected to
     * produce — the contracts analogue of {@link BmcProof#expect()}. Defaults to
     * {@link Verdict#VERIFIED}: a real contract's enforce-proof must be green before callers may
     * reuse it.
     *
     * <p>Declare {@link Verdict#REFUTED} on a <em>deliberately false</em> demo contract (its
     * enforce-proof passing-by-refutation proves "annotating is not asserting" still holds) or
     * {@link Verdict#VACUOUS} on an unsatisfiable-{@code @Requires} demo (proving the vacuity
     * guard still catches an empty precondition). Applies to every enforce-proof generated from
     * this type, so keep demo contracts single-method.
     */
    Verdict expectEnforce() default Verdict.VERIFIED;
}
