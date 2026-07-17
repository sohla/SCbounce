# AirConcert — end-to-end workflow

How the three repos hang together to turn a single orchestral recording into a
score-aware AirKit performance.

```
┌───────────────────┐        ┌───────────────────┐        ┌───────────────────┐
│   PyTrackMusic    │        │     beatsLab      │        │      AirKit       │
│ (MIDI → metadata) │        │ (audio → beats +  │        │ (SC performance)  │
│                   │        │  spectral meta)   │        │                   │
└─────────┬─────────┘        └─────────┬─────────┘        └─────────┬─────────┘
          │                            │                            │
          │ tick_index_N.json          │ beats_2_mul4.txt           │ conductorController.scd
          │ (harmony / chords /        │ beats_2_mul4_meta.json     │ reads all three, plus
          │  fitted_notes / sections)  │ (per-beat log-power bands, │ the source .wav
          │                            │  MFCC, chroma, loudness)   │
          ▼                            ▼                            ▼
    data/score/                    out/                       personalities/*
```

Two independent producers feed one consumer. **PyTrackMusic** derives
harmonic/structural metadata from the MIDI score. **beatsLab** derives beat
timings and spectral occupancy from the audio recording. **AirKit** slaves a
`TempoClock` to the beat stream, looks up harmony per beat, and hands the
resulting context to the live personalities.

---

## Repo locations

| Repo | Path |
|---|---|
| PyTrackMusic | `~/Develop/Python/Projects/PyTrackMusic` |
| beatsLab | `~/Develop/Python/Projects/beatsLab` |
| AirKit | `~/Develop/Supercollider/Projects/AirKit` (this repo) |

---

## The three files AirKit consumes

Set at `code3.0/conductorController.scd:36-39`:

```supercollider
~beatsPath = root +/+ "out"  +/+ "Beethoven4_4_MSO_beats_2_mul4.txt";
~musicPath = root +/+ "data" +/+ "audio" +/+ "Beethoven4_4_MSO.wav";
~scorePath = root +/+ "data" +/+ "score" +/+ "tick_index_2.json";
~metaPath  = root +/+ "out"  +/+ "Beethoven4_4_MSO_beats_2_mul4_meta.json";
```

| File | Producer | Purpose | Loaded by |
|---|---|---|---|
| `out/Beethoven4_4_MSO_beats_2_mul4.txt` | beatsLab | Per-beat timing grid (Audacity label format). 8 events per bar. Drives the `TempoClock` via `/beat` OSC and the phase-lock filter. | `code3.0/lib/beatfile.scd` |
| `data/audio/Beethoven4_4_MSO.wav` | (source recording) | The actual orchestral playback. Cued via `DiskIn`. | `\playStream` SynthDef |
| `data/score/tick_index_2.json` | PyTrackMusic | Harmonic tree: sections, phrases, chords, `fitted_notes`, `active_scale`, `key`, `mode`, `tension`, `density` etc., one entry per MIDI tick change. Looked up via `~paramsAtBeat.(i)`. | `code3.0/lib/scorefile.scd` |
| `out/Beethoven4_4_MSO_beats_2_mul4_meta.json` | beatsLab | Per-beat audio spectral occupancy: `bands`, `openness`, `dom_band`, `loudness`, `flatness`, `density`, `centroid_hz`, `mfcc[13]`, `chroma[12]`. Looked up via `~metaAtBeat.(i)`. | `code3.0/lib/metafile.scd` |

Both `~paramsAtBeat` and `~metaAtBeat` are read every event by the score-walker
Routine in `conductorController.scd:158-275`, packed into the `ctx` dict, and
dispatched to every enabled device's `~onTick` / `~onBeat` / `~onBar` /
`~onChord` / `~onSection` (etc.) hook.

---

## End-to-end regeneration walkthrough

Do this whenever you want to swap in a different piece. Each step lives in a
different repo and (for the Python steps) a different venv.

