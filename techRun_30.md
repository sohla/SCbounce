# techRun_30.md — Sensi-Test 2026-07-30 follow-up

Live working doc from the 2026-07-30 tech run. **Both sides append to this.** It's a task
list, not a report: add notes as they come up, tick boxes as they land, and move anything
that outlives the tech run into `COTF-ISSUES.md`.

Conventions match `COTF-ISSUES.md`: `- [ ]` open, `- [x]` done, plus a status tag —
`[open]` = engineering, `[steph]` = composer decision/patch, `[fixed]` = resolved and
deployed. Evidence is quoted as `file:line`.

Source: Steph's seven notes after the run. Every one is reproduced verbatim below.

---

## Read this first — three notes have a different cause than assumed

Written down so it doesn't get re-derived at the bench.

| The note said | Assumed cause | Actual cause |
|---|---|---|
| "it also has a bug when reloading or loading generally the samples dont get loaded. maybe channel issue?" | Channel mismatch, or buffers double-freed on reload | **Neither.** SC guards double-frees explicitly: `Buffer:freeMsg` nils `bufnum` before returning, and `ContiguousBlockAllocator:free` carries the literal comment *"this 'if' prevents an error if a Buffer object is freed twice"*. A second `~deinit` produces 15 warning lines and no server traffic. The real candidates are (a) **`Pdef:play` wraps the stream in `Pprotect`, so a single exception anywhere in the `\slotIdx` Pfunc or the event handler permanently ends the pattern** — no post, no retry, silent forever; (b) the 15 async `Buffer.read`s are never gated before `Pdef.play` (the file's own TODO at `cotf_percussionist1.sc:147`); (c) `\baseAmp` sitting under the 0.15 dead zone. All three are indistinguishable by ear. See task 1.0. |
| "patterns feel sluggish because of the grid" | Grid resolution is too coarse | **The grid is being truncated.** `out/Beethoven4_4_MSO_beats_2_mul4.txt` has `D` at line indices 0, 8, 16 → a score bar is **8** subdivisions. But every grid in `rawPatterns` is **16** chars, read via `grid.wrapAt(slot)` where `slot` can only reach 7. **Slots 8–15 have never sounded.** `kick: "K . . . k . . . k . . . K . . ."` gives two hits, not four; `fill_snare` plays only its first half. The grids were authored as 4/4 sixteenth grids and the score bar is 2/4, so one grid spans **two** score bars. Fix is the divisor (task 5.1) — *not* `\dur`. Halving `\dur` would reinterpret every grid as 32nds and turn `hat: "h h h h…"` into an 18.6 Hz buzz roll. |
| "the pattern and sounds seem to be slightly off. maybe there is a latency issue with this p-file? need to test." | Latency specific to this p-file | **No p-file-specific latency exists.** Nothing in `personalities/` sets `\latency`, `\lag` or `\timingOffset`; every event inherits `s.latency = 0.05` (`code3.0/cotf/config.scd:41`) exactly like every other personality. Nesting `.play` inside an event handler adds no delay — the handler runs synchronously at the outer event's scheduled beat. Three other things explain "off": the truncated grid above; the `sustain` collision (task 1.5); and **the `layers` indices probably point at the wrong samples** (task 1.7). |

### And three bugs that weren't in the notes

1. **`[open]` percussionist1 plays at full piece level during `idle` and `tuning`.**
   `~idleNext` (`cotf_percussionist1.sc:241`) and `~tuningNext` (`:250`) both end in
   `~pieceNext.(d, ctx)`, and the `Pdef(m.ptn).set(\baseAmp, 0)` gates are commented out at
   `:246` and `:252`. `~onRoomState`'s `\tuning` branch (`:229`) writes 0 exactly once, then
   the 30 Hz tick overwrites it ~33 ms later. So the drum kit is live through walk-in and
   tuning at piece amplitude — contradicting the file's own header (*"Silent at rest"*,
   `:4`) and its own comments. Two-line fix, biggest show risk in the file. → task 1.2

2. **`[open]` The hidden per-section kit swap has never fired.** `Event : Environment :
   IdentityDictionary` (verified in the class library), so `sectionToLayer` (`:69-76`)
   compares its keys by **identity**. Its keys are String literals; `ctx.sectionId` is a
   freshly parsed String — a different object. The lookup always returns nil, the
   `key.notNil` guard at `:315` always fails, and `currentLayerKey` stays `\default` for the
   entire piece. `code3.0/cotf/main_cotf.scd:231` already carries a warning about this exact
   identity trap. Same bug in `cotf_orchDrums1.sc:162-176`. → task 1.3

