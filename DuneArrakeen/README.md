# Original Arrakeen world installer and verifier

Paper 1.21.8 / Java 21 companion for the original `tools/build-arrakeen.py` block plan. The installer never modifies a world automatically. On enable it validates its bounded, embedded plan and exposes a **console-only** command. It does not grant permissions, implement spaceships or replace account/economy services.

Build with `gradle build`. Regenerate the embedded resource from the repository root with:

```text
python tools/build-arrakeen.py --output output/arrakeen --runtime-plan DuneArrakeen/src/main/resources/plan.bin.gz
```

Console `arrakeen status` shows the exact loaded Arrakis UUID, resource SHA-256 and installation checkpoint. Installation requires `arrakeen install <UUID> <SHA256>` using both exact values. It is destructive inside the explicit local footprint, so run it **only in a fresh, isolated world after retaining backups**, as done for the 7 November rollout. There is no player permission that enables this command. Saved completion state refuses replay; a different world UUID refuses reuse. It writes bounded batches and saves progress, then sets spawn to `(0,72,0)`, verifies 23 destinations and saves the world.

The footprint is X `-294..121`, Z `-120..135`, plan Y `68..155`; existing terrain above the plan is cleared, and gaps below its foundation are supported with sandstone. No other world is selected. `arrakeen verify` checks the installed destinations; `arrakeen sietch <x> <z>` deliberately generates/loads a sietch footprint for a console audit of its reservoir and extra rooms. It is an operator QA tool, not a player teleport feature.

The live installer has `complete: true`. The WorldGuard region `arrakeen` provides city protection separately. See `docs/ARRAKEEN-STARPORT.md` for the completed reset, backup and limits.
