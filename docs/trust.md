# Trust & isolation

The concern with any tool like this is "a build plugin running a binary." We keep
that boring and auditable:

- **The engine is a normal dependency, not a runtime download.** It ships inside a
  `bmc-engine-<platform>` jar, so its integrity is covered by Gradle's usual
  dependency verification — there is no fetch-and-exec from the internet at test time.
  (The jars themselves are assembled in CI from upstream CBMC release artifacts whose
  SHA-256 is pinned and verified before extraction.)
- **It runs as an isolated subprocess**, not linked into your build JVM, so a
  runaway solver can't corrupt or hang the build — set `timeoutSeconds` and a proof that
  exceeds its budget has its whole process tree force-killed and is reported UNKNOWN (see
  [the configuration docs](https://bmc4j.github.io/docs/)).
- **JBMC is a pure analyzer** — it reads bytecode and writes its result to stdout;
  it needs no network and modifies nothing.
- **Opt out anytime** with `bmc { jbmcPath = ... }` — and that's also the sandbox
  escape hatch: since the engine is just an executable bmc4j invokes, pointing
  `jbmcPath` at a wrapper script that does `docker run --network=none ... jbmc "$@"`
  runs every proof inside whatever container hardening you want, with no special
  support needed from bmc4j.
