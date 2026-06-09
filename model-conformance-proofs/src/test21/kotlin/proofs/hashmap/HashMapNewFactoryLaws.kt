package proofs.hashmap

import org.bmc4j.Bmc
import org.bmc4j.BmcProof

/**
 * Model proofs (axis 2): the presizing factory java.util.HashMap.newHashMap(int) is a Java 19+
 * static method, so it only resolves on the Java 21+ floor; this proof lives in the jvm21+ source
 * set (see build.gradle.kts) while the rest of HashMapLaws compiles on every supported floor.
 */
class HashMapNewFactoryLaws {

    @BmcProof
    fun newHashMap_returns_an_empty_usable_map() {
        // The presizing hint is observably irrelevant to the bounded model: a fresh empty map that
        // behaves exactly like new HashMap().
        val m = java.util.HashMap.newHashMap<Int, Int>(8)
        val k = Bmc.anyInt()
        val v = Bmc.anyInt()
        Bmc.check(m.size == 0 && m.isEmpty())
        m[k] = v
        Bmc.check(m[k] == v && m.size == 1)
    }
}
