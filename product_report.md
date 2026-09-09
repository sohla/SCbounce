# product_report.md

Possible shapes for AirKit as a product for schools and specialist
music/visual/movement settings — and the moves that get there without
slowing the R&D team down.

Status: exploration, nothing decided, no code changed. Written 2026-09-09 on
`Airsticks-RPI`.

Inputs assumed, from the brief:

- **We control the AirSticks hardware.** Firmware, OSC schema and enclosure are
  ours to change, and we could sell them.
- **~100–300 units in three years.**
- **The two constraints that decide it: zero-IT install, and non-expert
  authoring.**
- **The vehicle is undecided.** So this report keeps vehicle open and asks what
  each shape would need. See §8.
- **R&D velocity is a requirement, not a nice-to-have** — tap, pressure and
  stretch sensors are in the pipeline and the platform must absorb them.

---

## 0. The four findings

1. **You already have a product; you have not named it as one.** There is a
   tagged release pipeline, a customer-facing web updater with backup and
   rollback, generated end-user documentation, thirteen shipped versions and
   a deployment guide that uses the word "customer". The gap between where you
   are and a sellable kit is smaller than the question implies. §1.

2. **The Pi is not one decision, it is four.** It is simultaneously the audio
   host, the WiFi access point, the kiosk display, and the appliance boundary.
   Only the *access point* role is load-bearing for zero-IT install — and it is
   the single most valuable thing you have for getting into schools. Keep that;
   everything else is negotiable. §3.

3. **New sensors are blocked by one specific thing, and it is not the OSC
   layer.** It is that ~226 personality files read `d.sensors.gyroEvent` and
   friends directly, 2,600+ times. The device model is IMU-shaped by
   construction (`sensorsProto`, a 7-channel `sensorBus`, an
   IMU-specific `~processDeviceData`). Adding a stretch sensor is easy. Adding
   it *without a flag day across 226 files* is the actual design problem, and
   `digiIn` already shows the shape of the answer. §4.

4. **Non-expert authoring has a much cheaper first step than anyone expects.**
   The teacher-facing artefact already exists and is already the right
   granularity: a **list**. `lists/*.sc` is 32 curated set-lists, selected today
   by hand-editing a variable in `personalityController.scd`. Turning list
   selection and list editing into a web UI is one contained change and it is
   the highest-value single move in this document. §5.

---

## 1. Where you actually are

### The asset

| | |
|---|---|
| Personality files | **226**, ~62,500 lines |
| SynthDefs inside them | **271** |
| Curated set-lists | **32** |
| p-files using samples | 106 |
| p-files with visuals | 66 emit visual events, 21 define their own shapes |
| Git history | 983 commits, Sept 2017 → now |

This corpus is the product. It is nine years of tuned-by-ear instrument design
that cannot be reconstructed from a spec, and it is the thing a competitor
cannot copy. The engine is replaceable; the repertoire is not.

The corpus is also **evidence of the market you already have**. The list names
are the customer list: `list_ITR_Mel`, `list_ITR_Nic`, `list_ITR_Brenton`,
`list_ITR_Alessio`, `list_MIM25_Caz`, `list_MIM25_Heather`, `list_MIM25_James`,
`list_MIM25_Matt`, `list_MIM25_Tim`, `list_glenroy`, `list_workshop1`,
`list_OpenLab25`, `list_yourDNA24/25/26`. Named performers, named venues, named
workshops, named years. You have been running a bespoke-per-site service for
years without calling it one. Productisation here means *repeating* what you
already do, not inventing something new.

### The product machinery that already exists

Most of this was built in `AirKitWebApp` and is running:

- **Release pipeline.** `git tag v*` → GitHub Action → zips
  `code3.0/`, `personalities/`, `lists/` + `VERSION` → GitHub Release.
  Thirteen tags cut (`v0.0.1`–`v0.0.13`).
- **Field updater.** Pi runs a Bottle server on `:8080`. Customer joins the
  AirKit WiFi, opens a browser, uploads the zip. The updater shuts SC down,
  backs up, installs, relaunches. One-click rollback.
- **Generated end-user docs.** `how_to_update.template.md` → `make doc` →
  `dist/How To Update AirKit v0.0.N.{md,html}`, one per release.
- **Offline-first by design.** No pip install on the Pi, no internet needed at
  the site. This is exactly right for schools and was a good call.

### What is missing, honestly

