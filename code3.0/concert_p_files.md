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
| `~onResync` | `{|idx|}` | beat-clock re-anchor (seek etc.) | **install in the personality env (d.env), NOT topEnvironment**. Body wraps in `topEnvironment.use{}` internally. Conductor's beatSync dispatches per-device. See §6. |

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

**`~deinit` idempotency.** `titleView` off-then-on fires
`unLoadPersonality` then `loadPersonality`, which calls `~deinit`
twice on the same env. Guard both `group` and `samplesLib`:

```supercollider
fork {
    if (group.notNil) {
        s.bind { group.freeAll }; s.sync;
        group.free; group = nil;
    };
    if (samplesLib.notNil) {
        samplesLib.do({|sample|
            sample.buffer.free; s.sync;
        });
        samplesLib = nil;
    };
};
```

Without the guards the second call double-frees buffers → "Cannot
call free on a Buffer that has been freed". Reference: `cotf_celesta1.sc`.

---

## 6. `~onResync` for Pdef personalities

**Non-negotiable for Pdef personalities.** On seek, `~beatClock`
re-anchors. Without `~onResync`:

- TempoClock dumps every event scheduled between old and new beat —
  hundreds of `Synth.new` fire in a burst → **"too many nodes"**.
- Synths in flight from before the seek keep their `gate=1` — no
  scheduled release from the cancelled Pdef → **stuck notes**.
- PLL never converges, log fills with re-anchor lines.

Install in the **personality env (d.env)**, NOT `topEnvironment`.
`OSCdef(\beatSync)` iterates `~devices` and dispatches per-env
(`d.env.use { ~onResync.(idx) }`), so a global slot would let the
last-loaded personality clobber every other device's handler → their
Pdefs would stay stranded on re-anchor (silent instrument after
play/seek). Place `~onResync = { … }` at the tail of `~init`, AFTER
the `topEnvironment.use{}` block that plays the Pdef. Wrap the body
in `topEnvironment.use{}` internally so `~beatClock` / `~roomState` /
`~scoreBeatsPerBar` resolve:

