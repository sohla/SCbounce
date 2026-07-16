# AirKit OSC Contract

**This file is the single source of truth for every OSC address AirKit
consumes or emits.** Any commit that changes OSC behaviour MUST update this
file in the same commit.

## Compatibility rules

1. Additive only — add new addresses; never change or repurpose existing ones.
2. New arguments are appended, with defaults preserving old behaviour.
3. Defaults must preserve composer-machine behaviour exactly (single
   instance, langPort 57120, stereo out 0, conductor audio enabled).
4. Anything COTF-specific is marked **[COTF]** below.

## Device-side input (what an AirStick/router sends TO AirKit)

Sent to the instance's sclang port (composer default 57120; COTF Room 3 =
57120, Room 2 = 57121). `N` = virtual device 1..5. Devices are keyed by
sender source port + (N−1): senders must keep a stable source port.

**[COTF]** the COTF router binds a **fixed source port 9001** and addresses
devices as `devicePort = 9001 + seat − 1` (seat 1 = 9001 … seat 5 = 9005) —
this is the stable sender port the "device = source port + N−1" rule above
relies on.

| Address | Args | Notes |
|---|---|---|
| `/N/IMUFusedData` | `ax ay az qx qy qz qw` (floats; msg[1..7] read) | ~100 Hz IMU stream; first packet auto-creates the device |
| `/N/Battery` | `volts charge%` | optional |
| `/N/DigiIn` | `buttonIndex state` | up to 4 buttons |
| `/N/Config` | config dump; bytes [15..17] = RGB | reply to `/Config/GetConfig` |

## Host API (`/airkit/*`)

All on the instance's sclang port unless noted.

### Devices & personalities
| Address | Args | Notes |
|---|---|---|
| `/airkit/startOSCListening` | `port` | begin auto-detect (arg currently unused by the listener) |
| `/airkit/stopOSCListening` | — | free auto-detect listeners |
| `/airkit/addDevice` | `port` | emitted internally on detection; views subscribe |
| `/airkit/loadPersonality` | `port index` | load personality `index` from the device's list |
| `/airkit/reLoadPersonality` | `port index` | re-run `~init` (live-reload on file save) |
| `/airkit/unLoadPersonality` | `port index` | run `~deinit`, stop the routine |
| `/airkit/personalityName` | `port name` | broadcast; views update the title button |
| `/airkit/mute` / `/airkit/unmute` | `port` | **GUI semantic: unload/reload personality** (title button). NOT an audio mute — see `/airkit/voiceMute`. Do not repurpose. |
| `/airkit/calibrate` | `port` | capture current quaternion as reference |
| `/airkit/oscThru` | `bool` | forward incoming IMU packets to outAddr |
| `/airkit/remote/devices` | — | replies `/airkitremote/devices` with device ports |
| `/airkit/cc/<num>` | `value` | MIDI CC rebroadcast (composer GUI path) |
| `/airkit/getRoster` | — | replies `/airkit/roster/reply name1 name2 ...` to the sender: the loaded personality names in index order (index == `loadPersonality` index, both `silence` entries included). Used by the COTF server to enumerate patches. |
| `/airkit/getSeats` | — | replies `/airkit/seats/reply port1 name1 port2 name2 ...` to the sender: flat (devicePort, personalityName) pairs for every live device. Devices are lazily created on first IMU packet with personality `silence`; the COTF server polls this to reconcile actually-loaded vs saved assignments. |

### Transport (conductor)
| Address | Args | Notes |
|---|---|---|
| `/airkit/go` | — | start from t=0 (≡ seek 0) |
| `/airkit/seek` | `seconds (float)` | reposition score walker + beat clock (+ local audio if enabled) |
| `/airkit/beek` | `beatIndex (int)` | seek by beat index |
| `/airkit/pause` | — | stop transport, freeze playhead |

### [COTF] Transport orchestration
| Trigger | Action | Notes |
|---|---|---|
| Room 3 arrival | `/airkit/state tuning` | sent on group arrival, before any transport call |
| Staff Start | `/airkit/state piece` | sent **before** the QLab cue |
| Stop / reset / clear | `/airkit/state idle` | room reset to free play |

The COTF server **never sends `/airkit/go`** — the conductor is QLab-slaved
via the Network cue; QLab's cue drives transport, COTF only drives `state`.
Staff seat-mute bridges to `/airkit/voiceMute devicePort muted [fadeSec=1]`
(real audio fade — packet-drop alone would leave the pattern playing). The
COTF monitor polls `/airkit/getState` + `/airkit/getSeats` on **both rooms**
(57120 Room 3, 57121 Room 2) every ~10 s and re-pushes only drifted seats
(never a sounding seat mid-piece); Room 2's desired state mirrors Room 3's
saved seats.

### [COTF] Room state & voice mute
| Address | Args | Notes |
|---|---|---|
| `/airkit/state` | `idle\|tuning\|piece` | room-wide. `idle`: transport stopped, free play. `tuning`: transport stopped, `~scoreVoicePool` pinned to `[69]` (A4). `piece`: conductor owns pool/hooks; transport still started by go/seek. Dispatches `~onState.(old, new)` to personalities. |
| `/airkit/getState` | — | replies `/airkit/state/reply <name>` to sender |
| `/airkit/voiceMute` | `devicePort muted(0|1) [fadeSec=1]` | real audio mute on the device's monitor gain; pattern keeps playing so unmute is instant and beat-synced |

## Personality environment contract (informational)

Personalities may override: `~init`, `~deinit`, `~next`, `~onEvent`, `~plot`,
beat hooks (`~onTick ~onHalf ~onBeat ~onBar ~onPhrase ~onSection ~onChord
~onKey ~onScale`), `~onResync`, **[COTF]** `~onState`. Output convention:
route audio via `~outBus` (`\out, ~outBus` in Pbinds / `out:` on Synths);
defaults to 0 on composer machines, points at a per-seat bus under COTF.
