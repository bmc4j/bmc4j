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
  engine jar, and wires the `test` task. Written in Kotlin (metadata/stdlib pinned to a 1.9
  floor, like `bmc-kotlin`, so it loads against any Gradle's embedded Kotlin ≥ 1.9).
- **`core/bmc-models`** — *additive* JBMC models for JDK types (collections, `Optional`,
  `Stream`, `BigInteger`/`BigDecimal`, `java.time` as epoch primitives), placed on the
  analysis classpath so that logic is analyzable. Extends JBMC's coverage without a fork.
  Proven sound by the two-axis conformance harness — see [model soundness](model-soundness.md).
- **`core/bmc-string-model`** — sound char-array-backed models of `java.lang.String` /
  `StringBuilder` / `AbstractStringBuilder` / `StringBuffer`, used **only** when string
  refinement is OFF (`--no-refine-strings` / `StringMode.CHAR_ARRAY_MODEL`). Under refinement (the default)
  JBMC's refinement solver supplies the sound String model and these classes are NOT on the
  classpath; with refinement off the cbmc `core-models.jar` String/StringBuilder are degenerate
  intrinsic-only shells (`length()` → `nondetInt`, `charAt` → a placeholder, `StringBuilder.toString`
  → a possibly-null nondet), so a String's backing is null and a correct property like
  `Buffer().writeUtf8("ab"); size==2` false-REFUTES with a `NullPointerException`. These models
  back a String with a real `char[]` so construction / `length()` / `charAt()` are sound array
  operations. Packaged like `bmc-kotlin-models`: shipped as inert resources in `bmc-runtime` and
  prepended to JBMC's analysis classpath only under no-refine (`BundledStringModel`). Covers
  construction (`new String(char[])`, `StringBuilder.append(char)`+`toString()`), `length`,
  `charAt`, `isEmpty`, `equals`, `hashCode`, `substring`, `compareTo`. Limits under no-refine: a
  String **literal**'s content is not recovered (JBMC materializes a literal without a constructor,
  so its backing is a fresh nondet array — sound but content-unconstrained). A symbolic string's
  LENGTH is now bounded soundly under no-refine by `StringLengthBytecode`, which rewrites the
  symbolic-string introduction (`Bmc.anyString`/`anyAsciiString` helper bodies and bare
  `String s = nondetWithoutNull()`) into a bounded char-array construction: the per-call
  `anyString(n)` bound, and the global `@BmcProof.maxStringLength` for a bare nondet string, bind the
  same way they do under refinement (mode-agnostic) instead of being silently dropped.
- **`core/bmc-contracts`** — the `@Requires`/`@Ensures` annotation processor: generates
  replace-stubs, auto-discharged enforce-`@BmcProof`s, and the manifest the backend reads
  to redirect call sites. Enables modular (assume-guarantee) proofs — see
  [contracts](contracts.md). The plugin wires it onto consumers automatically. Written in
  Kotlin, as are `bmc-constraints`/`bmc-constraints-jakarta` — all three live on the
  annotation-processor path only, so their kotlin-stdlib never reaches a consumer's test or
  analysis classpath.

## Replay scratch files

When JBMC refutes a proof it hands back the symbolic input assignment that triggers the
violation. `ReplayRenderer` turns those bindings back into concrete source literals, and
`ReplayTestWriter` drops a runnable `@Test` scaffold at
`build/bmc4j/replays/<Class>_<method>Replay.{java|kt}` — a *scratch* artifact, never added
to any source set, that the developer pastes into a test source set and steps through.

**Language selection.** The replay matches the language of the proof it came from:

- **`auto`** (default): the proof class is inspected for the `kotlin.Metadata` annotation
  kotlinc stamps onto every class it emits. Present → a `.kt` replay (`val` bindings, Kotlin
  literal syntax, header pointing at `src/test/kotlin`); absent → a `.java` replay, **byte-
  identical to the historical output** so pure-Java users see no change.
- **forced**: `bmc { replayLanguage = "kotlin" }` / `"java"` (or, per run,
  `-Pbmc.replayLanguage=...` / `-Dbmc.replayLanguage=...`) pins one language regardless of
  the proof class. Only `auto|kotlin|java` are accepted; anything else fails the build at
  configuration time. `auto` is the runtime default, so the property is forwarded to the test
  JVM only when an explicit `kotlin`/`java` override is set.

