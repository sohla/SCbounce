# Personality Authoring — Ingredients, Recipes, Phrasebook

Reference doc for composing a COTF personality from a prose description.
Companion to `concert_p_files.md` (which holds the recipes) and the
existing `personalities/cotf_*.sc` files (which hold the working
examples).

**How to use this doc:** the user describes a personality in prose;
you (Claude) resolve each phrase against §4 (Phrasebook), pull the
matching recipes/ingredients from §2 and §3, and compose the file
from `personalities/_TEMPLATE_authored.sc` as skeleton. If a phrase
has no phrasebook entry, ask the user to clarify AND flag the gap so
we can extend the vocabulary (§6).

---

## 1. Workflow

1. **User writes a prose spec.** Any level of detail. Convention:
   states first ("in idle / tuning / piece / curtain"), gestures
   second ("accel threshold", "rotation drives"), character third
   ("low volume", "wanders around A5").
2. **Claude parses.** For each state, identify:
   - Sound engine (§2 A–E) — usually implied by the sample library
     mentioned, or by the phrase "sample" / "synth" / "granular".
   - Trigger strategy (§3.1) — accel-threshold / continuous Pdef /
     paused-Pdef + one-shots / layered hybrid.
   - Pitch source (§3.2) — fixed midi ("at A5") / voice-pool / tilt-
     driven octave.
   - Amp curve (§3.4 palette) — "quiet" / "expressive" / etc.
   - Rhythm control (§3.3) — fixed dur / gesture-driven / pattern-
     locked.
3. **Compose.** Start from `_TEMPLATE_authored.sc`, delete the slots
   you don't need, fill in the ones you do from the resolved
   recipes. Curve constants from §3.4 palette by default; tune later.
4. **Flag gaps.** If any prose phrase didn't resolve, list them for
   the user before finalising the file. Some will be missing
   recipes (add to `concert_p_files.md` and §4 here); some will
   just need a clarifying question.
5. **Bench.** Load, cycle states, verify each stated behaviour.
   Iterate on curve constants live (see §7).
6. **Feed vocabulary back.** Any new atom or recipe introduced —
   update `concert_p_files.md` (recipe) and §4 (phrasebook entry).

---

## 2. Ingredients — Sound engines

Each personality picks exactly one engine as its primary voice; §3.1
covers how the engine is *triggered*. Engines are structural — they
determine the shape of `~init`, `~deinit`, and per-state control.

| ID | Engine | Reference | When to use |
|---|---|---|---|
| **A** | Long-lived Synth (drone) | `cotf_simple1.sc:37`, `cotf_simple2.sc:47` | Continuous sound, sensor-modulated params. No note events. |
| **B** | Pdef + per-event sampler | `cotf_harp1.sc:130`, `cotf_celesta1.sc:117`, `cotf_dulcimer1.sc:182`, `cotf_marimba1.sc`, `cotf_marimba2.sc` | Rhythmic sample-based melody / chord line. Buffer library required. |
| **C** | Pdef + per-event synth voice | `cotf_simple3.sc` | Rhythmic synthesized notes. No sample library. |
| **D** | Paused-Pdef + accel one-shots | `cotf_celesta1.sc` (`\tuning`) | Isolated gestural hits; no running pattern. Requires §15 wiring. |
| **E** | Layered running-Pdef + one-shots | (new, `concert_p_files.md` §20) | Rhythmic backbone + gestural accents on top. Same group. |

An engine can behave differently per state — e.g. celesta1 is engine
B in idle/piece/curtain and engine D in tuning. That's a state-level
override; the base structure is still B.

---

## 3. Ingredients — Building blocks

### 3.1 Gesture sources & trigger strategies

Filtered model fields (declared in `~init` filter tuning block —
see `concert_p_files.md` §9 for typical attack/decay values):

| Source | Type | Range | Best for |
|---|---|---|---|
| `m.accelMassFiltered` | scalar | ~0..2.5 | shake / hit intensity |
| `m.rrateMassFiltered` | scalar | ~0..1.5 | rotation magnitude (all axes) |
| `d.sensors.gyroEvent.x / pi` | scalar | ~-1..1 | roll (left/right tilt) |
| `d.sensors.gyroEvent.y / pi.half` | scalar | ~-1..1 | pitch (up/down tilt) |
| `d.sensors.gyroEvent.z / pi` | scalar | ~-1..1 | yaw (spin) |
| `d.sensors.accelEvent.{x,y,z}` | scalar | raw m/s² | axis-specific hit direction |

