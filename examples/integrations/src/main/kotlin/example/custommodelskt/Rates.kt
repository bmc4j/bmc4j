package example.custommodelskt

/** A live FX service — un-analyzable, throws in src/main. Model it for proofs. */
class ExchangeRates {
    fun usdToEurBips(): Int =
        throw UnsupportedOperationException("live FX service — model it for proofs")
}

/** The code under proof. Its logic must hold for every rate the service could return. */
class Pricer(private val rates: ExchangeRates) {
    fun eurCents(usdCents: Int): Int = usdCents * rates.usdToEurBips() / 10_000
}