### Step 1 — PyTrackMusic: MIDI → `tick_index_N.json`

```bash
cd ~/Develop/Python/Projects/PyTrackMusic
source .venv/bin/activate

# Manual section boundaries recommended for known pieces:
python analyser/analyse.py symphony_7_4__c_cvikl.mid \
    --sections 1,33,97,161,225,321,385,450 --verbose
```

Writes `out/music_metadata_<TS>.json` + `out/tick_index_<TS>.json` and updates
the undated `out/music_metadata.json` / `out/tick_index.json` "latest" copies.

Copy the tick-index into beatsLab and AirKit, choosing a stable numbered name
(`_2` is the current slot):

```bash
cp out/tick_index.json \
   ~/Develop/Python/Projects/beatsLab/data/score/tick_index_2.json
cp out/tick_index.json \
   ~/Develop/Supercollider/Projects/AirKit/data/score/tick_index_2.json
```

### Step 2 — beatsLab: audio → beats file

```bash
cd ~/Develop/Python/Projects/beatsLab
pyenv shell 3.9.19 && . .venv-madmom/bin/activate

# --beats-per-bar pinned to the piece's meter (2 for 2/4, 4 for 4/4, …).
python backends/madmom/madmom_track.py \
    data/audio/Beethoven4_4_MSO.wav \
    --beats-per-bar 2 \
    --out out/Beethoven4_4_MSO_beats_2.txt
deactivate
```

Output is one line per beat, tab-separated `start<TAB>end<TAB>label`, `D` for
downbeat and `b` for other beats.

### Step 3 — beatsLab: densify to 8 events/bar

```bash
. .venv-core/bin/activate
python -m core.interpolate_beats \
    out/Beethoven4_4_MSO_beats_2.txt \
    out/Beethoven4_4_MSO_beats_2_mul4.txt \
    --mul 4
```

`--mul 4` inserts three linearly-interpolated `b` beats between each tracked
beat, so 2 events/bar → 8 events/bar. This is what AirKit's Routine steps
through; the finer grid lets `~scoreEventsPerBeat = 4` subdivisions
(`code3.0/conductorController.scd:21`) drive sub-beat `~onTick` calls without
asking madmom to hallucinate.

### Step 4 — beatsLab: spectral-occupancy meta

```bash
python -m core.audio_meta \
    data/audio/Beethoven4_4_MSO.wav \
    out/Beethoven4_4_MSO_beats_2_mul4.txt \
    out/Beethoven4_4_MSO_beats_2_mul4_meta.json
# Defaults: --window-seconds 1.0
#           --band-edges "20,80,160,320,640,1280,2560,5120,10240"
deactivate
```

One JSON object per beat, per-band log-power percentile-normalised across the
piece. See `beatsLab/core/audio_meta.py` and `beatsLab/README.md §3` for the
full field-by-field spec.

### Step 5 — copy beatsLab outputs into AirKit

```bash
cp out/Beethoven4_4_MSO_beats_2_mul4.txt \
   out/Beethoven4_4_MSO_beats_2_mul4_meta.json \
   ~/Develop/Supercollider/Projects/AirKit/out/
```

The source `.wav` also needs to be in `AirKit/data/audio/` — copy or symlink
it once.

### Step 6 — AirKit: run

Open SuperCollider. Evaluate `code3.0/main.sc`. That loads
`conductorController.scd` (which cues the audio, loads the three data files,
and sets up the beat-slaved `TempoClock`), then `conductorView.scd` for the
GUI. Trigger transport via the view or by OSC (`/airkit/go`, `/airkit/seek`,
`/airkit/beek`, `/airkit/pause`).

---

## Contracts AirKit relies on

Break any of these and the SC side stops working silently or with a cryptic
error.

### Beats file — Audacity label format

