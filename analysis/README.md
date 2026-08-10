# analysis/

Eight Python scripts mine this repo's git history and its personality files,
write JSON into `output/`, and eleven standalone D3 pages render it.

This file is how to *run* it. For what the analyses mean, see
`description.md`.

---

## Run it

```sh
cd analysis
python3 update.py               # regenerate everything into output/  (~60s)
python3 -m http.server 8000     # then open http://localhost:8000/timeline_10.html
```

`update.py` runs all eight analyzers, prints how long each took, and ends with
a freshness table — the newest commit date found inside each artefact. If those
dates trail the repo HEAD printed at the top, something failed quietly and the
pages are showing you old data. It exits non-zero if any analyzer fails.

The analyzers are independent, so you can also run one on its own
(`python3 analyze_git.py`) when you only need to refresh a single page.

**Requirements:** Python 3 and `git`. No pip install — the scripts are
stdlib-only. The pages load D3, three.js and compromise from CDNs, so viewing
them needs a network connection.

## The server is not optional

Every page loads its data with `d3.json()` or `fetch()`. Both are XHR, and
browsers block XHR against `file://` under the same-origin policy — opening a
page by double-clicking it gets you a blank screen with a CORS error in the
console and nothing else. Serve the directory.

## What produces what

| script | writes to `output/` | rendered by | cost |
|---|---|---|---|
| `analyze_git.py` | `commits_data.json` | `timeline_10`, `timeline_3d_flat`, `timeline_streamGraph`, `timeline_wordCloud`, `commit_activity` | ~20s |
| `analyze_file_networks.py` | `file_networks.json` | `file_networks` | <1s |
| `analyze_personality_code.py` | `personality_code_data.json` | `personality_synth_code_timeline` | ~20s |
| `work_type_analyzer.py` | `work_type_data.json` | `work_type_timeline` | <1s |
| `commit_imu_analyzer.py` | `commit_imu_analysis.json` | `personality_imu_timeline` | ~20s |
| `personality_analyzer.py` | `personality_similarities.json` | `personality_similarities` | <1s |
| `analyze_personality_history.py` | `personality_history.json` | `personality_archive` | ~19s |
| `imu_type_analyzer.py` | `imu_type_analysis.json` | *(no page consumes this)* | <1s |

The three slow ones each shell out to `git show` once per commit. That is the
whole of the cost. `analyze_personality_history.py` covers the same history in
~2s by taking the entire diff in a single `git log -p` and splitting it in
Python; the older three could be rewritten the same way.

## The archive page

`personality_archive.html` is the odd one out: every other page aggregates
across the repo, this one answers questions about a **single p-file**. Pick one
from the dropdown and it shows where it came from, how it evolved, what shipped
alongside it, and what it currently sounds like.

Two things it knows that no other analyzer here does:

- **Lineage, followed all the way back.** It is the only script that passes
  `-M -C --find-copies-harder`, so it can see that `pluck1.sc` was born as a
  67% copy of `sheet2.sc`. **123 of the 183 p-files have a detectable
  ancestor** — most of this corpus was duplicated from a working file rather
  than written fresh, and until now that was invisible repo-wide.

  The chain is walked transitively, not one hop: `mel1` reports
  `animalMat → mattCello → jamesCello → brenton1 → mel1`, five generations
  over fifteen months, with each hop's date, percentage and copy-vs-rename.
  Chains run up to **eight** deep (`cazBathPlug`, `heatherBath`, `nic1`).

  Ancestors that were later deleted are still in the chain — `mattCello` is not
  in the working tree — and render greyed as `(gone)`, because a lineage that
  silently skips its dead links is worse than no lineage.

- **Contested parents.** Git names exactly one source. When a family of
  near-identical patches is in flight, which is normal here, that choice is
  close to arbitrary. The script recomputes the field with a line-set Jaccard
  and says so when the runner-up was within `RIVAL_MARGIN`. **33 of 183 have a
  contested parent** — for those, trust the family, not the exact arrow.

  Note the two metrics disagree in scale: git's percentage and the Jaccard
  score are not directly comparable, and only the *ranking* is used.
- **Generated prose.** The three narratives (evolution, sound, gesture) are
  composed in Python from the evidence, not authored per-file, so they
  regenerate with the data and cannot drift from what the files say. The
  composers are the `narrate_*` functions at the bottom of the script; that is
  the place to change the wording. The evolution account is deliberately a
  chronology rather than a period-by-period table — the counts are already in
  the chart and the commit list directly beneath it, and what prose is for is
  the shape: where the mass of the work sits, how long the silences run, and
  when the sound stopped changing and the playing started.
