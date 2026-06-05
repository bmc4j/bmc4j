package org.bmc4j.engine;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ContractStubGeneratorTest {

    private static Map.Entry<String, String> p(String type, String name) {
        return Map.entry(type, name);
    }

    @Test
    void generates_assert_requires_nondet_assume_ensures_return_using_the_predicate_owner() {
        // Predicates resolve against the predicate owner (the contract type), not the target.
        String src = ContractStubGenerator.generate("pkg", "MathStubs", List.of(
                new ContractStubGenerator.Contract("pkg.MathImpl", "pkg.MathContract", "isqrt", "int",
                        List.of(p("int", "n")), "nonNegative", "resultNonNegative")));

        assertTrue(src.startsWith("package pkg;\n"));
        assertTrue(src.contains("public static int isqrt__stub(int n) {"));
        int req = src.indexOf("Bmc.check(pkg.MathContract.nonNegative(n));");
        int nd = src.indexOf("int r = org.cprover.CProver.nondetInt();");
        int ens = src.indexOf("Bmc.assume(pkg.MathContract.resultNonNegative(r, n));");
        int ret = src.indexOf("return r;");
        assertTrue(req > 0 && nd > req && ens > nd && ret > ens, "stub body order");
    }

    @Test
    void ensures_only_contract_omits_the_requires_assert() {
        String src = ContractStubGenerator.generate("", "C", List.of(
                new ContractStubGenerator.Contract("Foo", "Foo", "f", "long",
                        List.of(), null, "post")));
        assertFalse(src.contains("Bmc.check("));
        assertTrue(src.contains("long r = org.cprover.CProver.nondetLong();"));
        assertTrue(src.contains("Bmc.assume(Foo.post(r));"));   // no params -> just r
        assertFalse(src.contains("package "));                  // empty package
    }

    @Test
    void picks_the_right_nondet_per_primitive_and_objects() {
        assertTrue(stub("boolean").contains("CProver.nondetBoolean()"));
        assertTrue(stub("double").contains("CProver.nondetDouble()"));
        assertTrue(stub("java.lang.String").contains("(java.lang.String) org.cprover.CProver.nondetWithoutNull()"));
    }

    @Test
    void threads_multiple_params_into_both_predicates() {
        String src = ContractStubGenerator.generate("p", "C", List.of(
                new ContractStubGenerator.Contract("p.K", "p.K", "add", "int",
                        List.of(p("int", "a"), p("int", "b")), "pre", "post")));
        assertTrue(src.contains("add__stub(int a, int b)"));
        assertTrue(src.contains("pre(a, b)"));
        assertTrue(src.contains("post(r, a, b)"));
    }

    private static String stub(String returnType) {
        return ContractStubGenerator.generate("", "C", List.of(
                new ContractStubGenerator.Contract("F", "F", "f", returnType, List.of(), null, "post")));
    }
}
