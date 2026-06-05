plugins {
    java
    `maven-publish`
}

// This module's sources live in the java.* packages (JBMC models for JDK types),
// so they're compiled by patching java.base. The resulting classes are only ever
// read by JBMC from the analysis classpath — never loaded by a real JVM (the
// bootstrap loader always wins for java.*), so shipping them is safe.
//
// NOTE: --release is incompatible with --patch-module, so we don't set it here.
val javaSrc = layout.projectDirectory.dir("src/main/java").asFile.absolutePath

// 17 baseline so 17-targeting consumers (e.g. Kotlin 1.9) can resolve this.
// Use source/target (not --release, which is incompatible with --patch-module).
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    // The blocking j.u.c models (BlockingQueue.put/take, Semaphore.acquire, CountDownLatch.await)
    // prune their would-block path with org.cprover.CProver.assume — the same primitive Bmc.assume
    // and the engine desugars use. JBMC recognises CProver by FQN and substitutes its assume
    // semantics; the body never runs. compileOnly: needed only to compile against, never shipped on
    // this artifact (the proof plugin already puts bmc-runtime, where CProver lives, on JBMC's
    // analysis classpath). The non-blocking model surface stays pure Java and JVM-runnable.
    compileOnly(project(":bmc-runtime"))
}

tasks.withType<JavaCompile>().configureEach {
    // --add-reads: the patched java.base must be allowed to read org.cprover.CProver, which sits in
    // the unnamed module (the compileOnly classpath entry above).
    options.compilerArgs.addAll(
        listOf("--patch-module", "java.base=$javaSrc", "--add-reads", "java.base=ALL-UNNAMED"),
    )
}

// javadoc categorically refuses to document sources in java.* packages ("package exists in
// another module: java.base"), and this module shadows them BY DESIGN — so the javadoc task
// can never run here. The published javadoc jar remains (Central requires the artifact to
// exist) but is empty by necessity; the real documentation is docs/coverage.md.
tasks.withType<Javadoc>().configureEach {
    enabled = false
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
