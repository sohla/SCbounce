# airviz — a GLES2 renderer for AirKit visuals

A standalone C program that draws `\customVisualEvent` on the GPU, driven by
OSC from sclang. It is a second output target for `code3.0/`, **not** a
replacement for `visualCore.scd`.

> Unrelated to `visuals/visualServer/`. That is the older installed
> Extension (`VisualServer` / `Vbind`). Nothing here touches it.

---

## Why this exists

Two problems, and the second is the bigger one.

**Resolution.** SC's `UserView` paints through Qt's raster engine — software
rasterisation, antialiasing on by default (QtCollider sets
`QPainter::Antialiasing` in `beginPainting`). 3840×2160 is 10.6× the pixels of
1024×768 on the same CPU core, with no GPU path available.

**The thread.** `drawCanvas` runs *inside the sclang interpreter*, on AppClock
— the same thread as `~next` at 100 Hz (`personalityController.scd:343`,
`:189`), the cull routine, and the plotter at 33 Hz. Overrunning the frame
budget doesn't just make the visuals choppy, it starves the instrument.

Moving the draw into another process fixes the second problem at any
resolution. Doing it on the GPU fixes the first.

---

## Build

Pi 5 / Bookworm:

```sh
sudo apt install libegl1-mesa-dev libgles2-mesa-dev libx11-dev
make
./airviz -v
```

Headless — no GL, no X, builds anywhere including macOS. This is how you test
the SC side without a display:

```sh
make airviz-headless
./airviz-headless -n -v          # prints every event as it is parsed
```

If the fields look right in the headless dump, a problem is in GL. If they
don't, it's in `airviz.scd`.

### Flags

| flag | meaning |
|---|---|
| `-p N` | UDP port (default 57130) |
| `-w N` `-h N` | window size; default is the full screen size |
| `-W` | windowed rather than fullscreen |
| `-n` | headless: parse and schedule, never draw |
| `-v` | 2-second fps / cpu-ms / live-event report, and event dumps under `-n` |

---

## Running it with AirKit

Load the sender **after** `main.sc`:

```supercollider
Require("visuals/airviz/airviz.scd");
```

`airviz.scd` does not edit `visualCore.scd`. It captures the existing
`\customVisualEvent` function and installs a wrapper that sends the OSC
message and then calls the original — so the Qt canvases keep working, both
renderers see every event, and you can A/B them on the same performance.
Re-loading the file swaps the wrapper rather than nesting a second one inside
the first.

If `\customVisualEvent` isn't defined yet it refuses to install and says so,
rather than wrapping a nil — the original is what re-types the event to
`\note` and plays it (`visualCore.scd:531`), so a broken wrapper would
silence the instrument.

---

## Wire protocol

One message per event, `/av/ev`, 41 fixed arguments then an optional blob.
Integers and floats are interchangeable on the reader, so sclang's automatic
typing is never an issue.

| # | field | notes |
|---|---|---|
| 0 | `src` | `viewID` — the device port |
| 1 | `shape` | library name, or anything (→ circle) |
| 2 | `delay` | seconds ahead to schedule; from `vLatency`/`core.latency` |
| 3 | `dur` | seconds; **< 0 means held**, i.e. `duration: inf` |
| 4–7 | `sx sy ex ey` | normalised, 0 = centre, ±1 = edge, y down |
| 8–11 | `xEnv yEnv` | each a (kind:int, curve:float) pair |
| 12–15 | `size0 size1 sizeEnv` | sizes in **pixels** |
| 16–19 | `w0 w1 widthEnv` | widths in **pixels** |
| 20–29 | `col0[4] col1[4] colEnv` | straight RGBA floats |
| 30 | `rot` | radians |
| 31–33 | `fill closed npts` | `closed = -1` means "use the shape's default" |
| 34–38 | `modType freq amp phase harmonics` | `modType 0` = none |
| 39–40 | `p0 p1` | shape params: arc span/start, spiral turns, star inner ratio |
| 41 | *blob* | optional float32 xy pairs, **unit space** |

