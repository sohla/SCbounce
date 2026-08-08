# CLAUDE.md

Guidance for Claude Code when working in this repository.

This file is about **`code3.0/` — the live AirKit system**, and specifically
about adding visuals to personality files.

> **Not to be confused with `visuals/visualServer/`.** That is a separate,
> older system: an installed SuperCollider Extension providing
> `VisualServer` / `VisualSynthDef` / `Vbind` and the event types
> `\visual \vpulse \vline \vlive \audioVisual`. It is still installed and
> announces itself at sclang boot. It has its own `CLAUDE.md`. Nothing in this
> document applies to it, and none of its classes are used by `code3.0/`.

---

## Read the atlas first

**Before designing any visual, read `visuals/graphic-scores-atlas.md`.** It is
the design source for everything in this repo — ~90 graphic-score works,
systems and notational traditions from c.1913 to now, reduced to twelve
reusable visual grammars. Do not invent a visual language from scratch; take a
grammar from the atlas and build an original field in it.

It is ~850 lines, so read it in the order it was written to be read:

| Section | Why |
|---|---|
| **§0.2** the `(field, traversal, mapping)` triple | The single most important idea. A graphic score is a rule for converting space into time. |
| **§0.3** six traversal types | Linear playhead, scrolling field, radial sweep, free ranging, superimposition, event-triggered. Decide which one the piece is. |
| **§1** the twelve grammars, G1–G12 | *"This section is the reusable core of the document."* Usually all you need. |
| **§3.1** the space→sound→motion mapping table | The default semantics for x, y, weight, hue, curvature, overlap, whitespace. |
| **§3.4** rules of thumb | Protect the silence, mark the traversal, one grammar per sketch, give marks physics. |
| **§4** coverage map | Indexed **by grammar** — the fast way to find catalogue entries once you know you want, say, G8. |
| **§2** the catalogue | Read only the specific entries you are drawing on. |

Cite the lineage in the p-file's comment header, as `magicWand.sc` and
`bongo1.sc` do — it is both correct attribution and useful to the next reader.

**§0.4 is binding:** these works are under copyright and visually distinctive.
Never reproduce, trace or closely approximate a specific published score page.
Build original fields from the same primitives with the same logic. The correct
output is "a piece in the lineage of X", never "page 47 of X".

---

## Where things live

| File | Responsibility |
|---|---|
| `code3.0/visualCore.scd` | Canvases, the device→canvas router, the `\customVisualEvent` type, and `drawCanvas` (the per-frame draw loop). |
| `code3.0/shapeLib.scd` | Built-in point generators: `circle square line triangle star hexagon cross wave leaf spiral blobby`. |
| `code3.0/personalityController.scd` | Builds each personality's Environment, injects `~vdef` / `~model` / `~device`, runs `~init` / `~next` / `~plot`. |
| `code3.0/plotterView.scd` | The per-device plotter driven by `~plot`. |
| `personalities/*.sc` | The personality files ("p-files"). |
| `visuals/graphic-scores-atlas.md` | Design source document for visual grammars. |

Reference implementations to read before writing a new one: `metal1.sc`
(held event + per-note marks), `magicWand.sc` (spectrum as the mark),
`bongo1.sc` (cyclic rhythm score with rests), `personalities/templateVisual.sc`
(commented skeleton).

---

## A. The API contract

### Emitting a visual event

Add `\type, \customVisualEvent` to a `Pbind`, or build an Event literal and
`.play` it. Both reach the same event type (`visualCore.scd:365`).

```supercollider
// from a pattern
\type, \customVisualEvent,
\shape, \myShape,

// or fired directly, e.g. from a trigger in ~next
(type: \customVisualEvent, amp: 0, dur: 0.01, viewID: d.port,
 shape: \myShape, duration: 1.2).play;
```

**Routing.** The event needs `\viewID`. In a pattern, set it from `~next`:

```supercollider
Pdef(m.ptn).set(\viewID, d.port);
```

Without it the visual is silently dropped **but the audio still plays**
(`visualCore.scd:404`, `:414`). Audio-with-no-picture is the signature of a
missing or wrong `viewID`.

**Visual-only events** (no sound of their own) need `amp: 0` and a short
`dur:`. The event type re-types itself to `\note` and plays at
`visualCore.scd:414`, so without `amp: 0` you get a stray `\default` synth on
every mark.

### Event keys

