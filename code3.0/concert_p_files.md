# Concert Personality Files — Rules & Patterns

Notes on building AirKit personality (`.sc`) files for concert use.
Distilled from the cotf_simple1/2/3 iterations. **When authoring a new
personality, skim this doc first; when you learn something new, add
it here.**

Read alongside `code3.0/API.md` (OSC surface + personality environment
contract).

---

## 1. Front matter

Every personality starts with a YAML-ish block comment. Six keys,
one line each. Reference: `personalities/cotf_simple1.sc`.

```
/*
gestures:    [beat, shake, tilt]           # open vocab; append as needed
description: one-line technical summary    # what it *is*
sound:       one-line character summary    # what you *hear*
pitch:       where notes come from
rhythm:      rhythmic behaviour
instruments: [Lumivox | Gravitone | Velaphone | Aetherharp | Cellaris]
*/
```

Fixed vocabulary for `instruments`: the five COTF instruments only.
Everything else is open. Multi-value fields go in `[ ]`.

---

## 2. Architecture — pick one of two

### A. Long-lived synth (drone / continuous)
- One Synth created in `~init`, released in `~deinit`.
- `sustain: 1.0`, `attack: ~0.5`, `release: ~1.0` in `~init`'s
  `Synth.new` args so it holds level and releases cleanly on gate=0.
- Modulated via `synth.set(\amp, …)` / `synth.set(\freq, …)` in hooks.
- No Group needed (one synth, self-managed).
- Example: `cotf_simple1.sc`.

### B. Pdef pattern (rhythmic / per-note)
- Pdef built in `~init`, plays on `~beatClock`.
- Each event spawns a new `\simple` synth with `Done.freeSelf`.
- **Requires** a dedicated `Group` (see §5).
- **Requires** `~onResync` (see §6).
- Modulated via `Pdef(m.ptn).set(\amp, …)` in hooks.
- Example: `cotf_simple3.sc`.

Mixed forms (long-lived + Pdef) exist but complicate cleanup — avoid
unless you have a specific reason.

---

## 3. State system reference

Five states — set via `/airkit/state <name>`, read via `~roomState` /
`ctx.state`. Details in `API.md`.

| state | transport | tick hook | typical use |
|---|---|---|---|
| `\idle` | stopped | `~idleNext` | free play; audience arriving |
| `\tuning` | stopped | `~tuningNext` | tuning textures; pool pinned to `[69]` |
| `\piece` | live | `~pieceNext` | full expression, follows score |
| `\curtain` | free | `~curtainNext` | musical outro (fade / resolve) |
| `\silent` | free | **none** | hard mute; one-shot amp=0 in `~onRoomState` |

`\silent` is deliberately without a tick hook — handled once in
`~onRoomState \silent`. Everything else (state, pitch, pattern
position) keeps running so re-entry to `\piece` is beat-locked.

---

## 4. Hook signatures & when they fire

| hook | signature | fires | notes |
|---|---|---|---|
| `~init` | `{ }` | once on load | runs inside `d.env.use`; wrap in `topEnvironment.use{}` to reach `~beatClock` / `~scoreVoicePool` etc. |
| `~deinit` | `{ }` | once on unload | cleanup: `synth.set(\gate,0)` OR `Pdef.remove` + group teardown |
| `~next` | `{|d|}` | ~30 Hz always | gesture pre-processing; runs regardless of state |
| `~idleNext` `~tuningNext` `~pieceNext` `~curtainNext` | `{|d, ctx|}` | ~30 Hz per state | `ctx` = trimmed state snapshot (no change flags) |
| `~onTick` | `{|ctx|}` | every beats.txt event | ~4/beat in `_mul4` |
| `~onHalf` | `{|ctx|}` | half-bar change | |
| `~onBeat` | `{|ctx|}` | every true musical beat | 1/4 note in `_mul4` |
| `~onBar` | `{|ctx|}` | every downbeat | |
| `~onPhrase` `~onSection` `~onChord` `~onKey` `~onScale` | `{|ctx|}` | when named field changes | change-triggered; fires once per change |
| `~onRoomState` | `{|ctx|}` | state change | ctx has `state`, `prevState`, `stateChanged` |
| `~onResync` | `{|idx|}` | beat-clock re-anchor (seek etc.) | **install inside `topEnvironment.use{}` in `~init`** |

**Second arg to state ticks is `ctx`** — a snapshot of score state
(state, voicePool, loudness/tension/brightness/density/register,
sectionId, phraseId, chord, key, scale, p, m). No change flags —
those live on the beat-hook ctx. Cached each beat; nil-safe defaults
before the first beat.

---

