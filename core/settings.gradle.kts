// The product build: runtime + plugin + engine. Published standalone, and
// consumed by the repo-root examples build via includeBuild (no mavenLocal needed).
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

// Auto-provision JDK toolchains. The published core modules target Java 17 via
// options.release (not a toolchain), but this is wired here too so the core
// build can resolve/download any toolchain it might request when run standalone.
// 1.0.0 is the first Foojay resolver release compatible with Gradle 9.
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "bmc4j-core"

include(
    "bmc-runtime",
    "bmc-gradle-plugin",
    "bmc-engine-windows-x64",
    "bmc-engine-linux-x64",
    "bmc-engine-linux-x64-musl",
    "bmc-engine-linux-arm64",
    "bmc-engine-macos-x64",
    "bmc-engine-macos-arm64",
    "bmc-constraints",
    "bmc-constraints-jakarta",
    "bmc-contracts",
    "bmc-models",
    "bmc-string-model",
    "bmc-kotlin-models",
    "bmc-kotlin",
    "bmc-models-conformance",
)