Trigger strategies:

| Strategy | Recipe | Use |
|---|---|---|
| **Accel-threshold one-shot** | `concert_p_files.md` §15 | "accel triggers a note", "shake to fire" |
| **Continuous Pdef** | standard (`cotf_harp1.sc:143`) | "runs a pattern" |
| **Paused-Pdef + one-shots** | §15 | "isolated hits, no running pattern" |
| **Layered running-Pdef + one-shots** | §20 | "pattern plays AND gesture triggers hits" |
| **Beat-locked pitch change** | `~onBeat` / `~onHalf` (§4) | "pitch follows the beat" |
| **Section-driven amp shift** | `~onSection` (`cotf_dulcimer1.sc:365`) | "quieter in intro, fuller in dev" |

Throttle for one-shots: `if (TempoClock.beats > (lastTime + gap)) { fire; lastTime = TempoClock.beats }`. Typical gap: 0.2–1.0 s.

### 3.2 Pitch strategies

| Strategy | Recipe | Prose phrase |
|---|---|---|
| **Voice-pool wrap-to-pc** | `Pfunc { ~scoreVoicePool.choose.wrap(0,11).asInteger }` (harp1:137) | "pool pitch", "score-driven", unspecified |
| **Fixed MIDI** | `note: 0, root: 9, octave: 6` = A5 | "at A5", "on middle C" (60), etc. |
| **Tilt-driven octave** | `(gyroY / pi.half).lincurve(-1,1,LO,HI,1).asInteger` (harp1:265) | "tilt controls register", "up/down changes octave" |
| **Random octave choose** | `[3,4,5,6].choose` (harp1:225) | "wandering register", "sparkles at different heights" |
| **Pool-offset-from-top** | `pool.wrapAt(pool.size - 1 - offset)` (dulcimer1:145) | "melody at top of pool with dips" |
| **Octave-fold to range** | `while({ picked > hi }, { picked = picked - 12 })` (dulcimer1:146) — §16 | "stays in the instrument's sweet spot" |
| **`\ptch` bend (tuning)** | `.linlin(0, 1, 0.7, 1.0)` ramp — §16 | "bends into pitch", "slides in over 20s" |
| **Pitch wander around target** | §18 | "wanders around A5 and rests", "detunes and settles" |
| **Odd-MIDI detune** | `if (~note.odd) { … rate = 1.midiratio }` (harp1:117) | (transparent — auto-handled for even-sampled libraries) |
| **findClosestSample** | §16 | (transparent — auto-handled for sparse/diatonic libraries) |

Standard baseMidi = 60 (C4). Named pitches:
- C4 = 60 = root 0, octave 5
- A4 = 69 = root 9, octave 5
- A5 = 81 = root 9, octave 6
- Middle-C convention: SC's `~octave` default is 5, offset from
  midi 60. `(9 + 6*12) = 81` = A5 works in the standard event handler.

### 3.3 Rhythm strategies

| Strategy | Recipe | Prose phrase |
|---|---|---|
| **Fixed dur per state** | `Pdef(m.ptn).set(\dur, N)` (harp1:224) | (implicit — no rhythm phrase given) |
| **State-set dur** | `Pdef.set(\dur, if(amp > -30) { 0.5 } { 1.0 })` (harp1:270) | "faster when loud" |
| **Pattern-driven** | Pfunc reads a fixed array (dulcimer1:192) | "melody follows a set contour" |
| **Rotation-driven subdivision** | §19 | "rotation controls speed", "more rotation, smaller subdivision" |
| **Beat-locked one-shots** | `~onBeat` fires a synth (simple1:132) | "each beat plays a note" |
| **Throttled one-shots** | `TempoClock.beats > lastTime + gap` (celesta1:254) | "isolated hits", "not constant" |

### 3.4 Amp curves — palette (§17)

