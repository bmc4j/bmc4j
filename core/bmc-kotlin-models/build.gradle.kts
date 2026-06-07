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
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.commons.Remapper
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode

buildscript {
    repositories { mavenCentral() }
    dependencies {
        classpath("org.ow2.asm:asm-commons:9.7")
        classpath("org.ow2.asm:asm-tree:9.7")
    }
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

// Pull ONLY the audit annotation classes from bmc-models (not its java.* model classes), so these
// kotlin.* / kotlinx.* models can carry the same @BmcModelConforms / @BmcNotModelled / @BmcNotNeeded
// audit annotations. The annotations are CLASS-retention, so compileOnly is right.
val auditAnnotations by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
}

// The not-needed/not-modeled loud stubs (hand-written method-level @BmcNotNeeded) route their bodies
// through org.bmc4j.analysis.BmcUnmodelledReached.fail(...) — exactly as the JDK models in bmc-models
// do — so a reach demotes to a member-named UNKNOWN. That sentinel lives in bmc-runtime, but
// bmc-runtime already depends on THIS module (it bundles the compiled kotlin models as resources), so
// a project dependency back on bmc-runtime would be a cycle. We can't compile against bmc-runtime's
// copy; instead a tiny compile-only source-compatible stub of the sentinel sits in its own source set,
// compiled but NEVER bundled into the model jar (the jar packs only `main`). At verification time JBMC
// uses bmc-runtime's REAL BmcUnmodelledReached from the analysis classpath — this stub is invisible.
val sentinelApi by sourceSets.creating {
    java.srcDir("src/sentinel-api/java")
}

