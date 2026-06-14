// Sound char-array-backed models of java.lang.String / StringBuilder / AbstractStringBuilder for
// JBMC's ANALYSIS classpath, used ONLY when string refinement is OFF (--no-refine-strings, the
// StringMode.CHAR_ARRAY_MODEL path).
//
// WHY THIS MODULE EXISTS
// Under string refinement (the default), JBMC supplies its OWN sound String model via the refinement
// solver, and the cbmc core-models.jar String/StringBuilder are intrinsic-only shells (length() ->
// nondetInt, charAt -> a CProverString placeholder, StringBuilder.toString -> nondetWithNull). With
// refinement OFF those degenerate bodies link literally: a String's backing is null, so length()/
// charAt() dereference null and a correct property like `Buffer().writeUtf8("ab"); size==2` FALSELY
// REFUTES with a NullPointerException. So --no-refine-strings is not "flip a flag" - it removes the
// model refinement was providing.
//
// These classes carry the real fully-qualified names (java.lang.String, etc.) and back a String with a
// genuine `char[] value`: length() reads value.length, charAt(i) reads value[i] (sound array reads with
// the normal bounds behaviour), and construction (new String(char[]) / StringBuilder.append(char) +
// toString()) stores a real array. JBMC then analyses these as plain, tractable, SOUND array operations
// - the same machinery that already verifies writeByte / char-array proofs.
//
// PACKAGING (mirrors bmc-kotlin-models): these classes share names with real JDK classes, so they must
// NEVER reach a real runtime classpath (the bootstrap loader always wins for java.*, but a test JVM
// could still load a stray copy). They are therefore NOT a normal dependency: bmc-runtime consumes this
// module's compiled classes as inert RESOURCES, bundles them into its jar, and (only under no-refine)
// extracts them onto JBMC's analysis classpath at verification time (see BundledStringModel).
//
// Compiled by patching java.base (the only way to compile classes in the java.* packages), exactly like
// bmc-models. --release is incompatible with --patch-module, so source/target is used instead.
plugins {
    java
}

val javaSrc = layout.projectDirectory.dir("src/main/java").asFile.absolutePath

// 17 baseline so 17-targeting consumers can resolve this.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    // The lazy-backing path for string literals / nondet strings prunes via org.cprover.CProver
    // (nondetInt + assume) - the same primitive Bmc.assume and the engine desugars use; JBMC recognises
    // CProver by FQN and substitutes its semantics, the body never runs. compileOnly: needed only to
    // compile against, never shipped on this artifact (bmc-runtime, where CProver lives, is already on
    // JBMC's analysis classpath).
    compileOnly(project(":bmc-runtime"))
}

tasks.named<JavaCompile>("compileJava") {
    // --patch-module: compile the java.* models into java.base. --add-reads: the patched java.base must
    // be allowed to read org.cprover.CProver in the unnamed module (the compileOnly entry above).
    options.compilerArgs.addAll(
        listOf("--patch-module", "java.base=$javaSrc", "--add-reads", "java.base=ALL-UNNAMED"),
    )
    // Desugar Java string-concatenation (`+`) to explicit StringBuilder bytecode rather than an
    // invokedynamic bootstrap - invokedynamic is JBMC's one unsoundness boundary and would fail the
    // class conversion, so keep these analysis-classpath models invokedynamic-free (same hygiene as
    // bmc-kotlin-models).
    options.compilerArgs.add("-XDstringConcat=inline")
}

// javadoc refuses to document java.* packages ("package exists in another module: java.base"), and
// this module shadows them by design - so the javadoc task can never run here.
tasks.withType<Javadoc>().configureEach {
    enabled = false
}
