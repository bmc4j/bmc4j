package conformance

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import java.lang.reflect.InvocationTargetException

/**
 * Differential-conformance helpers. The real JDK class and the relocated model
 * (`bmcref.*`) are different types with the same method surface, so operations are applied
 * reflectively and compared uniformly: a model must produce the same observable as the JDK, or
 * fail with the same exception type — never silently diverge.
 */

val OBJECT: Class<*> = java.lang.Object::class.java
val INT: Class<*> = Int::class.javaPrimitiveType!!

/** Invoke a method by name on either implementation, unwrapping the reflection wrapper so the
 *  model's/JDK's real exception type is what surfaces. */
fun call(target: Any, method: String, argTypes: Array<Class<*>>, vararg args: Any?): Result<Any?> =
    runCatching {
        val m = publicMethod(target.javaClass, method, argTypes)
        try {
            m.invoke(target, *args)
        } catch (e: InvocationTargetException) {
            throw e.targetException ?: e
        }
    }

/**
 * Resolve a method via a PUBLIC declaring type. The JDK's collection views (keySet/values/entrySet)
 * are non-public classes (e.g. {@code HashMap$KeySet}); a Method whose declaring class is non-public
 * fails {@code invoke} with IllegalAccessException, and {@code setAccessible} is blocked by module
 * encapsulation on Java 17+. Resolving the same method from a public supertype/interface (e.g.
 * {@code java.util.Set.size()}) makes {@code invoke} pass the access check on any concrete instance.
 */
private fun publicMethod(cls: Class<*>, method: String, argTypes: Array<Class<*>>): java.lang.reflect.Method {
    // A method whose declaring class lives in an EXPORTED public type is invokable. A class can be
    // public yet sit in a non-exported module package (e.g. jdk.internal.util.NullableKeyValueHolder,
    // returned by LinkedHashMap.firstEntry) — invoke then fails IllegalAccessException. So prefer a
    // method resolved from a public, EXPORTED supertype/interface; only fall back to the class itself.
    fun exported(c: Class<*>): Boolean =
        java.lang.reflect.Modifier.isPublic(c.modifiers) && c.module.isExported(c.packageName)

    if (exported(cls)) {
        runCatching { return cls.getMethod(method, *argTypes) }
    }
    var c: Class<*>? = cls
    while (c != null) {
        for (itf in c.interfaces) {
            if (exported(itf)) runCatching { return itf.getMethod(method, *argTypes) }
        }
        val sup = c.superclass
        if (sup != null && exported(sup)) {
            runCatching { return sup.getMethod(method, *argTypes) }
        }
        c = sup
    }
    return cls.getMethod(method, *argTypes) // fallback: surface the original error at invoke
}

/** Invoke a static method by name on either implementation (used to pass nulls Kotlin would
 *  otherwise reject at a non-null parameter), unwrapping the reflection wrapper. */
fun staticCall(cls: Class<*>, method: String, argTypes: Array<Class<*>>, vararg args: Any?): Result<Any?> =
    runCatching {
        val m = cls.getMethod(method, *argTypes)
        try {
            m.invoke(null, *args)
        } catch (e: InvocationTargetException) {
            throw e.targetException ?: e
        }
    }

/**
 * Do the model's and real's exception outcomes conform? They match when:
 *  - both succeeded (no exception), or
 *  - they're the same type, modulo the `bmcref.` relocation prefix (a model that throws a *modeled*
 *    exception like NoSuchElementException throws the relocated twin on the JVM — the same one it
 *    throws under JBMC), or
 *  - the model throws a SUPERTYPE of what the JDK throws — e.g. the model throws the List.get
 *    contract's IndexOutOfBoundsException where a concrete JDK class throws its
 *    ArrayIndexOutOfBoundsException subtype. The model honoring the contract type is conforming.
 */
fun excMatches(real: Throwable?, model: Throwable?): Boolean {
    if (real == null && model == null) return true
    if (real == null || model == null) return false
    val realName = real.javaClass.name
    val modelName = model.javaClass.name.removePrefix("bmcref.")
    if (realName == modelName) return true
    return try {
        Class.forName(modelName).isAssignableFrom(real.javaClass) // model type >= real type
    } catch (e: Throwable) {
        false
    }
}

/** Same exception outcome (or both succeeded), per {@link #excMatches}. */
fun assertSameException(real: Result<*>, model: Result<*>) {
    val re = real.exceptionOrNull()
    val me = model.exceptionOrNull()
    withClue("exception: real=${re?.javaClass?.name}  model=${me?.javaClass?.name}") {
        excMatches(re, me) shouldBe true
    }
}

/**
 * Both outcomes must agree on a Map.Entry result (e.g. TreeMap firstEntry/lastEntry): a conforming
 * exception, both null (no qualifying entry), or both non-null with equal key AND value. The entry
 * types differ by the {@code bmcref.} relocation, so key/value are read reflectively via getKey/getValue.
 */
fun assertSameEntry(label: String, real: Result<Any?>, model: Result<Any?>) {
    val re = real.exceptionOrNull()
    val me = model.exceptionOrNull()
    withClue("$label  ->  real=${re?.javaClass?.name ?: real.getOrNull()}  model=${me?.javaClass?.name ?: model.getOrNull()}") {
        excMatches(re, me) shouldBe true
        if (re == null && me == null) {
            val rEntry = real.getOrNull()
            val mEntry = model.getOrNull()
            (mEntry == null) shouldBe (rEntry == null)
            if (rEntry != null && mEntry != null) {
                assertEquivalent("$label.key", call(rEntry, "getKey", arrayOf()), call(mEntry, "getKey", arrayOf()))
                assertEquivalent("$label.value", call(rEntry, "getValue", arrayOf()), call(mEntry, "getValue", arrayOf()))
            }
        }
    }
}

/** Both outcomes must agree: conforming exception (or both succeeded), and on success, equal value. */
fun assertEquivalent(label: String, real: Result<Any?>, model: Result<Any?>) {
    val re = real.exceptionOrNull()
    val me = model.exceptionOrNull()
    withClue("$label  ->  real=${re?.javaClass?.name ?: real.getOrNull()}  model=${me?.javaClass?.name ?: model.getOrNull()}") {
        excMatches(re, me) shouldBe true
        if (re == null && me == null) {
            model.getOrNull() shouldBe real.getOrNull()
        }
    }
}
