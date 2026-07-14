# CLAUDE.md — AirKit (branch AirConcert)

AirKit is Steph's live-performance SuperCollider system AND the production
sound engine for Concerts of the Future (COTF, Edinburgh Fringe 2026). Both
parties push directly to this branch. Treat it accordingly. COTF-side pushes
land in **batches, each with a plain-language entry in `COTF-CHANGELOG.md`**
so the other side can catch up at a glance.

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

## Machine setup (what a fresh host needs — every item broke the 2026-07-14 bench)

1. **Quarks:** `Require` (scztt/Require.quark) + `Canvas3D` (supercollider-quarks/Canvas3D) cloned
   into `~/Library/Application Support/SuperCollider/Extensions/`, then recompile. `main.sc`
   errors with "Class not defined" without them.
2. **Samples:** `~/Music/cotf_samples/` (harp, drums, celesta, marimba, dulcimer, …) — gitignored,
   from the composer. Sampler personalities print "Buffer UGen: no buffer data" and play silence
   without it. The `cotf_simple*` personalities need nothing.
3. **Data:** `data/audio/Beethoven4_4_MSO.wav` (COTF hosts need the **48 kHz** resample — the
   Aggregate device is 48k and the conductor pins server SR to the file), `data/score/tick_index_2.json`,
   `out/Beethoven4_4_MSO_beats_2_mul4{.txt,_meta.json}`. COTF archive:
   `/Users/m0/cotf-assets/airkit-data/` on M0.
4. Never double-click a `.sc`/`.scd` file — open the SuperCollider app, then Cmd-O.

## COTF show-profile notes (`code3.0/cotf/`)

- Boot: evaluate `main_cotf.scd` (env `COTF_ROOM=2|3`, default 3). When QLab owns the Beethoven
  audio, set `~conductorAudioEnabled = false` (env `COTF_CONDUCTOR_AUDIO=0`, or evaluate the line
  in IDE sessions).
- **No Cmd-. while the profile is up** — it kills the per-seat monitor synths, every personality
  process loop, and the score walker, with no auto-rebuild. Recovery: `Server.killAll`, reboot
  interpreter, evaluate `main_cotf.scd` once.
- Device port on COTF hosts = router fixed source port + seat − 1 (seat 1 = 9001). The post
  window's "device auto detected : N" prints the index/seat, not the port.