- **No fleet view.** You cannot answer "what version is Glenroy running?"
  without visiting Glenroy. At 300 units that is the first thing that breaks.
- **No configuration story.** `configPlan.md` diagnoses this precisely and is
  still unimplemented. Per-machine values live in files every machine edits;
  the result is **29 remote branches**, of which at least four are
  machine-identity branches (`Airsticks-RPI`, `Airsticks-RPI-Mel`,
  `AirKitDesktop`, `AirConcert`). The report already documents a merge that
  reported *"Automatic merge went well"* while silently handing one machine
  another machine's settings.
- **Version drift.** `VERSION` says `0.0.5`; the latest tag is `v0.0.13`.
  `systemView.scd` displays that file on the Pi, so **every unit in the field
  is currently reporting the wrong version.** That is a support problem the
  moment there is more than one version in the field.
- **Nothing a non-coder can change.** Changing what a device does means editing
  SuperCollider. Changing the set-list means editing SuperCollider.
- **No safety envelope.** No volume ceiling, no session limits, no "reset to
  factory", no guard against a p-file that crashes sclang mid-lesson.
- **No support surface.** No logs off-device, no crash reporting, no way for a
  teacher to say what went wrong other than describing it.

### The one structural risk worth naming now

`gatePlan.md` §"Rollback" identifies it: a bad **class file** lands outside
`APP_DIR`, so sclang dies at class-library compile, `main.sc` never runs, and
the web updater's rollback cannot reach it. **The unit bricks and it is an
in-the-room fix.** At 10 units that is a bad afternoon. At 300 units in
schools across three states it is the end of the product.

The mitigation is already written down in that document and it should become
policy, not a preference: **the field-updatable surface and the
non-recoverable surface must never be the same release.** Everything in
`code3.0/`, `personalities/`, `lists/` is one-click recoverable. Anything else
requires a visit. Design so that "anything else" changes approximately never.

---

## 2. What the product could be — five shapes

These are not mutually exclusive; the last section suggests a combination. Each
is stated with what it is, who pays, what it costs you, and what it forecloses.

---

### Shape A — **The Kit.** A sealed appliance you plug in.

A case containing: two AirSticks, charger, a Pi-class box, a small screen or
HDMI out, speaker or line-out. Turn it on, it makes its own WiFi, it works.

- **Buyer:** a specialist school's music therapist or specialist teacher, on a
  program budget. NDIS-adjacent funding in Australia. Possibly a disability
  arts organisation.
- **Why it fits:** it is the *only* shape that fully satisfies zero-IT install.
  No school network, no admin rights, no software to approve, no laptop to
  borrow. You already have the AP + updater to make it true.
- **Cost to you:** you are now a hardware company. Enclosures, BOM, batteries
  (lithium shipping rules), warranty, spares, RMA. Every unit is a physical
  object someone can break.
- **Forecloses:** cheap scale. Every additional school is a physical build.
- **Verdict:** this is your current trajectory and it is the right *core*.
  100–300 units is squarely in hand-built-with-a-fabrication-partner territory,
  which is exactly what SensiLab makes possible. But see §3 — the case for the
  Pi is much narrower than the case for the Kit.

---

### Shape B — **Software + bring your own computer.**

AirKit as an installable app on a school laptop. AirSticks sold separately.

- **Buyer:** a music teacher in a mainstream secondary school with a music-tech
  lab; a university.
- **Why it might fit:** near-zero marginal cost per school; scales past 300.
- **Why it probably fails your constraints:** it fails zero-IT install badly.
  A locked-down school laptop will not install unsigned software, will not open
  UDP ports, and its WiFi will not let a sensor device talk to it. **The AP is
  the feature**, and this shape deletes it. Also inherits every audio-driver,
  latency and permissions bug on every machine you don't control.
- **Verdict:** not the lead product. Viable as a **second SKU for
  universities and conservatoria**, where there is IT support and a reason to
  want it on their own machines. Do not let it drive architecture.

---

### Shape C — **Instrument-only.** Sell AirSticks; give AirKit away.

Hardware is the product; the software is free and open, and the sensors work
with anything that speaks OSC (Max, Ableton via a bridge, TouchDesigner, Unity).

- **Buyer:** artists, universities, makers — *not* your stated audience.
- **Why it is interesting anyway:** it is the lowest-effort route to a real
  revenue line, it seeds the R&D community, and it does not require you to
  support classroom outcomes.
