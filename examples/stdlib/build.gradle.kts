// Standard-library modeling — JDK types JBMC would otherwise stub to nondet, replaced with
// clean bounded models on the analysis classpath: String operations, List/Map/Set/Optional,
// exact-decimal BigDecimal, and java.time as epoch primitives. One package per concept.
plugins {
    java
    id("org.bmc4j")
}

java {
    // Proof-leg JVM target: -PbmcJvmTarget=N (the CI proof matrix runs each leg with
    // host JDK == toolchain == N, so the bytecode fed to the engine matches the leg).
    toolchain { languageVersion.set(JavaLanguageVersion.of((providers.gradleProperty("bmcJvmTarget").orNull ?: "25").toInt())) }
}
