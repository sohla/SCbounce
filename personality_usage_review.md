# P-file usage review, March to September 2026

What the personality files actually do, read off the code of the files that
were worked on in the last six months. Synthesis, mappings, common idioms, and
the gaps in the material as it stands. No new techniques are proposed; where
something is proposed it is a consolidation of a thing the files already do
in several places by hand.

The three ideas raised at the outset (a gated `lincurve`, a sensitivity
control, and putting gesture data on control buses) are worked through in
section 6 against what the code already contains.

---

## 1. Scope: what was used

`git log --since 2026-03-02` on `personalities/` touches 199 files, but two
commits are bulk operations (the archive import `8ba15d2`, the file removal
`cbfb97e`). Excluding those leaves a working set of **101 files** touched by
hand in the period. The sessions, in order:

| When | Session | Files |
|---|---|---|
| Mar 4 to 5 | Mel 2026 prep, waves buffer bug | waves, waves2, melbb1, visual1-3 |
| May 4 to 29 | post-performance fixes, Mel and Suz prep | mel1-5, melbb1/2, suz1-4, violin1/2, wind1/2, funMelody |
| Jun 15 | Mel chair work, yourDNA list | melChair1-4, animalMat, chopMat, frog1/2, harp1/2, heatherLaugh1, melodicPerc1/2 |
| Aug 10 to 12 | new visual core merged, droplet | droplet, silence, templates |
| Aug 13 to 17 | train quartet for Alon | trainChooka2 (17 commits), trainMove2 (14), trainMelody2 (10), trainBass2/3, trainMove3, trainMoveOnly |
| Aug 15 | COTF port | cotf_* (18 files), multiBeat1/2 |
| Aug 15, 24 | BtB session, Nic and Mel session | bongo1, cymbals1, miniMoog, pluck1, nicTwoNote, bells, circusChoir1, melChest1, velocity2/3 |
| Aug 17 | yourDNA prep | bees, westminsterChimes1, magicWand |
| Aug 24, Sep 1 to 2 | Glenroy workshops, cotf refactor | celeste1, dulcimer1, harpsichord1, piano1, multiBeat3/4/5, multiBeatSynth1 |

Five list files were rewritten in the period: `list_Mel_26`, `list_yourDNA26`,
`list_alon26`, `list_BtB`, `list_glenroy` (current, Sep 2). The files that sit
in two or more of those lists are the working core:

> cymbals1, droplet, melbb1, melbb2, mel4, nicTwoNote, pluck1, rain1, thunder,
> harp1, wind1, violin1, violin2, magicWand, bells, metal1, miniMoog, bongo1,
> bee, funBass, funMelody, waves, waves2, timWind1, frog2, multiBeat1, and
> the train quartet (trainMove2, trainBass3, trainMelody2, trainChooka2).

Everything below counts over the 101-file working set unless it says otherwise.

---

## 2. Synthesis: what you have

Counted by which UGens appear in the file's SynthDefs.

| Family | Files | Where it lives |
|---|---|---|
| Sampler (`PlayBuf`) | 46 | kits: cymbals1, melbb1/2, multiBeat4, cotf_drums1-4. Pitched nearest-sample libraries: celeste1, dulcimer1, harpsichord1, piano1, multiBeat5, cotf_marimba/celesta/harpsichord/dulcimer. Loopers: rain1 (four layers), waves2, violin1 |
| Subtractive osc into `RLPF`/`LPF`/`BPF` | 44 osc, 72 filter | miniMoog, multiBeat family, train quartet (`warmPadMove2`), funBass, button1/template3, melodicPerc1/2 |
| Noise-based | 26 | wind1/2 (`sheet3/4`), trainChooka (`WhiteNoise`+`BrownNoise` into `RLPF`), velocity2/3, waves |
| Modal (`DynKlank`/`Klank`) | 11 | magicWand/2, bells, metal1 (`bambooComplex`), bongo1, velocity2/3, melChest1, wind1, template |
| Granular / stretch (`Warp1`) | 6 | suz3, mel4, violin2, quackQuack, heatherTeddies, chopMat |
| Karplus (`Pluck`) | 1 | pluck1 |
| Feedback (`LocalIn`) | 5 | nicTwoNote, suz1, melChair1/4, bubblesMic |
| Live input (`SoundIn`) | 1 | bubblesMic |
| FM / PM (`PMOsc`), `Gendy`, wavetable | 0 | |

