# COTF changes — plain-language log for Steph

One entry per push batch from the Concerts of the Future side, newest first: what we changed,
why, and what (if anything) behaves differently on your machine. The intent is that nothing
here ever changes how AirKit behaves for you — if it does, that's a bug, tell us.

## 2026-07-24 — Multi-device ~onResync capture (cotf)

**Nothing changes for your solo/GUI workflow.** Your new state-aware `~onResync` hooks
(celesta, drums, marimba, dulcimer, harp) are exactly right — this makes them work when
*five* devices are loaded at once. As written, each personality's install into
`topEnvironment` overwrites the previous device's hook, so in the show only the
last-loaded seat would get the clean stop → `group.freeAll` → conditional restart on a
seek; the other four would dump their TempoClock backlog (node bursts / stuck notes).

- `personalityController.scd`: after `~init` runs, a small `captureResyncHook` moves the
  hook the personality just installed into that device's env and restores our dispatcher.
  **Guarded on the COTF profile being active** — in your GUI (`main.sc`) there is no
  dispatcher, the guard fails, and behaviour is byte-for-byte what `concert_p_files.md`
  §6 documents. Also: unloading a personality now drops its captured hook (a survivor
  would `freeAll` a group `~deinit` already nil'd).
- `cotf/main_cotf.scd` (ours): the old generic fan-out is now a dispatcher that prefers
  each device's own hook (run under `topEnvironment` so `~roomState`/`~beatClock`
  resolve) and falls back to the generic stop/replay for personalities without one
  (e.g. `cotf_simple*`).
- `concert_p_files.md` §6: short note added — keep installing into `topEnvironment`
  exactly as you do now; no p-file changes needed, ever.

Also confirmed on our side while pulling your batch: the new `/Config/GetConfig` probe in
`addDevice` lands on the COTF router's fixed source port (9001) — it's ignored gracefully
there, and since `/airkit/addDevice` only feeds GUI views (which the headless profile
never loads), the gated emission is harmless in production.

## 2026-07-20 — Research data collection note (cotf, docs only)

**Nothing in AirKit changes.** We've started collecting anonymised movement data from the show
for a CHI/NIME-oriented paper on DMI evaluation — a flag-gated tap in the COTF server's router
on M0, upstream of your input, feeding a brand-new separate process (`cotf-research`) with its
own database. No new OSC address, no code on M1, no change to what reaches your devices. Added
`docs/cotf-research-plan.md` (one page: study framing, the 8-feature movement battery, survey
items, the anonymisation model, and co-authorship) in case you want the detail or want to be a
co-author. If the research collector is ever off or crashed, nothing about AirKit's behaviour
changes — it's designed to be invisible from your side.

## 2026-07-19 — Day-1 fixes: per-seat meters, seat reset, and panic (cotf)

Three new additive OSC commands on `\cotfMonitor` in `main_cotf.scd` (your `main.sc` untouched),
all born from real-world issues discovered 2026-07-18. **Nothing changes for your workflow** —
these are staff-side operational tools.

- **`/airkit/getLevels`** — replies `/airkit/levels/reply <room> <p1..p5>` with per-seat linear
  peak amplitude since the last poll, tapped on the seat bus **before** any gain/trim/master
  multiplier. Your patches are untouched. Why: Room 3 is loud; staff had no way to see whether a
  patch was genuinely silent, broken, or just playing quietly. Now the staff iPad shows a live
  meter per seat (updates ~1 Hz, lightweight), so a dead patch is visible immediately.
- **`/airkit/resetSeat <seat 1-5>`** — tears down one seat only (ducks the monitor gain
  mute-aware via a new `~cotfSeatGain` dict, stops the Pdef, calls unload via the shared path,
  sets `d.name = "none"`). **No reload in SC.** When the M0 server sees a seat reset, it
  re-pushes the saved personality ~300 ms later — the seat re-enters like any mid-piece load, the
  conductor and beat clock stay locked, and the other four seats keep playing without interruption.
  Why: two 2026-07-18 experiences had a seat go completely silent mid-show (groups 27–29), and
  the only safe recovery is per-seat reload, not a full-room engine restart.
