// Clean Kotlin / kotlinx-coroutines models for JBMC's ANALYSIS classpath only.
//
// These classes carry the SAME fully-qualified names as real Kotlin runtime classes
// (kotlin.jvm.internal.Intrinsics, kotlinx.coroutines.*). They must therefore NEVER
// reach a real runtime classpath — there they would shadow the actual stdlib and break
// every test. So this module is deliberately:
//
//   - NOT a normal dependency of bmc-runtime (that would leak onto consumers' classpath),
//   - NOT published (nobody resolves it standalone).
//
// Instead, bmc-runtime consumes this module's compiled classes as inert RESOURCES,
// bundles them into its jar, and extracts them only onto JBMC's analysis classpath at
// verification time (see BundledKotlinModels). The real Kotlin types these models
// extend/implement are needed only to compile, hence `compileOnly`.
plugins {
    java
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}

dependencies {
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib:1.9.0")
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
}