3. **`[open]` `~onResync` is installed into the wrong environment — this is the confirmed
   cause of the logged silences.** It's assigned at `cotf_percussionist1.sc:197`, which is
   *inside* the `topEnvironment.use {` opened at `:139` and closed at `:204`, so it writes
   `topEnvironment[\onResync]`. But `conductorController.scd:125` and `:138` dispatch via
   `d.env.use { ~onResync.(idx) }`, and `d.env` has its **own** `\onResync` key — the
   loader's no-op stub at `personalityController.scd:228`, assigned before `interpret` — so
   the parent chain is never consulted and the real hook is never called. percussionist1
   therefore gets **no Pdef restart on beat-clock anchor or re-anchor**, and because
   `TempoClock` queues by beat value while `~beatClock.beats = idx`
   (`conductorController.scd:115`, `:132`) re-bases without touching the queue, a backward
   jump strands the next event ~5 minutes out. That is exactly
   *"cotf_percussionist1 went silent twice needing a seat reset+reload"*
   (`COTF-CHANGELOG.md:327`).

   Mechanically decisive: percussionist1 is the **only** file in the repo with a non-empty
   `~onResync` at two-tab depth. All 20 siblings install theirs after the `use` block closes
   (`cotf_harp1.sc:146`, `cotf_orchDrums1.sc:299`, `cotf_drums1.sc:96`, …). → task 1.1

---

## 1. cotf_percussionist1

> "cotf_percussionist1 is the least develpoed. layers need volume control for all the samples
> as well as a time offset the pattern and sounds seem to be slightly off. maybe there is a
> latency issue with this p-file? need to test. it also has a bug when reloading or loading
> generally the samples dont get loaded. maybe channel issue?"

All in `personalities/cotf_percussionist1.sc` unless noted.

Steph also left a note block in the file itself at `:10-19` during the run, which maps onto
the tasks below: *"super basic atm"*, *"volume of indivdual samples"* → 1.8,
*"latency issue ?"* → the corrections table above, *"we need a synth?"* → 5.8/5.9,
*"lots of differenc patterns we need to text from code"* → 1.9. **Line numbers in this
section already account for that block** (everything below `:9` shifted by +10 on
2026-07-30 — re-check them if the file moves again).

- [] **1.0 `[open]` Diagnostic post — do this before anything else.** Post four values on
      load and on `~onBar`: `bufs.collect(_.numFrames)`, `Pdef(m.ptn).isPlaying`,
      `Pdef(m.ptn).player.streamHasEnded`, `Pdef(m.ptn).envir[\baseAmp]`. These separate
      "buffers empty" from "Pdef killed by `Pprotect`" from "amp gated below dead zone" —
      three causes that sound identical. Every task below is unverifiable without it.
      **Pass:** one line of four numbers on each bar.

- [ ] **1.1 `[open]` Move `~onResync` out of `topEnvironment.use`, with two guards.**
      Move the assignment to after the closing brace at `:204`, matching
      `cotf_harp1.sc:146`. Keep the body's own inner `topEnvironment.use {` so
      `~beatClock` / `~score*` still resolve.
      Two guards are mandatory, not optional:
      - `group !? { s.bind { group.freeAll } }` — a stale hook must not throw. Relevant
        because `personalityController.scd:403` clears `d.env[\onResyncHook]`, which is
        **always already nil**, while the live hook in `d.env[\onResync]` is never cleared.
        A throw inside `d.env.use` propagates out of the `~devices.keysValuesDo` loop and
        costs *every later seat* its resync for that anchor.
      - Gate the restart on `bufs.notNil` — after `~deinit` runs `Pdef(m.ptn).remove`
        (`:209`), a stale hook's `.play` **re-creates an empty Pdef** with an
        EventStreamPlayer that never dies.
      No `\tuning` skip: percussionist1 never pauses its Pdef, so it wants the restart in
      every state (same as harp1 and orchDrums1). Only pause-in-tuning personalities like
      `cotf_drums1.sc:96-105` need the skip.
      **Pass:** `/airkit/beek 0` → `2000` → `0`. Before: silent and stays silent. After:
      re-enters within one bar. Assert in the IDE that `~devices.at(9004).env[\onResync]` is
      the hook. Then `/airkit/resetSeat 4` + a seek → no `/g_freeAll` FAILURE, no
      `nil.freeAll` throw.

- [ ] **1.2 `[open]` Silence idle and tuning.** Restore the `set(\baseAmp, 0)` gates at
      `:246` and `:252` while keeping the engagement accumulation (either keep the
      `~pieceNext` delegation and re-zero after it, or split the accumulator out).
      **Pass:** by ear across `/airkit/state idle|tuning|piece` — silent in the first two.

- [ ] **1.3 `[open]` Fix the section-table lookup.** Symbol keys in `sectionToLayer`
      (`:69-76`) plus `sectionToLayer[ctx.sectionId.asSymbol]` at `:314`.
      **Pass:** the existing `[perc] section %` post at `:319` fires and `currentLayerKey`
      leaves `\default`.