- **Why it is not the answer:** a specialist school cannot use a bare sensor.
  The value you provide is the 62,500 lines of repertoire and the fact that it
  works on arrival. Selling only the stick sells the least valuable half.
- **Verdict:** run it as a **side channel** to fund and feed the main product.
  It is also the natural home for the R&D community — see §6.

---

### Shape D — **The platform.** A curated content system with an authoring layer.

The Kit, plus: personalities become a distributable content format with
metadata; lists become teacher-authored programs; there is a library of
"instruments" a teacher browses, selects and arranges without code; new
sensors appear as new capabilities rather than new versions.

- **Buyer:** same as A, but the recurring value is the content and updates, so
  it supports a subscription or a service contract rather than a one-off sale.
- **Cost to you:** this is the largest engineering commitment in this document.
  It needs the sensor abstraction (§4) and the authoring tier (§5).
- **Verdict:** this is the destination, not the starting point. The important
  thing is that **A and D are the same product at different stages** — nothing
  in Shape A needs undoing to become Shape D, provided the sensor and authoring
  work in §4–5 lands before the fleet gets large.

---

### Shape E — **The service.** Residencies, programs, training, bespoke sets.

Sell the intervention, not the object. You bring kits, run a term-long program,
train the staff, leave a kit and a set-list built for those students.

- **Buyer:** the same schools, out of a program/arts budget rather than
  equipment. Often easier money than capital equipment.
- **Why it is here:** *you are already doing this.* Every `list_*.sc` is an
  instance. It is the highest-margin thing on the list and requires no new
  engineering. It is also what generates the repertoire that makes A and D
  valuable.
- **Verdict:** do this **now**, deliberately and priced, regardless of which
  other shape you pick. It funds the rest and it is the source of the content
  library.

---

### The shape I would argue for

**A + E now, converging on D.** Sell the Kit; sell the program that comes with
it; build toward the platform. Keep C as an open side channel for the R&D
community and universities. Treat B as a later SKU, never as the architecture.

The reasoning is that A is the only shape that satisfies zero-IT install, E is
the only shape that is already profitable and already exists, and D is what
turns 226 hand-tuned files from a liability into a catalogue.

---

## 3. Do we need the Pi?

The question is malformed, and unpacking it is most of the answer. The Pi is
doing four separate jobs:

| Job | Load-bearing? | Alternatives |
|---|---|---|
| **1. WiFi access point** | **Yes — critically** | ESP32-based AP; a small router in the case; nothing else that meets zero-IT |
| **2. Appliance boundary** | **Yes** | Any dedicated computer you control |
| **3. Audio engine host** | Negotiable | x86 mini-PC, Mac mini, phone/tablet, a DSP board |
| **4. Kiosk display** | Negotiable / arguably harmful | HDMI out to a TV; a tablet on the WiFi; no screen at all |

### Job 1 is why the Pi wins, and it has nothing to do with the Pi

The Pi makes its own network. That single fact is what makes AirKit installable
in a school with no IT involvement: the sticks join the AirKit WiFi, the teacher
joins the AirKit WiFi to change settings, and the school's network is never
touched, never asked for a port, never asked for a firewall exception. Your
updater already relies on this (`http://192.168.2.4:8080`).

**Every alternative must preserve this or it is disqualified by your own
stated constraint.** That is the test to apply to any "could we just use…"
proposal.

### Job 3 is where the Pi is weakest, and it is worth measuring

You are running SuperCollider with per-device routines at **100 Hz**
(`personalityController.scd:190`, `~secs = 0.01`), up to nine virtual devices,
plus a Qt GUI with per-frame `Pen` drawing, on a Pi. `configPlan.md` records
that the desktop runs the same loop at 33 Hz — a 3× difference that exists as
a per-machine value, which suggests the rate is being tuned against available
headroom rather than against a musical requirement.

Before deciding anything: **measure it.** How much CPU headroom does a real
kit have with two devices, a sample-heavy personality, and visuals on HDMI?
`systemView.scd` already reads `/proc/loadavg` and the thermal zone, so the
instrumentation is there. If the answer is "comfortable", the Pi stays and this
section is closed. If the answer is "thermally throttling in a warm classroom
in February", that is a product-defining fact and you want to know it now, not
at unit 150.