```supercollider
~init = ~init <> {
    // … load samples etc …
    topEnvironment.use {
        group = Group.new;
        Pdef(m.ptn, Pbind(...));
        Pdef(m.ptn).play(~beatClock, quant: ...);
    };

    // OUTSIDE topEnvironment.use — this assignment sets d.env's ~onResync.
    ~onResync = { |idx|
        topEnvironment.use {
            Pdef(m.ptn).stop;
            s.bind { group.freeAll };   // MUST be s.bind — see below
            Pdef(m.ptn).play(~beatClock, quant: ~scoreBeatsPerBar * ~scoreEventsPerBeat);
        };
    };
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

**State-aware restart.** If a state pauses the Pdef (see §15), the
resync's restart must not blindly re-`play`:

```supercollider
~onResync = { |idx|
    topEnvironment.use {
        Pdef(m.ptn).stop;
        s.bind { group.freeAll };
        if (~roomState != \tuning) {
            Pdef(m.ptn).play(~beatClock, quant: ~scoreBeatsPerBar * ~scoreEventsPerBeat);
        };
    };
};
```

Otherwise a seek during `\tuning` un-pauses the pattern and defeats
the paused-Pdef design. Reference: `cotf_celesta1.sc`.

**Multi-device dispatch.** `~onResync` lives in each personality's
own d.env. `conductorController.scd`'s `OSCdef(\beatSync)` iterates
`~devices` and calls each device's hook under `d.env.use` — same
idiom as `~onRoomState` dispatch. Loading harp on device 1 and
dulcimer on device 2 no longer strands one when Beethoven plays;
both devices' patterns restart on re-anchor. (Historical: before
2026-07-24, `~onResync` was a topEnvironment slot — last-loaded
personality clobbered the previous one and only THAT device's Pdef
survived seek. Watch for older personality files that still install
into topEnvironment; port them.)

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
- Hook installation (`~onResync = { … }`) goes OUTSIDE
  `topEnvironment.use{}` so the assignment lands in d.env. Wrap the
  BODY in `topEnvironment.use{}` for `~beatClock` etc. The controller
  dispatches per-device via `d.env.use`.
- Inside hook bodies, prefer `ctx` fields over `topEnvironment`
  lookups — ctx is passed by value and always accessible.
- Wrap `Pdef.play(~beatClock, …)` etc. in `topEnvironment.use{}` —
  `~beatClock` isn't in d.env.

`Pdef(m.ptn).set(\x, y)` is a class message and works anywhere;
doesn't need `topEnvironment`.

**`~onRoomState` runs under `d.env.use` too.** If any switch branch
calls `Pdef.resume(~beatClock, quant: ~scoreBeatsPerBar * …)` or
otherwise needs `~beatClock` / `~scoreBeatsPerBar` / `~scoreEventsPerBeat`,
wrap the whole `switch` in `topEnvironment.use { … }`. Personality-local
vars (m, group, tuneTime) are lexical so they stay in scope through
the swap. Reference: `cotf_celesta1.sc` `~onRoomState`.

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
- **Setting `~onResync` INSIDE `topEnvironment.use{}`** — goes to
  `topEnvironment` (a single global slot), so the next personality
  load on ANY device clobbers it. That device's Pdef then gets
  stranded on seek → silent instrument on play/re-anchor. Install
  OUTSIDE `topEnvironment.use{}` so it lands in d.env; wrap the body
  in `topEnvironment.use{}` for `~beatClock` etc. Controller iterates
  `~devices` and dispatches per-env. See §6.
- **`Event.addEventType(\name, {…})` with a shared name across
  personalities** — the registration lives on a class-level dict
  (`Event.eventTypes`). Same shared-slot pattern as the old
  `~onResync` bug: last loader wins, everyone else's handler is
  gone → cross-device sample bleed ("my harp is playing dulcimer
  samples"). Derive the name from `m.ptn` (unique per env):
  `var eventTypeName = (\customEvent_ ++ m.ptn).asSymbol;` and
  remove on `~deinit` (`Event.eventTypes.removeAt(eventTypeName)`)
  so reloads don't leak entries. Reference: any `cotf_*` sampler
  personality (harp/celesta/dulcimer/marimba/test/template).
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
- **`1 ! N` vs `[1] ! N`** — `1 ! N` returns an Array of N Integer
  1s (what you usually want). `[1] ! N` returns N copies of the
  Array `[1]`, and `.sum` on that broadcasts array-wise → returns
  `[N]` (a length-1 array), which then flows into things like
  `indexOfGreaterThan` and blows up. Watch for accidental array
  wrapping in pattern-length calcs. Reference: `cotf_dulcimer1.sc`
  `patternDur`.
- **Reload while already in target state** — `~onRoomState` fires
  only on state *change*, so reloading during `\tuning` won't run
  the `\tuning` branch. See §15 for the reload-guard idiom.

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

---

## 15. Accel-triggered one-shots in a paused-Pdef state

Reusable idiom for `\idle` / `\tuning` when you want gesture-triggered
isolated hits instead of the running Pdef. Reference:
`personalities/cotf_celesta1.sc` (`~tuningNext` + `~onRoomState \tuning`).

Three moving parts must line up:

**1. Pause + freeAll on state entry** (in `~onRoomState`, not the
tick — tick runs at ~30 Hz):

```supercollider
\tuning, {
    tuneTime = TempoClock.beats;
    Pdef(m.ptn).pause;
    s.bind { group.freeAll };   // kill in-flight tails
},
```

**2. Reload guard** — `~onRoomState` fires only on state *change*, so
a reload while already in the target state won't run that branch.
Mirror the entry setup at the tail of `~init` (inside `topEnvironment.use`):

```supercollider
if (~roomState == \tuning) {
    Pdef(m.ptn).pause;
    tuneTime = TempoClock.beats;
};
```

**3. State-aware `~onResync`** (see §6) — after `group.freeAll`,
only restart the Pdef when NOT in the paused state.

Then in the tick handler, threshold + throttle + inline event:

```supercollider
~tuningNext = {|d, ctx|
    var amp = m.accelMassFiltered.lincurve(0, 2.0, -70, -15, -4);
    if (m.accelMassFiltered > 0.5, {
        if (TempoClock.beats > (lastTime + 0.2), {
            (
                instrument: \stereoSampler,
                type:       eventTypeName,   // per-env, see §16
                out:        ob,
                group:      group,
                note:       0,
                root:       9,       // A
                octave:     6,
                amp:        amp.dbamp,
                ptch:       ptch,    // bend value (see §16)
            ).play;
            lastTime = TempoClock.beats;
        });
    });
};
```

Why an inline `(...).play` event rather than `Synth.new`:
- Runs through the personality's per-env event-type handler unchanged
  (see §16 on `eventTypeName`) — buffer lookup, odd/even resample,
  `~note+~root+12*~octave` calc all reused.
- `group: group` lands the synth in the personality's Group so
  `~deinit` / `~onResync` cleanup still catches it.
- Throttle idiom `TempoClock.beats > (lastTime + gap)` is the same
  one `harp1 ~idleNext` uses to rate-limit octave shuffles.

Works identically for `\idle` — swap `~roomState == \tuning` for
`\idle` in the reload guard and `\onResync` state check.

---

## 16. Pitch & sample handling recipes

Patterns from the sampler personalities (harp1, celesta1, dulcimer1,
marimba1/2).

### Per-env `Event.addEventType` name (avoid cross-device sample bleed)

`Event.addEventType(\name, {…})` registers on a class-level dict
(`Event.eventTypes`). If every sampler registers under the SAME name
(e.g. `\customEvent`), the last loader wins — every other device's
handler closure (which captures THAT env's `samplesLib`) is gone.
Symptom: "my harp is playing dulcimer samples" the moment a second
sampler loads on another device.

Fix — derive the name from `m.ptn` (fresh 16-char random per env, so
unique per device AND per reload). Add to top-of-file vars:

```supercollider
var eventTypeName = (\customEvent_ ++ m.ptn).asSymbol;
```

Register under this name in `~init`, reference it in the Pbind's
`\type`, and any inline `(...).play` events:

```supercollider
Event.addEventType(eventTypeName, {|e| … });
// …
Pbind(
    …
    \type, eventTypeName,
    …
);
```

Remove on `~deinit` so reloads don't leak entries in the class-level
dict:

```supercollider
Event.eventTypes.removeAt(eventTypeName);
```

Reference: any `cotf_*` sampler personality.

### `\freq` passthrough — send the true playing pitch to the SynthDef

`\rate` tells `PlayBuf` how fast to read the sample. It doesn't tell
the SynthDef what pitch is actually sounding. If you want a
freq-tracked filter, a sub-oscillator, a formant, a Klank body — the
SynthDef needs the effective playing frequency in Hz.

Add `\freq` as a passthrough SynthDef arg and let the customEvent
handler set it once the final MIDI note is known:

```supercollider
SynthDef(\stereoSampler, {|bufnum=0, out=0, amp=1, rate=1, freq=440, ptch=1, …|
    // freq is available inside — e.g. SinOsc.ar(freq * 0.25) for a sub
    …
}).add;

