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

    @Test
    fun instance_contract_nondets_a_receiver_calls_it_and_threads_self_into_predicates() {
        // The enforce-proof for a pure instance contract: nondet a `self`, assume requires(self, args),
        // call self.method(args) — the REAL body — and check ensures(result, self, args).
        val src = ContractEnforceProofGenerator.generate("acct", "AccountEnforce", listOf(
                ContractStubGenerator.Contract("acct.Account", "acct.AccountContract", "project", "int",
                        listOf(p("int", "amount")), "validAmount", "balanceBounded",
                        "VERIFIED", "acct.Account")))
        assertTrue(src.contains("public void enforce__project()"))
        val self = src.indexOf("acct.Account self = (acct.Account) org.cprover.CProver.nondetWithoutNull();")
        val arg = src.indexOf("int a0 = org.cprover.CProver.nondetInt();")
        val req = src.indexOf("Bmc.assume(acct.AccountContract.validAmount(self, a0));")
        val call = src.indexOf("int result = self.project(a0);")
        val ens = src.indexOf("Bmc.check(acct.AccountContract.balanceBounded(result, self, a0));")
        assertTrue(self in 0 until arg && arg < req && req < call && call < ens,
                "instance enforce body order (self -> args -> assume -> real call -> check):\n$src")
    }

    @Test
    fun instance_contract_with_no_params_still_threads_the_receiver() {
        val src = ContractEnforceProofGenerator.generate("p", "C", listOf(
                ContractStubGenerator.Contract("p.Box", "p.BoxContract", "size", "int",
                        listOf(), "open", "nonNeg", "VERIFIED", "p.Box")))
        assertTrue(src.contains("p.Box self = (p.Box) org.cprover.CProver.nondetWithoutNull();"))
        assertTrue(src.contains("Bmc.assume(p.BoxContract.open(self));"))      // requires(self)
        assertTrue(src.contains("int result = self.size();"))
        assertTrue(src.contains("Bmc.check(p.BoxContract.nonNeg(result, self));")) // ensures(result, self)
    }

    @Test
    fun object_hosted_predicates_are_checked_on_the_singleton_instance() {
        // predicateOnObject = true -> the enforce-proof invokes the object's predicate members on the
        // singleton `<Owner>.INSTANCE`, while still calling the REAL target method directly.
        val src = ContractEnforceProofGenerator.generate("pkg", "MathEnforce", listOf(
                ContractStubGenerator.Contract("pkg.MathImpl", "pkg.MathContract", "isqrt", "int",
                        listOf(p("int", "n")), "nonNegative", "resultNonNegative",
                        "VERIFIED", null, null, true)))
        assertTrue(src.contains("Bmc.assume(pkg.MathContract.INSTANCE.nonNegative(a0));"),
                "an object-hosted requires predicate must be assumed on the singleton:\n$src")
        assertTrue(src.contains("int result = pkg.MathImpl.isqrt(a0);"),
                "the target method is still called directly (not on the predicate singleton):\n$src")
        assertTrue(src.contains("Bmc.check(pkg.MathContract.INSTANCE.resultNonNegative(result, a0));"),
                "an object-hosted ensures predicate must be checked on the singleton:\n$src")
    }

    companion object {
        private fun p(type: String, name: String): Map.Entry<String, String> =
                java.util.Map.entry(type, name)
    }
}
