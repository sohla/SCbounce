# AirKit

AirKit is a SuperCollider application for consuming wireless OSC sensor data.
The OSC schema can be added to or enhanced for different devices; the current
build targets the **AirSticks** (an IMU-based wireless controller streaming
`IMUFusedData`, `Battery`, and `DigiIn` over OSC).

The host runs SuperCollider, listens for incoming sensor traffic, automatically
spawns a "device" for every stream it detects, and binds each device to a
**personality** — a small live-codable `.sc` file that describes how that
device's motion becomes music.

Originally targeted at a Raspberry Pi (current branch `Airsticks-RPI`) acting
as a standalone, GUI-driven instrument host, but the same code runs on
macOS/Linux.

---

## What it does

```
   AirStick(s)                  AirKit (SuperCollider)              Audio Out
 ┌────────────┐  /N/IMUFused   ┌────────────────────────────┐    ┌────────────┐
 │ IMU + DigiIn├──────────────►│  oscController             │    │ scsynth    │
 │ + battery   │  /N/Battery    │   ├── auto-detect devices │    │ (synths/   │
 │ wifi/OSC    │  /N/DigiIn     │   ├── quat → euler → gyro │    │  patterns) │
 └────────────┘                 │   ├── velocity / rrate    │    └─────┬──────┘
                                │   └── per-device sensors  │          │
                                │                            │          ▼
                                │  personalityController     │     speakers
                                │   ├── load .sc personality │
                                │   ├── 30 Hz process loop   │
                                │   ├── filter + threshold   │
                                │   └── hot-reload on save   │
                                │                            │
                                │  GUI (Qt)                  │
                                │   ├── device view (3D cube)│
                                │   ├── visual view (shapes) │
                                │   └── system view          │
                                └────────────────────────────┘
```

- One AirKit instance can host up to 5 virtual devices on the same IP
  (`numAirwareVirtualDevices = 5` in `oscController.scd`).
- Each device runs its own processing routine at ~30 Hz, with smoothed
  acceleration mass, rotation rate, and Euler angles derived from the incoming
  quaternion.
- Personalities are interpreted from disk and re-interpreted automatically when
  the file is saved — i.e. live-coding is the intended workflow.
- A Qt GUI shows each device's orientation, current personality, battery, and
  a per-device visual renderer; a system tab exposes server stats, OSC
  pass-through, network reset, and shutdown.

---

## Repository layout

| Path | What's in it |
|---|---|
| `code3.0/` | **Current app.** `main.sc` is the entry point. |
| `code2.0/`, `code1.0/` | Earlier iterations kept for reference. Not used by the current app. |
| `personalities/` | ~170 personality files (`.sc`). Each is one sound/behavior. |
| `lists/` | Curated personality playlists per device/performer (`list_*.sc`). |
| `synths/` | Standalone `SynthDef`s and synth experiments. |
| `visuals/` | Standalone visual experiments + a web-based synthesis UI. |
| `analysis/` | Python scripts and generated HTML for inspecting commit history, file networks, and personality code over time. |
| `embed/` | Firmware-side artifacts (M5Stick, Wemos + BNO055, etc.) — the hardware that produces the OSC stream. |
| `network/` | Network config files for the RPi setup. |
| `sc_osx_standalone-3.7.0-template/` | Template for building AirKit as a self-contained macOS app. |
| `airstickTemplate.sc` | Hand-rolled walkthrough of mapping raw AirStick OSC to a synth — good starting point for learning the protocol. |
| `configureAirStickOSC.sc` | Snippets for configuring an AirStick over OSC (LED, ID, stream destination). |
| `personality_findings.md` | Detailed write-up of the patterns personalities use (Pdef vs Ndef vs direct Synth, sensor mapping, smoothing). |
| `personality_provocations.md` | Design notes / open questions. |

---

## Architecture (code3.0)

`code3.0/main.sc` boots scsynth, opens MIDI, and constructs a Qt window with a
three-tab stack:

- **device** — `deviceView.scd`: per-device panel with 3D orientation cube
  (`threeDeeView.scd`), title button, prev/next personality buttons.
- **visual** — `visualView.scd`: per-device canvas driven by a custom
  `\customVisualEvent` event type defined in `renderer.scd` (shapes pulled
  from `shapeLib.scd`).