The authoritative list is the constructor at `visualCore.scd:365`. Carried
keys and their defaults:

| Key | Default | Notes |
|---|---|---|
| `\shape` | `\circle` | shapeLib name, or a vdef name |
| `\points` | `nil` | explicit point array, bypasses shape lookup |
| `\numPoints` | `nil` (→32) | |
| `\sx \sy \ex \ey` | `0.0` | normalised, 0 = canvas centre, ±1 = edge |
| `\xEnv \yEnv` | linear | position envelope over the event |
| `\startWidth \endWidth \widthEnv` | `1`, `1`, linear | stroke weight |
| `\startSize \endSize \sizeEnv` | `50`, `50`, linear | |
| `\startColor \endColor \colorEnv` | white, red α0.2, linear | |
| `\rotation` | `0` | radians, applied about the mark's own origin |
| `\duration` | `0.5` | seconds; `inf` = held (see below) |
| `\fill` | `false` | |
| `\closed` | `true` | |
| `\modulation` | `nil` | see below |

Two further keys are **read by the event type but not carried** into the event
dict, so a draw function never sees them: `\canvas` (overrides the router —
normally omit) and `\vLatency` (how far ahead the mark is stamped, defaults to
`core.latency`).

Three things to know about that table:

- **`\modulation` is the only free-form passthrough.** Any other custom key
  you invent is silently dropped by the constructor. It is therefore how a
  vdef receives per-note data — see the tightness rule in §C.
- **`\envelope` and `\alphaEnv` are dead.** They are carried into the event
  but never read by `drawCanvas`. Setting them does nothing.
- **`\bgColor` is deliberately ignored.** Canvas grounds are fixed black, set
  once in `makeCanvas`.

### What the core does for you

Before your draw function runs, `drawCanvas` has already applied
(`visualCore.scd:97-100`):

- **position** — from `sx/ex` + `xEnv` and `sy/ey` + `yEnv`, mapped to the canvas
- **rotation** — `Pen.rotate` about the mark's own origin, unwound after at `:206`
- **`Pen.width`** — blended from `startWidth`/`endWidth`/`widthEnv`
- **`Pen.fillColor` and `Pen.strokeColor`** — both, blended from `startColor`/`endColor`/`colorEnv`

So a vdef that wants the normal colour can simply call `Pen.stroke` and set
nothing. It also receives a context Event:

```
c: (pos: size: width: color: rotation: normTime: elapsed: now: bounds: view:)
```

`normTime` runs 0→1 across the event's `duration`.

### Stay on the API — the hard rule

**Everything about where a mark is, how big it is, and how it is oriented
comes from event keys. If a draw function is computing that for itself, the
implementation is wrong. Stop and ask rather than working around it.**