- **`/airkit/panic`** — fires `/airkit/resetSeat` for all five seats in sequence (no delay
  between). Our server fires this on every room transition and at close-down, so hanging voices
  die cleanly without requiring an engine stop. Why: a harp lingered in Room 2 long after its
  performer had moved to Room 3 — likely an un-released voice or held OSC value. This might be
  the same family as the stuck note at end-of-day, which we'll track alongside.
- **New `COTF-ISSUES.md`** — shared issue tracking for you and us. The first entries show what we found
  on 2026-07-18 (stuck note, hanging voices, a dead seat, and your compositional balance notes);
  feel free to add and comment. Checkbox on each when it's closed.

All three commands ship in commits `08096fe` + `7aa1cac` + fix `7fcd505`, both in `code3.0/cotf/`
only. `API.md` updated to match.

## 2026-07-18 (overnight) — Master level + speaker-test tone + state single-writer (cotf)

Two small additive OSC controls on `\cotfMonitor` (both rooms, `main_cotf.scd` only — your
`main.sc` untouched), plus a change in *which* states our server sends (no AirKit code change):

- **`/airkit/masterLevel <linearGain> [fadeSec=0.5]`** — per-room overall output level, a new
  independent `masterGain` multiplier on every seat monitor (alongside `gain`/voiceMute and the
  Room 2 `trim` pad, never through them). Default 1 = exactly today's loudness; the level is
  dialled from our admin iPad (−24…+6 dB) and re-pushed automatically after every engine restart,
  same as seat personalities. Never receiving the message = nothing changes.
- **`/airkit/testTone <seat 1-5> [durSec=1.0]`** — a short sine burst into that seat's monitor
  input bus, for our start-of-day speaker test (proves the whole chain incl. masterGain +
  outputMode). One-shot, self-freeing, harmless to a running piece but we only fire it between
  groups.
- **State single-writer (server-side change, heads-up):** our server now sends **only
  `/airkit/state idle`** (on Room 3 arrival and on stop/reset/clear). `tuning`/`piece` come from
  the QLab timeline cues, `curtain` from your conductor's own score-end trigger — so AirKit will
  no longer see a server `tuning` on group walk-in or a server `piece` at staff Start. API.md's
  transport-orchestration table updated to match.

## 2026-07-17 — Room 3 physical chair speakers (cotf)
- **Fix (same day, bench-caught):** the outputMode whitelist used `Array.includes`, which is identity-based in SC and always false for Strings — the env default and the OSC command were both silently ignored. Now `includesEqual` (commit 9ce7500). Verified live on M1: boot line reads `outputMode physical`, crossfade proven by ear both directions.
- `\cotfMonitor` now dual-writes: stereo → BlackHole (webrtcGain, unchanged path) + mono sum → MOTU outs 12–16 per seat (physicalGain). Exactly one gain active; crossfade via new `/airkit/outputMode <mode> [fadeSec]` (Room 3 only, additive). Boot default from `COTF_OUTPUT_MODE` env; absent env = webrtc-only, i.e. behaviour before this change. Nothing changes for main.sc / Room 2.

## 2026-07-16 — Docs-only: source-port convention + transport orchestration + Room 2 monitor heads-up

No code changes at all, just catching `API.md` up to how our side actually talks to yours — nothing
changes for your `main.sc` workflow.

- Documented our sender-side convention: our router binds a **fixed source port 9001** and
  addresses devices as `devicePort = 9001 + seat − 1` (seat 1 = 9001 … seat 5 = 9005). That's the
  stable source port your "device = source port + N−1" rule has been keying off already — just
  writing it down.
- Added a **"COTF transport orchestration"** section spelling out exactly when we send what:
  `state tuning` on Room 3 arrival, `state piece` before the QLab cue fires, `state idle` on
  stop/reset/clear, and a reminder that **we never send `/airkit/go`** ourselves — QLab drives
  transport via the Network cue, we only drive `state`.
- Heads-up: our drift monitor now also polls `/airkit/getState` + `/airkit/getSeats` against your
  Room 2 instance on port 57121 every ~10s (it already did this against Room 3 on 57120) — so
  you'll start seeing periodic queries land on the Room 2 instance too. Read-only, same polling
  pattern as before, nothing it does can touch a seat mid-piece.