- [ ] **1.4 `[open]` One PlayBuf, not two.** `PlayBuf.ar(2, bufnum, rate: [lr, lr * 1.0])`
      at `:126` multichannel-expands over `rate` into **two** stereo PlayBufs; `Out.ar` then
      expands over the nested array, so the bus gets `L+R` on one channel and `L+R` on the
      other. Every hit is a mono downmix played twice. Replace with
      `PlayBuf.ar(2, bufnum, rate: lr)` — true stereo **and** half the UGen count. (The
      siblings' `lr * 1.0017` only detunes the duplicate; each channel is still an L+R sum.)
      **Pass:** audibly stereo; UGen count per hit halves.

- [ ] **1.5 `[open]` Rename the `sustain` control.** The nested events (`:165-175`) pass no
      `~dur`, so Event's default `sustain = ~dur * ~legato * ~stretch` = `1.0 * 0.8` = **0.8**
      is resolved at `Event.sc:529` and emitted by `SynthDesc:makeMsgFunc` into `\percHit`'s
      `sustain` control (`:123`), overriding the SynthDef's `0.3` default. Every hit is
      currently 10 ms attack → 10 ms decay to **0.8** → hold → 1.0 s release: a fat plateau,
      not a 0.3 tail. Rename to `susLevel` rather than passing `sustain:` explicitly — in an
      Event, `sustain` is note *length* (it sets the gate-off time at `Event.sc:569`), so
      binding it to an envelope *level* would permanently couple loudness to duration.
      Same latent collision in `cotf_orchDrums1.sc:198` and every sibling with `sustain=` on
      an ADSR.
      **Pass:** changing `susLevel` changes the plateau; changing note length doesn't.

- [ ] **1.6 `[open]` Drop the dead `pan`.** Declared at `:122`, passed at `:171`, never read
      in the SynthDef body (`:124-128`). Either wire it to a `Pan2`/`Balance2` or remove the
      control. Same in `cotf_orchDrums1.sc:197-212`.

- [ ] **1.7 `[steph]` Audit the `layers` sample indices — likely the "slightly off" culprit.**
      `folder.entries` is `pathMatch` → `glob(3)`, whose sort is locale collation, and it
      **disagrees with `ls` on this exact folder**: `ls` gives `Tamb_ES_f_1.wav` before
      `TG1_ES_di_pp_mu_1.wav`, ASCII gives the reverse. So the index→file mapping tuned by ear
      may not be the one SC produces, and it shifts silently if a file is ever added or
      renamed. Concretely, `layers.default.snare = 3` is likely
      `HB1-L_trem_wool_mf_8s.wav` — a **6.15 s tremolo wool roll** being used as a snare —
      and `layers.recap.cymbal = 0` is likely `BE_a-due_20Is_mf.wav` at **19.65 s**. These
      are not transient-at-zero samples.
      Action: post `folder.entries.collect(_.fileNameWithoutExtension)` in `~init`, check the
      mapping by ear, then re-key `layers` by filename Symbol rather than index.
      **Pass:** each role sounds like its name.

- [ ] **1.8 `[open]` Per-sample gain / start / offset tables — the actual feature ask. Do
      this LAST.** Highest crash risk in the set: any nil lookup throws inside the handler,
      `Pprotect` kills the Pdef permanently, and we reproduce the exact symptom we started
      from.
      - `sampleGain`, `sampleStart`, `sampleOffset` **keyed by filename Symbol, not buffer
        index** (see 1.7), resolved to a parallel array once in `~init`.
      - Add a `roleGain` event too, since a per-role trim and a per-sample trim are different
        knobs: final amp = `v * base * sampleGain[name] * roleGain[role]`.
      - Add a `start` control to `\percHit` — it is the only sampler SynthDef in the repo
        without one. Use `startPos: start * BufSampleRate.kr(bufnum)` so **`start` is in
        seconds**. Do *not* copy harp1's normalised `start * BufFrames.kr(bufnum)`
        (`cotf_harp1.sc:72`): orchkit durations run 0.80 s to 19.65 s, so a fraction means
        20 ms on one file and 393 ms on another. This is the head-trim that makes a sample
        with pre-attack silence stop sounding late.
      - `sampleOffset` via the event's `\timingOffset`. This **does** work on a bare nested
        `( … type: \note ).play` — chain verified at `Event.sc:531 → :565 → :326 →
        SimpleNumber.sc:763`, and the gate-off is scheduled at `sustain + offset` so note
        length survives. Two constraints: units are **beats of `~beatClock`**
        (tempo-dependent, ~107 ms each — use `\lag` instead for a fixed-ms flam), and
        **negative offsets are impossible** (`clock.sched` fires ASAP on a negative delta;
        there is no look-ahead), so bias the whole table positive.
      - **`? default` on every single lookup.** A nil reaching `clock.sched` throws → the
        Pdef is dead for the rest of the show.
      **Pass:** soak a full score pass before this goes anywhere near a group.

- [ ] **1.9 `[open]` A way to audition patterns from code.** Steph's in-file note: *"lots of
      differenc patterns we need to text [test] from code"*. `rawPatterns` (`:97-114`) is
      currently a fixed Event selected only by tier, so trying a new groove means editing the
      table and reloading. Two cheap options, both using the existing mtime auto-reload:
      a settable `currentPatternKey` override (so `Pdef(m.ptn).set(\forcePattern, \myTest)`
      auditions one grid immediately), or a `~testPattern.("K . . . S . . .")` helper that
      parses and installs a grid at the prompt without a file save.
      Note the constraint from 5.1: a grid is **16 chars spanning two score bars**, so keep
      new patterns to that length or the wrap will fight the hypermeter.
      **Pass:** a new groove is auditionable without editing `rawPatterns`.

---

## 2. Synced, in-key idle states

> "all need a much more interesting idleNext state. so we need the patterns synced to the same
> clock and running so they can all play in time and in the same key. nothing shared like using
> the com structure though. i willl compose but will need to scaffold a little."

**The sync half is already done.** `~beatClock` (`conductorController.scd:93`) is a permanent
TempoClock created once per boot, PLL-locked to `/beat`, and **every** Pdef personality
already attaches to it with the same bar quant:
`Pdef(m.ptn).play(~beatClock, quant: ~scoreBeatsPerBar * ~scoreEventsPerBeat)`. That's why
all five seats are already bar-aligned. Nothing to build.

**What's missing is a key source in idle.** There is no `~root`, `~scale` or `~key` anywhere
in the repo. The only shared pitch state is `~scoreVoicePool`, and `\tuning` pins it to
`[69]` (`conductorController.scd:455`) — one note — while in idle the score walker isn't
running at all. `~onKey` and `~onScale` are dispatched by the conductor
(`conductorController.scd:266-268`) but are **empty stubs in every single personality**, so
key and mode are computed and thrown away.

**Approach: a pure function of `~beatClock.beats`.** No new globals, no shared mutable
state, no message passing — every seat agrees because every seat reads the same clock.

```supercollider
// copy into each p-file; no globals added
var idleHarmony = { |beats|
    var cycle = [ [0,4,7,11], [5,9,0,4], [7,11,2,5], [2,5,9,0] ];
    cycle[(beats div: 32).mod(cycle.size)]
};
// in the Pbind or idleNext:
\root, Pfunc { idleHarmony.(~beatClock.beats).choose + 60 }
```

Each p-file still picks its own octave, voicing, rhythm and register independently.

- [ ] **2.1 `[open]` Write the `idleHarmony` scaffold** and add it as a numbered recipe in
      `code3.0/concert_p_files.md` so it's copy-in, alongside the existing §17 amp palette.
- [ ] **2.2 `[steph]` Compose the chord cycle** and the bar length of each step.
- [ ] **2.3 `[steph]` Per-p-file idle voicing** — which p-files take which register / rhythm
      / density so the five seats sit together rather than doubling.
- [ ] **2.4 `[open]` Reduce idle start latency where it bites.** `quant: 8` on a clock at
      ~9.3 beats/s is up to **833 ms of dead air** on every `.play`/`.resume` — felt on state
      entry in the pause-in-tuning personalities (`cotf_drums2.sc:136`,
      `cotf_celesta1.sc:220-235`). `quant: ~scoreEventsPerBeat` (next beat) or `quant: 1`
      (next subdivision) are both available; the bar quant is a copied default, not a
      requirement. Leave resync at bar quant — a seek is allowed to be silent for a bar.
      **Reference material worth reading before composing:** the most interesting existing
      idles are `cotf_whisperer1.sc:132-149` (inverted tier — holding *still* opens the triad,
      motion collapses it to the root, plus a stillness-reveal ghost echo after 10 s) and
      `cotf_cascade1.sc:249` (no Pdef at all — direction-reversal detection fires
      `SystemClock.sched`ed arpeggios). Loudest current idle is `cotf_harpsichord2.sc:193`
      at a **−4 dB** peak.

---

## 3. Threshold-based amp mapping

> "dynamics. we need a more threshold based amp mapping which i will explain test and then
> replace for all instruments."

`[steph]` — blocked on Steph's explanation. Recording the current state so the replacement
has a baseline.

**How it works today.** Two scalars per tick at ~33 Hz
(`personalityController.scd:117-152`): `accelMass = accelEvent.sumabs * 0.33` and
`rrateMass = rrateEvent.sumabs`, each through a one-pole with separate attack/decay
(`~smooth`, `:221-226`). Each personality then hand-maps with `.lincurve` and hand-tunes the
endpoints live — the mtime auto-reload (`:244-248`) makes save-to-hear instant, which is why
tuning has been done this way.

**The problem with the status quo:**
- Peaks span **−25 dB** (`cotf_marimba2.sc:325`, idle) to **+4 dB**
  (`cotf_simple2.sc:137`, piece), with no shared trim anywhere between them.
- `\amp` semantics differ per file — some dB-domain via `.dbamp`, some raw linear.
- Ad-hoc post-multipliers stacked on top of the curve, and proliferating —
  `cotf_test3.sc` alone now has `amp * 5` (`:173`), `amp * 3` (`:197`), `amp * 1` (`:208`)
  and `amp * 20` (`:213`); plus `cotf_drums4.sc:186` `amp * 1.4`. These make the dB endpoints
  above meaningless as a comparison, which is the strongest argument for a shared mapping.
- Dead zones are hardcoded per file and inconsistent: 0.15 (percussionist1/orchDrums1 piece),
  0.12 (percussionist1 curtain), 0.05, `> 0.5` accel gates in the drums/test1 family, and a
  **non-zero floor** of 0.4 in `cotf_drums1.sc:153` so any triggered hit is loud.
- The documented §17 palette (`code3.0/concert_p_files.md:738-770`) is advisory and several
  files have drifted off it.

**There is already a home for a shared implementation.** `personalityController.scd:77-98`
holds commented-out threshold machinery from an earlier design —
`\rrateMassThreshold` (0.21, with a ControlSpec), `\accelMassAmpThreshold` (2.0), `\isHit`,
`\isMoving`, `\accelMassAmp`. Reviving that as the shared primitive is preferable to editing
20 files, and it keeps the mapping in one reviewable place.

- [ ] **3.1 `[steph]` Explain and bench the intended threshold mapping** (knee position,
      floor behaviour, whether the threshold is per-personality or global).
- [ ] **3.2 `[open]` Implement it in the shared model** at
      `personalityController.scd:77-98` rather than per-file, with defaults that preserve
      current behaviour (CLAUDE.md rule 6).
- [ ] **3.3 `[open]` Migrate personalities onto it**, one commit per batch, and update §17
      in `code3.0/concert_p_files.md` so the doc and the code agree.
- [ ] **3.4 `[open]` Normalise the `\amp` convention** (pick dB or linear) and delete the
      ad-hoc post-multipliers as each file migrates.

---

## 4. Compressor for peak control

> "we will want to add a compressor to all synthdefs, or to the group after the synths so we
> have contorl over the peak audio output."

**Decision: per-seat, in `\cotfMonitor`.** There is already a per-seat monitor synth at the
tail of every seat's private bus (`code3.0/cotf/main_cotf.scd:81-87`, created per seat at
`:97-111`), so the insert point exists and needs no new buses and no node-ordering work.