Effects and dynamics:

| Technique | Files | Note |
|---|---|---|
| Reverb (`FreeVerb`/`GVerb`) | 19 | in 17 of these it is inside the per-note SynthDef. Only droplet and dulcimer1 use a `Bus.audio` and a tail synth |
| Delay / comb / allpass | 10 | mostly chorus in the train pad |
| `Compander` / `Limiter` | 22 | |
| `.lag` / `.lagud` on a control | 30 | a second smoothing stage after the model filter |
| `Amplitude.kr` as a follower | 5 | trainMove2/3/Only, metal1, melChest1 |
| `DetectSilence` for freeing | 5 | droplet, bongo1, melodicPerc1/2, template |
| `Group.new` in `~init` | 13 | all Aug/Sep files; none of the May/June files |

Observations:

- **The sampler is the instrument.** Nearly half the working set plays
  buffers. The synthesis work in the period went into kits and pitched sample
  libraries (the COTF port and its Sep 1 refactor), not into new synthesis.
- **Subtractive is the only synthesised voice.** Every synthesised melodic
  voice is an oscillator or two into a resonant low-pass, with the cutoff
  being the main timbral map. Modal synthesis is the other real family, and
  it is where the strongest visuals came from (magicWand, bongo1, bells)
  because the SynthDef's constants could be mirrored.
- **Reverb is per voice.** droplet moved it to a group tail on Aug 11 and
  found the "room above 1 grows" bug in doing so; no other file has followed.
  There is no shared per-device effects bus in the controller.
- **Smoothing lives in three places** with no policy: the model one-pole
  (coefficients re-tuned at the top of every file, ten distinct decay values
  in use), server-side `lag` (30 files), and one-pole slews re-implemented
  inside draw functions (trainMove2 `stickA`, metal1 `droneAmpSmooth`)
  because the model coefficients belong to the sound. The controller's
  injected `~smooth` and `~slope` are called by **no** p-file directly.

---

## 3. Architectures and pattern vocabulary

| Architecture | Files |
|---|---|
| Pdef + Pbind (B and C in the authoring guide) | 66 |
| Long-lived `Synth` set from `~next` (A) | 38 |
| One-shot fired from `~next` with a `TempoClock.beats` throttle | 16 |
| Pattern paused/resumed from `~next` | 30 |
| Buttons (`digiInEvent`) | 14, all legacy files; no 2026 session file uses them |

Pattern classes, by number of files:

| Class | Files | | Class | Files |
|---|---|---|---|---|
| `Pfunc` | 59 | | `Pn` | 8 |
| `Pseq` | 50 | | `Pswitch` | 8 |
| `Pkey` | 45 | | `Pslide` | 6 |
| `Pwhite` | 42 | | `Prand` | 4 |
| `stutter` | 37 | | `Pexprand` | 1 |
| `Rest` | 13 | | `Pwrand`, `Pshuf`, `Pif`, `Pgeom`, `Pbrown`, `Pfin`, `Ppar`, `Pspawner`, `Pbindef` | 0 |

Pitch is always `\note` + `\root` + `\octave` (`\degree` appears in one
file). `Scale` is named in 51 files but almost always as `\scale, Scale.major`.
Rhythm is a `Pseq` of durations, or `\dur` set continuously from `~next`
(38 files), often through the subdivision ladder idiom
`0.5 * 2.pow(x.floor).reciprocal` (train quartet, funBass) or a `divs`
array with `Pswitch` (multiBeat family).

So the pattern layer is deterministic sequences with `Pwhite` jitter, driven
from outside by `Pdef.set`. Weighted choice, finite phrases, and nested or
parallel patterns are absent from the vocabulary.

---

## 4. Mappings: what drives what

### Sources

