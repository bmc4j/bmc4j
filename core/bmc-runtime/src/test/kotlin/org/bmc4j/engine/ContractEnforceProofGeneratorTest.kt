package org.bmc4j.engine

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class ContractEnforceProofGeneratorTest {

    @Test
    fun calls_the_real_target_and_checks_predicates_on_the_predicate_owner() {
        // The proof calls the TARGET's real method but resolves predicates on the contract type.
        val src = ContractEnforceProofGenerator.generate("pkg", "MathEnforce", listOf(
                ContractStubGenerator.Contract("pkg.MathImpl", "pkg.MathContract", "isqrt", "int",
                        listOf(p("int", "n")), "nonNegative", "resultNonNegative")))

        assertTrue(src.startsWith("package pkg;\n"))
        assertTrue(src.contains("public class MathEnforce {"))
        assertTrue(src.contains("@org.bmc4j.BmcProof"))
        assertTrue(src.contains("public void enforce__isqrt()"))
        // order: nondet args -> assume(requires) -> call REAL target -> check(ensures)
        val nd = src.indexOf("int a0 = org.cprover.CProver.nondetInt();")
        val req = src.indexOf("Bmc.assume(pkg.MathContract.nonNegative(a0));")
        val call = src.indexOf("int result = pkg.MathImpl.isqrt(a0);")
        val ens = src.indexOf("Bmc.check(pkg.MathContract.resultNonNegative(result, a0));")
        assertTrue(nd > 0 && req > nd && call > req && ens > call, "enforce body order")
    }

    @Test
    fun ensures_only_contract_omits_the_requires_assume() {
        val src = ContractEnforceProofGenerator.generate("", "C", listOf(
                ContractStubGenerator.Contract("Foo", "Foo", "f", "long",
                        listOf(), null, "post")))
        assertFalse(src.contains("Bmc.assume("))
        assertTrue(src.contains("long result = Foo.f();"))
        assertTrue(src.contains("Bmc.check(Foo.post(result));")) // no params -> just result
    }

    @Test
    fun threads_multiple_params_by_generated_names() {
        val src = ContractEnforceProofGenerator.generate("p", "C", listOf(
                ContractStubGenerator.Contract("p.K", "p.K", "add", "int",
                        listOf(p("int", "a"), p("int", "b")), "pre", "post")))
        assertTrue(src.contains("int a0 = "))
        assertTrue(src.contains("int a1 = "))
        assertTrue(src.contains("Bmc.assume(p.K.pre(a0, a1));"))
        assertTrue(src.contains("int result = p.K.add(a0, a1);"))
        assertTrue(src.contains("Bmc.check(p.K.post(result, a0, a1));"))
    }

    companion object {
        private fun p(type: String, name: String): Map.Entry<String, String> =
                java.util.Map.entry(type, name)
    }
}