Event.addEventType(eventTypeName, {|e|
    ~note = (~note + ~root + (12 * ~octave)).asInteger;
    ~freq = ~note.midicps;      // <-- passthrough
    if(~note.odd, { … }, { … }); // buffer + rate as before
    ~type = \note;
    currentEnvironment.play;
});
```

The default `\note` event type would recompute `~freq` from
`~note + ~root + 12*~octave`, but we've already folded those into
`~note` here — so we set `~freq` explicitly to avoid double-counting.
References: `cotf_harp1.sc`, `cotf_test1.sc`, `cotf_voice1.sc`.

### `\ptch` — continuous playback-rate bend
Sampler SynthDef takes a `ptch` k-rate control that multiplies the
playback rate on top of the note-to-sample `rate`:

```supercollider
var lr = rate * BufRateScale.kr(bufnum) * ptch;
```

Standard tuning-ramp idiom (in `~tuningNext`):

```supercollider
var elapsed = TempoClock.beats - tuneTime;
var ptch = if (elapsed < 20.0) {
    (elapsed / 20.0).linlin(0, 1, 0.7, 1.0)   // flat → true
} { 1.0 };
Pdef(m.ptn).set(\ptch, ptch);
```

Reset `\ptch` to 1 in other states' tick handlers so residual bend
from a preceding `\tuning` state is cleared.

### `findClosestSample` — for sparse / non-even sample sets
When the library has gaps wider than the harp1-style odd/even split
handles (dulcimer's diatonic G-major coverage, marimba sets, etc.),
scan for nearest MIDI and compute rate from the semitone diff:

```supercollider
var findClosestSample = { |targetMidi|
    var closest = samplesLib.minItem({|s| (s.midiNote - targetMidi).abs });
    var semitoneDiff = targetMidi - closest.midiNote;
    (buffer: closest.buffer, rate: semitoneDiff.midiratio)
};
```

Returns nearest-either-direction; for a diatonic set the rate shift
is always ≤ 1 semitone. Reference: `cotf_dulcimer1.sc`.

### Octave-folding voice-pool pitch into instrument register
When drawing from `~scoreVoicePool` and the instrument has a
comfortable range the pool overshoots (dulcimer wants `[C3, C5]`,
pool can go to E6), *fold* into range rather than pitch-shift down:

```supercollider
var picked = pool.wrapAt(idx).asInteger;
while({ picked > 72 }, { picked = picked - 12 });
while({ picked < 48 }, { picked = picked + 12 });
```

Preserves pitch class, avoids chipmunk playback-rate transposition.
`~octave` from the state tick still transposes the folded result
±12. Reference: `cotf_dulcimer1.sc` `melodyMidiForOffset`.

### Array-aware `\customEvent` (chord voicings)
If `~note + ~root + 12*~octave` can be an Array (chord tones),
branch on `.isArray` and `.collect` bufnum/rate arrays — Pbind's
built-in multichannel expansion fires the whole chord as one event:

```supercollider
Event.addEventType(\customEvent, {|e|
    var target = ~note + ~root + (12 * ~octave);
    if(target.isArray) {
        ~bufnum = target.collect({|n| findClosestSample.(n).buffer });
        ~rate   = target.collect({|n| findClosestSample.(n).rate });
    } {
        var found = findClosestSample.(target);
        ~bufnum = found.buffer;
        ~rate = found.rate;
    };
    ~type = \note;
    currentEnvironment.play;
});
```

Reference: `cotf_dulcimer1.sc`, `cotf_marimba2.sc`.

### Per-state grain envelope — expose `\grainAtk` / `\grainDec` separately

For grain-scale personalities (voice1, any short-sample scatter), the
grain envelope is *the character*. Don't hide it behind a single
`\grainDur` with a fixed `Env.sine` — expose attack and decay as
separate SynthDef args so state ticks can shape them:

```supercollider
SynthDef(\voiceGrain, {|out=0, bufnum=0, amp=0.5, rate=1, freq=440, start=0,
    grainAtk=0.02, grainDec=0.28, grainCurve = -4, pan=0|
    var env = EnvGen.kr(Env.perc(grainAtk, grainDec, 1, grainCurve), doneAction: 2);
    // …
}).add;
```

Then each state controls the grain character inline:

```supercollider
~idleNext = {|d, ctx|
    Pdef(m.ptn).set(\grainAtk, 0.005);   // sharp click
    Pdef(m.ptn).set(\grainDec, 0.055);
    …
};