Signal chain per seat today:

```
personality Group --Out.ar(~outBus)--> ~cotfSeatBus[seat] (2ch private Bus)
   ├── \cotfMeter   (Synth.tail, :123)  — taps PRE gain/trim/master
   └── \cotfMonitor (Synth.tail, :103)  — gain × trim × masterGain → final / physical out
```

Insert the compressor **right after `In.ar(in, 2)` and before the gain multipliers**. That
ordering matters:
- staff mute (`gain`), the room pad (`trim`) and `/airkit/masterLevel` (`masterGain`) all
  still act on the compressed signal, so none of their semantics change;
- `\cotfMeter` taps `~cotfSeatBus` upstream of the whole monitor, so
  `/airkit/getLevels` keeps reporting **true pre-compression peaks** — a compressor can't
  fake healthy seats.

One SynthDef edit covers all five seats. There is precedent for the UGen choice inside the
drum SynthDefs already: `Compander.ar(sig, sig, thresh: -15.dbamp, slopeBelow: 1,
slopeAbove: 0.5, clampTime: 0.01, relaxTime: 0.01)` (`cotf_drums1.sc:34-40`, and identically
in drums2/3/4 and `cotf_orchDrums1.sc:204`). Nothing in the repo currently uses `Limiter` or
`Normalizer`.

