<!-- Thanks! Two repo-specific things before review: -->

**What & why:**

**Checklist:**
- [ ] Green gate passes: `./gradlew -p core build` + `./gradlew test`
  (fail-on-purpose demos self-assert their expected verdict, so the root test is green)
- [ ] Model changes: both conformance axes + a `CoverageGateTest` entry
- [ ] Rewrite-layer changes: transform unit test + end-to-end soundness proof
- [ ] Docs updated if behavior/API/coverage changed (`docs/coverage.md` rows included)
