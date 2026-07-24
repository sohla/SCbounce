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

**State-aware restart.** If a state pauses the Pdef (see §15), the
resync's restart must not blindly re-`play`:

```supercollider
~onResync = { |idx|
    Pdef(m.ptn).stop;
    s.bind { group.freeAll };
    if (~roomState != \tuning) {
        Pdef(m.ptn).play(~beatClock, quant: ~scoreBeatsPerBar * ~scoreEventsPerBeat);
    };
};
```

Otherwise a seek during `\tuning` un-pauses the pattern and defeats
the paused-Pdef design. Reference: `cotf_celesta1.sc`.

**Multi-device (COTF production).** Keep installing into
`topEnvironment` exactly as above — nothing changes in the p-file.
With several devices loaded, each install would clobber the previous
device's hook, so in the COTF profile (`cotf/main_cotf.scd`)
`personalityController` captures the install into the device env
right after `~init` and restores a dispatcher that calls every
device's own hook on resync (under `topEnvironment`, so `~roomState`
/ `~beatClock` / `~score*` resolve; `m`/`group` are lexical
captures). In the solo GUI profile the capture is a no-op and the
hook runs exactly as documented here.

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
                type:       \customEvent,
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
- Runs through the personality's `\customEvent` handler unchanged —
  buffer lookup, odd/even resample, `~note+~root+12*~octave` calc
  all reused.
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
