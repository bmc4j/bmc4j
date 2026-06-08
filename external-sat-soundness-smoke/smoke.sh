#!/usr/bin/env bash
# External-SAT (fast solver) soundness smoke + Step-0 empirical probe (CI job `external-sat-soundness`).
#
# The cardinal invariant: the fast external SAT solver runs the engine with text/String reasoning OFF,
# so it must NEVER serve a VERIFIED for a proof that actually depends on text. This script proves that
# end-to-end against REAL jbmc + the bundled fast solver (Linux only), on two proofs that genuinely need
# string reasoning (proofs.extsat.TextProofs), across four phases:
#
#   Phase 0  cold, DEFAULT solver         -> both proofs pass (REFUTED-as-expected + VERIFIED): the
#                                            proofs are honest and the engine works.
#   Phase 1  GUARD (the regression test)  -> request the fast solver on these TEXT proofs. The safe-by-
#                                            default guard must FAIL LOUD with a plain-language message;
#                                            NO proof may report VERIFIED on the fast solver. <-- THE PROOF
#                                            THAT THE HOLE IS CLOSED.
#   Phase 2  PROBE (Step 0)               -> expert unsafe override ON: the proofs ACTUALLY run on the
#                                            fast solver, refinement-off. We record what jbmc does (does it
#                                            self-protect or falsely verify?) — diagnostic, never fails.
#   Phase 3  OPT-OUT fallback             -> fast solver requested + externalSatStringFallback=true: the
#                                            text proofs fall back to the SOUND default solver (no speedup)
#                                            and the run is GREEN.
#
# Any phase that doesn't see what it must fails LOUD, naming the phase.
set -uo pipefail

MODULE="external-sat-soundness-smoke"
FLAGS="${BMC_FLAGS:-}"   # e.g. -PbmcJvmTarget=21 from the workflow
LOG="$(mktemp)"
SUMMARY=""

# Run the module's test task with extra -D flags ($1), teeing output to $LOG. Never aborts the script
# (phases 1 EXPECTS a non-zero exit). --no-build-cache + --rerun-tasks so the engine actually runs each
# phase; -Dbmc.noCache=true so a prior phase's cached verdict never masks the solver behaviour.
run_proofs() {
  : > "$LOG"
  ./gradlew --no-daemon --no-build-cache --rerun-tasks --console=plain \
    -Dbmc.noCache=true $FLAGS "$@" ":${MODULE}:test" > "$LOG" 2>&1
  return $?
}

fail() { echo "::error::[$1] $2"; echo "----- captured proof output -----"; cat "$LOG"; exit 1; }
note() { echo "$1"; SUMMARY="${SUMMARY}\n$1"; }

# The kissat binary must be discoverable. The engine jar bundles it; extract happens on first proof run.
# We locate it under the engine cache after phase 0 has run the engine at least once.
locate_kissat() {
  find "${HOME}/.cache/bmc4j/engine" -type f -name kissat 2>/dev/null | head -n1
}

echo "=================================================================="
echo " Phase 0: cold run, DEFAULT solver -> both proofs pass"
echo "=================================================================="
if ! run_proofs; then
  fail "phase0-cold" "cold default-solver run failed; expected REFUTED-as-expected + VERIFIED"
fi
echo "[phase0-cold] OK: text proofs are honest under the default solver."

KISSAT="$(locate_kissat)"
if [ -z "$KISSAT" ]; then
  fail "phase0-cold" "no bundled kissat found under ~/.cache/bmc4j/engine after the engine ran (the fast solver must be bundled on linux-x64)"
fi
echo "[phase0-cold] bundled fast solver located: $KISSAT"

echo "=================================================================="
echo " Phase 1: GUARD — request the fast solver on TEXT proofs -> FAIL LOUD"
echo "=================================================================="
# Global external-SAT request (lowest precedence) so BOTH proofs are asked to use the fast solver.
# These are text proofs, so the safe-by-default guard must fail loud; the build must go RED, and NO
# proof may report VERIFIED on the fast solver (the soundness invariant).
if run_proofs "-Dbmc.externalSat=${KISSAT}"; then
  fail "phase1-guard" "SOUNDNESS BUG: requesting the fast solver on text proofs PASSED — the guard did not fail loud"
fi
# It failed as required. Verify it failed via the GUARD (plain-language text message), not some other way,
# and that NEITHER text proof was reported VERIFIED on the fast solver.
if ! grep -qi "text/String" "$LOG"; then
  fail "phase1-guard" "the run failed but NOT via the plain-language text-guard message (expected 'text/String')"
fi
# The catastrophic case: a text proof reporting VERIFIED while the fast solver was engaged. Must NOT happen.
if grep -E "verifies_only_with_string_reasoning -> VERIFIED" "$LOG" | grep -vq "cached"; then
  fail "phase1-guard" "SOUNDNESS BUG: a text proof reported VERIFIED with the fast solver requested"
fi
echo "[phase1-guard] OK: the guard failed loud in plain language; no false VERIFIED on a text proof."

echo "=================================================================="
echo " Phase 2: PROBE (Step 0) — expert unsafe override -> what does jbmc do refinement-off?"
echo "=================================================================="
# Turn the guard OFF via the expert unsafe override so the proofs ACTUALLY run on the fast solver with
# String reasoning off. This is the Step-0 experiment: observe jbmc's real behaviour. DIAGNOSTIC ONLY —
# we record the finding and never fail the build on it.
run_proofs "-Dbmc.externalSat=${KISSAT}" "-Dbmc.externalSatUnsafeTextOverride=true" || true
echo "----- probe output (refinement-off on text proofs) -----"
cat "$LOG"
REFUTABLE_LINE="$(grep -E "refutable_only_through_string_length -> " "$LOG" | tail -n1 || true)"
VERIFY_LINE="$(grep -E "verifies_only_with_string_reasoning -> " "$LOG" | tail -n1 || true)"
note "STEP-0 FINDING (jbmc 6.9.0 + fast solver, String reasoning OFF, on TEXT proofs):"
note "  (a) refutable-only-via-string-length: ${REFUTABLE_LINE:-<no verdict line captured>}"
note "  (b) verifies-only-with-string-reasoning: ${VERIFY_LINE:-<no verdict line captured>}"
if echo "$VERIFY_LINE" | grep -q "VERIFIED"; then
  note "  => jbmc DID verify (b) refinement-off: there IS a real hole. The static guard is LOAD-BEARING."
elif echo "$REFUTABLE_LINE" | grep -qE "REFUTED|as expected"; then
  note "  => jbmc still refuted (a) refinement-off: it appears to self-protect on string constraints;"
  note "     the guard is then defense-in-depth + UX. (See the full probe output above.)"
else
  note "  => jbmc was undecided/other refinement-off: the guard prevents relying on an unsound fast pass."
fi

echo "=================================================================="
echo " Phase 3: OPT-OUT fallback -> text proofs fall back to the SOUND default solver, GREEN"
echo "=================================================================="
if ! run_proofs "-Dbmc.externalSat=${KISSAT}" "-Dbmc.externalSatStringFallback=true"; then
  fail "phase3-fallback" "with the opt-out, text proofs should fall back to the default solver and PASS"
fi
if ! grep -qi "default solver" "$LOG"; then
  fail "phase3-fallback" "expected a plain-language fall-back note mentioning the default solver"
fi
echo "[phase3-fallback] OK: text proofs ran sound on the default solver (no speedup), build GREEN."

echo "=================================================================="
echo " external-sat soundness smoke: ALL PHASES PASSED"
echo "=================================================================="
echo -e "$SUMMARY"