~pieceNext = {|d, ctx|
    // motion shapes the grain: still = soft/long, moving = sharp/short
    Pdef(m.ptn).set(\grainAtk, m.accelMassFiltered.lincurve(0, 2.0, 0.08, 0.005, 1));
    Pdef(m.ptn).set(\grainDec, m.accelMassFiltered.lincurve(0, 2.0, 0.5, 0.08, 1));
    …
};
```

`\grainCurve` is the perc envelope's curvature (negative = exponential
decay; -4 is a nice sharp default). Total grain length = `grainAtk +
grainDec` — no separate duration control needed.

Reference: `cotf_voice1.sc`.

---

## 17. Amp-curve palette

Named `.lincurve` presets so the authoring workflow (see
`personality_authoring.md`) can resolve prose amp descriptors
without hand-tuning dB endpoints per personality.

Input side is `m.accelMassFiltered` (or `m.rrateMassFiltered` for
rotation-dominant gestures). All curves are on `.dbamp`-converted dB.

| Name | Curve | Peak | Use for |
|---|---|---|---|
| `whisper` | `.lincurve(0, 2.0, -90, -35, -4)` | -35 dB | ambient bed under a loud line |
| `low` | `.lincurve(0, 2.0, -70, -25, -4)` | -25 dB | quiet idle / curtain |
| `mid` | `.lincurve(0, 2.0, -70, -15, -2)` | -15 dB | idle / tuning normal |
| `expressive` | `.lincurve(0, 1.5, -70, -8, -1)` | -8 dB | piece full-dynamic |
| `faded` | `.lincurve(0, 2.0, -80, -30, -4)` | -30 dB | curtain / outro |

Rationale for curvature values: `-4` (soft-knee, quiet-stays-quiet)
suits idle/tuning/curtain where accidental brushes shouldn't
audibly trigger; `-1` (near-linear) suits piece where every gesture
should register; `-2` in between.

Prose phrasebook (personality_authoring.md §Phrasebook):
- "quiet" / "low volume" → `low`
- "very quiet" / "almost silent" → `whisper`
- "expressive" / "full dynamic" → `expressive`
- "faded" / "receding" → `faded`
- unspecified → `mid`

Palette is a starting point — override the peak or the input range
when the personality has an unusual sensor response. Reference
personalities: harp1 uses ~mid in idle (line 224), marimba2 uses
~expressive in piece.

---

## 18. Pitch wander around a target (with settle)

Variant of the `\ptch` tuning ramp (§16) where pitch oscillates
±N semitones around a target for a duration, envelope-decaying so
it settles at the target. Use where "pitch wanders around A5 and
finally rests" is the intent.

Compute at state-tick rate (~30 Hz), set via `\ptch` on the sampler
or via `\rate` at trigger time for accel-one-shot personalities.

```supercollider
~tuningNext = {|d, ctx|
    var wanderDur    = 20.0;    // seconds to settle
    var wanderRangeSt = 2.0;    // ± semitones peak
    var wanderRateHz = 0.3;     // oscillations/sec
    var elapsed = TempoClock.beats - tuneTime;
    var envelope = if (elapsed < wanderDur) {
        1.0 - (elapsed / wanderDur)   // 1 → 0 linear taper
    } { 0 };
    var offsetSt = envelope * wanderRangeSt * sin(elapsed * 2 * pi * wanderRateHz);
    var ptch = offsetSt.midiratio;

    // Apply to running Pdef:
    Pdef(m.ptn).set(\ptch, ptch);

    // Or for accel-one-shot inline event (see §15):
    // (…, root: 9, octave: 6, ptch: ptch, …).play;
};
```

Tuning knobs:
- **wanderDur** — how long until it rests. Shorter = more decisive.
- **wanderRangeSt** — how far it wanders in semitones. 1–3 typical.
- **wanderRateHz** — how fast it oscillates. 0.1–0.5 = musical
  slow drift; > 1 = warble.
- **envelope shape** — swap the `1.0 - (elapsed/wanderDur)` linear
  taper for `.linexp(0, 1, 1, 0.01)` (exponential, front-loaded)
  or a squared curve for slower initial decay.

Target pitch itself is set by the note+root+octave in the event
(A5 = `note: 0, root: 9, octave: 6` under the standard baseMidi=60
convention). Wander rides `\ptch` on top.

For personalities that combine wander with accel one-shots
(paused-Pdef style, §15), compute `ptch` in the state tick and
pass into the inline event literal as the `ptch:` key.

---

## 19. Rotation-magnitude-driven subdivision

Pattern `\dur` shrinks as `m.rrateMassFiltered` grows — "greater
rotation, smaller subdivision." Modulates rhythm (not amp / not
pitch). Applies only when a running Pdef is in the state.

```supercollider
~pieceNext = {|d, ctx|
    // hi = slow subdivision (idle rotation), lo = fast (max rotation)
    var dur = m.rrateMassFiltered.linexp(0.01, 1.0, 2.0, 0.25);
    Pdef(m.ptn).set(\dur, dur);
    // …other params (amp, octave, etc.) alongside
};
```

`.linexp` (exponential) rather than `.linlin` — subdivisions feel
proportional in ratio, not linear. `0.25..2.0` covers 16th to
half-note at bar-based grid; adjust to taste (e.g. `4.0..0.125` for
extreme range).

For quantised subdivision (e.g. only 4/8/16), snap after the linexp:

```supercollider
var raw = m.rrateMassFiltered.linexp(0.01, 1.0, 4, 0.25);
var snap = [0.25, 0.5, 1.0, 2.0, 4.0];
var dur = snap[snap.indexOfGreaterThan(raw).max(1) - 1];
```

Prose phrasebook:
- "greater rotation → smaller subdivision" → this recipe
- "rotation controls speed" → this recipe (repurpose dur→amp too)
- "pattern gets busier with motion" → this recipe

Combines cleanly with §15 (accel one-shots) and §20 (layered
running-Pdef + gestural one-shots).

---

## 20. Layered running-Pdef + gestural one-shots in the same state

State keeps a running Pdef going as rhythmic backbone AND fires
accel-threshold one-shots on top as gestural highlights. Both
share the personality's Group so cleanup is unchanged.

Structural requirements:
- Pdef **is running** in this state (no pause in `~onRoomState`
  for this state, unlike the paused-Pdef pattern of §15).
- `~onResync` restarts the Pdef unconditionally (again, unlike §15).
- The state tick modulates the Pdef (dur / amp / octave) *and*
  fires threshold-throttled one-shots. Both call sites are inside
  the same `~pieceNext` (or wherever).

```supercollider
~pieceNext = {|d, ctx|
    // --- running-Pdef modulation ---
    var amp = m.accelMassFiltered.lincurve(0, 1.5, -70, -8, -1);
    var dur = m.rrateMassFiltered.linexp(0.01, 1.0, 2.0, 0.25);   // §19
    Pdef(m.ptn).set(\amp, amp.dbamp, \dur, dur);

    // --- gestural one-shot on top (same group) ---
    if (m.accelMassFiltered > 0.6, {
        if (TempoClock.beats > (lastTime + 0.2), {
            (
                instrument: \stereoSampler,
                type:       \customEvent,
                out:        ob,
                group:      group,
                note:       0,
                root:       ~scoreVoicePool.choose.wrap(0, 11).asInteger,
                octave:     6,
                amp:        (amp + 3).dbamp,   // one-shot slightly louder
                ptch:       1.0,
            ).play;
            lastTime = TempoClock.beats;
        });
    });
};
```

Two audible layers with independent character: the Pdef is
rotation-density modulated (running rhythm), the one-shots are
accel-triggered highlights that punch through. Because both write
to the same output bus via the same personality group, `\silent`
mute (`Pdef.set(\amp, 0)` alone) still lets one-shots through —
add an explicit one-shot suppress if that matters:

```supercollider
\silent, {
    Pdef(m.ptn).set(\amp, 0);
    lastTime = TempoClock.beats + 1e6;   // block one-shots
},
```

Or gate the threshold check on `~roomState != \silent`.

**Where this differs from §15:** §15 pauses the Pdef entirely and
fires only one-shots; §20 keeps the Pdef live and adds one-shots as
a second voice. Choose §15 for "tuning is a series of hits"; choose
§20 for "the piece is a running pattern with gestural accents."

---

## 21. Physical prints — `personalities/prints.json` + the `prints:` header key

`personalities/prints.json` is a machine-generated snapshot of the COTF
physical print inventory (the 3D-printed instrument bodies performers carry),
regenerated on M0 by `npm run export:prints` — **do not hand-edit it**. While
`"status": "TEST_DATA"` the entries are placeholders; the real catalog (14+
prints) lands late July and flips it to `"LIVE"`.

Each personality header may add one optional key recommending which prints
suit its sound, using ids from that file:

    prints:      [whiteViolin, copperViolin]

The COTF instrument crafter treats this as a soft preference when assigning a
performer their print — recommended prints win ties, but a performer's own
character match can override. Omit the key to express no preference.

---

## 22. State ticks own the mappings — keep Pbind minimal

**Principle.** Every parameter that could vary per room state — including
score-driven ones like `\rate`, `\start`, `\note`, `\root` — is set from
state ticks via `Pdef.set`. Pbind holds ONLY static routing keys and, at
most, Pfuncs that do pure LOOKUPS against state-tick-set vars (never
direct gesture or score reads).

**Why.** State ticks are the single, obvious place to read a personality
and understand what it does in each state. If gesture-driven or
score-driven mappings sit inside a Pbind Pfunc, they are:

- Invisible to a reader scanning the state ticks.
- Not per-state — every state gets the same mapping unless it's guarded
  by an `if (~roomState == …)` inside the Pfunc, which is worse than
  redundant `Pdef.set` calls.
- Silent about intent — the Pfunc is a piece of infrastructure; the
  state tick expresses musical decisions.

Repetition across states is fine (idle and curtain might both hold
`\rate = 1`) — reading the state tick tells you exactly what that state
does, without cross-referencing a Pbind.

**What goes where.**

| Pbind (in `~init`) | State tick (~idleNext etc.) |
|---|---|
| `\instrument`, `\out`, `\group`, `\args` | `\amp`, `\dur`, `\octave`, `\ptch`, `\rate`, `\start`, `\grainDur`, `\bufnum`, `\note`, `\root`, … |
| `\pan` if it's a `Pwhite`/random pattern | anything driven by gesture (m.\*, d.sensors.\*) |
| Truly static routing | anything driven by score (ctx.voicePool, ctx.chord, ctx.p) |
| Pfuncs that read a state-tick-set VAR and do a pure lookup (e.g. `\bufnum` reads `currentLayer` + a clock-derived slot to index `~buffers`) | The state tick that SETS that var |

**Example — voice1's ~rate lives in state ticks, INLINE:**

```supercollider
// GOOD — piece-time \rate mapping lives in ~pieceNext. ONE line per
// Pdef.set, no intermediate vars, no helper function. Reads left-to-right.
~pieceNext = { |d, ctx|
    Pdef(m.ptn).set(\amp,  m.accelMassFiltered.lincurve(0, 2.0, -30, -3, 1).dbamp);
    Pdef(m.ptn).set(\dur,  subDivs[m.gyroXFiltered.clip(-1, 1).lincurve(-1, 1, 0, subDivs.size - 0.001, 1).asInteger]);
    Pdef(m.ptn).set(\rate, ((ctx.voicePool ? [69]).choose.asInteger.mod(12) - samplePitchMidi.mod(12)).wrap(-6, 5).midiratio);
};