## 2026-07-16 — Room 2 warm-up output pad (−10 dB)

Purely a level trim on our Room 2 (warm-up) boot path — **nothing changes for your `main.sc`
workflow or for Room 3.**

- **`cotf/main_cotf.scd`**: the `\cotfMonitor` SynthDef gained a fixed `trim` arg (default 1,
  i.e. no change) multiplied alongside the existing `gain`. On COTF Room 2 only (`COTF_ROOM=2`)
  we set `trim = 0.3162` (−10 dB) when we spawn the per-seat monitors, because the warm-up
  voices share one MOTU 9/10 cable to the Room 2 Genelecs with our ambient bed and were running
  hot. `gain` (the per-seat voiceMute, 0/1) is untouched and still owns muting; `trim` is a
  separate baked-in pad that survives every mute/unmute. Room 3 keeps `trim = 1`.

## 2026-07-15 — merged your reload fix + logging; added roster query

Merged your `reload p-file bug fix` and `welcome and device details logged` commits — clean
merge, no conflicts, nothing on our side touched the same lines.

- **`/airkit/getRoster`** (new) — replies `/airkit/roster/reply name1 name2 ...` to the sender
  with the loaded personality names in list-index order, so our admin UI can list what's
  available. Lives in `personalityController.scd` next to `loadPersonality` (that's where the
  list is actually in scope). Additive, replies only to whoever asks — no behaviour change for
  your `main.sc` workflow.

## 2026-07-14 (later) — headset latency bench

Chasing the gesture→headset delay (the VR headset leg, not your patches — those feel tight on
the room speakers). **Nothing changes for your `main.sc` workflow** — both edits are in our
`cotf/` boot path only.

- **`cotf/config.scd`**: the small-IO-buffer request is now **opt-in** (env `COTF_HW_BUFFER`;
  nothing set = device default). The first version unconditionally requested 128 samples and,
  against our drift-corrected Aggregate device, produced a server that booted and ticked while
  emitting pure silence on every channel — and left the loopback driver wedged machine-wide
  until a `sudo killall coreaudiod`. Your `main.sc` doesn't load this file, but if you ever see
  "server up, meters moving, zero sound" on an aggregate device, that's the shape of it.
- **`cotf/main_cotf.scd`**: a comment documenting our bench measurement tap (one eval line that
  mirrors seat 1's monitor onto a room speaker so we can phone-record speaker-vs-headset onset
  gaps). No behaviour change.

## 2026-07-14 — AirKit becomes the COTF show engine (bench day 1: it works!)

We ran a real AirStick through the venue routing into AirKit on our audio Mac, playing
`cotf_simple1` locked to Beethoven fired from QLab, heard in a VR headset. Sounding great.
Everything below supports that. **Nothing changes for your normal `main.sc` workflow** — every
edit defaults to today's behaviour on a standalone machine.

- **`CLAUDE.md`** (new) — house rules for this shared branch (both of us push here): pull before
  push, no history rewrites, our commits prefixed `cotf:`, and OSC compatibility rules. Also a
  machine-setup checklist (quarks, samples, data files) learned the hard way.
- **`code3.0/API.md`** rewritten as the living OSC contract — every address in and out, including
  the new COTF ones. Rule we hold ourselves to: any commit that changes OSC updates this file in
  the same commit. Yours to lean on too.
- **Hardcoded `57120` → `NetAddr.langPort`** everywhere — identical on your machine (your langPort
  IS 57120); lets us run a second AirKit instance for our warm-up room later.
- **`~conductorAudioEnabled`** (default `true` = you) — when `false`, the conductor walks the
  score/beat clock but doesn't play the Beethoven file locally, because QLab owns the audio at
  the show. Also fixed a crash we caused: seeking twice in that mode threw "Cannot close a Buffer
  that has been freed" and silently killed the score walker.
- **`~outBus` convention** — personalities route output via `~outBus` (`\out, ob` in Pbinds).
  Defaults to `0` (hardware out) standalone; under our show profile each device lands on its own
  bus → per-seat headset stream. All 14 `cotf_*` personalities + `template.sc` updated; each file
  captures `var ob = ~outBus ? 0;` at the top (the `topEnvironment.use{}` blocks would otherwise
  read the wrong environment).
