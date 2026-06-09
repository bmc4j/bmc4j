package proofs.hashset

import org.bmc4j.Bmc
import org.bmc4j.BmcProof

/**
 * Model proofs (axis 2): the presizing factory java.util.HashSet.newHashSet(int) is a Java 19+
 * static method, so it only resolves on the Java 21+ floor; this proof lives in the jvm21+ source
 * set (see build.gradle.kts) while the rest of HashSetLaws compiles on every supported floor.
 */
class HashSetNewFactoryLaws {

    @BmcProof
    fun newHashSet_returns_an_empty_usable_set() {
        // The presizing hint is observably irrelevant to the bounded model: a fresh empty set that
        // behaves exactly like new HashSet().
        val s = java.util.HashSet.newHashSet<Int>(8)
        val x = Bmc.anyInt()
        Bmc.check(s.size == 0 && s.isEmpty())
        s.add(x)
        Bmc.check(s.contains(x) && s.size == 1)
    }
}