// BAD — \rate mapping baked into the Pbind, same across all states,
// invisible to anyone reading the state ticks.
Pdef(m.ptn,
    Pbind(
        …
        \rate, Pfunc {
            var pool = (~scoreVoicePool ? [69]).asArray;
            var target = pool.choose.asInteger;
            (target.mod(12) - samplePitchMidi.mod(12)).wrap(-6, 5).midiratio
        },
        …
    );
);
```

**Avoid file-scope mutable state ("global-ish" vars). Prefer inputs
that are pure functions of live signals.** A `var playhead = 0` at the
top of the file, mutated by every state tick, is a hidden state
machine — action-at-a-distance between hooks. It works but it defeats
the point of "each state owns its behaviour explicitly".

For anything that feels like it needs memory across ticks, prefer:

- **Direct gesture mapping** — the performer IS the memory. Instead of
  drifting a `playhead` accumulator, map `m.gyroYFiltered` to `\start`
  directly and let the wrist BE the playhead. Reference:
  `cotf_voice1.sc` `~pieceNext` — `\start = m.gyroYFiltered.lincurve(-1, 1, 0, 0.98, 1)`.
- **Clock-derived** — `(TempoClock.beats * rate).mod(1)` is a
  deterministic scan without an accumulator var. Rate can come from
  gesture inline.
- **Per-event random** — `1.0.rand` for a slot value, `rrand(a, b)`
  for a range. No memory, no state, chaotic character.

A file-scope var is defensible ONLY when the value is truly
personality-wide state (a preallocated buffer, a Group reference) that
`~init` writes ONCE and everyone reads. When state ticks would need to
WRITE it, look for a gesture or clock source instead.

`cotf_orchDrums1.sc`'s `currentLayer` is a borderline case — it's a
state-tick-written file-scope var read by a Pbind Pfunc. The lookup is
per-event and clock-derived (which slot am I on), so the var acts as a
buffer between the 30 Hz gesture rate and the ~4 Hz event rate. Kept
because rewriting `\bufnum` inline in the state tick would drop
clock-slot precision. Not a template to copy — a compromise.

**Practical impact on state-tick length.** Each state tick becomes
longer — you're setting 5–10 parameters instead of 2–3. That's the
trade. When you edit a personality later, everything a state does is
visible in one function; no hunting through Pbind Pfuncs. Worth it.

**Exception — static references belong in `~init`, not state ticks.**
The principle is about MAPPINGS (gesture → param, score → param), not
about constants. If a value never changes over the personality's life
(a Buffer's `bufnum`, a fixed `\out` bus, a hard-coded envelope shape),
set it ONCE in `~init` via `Pdef.set` after the resource exists. Don't
repeat it in state ticks:

```supercollider
// GOOD — sampleBuffer.bufnum is constant across the personality's life.
~init = ~init <> {
    sampleBuffer = Buffer.read(s, path, action: {…});
    Pdef(m.ptn).set(\bufnum, sampleBuffer.bufnum);   // once
    …
};

