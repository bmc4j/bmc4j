<!-- bmc:metadata
proofs: 4
proof-execution: 32s summed across the module (JBMC time, MiniSat; approximate).
-->

# Language features (Java)

Java language constructs made analyzable. One package per concept.

```
./gradlew :examples:language-java:test
```

## `lambdas` — lambdas & method references

A lambda or method reference compiles to an `invokedynamic` (bootstrap `LambdaMetafactory`) —
the JVM spins a hidden class for it at runtime, which JBMC can't construct. bmc4j **desugars
each lambda site** during analysis: it generates an ordinary class implementing the functional
interface (captures become fields, the SAM method delegates to the lambda body), so your code
is analyzed unchanged — no engine fork.

```java
@BmcProof   // PASSES: a lambda passed into a higher-order function
void increment_twice_adds_two() {
    int x = Bmc.anyInt(-1000, 1000);
    Bmc.check(Rules.applyTwice(v -> v + 1, x) == x + 2);
}
```

**The bug it finds:** `avg = (x, y) -> (x + y) / 2`. The claim that the average is *strictly*
above the smaller input fails — for adjacent values `(0 + 1) / 2` is `0` by integer truncation.
BMC reports `a = 0, b = 1`. Lambdas and method references (static / instance / constructor,
capturing or not) work for both Java and Kotlin. *(3 pass + 1 fail.)*
