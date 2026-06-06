package example.lateinitprops

/**
 * `lateinit` — initialization promised, not proven by the type system. The compiler guards every
 * read with an initialized-check; under BMC an uninitialized read is a refutable defect, and the
 * `::prop.isInitialized` guard is a real branch the analysis follows.
 */
class Session {

    lateinit var user: String

    /** BUG: reads `user` on faith — before `start()`, this is an uninitialized access. */
    fun greetLength(): Int = user.length

    /** Fixed: guard with `isInitialized` (a real field-null check in the bytecode). */
    fun safeGreetLength(): Int = if (::user.isInitialized) user.length else 0

    fun start(name: String) {
        user = name
    }
}
