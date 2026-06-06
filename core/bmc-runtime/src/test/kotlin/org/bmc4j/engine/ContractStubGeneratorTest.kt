package org.bmc4j.engine

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class ContractStubGeneratorTest {

    @Test
    fun generates_assert_requires_nondet_assume_ensures_return_using_the_predicate_owner() {
        // Predicates resolve against the predicate owner (the contract type), not the target.
        val src = ContractStubGenerator.generate("pkg", "MathStubs", listOf(
                ContractStubGenerator.Contract("pkg.MathImpl", "pkg.MathContract", "isqrt", "int",
                        listOf(p("int", "n")), "nonNegative", "resultNonNegative")))

        assertTrue(src.startsWith("package pkg;\n"))
        assertTrue(src.contains("public static int isqrt__stub(int n) {"))
        val req = src.indexOf("Bmc.check(pkg.MathContract.nonNegative(n));")
        val nd = src.indexOf("int r = org.cprover.CProver.nondetInt();")
        val ens = src.indexOf("Bmc.assume(pkg.MathContract.resultNonNegative(r, n));")
        val ret = src.indexOf("return r;")
        assertTrue(req > 0 && nd > req && ens > nd && ret > ens, "stub body order")
    }

    @Test
    fun ensures_only_contract_omits_the_requires_assert() {
        val src = ContractStubGenerator.generate("", "C", listOf(
                ContractStubGenerator.Contract("Foo", "Foo", "f", "long",
                        listOf(), null, "post")))
        assertFalse(src.contains("Bmc.check("))
        assertTrue(src.contains("long r = org.cprover.CProver.nondetLong();"))
        assertTrue(src.contains("Bmc.assume(Foo.post(r));"))   // no params -> just r
        assertFalse(src.contains("package "))                  // empty package
    }

    @Test
    fun picks_the_right_nondet_per_primitive_and_objects() {
        assertTrue(stub("boolean").contains("CProver.nondetBoolean()"))
        assertTrue(stub("double").contains("CProver.nondetDouble()"))
        assertTrue(stub("java.lang.String").contains("(java.lang.String) org.cprover.CProver.nondetWithoutNull()"))
    }

    @Test
    fun threads_multiple_params_into_both_predicates() {
        val src = ContractStubGenerator.generate("p", "C", listOf(
                ContractStubGenerator.Contract("p.K", "p.K", "add", "int",
                        listOf(p("int", "a"), p("int", "b")), "pre", "post")))
        assertTrue(src.contains("add__stub(int a, int b)"))
        assertTrue(src.contains("pre(a, b)"))
        assertTrue(src.contains("post(r, a, b)"))
    }

    companion object {
        private fun p(type: String, name: String): Map.Entry<String, String> =
                java.util.Map.entry(type, name)

        private fun stub(returnType: String): String =
                ContractStubGenerator.generate("", "C", listOf(
                        ContractStubGenerator.Contract("F", "F", "f", returnType, listOf(), null, "post")))
    }
}
