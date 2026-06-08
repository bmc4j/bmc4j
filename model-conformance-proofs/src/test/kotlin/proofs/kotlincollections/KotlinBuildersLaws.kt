package proofs.kotlincollections

import org.bmc4j.Bmc
import org.bmc4j.BmcProof

/**
 * Model proofs (axis 2) for the Kotlin `build*` builders — `buildList`/`buildSet`/`buildMap` (CollectionsKt/
 * SetsKt/MapsKt) and `buildString` (StringsKt). These are INLINE stdlib functions, so each call site below
 * inlines the builder body into the proof: `buildList { … }` reaches `createListBuilder()` + the desugared
 * builder lambda + `build(list)`, `buildString { … }` reaches `new StringBuilder()` + the lambda +
 * `toString()`, etc. — all over bmc4j's bounded java.util / StringBuilder models. A wrong (or nondet-stubbed)
 * builder/helper is caught: the laws assert the built collection's concrete size / membership / contents and
 * the built string's length / content. Inline facades have no relocatable JVM body to differential-test, so
 * these `@BmcProof` laws are the conformance coverage for the build* surface.
 */
class KotlinBuildersLaws {

    // ---- buildList { } ----

    @BmcProof
    fun buildList_size_and_contents() {
        val xs = buildList {
            add(1)
            add(2)
            add(3)
        }
        Bmc.check(xs.size == 3 && xs[0] == 1 && xs[1] == 2 && xs[2] == 3)
    }

    @BmcProof
    fun buildList_addAll_and_sum() {
        val xs = buildList {
            add(10)
            addAll(listOf(20, 30))
        }
        Bmc.check(xs.size == 3 && xs.sum() == 60)
    }

    /** Symbolic buildList law: the builder preserves the elements added, in order. */
    @BmcProof
    fun symbolic_buildList_preserves_elements() {
        val a = Bmc.anyInt(0, 100)
        val b = Bmc.anyInt(0, 100)
        val xs = buildList {
            add(a)
            add(b)
        }
        Bmc.check(xs.size == 2 && xs[0] == a && xs[1] == b)
    }

    @BmcProof
    fun buildList_with_capacity_hint() {
        val xs = buildList(4) {
            add(7)
            add(8)
        }
        Bmc.check(xs.size == 2 && xs[0] == 7 && xs[1] == 8)
    }

    // ---- buildSet { } ----

    @BmcProof
    fun buildSet_dedups_and_membership() {
        val s = buildSet {
            add(1)
            add(2)
            add(2)
            add(3)
        }
        Bmc.check(s.size == 3 && s.contains(2) && !s.contains(9))
    }

    @BmcProof
    fun buildSet_with_capacity_hint() {
        val s = buildSet(2) {
            add(5)
            add(6)
        }
        Bmc.check(s.size == 2 && s.contains(5) && s.contains(6))
    }

    // ---- buildMap { } ----

    @BmcProof
    fun buildMap_put_and_lookup() {
        val m = buildMap {
            put(1, 10)
            put(2, 20)
        }
        Bmc.check(m.size == 2 && m[1] == 10 && m[2] == 20)
    }

    /** Symbolic buildMap law: each distinct key recovers the value put under it. */
    @BmcProof
    fun symbolic_buildMap_lookup() {
        val a = Bmc.anyInt(0, 100)
        val b = Bmc.anyInt(101, 200)
        val m = buildMap {
            put(a, a + 1)
            put(b, b + 2)
        }
        Bmc.check(m.size == 2 && m[a] == a + 1 && m[b] == b + 2)
    }

    @BmcProof
    fun buildMap_with_capacity_hint() {
        val m = buildMap(2) {
            put(3, 30)
        }
        Bmc.check(m.size == 1 && m[3] == 30)
    }

    // ---- buildString { } ----

    @BmcProof
    fun buildString_append_chars_and_length() {
        val s = buildString {
            append("a")
            append("b")
            append("c")
        }
        Bmc.check(s.length == 3 && s == "abc")
    }

    @BmcProof
    fun buildString_append_int_and_char() {
        val s = buildString {
            append(4)
            append('x')
        }
        Bmc.check(s == "4x")
    }

    @BmcProof
    fun buildString_with_capacity_hint() {
        val s = buildString(8) {
            append("hi")
        }
        Bmc.check(s.length == 2 && s == "hi")
    }
}
