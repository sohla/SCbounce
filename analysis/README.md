# analysis/

Seven Python scripts mine this repo's git history and its personality files,
write JSON into `output/`, and ten standalone D3 pages render it.

This file is how to *run* it. For what the analyses mean, see
`description.md`.

---

## Run it

```sh
cd analysis
python3 update.py               # regenerate everything into output/  (~60s)
python3 -m http.server 8000     # then open http://localhost:8000/timeline_10.html
```

`update.py` runs all seven analyzers, prints how long each took, and ends with
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
| `imu_type_analyzer.py` | `imu_type_analysis.json` | *(no page consumes this)* | <1s |

The three slow ones each shell out to `git show` once per commit. That is the
whole of the cost.

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
- **No rename detection.** No analyzer passes `-M`/`--follow`, so the
  `code1.0 → code2.0 → code3.0` migration appears as unrelated files rather
  than one lineage — which is a real limitation of `file_networks`.
- **`description.md` refers to `personality_code_timeline.html`**; the file is
  actually `personality_synth_code_timeline.html`.