A specific thing to check: `screens.scd` shells out to `xrandr`, and `main.sc`
notes that `unixCmdGetStdOut` **blocks the same language thread that runs
`~next`**. The code already avoids polling for that reason. Any future feature
that shells out — fleet reporting, log upload, health checks — hits the same
hazard and must not be written naively.

### The honest options

1. **Stay on Pi.** Cheapest, already works, already deployed, thermal and CPU
   headroom is the open question. Best choice unless measurement says otherwise.
2. **Pi for AP + control, separate box for audio.** Splits jobs 1/2 from 3.
   More BOM, more failure modes, more cables. Only justified if measurement
   forces it.
3. **x86 mini-PC (N100-class).** ~3–5× the CPU, runs an ordinary Linux, still
   makes its own AP, still fits in a case, costs perhaps 2–3× a Pi. If the Pi
   is genuinely marginal this is the boring correct answer, and it changes
   almost nothing above the OS.
4. **Phone/tablet as host.** Attractive for cost and screen. Fails on
   SuperCollider deployment, background audio policy, and app-store review, and
   deletes the AP. Not viable for the lead product.

### The recommendation

**Keep the Pi for now, but stop treating "Pi" as part of the identity.** The
product is "a box that makes its own network and plays". `configPlan.md`
already proposes the mechanism that makes the host swappable — per-machine
config files instead of per-machine branches. Implement that and the choice of
host stops being a branch and starts being a line in a file. That is the move
that keeps this question open cheaply.

**And separately: reconsider the screen.** The kiosk GUI is a performer's tool
— device panels, orientation cubes, personality names, server stats, a shutdown
button. In a classroom it is mostly an attractive nuisance. The HDMI-visuals
path in `main.sc` is the part with classroom value; the control GUI probably
wants to be a web page on the teacher's own tablet, served by the same Bottle
server that already serves the updater. That also solves a problem you will
otherwise hit: **you cannot put a touchscreen in front of a student who is
meant to be moving.**

---

## 4. New sensors — the real architecture question

Tap, pressure and stretch are in R&D. Here is precisely what stands in the way,
grounded in the code.

### The current shape

```
oscController.scd:8    var oscMessageTag = "IMUFusedData";   // ONE packet shape
oscController.scd:11   var numAirwareVirtualDevices = 9;
oscController.scd:35   sensorsProto = ( gyroEvent, gyroMass, rrateEvent,
                                        rrateMass, accelEvent, accelMass,
                                        quatEvent, quatReference,
                                        quatCalibrated, velocity, ...,
                                        digiInEvent )
oscController.scd:71   sensorBus = Bus.control(s, 7)         // ax ay az w x y z
```

and the derivation in `personalityController.scd:194–229` is entirely
IMU-specific: `accelMass` from `accelEvent.sumabs`, `rrateMass` from
`rrateEvent.sumabs`, three normalised filtered gyro axes.

So the device model is not "a sensor device". It is "an IMU, plus four buttons".

### The good news: `digiIn` already proves the pattern

Buttons are not IMU data. They arrive on their own OSC address
(`/N/DigiIn`), have their own listener, land in their own field
(`digiInEvent`), need no derivation, and **39 personality files use them
today**. Nothing about adding them required changing the IMU path.

That is the template. A pressure sensor is `digiIn` with a float. A stretch
sensor is `digiIn` with a float and a calibration. The OSC layer is not the
problem.

### The actual problem: 2,600 direct reads

```
sensors.gyroEvent   1009 uses across personalities/
sensors.rrateEvent   727
sensors.accelEvent   606
sensors.velocity     201
sensors.quatEvent     60
sensors.digiInEvent   61
sensors.rotateEvent   25
```

Personalities reach into the device's sensor Event by name. That is fine and
was the right call for a live-coding instrument — but it means **the sensor
schema is public API across 226 files**, and any change to its shape is a flag
day.

Note `rotateEvent`: declared in `sensorsProto` at line 49, **never written
anywhere in `code3.0/`**, and read 25 times in personalities. That is a field
that used to be populated and no longer is — a silent zero flowing into 25 read
sites. It is a small, concrete example of exactly the failure mode a schema
with no contract produces.

### What to build

**Do not** refactor the sensor model. It works, 226 files depend on it, and
"correct in isolation, wrong for the situation" is the likeliest outcome.

**Do** add alongside it:

