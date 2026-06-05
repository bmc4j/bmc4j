package example.custommodelskt

import org.bmc4j.Bmc

/**
 * Analysis-only **model**, written in Kotlin, with the same fully-qualified name as the
 * real [example.ExchangeRates]. On JBMC's analysis classpath it shadows the real class;
 * it never reaches the test runtime classpath. The rate is symbolic, so proofs hold for
 * every value in range (0.0001 .. 2.0).
 */
class ExchangeRates {
    fun usdToEurBips(): Int = Bmc.anyInt(1, 20_000)
}
