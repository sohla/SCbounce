# COTF changes — plain-language log for Steph

One entry per push batch from the Concerts of the Future side, newest first: what we changed,
why, and what (if anything) behaves differently on your machine. The intent is that nothing
here ever changes how AirKit behaves for you — if it does, that's a bug, tell us.

## 2026-07-17 — Room 3 physical chair speakers (cotf)
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
