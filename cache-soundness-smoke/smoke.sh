#!/usr/bin/env bash
# Verdict-cache soundness smoke (CI job `cache-soundness`).
#
# Proves the verdict cache end-to-end (plugin -> cache -> cone key -> engine) on the tiny
# dedicated `cache-soundness-smoke` module, in four phases. Each phase runs the proof and
# asserts on the plugin's per-proof progress line, which reads:
#     bmc4j < OK   AdderProof.dbl_equals_two_x (cached verdict, 0.0s)   <- a verdict-cache HIT
#     bmc4j < OK   AdderProof.dbl_equals_two_x (1.3s)                    <- a live engine solve
# i.e. the literal "cached verdict" marker (emitted in BmcPlugin's afterTest listener) is the
# cache-HIT signal; its ABSENCE means the engine actually ran.
#
# Any phase that does not see what it must fails LOUD, naming the phase. NO `clean` runs between
# phases: the cache lives under build/bmc4j/verdict-cache and `clean` would wipe it, defeating
# the point. --rerun-tasks forces the :test task to actually execute every phase (so the cache is
# consulted) instead of Gradle skipping an up-to-date run; --no-build-cache mirrors the main CI.
set -euo pipefail

MODULE="cache-soundness-smoke"
PROOF="AdderProof.dbl_equals_two_x"
ADDER="${MODULE}/src/main/java/smoke/proven/Adder.java"
UNRELATED="${MODULE}/src/main/java/smoke/proven/Unrelated.java"
FLAGS="${BMC_FLAGS:-}"   # e.g. -PbmcJvmTarget=21 from the workflow
LOG="$(mktemp)"

# Run the proof task, teeing all output to $LOG. Returns gradle's exit code (does not abort the
# script on a non-zero run; phase 3 EXPECTS a failure).
run_proof() {
  : > "$LOG"
  set +e
  ./gradlew --no-daemon --no-build-cache --rerun-tasks --console=plain \
    $FLAGS ":${MODULE}:test" 2>&1 | tee "$LOG"
  local rc=${PIPESTATUS[0]}
  set -e
  return $rc
}

fail() { echo "::error::[$1] $2"; echo "----- captured proof output -----"; cat "$LOG"; exit 1; }

# The progress line for our one proof, if present.
proof_line() { grep -E "bmc4j < .*${PROOF}" "$LOG" || true; }
is_hit()  { proof_line | grep -q "cached verdict"; }

echo "=================================================================="
echo " Phase 1: cold run -> must be GREEN and a LIVE solve (cache empty)"
echo "=================================================================="
# Start from a clean cache so phase 1 is genuinely cold.
rm -rf "${MODULE}/build/bmc4j/verdict-cache"
run_proof || fail "phase1-cold" "cold proof run failed; expected a GREEN verify"
[ -n "$(proof_line)" ] || fail "phase1-cold" "proof never ran (no progress line for ${PROOF})"
if is_hit; then
  fail "phase1-cold" "expected a LIVE solve on the cold run, but the line says 'cached verdict'"
fi
echo "[phase1-cold] OK: proof verified live, no cache hit."

echo "=================================================================="
echo " Phase 2: unchanged re-run -> must be a CACHE HIT"
echo "=================================================================="
run_proof || fail "phase2-hit" "unchanged re-run failed; expected a cached GREEN"
is_hit || fail "phase2-hit" "expected 'cached verdict' on the unchanged re-run, but the proof re-solved"
echo "[phase2-hit] OK: unchanged proof served from the verdict cache."

echo "=================================================================="
echo " Phase 3: mutate the proven code to VIOLATE -> must RE-SOLVE and FAIL"
echo "=================================================================="
cp "$ADDER" "${ADDER}.bak"
# Break the property: dbl(x) now returns x + x + 1, so dbl(x) == 2*x is false. The class CONTENT
# changes -> the proof's cone-scoped cache key changes -> the stale green must NOT be served; a
# fresh engine run must REFUTE and the build must go RED.
sed -i 's/return x + x;/return x + x + 1;/' "$ADDER"
if run_proof; then
  cp "${ADDER}.bak" "$ADDER"; rm -f "${ADDER}.bak"
  fail "phase3-stale" "the violated proof PASSED — a stale cached green was served (SOUNDNESS BUG)"
fi
# It failed as required; make sure it failed by RE-SOLVING (not by serving a stale-hit-then-erroring).
if is_hit; then
  cp "${ADDER}.bak" "$ADDER"; rm -f "${ADDER}.bak"
  fail "phase3-stale" "the mutated proof reported 'cached verdict' — the cache did not invalidate"
fi
cp "${ADDER}.bak" "$ADDER"; rm -f "${ADDER}.bak"
echo "[phase3-stale] OK: violated proof re-solved and FAILED; no stale green served."

echo "=================================================================="
echo " Phase 4: restore + touch an UNRELATED class -> must still be a HIT"
echo "=================================================================="
# Phase 3 restored Adder.java to its proven form, but never wrote a cache entry for the mutated
# code (failures are never cached) and the restored Adder is byte-identical to phase 2's, so its
# phase-2 cache entry is still valid. Now perturb a class OUTSIDE the proof's reachable cone: a
# cone-scoped key must ignore it, so the proof must still be a HIT (the cone-key promise).
cp "$UNRELATED" "${UNRELATED}.bak"
sed -i 's/return 0;/return 42;/' "$UNRELATED"
if ! run_proof; then
  cp "${UNRELATED}.bak" "$UNRELATED"; rm -f "${UNRELATED}.bak"
  fail "phase4-cone" "re-run after touching an unrelated class failed; expected a cached GREEN"
fi
if ! is_hit; then
  cp "${UNRELATED}.bak" "$UNRELATED"; rm -f "${UNRELATED}.bak"
  fail "phase4-cone" "touching an unrelated class invalidated the proof (cone key did not hold)"
fi
cp "${UNRELATED}.bak" "$UNRELATED"; rm -f "${UNRELATED}.bak"
echo "[phase4-cone] OK: unrelated-class edit kept the proof a cache HIT (cone key holds)."

rm -f "$LOG"
echo "=================================================================="
echo " ALL PHASES PASSED: verdict cache is sound end-to-end."
echo "=================================================================="
