# configPlan.md

A plan for replacing per-machine **branches** with per-machine **config files**.

Written 2026-08-11, on `Airsticks-RPI`.

Status, 2026-09-10: **steps 1 and 2 implemented** on `Airsticks-RPI`.
`machines/rpi.scd` and `machines/desktop.scd` exist, `code3.0/machine.scd`
resolves one of them at boot, and the four core files read it. Steps 3–5
(the merge into `AirKitDesktop`, then `mel`/`alon`, then retiring the machine
branches) are not done. `AirConcert` is deliberately out of scope — it is a
different output with its own UI and mechanism, not a machine variant.

The delta table below is the **2026-08-11 measurement and is now stale** — see
the note under it for what actually differed on the day of implementation.

---

## The decision this rests on

`Airsticks-RPI` is now the single branch every AirKit gets. Branches stop being
the way machines differ; a config file becomes the way machines differ.

---

## Why

Several AirKit units need different settings, and until now each unit had its
own branch. Two merges in one day showed what that costs.

**Merge 1 — `AirKitDesktop` → `Airsticks-RPI` (landed, `ebc8fab`).**
Brought the new visual core to the Pi. Divergence was ~40 commits each side,
162 files. Of that, exactly **three core files** needed hand-resolution and one
file — `oscController.scd` — had to be **force-kept** from the RPI side because
git reported *no conflict* on it at all: only desktop had touched it, so
desktop's version would have been taken silently, replacing the OSC device-add
handshake and the airstick colour indices.

**Merge 2 — `Airsticks-RPI` → `AirKitDesktop` (attempted, aborted).**
Worse. Because merge 1 absorbed all of desktop's history into RPI, desktop is
now a full **ancestor** of RPI — not divergent from it, just behind. So the
merge had nothing to reconcile and reported *"Automatic merge went well"* with
zero conflicts, while handing desktop RPI's entire tree. Desktop would have
silently lost every one of its platform values: booting borderless-fullscreen
and talking to the wrong IP, with nothing in the output to say why.

That is the real argument. A conflict stops and asks. This does not.

---

## The actual per-machine delta

Measured, not guessed — this is the complete set of values that differ between
`Airsticks-RPI` (`1a3dd17`) and `AirKitDesktop` (`153c813`).

| # | Value | File | RPI | Desktop |
|---|---|---|---|---|
| 1 | window border / fullScreen | `main.sc:74,78` | `border: false`, `.fullScreen` | `border: true`, no fullScreen |
| 2 | `outAddr` | `oscController.scd:9` | `192.168.100.40:3333` | `192.168.70.211:5005` |
| 3 | config colour indices | `oscController.scd:353` | `[15,16,17]` | `[16,17,18]` |
| 4 | `list` (roster) | `personalityController.scd:16` | `list_yourDNA26.sc` | `list_workshop1.sc` |
| 5 | `~secs` (routine rate) | `personalityController.scd:187` | `0.01` | `0.03` |

Five values. That is the entire permanent difference between a Pi and a laptop.

**Re-measured 2026-09-10, at implementation time.** Three of the five had
already been fixed on `Airsticks-RPI` and two new ones had appeared, so the
machine files carry this set instead:

| # | Value | 2026-08-11 | 2026-09-10 |
|---|---|---|---|
| 1 | window border / fullScreen | divergent | **fixed** — `main.sc` had become a `Platform.case`; now a config key so a Pi on a dev monitor can have a border |
| 2 | `outAddr` | divergent | divergent — `192.168.50.53` vs `192.168.70.211`, both port 5005 |
| 3 | config colour indices | divergent | **gone** — `oscController.scd:353` now reads `msg2.keep(-4)`, no indices |
| 4 | `list` (roster) | divergent | divergent — `list_glenroy.sc` vs `list_alon26.sc` |
| 5 | `~secs` | `0.01` vs `0.03` | **converged** on `0.01`; kept as a key anyway |
| 6 | `numAirwareVirtualDevices` | not measured | **new** — 9 vs 5, and it must equal the roster count |
| 7 | `s.latency` | not measured | **new** — `0.1` vs `0.03` |

Item 6 is the one that would have bitten. It was never in the original table,
it is a silent-clobber value exactly like item 3, and it has to stay in step
with the roster length or `defaultLists[i]` returns nil for the high devices.
It is therefore a single `\numDevices` key that drives both.

Two things that are *not* in the table, and are worth knowing:

- **`listsDir` / `personalityDir` are already identical** on both branches
  (`~/Develop/SuperCollider/Projects/AirKit/`). They are not currently a
  difference. They should still move to config, because the commented-out
  alternatives in `personalityController.scd:3-4` show they have been one
  before, and will be again on a machine that clones elsewhere.
