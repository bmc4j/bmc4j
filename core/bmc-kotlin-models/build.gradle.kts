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
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.commons.Remapper

buildscript {
    repositories { mavenCentral() }
    dependencies { classpath("org.ow2.asm:asm-commons:9.7") }
}

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

// kotlin.time.Duration is a @JvmInline value class: its erased JVM ABI names members whose signatures
// mention the value type with kotlinc's name mangling (e.g. plus-LRDsOJo, getInWholeSeconds-impl). Java
// identifiers can't contain '-', so the model is authored with legal placeholder names and the compiled
// kotlin/time/Duration.class is rewritten here to the exact dashed ABI names the consumer bytecode calls
// (invokestatic ...Duration."plus-LRDsOJo":(JJ)J). The map is keyed by (placeholderName + descriptor); the
// rename also fixes the class's own internal invokestatic call sites (e.g. minus -> plus/unaryMinus).
val durationAbiRenames: Map<String, String> = mapOf(
    "plus(JJ)J" to "plus-LRDsOJo",
    "minus(JJ)J" to "minus-LRDsOJo",
    "compareTo(JJ)I" to "compareTo-LRDsOJo",
    "unaryMinus(J)J" to "unaryMinus-UwyO8pc",
    "getAbsoluteValue(J)J" to "getAbsoluteValue-UwyO8pc",
    "isNegative(J)Z" to "isNegative-impl",
    "isPositive(J)Z" to "isPositive-impl",
    "isInfinite(J)Z" to "isInfinite-impl",
    "isFinite(J)Z" to "isFinite-impl",
    "toLong(JLkotlin/time/DurationUnit;)J" to "toLong-impl",
    "getInWholeDays(J)J" to "getInWholeDays-impl",
    "getInWholeHours(J)J" to "getInWholeHours-impl",
    "getInWholeMinutes(J)J" to "getInWholeMinutes-impl",
    "getInWholeSeconds(J)J" to "getInWholeSeconds-impl",
    "getInWholeMilliseconds(J)J" to "getInWholeMilliseconds-impl",
    "getInWholeMicroseconds(J)J" to "getInWholeMicroseconds-impl",
    "getInWholeNanoseconds(J)J" to "getInWholeNanoseconds-impl",
    "equals0(JJ)Z" to "equals-impl0",
    "hashCode(J)I" to "hashCode-impl",
)

val renameDurationAbi by tasks.registering {
    description = "Rename kotlin.time.Duration value-class members to their mangled JVM ABI names."
    val classesDir = tasks.named<JavaCompile>("compileJava").flatMap { it.destinationDirectory }
    inputs.property("renames", durationAbiRenames)
    outputs.dir(classesDir)
    doLast {
        val durationOwner = "kotlin/time/Duration"
        val target = classesDir.get().asFile.resolve("kotlin/time/Duration.class")
        if (!target.exists()) {
            throw GradleException("kotlin/time/Duration.class not found for ABI rename: $target")
        }
        val remapper = object : Remapper() {
            override fun mapMethodName(owner: String, name: String, descriptor: String): String {
                if (owner == durationOwner) {
                    durationAbiRenames[name + descriptor]?.let { return it }
                }
                return name
            }
        }
        val cr = ClassReader(target.readBytes())
        val cw = ClassWriter(0)
        cr.accept(ClassRemapper(cw, remapper), 0)
        target.writeBytes(cw.toByteArray())
    }
}

tasks.named("classes") { dependsOn(renameDurationAbi) }