| Name | Curve | Peak | Prose |
|---|---|---|---|
| `whisper` | `.lincurve(0, 2.0, -90, -35, -4)` | -35 dB | "very quiet", "almost silent" |
| `low` | `.lincurve(0, 2.0, -70, -25, -4)` | -25 dB | "quiet", "low volume" |
| `mid` | `.lincurve(0, 2.0, -70, -15, -2)` | -15 dB | "normal", (unspecified) |
| `expressive` | `.lincurve(0, 1.5, -70, -8, -1)` | -8 dB | "expressive", "full dynamic" |
| `faded` | `.lincurve(0, 2.0, -80, -30, -4)` | -30 dB | "faded", "receding" |

Multiply by `ctx.loudness.linlin(0, 1, 0.3, 1.0)` for score-driven
dynamic scaling in piece (harp1:267, dulcimer1:326).

### 3.5 Cleanup / lifecycle

Every personality gets these — the template provides defaults; only
override when there's a specific reason.

| Concern | Recipe | Reference |
|---|---|---|
| Dedicated Group | `group = Group.new` in `~init`, `\group, group` in Pbind | `concert_p_files.md` §5 |
| `~onResync` | Stop Pdef, `s.bind { group.freeAll }`, restart Pdef | §6 |
| State-aware `~onResync` | Don't restart Pdef if in paused-Pdef state | §6 |
| Idempotent `~deinit` | `notNil`-guard `group` AND `samplesLib`, fork with s.sync | §5 |
| Reload guard | `if (~roomState == \tuning) { Pdef.pause; tuneTime = TempoClock.beats }` after `~init`'s Pdef.play | §15 |
| `~onRoomState` env scope | Wrap switch in `topEnvironment.use` if branches use `~beatClock` | §7 |
| `\silent` mute | `Pdef.set(\amp, 0)` and/or `synth.set(\amp, 0)` inline in `~onRoomState \silent` | §3 |

---

## 4. Phrasebook

Prose phrase → resolved recipe/ingredient. Extended as new phrasings
appear. Grouped by intent.

### States
| Phrase | Resolves to |
|---|---|
| "in idle" / "during idle" | `~idleNext` state tick + `\idle` branch of `~onRoomState` |
| "in tuning" | `~tuningNext` + `\tuning` branch (usually captures tuneTime) |
| "in piece" / "during the piece" | `~pieceNext` + `\piece` branch |
| "in curtain" / "at the end" | `~curtainNext` + `\curtain` branch |
| "silent" / "muted" | inline `\silent` branch, no tick (§3.5) |

### Triggers
| Phrase | Resolves to |
|---|---|
| "accel threshold triggers a note" | §15 accel one-shot (paused-Pdef in that state) |
| "single note" | one Event.play per trigger, throttled |
| "runs a pattern" / "plays a pattern" | continuous Pdef |
| "pattern AND accel triggers" | §20 layered running-Pdef + one-shots |
| "beat plays a note" | `~onBeat` inline synth |
| "no rhythm" / "just drone" | engine A (long-lived synth) |

### Pitch
| Phrase | Resolves to |
|---|---|
| "at A5" / "around A5" | fixed `note: 0, root: 9, octave: 6` |
| "at middle C" | fixed `note: 0, root: 0, octave: 5` |
| (unspecified pitch) | voice-pool wrap-to-pc (§3.2) |
| "tilt controls octave" | tilt-driven octave (§3.2) |
| "wandering register" / "different heights" | random octave choose (§3.2) |
| "bends into pitch" / "slides in tune" | `\ptch` ramp (§16) — 20s default |
| "wanders around X and rests" | pitch wander around target (§18) |
| "in the instrument's range" | octave-fold to range (§16) |

### Rhythm
| Phrase | Resolves to |
|---|---|
| "greater rotation → smaller subdivision" | rotation-density (§19) |
| "faster when loud" | state-set dur on amp threshold (harp1:270) |
| "isolated hits" | throttled one-shots (§3.3) |
| "constant" / "continuous" | Pdef with fixed dur or gesture-driven dur |
| "follows the beat" | `~onBeat` sets `\dur` or fires directly |
| "on every downbeat" | `~onBar` handler |

