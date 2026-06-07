// Contracts — method contracts (@Requires/@Ensures) and the soundness guard, one package
// per concept (basics, recursion, stacking, soundness). The low per-proof bounds that make
// the "contracts beat inlining" point are set with @BmcProof(unwind = …) on each proof; the
// auto-generated enforce proofs summarize self-recursion via the contract, so they discharge
// at the default bound regardless.
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
    // The `purity` concept ships a contract on an INTENTIONALLY impure method
    // (contracts.purity.LedgerContract on example.purity.Ledger.record, which mutates a static).
    // bmc4j's purity audit rejects it at proof time with a ContractPurityError — an UNCONDITIONAL
    // build failure (an impure method is not a legal contract target; no @ExpectEnforce can bless
    // it). The contract's auto-generated enforce-proof would therefore fail by design, so it is
    // excluded here; proofs.purity.PurityAuditDemoTest documents the rejection deterministically
    // instead. (Removing this exclusion is itself a regression check: the build then goes red with
    // the audit's message naming the PUTSTATIC Ledger.total instruction.)
    filter {
        excludeTestsMatching("contracts.purity.LedgerContract__BmcEnforce")
    }
}