**Trade-off to be aware of:** this is COTF-profile-only, per CLAUDE.md rule 6. The composer's
`main.sc` GUI path gets nothing from it (`main.sc:78` sets `s.volume = -2`, which the headless
profile never runs). If peak control is wanted on the composer machine too, that's a second,
separate change.

- [ ] **4.1 `[open]` Add `Compander`/`Limiter` to `\cotfMonitor`** after `In.ar`, with
      threshold and ratio exposed as controls so they're tunable live from the IDE.
- [ ] **4.2 `[steph]` Set threshold/ratio by ear** per room (Room 2 already carries a −10 dB
      `trim` pad at `main_cotf.scd:93`; Room 3 headsets are unpadded).
- [ ] **4.3 `[open]` Confirm `/airkit/getLevels` still reports pre-compression peaks** after
      the change — the meter's whole purpose (`main_cotf.scd:120-122`).
- [ ] **4.4 `[open]` Note the change in `COTF-CHANGELOG.md`.** No OSC surface change, so
      `API.md` is untouched.

---

## 5. Synth + pattern in every p-file; sound for where the stick moves

> "every p-file shouold have a synth and pattern setup. we need patterns to also have some
> sound for where airstick moves. patterns feel sluggish because of the grid."

Three separate things.

### 5a. The grid — one token