// BAD — this races with ~deinit (which nils sampleBuffer). AppClock
// procRout can fire a state tick between ~deinit's nil and full
// procRout shutdown, hitting nil.bufnum:
~pieceNext = { |d, ctx|
    Pdef(m.ptn).set(\bufnum, sampleBuffer.bufnum);   // NO — race → nil.bufnum
    …
};
```

If a param has both a static component AND a dynamic component (e.g.
`~buffers[dynamicIdx]` — the array is static, the index varies), put
the lookup in a Pbind Pfunc that reads a state-tick-set var for the
dynamic input; the array reference stays lexical.

Rule of thumb: if a state tick would set the SAME value on every call,
that value belongs in `~init`.

**No helper functions. No abstraction. Inline every mapping.**

The instrument loses life when logic gets factored out. Even if two
states share the exact same rate calculation, DO NOT extract it into a
top-of-file `poolRate = { |pool| … }` and call it from both. Write the
full one-line expression in each state.

```supercollider
// GOOD — the mapping is right there in the state, ready to tweak
// without hunting for a helper elsewhere.
~pieceNext = { |d, ctx|
    Pdef(m.ptn).set(\rate, ((ctx.voicePool ? [69]).choose.asInteger.mod(12) - samplePitchMidi.mod(12)).wrap(-6, 5).midiratio);
};

