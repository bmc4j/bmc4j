// Language features (Java) — Java language constructs made analyzable. Lambdas & method
// references are desugared from their invokedynamic form in our own layer (no engine fork).
plugins {
    java
    id("org.bmc4j")
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(25)) }
}