| Source | Files | Live mapping lines | Typical use |
|---|---|---|---|
| `m.accelMassFiltered` | 101 | 647 | everything: amp, dur, release, cutoff, index, density |
| `m.rrateMassFiltered` | 92 | 309, but ~15 are mappings; the rest are `~plot` probes | amp in melChair1/2/4, rate in cymbals1, cutoff in miniMoog and harp1 |
| `m.gyroYFiltered` / `gyroEvent.y` (tilt) | 40 | 86 | octave, note index, sample index |
| `m.gyroZFiltered` / `gyroEvent.z` (twist), folded | 28 | 58 | cutoff, pan, note index |
| `m.gyroXFiltered` / `gyroEvent.x` (roll), folded | 27 | 35 | cutoff, decay, wobble, register |
| `m.accelMass` (raw) | 88 | 12 as a mapping; the rest in probes | hit gates (trainBass3 `doubleThresh`), pluck1 |
| `accelEvent.x/y/z` single axis | 4 | | droplet `side`, cymbals1 `thr`, trainMove2/3 strike on `x > 1.0` |
| `m.com.root` | 33 readers, 18 writers | | root shared across the train quartet and multiBeat family |
| `d.sensors.velocity` | 0 outside probes | | |
| `d.sensors.quatEvent` / `quatCalibrated` | 0 | | |

Two points follow. Acceleration **magnitude** is the instrument; direction is
discarded at the model (`sumabs`), and the four files that want an axis reach
past the model to get it. And rotation rate is computed and plotted for every
file but drives only a handful, where it produces the most distinctive
behaviours in the set (the melChair family's "amp from rotation", cymbals1's
roll rate).

### Targets

Amplitude, pattern `\dur`, envelope release and attack, filter cutoff,
octave, note index, sample start and rate, and the visual keys. Amplitude in
dB via `lincurve(...).dbamp` in 48 files; the remainder map to linear 0..1 and
then apply an ad-hoc scalar after the mapping (`a * 0.3`, `amp * 0.4`,
`a * 0.8`, `sa * 2`, 18 such lines), which is where per-file level balance
currently lives. `d.params.volume` exists on every device, is set to 0.5 at
connect, and is read by nothing.

### The curve calls

538 live mapping lines: 376 `lincurve`, 237 `linlin`, 44 `linexp`,
10 `explin`. The input maximum used for `accelMassFiltered.lincurve`:

| inMax | Calls | | inMax | Calls |
|---|---|---|---|---|
| 2.5 | 66 | | 1.4 | 7 |
| 2.0 | 29 | | 3.0 | 7 |
| 1.5 | 19 | | 0.5 | 5 |
| 1.0 | 20 | | 0.3 | 5 |
| | | | 0.1 | 6 |

So "a full gesture" is defined in at least nine places between 0.1 and 4.0.
The cluster at 0.1 to 0.5 is a set of files that are already running at
roughly twenty-five times the sensitivity of the 2.5 files: trainChooka2 and
nicTwoNote (`0..0.1` for amp), velocity3, cymbals1, bongo1, trainBass3. Only
three calls in the whole set use a non-zero input minimum (the train pad's
`rel` on `0.5..2.5`).

Curve values: `-1` (73), `1` (50), `-2` (46), `-3` (27), `2` (20), `3` (16),
`0` (15), steeper than `-4` rare. The house style is a `-1` to `-3` fast
rise for amp and density, `+2` to `+3` late opening for cutoff, exactly as
the authoring guide says.

---

## 5. Gating: the same thing done five ways

This is the clearest gap. Every file needs the instrument to be silent at
rest, and the working set contains five separate idioms for it, often two or
three in the same file.

**a. Pause and resume the pattern on a threshold** (30 files):

```supercollider
if(m.accelMassFiltered > 0.07,{
    if( Pdef(m.ptn).isPlaying.not,{ Pdef(m.ptn).resume(quant:dur) });
},{
    if( Pdef(m.ptn).isPlaying,{ Pdef(m.ptn).pause() });
});
```

Threshold values in use: 0.004, 0.015, 0.02, 0.03, 0.05, 0.07, 0.1, 0.11,
0.12, 0.5. No hysteresis anywhere, so a value sitting on the threshold
flickers the pattern.

**b. A floor applied after the curve** (86 lines):

```supercollider
var a = m.accelMassFiltered.lincurve(0,1.5,0,1,-2);
if(a<0.03,{a=0});
if(a>0.9,{a=0.9});
```

The ceiling variant is in the train pad, funBass, wind1/2, metal1, melChest1.
Note that `lincurve` already clips at the top, so `if(a>0.9)` is only doing
something when the output range exceeds it, which in `metal1` (`a>0.9 → 0.3`)
it is being used as a deliberate fold-back on an overshoot.

**c. The same floor in the dB domain** (7 files: multiBeat4/5/Synth1,
dulcimer1, piano1, nicTwoNote, harp2):