- **`VERSION` path is a hardcoded absolute** in `systemView.scd:152`. It
  happens to match on both machines, so it is not a divergence today — but it
  throws on any machine whose checkout is elsewhere.

Item 3 deserves emphasis: `[15,16,17]` vs `[16,17,18]` is an **airstick
firmware** difference, not a preference. It is per-hardware, and it is the kind
of thing that gets silently clobbered and then debugged for an hour as "the
colours are wrong".

---

## Why not an untracked local file

The obvious design is a gitignored `local.scd` per machine. It was rejected:
if config is not in the repo, a freshly imaged Pi clones and has nothing, the
settings are not backed up, and they cannot be reviewed or diffed.

**The collisions were never caused by config being tracked.** They were caused
by per-machine values living in files that *every* machine edits — `main.sc`,
`oscController.scd`, `personalityController.scd`. Two machines editing one
shared file is what git has to reconcile.

Give each machine its own file and there is nothing to reconcile. The Pi only
ever edits `machines/rpi.scd`; the laptop only ever edits `machines/desktop.scd`.
Different files never conflict, however many branches exist.

So: **tracked, in the repo, one file per machine.**

---

## The design

### `machines/*.scd`

One small tracked file per kit, returning an Event:

```
machines/
  rpi.scd
  desktop.scd
  mel.scd
  alon.scd
```

Each holds the five values above plus the paths and version path — nothing
invented, nothing beyond what is already in the table.

Two fields matter conceptually and should stay **separate**:

- **platform** — window, `~secs`, `outAddr`, colour indices, paths
- **roster** — which `lists/*.sc` to load

Keeping them separate is what makes "Alon reviews Mel's set" work: Alon's file
points at Mel's roster while keeping Alon's own platform settings. Collapse
them into one opaque "kit" and loading Mel's kit drags Mel's platform too,
which is the coupling branches already gave us.

### Rosters already exist

`lists/*.sc` is the roster mechanism, working today — 20+ files, selected by
hand-editing `var list =` in `personalityController.scd`. The plan does not
introduce rosters; it moves the *selection* out of a shared file.

### Selection

`main.sc` picks a machine file at boot. Preferred mechanism is hostname:
`systemView.scd:2` already shells out with `"hostname -I".unixCmdGetStdOut`, so
the approach is proven in the codebase.

**Resolved 2026-09-10.** The open question was that this laptop reports
`MU00157721X` (an institution-managed asset name that may change on reimage)
and the Pis' hostnames were unknown and unverified. Rather than bet on
hostnames, selection is a four-step ladder in `code3.0/machine.scd`:

1. `machines/CURRENT` — one line naming a machine file. **Gitignored**: it is
   the one filename every kit would edit, so tracking it would reintroduce
   exactly the collision this plan removes. The values stay tracked in
   `machines/*.scd`; only the pointer is local.
2. **hostname** — matched against each file's own `\hostnames` list. The list
   lives in the machine file, so there is no shared hostname→machine map to
   conflict over.
3. **platform** — the single file claiming this `\platform`. This is what lets
   a freshly imaged Pi boot correctly with nobody knowing its hostname. It
   holds only while one file per platform exists; the second Linux kit makes
   it ambiguous, and ambiguous falls through to step 4.
4. **nothing matched** — a loud multi-line block naming the hostname, the
   platform and the fix, then built-in defaults.

So it fails loudly, but it does not throw: an unrecognised Pi in the field
still boots and still makes sound, and says why it is unsure. Throwing would
brick a unit beyond the reach of the web rollback, which `gatePlan.md` argues
against for class files and which applies just as well here.

---

## What changes in the core

Four files stop hardcoding and start reading:

| File | Change |
|---|---|
| `main.sc` | load the machine file; window border / fullScreen from it |
| `oscController.scd` | `outAddr` and colour indices from it |
| `personalityController.scd` | `listsDir`, `personalityDir`, roster, `~secs` from it |
| `systemView.scd` | `VERSION` path from it |

One logical change. No new mechanism beyond "read a file at boot".

---

## Migration order

1. ~~Add `machines/` with `rpi.scd` and `desktop.scd`, carrying the exact values
   in the table. Nothing reads them yet.~~ **Done** — plus `code3.0/machine.scd`,
   the loader.
2. ~~Point the four core files at the machine file.~~ **Done, but only verified
   on macOS.** Parse-checked, and the loader and its four consumers were
   evaluated under `sclang`; the Pi boot is still untested and is the step that
   can break boot.
