package org.bmc4j.engine;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ContractEnforceProofGeneratorTest {

    private static Map.Entry<String, String> p(String type, String name) {
        return Map.entry(type, name);
    }

    @Test
    void calls_the_real_target_and_checks_predicates_on_the_predicate_owner() {
        // The proof calls the TARGET's real method but resolves predicates on the contract type.
        String src = ContractEnforceProofGenerator.generate("pkg", "MathEnforce", List.of(
                new ContractStubGenerator.Contract("pkg.MathImpl", "pkg.MathContract", "isqrt", "int",
                        List.of(p("int", "n")), "nonNegative", "resultNonNegative")));

        assertTrue(src.startsWith("package pkg;\n"));
        assertTrue(src.contains("public class MathEnforce {"));
        assertTrue(src.contains("@org.bmc4j.BmcProof"));
        assertTrue(src.contains("public void enforce__isqrt()"));
        // order: nondet args -> assume(requires) -> call REAL target -> check(ensures)
        int nd = src.indexOf("int a0 = org.cprover.CProver.nondetInt();");
        int req = src.indexOf("Bmc.assume(pkg.MathContract.nonNegative(a0));");
        int call = src.indexOf("int result = pkg.MathImpl.isqrt(a0);");
        int ens = src.indexOf("Bmc.check(pkg.MathContract.resultNonNegative(result, a0));");
        assertTrue(nd > 0 && req > nd && call > req && ens > call, "enforce body order");
    }

    @Test
    void ensures_only_contract_omits_the_requires_assume() {
        String src = ContractEnforceProofGenerator.generate("", "C", List.of(
                new ContractStubGenerator.Contract("Foo", "Foo", "f", "long",
                        List.of(), null, "post")));
        assertFalse(src.contains("Bmc.assume("));
        assertTrue(src.contains("long result = Foo.f();"));
        assertTrue(src.contains("Bmc.check(Foo.post(result));")); // no params -> just result
    }

    @Test
    void threads_multiple_params_by_generated_names() {
        String src = ContractEnforceProofGenerator.generate("p", "C", List.of(
                new ContractStubGenerator.Contract("p.K", "p.K", "add", "int",
                        List.of(p("int", "a"), p("int", "b")), "pre", "post")));
        assertTrue(src.contains("int a0 = "));
        assertTrue(src.contains("int a1 = "));
        assertTrue(src.contains("Bmc.assume(p.K.pre(a0, a1));"));
        assertTrue(src.contains("int result = p.K.add(a0, a1);"));
        assertTrue(src.contains("Bmc.check(p.K.post(result, a0, a1));"));
    }
}
