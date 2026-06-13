package example.exceptions

/**
 * The live-local counterpart of an elided exception message, in idiomatic Kotlin: a function that guards
 * its argument with a `throw IllegalArgumentException(<string template>)` and then, on the normal path,
 * carries a value through a LOCAL it returns for the caller to use.
 *
 * The string template compiles to a `makeConcatWithConstants` invokedynamic, so exception-message elision
 * rewrites this function; the returned `label` is genuinely LIVE. It pins that eliding the message leaves
 * the rest of the function - its live locals and their non-null tracking - untouched. The value carried is
 * a SYMBOLIC string (the caller passes `Bmc.anyAsciiString(...)`), whose non-null guarantee comes from the
 * engine's nondet modeling via the LocalVariableTable. A rewrite that drops a modified method's
 * LocalVariableTable wholesale made the returned `String` read back null, so a caller that reads
 * `label.length` FALSE-REFUTED with a NullPointerException over correct code.
 */
fun checkedLabel(s: String?, tag: Int): String {
    if (s == null) throw IllegalArgumentException("label must be non-null, tag=$tag")
    val label = s
    return label
}
