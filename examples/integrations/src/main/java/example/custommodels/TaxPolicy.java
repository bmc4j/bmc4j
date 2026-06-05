package example.custommodels;

/**
 * A live tax-policy service. Like {@link ExchangeRates} it is un-analyzable at runtime (it would call
 * out to a real service), so it throws here; proofs run against the analysis-only model in
 * {@code src/bmcModel/java/example/custommodels/TaxPolicy.java}, which shadows this class on JBMC's
 * analysis classpath only.
 */
public class TaxPolicy {

    public int rateBips(String region) {
        throw new UnsupportedOperationException("live tax service — model it for proofs");
    }

    public boolean regionMatchesItself(String region) {
        throw new UnsupportedOperationException("live tax service — model it for proofs");
    }

    public long taxOn(long amountCents, String region) {
        throw new UnsupportedOperationException("live tax service — model it for proofs");
    }
}
