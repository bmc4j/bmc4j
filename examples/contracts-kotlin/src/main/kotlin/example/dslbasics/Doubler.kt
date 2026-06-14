package example.dslbasics

/**
 * A pure instance method target for the contracts DSL example. [twice] returns the argument doubled - a
 * pure function of its argument (the receiver carries a `label` it never reads here). It has NO annotation
 * contract, so the DSL example's enforce-proof exercises the DSL path end-to-end and its `twice` call
 * sites are not redirected by any other contract.
 */
class Doubler(val label: String) {

    fun twice(amount: Int): Int = amount * 2
}