### Amp
| Phrase | Resolves to |
|---|---|
| "very quiet" / "almost silent" | `whisper` curve (§17) |
| "quiet" / "low volume" | `low` curve |
| "normal" / (unspecified) | `mid` curve |
| "expressive" / "full dynamic" | `expressive` curve |
| "faded" / "receding" | `faded` curve |
| "gets louder with motion" | curve driven by `m.accelMassFiltered` (default) |
| "gets louder with rotation" | curve driven by `m.rrateMassFiltered` |
| "score follows loudness" | multiply by `ctx.loudness.linlin(0,1,0.3,1.0)` |

### Sound engines
| Phrase | Resolves to |
|---|---|
| "harp" / "harp samples" | engine B, folder = `~/Music/cotf_samples/harp` |
| "celesta" / "bells" | engine B, folder = `~/Music/cotf_samples/Celesta_ES_mf` |
| "dulcimer" | engine B, folder = `~/Music/cotf_samples/Celtic Hammered Dulcimer`, filter `Dlcmr-hrd` |
| "marimba" | engine B, folder = marimba library |
| "warm drone" / "synth pad" | engine A, saw + sin (simple1/2 SynthDef) |
| "granular" | (not yet integrated as engine — see `/synths/harp_grain_*.scd` for standalone experiments; §6 lists as future extension) |

### Structural
| Phrase | Resolves to |
|---|---|
| "instrument name" (front matter) | pick from Lumivox / Gravitone / Velaphone / Aetherharp / Cellaris |
| "reload safely mid-state" | reload guard in `~init` (§3.5, §15) |

---

## 5. Recipes cross-index

Full recipes live in `concert_p_files.md`. Summary map:

- **§15** Accel-triggered one-shots (paused-Pdef state)
- **§16** Pitch / sample handling recipes (\ptch, findClosestSample, octave-fold, array-aware customEvent)
- **§17** Amp-curve palette
- **§18** Pitch wander around target
- **§19** Rotation-magnitude-driven subdivision
- **§20** Layered running-Pdef + gestural one-shots
- **§23** Energy accumulator + tier switching (\low / \med / \high)
- **§24** Derived gesture primitives (strike / stillness / direction / hold / reversal)
- **§25** Multi-synthdef layering (voice A + voice B)
- **§26** Hidden-layer / reveal patterns

Foundational patterns (also in `concert_p_files.md`):

- **§1** Front matter (six keys)
- **§2** Architecture (long-lived vs Pdef)
- **§3** State system
- **§4** Hook signatures
- **§5** Dedicated Group
- **§6** `~onResync`
- **§7** Environment scoping
- **§8** Timing & latency
- **§9** Gesture mapping conventions
- **§10** Pitch conventions
- **§11** Score features on ctx
- **§12** Common pitfalls
- **§13** Testing checklist

---

## 6. Growing the vocabulary

When composing a personality reveals a phrase or pattern this doc
doesn't cover:

1. **Small tweak** (curve constant, threshold value) — inline in the
   file, no doc changes needed. Personalities are the source of
   truth for numbers.
2. **New named recipe** (a pattern that could recur) — add to
   `concert_p_files.md` as a new numbered section, add phrasebook
   entry in §4 here, add ingredient row in §3 here. Reference the
   file:line where it first appeared.