```supercollider
if(amp < 39.neg, { amp = 90.neg });
```

**d. A `~curveAbove` helper**, defined in trainMove2, trainMoveOnly and
cotf_whisperer1 with two different signatures, and **called from none of
them**. cotf_orchDrums1 writes the same thing inline as a `deadZone`:

```supercollider
var amp = if (raw > deadZone, { raw.lincurve(deadZone, 2.0, 0.0, 1.0, 1) }, { 0 });
```

**e. Latches and edge triggers**: trainBass3 `started` (play once the first
strike exceeds `doubleThresh`), velocity3 `trig` (rotate the note on the
rising edge of amp), bells and metal1 `move > 0.02/0.05`.

The model itself once carried this. `personalityController.scd` still has
`\accelMassAmpThreshold`, `\rrateMassThreshold`, `\isHit` and `\isMoving`
commented out, and **46 files** in the working set still have `~plot` probe
lines that read `m.rrateMassThreshold` or `m.accelMassAmp`, which are now nil.

What the idioms have in common: a threshold in sensor units, a curve above it,
zero (or silence in dB) below it, and, for the pattern case, an on/off state.
That is one function and one model field.

---

## 6. The three proposals, against the code

### 6.1 A gated `lincurve` on `SimpleNumber`

Yes, and the shape is already fixed by the four idioms above:

```supercollider
+ SimpleNumber {
    gatecurve { |thresh = 0.05, inMax = 2.5, outMin = 0, outMax = 1, curve = -1, floor = 0|
        ^if(this < thresh, { floor }, {
            this.lincurve(thresh, inMax, outMin, outMax, curve)
        })
    }
}
```

`floor` is what makes it cover the dB idiom (c) as well as the linear one:

```supercollider
var amp = m.accelMassFiltered.gatecurve(0.07, 1.4, -50, -5, -1, -90).dbamp;
```

Design points:

- **Below `thresh` returns `floor`, not `outMin`.** That is the whole reason
  `lincurve` alone does not do it: it clips to `outMin` below `inMin`, so a
  `-50 dB` floor is still audible. The three non-zero `inMin` calls in the set
  are exactly the cases where the author wanted this and could not have it.
- **The curve starts at the threshold**, so raising the threshold does not
  also cut off the bottom of the curve. This is what `~curveAbove` and the
  orchDrums `deadZone` both did.
- **No hysteresis in the Number method.** Hysteresis needs state, and state
  belongs in the model (6.2). Keep the method pure so it is usable inside a
  `Pfunc`, a `~vdef` and a `~plot` probe identically.
- **Where it lives.** A method on `SimpleNumber` has to be a class extension,
  which means a `.sc` file in an Extensions directory and a class-library
  recompile, not something `Require` can load. There is precedent: the
  `visualServer` extension is installed the same way and announces itself at
  boot. The alternative, injecting `~gatecurve` next to `~smooth` in the
  Environment, works but breaks the method-chaining that every one of the 376
  calls uses, and `~smooth` itself has zero callers, which suggests injected
  helpers do not get picked up. Go with the extension, and add it to whatever
  installs `visualServer` on the Pi.
- **Migration is mechanical**: idiom (b) becomes one line, idiom (c) becomes
  one line with `floor: -90`, the three dead `~curveAbove` definitions go, and
  the orchDrums `deadZone` block becomes a call.

### 6.2 `m.moving` in the model

The 30 pause/resume blocks are gating a *state*, not a value, and the state
needs hysteresis. Put it in `~processDeviceData`:

```supercollider
\accelGateOn: 0.07,
\accelGateOff: 0.03,
\moving: false,
...
~model.moving = if(~model.moving,
    { ~model.accelMassFiltered > ~model.accelGateOff },
    { ~model.accelMassFiltered > ~model.accelGateOn });
```

Then the p-file sets the two thresholds at the top where it already sets the
filter coefficients, and the nine-line block becomes:

```supercollider
if(m.moving and: { Pdef(m.ptn).isPlaying.not }, { Pdef(m.ptn).resume(quant: dur) });
if(m.moving.not and: { Pdef(m.ptn).isPlaying }, { Pdef(m.ptn).pause });
```

