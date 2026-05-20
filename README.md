# AirKit

AirKit is a SuperCollider application for turning live OSC sensor data into sound and visuals. Any device that can stream sensor data over OSC — wireless IMU controllers, wearables, custom microcontroller rigs, phone apps, environmental sensors — can drive an AirKit instrument. The canonical source is the AirSticks / Airware wireless IMU controllers, but the architecture is sensor-agnostic.

It is designed for collaborative and inclusive performance making: each performer holds (or is wired to) a sensor, AirKit receives the streaming OSC data, runs it through a per-device "personality" (a live-codable behaviour patch), and produces synchronised audio, MIDI and on-screen visuals.

The same code base runs in two deployment targets:

- **Raspberry Pi** — headless or kiosk-style performance unit. Boots into the AirKit GUI full-screen, includes shutdown / network-reset / OSC-thru controls.
- **Desktop (macOS)** — same app, launched from the SuperCollider standalone for development, rehearsal and authoring of personalities.

Run on macOS from the command line:

```sh
/Applications/SuperCollider/SuperCollider.app/Contents/MacOS/sclang \
  /path/to/AirKit/code3.0/main.sc
```

On the Pi the same `code3.0/main.sc` is launched from a systemd / startup script.

---

## What AirKit does, end-to-end

1. **Listens for devices.** On boot, `code3.0/oscController.scd` opens an OSC listener for incoming sensor packets (the default tag is `IMUFusedData`, matching AirSticks; any source that sends a compatible payload — accel + quaternion, or whatever a personality is written to consume — is accepted). Up to five devices per host IP/port are supported. Each newly seen device is auto-registered with a unique colour and slot.
2. **Cleans and derives data.** For each device the controller stores raw accelerometer and quaternion data, then derives Euler angles (`gyroEvent`), angular rate (`rrateEvent`), velocity, calibrated orientation, and the "mass" signals (`accelMass`, `rrateMass`) — magnitude summaries used by personalities as expressive energy. Quaternion calibration is supported (tap the 3D view to set the current orientation as the reference). Sensor sources that don't supply a full IMU payload simply leave the unused fields at rest; personalities can read whichever channels they care about.
3. **Assigns a personality.** Each device is assigned a personality — a `.sc` file in `personalities/` — drawn from a `list_*.sc` in `lists/`. Personalities are cycled with prev/next buttons or driven over OSC. Each personality runs inside its own Environment with shared helpers.
4. **Runs a per-device process loop.** A Routine (`procRout`) ticks at ~30 fps. On each tick it: (a) reloads the personality file if it has been saved (live-coding), (b) pre-processes the device sensor stream into the personality's `~model`, and (c) calls the personality's `~next` to do the actual musical / visual work.
5. **Plays audio, sends MIDI, draws visuals.** Personalities use SuperCollider's `SynthDef` / `Pdef` for sound, can send MIDI/OSC out, and emit `customVisualEvent` events that are picked up by the visual renderer.

---

## Architecture

```
                +-----------------------------+
  OSC sensor ---|  oscController.scd          |---> ~devices[port] (Event)
  packets       |  - device auto-detect       |     - sensors (raw + derived)
  (UDP/OSC      |  - quaternion math          |     - params, listeners, env
   from any     |  - per-device listeners     |     - sensorBus (control bus)
   source)      +-----------------------------+
                              |
                              v
                +-----------------------------+
                |  personalityController.scd  |
                |  - load list_*.sc           |
                |  - interpret personality    |
                |  - build Environment        |
                |  - createProcRout (30 fps)  |
                |  - live reload on file save |
                +-----------------------------+
                              |
                              v
                +-----------------------------+        +---------------------+
                |  personality (.sc)          |------->| SynthDefs / Pdefs   |
                |  ~init / ~next / ~deinit    |        | audio out           |
                |  ~onEvent / ~plot           |        +---------------------+
                |  reads ~model + ~device     |        +---------------------+
                |  writes ~model.com (shared) |------->| customVisualEvent   |
                +-----------------------------+        | -> renderer.scd     |
                                                       +---------------------+
```