- [ ] **5.1 `[open]` Fix the grid divisor.** In `cotf_percussionist1.sc:187`, change
      `barLen = ~scoreBeatsPerBar * ~scoreEventsPerBeat` (= 8) to a named `gridLen = 16`.
      **Keep `\dur, 1` and keep `quant` as they are.** Because the slot derives from absolute
      `~beatClock.beats`, grid phase is anchor-relative and independent of when the stream
      starts, so `quant: [8, phase]` still gives one-bar re-entry after a seek. If the
      hypermetric phase lands on the wrong bar of the pair, `phase = 8` is the entire tuning
      surface.
      Keep `.round`, don't switch to `.floor`: `~beatClock.beats` returns exact logical beats
      via `_TempoClock_Beats` when `thisThread.clock == ~beatClock`, so both agree — but
      `.round` absorbs a −1 ulp error where `.floor` would silently drop a slot.
      Zero extra synths, zero level change.
      **Pass:** grooves read as grooves — the med snare's second hit and the offbeat hats
      appear; the high kick gives four hits across the pair of bars instead of two.
- [ ] **5.2 `[open]` Same fix in `cotf_orchDrums1.sc:286-290`** — identical truncation. Its
      `march` pattern `kick: "K . . . . . . . K . . . . . . ."` is currently a single kick
      per bar because the second one is unreachable.
- [ ] **5.3 `[open]` Correct the headers that describe behaviour the engine doesn't deliver**
      — `cotf_percussionist1.sc:6` ("16-slot bar"), `cotf_orchDrums1.sc:6` and `:80`
      ("16 slots per bar (16th-note resolution)", "Use 16 chars per bar"). After 5.1 these
      become true; make sure the doc states the two-score-bar span explicitly.

### 5b. Sound for where the stick moves

There is **no position data at all** in the OSC contract — `/N/IMUFusedData` carries linear
acceleration and an orientation quaternion, nothing absolute (`code3.0/API.md:26-32`).
`d.sensors.velocity` exists but is scaled `* 0.3` **every packet** (`oscController.scd:348`),
so it decays to ~0 within a few frames and is unusable; no `cotf_*` personality reads it.

So "where the stick is" in practice means **tilt**: `gyroEvent.y / pi.half` ∈ [−1, 1].
(Despite the name, `gyroEvent` is absolute orientation in radians, not rate —
`oscController.scd:315-318`.) Most personalities already use it — tilt→octave
(`cotf_harp1.sc:267`), tilt→3-band kit voicing (`cotf_orchDrums1.sc:376-380`), tilt→sample
choice (`cotf_drums1.sc:151`).

**percussionist1 uses no tilt at all** — it is activity-magnitude only (`:256-281`). So the
note is literally true for it, and for `cotf_simple1.sc` (fixed `freq 69`).

- [ ] **5.4 `[steph]` Decide what tilt should control in percussionist1** — kit voicing like
      orchDrums1, or pattern selection, or a pitched layer.
- [ ] **5.5 `[open]` Add off-grid gestural one-shots alongside the running pattern.** The
      documented recipe is `code3.0/concert_p_files.md` §20 (`:861-923`) "layered running-Pdef
      + gestural one-shots in the same state". A live example is `cotf_celesta1.sc:266-284`
      (direct `( … ).play` when `m.accelMassFiltered > 0.5`, throttled at 0.2 s). This is the
      real answer to "sluggish": pattern events are locked to a **107 ms lattice**, so a hit
      is on average ~50 ms late no matter what; one-shots fired from the 33 Hz tick are not.
- [ ] **5.6 `[open]` Consider rotation-driven subdivision** — §19 (`:823-858`),
      `Pdef.set(\dur, m.rrateMassFiltered.linexp(0.01, 1.0, 2.0, 0.25))`. Motion changes
      rhythmic density rather than only amplitude.
- [ ] **5.7 `[open]` Fix the dead-zone/grid-rate mismatch.** `\baseAmp` is written at 33 Hz
      but only *read* at ~9.3 Hz, so a short flick can rise and fall entirely between two
      grid slots and make no sound at all. Either latch the peak between reads or let
      one-shots (5.5) carry the fast gestures.

### 5c. Synth + pattern in every p-file

- [ ] **5.8 `[steph]` Confirm the target shape.** Currently the 16 show patches split into
      pattern-only (harp, celesta, dulcimer, marimba, harpsichord, percussionist, orchDrums,
      test1/2/3, simple3/4, voice1), synth-only (simple1, simple2), and no-pattern-at-all
      (`cotf_cascade1.sc`, which fires `SystemClock.sched`ed arpeggios;
      `cotf_whisperer1.sc`, three long-lived voices). Does "every p-file should have both"
      include cascade and whisperer, or are those deliberate exceptions?
- [ ] **5.9 `[open]` Add a pattern to `cotf_simple1`/`cotf_simple2`** once 5.8 is answered.

---

## 6. Better synthdefs

> "basic synths need to be replaced with better sounding synthdefs. i have some."

`[steph]` names the pairings; porting is engineering.