1. **A capability descriptor per device.** When a device announces itself, it
   says what it has: `(imu: true, digiIn: 4, pressure: 2, stretch: 1)`. Today
   `addDevice` already does a `/Config/GetConfig` handshake and reads colour
   bytes out of the reply — the handshake exists, it just carries almost
   nothing. Extend the reply, not the architecture.

2. **A generic sensor channel with the `digiIn` shape.** New sensor types get
   their own OSC address, their own listener, their own named field. Existing
   fields never change. `sensorsProto` grows; it never mutates.

3. **A declared requirement per personality**, so a p-file can say "I need
   stretch" and the system can grey it out — rather than a teacher selecting an
   instrument that silently does nothing because the stick in their hand has no
   stretch sensor. This is the single most important item for classroom
   robustness, and it is *also* the thing that makes a teacher-facing library
   possible at all (§5). Cheapest form: one optional line at the top of a
   p-file, defaulting to "IMU only" so all 226 existing files remain valid
   without edits.

4. **A capability-aware `~processDeviceData`.** Today it is one fixed IMU
   derivation. It should run the IMU derivation *if the device has an IMU*, and
   run a pressure derivation if it has pressure. Additive, per capability.
   No existing p-file changes.

5. **Widen the control bus, or don't.** `Bus.control(s, 7)` is exactly the IMU
   payload, and SynthDefs read it directly. New sensors either get their own
   bus or the bus becomes capability-sized. Worth deciding early because it is
   the one place where the change is genuinely visible to SynthDefs.

### And the thing to protect

Whatever is built here, **`m.accelMassFiltered` must keep meaning what it means
today** (891 uses). Every one of those is a tuned mapping. A "cleaner" model
that shifts the scaling of `accelMass` by even a little silently retunes 226
instruments, and you will find out on stage. Additive only.

---

## 5. Non-expert authoring

You named this as one of two deciding constraints. There are three tiers, and
they are wildly different in cost.

### Tier 1 — **Curate.** Choose and order instruments. *(cheap, do this first)*

The artefact already exists. A list is:

```supercollider
( [ "silence", "trainMove2", "trainBass3", "multiBeat1", ... ] )
```

An array of names, selected today by editing `var list =` in
`personalityController.scd:19`. There are 32 of them and they are named after
performers, venues and workshops — which is to say **a list is already the unit
of teacher-facing work.**

The move: put list selection and list editing in the web UI that is already
running on the Pi. A teacher browses available instruments, drags them into an
order, names the list, saves it, and it is live. No SuperCollider, no file
editing, no zip.

This is a contained change to a system that already exists, and it converts
the single most common bespoke request ("can we have a set for this student?")
from a developer task into a teacher task. **This is the highest-value single
move in this document.**

Two prerequisites, both small:
- Personalities need **metadata** — a display name, a one-line description, and
  ideally a tag or two (loud/quiet, tonal/percussive, needs-stretch). Right now
  a p-file's identity is its filename. `bongo1` and `trainChooka2` mean nothing
  to a teacher.
- Lists need to move out of `personalityController.scd`. `configPlan.md`
  already proposes this and separates **platform** from **roster** for exactly
  the right reason.

### Tier 2 — **Tune.** Change how an instrument responds. *(moderate)*

Sensitivity, volume ceiling, pitch range, how much movement is needed to
trigger a sound. In a specialist setting this is not a nicety — it is the
difference between an instrument a student can play and one they cannot.

The good news is that the house style already points here. The "tightness rule"
in `CLAUDE.md` says a draw function reads its constants off the event, and
"every tunable in one place". `gatePlan.md` documents that the amplitude gate
thresholds are per-file magic numbers that ought to be named parameters. The
codebase is already arguing with itself in favour of surfacing parameters.

The move: let a p-file **declare** a small set of named, ranged, labelled
parameters, and let the web UI render sliders for them. `ControlSpec` is
already used for exactly this shape in commented-out model fields
(`personalityController.scd:98, 106`). Per-student presets fall out of it.

This should be **opt-in per personality**. Do not attempt to retrofit 226
files. Add it to the template, use it on the next 20 files written, and let it
spread by usefulness.

### Tier 3 — **Create.** Make a new instrument. *(expensive; probably don't)*

The temptation is a visual patcher. Resist it. It is a multi-year project, it
will be worse than what exists, and the people who would use it are the same
people who could learn to write a p-file.

The better answer is **Shape E**: creating instruments stays a service you
provide, and stays how the repertoire grows. A teacher who wants something new
asks for it; it arrives in the next release; it also ships to everyone else.
That is a feature of the business model, not a limitation of the software.

