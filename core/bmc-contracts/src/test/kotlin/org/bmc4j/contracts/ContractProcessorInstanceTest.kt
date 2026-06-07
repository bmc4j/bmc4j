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
    fun a_suspend_mirror_is_accepted_hiding_the_continuation_and_recovering_the_result(@TempDir out: Path) {
        // A suspend function, as kapt lowers it for javac: a trailing kotlin.coroutines.Continuation
        // parameter and an Object return. The processor must ACCEPT it, hide the Continuation from the
        // predicates, recover the declared result type (Int) from Continuation<? super Integer>, and
        // generate a stub/enforce that speak the lowered (args, Continuation)Object ABI.
        val (ok, manifest, _) = process(out,
                StringSource("kotlin.coroutines.Continuation", CONTINUATION_STUB),
                StringSource("org.bmc4j.coroutines.BmcSuspend", BMCSUSPEND_STUB),
                StringSource("demo.Worker", WORKER_SRC),
                StringSource("demo.WorkerContract", SUSPEND_CONTRACT_SRC))
        assertTrue(ok, "a suspend contract mirror (incl. generated stub/enforce) must compile: ")

        // The redirect's call-site descriptor is the lowered suspend ABI: (int, Continuation)Object.
        val contractLine = manifest.first { it.startsWith("contract ") }
        assertTrue(contractLine.contains("compute (ILkotlin/coroutines/Continuation;)Ljava/lang/Object;"),
                "the redirect must match the lowered suspend ABI descriptor: $contractLine")

        // The stub replicates the ABI: trailing Continuation param, Object return, boxed result; the
        // predicates bind the DECLARED int (Continuation hidden).
        val stub = generatedSource(out, "demo/WorkerContract__BmcStubs.java")
        assertTrue(stub.contains("public static java.lang.Object compute__stub(int n, kotlin.coroutines.Continuation"),
                "stub must replicate the suspend ABI (Object return, trailing Continuation):\n$stub")
        assertTrue(stub.contains("int r = org.cprover.CProver.nondetInt();"),
                "stub must havoc the DECLARED (unboxed) result type:\n$stub")
        assertTrue(stub.contains("WorkerContract.ok(n)"),
                "requires predicate must NOT receive the Continuation:\n$stub")
        assertTrue(stub.contains("return java.lang.Integer.valueOf(r);"),
                "stub must box the result into the ABI's Object return:\n$stub")

        // The enforce drives to completion (immediate-dispatch continuation) and unboxes for @Ensures.
        val enforce = generatedSource(out, "demo/WorkerContract__BmcEnforce.java")
        assertTrue(enforce.contains("org.bmc4j.coroutines.BmcSuspend.complete()"),
                "enforce must drive the suspend body with an immediately-completing continuation:\n$enforce")
        assertTrue(enforce.contains("int result = ((java.lang.Integer) demo.Worker.compute(a0, org.bmc4j.coroutines.BmcSuspend.complete())).intValue();"),
                "enforce must call the real suspend body and unbox the boxed result:\n$enforce")
    }

    @Test
    fun a_raw_continuation_suspend_mirror_is_rejected(@TempDir out: Path) {
        // A suspend mirror whose Continuation has no recoverable type argument (raw) can't yield a
        // declared result type — reject loudly rather than guess.
        val (ok, _, diagnostics) = process(out,
                StringSource("kotlin.coroutines.Continuation", CONTINUATION_STUB),
                StringSource("org.bmc4j.coroutines.BmcSuspend", BMCSUSPEND_STUB),
                StringSource("demo.Worker", WORKER_SRC),
                StringSource("demo.RawContract", RAW_SUSPEND_CONTRACT_SRC))
        assertFalse(ok, "a raw-Continuation suspend mirror must fail compilation")
        val errors = diagnostics.filter { it.kind == Diagnostic.Kind.ERROR }
                .joinToString("\n") { it.getMessage(null) }
        assertTrue(errors.contains("unrecoverable declared result type"),
                "the processor must reject an unrecoverable suspend result, naming it: $errors")
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

        // A minimal stand-in for the bmc-runtime immediate-dispatch driver the generated enforce names.
        val BMCSUSPEND_STUB = """
            package org.bmc4j.coroutines;
            import kotlin.coroutines.Continuation;
            public final class BmcSuspend {
                private BmcSuspend() {}
                public static Continuation<Object> complete() { return null; }
            }
            """.trimIndent()

        // The lowered suspend production target: `suspend fun compute(n: Int): Int` compiles to
        // `Object compute(int, Continuation)` returning the boxed Integer (or COROUTINE_SUSPENDED).
        val WORKER_SRC = """
            package demo;
            import kotlin.coroutines.Continuation;
            public final class Worker {
                public static Object compute(int n, Continuation<? super Integer> ${'$'}c) {
                    return Integer.valueOf(n);
                }
            }
            """.trimIndent()

        // A suspend mirror as kapt lowers it: trailing Continuation<? super Integer> parameter, Object
        // return. The processor recovers the declared result (Int) and hides the Continuation.
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
                static boolean nn(int result, int n) { return result >= 0; }
            }
            """.trimIndent()

        // A suspend mirror whose Continuation is RAW (no type argument): the declared result type is
        // unrecoverable, so the processor rejects it.
        val RAW_SUSPEND_CONTRACT_SRC = """
            package demo;
            import org.bmc4j.BmcContractsFor;
            import org.bmc4j.Ensures;
            import org.bmc4j.Requires;
            import kotlin.coroutines.Continuation;

            @SuppressWarnings("rawtypes")
            @BmcContractsFor(Worker.class)
            interface RawContract {
                @Requires("ok") @Ensures("nn") Object compute(int n, Continuation ${'$'}c);
                static boolean ok(int n) { return n >= 0; }
                static boolean nn(int result, int n) { return result >= 0; }
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