This is the commented-out `isMoving` coming back with the one thing it
lacked. The `~plot` menu gets a fixed line `[m.moving.binaryValue]` so the
gate is visible next to the signal it is gating. The rrate-driven files
(melChair, chicken1, animalMat) want the same on `rrateMassFiltered`, so
either a second pair of fields or make the gate source selectable.

### 6.3 Sensitivity

The idea as stated: one control that multiplies the input maximum of every
`lincurve`, so that reducing it makes the instrument more sensitive.

**Put it on the model, not on the calls.** For the 99% of calls whose input
minimum is 0, the two are the same thing:

```
x.lincurve(0, inMax * k, ...)   ==   (x / k).lincurve(0, inMax, ...)
```

so scaling `accelMass` once in `~processDeviceData` is equivalent to editing
the 376 calls, and has three properties the call-site version does not:

1. **The visuals follow for free.** Twenty files have draw functions or
   `\modulation` Pfuncs that recompute their drive from `m.accelMassFiltered`
   with their own input maximum (pluck1 `* 1.3`, trainMove2 `litFull: 1.2`
   and `2.0`, multiBeatSynth1 `1.0`, miniMoog `2.0`, metal1 `* 0.5`). A
   scalar on `m` keeps sound and picture in step; a scalar inside `~next`'s
   calls would leave every held-event visual at the old sensitivity. The
   Pbind visuals that read `e[\amp]` follow either way.
2. **The `~plot` probes follow**, because they recompute from `m` too.
3. **It can be set from outside the file.** The hooks already exist and are
   all dead: `d.params.sensitivity` is set to 0.5 at connect and never read
   (`oscController.scd:374`); `controlViewX.scd` has a sensitivity slider and
   a `/airkit/cc/75` handler that writes a dictionary nobody reads.

Concretely, in `~processDeviceData`:

```supercollider
~model.accelMass = d.sensors.accelEvent.sumabs * 0.33 / (~model.sensitivity ? 1);
```

with `\sensitivity` defaulting into the model from `d.params.sensitivity`, an
OSC path `/airkit/sensitivity port value`, and a p-file allowed to override
it at the top of the file the way it overrides filter coefficients.

**Keep the gate in absolute units.** This is the one interaction to get right.
If sensitivity scales the mass, then thresholds written against the mass
(6.1, 6.2) scale with it, and at high sensitivity the sensor's noise floor at
rest walks through the gate. So compute `m.moving` and the `gatecurve`
threshold on the **unscaled** mass, and apply sensitivity only to the curve
input. Two controls, two jobs: the gate says *whether* the instrument is
playing and is anchored to the hardware; sensitivity says *how much gesture
is a full gesture* and is a musical setting. In practice that means the
model carries both `accelMassRaw` (for gates) and `accelMass` (scaled, for
curves), or `gatecurve` takes its threshold from the raw value.

**Standardise the input maximum while doing it.** With a global control in
place, the 0.1 to 0.5 cluster has no reason to exist: trainChooka2's
`lincurve(0, 0.1, ...)` becomes `lincurve(0, 2.5, ...)` at sensitivity 25,
and the file's curve becomes comparable to every other file's. 2.5 is already
the modal value (66 calls); make it the house full-gesture and let
sensitivity do the rest.

**Visuals specifically.** Three things to watch when sensitivity moves:

- Held-event draw functions that carry their own floors (`metal1`
  `drive < 0.02`, trainMove2's `lit` slew) are gates and should read the
  raw value, same rule as above.
- `\startSize, Pkey(\amp) * 400` style marks scale with amp, which is the
  intent. Marks that read `e[\amp]` through a fixed window
  (`(e[\amp] ? 0.2).linlin(0.15, 0.25, 40, 120)` in magicWand) will saturate;
  those windows were written for one sensitivity and should be widened or
  moved to dB.
- The `~plot` MODEL section should show scaled and raw mass side by side so a
  sensitivity change is visible as a change in the plot.

### 6.4 Gesture data on control buses

`d.sensorBus` already exists: a seven-channel `Bus.control` per device,
written on every packet with accel x/y/z and the quaternion
(`oscController.scd:262`), and **read by nothing**. So the infrastructure is
half there; what is missing is the model on it and any consumer.

Who benefits: the 38 architecture-A files that hold a synth and `.set` it at
100 Hz from `~next` (wind1/2, metal1, pluck1, velocity2/3, the train pad,
bee/bees, suz2/3, mel4, rain1's four layers). For those, `synth.map(\accel,
bus.subBus(n))` replaces the `.set` traffic, and the SynthDef's own `lag` or
`lagud` becomes the one smoothing stage instead of the second one.

Who does not: the 66 Pbind files. A pattern reads its values at event time,
which is what `Pdef.set` already does; a bus adds nothing there. And the
mapping curve would have to move into the SynthDef (`\accel.kr.lincurve(...)`)
for the mapped files, which is the opposite of the current rule that the
mapping sits in `~next` next to the probe that shows it.

So: extend `sensorBus` to carry the **model** (raw mass, scaled mass,
filtered mass, rrate, three gyro axes, `moving`), keep `~next` and `~plot`
as they are, and let drone files opt in with `.map`. Do it third, after 6.1
and 6.2, and only if the `.set` traffic is measurably a problem; the
100 Hz `.set` has not been the bottleneck in any commit message this year.

One thing to fix on the way: `oscController.scd:296` assigns
`sensors.quatCalibrated = q`, and `q` is not declared in that function, so it
resolves to the interpreter variable and the calibrated quaternion is never
stored. Nothing reads it, which is why it has not shown.

---

## 7. Other gaps in the material

Things the six months of files reveal about the practice, in rough order of
how much they would change what a p-file can do.

- **Rotation rate is a probe, not a driver.** It is on every `~plot` menu and
  in the model for every file, and it drives sound in about a dozen lines.
  The files where it is the primary driver are the odd ones out and the
  interesting ones.
- **Direction is discarded.** `accelMass` is `sumabs`; the model has no sign,
  no dominant axis, no "which way was that". `velocity` is computed as a
  leaky integral and never used. The four files that want an axis read
  `accelEvent` directly with their own scaling.
- **Cross-device sharing is harmonic only.** `m.com` has `accelMass` and
  `rrateMass` slots and no file writes them, so no personality can respond to
  another performer's energy. All 18 writers publish `root` and `dur`.
- **Per-device master level does not exist.** Balance between voices is an
  18-line collection of `a * 0.3` scalars after the mapping. `d.params.volume`
  is the unread hook.
- **Duplication in the sampler files.** `noteToMidi` is copied into 19 files
  and `findClosestSample` into 10. The Sep 1 refactor produced four clean
  files (celeste1, dulcimer1, harpsichord1, piano1) that still each carry the
  copy. A shared sampler library is the obvious next infrastructure step for
  the family that half the set belongs to.
- **Near-copies in the train quartet.** trainChooka2 vs trainChooka3 differ
  by 5 lines; trainMove2 vs trainMove3 by 64 out of 510. Variants are being
  made by copying the file, so a fix to one does not reach the other.
- **Dead probes.** 46 files have `~plot` lines against model fields that no
  longer exist. The Sep 2 "add ~plot code to all" commit fixed 10 files; the
  standard block should replace the rest.
- **Silence is under-used in the patterns.** `Rest` appears in 13 files.
  bongo1's five rests are the piece, and the authoring guide's own rule is to
  protect the silence, but the pattern layer mostly runs continuous
  sequences gated on and off from outside.
- **Per-voice reverb** in 17 files, each note carrying its own instance.
  droplet's tail-synth pattern is the template; it needs a device-level
  effects bus in the controller to become the default.
- **Injected helpers nobody calls.** `~smooth` and `~slope` have no direct
  callers; `~curveAbove` is defined three times and called zero. Helpers that
  live in the Environment do not get used; helpers on `Number` do. That is
  the strongest argument for 6.1 as a class extension.

---

## 8. Suggested order

1. `gatecurve` as a class extension, with `floor`. Migrate idioms (b), (c),
   (d) as files are touched. One afternoon, no sound changes.
2. `m.moving` with hysteresis in the model; replace the pause/resume blocks
   as files are touched. Add `[m.moving.binaryValue]` to the standard `~plot`
   block.
3. Sensitivity on the model, gate on the raw value, `d.params.sensitivity`
   wired to OSC and to the device view. Then normalise the 0.1 to 0.5
   `inMax` cluster to 2.5.
4. `d.params.volume` read as a per-device master, and the `a * 0.3` scalars
   moved into it.
5. Model on `sensorBus`, opt-in `.map` for drone files.
6. Shared sampler library for the pitched-sample family; then the reverb
   tail bus.