### The device model

`oscController.scd` defines `deviceProto`, a prototype Event used for every detected device. "Device" here just means an OSC sensor source — an AirStick, a phone, a microcontroller, etc. Key fields:

- `port`, `ip`, `index`, `color`, `volts`, `charge` — identity and battery (battery fields are unused for sources that don't report power)
- `sensors` — accel, quaternion, derived Euler / rate, velocity, calibration reference, four digital inputs
- `sensorBus` — a 7-channel control bus carrying live accel + quat values to the audio server so SynthDefs can read sensor data directly
- `params` — per-device user parameters (volume, sensitivity)
- `env` — the Environment running the personality
- `procRout` — the ~30 fps process Routine
- `listeners` — OSCFuncs for sensor data, battery, increment/decrement, digital inputs

### Personalities

A **personality** is a SuperCollider source file (`personalities/*.sc`) that describes how one performer's instrument behaves: what it sounds like, how it maps motion to sound, what it looks like, and how it relates to the rest of the ensemble. Personalities are written in plain SuperCollider and are reloaded automatically whenever the file is saved — this is the live-coding loop.

Each personality is interpreted into an Environment that already has a number of names bound for it (see `interpretPersonality` in `personalityController.scd`):

| Name | Purpose |
|---|---|
| `~model` | This personality's working state — name, pattern id, filtered sensor signals, plus `~model.com` (see Community below) |
| `~device` | The full device Event (sensors, params, color, port…) |
| `~secs` | Tick interval for the process Routine (default `0.03`) |
| `~processDeviceData` | Pre-fills `~model.accelMass`, `~model.rrateMass`, filtered gyro etc. before `~next` runs |
| `~smooth`, `~slope` | Helpers for one-pole smoothing and difference |
| `~init`, `~deinit` | Called when the personality is loaded / unloaded |
| `~next` | Called every tick with the current device — the main mapping hook |
| `~onEvent` | Called for each pattern event (e.g. from a `Pbind`) |
| `~plot`, `~plotMin`, `~plotMax` | What to draw in the per-device sensor plotter |
| `~midiControllerValue` | Hook for incoming MIDI CC |

A minimal personality therefore contains:

1. One or more `SynthDef`s describing its sound.
2. A `Pdef`/`Pbind` (optional) describing its pattern, often emitting `\customVisualEvent` events alongside notes.
3. A `~next` block that maps motion → synth/pattern parameters (filter cutoff, pitch, density, amplitude, visual shape, colour, etc.).
4. Optional `~init` / `~deinit` for setup and teardown.

See `personalities/template.sc` and `personalities/template3.sc` for skeletons, and any of the many existing personalities (`bells.sc`, `funBass.sc`, `drumkit.sc`, `melodicPerc1.sc`, …) for working examples.

#### Lists

`lists/list_*.sc` files are simply arrays of personality file names. A list is assigned to each device slot (1..5), and the prev/next buttons cycle through the names in that list. This makes it easy to curate a setlist for a session — e.g. `list_ITR_Mel.sc`, `list_MIM25_James.sc` — without editing personalities themselves.

### Community structure

Personalities need to play *together*. AirKit gives every personality access to a shared "community" Event, `~model.com`, with at minimum:

```
com = (
    \root: 0,         // common tonal root
    \dur: 1,          // common rhythmic duration
    \accelMass: 0,    // shared movement energy
    \rrateMass: 0,    // shared rotational energy
)
```

The `com` Event is passed to every personality at interpret time, so any personality can both *read* it (to stay in key, to phase with the ensemble) and *write* it (to broadcast a new root or tempo to the others). Typical patterns:

- A "lead" personality writes `~model.com.root` on each note; "accompaniment" personalities read it to transpose themselves.
- A drum personality writes `~model.com.dur` to update tempo; melodic personalities follow.
- Movement-driven personalities aggregate `accelMass` / `rrateMass` so collective energy can influence everyone.

Because `com` is shared, personality authors can opt into as much or as little ensemble coupling as the piece needs, without hard-wiring connections between specific instruments.

### Live coding loop

`createProcRout` watches `File.mtime(~filePath)` on every tick. When the personality file is saved, it triggers `/airkit/loadPersonality`, which calls `~deinit`, re-interprets the file, and calls `~init` again. This means a performer or composer can edit a personality while it is running — change a SynthDef, a mapping, a pattern — and hear the change on next save. `reLoadPersonality` is also exposed over OSC so an external editor / controller can force a refresh.

---

## Visual system

AirKit's visuals are built into the same SuperCollider app — there is no separate renderer process. Three layers:

### 1. Per-device 2D event renderer (`code3.0/renderer.scd`)

Each device gets its own `UserView` (animated at 60 fps). Personalities emit events of type `\customVisualEvent` (defined in `renderer.scd`), which carry shape, position, size, colour, rotation, modulation and duration. Each event is added to that device's `visualEvents` list and rendered until it expires.

Key features of a visual event:

- `shape` — name resolved against `shapeLib.scd`: `circle`, `square`, `triangle`, `star`, `hexagon`, `cross`, `wave`, `leaf`, `spiral`, `blobby`, `line`, or custom `points`.
- `sx`,`sy` → `ex`,`ey` — start and end position, blended over `duration` via `xEnv`/`yEnv` envelopes.
- `startSize`/`endSize`, `startColor`/`endColor`, `startWidth`/`endWidth` — envelope-driven interpolation.
- `rotation` — drawing rotation about the event centre.
- `modulation` — optional per-point modulation: `radial`, `normal`, `noise`, or `phase`, with `freq`, `amp`, `harmonics`, `phase`. Used to make shapes "breathe", jitter, or wave-distort over their lifetime.
- `fill` / `closed` — fill vs stroke, open vs closed path.

Because the event type integrates with `Pbind`, a personality can drive visuals straight from a pattern, e.g.:

```
Pbind(
    \instrument, \simple,
    \type, \customVisualEvent,
    \shape, Pseq([\circle, \square, \triangle], inf),
    \sx, Pwhite(-1.0, 1.0),
    \startColor, Color.hsv(...),
    ...
)
```

Note positions and visual positions are produced from the same stream of events, which is what keeps them in sync.

### 2. Per-device 3D orientation view (`code3.0/threeDeeView.scd`)

A `Canvas3D` panel per device showing a cube driven by the quaternion (via `gyroEvent` Euler angles) plus three small axis cubes scaled by raw accelerometer X/Y/Z. Clicking the panel sends `/airkit/calibrate` to set the current orientation as the device's reference (zero) — used to align the controller before a piece.

### 3. Per-device sensor plotter (`code3.0/plotterView.scd`)

A scrolling plot whose contents are whatever the personality returns from `~plot`. Personalities use this to expose whatever signal is most useful for diagnosing the current mapping (e.g. `accelMassFiltered`, button state, raw gyro axes). `~plotMin` and `~plotMax` set the y-axis range.

### Tabs

The main window is a three-tab stack (`main.sc`):

- **device** — per-device detail: ID, IP/port, battery, 3D orientation, plot, title (which is also the personality switcher).
- **visual** — fullscreen-style grid of just the 2D event renderers, one per device.
- **system** — server stats (CPU, UGens, synths), IP, port, OSC thru toggle, free nodes/buffers, network reset, quit, and (on Pi) hardware shutdown.

### External visual / control surfaces

The `visuals/` folder also contains experimental external surfaces:

- `synthesis-ui.html`, `synthesis-ui-3d.html` — browser-based UIs that talk to AirKit over OSC/WebSocket.
- `customEvent*.sc`, `Visual.sc`, `visualSynth.sc` — older / alternate visual event implementations kept for reference.
- `synthesisui/synthesisui.pde` — a Processing sketch.

These are optional and not required for the core app to run.

---

## Sending OSC to AirKit

Any device that can speak OSC can drive an AirKit instrument. There are three sensor-side message patterns the OSC listener (`oscController.scd`) subscribes to, one per virtual device slot `n` (1..5):

| Pattern | Args | Notes |
|---|---|---|
| `/n/IMUFusedData` | `ax, ay, az, qx, qy, qz, qw` (7 floats) | Primary sensor packet. Accel is multiplied by `0.1` on receive (matches AirSticks scaling). Quaternion is stored as `(w, x, y, z)`. Sources that don't have a quaternion can send zeros, or `1,0,0,0` for identity. |
| `/n/Battery` | `volts, charge` (2 floats) | Optional. Drives the on-screen battery indicator. Omit if your source has no battery. |
| `/n/DigiIn` | `index, value` (2 ints) | Optional. Four digital input channels (`index` 0..3); used for buttons / footswitches / GPIO. |

Notes for non-AirSticks sources:

- The default tag is `IMUFusedData` (see `oscController.scd:8`). To use a different tag (e.g. `CombinedDataPacket`), change `oscMessageTag` in that file — it is shared across all device slots.
- The slot index `n` in `/n/...` is the *virtual device* number, not the host port. Up to five slots share a single host IP/port, so a multi-sensor rig can multiplex by emitting `/1/IMUFusedData`, `/2/IMUFusedData`, etc. from one network address.
- Send rate is up to you, but personalities are ticked at ~30 fps internally, so anything between 30–200 Hz is reasonable.
- Personalities consume whichever derived fields they care about (`accelMass`, `rrateMass`, `gyroEvent`, `quatEvent`, `digiInEvent`, etc.), so a stripped-down sensor that only fills part of the payload still works for personalities that don't depend on the missing channels.

## OSC API (selected)

See `code3.0/API.md` for the canonical list. The most commonly used messages:

| Message | Args | Effect |
|---|---|---|
| `/airkit/startOSCListening` | `port` | Begin device auto-detection |
| `/airkit/stopOSCListening` | — | Stop and free listeners |
| `/airkit/addDevice` | `port` | Register a detected device |
| `/airkit/loadPersonality` | `port, index` | Load personality at `index` from the device's list |
| `/airkit/reLoadPersonality` | `port, index` | Re-interpret the current personality file |
| `/airkit/unLoadPersonality` | `port, index` | Tear down without loading a new one |
| `/airkit/personalityName` | `port, name` | Broadcast / set the current personality name |
| `/airkit/calibrate` | `port` | Use current quaternion as orientation reference |
| `/airkit/oscThru` | `bool` | Forward incoming sensor OSC to a configured downstream address |
| `/airkit/mute`, `/airkit/unmute` | `port` | Mute / unmute a device |

The MIDI CC handler in `main.sc` also forwards any incoming CC as `/airkit/cc/<num>` so personalities can subscribe to hardware controllers.

---

## Repository layout

```
code3.0/           current app — main.sc + controllers + views + renderer
code1.0/, code2.0/ earlier iterations, kept for reference
personalities/     one .sc file per instrument behaviour
lists/             list_*.sc — curated arrays of personality names
visuals/           experimental external visual surfaces (web, Processing, .sc)
synths/            stand-alone SynthDef explorations
analysis/, embed/, network/, data/  research material, firmware, support files
sc_osx_standalone-3.7.0-template/   standalone build template for macOS
*.wiki/            wiki submodule (still named SCbounce.wiki on disk)
```

---

## Work in progress

- Move the data store / model to control buses end-to-end (some of this is already done via `sensorBus`).
- Make personalities more consistent — define a clearer contract so any list of personalities can be combined safely.
- Graceful failure when a live-coded personality throws — currently an error in the personality can leave the device in a partial state.
- Formalise the supported OSC sensor payloads so non-AirSticks sources have a documented contract to target.
