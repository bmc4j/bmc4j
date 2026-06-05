package example.custommodels;

import org.bmc4j.Bmc;

/**
 * Proof model of the live {@link ExchangeRates} service. Same fully-qualified name as
 * the real class — so on JBMC's analysis classpath this shadows it (the real one still
 * runs in production; this is never on the runtime classpath).
 *
 * <p>Instead of a network call it returns {@link Bmc#anyInt} — a <em>symbolic</em>
 * rate. The proof then holds for <b>every</b> rate in the range at once, not just one
 * sampled value. Bound it to whatever the service can actually return.
 */
public class ExchangeRates {

    public int usdToEurBips() {
        return Bmc.anyInt(1, 20_000); // any plausible rate: 0.0001 .. 2.0
    }
}