`synths/` holds ~78 candidates, including `blendOsc.scd`, `jupiterEight.sc`, `buchla.sc`,
`arpOdyssey.sc`, `dMau5.sc`, `membraneDrum.sc`, `largeGong.sc`, `metalBamboo.sc`,
`bEno.sc`, `grainPlay1.sc`, and the three already-COTF-shaped
`harp_grain_{grainbuf,tgrains,warp1}.scd`.

The "basic" synths to replace are `\simple` (`cotf_simple1.sc:27`, `simple2:29`,
`simple3:31`, `simple4:40`, `cotf_test2.sc:34`) and `\drumkitt`* (drums1–4).

- [ ] **6.1 `[steph]` Name the pairings** — which `synths/` file replaces which p-file's
      SynthDef.
- [ ] **6.2 `[open]` Port each one.** Checklist: arg-name mapping to the existing Pbind/tick
      keys; `Out.ar(out, …)` routed via `~outBus` (captured as `var ob = ~outBus ? 0;` at
      file scope — **must** be captured before `~init`, which runs under
      `topEnvironment.use`); `\group, group` threaded so `~onResync`/`~deinit` can
      `s.bind { group.freeAll }`; a `gate` arg with `doneAction: 2` so voices free
      themselves; and the dB-vs-linear amp convention settled per task 3.4.
- [ ] **6.3 `[open]` Give every SynthDef a unique name.** `\stereoSampler` is currently
      defined in **6** files (harp1, celesta1, dulcimer1, marimba1, marimba2, test1),
      `\simple` in **5**, `\stereoSamplerH` in **2** — all last-loader-wins on the server, so
      two seats on different patches can be playing each other's synth. Event *types* were
      already uniquified per env for exactly this reason (`cotf_harp1.sc:41-43`: *"multiple
      personalities sharing `\customEvent` clobber each other's handlers (last loader wins →
      cross-device sample bleed)"*); SynthDef names never got the same treatment. Apply the
      same `(\name_ ++ m.ptn).asSymbol` trick, or agree a naming convention.
- [ ] **6.4 `[open]` Bench each replacement under the conductor before it goes in the show
      list.** Per CLAUDE.md, the sampler personalities have historically never been proven
      under the conductor.

---

## 7. Ordering — and what actually fits

> "need to do most of this in the next 2 hours."

**Straight answer: six items do not fit two hours.** What fits is roughly item 1 plus the
grid half of item 5 — call it tasks 1.0 through 1.6 and 5.1, ~75 minutes with testing
between each. Items 2, 3, 4 and 6 are fully specced above and ready to pick up, but 3 and 6
are blocked on Steph and 2 needs composition time.

Suggested order, by certainty × payoff × blast radius:

| # | Task | Why here |
|---|---|---|
| 0 | **1.0** diagnostic post | 5 min, no behaviour change. Everything else is unverifiable without it. |
| 1 | **1.1** `~onResync` + guards | Confirmed cause of the logged silences. Smallest diff in the set. |
| 2 | **1.2** idle/tuning silence | Two lines. Certain-to-be-noticed problem. |
| 3 | **1.3** section-key Symbol fix | Unlocks a feature that has never once run. |
| 4 | **1.4–1.6** `\percHit` voicing | Changes the sound. Do it **before** the grid so the grid is judged on the corrected envelope — and it halves UGen count first. |
| 5 | **5.1** grid divisor | One token. Big audible change, zero extra synths, zero level change. |
| 6 | **1.7–1.8** sample audit + gain tables | **Last.** Highest crash risk: any nil lookup throws inside the handler and `Pprotect` kills the Pdef permanently — reproducing the original symptom. |

Two things explicitly **not** to do in a two-hour window:

- Don't halve `\dur` to fix the grid. It reinterprets every pattern as 32nds, roughly doubles
  concurrent voices (each hit lives ~1.1 s), and adds **+3 to +6 dB** on the seat bus with no
  compensating trim and no limiter in place yet.
- Don't ship an index-keyed, unguarded gain table. See 1.7 and 1.8.

---

## Cross-file backlog

Found while diagnosing the above. Each is the same class of bug in a sibling file; none is
tech-run-blocking, all should migrate to `COTF-ISSUES.md`.

- [ ] **`[open]` `cotf_orchDrums1.sc` shares every percussionist1 bug** — grid truncation
      (`:286`), String-keyed section table (`:162`), `sustain` collision (`:198`), dead `pan`
      — **and** reads the all-stereo orchkit folder with `PlayBuf.ar(1, …)` (`:201`), so it
      is probably silent on the server. It is index 13 in `lists/list_cotf.sc`, i.e. in the show.
- [ ] **`[open]` `cotf_simple3` / `cotf_simple4` define no `~onResync` at all** → no Pdef
      restart on seek for those seats either. The generic fallback inside
      `~cotfResyncDispatcher` (`main_cotf.scd:48-57`) does **not** cover them, because
      nothing reads `topEnvironment[\onResync]` — see below.
