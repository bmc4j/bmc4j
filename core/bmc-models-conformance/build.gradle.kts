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

// Pull ONLY the audit source-set classes from bmc-models (the org.bmc4j.models.audit.* annotations
// AND the FpTotalOrder helper), NOT its java.* model classes — those are relocation-input only. The
// differential float/double total-order test compares FpTotalOrder.compare against the real JDK.
val auditClasses by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
}

dependencies {
    modelsToRelocate(project(":bmc-models"))
    // Kotlin models are also relocated — not for differential loading (their facades just delegate to
    // the java models, validated separately by model-conformance-proofs), but so the coverage gate
    // can enumerate every model class from one place and fail the build if a new one lacks a suite.
    modelsToRelocate(project(":bmc-kotlin-models"))
    auditClasses(project(path = ":bmc-models", configuration = "auditAnnotations"))
    testImplementation(files(auditClasses))
    testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
    testImplementation("io.kotest:kotest-property:5.9.1")
    // The model auditing gate reads audit annotations + method tables off the relocated model bytecode.
    testImplementation("org.ow2.asm:asm-tree:9.7")
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
    // Forward the docs-regeneration flag to the test JVM (ModelCoverageDocsTest rewrites
    // docs/model-coverage.md when set). Off by default: the test then just stale-checks.
    providers.systemProperty("bmc.regenerateDocs").orNull?.let { systemProperty("bmc.regenerateDocs", it) }
}

fun relocateModelJar(inputs: Set<File>, output: File) {
    val prefix = "bmcref/"
    // The model classes that get relocated for side-by-side loading: java.*, kotlin.*, kotlinx.*.
    // The org.bmc4j.models.audit.* annotation classes ride in the model jar but are NOT models — they
    // must stay UN-relocated so the annotation FQNs the gate reads off the model bytecode remain
    // `org/bmc4j/models/audit/...` (relocating them would both break the gate's descriptor matching and
    // contradict the requirement that annotation FQNs survive relocation). We neither own nor re-emit
    // them here; the gate reads the descriptor strings, not the loaded annotation classes.
    // java.util.function is DELIBERATELY excluded: those functional interfaces are SHARED between the
    // real-JDK collection models and the lambdas a differential test passes them, so relocating them to
    // bmcref.* would break passing a real java.util.function.Predicate to a model's removeIf (and every
    // other functional-arg method). The function models exist only to give JBMC sound default-method
    // bodies on the PROOF analysis classpath (the un-relocated bmc-models jar) — they are validated by
    // model proofs (proofs.function), never the differential axis, so they never load on the test JVM
    // and need no relocated twin. (Streams/Collectors stay relocatable: they're concrete classes the
    // differential gate enumerates but never JVM-runs, and they only USE function interfaces.)
    fun isModel(internalName: String) =
        (internalName.startsWith("java/") || internalName.startsWith("kotlin/") || internalName.startsWith("kotlinx/")) &&
            !internalName.startsWith("java/util/function/")
    // 1) Collect every owned internal class name across the input jars (models only).
    val owned = mutableSetOf<String>()
    for (f in inputs) {
        JarFile(f).use { jar ->
            jar.entries().asSequence()
                .filter { it.name.endsWith(".class") }
                .map { it.name.removeSuffix(".class") }
                .filter { isModel(it) }
                .forEach { owned.add(it) }
        }
    }
    // 2) Map owned names to bmcref.*; SimpleRemapper leaves everything else (incl. org.bmc4j.*) untouched.
    val remapper = SimpleRemapper(owned.associateWith { prefix + it })
    output.parentFile.mkdirs()
    val written = mutableSetOf<String>()
    JarOutputStream(output.outputStream()).use { out ->
        for (f in inputs) {
            JarFile(f).use { jar ->
                jar.entries().asSequence()
                    .filter { it.name.endsWith(".class") }
                    .filter { isModel(it.name.removeSuffix(".class")) } // skip non-model classes (audit annotations)
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