If a middle tier is wanted later, it is **templates** — "a percussion instrument
with these four samples", "a drone with this scale" — not a general patcher.
The 226 files already cluster into a handful of architectures;
`personality_findings.md` documents four of them.

---

## 6. Keeping the R&D team fast

This was stated as important in the brief and it deserves an explicit section,
because most of the natural productisation moves are exactly the moves that
would kill it.

### What is already right

**The personality file is the plugin format, and it is a good one.** It is
plain interpreted SuperCollider, hot-reloaded on save
(`personalityController.scd:283`), namespaced per device, with a documented
contract (`ak_pfile_authoring.md`, 700 lines) and a template. A researcher can
open a file, save it, and hear the change in 10 milliseconds without restarting
anything. That is a better iteration loop than most commercial audio software
offers, and it is worth defending against every "for production we should…"
argument that will come.

**The core/content split is already the release boundary.**
`release.config.json` ships `code3.0` (core), `personalities` (content) and
`lists` (curation). That is the right decomposition and it happened by
instinct. Make it explicit and versioned.

### What would kill it

- **Compiled class files in the boot path.** `gatePlan.md` establishes this
  in detail: `-a` standalone mode ignores the Extensions dirs, the class must be
  copied into the standalone tree, it only compiles at the *next* boot, and a
  syntax error in it bricks a unit beyond the reach of the web rollback.
  Its recommendation — **Option 1, environment functions in `code3.0/`, not
  class extensions** — is the right call and should be a standing rule, not a
  one-off decision. The house idiom (`~curveAbove`) already does this.
- **A GUI that must be updated whenever a sensor is added.** Hence the
  capability descriptor in §4: the UI reads what the device says it has, rather
  than being taught about each new sensor.
- **Freezing the schema.** Once there is a published personality format,
  changing it breaks the field. Version it from day one so it can change.
- **Branch-per-machine.** Already costing you: **29 remote branches**, and
  `gatePlan.md` was written on `AirConcert` referring to p-files
  (`PERCUSSION.sc`, `SOPRANOVOICE.sc`, `BIRDY.sc`, `WindVoice.sc`) that do not
  exist on this branch. Repertoire is diverging silently across branches. At
  100+ units with several researchers this becomes unmanageable.
  `configPlan.md` is the fix and it is written and unimplemented.

### The SensiLab loop

Fabrication access is a genuine strategic asset, and it argues for a specific
architecture: **make new sensors cheap to try, not cheap to ship.** A researcher
should be able to bring up a stretch sensor on a breadboard, have it announce
itself over OSC, and have it show up in AirKit as a new capability with a p-file
that uses it — in an afternoon, without touching `code3.0/`, and without any
of the 226 existing files noticing. Everything in §4 is in service of that
sentence.

The corollary is a **two-speed release**: a stable channel that goes to schools
and a research channel that goes to SensiLab and to your own performances. They
share the core; they do not share a release cadence. Today they are the same
thing, which is why a workshop set-list and a concert set-list are different
branches.

---

## 7. The moves

Ordered by dependency, not by ambition. Nothing here requires the vehicle
decision to be made first.

### Phase 0 — Stop the bleeding *(weeks, no product decisions)*

1. **Fix the `VERSION` drift.** Units are reporting `0.0.5` against a `v0.0.13`
   tag. It is a two-line fix and it is a support blocker.
2. **Implement `configPlan.md`.** It is diagnosed, measured, sequenced, and
   sitting there. It unblocks: host swapping (§3), roster-as-product (§5),
   branch consolidation (§6), and per-site configuration (all of it). Follow its
   migration order — step 3 before step 2 fails silently.
3. **Make the "never ship a class file with anything else" rule explicit** in
   the release process, per `gatePlan.md`.
4. **Retire the machine branches** once 2 lands.

### Phase 1 — Make a fleet possible *(the 100–300 unit prerequisite)*

5. **Units report in.** Version, uptime, last error, which list is loaded —
   whenever they see the internet, or via a file the teacher can email. Mind the
   blocking-`unixCmd` hazard noted in §3.
6. **Logs off-device.** A rolling log the updater can hand back as a download,
   so a support conversation starts with data.