## 5. Dedicated Group (Pdef personalities)

Any personality that spawns per-note synths (Pdef, direct
`Synth.new` in hooks, etc.) **must** put them in a dedicated Group.
Enables clean cleanup on seek and unload.

```supercollider
var group;   // top-level file scope

~init = ~init <> {
    topEnvironment.use{
        group = Group.new;

        Pdef(m.ptn,
            Pbind(
                \instrument, \simple,
                \out, ob,
                \group, group,    // route every event's synth into our group
                ...
            );
        );
        Pdef(m.ptn).play(~beatClock, quant: ~scoreBeatsPerBar * ~scoreEventsPerBeat);
    };
};

~deinit = ~deinit <> {
    Pdef(m.ptn).remove;
    if (group.notNil) {
        s.bind { group.freeAll };
        group.free;
        group = nil;
    };
};
```

`group.freeAll` frees only that group's children — safe, doesn't
affect other personalities' groups.

---

## 6. `~onResync` for Pdef personalities

**Non-negotiable for Pdef personalities.** On seek, `~beatClock`
re-anchors. Without `~onResync`:

- TempoClock dumps every event scheduled between old and new beat —
  hundreds of `Synth.new` fire in a burst → **"too many nodes"**.
- Synths in flight from before the seek keep their `gate=1` — no
  scheduled release from the cancelled Pdef → **stuck notes**.
- PLL never converges, log fills with re-anchor lines.

Install inside `~init`'s `topEnvironment.use{}` so it lives in
`topEnvironment` where `OSCdef(\beatSync)` looks for it:

```supercollider
~onResync = { |idx|
    Pdef(m.ptn).stop;
    s.bind { group.freeAll };   // MUST be s.bind — see below
    Pdef(m.ptn).play(~beatClock, quant: ~scoreBeatsPerBar * ~scoreEventsPerBeat);
};
```

**Why `s.bind` on freeAll:** Pbind sends `/s_new` bundled with
`s.latency` (~200 ms). Without `s.bind`, our `/g_freeAll` arrives at
the server *immediately*, but a `/s_new` still in flight lands 200 ms
later into the emptied group — that synth never gets a release, stuck
at sustain forever. `s.bind` puts our `/g_freeAll` on the same
latency timeline so client-side send order (event's `/s_new` first,
then our `/g_freeAll`) is preserved server-side.

Long-lived-synth personalities don't need this: they don't create
multiple in-flight synths.

---

## 7. Environment scoping

Personality file bodies run inside `d.env.use { interpret(str) }`
(see `personalityController.scd:184`). Two consequences:

- Top-level `~foo = X` assignments go to **d.env**, not
  topEnvironment.
- Inside `~init`, `~deinit`, and hook bodies, `~beatClock`,
  `~scoreVoicePool`, `~scoreBeatsPerBar` etc. resolve to `d.env` and
  **read as nil** unless you wrap in `topEnvironment.use{}`.

Practical rules:

- Personality-local vars go at file top as `var` declarations
  (`var m, ob, synth, group, lastTime, tuneTime;`).
- Hook installation (`~onResync = { … }`) goes inside `~init`'s
  `topEnvironment.use{}` so the controller can find it.
- Inside hook bodies, prefer `ctx` fields over `topEnvironment`
  lookups — ctx is passed by value and always accessible.
- Wrap `Pdef.play(~beatClock, …)` etc. in `topEnvironment.use{}` —
  `~beatClock` isn't in d.env.

`Pdef(m.ptn).set(\x, y)` is a class message and works anywhere;
doesn't need `topEnvironment`.

---

## 8. Timing & latency

- **Beat hooks** fire from the score Routine on SystemClock —
  sample-accurate against `~beats[i]` timestamps. Precise.
- **State ticks** fire from `procRout` on AppClock at ~30 Hz —
  good enough for gesture, not for audio-locked events.
- **`~next`** — same as state ticks (AppClock, ~30 Hz).
- Any `Synth.new` / `synth.set` that must lock to audio (e.g., beat-
  driven pitch changes) should be wrapped in `s.bind` so it inherits
  `s.latency` and lands in phase with the conductor's DiskIn stream.
- Gesture-driven params (amp, filter cutoff) can skip `s.bind` — feel
  is more important than latency-alignment.

---

## 9. Gesture mapping conventions

Standard model fields (declared in `~init` block):

```
m.accelMassFilteredAttack  = 0.9 .. 0.99   # slow rise = smooth amp
m.accelMassFilteredDecay   = 0.2 .. 0.7
m.rrateMassFilteredAttack  = 0.95 .. 0.999
m.rrateMassFilteredDecay   = 0.3 .. 0.6
m.gyroFilteredAttack       = 0.7           # generally ok
m.gyroFilteredDecay        = 0.7
```

