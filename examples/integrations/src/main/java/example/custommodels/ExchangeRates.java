package example.custommodels;

/**
 * A live FX-rate service — in real life it makes a network call. You can't (and
 * shouldn't) hit it inside a proof: it's non-deterministic and has side effects. To
 * verify code that depends on it, provide a <em>model</em> in {@code src/bmcModel}
 * with the same fully-qualified name; JBMC uses the model, the real class still runs
 * in production.
 */
public class ExchangeRates {

    /** Current USD→EUR rate in basis points (1 bip = 0.0001; e.g. 9000 = 0.9). */
    public int usdToEurBips() {
        throw new UnsupportedOperationException("live FX service — model it for proofs");
    }
}
