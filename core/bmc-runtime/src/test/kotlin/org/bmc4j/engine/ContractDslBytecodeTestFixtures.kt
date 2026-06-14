package org.bmc4j.engine

import org.bmc4j.BmcContracts
import org.bmc4j.contracts.contractFor

/** A target instance method for the [ContractDslBytecode] decode test. */
class DslFixtureTarget {
    fun scale(amount: Int): Int = amount * 3
}

/** A `@BmcContracts` registration using the DSL - its bytecode is decoded by the test. */
@BmcContracts
class ContractDslBytecodeTestFixtures {
    init {
        contractFor(DslFixtureTarget::scale) {
            whenPrecondition("amount in range") { self, amount -> amount in 0..8 }
                    .thenPostCondition("result non-negative") { before, after, amount, ret -> ret >= 0 }
        }
    }
}
