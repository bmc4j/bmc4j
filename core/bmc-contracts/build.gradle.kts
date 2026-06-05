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
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
