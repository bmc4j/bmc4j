package org.bmc4j.contracts

import org.bmc4j.engine.ContractManifest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.StringWriter
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import javax.tools.Diagnostic
import javax.tools.DiagnosticCollector
import javax.tools.JavaFileObject
import javax.tools.SimpleJavaFileObject
import javax.tools.StandardLocation
import javax.tools.ToolProvider

/**
 * In-process [ContractProcessor] tests for **pure instance** contracts (the v2 receiver-binding
 * convention) and for signature-binding failure modes. Each compiles a small `@BmcContractsFor`
 * type with the real processor and inspects the emitted manifest / generated sources / diagnostics.
 */
class ContractProcessorInstanceTest {

    @Test
    fun instance_target_emits_an_instance_contract_line_and_threads_self(@TempDir out: Path) {
        val (ok, manifest, _) = process(out,
                StringSource("demo.Account", ACCOUNT_SRC),
                StringSource("demo.AccountContract", ACCOUNT_CONTRACT_SRC))
        assertTrue(ok, "instance contract sources (incl. generated stub/enforce) must compile")

        val contractLine = manifest.first { it.startsWith("contract ") }
        // contract demo/Account project (I)I demo/AccountContract__BmcStubs project__stub instance (Ldemo/Account;I)I
        assertTrue(contractLine.contains(" instance "),
                "an instance target must emit an 'instance' contract line: $contractLine")
        assertTrue(contractLine.endsWith(" (Ldemo/Account;I)I"),
                "the stub descriptor must prepend the receiver type: $contractLine")

        // The generated stub threads the receiver as a leading `self` param.
        val stub = generatedSource(out, "demo/AccountContract__BmcStubs.java")
        assertTrue(stub.contains("project__stub(demo.Account self, int amount)"),
                "stub must take the receiver first:\n$stub")
        // The generated enforce-proof nondets a receiver and calls it.
        val enforce = generatedSource(out, "demo/AccountContract__BmcEnforce.java")
        assertTrue(enforce.contains("demo.Account self = (demo.Account) org.cprover.CProver.nondetWithoutNull();"),
                "enforce must nondet a symbolic receiver:\n$enforce")
        assertTrue(enforce.contains("self.project(a0)"), "enforce must call the real instance method:\n$enforce")
    }

    @Test
    fun a_renamed_instance_method_orphans_the_contract_with_a_named_error(@TempDir out: Path) {
        val (ok, _, diagnostics) = process(out,
                StringSource("demo.Account", ACCOUNT_SRC),
                StringSource("demo.OrphanContract", ORPHAN_CONTRACT_SRC))
        assertFalse(ok, "an orphaned contract mirror must fail compilation")
        val errors = diagnostics.filter { it.kind == Diagnostic.Kind.ERROR }.joinToString("\n") { it.getMessage(null) }
        assertTrue(errors.contains("no method on Account matches"),
                "the processor must report the orphan at processing time, naming the target: $errors")
        assertTrue(errors.contains("projectRenamed"),
                "the error must name the missing mirror signature: $errors")
    }

    @Test
    fun a_suspend_mirror_is_rejected_with_a_named_error(@TempDir out: Path) {
        // A suspend function, as kapt lowers it for javac: a trailing kotlin.coroutines.Continuation
        // parameter and an Object return. The processor must reject it by name, not bind a
        // (…,Continuation)Object shape no caller expects.
        val (ok, _, diagnostics) = process(out,
                StringSource("kotlin.coroutines.Continuation", CONTINUATION_STUB),
                StringSource("demo.Worker", WORKER_SRC),
                StringSource("demo.WorkerContract", SUSPEND_CONTRACT_SRC))
        assertFalse(ok, "a suspend contract mirror must fail compilation")
        val errors = diagnostics.filter { it.kind == Diagnostic.Kind.ERROR }
                .joinToString("\n") { it.getMessage(null) }
        assertTrue(errors.contains("is a suspend function"),
                "the processor must reject a suspend mirror, naming it: $errors")
    }

    @Test
    fun a_contract_type_with_no_visible_mirror_is_a_hard_error(@TempDir out: Path) {
        // The value/inline-class case as kapt presents it: the mangled mirror is OMITTED from the Java
        // stub, so the type has no visible @Requires/@Ensures method. That silent no-bind must be a
        // hard error, and the message must point at the value-class cause.
        val (ok, _, diagnostics) = process(out,
                StringSource("demo.Empty", EMPTY_TARGET_SRC),
                StringSource("demo.EmptyContract", EMPTY_CONTRACT_SRC))
        assertFalse(ok, "a @BmcContractsFor type that binds no contract must fail compilation")
        val errors = diagnostics.filter { it.kind == Diagnostic.Kind.ERROR }
                .joinToString("\n") { it.getMessage(null) }
        assertTrue(errors.contains("binds no contract"),
                "the no-mirror case must be a hard error: $errors")
        assertTrue(errors.contains("value/inline class"),
                "the error must name the value/inline-class cause: $errors")
    }

