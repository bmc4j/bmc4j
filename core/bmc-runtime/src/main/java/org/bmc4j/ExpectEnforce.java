package org.bmc4j;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Per-mirror-method expected verdict for a contract's generated <b>enforce-proof</b> — the
 * method-level override of {@link BmcContractsFor#expectEnforce()} (which sets the default for
 * every mirror method of the type).
 *
 * <p>Use it when one contract type mixes a deliberately-false demo mirror with genuine contracts:
 *
 * <pre>{@code
 * @BmcContractsFor(Deltas.class)
 * interface DeltasContract {
 *     @ExpectEnforce(Verdict.REFUTED)              // the demo: a FALSE @Ensures must be refuted
 *     @Requires("bounded") @Ensures("neverNegative") int delta(int a, int b);
 *
 *     @Requires("bounded") @Ensures("neverNegative") int absDelta(int a, int b);  // real: must verify
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ExpectEnforce {

    /** The verdict the generated enforce-proof for this mirror method must produce. */
    Verdict value();
}
