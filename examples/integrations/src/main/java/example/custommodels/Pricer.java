package example.custommodels;

/** Converts USD to EUR using the (modeled) {@link ExchangeRates} service. */
public class Pricer {

    private final ExchangeRates rates;

    public Pricer(ExchangeRates rates) {
        this.rates = rates;
    }

    /** Convert an amount in USD cents to EUR cents at the current rate. */
    public int eurCents(int usdCents) {
        int bips = rates.usdToEurBips();
        return usdCents * bips / 10_000;
    }
}