- **Drawn mapping curves.** Every `m.sensor.lincurve(…)` in `~next` is parsed
  for its numeric arguments and plotted as its actual transfer function, with a
  dashed straight line behind it so the amount of bend is visible. `lincurve`
  is not a shape anyone can read off the digits — the sign of the last argument
  inverts the whole response — and it is how nearly every instrument here gets
  its feel. 218 of the 229 bindings plot; the other 11 have a variable
  somewhere in the arguments (`notes.size` and the like) and cannot be drawn
  from a static read, so they show the binding without a plot.

  The curve maths in the page is a port of SuperCollider's own
  `SimpleNumber.lincurve` / `linexp` / `explin`, **checked against `sclang`** —
  worst deviation 2×10⁻¹². If you change it, re-check it the same way rather
  than by eye.

### The timeline scrubber

Under *step through the changes* the page reconstructs the file at each point
in its history and lets you scrub it. It reads every revision of the file from
git, extracts the same things the current-state analysis extracts, and keeps a
stop **only where something changed**.

That deduplication is the whole design. There are 1,411 (file, commit) pairs in
scope but only **633 stops**, because 98 of the 183 files never change their
mappings at all — scrubbing through 36 identical frames to discover that would
be worse than useless. 125 files have more than one stop.

Each stop shows what changed, colour-coded: green added, red removed, amber
reshaped, blue synthesis, purple comments. A binding that survived but whose
numbers moved is drawn **over a dashed ghost of its previous shape on the same
axes**, so a reshape reads as a movement rather than as two charts to compare
by eye.

`melbb1` is the example worth opening. Over two days in October 2025 it moves
from raw `d.sensors.gyroEvent` to the smoothed `gyroXFiltered`/`gyroYFiltered`,
reverts to raw the same day, then switches back two days later and keeps it —
a try, a revert, and a decision, none of which is visible from the current file
or from the commit subjects.

Deep-link a moment with `#name@state`, e.g.
`personality_archive.html#melbb1@11`.

### Writing notes for the archive

The generated prose reports evidence. It cannot tell you *why* a change was
made, what was learned in a session, or what an instrument is for. Write that
in the p-file itself, in a comment block starting with `note:`:

```supercollider
// note: inherited rather than composed. mattCello -> jamesCello is a copy at
// 100% - identical content under a new performer's name. The patch is not a
// piece so much as an instrument that gets handed on.
```

The archive promotes those to a **Note** panel above the generated prose,
marked "written in the p-file", with the line number. Any length works, and
`note -` and `note —` are accepted too.

It lives in the p-file rather than in a sidecar file deliberately: the note
travels with the code it describes, survives the copies and renames that
define this corpus, and is in front of you while you edit. There is no
`notes.json` and nothing to keep in sync.

This does not replace the comment convention in the repo's `CLAUDE.md` — the
block above a `~vdef` explaining the mark and its atlas lineage is still that.
`note:` is for the archival reading, and unmarked prose blocks of three or
more lines still appear further down the page under *what the file says about
itself*.

Its scope is the same `--all --since=2024-01-01` as everything else, which
costs almost nothing here — only 5 of the 183 p-files predate it, and those are
reported as `predates_window` with no birth commit rather than being given a
false origin date.

It covers the **working tree only**. Ancestors, descendants and co-committed
files routinely name p-files that were later deleted (11 ancestors currently);
those render greyed out and are not clickable, because there is no entry for
them.

One caveat worth knowing when reading the sound and gesture sections: they
describe the file **as it stands now**, not as it was at any point in its
history. Only the evolution narrative and the chart are historical.

## `output/` is gitignored

The scripts are the source of truth; the JSON is disposable and is not
committed. If a page looks wrong or empty, run `update.py` before reading
anything into it.

## The scope invariant

Every analyzer must read the **same commit scope**, or the timelines describe
different populations of commits and cannot be read against one another.

It is currently `--all --since=2024-01-01`, declared at the top of each script
as `GIT_SCOPE` (or `SINCE` in `commit_imu_analyzer.py`).

This has already gone wrong once: `work_type_analyzer.py` was missing `--all`
and so analysed 585 commits while everything else analysed 801, and
`commit_imu_analyzer.py` used a relative `"2 years ago"` window that slid
forward on every run. If you change the scope, change it in all of them.

## Known gaps

- **`imu_type_analysis.json` has no consumer.** Nothing renders it. It is
  also the one scope outlier — `--all` with no `--since`, so it reaches back
  to 2017 while the rest stop at 2024.
- **The `2024-01-01` floor excludes 317 commits**, all of 2017–2023, about a
  quarter of the repo's history. Deliberate, but easy to forget when reading
  a chart that starts abruptly in 2024.
- **No rename detection, except in the archive.**
  `analyze_personality_history.py` passes `-M -C --find-copies-harder`, but it
  is the only one, and it looks at `personalities/` alone. Everywhere else the
  `code1.0 → code2.0 → code3.0` migration still appears as unrelated files
  rather than one lineage — a real limitation of `file_networks`.
- **`description.md` refers to `personality_code_timeline.html`**; the file is
  actually `personality_synth_code_timeline.html`.