    // --- harness ---

    private data class Result(val ok: Boolean, val manifest: List<String>, val diagnostics: List<Diagnostic<out JavaFileObject>>)

    private fun process(out: Path, vararg sources: JavaFileObject): Result {
        val javac = ToolProvider.getSystemJavaCompiler()
                ?: fail("no system Java compiler available (run on a JDK, not a JRE)")
        val diagnostics = DiagnosticCollector<JavaFileObject>()
        javac.getStandardFileManager(diagnostics, null, null).use { fm ->
            fm.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, listOf(out))
            fm.setLocationFromPaths(StandardLocation.SOURCE_OUTPUT, listOf(out))
            val task = javac.getTask(StringWriter(), fm, diagnostics, null, null, sources.toList())
            task.setProcessors(listOf(ContractProcessor()))
            val ok = task.call()
            val manifestPath = out.resolve(ContractManifest.RESOURCE)
            val manifest = if (Files.isRegularFile(manifestPath)) Files.readAllLines(manifestPath) else emptyList()
            return Result(ok, manifest, diagnostics.diagnostics)
        }
    }

    private fun generatedSource(out: Path, relativePath: String): String =
            Files.readString(out.resolve(relativePath))

    private class StringSource(fqn: String, private val code: String) : SimpleJavaFileObject(
            URI.create("string:///" + fqn.replace('.', '/') + JavaFileObject.Kind.SOURCE.extension),
            JavaFileObject.Kind.SOURCE) {
        override fun getCharContent(ignoreEncodingErrors: Boolean): CharSequence = code
    }

    private companion object {

        val ACCOUNT_SRC = """
            package demo;
            public final class Account {
                private final int balance;
                public Account(int balance) { this.balance = balance; }
                public int balance() { return balance; }
                public int project(int amount) { return balance + amount; }
            }
            """.trimIndent()

        val ACCOUNT_CONTRACT_SRC = """
            package demo;
            import org.bmc4j.BmcContractsFor;
            import org.bmc4j.Ensures;
            import org.bmc4j.Requires;

            @BmcContractsFor(Account.class)
            interface AccountContract {
                @Requires("ok") @Ensures("atLeast") int project(int amount);
                static boolean ok(Account self, int amount) { return amount >= 0; }
                static boolean atLeast(int result, Account self, int amount) { return result >= self.balance(); }
            }
            """.trimIndent()

        // A minimal stand-in for kotlin.coroutines.Continuation so the suspend mirror's signature
        // resolves under plain javac (the processor matches it by fully-qualified name).
        val CONTINUATION_STUB = """
            package kotlin.coroutines;
            public interface Continuation<T> { }
            """.trimIndent()

        val WORKER_SRC = """
            package demo;
            public final class Worker {
                public int compute(int n) { return n; }
            }
            """.trimIndent()

        // A suspend mirror as kapt lowers it: trailing Continuation parameter.
        val SUSPEND_CONTRACT_SRC = """
            package demo;
            import org.bmc4j.BmcContractsFor;
            import org.bmc4j.Ensures;
            import org.bmc4j.Requires;
            import kotlin.coroutines.Continuation;

            @BmcContractsFor(Worker.class)
            interface WorkerContract {
                @Requires("ok") @Ensures("nn") Object compute(int n, Continuation<? super Integer> ${'$'}c);
                static boolean ok(int n) { return n >= 0; }
                static boolean nn(Object result, int n) { return true; }
            }
            """.trimIndent()

        val EMPTY_TARGET_SRC = """
            package demo;
            public final class Empty {
                public int f(int n) { return n; }
            }
            """.trimIndent()

        // A @BmcContractsFor type with NO @Requires/@Ensures method visible — exactly what kapt leaves
        // behind after dropping a value-class-typed mirror.
        val EMPTY_CONTRACT_SRC = """
            package demo;
            import org.bmc4j.BmcContractsFor;

            @BmcContractsFor(Empty.class)
            interface EmptyContract {
                static boolean ok(int n) { return n >= 0; }
            }
            """.trimIndent()

        // Mirrors a method `projectRenamed` that does not exist on Account -> orphan.
        val ORPHAN_CONTRACT_SRC = """
            package demo;
            import org.bmc4j.BmcContractsFor;
            import org.bmc4j.Ensures;
            import org.bmc4j.Requires;

            @BmcContractsFor(Account.class)
            interface OrphanContract {
                @Requires("ok") @Ensures("atLeast") int projectRenamed(int amount);
                static boolean ok(Account self, int amount) { return amount >= 0; }
                static boolean atLeast(int result, Account self, int amount) { return result >= self.balance(); }
            }
            """.trimIndent()
    }
}
