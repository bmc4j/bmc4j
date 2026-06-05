// Standard-library modeling — JDK types JBMC would otherwise stub to nondet, replaced with
// clean bounded models on the analysis classpath: String operations, List/Map/Set/Optional,
// exact-decimal BigDecimal, and java.time as epoch primitives. One package per concept.
plugins {
    java
    id("org.bmc4j")
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(25)) }
}
