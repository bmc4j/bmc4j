plugins {
    `java-library`
    `maven-publish`
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 17
}

dependencies {
    // Exposed so a consumer's annotationProcessor classpath gets the generator too.
    api(project(":bmc-constraints"))
    // The Jakarta Bean Validation annotations the processor reads.
    implementation("jakarta.validation:jakarta.validation-api:3.0.2")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
