// Assumed output-contracts — Bmc.assumeEvery / Bmc.assumeStable. A proof ASSUMES an external,
// unanalyzable dependency upholds an output property (no model, no annotation, no string method name)
// and proves on top of it. Each proof self-asserts its expected verdict via @BmcProof(expect = …):
// the assumption is load-bearing (drop it => REFUTED/UNKNOWN), an over-tight predicate => VACUOUS.
plugins {
    java
    id("org.bmc4j")
}

java {
    // Proof-leg JVM target: -PbmcJvmTarget=N (the CI proof matrix runs each leg with
    // host JDK == toolchain == N, so the bytecode fed to the engine matches the leg).
    toolchain { languageVersion.set(JavaLanguageVersion.of((providers.gradleProperty("bmcJvmTarget").orNull ?: "25").toInt())) }
}

tasks.withType<Test>().configureEach {
    // proofs.purity.ImpurePredicateDemo declares an assumeEvery predicate that reads a MUTABLE STATIC.
    // bmc4j's purity audit rejects it at proof time with a ContractPurityError — an UNCONDITIONAL build
    // failure (an impure predicate is not a legal assumed-contract; no expect() can bless it). It is
    // excluded here so the suite stays green; removing this exclusion is a regression check that the
    // audit still fires for assumed-contract predicates (the build then goes red naming the GETSTATIC).
    filter {
        excludeTestsMatching("proofs.purity.ImpurePredicateDemo")
    }
}
