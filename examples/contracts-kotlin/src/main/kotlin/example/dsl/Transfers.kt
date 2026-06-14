package example.dsl

/**
 * A STATIC target for the contracts DSL (threads no `self` - the form is uniform with the instance
 * case). [clamp] is a pure function over its arguments and result; its DSL contract relates the result
 * to the inputs.
 */
object Transfers {

    /** Clamp [value] to at most [cap] (and never below zero). Pure: a function of its arguments only. */
    @JvmStatic
    fun clamp(value: Int, cap: Int): Int =
            if (value < 0) 0 else if (value > cap) cap else value
}
