plugins {
    `java-library`
    `maven-publish`
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 17
}

dependencies {
    // The @Ensures/@Requires annotations this processor reads, plus the ContractStubGenerator /
    // ContractEnforceProofGenerator / ContractManifest it renders and emits. Exposed so a
    // consumer's annotationProcessor classpath gets the generators transitively.
    api(project(":bmc-runtime"))
    // The processor test compiles small @BmcContractsFor sources in-process (ToolProvider
    // javac) and runs ContractProcessor over them, so the test JVM needs the @BmcContractsFor /
    // @Requires / @Ensures / @ExpectEnforce annotations on its classpath. (JUnit is added by the
    // root build's per-module Java setup.)
    testImplementation(project(":bmc-runtime"))
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