- **`/airkit/state idle|tuning|piece`** + **`/airkit/getState`** — room-wide state; `tuning` pins
  the voice pool to A (69). Personalities can react via a new optional `~onState = {|old,new|}`
  hook. Nothing fires unless the message is sent.
- **`/airkit/voiceMute <devicePort> <0|1> [fadeSec]`** — a real audio mute (gain fade on our
  show profile's per-seat monitor; patterns keep playing so unmute re-enters in time). Distinct
  from your `/airkit/mute` title-button semantics, which we deliberately did not touch.
- **`code3.0/cotf/`** (new, ours) — the show entry point (`main_cotf.scd` + `config.scd`): same
  controllers as your `main.sc`, headless, our audio device + per-seat buses. You never need to
  run it, but you can.
- **`personalityController.scd` paths** now derive from the checkout (`lists/`, `personalities/`
  relative to the repo) — no more per-machine path editing at the top of the file; your old
  hardcoded lines are kept as comments.
- **`config.scd` sets `numBuffers = 2048`** matching your `main.sc`, so the sampler personalities
  don't starve under the show profile.

Things we learned that live outside the repo (documented in CLAUDE.md): the `Require` and
`Canvas3D` quarks are prerequisites; sampler personalities need `~/Music/cotf_samples/`; our rig
needs the Beethoven WAV at 48 kHz (yours can stay 44.1k).

## 2026-07-15 (later) — /airkit/getSeats live-state query
- New `/airkit/getSeats` (personalityController.scd, beside getRoster): replies
  `/airkit/seats/reply port1 name1 ...` to the sender — what each live device
  actually has loaded. Why: devices are created lazily on their first IMU
  packet with the default "silence", which was overwriting the COTF server's
  personality pushes after a restart (found on the bench today). The server
  now polls this and re-pushes saved assignments. Additive, no behaviour
  change to anything of yours.

## 2026-07-15 (bench fix) — patterns survive seek re-anchor
- cotf/main_cotf.scd: implemented the ~onResync hook (your no-op default) —
  restarts each live device's Pdef against ~beatClock's new time on every
  anchor/re-anchor. Why: seek 0 jumps the boot-long beat clock BACKWARD, so a
  pattern already playing had its next event stranded hours in the future —
  instruments went dead-silent at the first QLab seek of the night. Probably
  the same family as the gesture-delay we chased on the 14th. COTF profile
  only; your main.sc behaviour unchanged.

## 2026-07-17 — captured a stray bench edit (cotf_simple1)
- personalities/cotf_simple1.sc: `~scoreAnchorBeat = 0;` added to ~init — found
  sitting uncommitted on M1, almost certainly from the seek-anchor bench
  session on the 15th (same family as ~onResync). Committing it so the branch
  matches what the machine is actually running before your next push lands.
  If this collides with your current work, your version wins — shout and we
  will reconcile.

## 2026-07-24 — bind-check now verifies the Aggregate's channel count
- cotf/bind-check.scd: after the device-NAME check passes, we now ask CoreAudio
  (system_profiler) how many output channels the Aggregate actually has and
  refuse to run below 144. Why: this morning the venue power-cycled with the
  MOTU accidentally unplugged — the Aggregate still existed under its usual
  name, scsynth bound it at 16 channels (BlackHole alone), the old check
  printed "BOUND — OK", and no room audio was possible. Under PM2 the failure
  now exits after 8s so PM2 retries until the MOTU is back (a climbing restart
  counter is visible; a green lie is not); in the IDE it only refuses, never
  kills your session. COTF profile only, no OSC change, no API.md change.

## 2026-07-24 (evening) — captured your live M1 tuning from the test run
- personalities/cotf_{drums4,harp1,harpsichord2,simple2}.sc: your ssh edits on M1
  during our "5 fingered test" run tonight (amp lincurves, octave shifts, hit-window
  0.15→0.1), committed from M1 under your name so the branch matches what the
  machine is running. If any of these were experiments you didn't want kept,
  shout and we'll reconcile — nothing else was touched.