**Kotlin literal rendering** is a real mode, not a string-replace over the Java output:
doubles are emitted bare (Kotlin has no `d`/`D` suffix), `$` is escaped in strings (template
interpolation), `short`/`byte` bindings get an explicit type (`val x: Short = 3`, since a
bare integer literal is `Int`), while `1L` / `'c'` / `true` / `Float.POSITIVE_INFINITY` /
enum constants (`Suit.HEARTS`) / `\uXXXX` escapes carry over unchanged. A Kotlin proof method
with a backtick name containing spaces (`fun \`clamp is in bounds\`()`) is sanitized to a
plain identifier for the file/class name and shown backtick-quoted where used as a Kotlin
identifier. Non-reconstructible bindings (object graphs, references) stay **commented
descriptions** in both languages — the renderer never emits non-compiling code presented as
runnable.

## Platform support

| OS | Engine jar | Status |
|---|---|---|
| Windows x64 | `bmc-engine-windows-x64` (from CBMC `.msi`) | **bundled + verified** |
| Linux x64 / arm64 (glibc) | `bmc-engine-linux-x64`, `bmc-engine-linux-arm64` (from CBMC `.deb`) | **bundled + verified** (CI-built) |
| Linux x64 (musl / Alpine) | `bmc-engine-linux-x64-musl` (static-musl `jbmc` fetched from the [bmc4j/jbmc-musl-builds](https://github.com/bmc4j/jbmc-musl-builds) release; `core-models.jar` reused from the glibc `.deb`) | **bundled + verified** (CI-built) |
| macOS x64 / arm64 | `bmc-engine-macos-*` (from the Homebrew bottle) | **bundled + verified** (CI-built) |

Each `bmc-engine-*` jar can only be *assembled* on its own OS (extraction tooling
differs), so the cross-platform jars are produced by a per-OS CI matrix
(`.github/workflows/engine-jars.yml`) and published to GitHub Packages. The
runtime's extraction + execution path is platform-generic.

The musl/Alpine engine exists because upstream CBMC ships only glibc artifacts, and
a glibc-linked `jbmc` cannot exec under musl. Since there's no upstream musl artifact
to fetch, the static-musl `jbmc` is built — once per CBMC bump — in a dedicated builder
repo ([bmc4j/jbmc-musl-builds](https://github.com/bmc4j/jbmc-musl-builds)): it compiles
`jbmc` from the integrity-pinned CBMC 6.9.0 source in an `alpine` container with the
musl toolchain (statically linked, so the bundled binary has no apk runtime
dependencies), smoke-tests it, and publishes it as a SHA-256-pinned GitHub release
asset. The `linux-x64-musl` engine jar is then assembled exactly like every other
platform — `prepareEngine` *fetches* that prebuilt tarball, verifies its SHA-256, and
extracts it (no compiler runs in bmc4j's own pipeline); the architecture-independent
`core-models.jar` is reused verbatim from the (integrity-pinned) glibc `.deb`. The
runtime tells a musl x64 host
apart from glibc by probing for the Alpine release marker or an `ld-musl-*` loader
(`Platform.current()` / `BundledEngine.isMuslLibc`), and selects this jar accordingly;
the Gradle plugin runs the same probe when wiring the engine dependency.

## Java & Kotlin versions

bmc4j doesn't pin your language version — it analyses whatever bytecode you
target. Verified ranges (the bundled engine is CBMC 6.9.0 / JBMC):

| | Verified | Notes |
|---|---|---|
| **Kotlin** | 2.0 – 2.4 | verified on every merge by the consumer-compiler CI matrix (the conformance suite + Kotlin examples recompiled with kotlinc 2.0, 2.2, and 2.3; 2.4 is the default toolchain). The 2.x K2 line is behaviourally identical for analysis; Kotlin 2.x requires the `compilerOptions` build DSL (not `kotlinOptions`). **Kotlin 1.9** consumers are supported by design — `bmc-kotlin` pins its emitted metadata (`languageVersion`/`apiVersion`) and its stdlib dependency (`coreLibrariesVersion`) to a 1.9 floor, and builds with kotlinc 2.3 (the last compiler line that can emit it: 2.4 removed `languageVersion 1.9`) — but no longer continuously re-verified: KGP 1.9 cannot configure under the Gradle 9 build, so the 1.9 claim rests on those pins plus the original 1.9 sweep |
| **Java** | 17 – 25 | class-file major 61–69 all parse and analyse correctly; no JBMC ceiling found through 25. Every merge gates on 17/21/25: the full suite on 21 and 25, core + model conformance on the 17 shipped-floor runtime |