Common mappings:

- `m.accelMassFiltered.lincurve(0, 2.5, -90, -10, -1)` — amp from
  filtered acceleration, dB output, exponential curve so quiet is
  quiet.
- `(m.accelMass + m.rrateMass).lincurve(0, 2.0, -90, -10, -1)` —
  hit or shake amp.
- `((d.sensors.gyroEvent.x / pi).fold(-0.5, 0.5) * 2).lincurve(-1, 1, 500, 12000, 3)` — filter cutoff from roll tilt.
- `d.sensors.gyroEvent.y / pi.half` — pitch-tilt normalised to
  `[-1, 1]`.

Amp values are dB; convert with `.dbamp` before passing to a synth
param.

---

## 10. Pitch conventions

- `baseMidi = 60` (C4) is the project convention. **Score data is
  C4-based** even though the current piece is in A major — that's a
  known compromise, don't try to "fix" it per-personality.
- `ctx.voicePool.choose.asInteger.wrap(0, 11)` — random pitch class
  from current score voice pool.
- `(pitch + (12 * octave)).midicps` — MIDI-to-Hz.
- `.midicps` uses `A4 = 69 = 440 Hz` (standard MIDI); `60.midicps ≈
  261.63 Hz` (C4).
- Wrap voicePool access with a nil-guard if it might be empty
  (bridge in `conductorController.scd` already falls back to `[69]`).

---

## 11. Score features on ctx

Both beat-hook `ctx` and state-tick `ctx` carry these as floats,
extracted from `~paramsAtBeat.(i)`:

- `ctx.loudness` (0..1)
- `ctx.tension` (0..1)
- `ctx.brightness` (0..1)
- `ctx.density` (0..1)
- `ctx.register` (0..1)

Plus `ctx.sectionId`, `ctx.phraseId`, `ctx.chord`, `ctx.key`,
`ctx.scale`, `ctx.voicePool`. Full row available as `ctx.p`
(fitted_notes, chord_root, active_motives, etc.); meta as `ctx.m`
(bands, dom_band, flatness).

Personalities compose their own curves — the conductor exposes raw
values, not pre-scaled amps.

---

## 12. Common pitfalls

- **`.odd` on a Float** throws `Message 'odd' not understood`. Default
  `~octave` is `5.0`. Cast: `~note = (~note + ~root + (12 * ~octave)).asInteger` before `.odd`.
- **`~scoreAnchorBeat = X` in personality** — anti-pattern. The
  conductor owns this. Removing wholesale from all personalities is
  done; don't reintroduce.
- **Empty `fitted_notes` half** — bridged in `conductorController.scd`
  to `[69]`, but if you're touching the score exporter, don't emit
  empty halves at source.
- **`Pdef.stop` alone leaves stuck notes** — see §5, §6.
- **Setting `~onResync` outside `topEnvironment.use{}`** — goes to
  `d.env`, controller can't find it, seek → stuck notes.
- **Long release + fast dur** — `\release, 1.4` with `\dur, 0.2` at
  `_mul4` tempo = 7 overlapping synths per event line at 40 events/sec
  = 280 concurrent → server node pressure.
- **~onResync loaded before Pdef.play** — safe because ~onResync only
  fires from OSCdef(\beatSync), which is on `/beat` OSC. No /beat
  until the score Routine ticks. By then Pdef.play has completed.
- **Server ordering with mixed latency**: `s.bind` bundles at
  `s.latency`; plain `sendMsg` is immediate. If you mix them,
  ordering can flip on the server. Match the latency of surrounding
  messages when timing matters (§6).

---

## 13. Testing checklist

Every new personality:

1. Load it — no post-window errors on `~init`.
2. State cycle: `/airkit/state idle → tuning → piece → curtain → silent → idle`. No stuck notes. Amp behaves per state.
3. `/airkit/go` — plays.
4. `/airkit/seek 60.0` mid-playback — no "too many nodes", no stuck notes, no persistent re-anchor log.
5. `/airkit/seek 0` (jump back) — same.
6. Personality swap (`/airkit/loadPersonality port index`) — old
   personality's synths freed within ~1 sec, no bleed.
7. `s.queryAllNodes` at rest — should show only the conductor's
   `\playStream` (if audio enabled) and each personality's group. No
   stray synths in the default group.

---

## 14. When adding a new pattern

- Update this file's relevant section with the new rule / pattern.
- If it's a new hook signature or ctx field, update `API.md` too.
- If it's just an experimental sound-design idea, keep it in the
  personality's front matter and leave this doc alone.
