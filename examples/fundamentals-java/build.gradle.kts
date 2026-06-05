// Fundamentals (Java) — the core bounded-model-checking concepts, one package per
// concept. This is the whole setup a real project needs: the bmc plugin on top of `java`.
plugins {
    java
    id("org.bmc4j")
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(25)) }
}