Tab-separated, three columns, `start<TAB>end<TAB>label`, times in seconds
relative to `~musicPath`, strictly increasing, UTF-8, `\n` line endings.
`label` is `D` (downbeat) or `b` (other beat). `end == start` for
instantaneous markers.

**Downbeat count and cadence must reflect the audio.** The Routine at
`conductorController.scd:170` increments `barIdx` on every `D` label — if
downbeats drift out of place, the harmony lookup drifts with them.

Single canonical schema lives in `beatsLab/core/beatfile.py`. Do not emit a
variant.

### tick_index — PyTrackMusic contract

Array of dicts, one per MIDI tick change, sorted by `tick`. Every entry must
carry the keys AirKit reads:

```
tick, section_id, phrase_id, bar,
key, mode, chord_root, chord_roman, chord_midi_pitches,
tension, density, loudness,
fitted_notes, fitted_passing_notes, active_scale, active_motives
```

Schema is defined in `PyTrackMusic/SCHEMA.md`. AirKit looks up by beat index
after applying `~scoreAnchorBeat` (see below).

### meta.json — beatsLab contract

Top-level object with `band_edges_hz`, `window_seconds`, `mfcc_n_coeffs`,
`chroma_labels`, `entries[]`. Each entry has: `idx`, `t_start`, `t_end`,
`label`, `loudness`, `bands`, `openness`, `dom_band`, `centroid_hz`,
`flatness`, `density`, `mfcc`, `chroma`. Index space must be **identical** to
the beats file it was generated from — one entry per row of `beats_2_mul4.txt`
in order. `~metaAtBeat.(i)` is a direct array lookup, no anchor.

Schema is defined in `beatsLab/core/audio_meta.py` docstring.

---

## `~scoreAnchorBeat` — the manual alignment knob

`code3.0/conductorController.scd:24` currently sets `~scoreAnchorBeat = 3`.

The beats file and the tick_index are produced by two different systems from
two different sources (audio vs MIDI). Their index spaces are not
automatically aligned — the audio recording may start with an anacrusis, a
conductor's upbeat, or leading silence that the MIDI score doesn't have.
`~scoreAnchorBeat` is a hand-tuned integer offset that shifts the tick_index
lookup so that beat `i` of the audio maps to the correct musical bar of the
score.

Retune it whenever the audio, the tick_index, or the beats file changes.
`sc/sync_plan2_pattern_score_gui.scd` in beatsLab has a GUI-driven workflow
for finding the right value; the AirKit equivalent is a manual edit + reload.

---

## Current state (2026-07-14)

- **Audio and score describe different pieces.** `Beethoven4_4_MSO.wav` is
  Symphony 4 mvt IV, but `tick_index_2.json` was generated by PyTrackMusic
  from `symphony_7_4__c_cvikl.mid` (Symphony 7 mvt IV). The harmony/section
  data therefore does not describe what is actually being heard.
  `~scoreAnchorBeat = 3` picks an offset that "sounds okay" but does not
  represent structural alignment. Regenerating a real Symphony 4/iv tick_index
  from a matching MIDI is the intended next step.
- **Beats file has drifted between beatsLab and AirKit.**
  `AirKit/out/Beethoven4_4_MSO_beats_2_mul4.txt` differs from
  `beatsLab/out/Beethoven4_4_MSO_beats_2_mul4.txt`; the `.bak` next to the
  AirKit copy matches the beatsLab version. Someone has hand-edited beats on
  the AirKit side. Regenerating from beatsLab (Step 5 above) will overwrite
  that edit — copy the current AirKit file aside first if you want to keep
  those adjustments.

---

## Related docs to read

- `PyTrackMusic/CLAUDE.md`, `PyTrackMusic/OVERVIEW.md`, `PyTrackMusic/SCHEMA.md`
- `beatsLab/README.md`, `beatsLab/CLAUDE.md`, `beatsLab/SETUP.md`
- `AirKit/README.md`, `AirKit/code3.0/API.md`
