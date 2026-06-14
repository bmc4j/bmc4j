package org.bmc4j;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a test-side class that registers contracts with the {@code contractFor(...)} DSL, so the build's
 * DSL-lowering pass knows to decode it and generate the enforce-proof(s). The annotated class declares one
 * or more {@code contractFor(Type::member) { ... }} calls (typically in its constructor or an
 * {@code init} block); the pass reads the method reference and the predicate lambdas statically and emits
 * a {@code <Class>__BmcDslEnforce} {@link BmcProof} that discharges each contract against the real body.
 *
 * <p>This is the DSL analogue of {@link BmcContractsFor}: same enforce-before-reuse guarantee, but the
 * contract is authored as typed pre/post-condition lambdas over an unbound method reference rather than
 * as stringly-typed {@code @Requires}/{@code @Ensures} predicate-method names.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface BmcContracts {

    /**
     * The verdict every generated enforce-proof of this registration is expected to produce - the DSL
     * analogue of {@link BmcContractsFor#expectEnforce()}. Defaults to {@link Verdict#VERIFIED}: a real
     * contract's enforce-proof must be green. Declare {@link Verdict#REFUTED} on a deliberately-false demo
     * registration so its enforce-proof self-asserts that a false postcondition is caught.
     */
    Verdict expectEnforce() default Verdict.VERIFIED;
}
