<!-- bmc:metadata
proofs: 32
proof-execution: 304s summed across the module (JBMC time, MiniSat; approximate). Proofs run in
  parallel, so wall-clock is far lower — this number is for spotting slow concepts, not timing the build.
-->

# Standard-library modeling

JBMC stubs most `java.*` library types to nondeterministic values — so any proof touching them
is silently unsound. bmc4j ships **clean, bounded models** on the analysis classpath (the real
JVM ignores them; `java.*` always loads from bootstrap), so these types verify for real. One
package per concept.

```
./gradlew :examples:stdlib:test
./gradlew :examples:stdlib:test --tests "proofs.bigdecimal.*"
```

## `strings` — sound `equals` / `startsWith` / `contains` + concatenation

JBMC's native `String.equals` is unsound (it can't even prove `"x".equals("x")`). bmc4j rewrites
`String.equals`/`startsWith`/`endsWith`/`contains` call sites — **without forking the engine** — to
sound stand-ins built from `length()` + `charAt`, comparing character-by-character; and desugars
`String`+`String` concatenation from its `StringConcatFactory` invokedynamic. Your code keeps
calling `equals`. Bound symbolic strings with `Bmc.anyString(n)` (the comparison loops to the
length). **The bug:** `banner()` calls `.equals` on a possibly-unset value → NPE.
*(6 pass + 1 fail.)*

### Charset-bounded symbolic strings

`Bmc.anyString(min, max)` bounds the length both ways; `Bmc.anyString(n, alphabet)` and
`Bmc.anyAsciiString(n)` additionally bound every character to an alphabet (resp. printable ASCII
`0x20..0x7E`) — the string analogue of `anyInt(lo, hi)`. Folding the per-char domain into the
helper shrinks the SAT problem per character and dodges the all-of-UTF-16 content trap, while
keeping the symbolic length (the cost driver) small. The constraints are assumed over the sound
`charAt` primitive, so a proof reading `length()`/`charAt(i)` back honours them. See
`proofs.strings.CharsetProofs` (8 pass + 3 intended fail).

## `collections` — `List` / `Map` / `Set` / `Optional`

Array-backed bounded models (capacity 64) for `List`/`ArrayList`/`LinkedList`,
`Map`/`HashMap`/`TreeMap`, `Set`/`HashSet`, `Optional`, their iterators, the `of(...)` factories,
and `Stream`/`IntStream`. Kotlin works too (`listOf`/`mapOf`/… via clean facade models + the
`map`/`filter`/`fold`/`sum` extensions). **The bug:** `Cart.firstPrice()` does `get(0)` on a
possibly-empty cart → `IndexOutOfBounds` (invisible under the old nondet stub). Keep collections
within the proof's `unwind`. *(6 pass + 1 fail.)*

## `bigdecimal` — exact decimal money arithmetic

Modeled as an unscaled `long` + an `int` scale, so `0.10 + 0.20` is exactly `0.30` — **without**
falling back to `double` (which would reintroduce the binary error `BigDecimal` exists to avoid).
Sound: `add`/`subtract`/`multiply`, `compareTo`/`equals`, `setScale`/`divide` with an explicit
`RoundingMode`. The `double` constructor is omitted by design. **The bug:** the classic
penny-split — `$10.00 / 3 = $3.33` each, `3 × $3.33 = $9.99`, a penny short. *(2 pass + 1 fail.)*

## `datetime` — `java.time` as epoch primitives

`Instant` → epoch-millis `long`, `Duration` → millis, `LocalDate` → epoch-day — so date logic
becomes integer comparison, JBMC's strength. Your code uses the real JDK types. Two bugs:

- **inclusive/exclusive boundary** — `within(when, start, end)` uses `isBefore(end)`, wrongly
  excluding `end` from its own range.
- **DST fall-back re-entry** — a toggle keyed on wall-clock time can go on→off→on across a
  (symbolic) daylight-saving transition, because local time is non-monotonic there. The fix:
  schedule on instants (UTC). Time zones / the IANA calendar are out of scope by design — a
  single symbolic transition is enough to prove DST-robustness. *(2 pass + 2 fail.)*
