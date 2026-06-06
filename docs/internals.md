# How it works

```
@BmcProof method ──► BmcProofExtension (JUnit InvocationInterceptor)
                         │  jbmc = bundled engine on the classpath (extracted once), or jbmcPath
                         │  entry = Class.method;  classpath = test JVM's java.class.path
                         ▼
                     jbmc --function ... --json-ui --trace   (subprocess)
                         │
                         ▼
                     JbmcOutputParser ──► verdict + counterexample + synthesized stack trace
```

## Module layout

The product modules live under `core/` (a Gradle build published standalone and
consumed by the `examples/` via `includeBuild`):

- **`core/bmc-runtime`** — the `@BmcProof`/`Bmc` API, the JUnit extension, the JBMC
  runner + output parser, and `BundledEngine`, which extracts the bundled binary
  from the classpath to a local cache (`~/.cache/bmc4j/engine/<platform>-<ver>/`).
  Self-contained: ships its own `org.cprover.CProver` (JBMC substitutes the semantics).
  Its two internal runtime libraries — **Gson** (parses JBMC's `--json-ui` output) and
  **ASM** (rewrites bytecode before analysis) — are **shaded + relocated** into the
  published jar under `org.bmc4j.internal.shaded.*` (via the Shadow plugin). So they
  are *not* declared as POM dependencies and never reach a consumer's classpath, where
  they could otherwise conflict with a different gson/asm the consumer pins. Nothing
  for consumers to configure or exclude.
- **`core/bmc-engine-<platform>`** — a resource-only jar bundling `jbmc` + `core-models.jar`
  for one OS/arch. Assembled at *our* build time (fetch + SHA-256-verify + extract);
  nothing binary is committed to git.
- **`core/bmc-gradle-plugin`** — applies `java` + JUnit 5, adds the runtime + the matching
  engine jar, and wires the `test` task.
- **`core/bmc-models`** — *additive* JBMC models for JDK types (collections, `Optional`,
  `Stream`, `BigInteger`/`BigDecimal`, `java.time` as epoch primitives), placed on the
  analysis classpath so that logic is analyzable. Extends JBMC's coverage without a fork.
  Proven sound by the two-axis conformance harness — see [model soundness](model-soundness.md).
- **`core/bmc-contracts`** — the `@Requires`/`@Ensures` annotation processor: generates
  replace-stubs, auto-discharged enforce-`@BmcProof`s, and the manifest the backend reads
  to redirect call sites. Enables modular (assume-guarantee) proofs — see
  [contracts](contracts.md). The plugin wires it onto consumers automatically.

## Platform support

| OS | Engine jar | Status |
|---|---|---|
| Windows x64 | `bmc-engine-windows-x64` (from CBMC `.msi`) | **bundled + verified** |
| Linux x64 / arm64 | `bmc-engine-linux-*` (from CBMC `.deb`) | **bundled + verified** (CI-built) |
| macOS x64 / arm64 | `bmc-engine-macos-*` (from the Homebrew bottle) | **bundled + verified** (CI-built) |

Each `bmc-engine-*` jar can only be *assembled* on its own OS (extraction tooling
differs), so the cross-platform jars are produced by a per-OS CI matrix
(`.github/workflows/engine-jars.yml`) and published to GitHub Packages. The
runtime's extraction + execution path is platform-generic.

## Java & Kotlin versions

bmc4j doesn't pin your language version — it analyses whatever bytecode you
target. Verified ranges (the bundled engine is CBMC 6.9.0 / JBMC):

| | Verified | Notes |
|---|---|---|
| **Java** | 17 – 25 | class-file major 61–69 all parse and analyse correctly; no JBMC ceiling found through 25. Every merge gates on 17/21/25: the full suite on 21 and 25, core + model conformance on the 17 shipped-floor runtime |
| **Kotlin** | 2.0 – 2.3 | verified on every merge by the consumer-compiler CI matrix (the conformance suite + Kotlin examples recompiled with kotlinc 2.0 and 2.2; 2.3 is the default toolchain). The 2.x K2 line is behaviourally identical for analysis; Kotlin 2.x requires the `compilerOptions` build DSL (not `kotlinOptions`). **Kotlin 1.9** consumers are supported by design — `bmc-kotlin` pins its emitted metadata (`languageVersion`/`apiVersion`) and its stdlib dependency (`coreLibrariesVersion`) to a 1.9 floor — but no longer continuously re-verified: KGP 1.9 cannot configure under the Gradle 9 build, so the 1.9 claim rests on those pins plus the original 1.9 sweep |
