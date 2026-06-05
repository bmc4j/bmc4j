// Concurrency (Java) — @BmcProof(concurrent = true) makes JBMC explore thread interleavings,
// finding a read-after-write race that a lock (or a Latch barrier) closes.
plugins {
    java
    id("org.bmc4j")
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(25)) }
}
