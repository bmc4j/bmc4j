import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.commons.SimpleRemapper
import java.io.File
import java.util.jar.JarFile
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

buildscript {
    repositories { mavenCentral() }
    dependencies { classpath("org.ow2.asm:asm-commons:9.7") }
}

plugins {
    // Matches bmc-kotlin's pin: one KGP version per build (mixing versions in a single
    // Gradle build is unsupported), and bmc-kotlin is held at 2.3 for its 1.9 floor.
    kotlin("jvm") version "2.3.21"
}

repositories { mavenCentral() }

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

// bmc-models' compiled classes, pulled in ONLY as relocation input — never on our compile
// classpath, since they live in java.* and a real JVM refuses to load user classes there.
val modelsToRelocate by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
}

dependencies {
    modelsToRelocate(project(":bmc-models"))
    // Kotlin models are also relocated — not for differential loading (their facades just delegate to
    // the java models, validated separately by model-conformance-proofs), but so the coverage gate
    // can enumerate every model class from one place and fail the build if a new one lacks a suite.
    modelsToRelocate(project(":bmc-kotlin-models"))
    testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
    testImplementation("io.kotest:kotest-property:5.9.1")
}

val relocatedJar = layout.buildDirectory.file("relocated/bmcref-models.jar")

// Relocate every java.* model type to bmcref.java.* so the model and the real JDK class can be
// loaded side by side on a real JVM and compared (differential conformance). Only the
// model's OWN types are remapped — references to unmodeled types (Object, String, exceptions) keep
// pointing at the real JDK.
val relocateModels by tasks.registering {
    description = "Relocate bmc-models java.* -> bmcref.java.* for differential conformance testing."
    inputs.files(modelsToRelocate)
    outputs.file(relocatedJar)
    doLast {
        relocateModelJar(modelsToRelocate.files, relocatedJar.get().asFile)
    }
}

dependencies {
    testImplementation(files(relocatedJar))
}

tasks.named("compileTestKotlin") { dependsOn(relocateModels) }
tasks.test {
    dependsOn(relocateModels)
    useJUnitPlatform()
}

fun relocateModelJar(inputs: Set<File>, output: File) {
    val prefix = "bmcref/"
    // 1) Collect every owned internal class name across the input jars.
    val owned = mutableSetOf<String>()
    for (f in inputs) {
        JarFile(f).use { jar ->
            jar.entries().asSequence()
                .filter { it.name.endsWith(".class") }
                .forEach { owned.add(it.name.removeSuffix(".class")) }
        }
    }
    // 2) Map owned names to bmcref.*; SimpleRemapper leaves everything else untouched.
    val remapper = SimpleRemapper(owned.associateWith { prefix + it })
    output.parentFile.mkdirs()
    val written = mutableSetOf<String>()
    JarOutputStream(output.outputStream()).use { out ->
        for (f in inputs) {
            JarFile(f).use { jar ->
                jar.entries().asSequence()
                    .filter { it.name.endsWith(".class") }
                    .forEach { e ->
                        val cr = ClassReader(jar.getInputStream(e).readBytes())
                        val cw = ClassWriter(0) // pure name remap; frames/maxs unchanged
                        cr.accept(ClassRemapper(cw, remapper), 0)
                        val newName = prefix + e.name
                        if (written.add(newName)) {
                            out.putNextEntry(JarEntry(newName))
                            out.write(cw.toByteArray())
                            out.closeEntry()
                        }
                    }
            }
        }
    }
}