- **system** — `systemView.scd`: server CPU/UGen/Synth counts, OSC thru toggle,
  network restart, free all nodes/buffers, quit, and shutdown (Pi).

Two controllers do the real work:

### `oscController.scd` — device model + OSC ingest

- Defines `deviceProto` (Event-based "object") with sub-Events for
  `sensors`, `params`, and `listeners`.
- On boot, registers OSC listeners on `/N/IMUFusedData` for N = 1..5. The
  *first* packet on a path auto-creates a new device entry in `~devices`,
  keyed by port.
- For every IMU packet it:
  1. Writes accel x/y/z + quaternion w/x/y/z to a 7-channel control bus
     (`d.sensorBus`) for SynthDefs to read directly.
  2. Stores accel and quat into `d.sensors`.
  3. Converts quat → Euler (with gimbal-lock handling), optionally relative
     to a stored reference (calibration).
  4. Computes per-axis angular rate using a wrap-aware `angleDiff`.
  5. Integrates accel → velocity, with a leaky decay.
- Also listens for `/N/Battery` and `/N/DigiIn`.
- Exposes OSC endpoints: `/airkit/startOSCListening`,
  `/airkit/stopOSCListening`, `/airkit/addDevice`, `/airkit/calibrate`,
  `/airkit/oscThru`, `/airkit/remote/devices` (see `code3.0/API.md`).

### `personalityController.scd` — personality lifecycle

- Reads a list file from `lists/` (e.g. `list_ITR_Mel.sc`) — a plain array of
  personality names.
- For a given device + index, loads `personalities/<name>.sc`, builds a fresh
  `Environment` with:
  - `~model` — per-personality state (filtered accel/gyro mass, pattern key,
    smoothing coefficients, etc.).
  - `~device` — back-reference to the OSC device.
  - `~secs` — process tick (default 0.03 s ≈ 33 Hz).
  - `~processDeviceData` — pulls from `d.sensors`, applies attack/decay
    smoothing into `~model`.
  - Hooks the personality file overrides: `~init`, `~deinit`, `~next`,
    `~onEvent`, `~plot`, `~midiControllerValue`.
- Starts a `Routine` that, every tick, checks the personality file's mtime
  and re-sends `/airkit/loadPersonality` if it changed — this is the
  **live-coding loop**.
- OSC endpoints: `/airkit/loadPersonality`, `/airkit/unLoadPersonality`,
  `/airkit/reLoadPersonality`.

### Personality contract

A personality file (see `personalities/template.sc` for the skeleton) runs
inside the personality Environment, so it sees:

- `~model` (`m`), `~device` (`d`)
- `~smooth`, `~slope` helpers
- Anything else set up by `personalityController.scd`

It is expected to (optionally) override:

```supercollider
~init    = ~init    <> { ... };  // build SynthDef/Pdef, allocate buffers
~deinit  = ~deinit  <> { ... };  // tear down cleanly
~next    = {|d| ... };           // called every ~secs; main mapping logic
~onEvent = {|e| ... };           // receive shared root/dur from other devices
~plot    = {|d,p| [ ... ] };     // values for the per-device plot
```

`personality_findings.md` documents the four common implementation styles
(Pdef + Pbind, Ndef + Pbindef, direct Synth, hybrid) with worked examples.

---

## OSC API (host)

All endpoints live at `NetAddr("127.0.0.1", NetAddr.langPort)` unless noted.
Full list in `code3.0/API.md`. The important ones:

| Address | Args | Description |
|---|---|---|
| `/airkit/startOSCListening` | `port` | Begin auto-detecting incoming devices. Sent by `main.sc` on boot. |
| `/airkit/stopOSCListening` | — | Free the auto-detect listeners. |
| `/airkit/addDevice` | `port` | Emitted internally when a new device is detected; views subscribe to it to add UI. |
| `/airkit/loadPersonality` | `port, index` | Load personality `index` from the list assigned to the device on `port`. |
| `/airkit/reLoadPersonality` | `port, index` | Re-run `~init` for the current personality (used by live-reload on file save). |
| `/airkit/unLoadPersonality` | `port, index` | Run `~deinit`, stop the routine. |
| `/airkit/calibrate` | `port` | Capture the current quaternion as the reference orientation for that device. |
| `/airkit/oscThru` | `bool` | Forward incoming IMU packets to `outAddr` (configurable at the top of `oscController.scd`). |
| `/airkit/personalityName` | `port, name` | Broadcast; views update the title button. |
| `/airkit/mute` / `/airkit/unmute` | `port` | UI toggles via title button. |