7. **A safety envelope.** Volume ceiling, a factory-reset path, and a
   watchdog that restarts sclang if it dies mid-lesson. `start_airkit.sh` is
   currently a one-line background launch with nothing watching it.
8. **Decide the host.** Measure Pi headroom under realistic classroom load and
   close §3 with a number.

### Phase 2 — Absorb the new sensors *(unblocks R&D)*

9. **Capability descriptor** in the device handshake.
10. **Additive sensor channels**, `digiIn`-shaped.
11. **Capability-aware derivation** in `~processDeviceData`, additive only.
12. **Per-personality requirements**, defaulting to IMU-only.

### Phase 3 — Give it to teachers *(the authoring constraint)*

13. **Personality metadata** — name, description, tags, requirements.
14. **List editing in the web UI.** The high-value move.
15. **Declared parameters + sliders**, opt-in, on new files first.
16. **Move the control GUI to the browser**; keep HDMI for visuals.

### Phase 4 — Decide the vehicle

Only once 1–3 are real, because they are needed under every vehicle.

### The "not yet" list

- A visual patcher for instrument creation.
- A cloud account system.
- Porting off SuperCollider.
- An iOS/Android app.
- Any rewrite of the sensor model.
- Any refactor of the 226 personalities.

---

## 8. The vehicle — what each option would need

Undecided, so this is the decision framing rather than a recommendation.

| Vehicle | Needs | Good if |
|---|---|---|
| **Spin-out** | Capital, an IP assignment from Monash, someone doing sales full-time, warranty and liability structure | You want this to be the main thing, and 300 units is a floor not a ceiling |
| **Research platform** | Grant continuity, a partner-school network, publication output | R&D velocity and access matter more than revenue; SensiLab is the natural home |
| **Licence to a distributor** | A product stable enough to hand over, documentation, a support SLA someone else can meet | You want reach without becoming a company; costs margin and control |
| **Open source + services** | A public repo you are willing to maintain, a services capacity | Community R&D matters; revenue comes from §2 Shape E |

Two observations that hold under all four:

- **The moves in Phase 0–2 are required regardless.** None of them commits you
  to a vehicle. Do them while deciding.
- **Shape E (services) is available immediately under every vehicle**, needs no
  engineering, is already what you do, and generates the content library the
  other shapes depend on. If there is one thing to formalise this year, it is
  pricing and packaging the residency/program work you are already delivering.

---

## 9. Naming

`AirKit` is the codebase name and the host software. `AirSticks` is the
instrument. Neither is a product name for a school.

Points to consider:

- What a teacher buys is **the whole thing** — sticks, box, repertoire,
  support. That needs a name, and it probably isn't "AirKit", which describes
  the OSC host and means nothing to a buyer.
- Keeping `AirKit` internally as the platform name is fine and probably right —
  it keeps the R&D identity intact while the school-facing product gets its own
  name.
- **Check trademark availability early**, in AU at minimum. It is cheap now and
  expensive after you have printed enclosures.
- "Air-" is a crowded prefix in music tech and in Apple's product line.

---

## 10. Open questions

Ordered by how much they change the plan.

1. **What does a Pi actually have left over** under two devices, a
   sample-heavy personality and HDMI visuals, in a warm room? This closes §3
   and it is a measurement, not an opinion.
2. **Is the 100 Hz process rate a musical requirement or a headroom artefact?**
   The desktop runs the same loop at 33 Hz. If 33 Hz is fine, a lot of §3
   pressure disappears.
3. **Who is the buyer, precisely** — a therapist with a program budget, a music
   department with an equipment budget, or a school executive? They have
   different price ceilings, different procurement paths, and want different
   evidence.
4. **What does the sensor roadmap actually look like?** Tap, pressure and
   stretch on the *stick*, or a family of different objects — a mat, a cushion,
   a wearable? The second is a much bigger product question, and it is a
   plausible read of the specialist-school need (not every student can hold a
   stick).
5. **Is there an evidence requirement?** Specialist settings often need to
   justify a purchase against outcomes. If so, that is a research output
   SensiLab is well placed to produce, and it should be planned rather than
   improvised.
6. **What is the support commitment you are willing to make?** This determines
   more about the product than any technical choice. "Next release fixes it" and
   "someone comes out within a week" are different companies.
7. **Accessibility and safety compliance** — electrical, battery shipping,
   volume limits for hearing safety, and any duty-of-care requirements in
   specialist settings. Worth finding out before the enclosure is designed, not
   after.