dependencies {
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib:1.9.0")
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    auditAnnotations(project(path = ":bmc-models", configuration = "auditAnnotations"))
    compileOnly(files(auditAnnotations))
    compileOnly(sentinelApi.output)
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
    // Int-scalar times/div (the Double overloads stay JBMC nondet stubs — no-double policy). Both
    // value-class-returning ops share the -UwyO8pc value-type mangle suffix.
    "times(JI)J" to "times-UwyO8pc",
    "div(JI)J" to "div-UwyO8pc",
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
    // outputs.dir alone creates NO task dependency — without the explicit dependsOn this raced
    // compileJava on cold parallel builds (surfaced by the arm64 smoke once the audit tasks
    // reshuffled the schedule) and failed with "Duration.class not found".
    dependsOn(tasks.named("compileJava"))
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

// ---------------------------------------------------------------------------------------------
// Loud-body synthesis, mirroring bmc-models: synthesize a message-free sentinel-routing body for
// every class-level @BmcNotNeeded(member=) member these models declare but do not implement, so a
// proof reaching an unmodeled member demotes to a member-named UNKNOWN under JBMC instead of silently
// havocking. (@BmcNotModelled is method-only — its waivers are hand-written stubs, not synthesized.
// No kotlin model uses these annotations today, so this pass currently synthesizes nothing — it
// exists so the shape is correct and cheap the moment one does.)
val synthesizeLoudBodies by tasks.registering {
    description = "Synthesize loud-failing bodies for class-level @BmcNotNeeded(member=) members."
    val classesDir = tasks.named<JavaCompile>("compileJava").flatMap { it.destinationDirectory }
    inputs.dir(classesDir)
    outputs.dir(classesDir)
    mustRunAfter(renameDurationAbi)
    doLast {
        synthesizeLoudUnmodelledBodies(classesDir.get().asFile)
    }
}
tasks.named("classes") { dependsOn(synthesizeLoudBodies) }

fun synthesizeLoudUnmodelledBodies(classesDir: File) {
    // Class-level (member=) declarations are @BmcNotNeeded only — @BmcNotModelled is method-only (no
    // TYPE target), so its waivers are hand-written method-level stubs, never synthesized from a
    // class-level declaration.
    val notNeededDesc = "Lorg/bmc4j/models/audit/BmcNotNeeded;"
    val notNeededListDesc = "Lorg/bmc4j/models/audit/BmcNotNeededList;"

    classesDir.walkTopDown().filter { it.isFile && it.extension == "class" }.forEach { classFile ->
        val node = ClassNode()
        ClassReader(classFile.readBytes()).accept(node, 0)

        data class Decl(val member: String, val reason: String)
        val decls = mutableListOf<Decl>()
        fun readDecl(values: List<Any?>?): Decl? {
            if (values == null) return null
            var member: String? = null
            var reason: String? = null
            var i = 0
            while (i + 1 < values.size) {
                val k = values[i] as? String
                val v = values[i + 1]
                if (k == "member") member = v as? String
                if (k == "reason") reason = v as? String
                i += 2
            }
            return if (member != null && reason != null) Decl(member, reason) else null
        }
        for (ann in (node.invisibleAnnotations ?: emptyList())) {
            when (ann.desc) {
                notNeededDesc -> readDecl(ann.values)?.let { decls.add(it) }
                notNeededListDesc -> {
                    val vals = ann.values ?: continue
                    var j = 0
                    while (j + 1 < vals.size) {
                        if (vals[j] == "value") {
                            @Suppress("UNCHECKED_CAST")
                            val list = vals[j + 1] as? List<org.objectweb.asm.tree.AnnotationNode>
                            list?.forEach { inner -> readDecl(inner.values)?.let { decls.add(it) } }
                        }
                        j += 2
                    }
                }
            }
        }
        if (decls.isEmpty()) return@forEach

        var changed = false
        for (decl in decls) {
            val parsed = parseMemberSignature(decl.member) ?: continue
            val (name, paramTypes) = parsed
            val desc = Type.getMethodDescriptor(Type.VOID_TYPE, *paramTypes.toTypedArray())
            if (node.methods.any { it.name == name && it.desc == desc }) continue
            if (node.methods.any { it.name == name && Type.getArgumentTypes(it.desc).toList() == paramTypes }) continue

            val access = Opcodes.ACC_PUBLIC
            val mn = MethodNode(access, name, desc, null, null)
            mn.visitAnnotation("Lorg/bmc4j/models/audit/BmcSynthesizedLoud;", false)
            val iv = mn.instructions
            // Route through the BmcUnmodelledReached sentinel (message-free) — identical shape to
            // bmc-models: the violated function becomes the recognized sentinel so a reach demotes to a
            // member-named UNKNOWN (recovered from the trace call-chain), never a silent havoc. No
            // message string: a unique constant per synthesized method was the loud-body proof-time tax
            // (JBMC interns every constant even on unreached paths), and JBMC discards assert messages
            // anyway. The trailing `athrow null` keeps the method well-formed for any return type.
            iv.add(org.objectweb.asm.tree.InsnNode(Opcodes.ACONST_NULL))
            iv.add(org.objectweb.asm.tree.MethodInsnNode(Opcodes.INVOKESTATIC,
                "org/bmc4j/analysis/BmcUnmodelledReached", "reached", "(Ljava/lang/String;)V", false))
            iv.add(org.objectweb.asm.tree.InsnNode(Opcodes.ACONST_NULL))
            iv.add(org.objectweb.asm.tree.InsnNode(Opcodes.ATHROW))
            mn.maxStack = 1
            node.methods.add(mn)
            changed = true
        }
        if (changed) {
            val cw = ClassWriter(ClassWriter.COMPUTE_MAXS)
            node.accept(cw)
            classFile.writeBytes(cw.toByteArray())
        }
    }
}

fun parseMemberSignature(sig: String): Pair<String, List<Type>>? {
    val open = sig.indexOf('(')
    val close = sig.lastIndexOf(')')
    if (open < 0 || close < open) return null
    val name = sig.substring(0, open).trim()
    val params = sig.substring(open + 1, close).trim()
    val types = if (params.isEmpty()) emptyList() else params.split(',').map { erasedTypeToAsm(it.trim()) }
    if (types.any { it == null }) return null
    @Suppress("UNCHECKED_CAST")
    return name to (types as List<Type>)
}

fun erasedTypeToAsm(s: String): Type? {
    if (s.isEmpty()) return null
    if (s.endsWith("[]")) {
        val elem = erasedTypeToAsm(s.removeSuffix("[]")) ?: return null
        return Type.getType("[" + elem.descriptor)
    }
    return when (s) {
        "int" -> Type.INT_TYPE
        "long" -> Type.LONG_TYPE
        "boolean" -> Type.BOOLEAN_TYPE
        "byte" -> Type.BYTE_TYPE
        "char" -> Type.CHAR_TYPE
        "short" -> Type.SHORT_TYPE
        "float" -> Type.FLOAT_TYPE
        "double" -> Type.DOUBLE_TYPE
        "void" -> Type.VOID_TYPE
        else -> Type.getObjectType(s.replace('.', '/'))
    }
}