Env kinds: `0 lin, 1 sin, 2 welch, 3 squared, 4 cubed, 5 curve` (curve field
carries SC's numeric curvature; `\exp` maps to curve −4).
Mod types: `0 none, 1 radial, 2 normal, 3 noise, 4 phase`.

Also: `/av/clear <src:i>` (mirrors `clearEvents`), `/av/quit`.
`#bundle` is unwrapped; **timetags are ignored on purpose** — every event
carries its own `delay` and is scheduled against the renderer's monotonic
clock, so the two processes never have to agree what time it is.

---

## What is ported, and what is not

**Ported exactly**, deliberately including the quirks, so a mark looks the
same under both renderers:

- All twelve `vdefLib.scd` shapes, with their original default point counts
  and constants — including `\star` stepping by `i*pi/5` over 32 points so
  the angle wraps several times (`vdefLib.scd:118` says that *is* the look),
  and `\square`'s minimum of 2 points per side.
- The full envelope blend for position, size, width and colour.
- All four `\modulation` types, with the `|sin(harmonics·t·2π)|` window —
  **so a 2-point path still never moves**, exactly as under Qt.
- Held events: `normTime` pinned at 0, every `start*`→`end*` resolving to its
  `start*`, never culled.
- Unknown shape → circle, warned once.

**Not ported, and it cannot be papered over:** a personality's own `~vdef` is
a SuperCollider closure. It does not cross a socket.

| kind of vdef | status |
|---|---|
| library shape | works |
| points func that sets `\points` | works — sent as a blob |
| points func reading `normTime` / `now` | baked once at fire time, so it no longer animates |
| **draw func** (`c[\render]` / `c[\draw]` sub-paths) | **no representation on this wire** |

`bongo1`'s `\membrane`, `magicWand`'s `\comb` and `metal1`'s `\sheetFrame`
are all draw funcs. They draw a circle here and their real mark under Qt.
Fixing that means either a multi-path event on the wire plus per-frame point
streaming from sclang, or hand-porting each one to C. Neither is free, and
neither should be bundled into anything else.

### Other known divergences

- **`\fill` is a centroid triangle fan.** Correct for every `vdefLib` form
  (`\star` and `\cross` included, which are star-shaped about their
  centroid), wrong for a self-intersecting custom points func.
- **No text.** `Pen.stringAtPoint` has no equivalent. Nothing in `code3.0/`
  uses it inside a visual event.
- **One canvas.** Scenes and the per-device grid are not implemented; every
  `src` draws to the same surface, i.e. `\overlay` only. The grid is a debug
  view and is better left on the panel.
- **Sizes are still pixels**, exactly as `CLAUDE.md` specifies. Going to 4K
  therefore still shrinks every mark to about a quarter of its current
  relative size. That is a repo-wide rescale of 55 p-files and 304 size/width
  values, and it is a separate piece of work — see the note below.

---

## Performance

`-v` prints mean and max CPU milliseconds per frame for the event loop.
That's the number to watch: it is the sclang thread's old workload, now
somewhere it cannot hurt the instrument.

Antialiasing is analytic — strokes are expanded to a triangle ribbon on the
CPU and faded in the fragment shader against a 1px feather. There is **no
MSAA buffer**, which is the one thing on this GPU that really would cost
bandwidth at 4K. AA is therefore free and resolution-independent, and this is
the main reason 4K is affordable here and is not under Qt.

Blending is premultiplied source-over, matching Qt's composite over the fixed
black ground. For additive — often better over black — change the one line in
`gfx.c`:

```c
glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);   /* -> GL_ONE, GL_ONE */
```

### The pixel-scale question

If you do go to 4K, the cheap fix for mark sizes is a single scale applied
once where size and width resolve, rather than editing 55 p-files: multiply
`size` and `width` in `draw_event`, and `modAmp` in `modulate`, by
`min(w,h)/768.0`. That keeps every personality authored against the current
reference. It is a one-line-per-site change here and a core change on the Qt
side — worth deciding once, for both.

### If you want the last few milliseconds

This runs as an X11 client so it can coexist with the compositor that owns
the DSI panel. A dedicated visuals box could instead take the HDMI connector
directly with EGL on GBM/DRM — one fewer composite pass and no compositor
frame pacing — but it needs DRM master, which the desktop already holds.
That's a deployment decision, not a code one; only `gfx.c` changes.
