<!-- bmc:metadata
proofs: 3
proof-execution: 26s summed across the module (JBMC time, MiniSat; approximate).
-->

# Concurrency (Java)

```
./gradlew :examples:concurrency-java:test
```

## `datarace` — exploring thread interleavings

`@BmcProof(concurrent = true)` turns on JBMC's thread analysis: it models
`Thread`/`Runnable`/`start()` and `synchronized`, and searches **interleavings** for one that
violates an assertion.

**The idiom:** assert the safety property **at the point of interest and let the threads race** —
don't rely on `Thread.join()` to sequence (JBMC doesn't model it as a barrier). To check a
*final* state, use a `Latch` barrier instead.

```java
@BmcProof(concurrent = true)
void read_sees_its_own_write() {
    Thread t = new Thread() { public void run() {
        shared = 44; int seen = shared; assert seen == 44;   // can fail!
    }};
    t.start();
    shared = 10;   // interleaves between the write and the read above
}
```

The other thread's `shared = 10` can land between `shared = 44` and `int seen = shared`, so
`seen == 10` — JBMC finds that interleaving. Guarding both accesses with the same monitor (or
sequencing via a `Latch`) is proven race-free. *(2 pass + 1 fail.)*
