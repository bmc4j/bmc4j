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
    toolchain { languageVersion.set(JavaLanguageVersion.of(25)) }
}