- [ ] **`[open]` `~cotfResyncDispatcher` is dead code on the live path.**
      `conductorController.scd:105-107` says *"~onResync is now dispatched per-device… No
      global slot"*, and `:125`/`:138` always resolve `d.env[\onResync]`, which always exists.
      So `topEnvironment[\onResync]` is never read. Consequently `captureResyncHook`'s guard
      (`personalityController.scd:327`) fails for all 20 personalities that install into
      `d.env`, `d.env[\onResyncHook]` is always nil, and `unLoadPersonality:403` clears a key
      that is always already nil — **while the live hook in `d.env[\onResync]` is never
      cleared**. Stale hooks fire on every anchor after a `resetSeat`/`panic`, which is where
      the `FAILURE IN SERVER /g_freeAll Group N not found` lines come from, and a throw there
      truncates the resync loop for every seat later in iteration order. Either delete the
      dispatcher or clear the right key.
- [ ] **`[open]` `~deinit` nils `group`/`bufs` only after 16 `s.sync` round-trips**
      (`cotf_percussionist1.sc:208-223`, and the same late-nil ordering in all five sampler
      siblings). `resetSeat` → `unLoadPersonality` runs `~deinit`, M0 re-pushes ~300 ms later,
      and `loadPersonality` (`personalityController.scd:343-351`) runs `~deinit` **again** on
      the same still-current `d.env` — inside that window both notNil guards pass. Worth
      3 lines of log hygiene (capture to temporaries, nil immediately). **This is not the
      samples fix** — see the corrections table — and must not be scheduled as if it were.
- [ ] **`[open]` `loadPersonality` doesn't await `~deinit` before `~init`**
      (`personalityController.scd:343` vs `:361` — two racing Routines). Not a buffer hazard
      (`freeMsg` releases the block and sends `/b_free` with no yield, and scsynth serialises
      async commands) nor a node hazard (`NodeIDAllocator` is monotonic from 1000). It costs
      ~75 ms of contention on the server's async thread, which delays the ungated buffer reads.
      Shared-controller change — needs care per CLAUDE.md rule 6.
- [ ] **`[open]` `~devices.at(port)` unguarded** at `personalityController.scd:335`, then
      `d.index` at `:336`. This is the changelog's `'index' not understood, RECEIVER: nil`
      when a `/airkit/loadPersonality` arrives before the seat's device exists. A `d.notNil`
      guard makes pre-power-on loads harmless no-ops.
- [ ] **`[open]` Off-by-one in the personality list index.**
      `personalityController.scd:354` does `index.mod(devicePersonalityList.size-1)` — mods by
      **17**, not 18 — so `lists/list_cotf.sc`'s trailing `"silence"` at index 17 is
      unreachable and wraps to index 0.
- [ ] **`[open]` `~onKey` / `~onScale` are empty stubs in every personality**, so the
      conductor's computed key/mode/scale (`conductorController.scd:266-268`) is discarded.
      Relevant to item 2 if the idle harmony should ever track the score's key.
- [ ] **`[open]` Stale `CLAUDE.md` warning.** It still lists as a known open issue that
      *"`cotf_harp1` goes silent in `piece` even after a clean seek… the sampler personalities
      have never been proven under the conductor"* (2026-07-15). Harp has been in the show
      since; confirm and update or re-confirm the warning.

---

## Bench procedure

Gathered from `CLAUDE.md` so it's in one place during the run.

1. `git pull --ff-only` before starting and before every push. Never force-push or rebase
   on AirConcert.
2. `pm2 stop cotf-airkit-room3`, then evaluate `code3.0/cotf/main_cotf.scd` in the IDE
   (`COTF_ROOM=3`, `COTF_CONDUCTOR_AUDIO=0` when QLab owns the Beethoven audio).
3. **Load personalities from the Patches admin page, not by hand.** M0's `/airkit/getSeats`
   reconcile reverts a hand-loaded personality within ~10 s outside `piece`.
4. **No Cmd-. while the profile is up.** It kills the per-seat monitor synths, every
   personality process loop and the score walker, with no auto-rebuild. Recovery:
   `Server.killAll`, reboot the interpreter, evaluate `main_cotf.scd` once.
5. File-save auto-reload (`personalityController.scd:244-248`) polls mtime and re-sends
   `/airkit/loadPersonality`, so every edit is audible immediately — this is what makes
   live tuning practical.
6. Device port = 9001 + seat − 1. The post window's "device auto detected : N" prints the
   index/seat, **not** the port.
7. `FAILURE /n_set Node not found` right after a reload is a known transient, not a cause.
8. After a `/airkit/panic`, seats report personality `none` until M0's monitor re-pushes
   (~10 s). A stick played in that window is silent — expected, not a new bug.
9. Commits prefixed `cotf:`, small and per-task. Any OSC behaviour change updates
   `code3.0/API.md` in the same commit (nothing in this doc changes OSC). Push batches get a
   plain-language entry in `COTF-CHANGELOG.md`.
