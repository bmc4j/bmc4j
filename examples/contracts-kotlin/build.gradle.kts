// Contracts (Kotlin) — method contracts (@Requires/@Ensures) declared test-side from KOTLIN, with
// the contracts processor wired via KSP by the org.bmc4j plugin (a native KSP SymbolProcessor,
// replacing the deprecated kapt). One package per concept (basics, instance, defaults, soundness,
// purity, suspendcontracts). The low per-proof bounds that make the "contracts beat inlining" point are set with
// @BmcProof(unwind = …); the auto-generated enforce proofs discharge each @Ensures against the real
// Kotlin body. The Kotlin counterpart of examples/contracts.
plugins {
    kotlin("jvm") // version from the root settings pluginManagement (-PbmcKotlinVersion overrides)
    id("org.bmc4j") // applies KSP + wires kspTest(bmc-contracts) + javaParameters automatically
}

dependencies {
    // The `suspendcontracts` concept contracts `suspend` functions: production code is `suspend`
    // (kotlinx-coroutines on the main classpath) and the caller proofs drive them through
    // `runBlocking { }` (test classpath). JBMC analyses clean bundled models of the coroutine runtime
    // (no real dispatcher/event loop), exactly like `examples/kotlin-coroutines-and-lincheck`.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
}

// The CONSUMER-side compile target. Default 25; the Kotlin-version CI matrix passes
// -PbmcJvmTarget=21 alongside -PbmcKotlinVersion for KGPs without a JVM_25 target.
val bmcJvmTarget = providers.gradleProperty("bmcJvmTarget").orNull ?: "25"

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(bmcJvmTarget.toInt())) }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(bmcJvmTarget)) }
}

tasks.withType<Test>().configureEach {
    // The `purity` concept ships a contract on an INTENTIONALLY impure Kotlin instance method
    // (contracts.purity.LedgerContract on example.purity.Ledger.record, which mutates a pre-existing
    // field of `this`). bmc4j's purity audit rejects it at proof time with a ContractPurityError — an
    // UNCONDITIONAL build failure (an impure method is not a legal contract target; no @ExpectEnforce
    // can bless it). The contract's auto-generated enforce-proof would therefore fail by design, so it
    // is excluded here; proofs.purity.PurityAuditDemoTest documents the rejection deterministically
    // instead. (Removing this exclusion is itself a regression check: the build then goes red with the
    // audit's message naming the PUTFIELD on the receiver.)
    filter {
        excludeTestsMatching("contracts.purity.LedgerContract__BmcEnforce")
        // The `suspendcontracts` concept ALSO ships an intentionally-impure contract — on the suspend
        // method example.suspendcontracts.Accumulator.add, which mutates a pre-existing field of `this`
        // underneath the coroutine plumbing. The purity audit's coroutine allowance list lets the
        // state-machine plumbing through but still rejects the receiver mutation, so this contract's
        // enforce-proof is rejected at proof time with a ContractPurityError — excluded here, with
        // proofs.suspendcontracts.SuspendPurityAuditDemoTest documenting it deterministically.
        excludeTestsMatching("contracts.suspendcontracts.AccumulatorContract__BmcEnforce")
    }
}
