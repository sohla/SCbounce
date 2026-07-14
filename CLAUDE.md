# CLAUDE.md — AirKit (branch AirConcert)

AirKit is the composer's live-performance SuperCollider system AND the
production sound engine for Concerts of the Future (COTF, Edinburgh Fringe
2026). Both parties push directly to this branch. Treat it accordingly.

## Rules (both humans and Claude sessions)

1. `git pull --ff-only` before starting work and before every push. Never
   force-push, rebase, or rewrite history on AirConcert.
2. Small, focused commits. COTF-side commits are prefixed `cotf:`.
3. Never commit `data/`, `out/`, or audio files (gitignored Beethoven assets;
   COTF archives copies at `/Users/m0/cotf-assets/airkit-data/` on M0).
4. Never discard uncommitted changes found in a checkout — they may be
   someone's live-coding session. Commit or stash them first, or stop and ask.
5. **OSC is the compatibility boundary.** `code3.0/API.md` is the living
   contract — any commit that changes OSC behaviour updates API.md in the
   same commit. Changes are additive only: new addresses rather than changed
   semantics; new args appended with defaults; never repurpose an address.
6. COTF-specific code lives in `code3.0/cotf/` only. Changes to the shared
   controllers (`oscController.scd`, `personalityController.scd`,
   `conductorController.scd`) must be additive, with defaults that preserve
   composer-machine behaviour exactly. `main.sc` and the view files are the
   composer's — change only by explicit agreement.

## Layout pointers

- `code3.0/main.sc` — composer's entry point (GUI). COTF production entry
  point: `code3.0/cotf/main_cotf.scd` (headless, env-driven; see its header).
- `code3.0/API.md` — THE OSC contract. Read before touching any OSC.
- `personalities/cotf_*.sc` — the COTF show personalities.
- `lists/list_cotf.sc` — the COTF personality list.
- COTF integration spec lives in the COTF repo:
  `docs/superpowers/specs/2026-07-14-airkit-integration-design.md`.