// BAD — you have to read the helper to understand what \rate does,
// and every state calls it with the same signature so per-state
// variation gets awkward (need arg lists, if branches, etc.).
var poolRate = { |pool|
    ((pool ? [69]).choose.asInteger.mod(12) - samplePitchMidi.mod(12)).wrap(-6, 5).midiratio
};
~pieceNext = { |d, ctx|
    Pdef(m.ptn).set(\rate, poolRate.(ctx.voicePool));
};
```

The apparent repetition IS the point. When you're tuning `~pieceNext`
mid-performance and want it to react differently from `~idleNext`, you
just change the numbers in that one function. No refactor. No fear of
breaking another state.

**Mapping idioms — conventions across all p-files:**

- **`lincurve` over `linlin`.** Add the 5th arg (curve) even if you
  start at `1` — the curve knob is one of the most expressive levers
  when tuning gesture response. `linlin` sacrifices that for no
  savings.
- **Single-line mappings.** Chain `.clip → .lincurve → .asInteger`
  and any array lookup in ONE `Pdef.set(\key, …)` line. Intermediate
  `var x = …; var idx = …;` scaffolding fragments the mapping and
  costs more to read than it saves.
- **`m.accelMassFiltered` is the default amp gesture.** Every state's
  `\amp` mapping should start `m.accelMassFiltered.lincurve(…).dbamp`
  unless there's a specific reason (an idle state that responds only
  to rotation, a sample scrubber tied to gyro, etc.). Consistency
  makes cross-personality behaviour predictable for performers.
- **Guard `ctx.voicePool` inline with `? [69]`.** Simple, cheap, gets
  the mapping past the first-beat window without a separate check.