3. **New atom** (a gesture source, sound engine, or lifecycle
   pattern this doc doesn't list) — extend the relevant §2 or §3
   table here.

Rule of thumb: if I hesitated for more than a moment translating
the prose, the missing piece deserves a doc entry.

### Known gaps / future extensions

- **Percussion & drums** — `cotf_drums[1-4].sc` is deliberately
  outside this doc's scope. When the drums vocabulary is added
  (probably after the melodic personalities are stable), it needs
  its own catalog sweep: trigger source (hit strength → velocity),
  sample-set organisation (kick/snare/hat separation), pattern
  languages, fill logic.
- **Granular sound engine** — `/synths/harp_grain_*.scd` files are
  standalone experiments. Not yet wrapped as an engine option
  for a personality. Would extend §2 with an engine F (per-event
  granular) once the mono-buffer path is solid.
- **MIDI / OSC input triggers** — currently everything derives from
  the AirStick IMU. External triggers (foot pedal, keyboard,
  network events) would extend §3.1.
- **Cross-personality coordination** — e.g. one personality
  responds to another's beat hooks. Not currently a pattern.

---

## 7. Live-iterating curve constants

Amp / density / subdivision endpoints are best tuned live at the
bench, not guessed in the file. Workflow:

1. Load the personality.
2. Cycle to the state you want to tune.
3. In the composer's post window, `Pdef(m.ptn).set(\dur, 0.5)` or
   similar to try a value. Multiple values via successive `.set`s.
4. When the number feels right, edit it into the personality file.
5. Reload (or wait for the next state entry).

For the amp palette (§17), the constants are shared — changing
`low` there propagates to every personality that resolves "quiet" to
`low`. Change the palette when the *default character* of "quiet"
should shift; override in-file when a specific personality wants a
one-off.

---

## 8. Character reference implementations

Three worked-example personalities demonstrating the depth vocabulary
(energy tiers + hidden layers) end-to-end. Each is a compact reference
for a specific architectural + mechanic combination — when authoring
a new character in a similar register, open the closest reference and
copy its skeleton.

### `personalities/cotf_whisperer1.sc` — long-lived multi-synthdef
**Architecture:** engine A + multi-synthdef (§25) — three long-lived
`\whisperVoice` synths (root / fifth / octave) held from `~init`, all
in the personality Group. No Pdef.

**Depth mechanic:** energy accumulator + tier switching (§23) drives
per-voice amp. `\low` = root only (single sine drone). `\med` = root
+ fifth. `\high` = full triad + slow LPF opening.

**Hidden reveal:** stillness > 10 s after non-`\low` activity fires a
one-shot ghost echo of the last chord, fading over 6 s (§26).

**Copy this when:** the personality is drone-like, needs multiple
tuned voices held simultaneously, and rewards sustained gesture with
harmonic reveal.

### `personalities/cotf_percussionist1.sc` — Pdef + tier pattern swap
**Architecture:** engine B (Pdef + per-event sampler) + multi-hit
custom event type (§20 pattern). Uses the orchkit sample library.

**Depth mechanic:** energy tier (§23) selects the running pattern —
`\low` = pulse only, `\med` = full groove, `\high` = double-time with
snare fills inserted every 4 bars via `~onBar` counter.

**Hidden reveal:** `~onSection` silently rotates the kit voicing
(§26 section-swap) — intro / A / dev / B / recap / coda each carry a
different set of orchkit samples voicing the six roles. Not
announced; only obvious across back-to-back section comparison.

**Copy this when:** the personality is rhythmic, has a static grid
that grows in complexity with energy, and could benefit from
section-driven timbral rotation.

### `personalities/cotf_cascade1.sc` — event one-shots + direction detection
**Architecture:** no Pdef at all — every note fires from
`SystemClock.sched`ed `Event.play` calls triggered by direction
events in `~next` (§15 event-idiom taken further).

**Depth mechanic:** direction-of-motion derived gesture (§24). Each
transition of gyroY's derivative to a non-still direction fires an
arpeggio in that direction. Energy tier (§23) sets arpeggio length
(1 / 3 / 5 notes) and note tail length.

**Hidden reveal:** rapid reversal within 500 ms — a "shake in place"
gesture — cancels the second arpeggio and instead fires a chord (all
tier's notes simultaneously) at higher amp (§26 gesture-sequence
reveal).

**Copy this when:** the personality is gestural / expressive, notes
are one-shot rather than pattern-driven, and gesture direction /
motion character should shape musical output.

### What each reference contributes to the vocabulary

- **whisperer** — three-voice unison held long-lived; per-voice amp
  from tier; stillness reveal idiom.
- **percussionist** — tier-driven pattern-key swap; section-driven
  layer-key swap; bar-counter fill insertion.
- **cascade** — direction edge-detection; SystemClock-scheduled
  arpeggio spawning; reversal-detection for chord reveal.

Between them the three files touch every recipe in `concert_p_files.md`
§5–§26 at least once. Future personalities should be able to pick a
reference file whose architecture matches and adapt from there.
