// Fundamentals (Java) — the core bounded-model-checking concepts, one package per
// concept. This is the whole setup a real project needs: the bmc plugin on top of `java`.
plugins {
    java
    id("org.bmc4j")
}

java {
    // Proof-leg JVM target: -PbmcJvmTarget=N (the CI proof matrix runs each leg with
    // host JDK == toolchain == N, so the bytecode fed to the engine matches the leg).
    toolchain { languageVersion.set(JavaLanguageVersion.of((providers.gradleProperty("bmcJvmTarget").orNull ?: "25").toInt())) }
}

// Emit full debug info (incl. the LocalVariableTable) for the test proofs. @LoopInvariant binds a
// predicate's parameter names to the contracted loop's locals via the LVT, so the proofs that use it must
// carry local-variable names; javac omits the LVT without -g.
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-g")
}
