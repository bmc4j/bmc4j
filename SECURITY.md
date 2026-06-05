# Security policy

## Soundness bugs are security bugs

bmc4j's product is trust: a green `@BmcProof` is a claim that **no input** within
the bound violates the property. Anything that can make that claim false while
showing green — an unsound model, a broken bytecode rewrite, a stale verdict
served from cache, an engine error misreported as verified — is treated with the
severity of a security vulnerability, because downstream users make decisions
(ship code, skip review, trust money paths) based on it.

If you find a way to make bmc4j report **verified** for code that is actually
refutable within the stated bounds and documented limits, please report it
privately.

## Reporting

- **GitHub private vulnerability reporting** (Security → Report a
  vulnerability on this repository)

Include the smallest proof + code pair that shows the false green, plus the
bmc4j version and platform. Known, documented over-approximations and limits
([docs/limits.md](docs/limits.md), [docs/coverage.md](docs/coverage.md)) are not
vulnerabilities — the boundary is *silent* unsoundness within what the docs
claim is covered.

## Scope notes

- The bundled engine is the official CBMC/JBMC release binary, SHA-256-pinned at
  packaging time; its integrity model is described in [docs/trust.md](docs/trust.md).
  Engine-level soundness issues are upstream bugs (we track and work around
  them), but a bmc4j configuration that *hides* one is in scope here.
- Supported version: the latest release. Pre-1.0 there are no security
  backports.