3. **Then** merge `Airsticks-RPI` into `AirKitDesktop`. Safe at this point,
   because the shared files no longer contain anything machine-specific. This
   is also what stops the 17 p-file conflicts from merge 1 recurring.
4. Add `machines/mel.scd` and `machines/alon.scd`.
5. Retire `Airsticks-RPI-Mel` and `AirKitDesktop` as machine branches.

Step 3 must not happen before step 2. Attempting it early is what was aborted
today, and it fails silently.

---

## What it enables

- **Alon is a file, not a branch.** Same hardware as Mel, different repertoire
  — so `machines/alon.scd` is a handful of lines naming a roster. No branch, no
  merges, no ongoing cost.
- **Cross-loading repertoire.** Alon can run Mel's set by pointing at Mel's
  roster, without inheriting Mel's platform settings.
- **Merges stop being dangerous in both directions**, including the silent one.
- **Freezing a kit for a run of gigs** becomes a tag, not a branch — the
  machine file travels with the tag. There are already tags `v0.0.3`–`v0.0.7`
  and a release workflow that fires on `v*`.

---

## Interaction with AirKitWebApp (the updater)

Checked 2026-09-10 against `~/Develop/Web/Projects/AirKitWebApp` @ `6979644`.
**No change is needed in the updater, and `machines/` is safe from it.**

`release.config.json` ships `code3.0`, `personalities`, `lists` and a
generated `VERSION` (`.github/workflows/release.yml`). `machines/` is *not* in
that list, and `engine.py:107-171` backs up, `rmtree`s and replaces **only the
top-level directories actually present in the zip** — "everything else in
APP_DIR (`.git`, `synths`, `analysis`, …) is left untouched."

So a web update replaces the core and the repertoire and **leaves the machine
file alone**. That is exactly the right split and it comes for free: core code
updates, site config survives. Do not "fix" it by adding `machines` to
`release_dirs` — that would make every update `rmtree` the kit's own settings
and replace them with whatever happened to be in the repo, which is the silent
clobber this whole plan exists to prevent.

Three continuity checks, all fine:

- `APP_DIR` is `/home/pi/Develop/SuperCollider/Projects/AirKit`
  (`config/settings.py:13`) — the same layout as the Mac, so `machine.scd`'s
  `dirname.dirname` lands on the repo root and `machines/` sits beside
  `code3.0/`. The derived `listsDir` / `personalityDir` / `versionPath` come
  out identical to the old hardcoded `~/Develop/...` strings, because `~` is
  `/home/pi` there. Nothing moves.
- **`VERSION` still works.** The zip extracts it to the APP_DIR root and
  `systemView.scd` now reads `<root>/VERSION` — the same file. The updater
  keeps owning the version display.
- **Restart re-reads config.** `_launch_startup_script()` (`app.py:328`) runs
  `/home/pi/start_airkit.sh`, a full sclang relaunch, so `main.sc` re-`Require`s
  `machine.scd`. Config is boot-time and the updater's restart is a real
  restart.

The updater reads nothing else that changed — no roster, no
`personalityController.scd`, no `machines/`.

### The one thing that needs doing per Pi

`machines/` is tracked in git but **not in the release zip**, so it reaches a
kit only by `git pull` (or one hand copy). A Pi that receives the new
`code3.0` *by zip alone*, having never got `machines/`, will find no machine
file and boot on built-in defaults: roster `list_dev.sc`, 5 devices, `outAddr`
`127.0.0.1:5005`.

It says so loudly — that is what the UNKNOWN MACHINE block is for — but it is
still the wrong kit. **So each Pi needs `machines/rpi.scd` placed once, before
or alongside the first release that contains `code3.0/machine.scd`.** After
that it is permanent and self-maintaining, and survives every future update.

This is the one ordering hazard in the whole change, and it is the same shape
as "step 3 before step 2": the zip is happy to install a core that is looking
for a file the zip does not carry.

---

## Loose ends

- `VERSION` currently reads `0.0.5` while the latest tag is `v0.0.7`, and
  `systemView.scd` displays that file on the Pi — so kits are reporting the
  wrong version today. Worth fixing whenever the next tag is cut.
- `origin/merge-desk-into-rpi` is a leftover branch label pointing at the same
  commit as `Airsticks-RPI`. Safe to delete once any Pi still tracking it has
  been switched over.
- `AirKitDesktop` still holds stripped versions of 17 p-files (visuals deleted
  from `mel1`/`silence`, `funBass` muted to amp `0.01`, an empty `((0)!0 ++ sig)`
  channel scaffold in `mel1`). Step 3 resolves this by giving desktop RPI's
  versions.