The giveaway is `c[\bounds]`. It is in the context so a draw function can know
the surface it is on — not so it can derive positions. Nearly every use is a
mistake, and it is always self-justifying in the moment ("this mark is
canvas-anchored", "a circle needs the aspect"). It isn't. Two corollaries
cover the cases that tempt it:

**A canvas-relative distance comes from a size, not from bounds.** Sizes are
in **pixels**, and pixels are aspect-independent — a radius of 240 is round on
the panel, the HDMI window and a narrow grid column alike. Deriving the same
distance from `min(b.width, b.height)` or, worse, normalising it against the
canvas and adding a correction term, hardcodes one aspect ratio and distorts
everywhere else.

**An angle goes in `\rotation`.** The core rotates about the mark's *own*
origin and unwinds afterwards, so an event anchored at the centre gets polar
placement for free: `\rotation` is the angle, `\startSize` is the radius, and
the draw function only has to step out along `+x`.

`bongo1`'s ring is the worked example. Its marks sit on a circle centred on
the canvas — apparently the strongest case for reading bounds — and it needs
none:

```supercollider
// in the Pbind : the geometry, entirely in event keys
\rotation,  Pfunc({ |e| (e[\cyc] * 2pi) - 0.5pi }),   // where on the ring
\startSize, Pfunc({ |e| e[\rad] * 400 }),             // how far out, in px
                                                      // \sx \sy default 0 = centre

// in the vdef : no geometry at all
var mid  = c[\pos];            // the centre
var ring = c[\size];           // the radius
var pos  = mid + (ring @ 0);   // \rotation already swung the frame
```

Note also that `\sx`/`\sy` normalise against half-width and half-height
**separately**, so they cannot express a circle on their own. That is a reason
to reach for size-plus-rotation, never a reason to leave the API.

### The two vdef contracts

`~vdef.(\name, func)` registers a shape for this device. Which contract you get
is decided by **what the function returns** (`visualCore.scd:129`):

**Points func — preferred.** Return an `Array` of `Point`s. The core then
applies `\modulation`, `\closed` and `\fill` to it exactly as for a shapeLib
shape.

```supercollider
~vdef.(\blob, { |ev, c|
    Array.fill(ev[\numPoints] ? 32, { |i|
        var a = i / 32 * 2pi;
        c[\pos] + Polar(c[\size], a).asPoint
    })
});
```

**Draw func.** Draw with `Pen` and return anything else. `\modulation`,
`\closed` and `\fill` are then **yours to honour** — all three operate on a
point array you never produce.

```supercollider
~vdef.(\blob, { |ev, c|
    Pen.addOval(Rect.aboutPoint(c[\pos], c[\size], c[\size]));
    if(ev[\fill] ? false, { Pen.fill },{ Pen.stroke });
});
```

Use a draw func only when the mark genuinely is not one polyline — concentric
rings (`bongo1`'s `\membrane`), disjoint strokes (`magicWand`'s
`\partialComb`), or separating edges (`bells`' `\shards`). Otherwise use a
points func and inherit the whole pipeline.

Note the free side-effect: because the core skips modulation for draw funcs,
`\modulation` is available to them purely as a data channel.

### Held events

`duration: inf` gives one long-lived mark instead of a stream:

- the cull never expires it, and `normTime` stays pinned at 0
- so every `start*`→`end*` blend resolves to its `start*` value — the event is
  effectively **static**, and all animation must come from the draw func
  reading live state each frame

`metal1`'s `\sheetFrame` is the reference: `~next` writes a plain file-level
var, the draw func reads it at frame rate. That var is the control bus; the
assignment is the visual equivalent of `synth.set`.

`clearEvents` (`visualCore.scd:289`, called from `personalityController.scd:299`
and `:362`) is what removes held events when the personality unloads. Without
it they would outlive their vdef and degrade into a stray fallback circle.

Known limitation: a held event does **not** follow a scene switch
(overlay↔grid), because it is already sitting in one canvas's list while
routes are re-resolved per fire.

---

## B. SuperCollider traps

Each of these was found the hard way. The symptom is never where the cause is.

### A bare `Env` as a `Pbind` value

`Env` implements `embedInStream`, so inside a pattern it is consumed as a
**time-varying value** — your event receives a Float, not an Env. It fails
later, inside the draw loop, as `Message 'at' not understood`.

```supercollider
\sizeEnv, Env([0,1],[1],2),          // WRONG - becomes a Float
\sizeEnv, Pfunc({ myEnv }),          // right
```

Applies to every `*Env` key. A direct `(...).play` Event literal is unaffected
— nothing streams the value there, so a literal `Env` arrives intact.

### `c.size` / `c.width` inside a vdef

`Set` declares `var <size`, so `size` is a real method: `c.size` returns the
context Event's **item count**, not the radius. `doesNotUnderstand` never
fires, so there is no error — just a wrong number.

**Always bracket-access the context:** `c[\size]`, `c[\width]`, `c[\pos]`.

### `var` after a statement

SuperCollider requires all `var` declarations at the top of a function body.
Break that and the **whole p-file fails to compile**, so `interpret` returns
nil and the Environment ends up with no `~init`, `~next` or `~plot`.

The visible symptom is a **Plotter** error — `Message '-' not understood` on an
all-nil array — because `plotterView.scd` calls a nil `~plot` 33×/sec. It
points nowhere near the actual mistake. If you see that error, suspect a
compile failure in the loaded personality first.

### Rests reach the visual type

`\dur, Rest(0.25)` still fires `\customVisualEvent`; only the `\note` play is
skipped. Unguarded, marks appear in the silences. `e.isRest` is true during
`Pbind` evaluation, so pass it through and skip drawing:

```supercollider
\modulation, Pfunc({ |e| (rest: e.isRest, ...) }),
```

### `Pkey` and `Pdef.set`

`Pkey` **can** read a value set with `Pdef(...).set(...)` — the envir is merged
into the event before the `Pbind` runs. That is how `\viewID` works.

But a `Pbind` key of the same name **overrides** the envir. If `~next` should
own a value, comment the key out of the `Pbind` (see `miniMoog.sc`, where
`\amp, 0.3` had to go so `Pdef.set(\amp, ...)` could take effect).

### `~next` has not run when the first events fire

`Pdef(...).play` in `~init` starts immediately; `~next` follows ~30ms later. So
the first events see nil for anything `~next` supplies. A bare
`Pkey(\amp).linlin(...)` throws on `nil`. Use the defaulted form:

```supercollider
\startSize, Pfunc({ |e| (e[\amp] ? 0.2).linlin(0.15, 0.25, 40, 120) }),
```

### `Require` caches — reload `main.sc` after editing the core

`Require` caches by path. Reloading a **personality** does not re-evaluate
`visualCore.scd`; only re-running `main.sc` does, because a root `Require` call
clears the `\evaluate` cache.

Symptom: a correct new vdef draws nothing at all, with no error, while
shapeLib shapes still work — the live `drawCanvas` is the old one.

### `shapeLib` `\square` with `numPoints < 8`

`shapeLib.scd:18` divides by `(count - 1)`. With `numPoints: 4`,
`pointsPerSide` is 1 and every corner gets a nan. Use 8 or more (the default
32 is fine).

---

## C. Design rules

These are the repo-specific rules. The design method itself is in
`visuals/graphic-scores-atlas.md` — see **Read the atlas first** above, and go
there before designing a mark.

- **Pick one grammar and commit.** Two at most. Hybrids of four look like a
  moodboard. (atlas §1, §3.4)
- **Represent the synth, not the idea of the instrument.** Mirror the
  SynthDef's own constants into file-level vars and drive the drawing from
  them, so the visual is provably the sound rather than a decoration next to
  it. `magicWand` mirrors its six `DynKlank` partial ratios and ring times;
  `bongo1` mirrors its four membrane modes. Both draw the actual decay.
- **Mark the traversal.** If a viewer cannot tell how time is moving through
  the space, it has failed as a score however pretty it is.
- **Protect the silence.** Rests and empty space are structural, not gaps to
  fill. `bongo1` leaves its five rests as gaps in the ring, and that is the
  piece.
- **Derive the palette from a substrate** (atlas §0.5), not from UI defaults.
  Canvas grounds are fixed black, so the phosphor/ANS and saturated-primary
  rows work; ink-on-vellum and graph-paper do not.

### Comments: the code tells the story

**No comments inside a `Pbind`, an event literal, or a `~vdef` body.** If a
line needs explaining, name the variable better or pull the expression out —
the code is the explanation.

Comments belong in **one place**: the block above `~vdef` / above the event, in
`~init`. That block carries the context the code genuinely cannot — what the
mark is, the atlas lineage it comes from, and which musical parameter drives
which visual dimension:

```supercollider
// visual : a cyclic score. Hits are laid round a ring by their position
// in a 128 step cycle ... Rests draw nothing, so the gaps are real.
//
// Lineage: the cyclic notations in the atlas crossed with Chladni/cymatic
// plate figures for the mark itself. Atlas grammars G8 / G7.
//
//   cycle    -> angle round the ring       (\cyc -> \rotation)
//   pitch    -> radius                     (\rad -> \startSize)
//   amp      -> head size                  (\modulation)
//   damp     -> how long the mark lives    (\duration)
~vdef.(\membrane, { |ev, c|
    ...
});
```

That mapping table is the useful part and the part nothing else records. A
comment restating what `Pen.width = wid * a` does is noise.

### The tightness rule

A draw function reads `size`, `width` and `color` **from the event** and scales
them only by whatever the physics says is happening at that instant. Every
other constant is a `?` fallback on a `\modulation` key, set in the `Pbind`
next to everything else.

```supercollider
// in the vdef - no magic numbers, only event lookups
var lane = mod[\lane] ? 26;
var wid  = c[\width];
...
Pen.width = wid * a;          // a = this partial's current amplitude

// in the Pbind - every tunable in one place
\startWidth, 5,
\endWidth, 0.6,
\modulation, Pfunc({ |e| (hardness: e[\hardness] ? 0.5, lane: 26) }),
```

File-level vars are for **structure** (SynthDef mirrors) and **state**
(`step`, `droneAmp` — values that must persist between events). Never for
values you would want to tweak; those belong on the event.