## OSC API (device-side, expected input)

For each virtual device `N` (1..5) on a given source IP:

| Pattern | Payload |
|---|---|
| `/N/IMUFusedData` | `[ax, ay, az, qx, qy, qz, qw, ...]` (msg[1..7] used) |
| `/N/Battery` | `[volts, charge%]` |
| `/N/DigiIn` | `[buttonIndex, state]` (up to 4 buttons) |
| `/N/Config` | Returned on `/Config/GetConfig`; bytes [15..17] are RGB used for device colour. |

The AirStick configuration commands (`/Config/SetID`, `/Config/SetLED`,
`/Config/RequestStream`, `/Config/GetConfig`) are demonstrated in
`configureAirStickOSC.sc`.

---

## Setup

### Prerequisites

- SuperCollider 3.12+ (Qt GUI required for the app; `Canvas3D` from the
  **Canvas3D** quark is used by the orientation view).
- An AirStick (or any device pushing the OSC schema above) on the same
  network as the host.
- Optional: a MIDI controller — `main.sc` calls `MIDIIn.connectAll` and
  rebroadcasts CCs as `/airkit/cc/<num>`.

### Running on macOS / Linux desktop

1. Edit `code3.0/personalityController.scd` so `listsDir` and `personalityDir`
   point at your local checkout. The current values assume:
   ```
   ~/Develop/SuperCollider/Projects/SCbounce/lists/
   ~/Develop/SuperCollider/Projects/SCbounce/personalities/
   ```
   Commented alternatives for laptop / mac-mini setups are in the file.
2. Pick a personality list by editing the `list` variable near the top of
   `personalityController.scd` (e.g. `"list_ITR_Mel.sc"`).
3. Launch the app. Either:
   - Open `code3.0/main.sc` in the SuperCollider IDE and evaluate the whole
     block, **or**
   - Run sclang directly from a terminal (useful for headless / desktop
     auto-start):
     ```
     /Applications/SuperCollider/SuperCollider.app/Contents/MacOS/sclang \
         /path/to/AirKit/code3.0/main.sc
     ```
   The app boots the server, opens fullscreen, and starts listening.
4. Power on the AirStick(s) and point them at this host's IP/port (57120).
   `configureAirStickOSC.sc` has the snippets for `/Config/RequestStream`.

### Running on the Raspberry Pi (this branch)

The `Airsticks-RPI` branch is wired for a Pi acting as a kiosk:

- `systemView.scd` reads `hostname -I`, `/proc/loadavg`,
  `/sys/class/thermal/...` and exposes `sudo shutdown now` /
  `sudo systemctl restart NetworkManager` buttons.
- Paths in `personalityController.scd` point at
  `~/Develop/SuperCollider/Projects/SCbounce/...` — keep the repo at that
  location on the Pi or update the constants.
- Launch the same way (sclang on `main.sc`) at boot.

### Building a macOS standalone

`sc_osx_standalone-3.7.0-template/` contains a SuperCollider 3.7 standalone
shell. `run.sh` writes a `langconf.yaml` and starts sclang against
`init.scd`. To bundle AirKit, drop the project sources into the standalone
and wrap with Platypus per that folder's README.

---

## A minimal personality

```supercollider
// personalities/myFirst.sc
var m = ~model;
var d = ~device;

~init = ~init <> {
    SynthDef(\hum, { |out=0, freq=220, amp=0|
        var sig = SinOsc.ar(freq) * amp.lag(0.1);
        Out.ar(out, sig ! 2);
    }).add;
    ~synth = Synth(\hum);
};

~next = {|d|
    // map filtered acceleration → amp, gyro Y (tilt) → pitch
    var amp = m.accelMassFiltered.linlin(0, 2.5, 0, 0.3);
    var freq = (d.sensors.gyroEvent.y / pi).linexp(-0.5, 0.5, 110, 880);
    ~synth.set(\amp, amp, \freq, freq);
};

~deinit = ~deinit <> { ~synth.free };
```

Add `"myFirst"` to the list file you've selected, save, and the host will
auto-reload it on the next tick. Cycle to it on a device using the prev/next
buttons in the **device** or **visual** tab.
