plugins {
    `java-gradle-plugin`
    `maven-publish`
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 17
}

dependencies {
    testImplementation(gradleTestKit())
}

gradlePlugin {
    plugins {
        create("bmc") {
            id = "org.bmc4j"
            implementationClass = "org.bmc4j.gradle.BmcPlugin"
            displayName = "JVM BMC"
            description = "Bounded model checking for JVM tests, powered by JBMC. Auto-provisions the engine."
        }
    }
}
