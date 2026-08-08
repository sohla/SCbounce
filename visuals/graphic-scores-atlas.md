# An Atlas of Graphic Scores

### A source document for generating animated visual code

**Scope:** ~90 works, systems, instruments and notational traditions spanning acoustic, electro-acoustic, electronic, conceptual, improvisatory, screen-based, algorithmic, and vernacular practices, c. 1913–present.

**Intended consumer:** Claude Code, generating original animated sketches (Canvas/WebGL/SVG/p5/three.js/shaders) inspired by these visual grammars.

---

## 0. How to use this document

### 0.1 Entry schema

Every catalogue entry uses the same four fields, chosen so they map directly onto code:

| Field | Meaning for a generator |
|---|---|
| **Look** | The static visual description — marks, geometry, ground, density, color. This is your *drawing* spec. |
| **Read** | How a performer traverses it — reading direction, time mapping, indeterminacy rules. This is your *animation* spec: it tells you what moves, and what the moving thing means. |
| **Primitives** | The minimal set of drawable/animatable objects. Feed these straight into a render loop. |
| **Seed** | One concrete sketch brief. A starting point, not a ceiling. |

### 0.2 The single most important idea

A graphic score is **a rule for converting space into time**. Almost every entry here can be reduced to a triple:

```
(field, traversal, mapping)

field      = the marks and their spatial arrangement
traversal  = how a reading position moves through the field
mapping    = what a mark's geometry means when the traversal reaches it
```

Conventional notation fixes all three (left-to-right, staff = pitch, noteheads = onsets). Graphic scores loosen one, two, or all three. **The animation you generate is usually the traversal made visible.** When the traversal is undefined (Brown, Cardew, Cage's transparencies), the animation becomes the *field reconfiguring itself* instead.

### 0.3 Six traversal types, and what each implies in code

1. **Linear playhead** — a cursor sweeps x; marks fire on contact. Cheapest, most legible. (Feldman, Stockhausen *Studie II*, Cat Hope, spectrograms.)
2. **Scrolling field** — the field moves past a fixed cursor. Better for endless/generative material; equivalent math, different feel. (Screen scores, Decibel ScorePlayer, piano-roll.)
3. **Radial sweep** — angular cursor over polar layout; loops without seams. (Crumb, Stockhausen *Zyklus*, *Refrain*, clock/orrery scores.)
4. **Free ranging** — reader chooses path, direction, orientation; multiple simultaneous readers. Implement as agents with steering behaviour. (Brown, Cardew, Applebaum, Marclay.)
5. **Superimposition** — no cursor at all; meaning arises from the *relative position of stacked transparent layers*. Animate by drifting/rotating layers and measuring intersections. (Cage *Variations*, *Fontana Mix*, *Cartridge Music*.)
6. **Event-triggered / conditional** — the next state depends on a rule, a die, a sensor, an audience, or the previous state. (Fluxus, game pieces, Braxton, generative systems.)

### 0.4 Provenance and originality — read before generating

These works are under copyright and are visually distinctive. **Do not attempt to reproduce, trace, or approximate any specific published score page.** Use the descriptions here as a *grammar*: build original fields from the same primitives, with the same logic. The correct output is "a piece in the lineage of Cardew's *Treatise*," never "page 47 of *Treatise*."

Practical rule: if a sketch would be recognisable as a particular page, randomise it further. Vary counts, positions, proportions, and palette per run — seeded randomness is both safer and more interesting.

### 0.5 Design direction (aesthetic guardrails)

The material has real physical origins. Derive palettes from **the substrate the score was made on**, not from generic UI defaults:

| Substrate | Palette anchor |
|---|---|
| Ink on vellum / rapidograph | `#141210` on `#EDE7DA`, hairlines 0.5–1px, no antialiasing softness |
| Blueprint / diazo (studio schematics) | `#0B3D6B` ground, `#D9E6F2` lines |
| Mimeograph / duplicator purple | `#4B2E83` on `#F2EFE6`, blotchy edges, misregistration |
| Graph paper (Feldman) | `#B9C7BA` grid on `#F5F3EA`, filled boxes in near-black |
| 35mm optical soundtrack | `#0A0A0A` / `#F0EDE4`, sprocket rhythm, grain, jitter |
| Oscilloscope / ANS phosphor | `#0D1B0F` ground, `#7CFFA0` trace with decay |
| Data / test-pattern (Ikeda, Alva Noto) | pure `#FFFFFF` on `#000000`, one accent max, no grey |
| Color-field improvisation scores (Braxton, Wadada Leo Smith) | saturated primaries + black, flat fills, hand-drawn wobble |

Avoid: cream + serif + terracotta, black + acid green, and generic "data-viz" gradients. Those are defaults, not choices. Pick a substrate first, then commit to it completely — including grain, registration error, and paper texture where appropriate.

---

## 1. A taxonomy of visual grammars

Twelve grammars. Most entries in the catalogue are one of these, or a hybrid of two. Each is given with its drawable primitives and its natural animation verbs. **This section is the reusable core of the document** — the catalogue is evidence for it.

### G1. Field / constellation
Discrete marks scattered in a bounded plane with no ruled reference. Meaning is relational: proximity, density, clustering.
- *Primitives:* points (varying radius), small glyphs, bounding field, optional connective lines.
- *Verbs:* accrete, drift, cluster, thin out, connect-nearest, Voronoi-partition, twinkle.
- *Math:* Poisson-disc sampling, k-nearest graphs, Delaunay, density fields.

### G2. Trajectory / glissando
Continuous lines through a 2D space where at least one axis is continuous pitch or frequency.
- *Primitives:* polylines, Bézier bundles, ruled surfaces (straight lines forming curves), envelopes.
- *Verbs:* sweep, fan, converge, cross, thicken with intensity, leave decaying trails.
- *Math:* hyperbolic paraboloids from straight-line families, Perlin-driven control points, bundling.

### G3. Grid / lattice
Ruled quantised space; marks occupy cells. The oldest and most computational grammar.
- *Primitives:* cell matrix, filled/empty states, spans across cells, register bands.
- *Verbs:* fill, step, scan row/column, transpose, shift with wraparound, cellular-automaton update.

### G4. Block / register-band
Filled rectangles whose width is duration, height is bandwidth, vertical position is register.
- *Primitives:* rects, opacity = dynamic, stacked bands, overlap-blend.
- *Verbs:* extend, stack, fade, crossfade, collide, occlude.

### G5. Calligraphic / gestural
The mark records the movement of a hand or body. Speed, pressure, and ink-load are the information.
- *Primitives:* variable-width strokes, splatter, blot, dry-brush skips, overshoot.
- *Verbs:* draw-on (reveal along path), splatter-burst, bleed, dry out.
- *Math:* stroke width from velocity, tapered polylines, ink diffusion.

### G6. Iconographic / symbolic vocabulary
A designed alphabet of non-standard symbols with (partly) fixed meanings — a private notation system.
- *Primitives:* glyph atlas, composition rules, connectors, modifiers/diacritics.
- *Verbs:* emit glyph, mutate glyph, chain into phrases, cross-fade between glyph families.

### G7. Topographic / contour
Isolines, terrain, envelopes, traced outlines of found objects.
- *Primitives:* contour sets, filled level bands, traced silhouettes, hatching.
- *Verbs:* raise/lower the water level, morph the underlying field, march the cursor along an isoline.
- *Math:* marching squares over an animated scalar field.

### G8. Circular / radial / spiral
Time as angle. Inherently loopable; no seam, no page-turn.
- *Primitives:* concentric rings, radial spokes, Archimedean/logarithmic spirals, rotating overlays.
- *Verbs:* rotate at differing rates (orrery), phase, unwind, precess.

### G9. Mobile / modular / transparency
Discrete parts with no fixed order, or stacked transparent layers whose intersections generate values.
- *Primitives:* movable modules, transparent sheets, alpha blend/multiply, measured perpendiculars.
- *Verbs:* shuffle, rotate a layer, translate a layer, re-measure, recombine.
- *Key technique:* compute intersections between layers *and render the measurement itself* — the dropped perpendicular is beautiful and is the actual meaning.

### G10. Data / pattern / machine-legible
Barcodes, matrices, waveforms, spectrograms, code listings, punch cards. Score and signal are the same object.
- *Primitives:* dense binary grids, scanlines, bit-planes, FFT bins, glitch/tear artifacts.
- *Verbs:* scan, decode, transpose bit-planes, strobe, tear/displace rows.

### G11. Text / instruction / conceptual
The visual is typography and page layout; the "sound" is an action. Card decks, single lines, imperative verbs.
- *Primitives:* typographic blocks, cards, blank space, rules, stamped/typewritten texture.
- *Verbs:* deal, flip, reveal one line at a time, let a card sit unreadably long.
- *Note:* generate **original** instruction texts. Do not reproduce existing Fluxus scores verbatim.

### G12. Circuit / apparatus / system diagram
The score is a wiring diagram, floor plan, or signal-flow chart — a description of a machine, not of sounds.
- *Primitives:* nodes, ports, edges with orthogonal routing, junction dots, labels, feedback loops.
- *Verbs:* animate signal packets along edges, light up nodes, introduce feedback and let it saturate.

---

## 2. Catalogue

### A. Proto-graphic and precursors (1913–1950)

**1. Luigi Russolo — *Risveglio di una città* (c. 1913–14), intonarumori**
- **Look:** Thick continuous horizontal lines undulating across a ruled staff-like grid, one line per noise-instrument family; no discrete noteheads. Reads like a seismogram with a time signature.
- **Read:** Left-to-right, but pitch is continuous — the line *is* the glissando.
- **Primitives:** ruled ground, thick monotone polylines, family labels.
- **Seed:** 6 stacked lanes, each a slow-moving 1D noise walk; line weight = amplitude; the grid stays rigid while the lines refuse it. (G2, G3)

**2. Kurt Schwitters — *Ursonate* (1922–32), typographic sound poem**
- **Look:** The score is typesetting. Weight, size, spacing and capitalisation carry dynamics and articulation; the page is a Bauhaus-adjacent typographic composition.
- **Read:** Read as text, but the *design* is the performance instruction.
- **Primitives:** type as visual object, tracking/leading as duration, repetition blocks.
- **Seed:** Nonsense phoneme strings where font-size = loudness, letter-spacing = tempo, and a playhead reads them at a variable rate. (G11)

**3. Marcel Duchamp — *Erratum Musical* (1913)**
- **Look:** Ordinary staves filled by chance-drawn notes; visually plain, conceptually radical. Related apparatus: numbered balls drawn from a container.
- **Read:** Normal — the *composition process*, not the notation, is the innovation.
- **Primitives:** urn/drawing mechanism, sequence buffer.
- **Seed:** A visible drawing-machine: balls fall, are read, and write marks onto a staff in real time. The mechanism is the animation. (G11, G12)

**4. Wassily Kandinsky — *Point and Line to Plane* (1926) / Paul Klee's polyphonic drawings**
- **Look:** Not scores, but a systematic theory of point, line, plane and their "sounds." Klee's grids of overlapping colour-planes explicitly transcribe polyphonic voice-leading into stacked translucent rectangles.
- **Primitives:** translucent overlapping planes, point-line-plane hierarchy, colour as timbre.
- **Seed:** Four independent rectangular "voices" drifting horizontally at different rates; where they overlap, multiply-blend. Harmony = colour mixing. (G4)

**5. Ernst Chladni figures / cymatics**
- **Look:** Sand on a vibrating plate self-organising into nodal patterns — starbursts, lattices, mandalas that reconfigure at each resonant frequency.
- **Read:** Not a score at all; a *natural notation* — sound writing itself.
- **Primitives:** particle system, standing-wave scalar field, nodal lines (zero-crossings).
- **Seed:** Particles descending a gradient of |sin(mπx)sin(nπy)|; sweep m,n and let the pattern snap between modes. (G7)

---

### B. Acoustic and instrumental graphic scores (1950–)

**6. Morton Feldman — *Projection 1* (1950) and the *Projection*/*Intersection* series**
- **Look:** Graph paper. Three horizontal registers (high/middle/low). Sound is a rectangle or box occupying a register; horizontal extent = duration measured in fixed time units; small icons distinguish arco / pizzicato / harmonic. Sparse, quiet, enormous amounts of white.
- **Read:** Strict left-to-right at a fixed pulse; register is *relative* — the performer chooses actual pitch inside the band. The score constrains without determining.
- **Primitives:** faint grid, register lanes, filled/outlined boxes, tiny timbre glyphs, generous margins.
- **Seed:** The purest playhead sketch. Three lanes, boxes placed by Poisson process, ~85% empty. When the cursor enters a box, the box brightens and a resolved "actual pitch" dot appears at a random height inside it — visualising the performer's choice. (G3, G4)

**7. Earle Brown — *December 1952* (from *Folio*)**
- **Look:** A white field with ~30 solid black horizontal and vertical rectangles of varying length and thickness. Nothing else. No staff, no marks of duration, no top or bottom.
- **Read:** Any orientation, any direction, any speed, any number of readers. Brown described it in relation to Calder's mobiles and Pollock's fields — the reader moves *through* a three-dimensional imagined space.
- **Primitives:** axis-aligned rects only; two dimensions of variation (length, thickness); pure black on pure white.
- **Seed:** The definitive free-traversal sketch. Generate the field once; run 4 independent agents with different velocities and headings; each leaves a fading trail and flashes rectangles it crosses. Rotate the *entire canvas* slowly to make the orientation-independence literal. (G1, G4)

**8. Earle Brown — *Available Forms I & II* (1961–62)**
- **Look:** Pages of notated "events," each a self-contained graphic-notational island, numbered in boxes.
- **Read:** The conductor points at events in real time — order and overlay decided live. "Open form."
- **Primitives:** module cards, selection pointer, active-set highlight.
- **Seed:** A grid of 20 event-tiles; a pointer jumps between them by weighted random walk; selected tiles animate their internal content and are pushed onto a visible "now playing" stack. (G9)

**9. John Cage — *Concert for Piano and Orchestra* (1957–58)**
- **Look:** Dozens of distinct notation types across 63 pages — clusters of noteheads, geometric figures, curved lines, sizes indicating duration, marks derived from paper imperfections.
- **Read:** Any amount, any order, any duration; parts are independent, no master score.
- **Primitives:** a *library* of heterogeneous notation objects rather than one grammar.
- **Seed:** A generator that randomly composes pages from 12 different sub-notations — the point is stylistic heterogeneity on one surface. (G6, G9)

**10. John Cage — *Aria* (1958)**
- **Look:** Continuous curving lines drawn in ten different colours across the page height, each colour denoting a vocal style chosen by the singer; black squares mark unpitched/noise events; text in several languages runs beneath.
- **Read:** Left-to-right; height = relative pitch; colour = manner of production.
- **Primitives:** smooth multi-coloured ribbons, black squares, text baseline.
- **Seed:** Ten colour-coded Catmull-Rom curves; a cursor sweeps and the curve under it swells; black squares punch holes in the ribbon. (G2)

**11. John Cage — *Fontana Mix* (1958)**
- **Look:** A kit, not a page: transparent sheets with points; transparent sheets with six differently-shaped curved lines; a grid; a single straight line on a transparency.
- **Read:** Superimpose sheets arbitrarily; connect points with the straight line; read intersections against the grid to derive parameters.
- **Primitives:** point layer, curve layer, grid layer, measuring line, intersection markers.
- **Seed:** The canonical superimposition sketch. Slowly drift and rotate three transparent layers; continuously recompute intersections; draw the measuring line and its perpendiculars, and pulse each intersection as it forms. The *measurement* is the artwork. (G9)

**12. John Cage — *Variations I & II* (1958/61)**
- **Look:** Six square transparencies. One carries points of several different sizes (size = number of parameters affected); the others each carry five straight lines, each line standing for one sound parameter (frequency, amplitude, timbre, duration, point of occurrence).
- **Read:** Drop perpendiculars from each point to each line; the measured distances are the parameter values.
- **Primitives:** points with radius-encoded meaning, five labelled lines, perpendicular droppers, distance readouts.
- **Seed:** Animate the perpendicular-dropping as a mechanical process — one point at a time, five thin lines shooting out, numbers appearing and dissolving. Make the geometry legible; the whole piece is a ruler-and-compass instrument. (G9)

**13. John Cage — *Cartridge Music* (1960)**
- **Look:** Transparent sheets bearing irregular amoeba-like shapes, dotted circles, points and a circle marked like a stopwatch.
- **Read:** Overlay to build a personal performance timetable; the stopwatch circle converts geometry to clock time.
- **Primitives:** blobby closed curves, dotted circle, timing dial.
- **Seed:** Overlaid blobs + a rotating clock hand; every time the hand crosses a blob boundary, fire an event. (G8, G9)

**14. John Cage — *Atlas Eclipticalis* (1961–62)**
- **Look:** Notes derived by tracing star positions from an astronomical atlas onto staves; note size indicates dynamic. Visually: a sky, faintly ruled.
- **Read:** Any duration, any subset of the 86 parts; conductor's arms as a slow clock.
- **Primitives:** star field with magnitude-scaled radii, faint staff overlay, tracing lines.
- **Seed:** Real star-catalogue-style distribution (clustered, magnitude power-law); staff lines fade in over the sky; a slow conductor-arm sweep converts stars to events. (G1)

**15. John Cage — *Ryoanji* (1983–85)**
- **Look:** Contours traced around a fixed set of stones, repeatedly, at different placements — overlapping outline drawings on a horizontal band. Beautifully sparse.
- **Read:** Each contour is a continuous glissando for the instrument; garden as time-line.
- **Primitives:** irregular closed contours, repetition with offset, band constraint.
- **Seed:** 15 stone silhouettes, each traced 4× at jittered positions; a playhead reads the *upper envelope* of the accumulated outlines as pitch. (G7)

**16. Cornelius Cardew — *Treatise* (1963–67)**
- **Look:** 193 pages of large-scale abstract drawing: circles, arcs, blocks, number-lines, staff fragments that appear and dissolve, and a single horizontal centre line that persists through nearly every page as a spine. Precise draughtsmanship, no colour, no instructions of any kind.
- **Read:** Entirely open. No performance directions, no key. The persistence of the centre line is the only continuity.
- **Primitives:** the spine line; geometric solids; arcs; parallel-line bundles; occasional intrusions of real notation; deliberate scale contrast (a huge circle beside a tiny numeral).
- **Seed:** The strongest single sketch in this document. A fixed horizontal spine across the canvas; a slowly scrolling field of generated abstract figures that interact with the spine — bending it, being clipped by it, orbiting it. Never explain the symbols. Let scale contrast do the work. (G1, G5, G6)

**17. Sylvano Bussotti — *Five Piano Pieces for David Tudor* (1959)**
- **Look:** Explosive calligraphy. Staves are torn, bent, and swept through by ink gestures; fragments of conventional notation are embedded in what is essentially an abstract drawing. Famously reproduced in Eco's writing on the open work.
- **Read:** The drawing's energy is the instruction; the performer interprets density, velocity, and direction of stroke.
- **Primitives:** variable-width ink strokes, curved/broken staves, splatter, embedded noteheads.
- **Seed:** Draw-on animation: strokes reveal along their path at velocity-dependent width; the staff beneath warps as a spring mesh under each stroke. (G5)

**18. Roman Haubenstock-Ramati — *Mobile for Shakespeare* (1959), *Konstellationen*, *Alone I***
- **Look:** Elegant, architectural "musical graphics": arrow-forms, filament bundles, tapering wedges, nested arcs, arranged as spatially balanced constellations. Cleaner and more designed than Bussotti.
- **Read:** Mobile form — modules read in variable order; arrows indicate possible routes.
- **Primitives:** arrow-glyphs, tapered filament bundles, module boundaries, route graph.
- **Seed:** A directed graph whose nodes are drawn modules and whose edges are curved arrows; a token traverses the graph, animating each module as it arrives. Re-route every cycle. (G6, G9)

**19. Anestis Logothetis — *Odyssee*, *Kulmination*, *Styx* (1960s)**
- **Look:** The most systematically *invented* notation here. Logothetis built a consistent symbol vocabulary — dense hatchings, ray-bundles, spirals, thickening bands, dotted swarms — with defined classes of "action signal," so his scores are legible as a language while looking like abstract graphics.
- **Read:** Symbols specify sound *behaviour* (attack, growth, dispersion, tremor) rather than pitch; routes across the page are often multi-directional.
- **Primitives:** a genuine glyph atlas with parameterised members: hatch density, ray count, spiral tightness, band taper.
- **Seed:** Build a parametric glyph library (8 families × continuous parameters), then compose pages by grammar. This is the entry that most rewards a *procedural typography* approach. (G6)

**20. Iannis Xenakis — *Metastaseis* (1953–54) and *Pithoprakta* (1955–56)**
- **Look:** The famous graph sketches: dozens of individual string glissandi drawn as straight lines on a time/pitch grid, whose collective envelope forms a curved ruled surface (a hyperbolic paraboloid). *Pithoprakta*'s sketch resembles the trajectories of gas molecules.
- **Read:** Direct: x = time, y = pitch, each line = one player. A rare case where the graphic is *more* precise than notation, not less.
- **Primitives:** dense straight-line families, ruled surfaces, envelope curves, stochastic point clouds.
- **Seed:** 60 line segments whose endpoints move along two curves — the classic string-art construction. Animate the two guide curves and let the ruled surface breathe. Add a second mode where each line is an independent Brownian walker (Pithoprakta). (G2)

**21. György Ligeti / Rainer Wehinger — *Artikulation* listening score (1958 / 1970)**
- **Look:** Not a compositional score but a *hörpartitur* — an after-the-fact visual analysis of an electronic tape, in bright colour: combs of parallel strokes, swarms of dots, blobs of granular texture, long tapering wedges, on a black-ruled time axis. One of the most immediately animatable images in the field.
- **Read:** Strict left-to-right synchronised playback; colour = sound family; vertical position = register.
- **Primitives:** dot swarms, comb-rakes, filled blobs with soft edges, wedges, coloured strokes on white.
- **Seed:** A scrolling field generated by a "texture engine" with 6 event types; each type has its own emitter (burst, comb, smear, wedge). This is the best template for *sonification-style* animation. (G1, G4, G10)

**22. Karlheinz Stockhausen — *Zyklus* (1959), solo percussion**
- **Look:** Spiral-bound, no fixed first page. Symbols form a continuum from precisely-notated to entirely graphic across the piece; layout is a horizontal ribbon of percussive events.
- **Read:** Begin at any page; play forward and return to the start point; the score may be read upside-down — literally rotate the book.
- **Primitives:** ring-buffer layout, dual-orientation glyphs, determinacy gradient.
- **Seed:** A closed loop of material; a start-point chosen at random each cycle; halfway through, flip the canvas 180° and continue — the same field now reads differently. (G8)

**23. Karlheinz Stockhausen — *Refrain* (1959)**
- **Look:** Curved staves radiating across a page, with a transparent plastic strip pivoted at the centre carrying the "refrain" material.
- **Read:** Rotate the strip to a new position for each performance; wherever it lands, its material interrupts the underlying music.
- **Primitives:** curved staff arcs, pivoting transparent overlay, interruption events.
- **Seed:** Rotating overlay on a radial field; where the strip crosses a stave, the underlying material is visibly *displaced*. (G8, G9)

**24. Karlheinz Stockhausen — *Plus-Minus* (1963)**
- **Look:** Seven pages of symbols plus seven pages of note-forms; a process notation with +, −, and 0 operators controlling growth and decay of material.
- **Read:** The score is a program. Elements accumulate or erode according to operators; a realisation must be derived before performance.
- **Primitives:** symbol tokens, operator states, accumulation counters.
- **Seed:** A visible state machine: shapes grow by + and erode by −; when a shape's counter hits zero it is replaced. Show the counters. (G11, G12)

**25. Mauricio Kagel — *Transición II* (1958–59) and *Ludwig van* (1969)**
- **Look:** *Transición II* combines piano, percussion-on-piano, and live tape with layered mixed notation. *Ludwig van*: the "score" is photographs of a room whose surfaces are papered with fragments of Beethoven — the performer plays what is visible, distorted by perspective.
- **Read:** Read the photograph. Occlusion, angle, and blur are musical parameters.
- **Primitives:** perspective-projected notation fragments, occluders, depth blur.
- **Seed:** 3D scene: planes textured with generated notation, a camera drifting through; the "playhead" is the camera, and notation is legible only when nearly frontal. (G7, G9)

**26. George Crumb — *Makrokosmos* (1972–79), *Black Angels* (1970)**
- **Look:** Staves bent into symbolic shapes — a spiral ("Spiral Galaxy"), a circle ("The Magic Circle of Infinity"), a cross ("Crucifixus"), a peace sign in Volume II. Exquisite hand-copied calligraphy; the symbol is both image and playable staff.
- **Read:** Follow the deformed staff along its shape; a circular staff implies perpetual motion.
- **Primitives:** path-constrained staves, notes positioned by arc-length, symbolic silhouettes.
- **Seed:** A generic "staff-along-a-path" renderer: give it any SVG path and it lays five lines and noteheads along it. Then animate a cursor travelling the path. Immediately reusable. (G8)

**27. Cathy Berberian — *Stripsody* (1966), score drawn by Roberto Zamarin**
- **Look:** A comic strip. Onomatopoeia words (drawn in comic lettering) are placed on a three-level implied staff for high/middle/low voice, interleaved with cartoon vignettes.
- **Read:** Left-to-right as a comic; height = pitch register; lettering style = delivery.
- **Primitives:** word-as-object with weight/size/style, three register bands, panel vignettes.
- **Seed:** Typographic playhead sketch — generated onomatopoeia flying in at heights, scaled by loudness, sheared by attack, with speed-lines. Deliberately funny. (G11, G4)

**28. Toshi Ichiyanagi — *Music for Piano* series, *IBM for Merce Cunningham* (1960)**
- **Look:** Fields of dots and small marks on otherwise blank paper; in *IBM*, marks derived from computer punch-card holes.
- **Read:** Dots map to events with performer-determined parameters; the punch-card grid provides the underlying quantisation.
- **Primitives:** punch-card matrix, hole/no-hole, sparse dot field.
- **Seed:** A punch-card reader: an 80×12 hole matrix scrolls past a read-head; columns trigger events; occasionally show the card physically. (G3, G10)

**29. Christian Wolff — *Edges* (1968), *For 1, 2 or 3 People* (1964)**
- **Look:** A page of isolated symbols scattered around a large empty space — the symbols describe the *boundaries* of the piece, not its content. Elsewhere, coordination cues: play only in response to another player.
- **Read:** Play *around* the symbols, approaching them without stating them. Relational, contingent.
- **Primitives:** boundary glyphs at the periphery, an empty centre, inter-agent dependency edges.
- **Seed:** Agents that only move when another agent moves; symbols at the canvas edge act as attractors that are never reached. Visualise the dependency edges. (G1, G11)

**30. Mark Applebaum — *The Metaphysics of Notation* (2008)**
- **Look:** A continuous hand-drawn scroll (originally ~72 feet, installed around a gallery) of dense, invented, obsessively detailed symbols — machine parts, organic tangles, pseudo-technical diagrams. No key exists.
- **Read:** Performers roam the installation; a slowly moving physical pointer (a mobile) indicates a region.
- **Primitives:** infinite scroll, extremely dense procedural line-work, a wandering pointer.
- **Seed:** An endless procedurally-generated scroll (seeded, so it can be revisited) with a slow pointer that determines which region is "active." Density should reward zooming. (G5, G6)

**31. Chiyoko Szlavnics — drawing-based scores (2000s–)**
- **Look:** Sparse compositions of long straight and gently curved lines, drawn first as pure drawings, then measured and converted into precise sustained pitches and glissandi. The drawing precedes the music.
- **Read:** x = time, y = frequency, with real numeric mapping — lines intersect to create beating and difference tones.
- **Primitives:** few, very long lines; intersection points; near-parallel pairs (which is where the beating lives).
- **Seed:** 8 long lines, mostly near-horizontal, slowly changing slope; render intersections as expanding interference rings; where two lines are nearly parallel, show a moiré/beat pattern between them. Minimal and precise. (G2)

**32. Jennifer Walshe — *Historical Documents of the Irish Avant-Garde* / *THMOTES* and related works (2000s–)**
- **Look:** Hybrid scores mixing hand-drawn glyphs, photographs, found documents, video stills and typed instruction, presenting an invented archive. Deliberately unstable in register — funny, forensic, fake-historical.
- **Read:** Multimedia; performer navigates documents as much as notation.
- **Primitives:** collage layers, photocopy degradation, annotation, mixed media.
- **Seed:** A collage engine: generated "archival" fragments layered with multiply blending, xerox noise, and tape marks; a cursor that annotates as it moves. (G11, G9)

---

### C. Electro-acoustic, mixed media, and apparatus scores

**33. Karlheinz Stockhausen — *Mikrophonie I* (1964)**
- **Look:** A connection schema (*Verbindungsschema*) plus 33 modular "moments," each described by symbols for excitation, microphone distance/movement, and filtering. Part diagram, part notation.
- **Read:** Moments are assembled into a form-scheme; three performer roles (exciter, microphone, filter) act on one tam-tam.
- **Primitives:** signal-flow graph, module cards, three-role assignment, distance parameter.
- **Seed:** A single resonating object at centre; three orbiting agents (striker, mic, filter); render the signal chain as animated edges whose thickness responds to the agents' proximity. (G12, G9)

**34. Mauricio Kagel / studio-era realisation scores generally**
- **Look:** Tape-studio realisation scores: splice diagrams, tape-length measurements in centimetres, speed-change ramps, channel routing.
- **Primitives:** tape strip as timeline, splice marks, ramp envelopes, channel lanes.
- **Seed:** A tape transport: a horizontal strip scrolls, splices are visible cuts, and speed changes physically stretch the strip's texture. (G4, G12)

**35. David Tudor — *Rainforest* (1968–73) and his realisations of Cage**
- **Look:** Two things. (a) Tudor's *realisation* sheets: hand-drawn graph-paper measurement charts converting Cage's transparencies into playable data — beautiful working documents. (b) *Rainforest*'s "score" is essentially a circuit and suspended-sculpture plan: objects driven as loudspeakers, their resonances re-picked-up.
- **Read:** (a) is a conversion procedure; (b) is a system to be built and inhabited.
- **Primitives:** measurement grid with hand-plotted values; node-and-edge circuit with feedback loops; physical objects as filters.
- **Seed:** Split-screen: left, the abstract transparency; right, the derived graph-paper data being plotted point by point. Show the *translation*. (G12, G9)

**36. Alvin Lucier — *Music on a Long Thin Wire* (1977), *I Am Sitting in a Room* (1969), *Vespers* (1968)**
- **Look:** Prose instructions and setup diagrams. *Long Thin Wire*: a wire, a magnet, an oscillator, and the room. *Sitting in a Room*: an iterative feedback procedure in one paragraph.
- **Read:** Build the apparatus; the physics performs the piece. The visual interest is in the *process*, which is recursive.
- **Primitives:** apparatus diagram; iteration counter; a spectrum that progressively collapses onto room resonances.
- **Seed:** The definitive recursion sketch. Take any image/waveform, re-render it through a fixed "room" transfer function repeatedly, showing generation N. Detail dissolves into resonance. Elegant and cheap. (G12, G10)

**37. Robert Ashley — *The Wolfman* (1964), *Perfect Lives* (1978–83)**
- **Look:** Amplitude-and-feedback instructions; later, television storyboards — the opera's score includes camera and edit logic.
- **Primitives:** storyboard grid, cut-timing lane, feedback threshold.
- **Seed:** A storyboard whose panels advance on a strict rhythmic grid while their contents feed back into each other. (G3, G11)

**38. David Behrman — *Wave Train* (1966), *Runthrough* (1967–68)**
- **Look:** Little or no notation; homemade circuits with hand-labelled controls and photocells. The score is the instrument's affordances.
- **Primitives:** control-surface diagram, photocell responding to light, threshold triggers.
- **Seed:** A light-source moving across a plane of photocells; each cell's output drives a visual oscillator. Interaction, not notation. (G12)

**39. Nicolas Collins — hardware-hacking schematics / *Handmade Electronic Music* practice**
- **Look:** Hand-drawn circuit sketches, breadboard photos, bent-toy annotations. Vernacular technical drawing.
- **Primitives:** sketchy schematic symbols, hand lettering, arrows, "try here" annotations.
- **Seed:** A schematic that redraws itself — components migrating, connections rerouting, with an audible-looking signal flowing through. (G12)

**40. Ellen Fullman — Long String Instrument floor-plans (1980s–)**
- **Look:** The score includes the *architecture*: a plan of a 20+ metre string array and the performer's walking path along it, since sound is produced by walking while stroking the strings.
- **Read:** Movement through physical space = movement through the piece.
- **Primitives:** long parallel string lines in plan view, a walking path, contact points, resonance nodes.
- **Seed:** Top-down: 40 parallel lines; a walker moves along them; longitudinal waves visibly propagate outward from the contact point. (G2, G12)

**41. Éliane Radigue — synthesiser setting notes and the *Occam* series (1970s / 2011–)**
- **Look:** For the ARP works, meticulously hand-written parameter tables and patch notes — private technical documents. For *Occam*, the "score" is transmitted person-to-person, often anchored by a single image (a waterfall, a natural form) rather than a page.
- **Read:** Extremely slow parameter interpolation; the piece is a trajectory through a settings space.
- **Primitives:** parameter table, interpolation curves over very long durations, single reference image.
- **Seed:** A patch-sheet where every value drifts imperceptibly; over ten minutes the whole sheet has changed and no single frame shows motion. Test of restraint. (G11, G12)

**42. Pauline Oliveros — *Sonic Meditations* (1971), Expanded Instrument System diagrams**
- **Look:** Text meditations (attention instructions rather than sound instructions); separately, EIS signal-flow diagrams with delay lines and routing matrices.
- **Read:** Awareness-directed; often no external result required.
- **Primitives:** typed instruction card; delay-network graph with variable-length taps.
- **Seed:** A delay network drawn as a graph where a single impulse spawns echoes travelling edges of different lengths, filling the graph with polyrhythm. (G12, G11)

---

### D. Electronic studio, analysis, and machine-legible scores

**43. Karlheinz Stockhausen — *Studie II* (1954)**
- **Look:** Possibly the most beautiful technical score ever published. Three stacked panels: the top plots frequency bands as outlined rectangles on a time/frequency grid; the middle shows the time structure; the bottom shows amplitude as trapezoidal envelope shapes in decibels. Ruler-drawn, unornamented, entirely legible.
- **Read:** It is a *construction* document — instructions for building the tape, and simultaneously a perfect graphic of the result.
- **Primitives:** log-frequency grid, band rectangles, trapezoid envelopes, three synchronised panels.
- **Seed:** Three linked panels with one shared playhead; envelopes drawn as literal trapezoids; frequency axis logarithmic. The cleanest possible "electronic music looks like this" sketch. (G3, G4)

**44. Gottfried Michael Koenig / Herbert Brün — algorithmic composition documents (1960s)**
- **Look:** Program listings, parameter tables, printer output. The score is code and its printout.
- **Primitives:** monospaced listing, line numbers, column-aligned parameter fields, fanfold paper.
- **Seed:** A terminal that generates a score as text and simultaneously renders it graphically beside itself. Show the code as the aesthetic object. (G10, G11)

**45. Henri Pousseur — *Scambi* (1957)**
- **Look:** A modular tape work: sections whose properties (rising/falling density, continuity) are charted so listeners/editors can assemble their own version.
- **Primitives:** module blocks with in/out property tags, valid-connection rules.
- **Seed:** A jigsaw: modules with coloured edges; only matching edges may join; the sequence rebuilds itself continuously. (G9)

**46. Spectrograms as scores — Aphex Twin (*Windowlicker* B-side, *ΔMi−1*), Venetian Snares (*Songs About My Cats*)**
- **Look:** Images hidden *in the audio itself*, visible only in a spectrogram — a face, cats. Score and signal are literally the same data.
- **Read:** Time = x, frequency = y (log), brightness = magnitude. Read by machine.
- **Primitives:** FFT bin matrix, log-frequency mapping, magnitude→brightness ramp.
- **Seed:** Bidirectional: render an image as a spectrogram, then re-analyse it back into an image, showing the degradation. Also: a live scrolling spectrogram whose "image content" slowly emerges. (G10)

**47. Ryoji Ikeda — *datamatics*, *test pattern*, *supercodex* (2000s–)**
- **Look:** Pure black and white. Dense binary matrices, barcodes, scanlines, rapidly counting numerals, hairline grids, occasional single-frame flashes. No grey, no gradient, no curve.
- **Read:** Data converted directly to both image and sound by the same transformation — the "score" is a conversion rule.
- **Primitives:** 1-bit grids, barcode strips, monospace numerals, strobe frames, horizontal tear.
- **Seed:** A 1-bit renderer: everything is on or off. Barcode columns generated from a data stream; numerals counting at absurd rates; one frame in 120 fully inverted. Maximum discipline, maximum impact. (G10)

**48. Carsten Nicolai / Alva Noto — *telefunken* (2000), *milch* (2000)**
- **Look:** *telefunken*: audio signal fed directly into a video input, so the waveform *is* the image — horizontal bands, rolling sync, pure geometry. *milch*: sound vibrating liquids into standing patterns.
- **Read:** No mapping layer; a direct physical/electrical identity between sound and image.
- **Primitives:** raster lines driven by a waveform, sync-roll offset, cymatic ripple field.
- **Seed:** Simulate a CRT: each scanline's brightness comes from an audio buffer sample; deliberately mistime the sync so the image rolls and tears. (G10)

**49. Music Animation Machine (Stephen Malinowski, 1980s–) and the piano-roll lineage**
- **Look:** Coloured horizontal bars scrolling past a vertical playhead; colour = instrument or harmonic function; bars illuminate on strike.
- **Read:** The purest scrolling-field traversal; a direct descendant of player-piano rolls.
- **Primitives:** bars (start, duration, pitch, channel), scroll transform, strike highlight.
- **Seed:** Baseline utility sketch — build it once as a reusable component and reuse it under other grammars. (G3, G4)

**50. Punch cards, player-piano rolls, and music boxes**
- **Look:** Perforations in paper or metal; the physical hole is the note. Barrel-organ books fold in fanfold pleats.
- **Primitives:** hole matrix, feed mechanism, sprocket rhythm, pin-barrel cylinder.
- **Seed:** A rotating pin-barrel that plucks a comb, rendered as a 3D cylinder unwrapping into a 2D grid — showing the equivalence of cylinder and grid. (G3, G8)

---

### E. Drawn sound and optical synthesis (image *becomes* audio)

This family is the richest for code: the mapping is total, invertible, and physical.

**51. Arseny Avraamov, Evgeny Sholpo (Variophone), Boris Yankovsky — Soviet "ornamental sound" (1929–30s)**
- **Look:** Hand-drawn and cut geometric shapes photographed onto the optical soundtrack strip: sawtooth combs, ornamental friezes, cut-paper templates spun on discs.
- **Read:** The soundtrack area's transparency modulates light onto a photocell — waveform = drawn shape.
- **Primitives:** waveform-as-ornament, rotating template disc, film strip with sprockets.
- **Seed:** A drawn periodic shape spun on a disc; a slit reads its radius over angle; render the resulting waveform live beside the disc. (G10, G8)

**52. Rudolf Pfenninger — *Tönende Handschrift* (1932); Oskar Fischinger — *Sounding Ornaments* (1932)**
- **Look:** Libraries of hand-drawn waveform cards; Fischinger's ornamental strips presented as a claimed alphabet linking visual form and timbre.
- **Primitives:** card catalogue of periodic shapes, splice-assembled sequences.
- **Seed:** A visible "waveform card index": drawing a new card changes the timbre; splice cards into a sequence and hear/see it play. (G10)

**53. Norman McLaren — *Synchromy* (1971), *Dots*, *Loops*, *Neighbours* (1940s–70s)**
- **Look:** Striped patterns of varying width and spacing, photographed onto the optical track — and in *Synchromy*, the very same stripe-cards are shown *in the picture area*, so you literally watch the sound. Bright flat colour bands, rigidly rhythmic.
- **Read:** Stripe frequency = pitch; stripe height = amplitude; the image IS the audio.
- **Primitives:** vertical stripe cards, spacing→pitch mapping, colour bands, exact frame sync.
- **Seed:** Columns of stripe-cards whose spacing sets pitch, stacked as a chord; animate as a strict grid sequencer where every visual change is necessarily an audible one. Ideal audiovisual-unity demo. (G3, G10)

**54. Evgeny Murzin — ANS synthesiser (1938 designed, completed 1958)**
- **Look:** A photo-optic instrument: the composer scratches through black mastic on a moving glass plate; every scratch admits light to a bank of ~720 microtonal tracks. The "score" is a scratched black plate — a hand-made spectrogram.
- **Read:** x = time (plate travel), y = frequency (very fine), scratch width = amplitude. Used by Artemiev, Gubaidulina, Schnittke, Denisov; heard in *Solaris*.
- **Primitives:** black plate, scratched-through strokes revealing light, ultra-fine frequency lanes, plate transport.
- **Seed:** A scratch-off canvas: drag to remove black; a vertical read-line travels and lights up every scratch it meets. Direct, tactile, and historically exact in spirit. (G10, G5)

**55. Daphne Oram — Oramics (1959–)**
- **Look:** Ten parallel strips of 35mm film, drawn on by hand — separate strips for pitch, timbre (drawn waveform), vibrato, and amplitude envelope, all read simultaneously by photocells.
- **Read:** Parallel control-lanes; the composer draws each parameter independently. Essentially a hand-drawn modular synthesiser.
- **Primitives:** 10 stacked draw-lanes with distinct semantics, film transport, photocell read-head.
- **Seed:** A multi-lane drawing interface where lane 1 = pitch contour, lane 2 = waveform shape, lane 3 = vibrato depth, etc. Show all lanes scrolling under one read-head. Extremely faithful to the source *and* a genuinely good instrument UI. (G10, G12)

**56. Iannis Xenakis — UPIC (1977) and *Mycenae-Alpha* (1978)**
- **Look:** Freehand "arcs" drawn on a large digitising tablet; a page of UPIC drawing looks like wind-blown grass or geological strata — bundles of curving lines sweeping across a time/pitch field.
- **Read:** x = time, y = pitch, drawn arcs = glissando envelopes; drawings can be zoomed and time-scaled arbitrarily.
- **Primitives:** freehand arc bundles, time/pitch field, waveform and envelope defined by other drawings (drawings all the way down).
- **Seed:** Bundles of hand-like curves (use velocity-varying noise, not smooth splines) sweeping a field; allow the whole drawing's time-scale to stretch, so the same image is a gesture or an hour. (G2)

**57. Modern image-to-sound tools — MetaSynth, Photosounder, IanniX, HighC, Virtual ANS**
- **Look:** Software canvases where paint = spectrum. IanniX and HighC are direct UPIC descendants: IanniX shows curves, triggers, and cursors as coloured objects in 2D/3D space.
- **Read:** IanniX in particular formalises the model — *curve* (a path), *cursor* (a traversal), *trigger* (an event) — which is exactly the (field, traversal, mapping) triple.
- **Primitives:** curve objects, cursor objects with speed/direction, trigger points, message routing.
- **Seed:** Implement the IanniX object model as a mini graphic-sequencer: draw curves, attach cursors with different speeds, place triggers, and let the whole thing run as a visual polyrhythm engine. **This is the highest-leverage single build in the document.** (G2, G9, G12)

---

### F. Event scores, Fluxus, and conceptual notation

> **Generate original instruction texts for all of these.** The originals are short and heavily protected; their value here is the *form*, not the wording.

**58. La Monte Young — *Composition 1960* series, *The Well-Tuned Piano* charts**
- **Look:** A single typed line on an otherwise empty page. Elsewhere, tuning-ratio lattices: dense arrays of just-intonation ratios in geometric arrangement.
- **Read:** The instruction may specify an impossible, absurd, or extremely long action. Duration is the medium.
- **Primitives:** one line of type in vast whitespace; separately, a rational-number lattice (primes as axes).
- **Seed:** Two sketches. (a) A single generated instruction, held on screen so long it becomes uncomfortable. (b) A 3D just-intonation lattice (3-limit × 5-limit × 7-limit axes) where each node glows at its frequency ratio. (G11)

**59. George Brecht — *Water Yam* (1963), event cards**
- **Look:** A box of small printed cards, each bearing a title and a terse action. Typography is plain, almost clinical; the box is part of the work.
- **Read:** Draw a card. Perform, or merely consider, the action.
- **Primitives:** card deck, deal/flip mechanics, small centred type, box as container.
- **Seed:** A dealt deck of generated event cards with a physically convincing flip; leave long silences between deals. (G11)

**60. Yoko Ono — *Grapefruit* (1964)**
- **Look:** A small book of instruction pieces, many of them imaginary or impossible; grouped by element (music, painting, event, poetry, object).
- **Read:** Performed in the mind as legitimately as in the world.
- **Primitives:** short imperative text, generous margins, section taxonomy.
- **Seed:** Instructions that describe actions the *sketch itself* cannot perform, displayed over a canvas that visibly fails to comply. (G11)

**61. Mieko Shiomi — *Spatial Poem* (1965–75)**
- **Look:** Instructions mailed worldwide; responses plotted onto a world map with flags/pins. The score's realisation is a *map*.
- **Read:** Distributed, asynchronous, global.
- **Primitives:** world map, pins with timestamps, arrival animation.
- **Seed:** A map where events arrive at their local times, so a wave of responses circles the globe with the sun. (G7, G11)

**62. Alison Knowles — *The House of Dust* (1967), computer-generated poem**
- **Look:** Fanfold computer printout: quatrains combinatorially assembled from four lists (material, location, light source, inhabitants). One of the earliest computer-generated poems, later built as architecture.
- **Primitives:** slot-grammar generator, fanfold printout aesthetic, endless scroll.
- **Seed:** A combinatorial generator printing endlessly on virtual fanfold paper; occasionally, one stanza is rendered as a 3D dwelling. (G11)

**63. Milan Knížák — *Broken Music* (1963–79); Nam June Paik — *Random Access* (1963)**
- **Look:** Physical media as score: records cut apart and reglued into collaged discs; magnetic tape pasted over a wall to be read by a hand-held head.
- **Read:** The listener's hand chooses the path across the tape — free traversal over a physical field.
- **Primitives:** collaged disc sectors with visible seams, tape-strip wall, hand-held playhead.
- **Seed:** A wall of tape strips; a draggable read-head; audio/visual output depends entirely on the path and speed of the drag. (G9, G5)

**64. Takehisa Kosugi — *Anima 7*, *Micro 1* (1960s); Yasunao Tone — *Solo for Wounded CD* (1985)**
- **Look:** Kosugi: minimal texts prescribing extreme temporal stretching of a single action. Tone: the "score" is damage — a CD prepared with tape/pinholes so error-correction fails unpredictably.
- **Read:** Tone's is a *corrupted-data* traversal: the read-head skips, stutters, and reconstructs wrongly.
- **Primitives:** read-head jumps, error-concealment artefacts, block dropouts.
- **Seed:** A scanline reader over an image with deliberately corrupted error-correction: blocks repeat, misplace, and interpolate. Glitch as method, not decoration. (G10, G11)

---

### G. Creative-music, improvisation, and conduction systems

**65. Anthony Braxton — Falling River Music (2000s), Language Music types, diagrammatic titles**
- **Look:** Falling River Music scores are large multi-coloured freehand drawings — looping ribbons, blocks, hatch fields, in saturated primaries — where colour identifies material families. Braxton's *titles* are themselves schematic diagrams (numerals, geometric figures, arrows, letter-codes).
- **Read:** Materials are navigated, not executed; performers move among "logics" at will.
- **Primitives:** colour-coded gestural ribbons; a separate glyph-language for titles; navigational state.
- **Seed:** Two-layer sketch: a colour-coded drawing plus a visible "navigation state" HUD showing which logic is currently active and what transitions are legal. (G5, G6)

**66. Wadada Leo Smith — Ankhrasmation (1960s–)**
- **Look:** Colour-symbol scores: coloured rectangles, triangles, arrows, curved forms in red/blue/yellow/black with hand-drawn edges, arranged as a sequence of image-units. Silence is notated as a positive object, and units may specify "velocity" rather than metric duration.
- **Read:** Symbols are read as fields of possibility; the improviser's rhythmic identity is generated, not prescribed.
- **Primitives:** flat coloured geometric units with wobbly hand edges, explicit silence-blocks, velocity indicators.
- **Seed:** A left-to-right sequence of coloured units where duration is *not* proportional to width — the playhead deliberately moves at irregular speed, which is the point. (G6, G4)

**67. Butch Morris — Conduction (1985–2013)**
- **Look:** No page. A codified vocabulary of ~20 hand/baton gestures (memory, repeat, sustain, literal, graphic) directed at an ensemble in real time.
- **Read:** The score exists only as gesture, in the moment; performers hold "memories" that can be recalled.
- **Primitives:** gesture tokens, target selection (who), memory registers, recall events.
- **Seed:** Visualise the ensemble as nodes; a conductor cursor issues typed gestures to subsets; memory slots fill and are recalled with a visible retrieval animation. (G12)

**68. John Zorn — *Cobra* (1984) and the game pieces**
- **Look:** Cue cards and hand signals; the rulebook is the score, and it is not public in full. Downbeats, squads, guerrilla systems.
- **Read:** A game with a prompter, not a conductor. Structure emerges from rule enforcement.
- **Primitives:** rule engine, cue cards, player state, prompter arbitration.
- **Seed:** An agent-based simulation with a visible rule state machine; render card plays, contested moves, and the resulting group structure. (G11, G12)

**69. George Lewis — *Voyager* (1987–)**
- **Look:** No graphic score; an interactive "virtual improvising orchestra" that listens and responds. Its documentation is signal-flow and behaviour description.
- **Read:** The machine has its own agency — non-hierarchical, not accompaniment.
- **Primitives:** listening analysis lanes, response-generator state, mutual influence edges.
- **Seed:** Two agents (human-proxy and machine) whose visual outputs influence each other with lag; neither is master. Show the analysis window feeding the generator. (G12)

**70. Christian Marclay — *Graffiti Composition* (1996–2002), *Screen Play* (2005), *Manga Scroll* (2010), *Shuffle* (2007)**
- **Look:** *Graffiti Composition*: blank manuscript paper posted on city walls, photographed after the public marked it — accidental notation, weathering, tears, tags. *Screen Play*: black-and-white found footage overlaid with coloured lines, dots and shapes that performers read live. *Shuffle*: playing cards of photographed music-notation found in daily life.
- **Read:** *Screen Play* is the key one here — it is literally an animated graphic score, with the film's motion as the traversal.
- **Primitives:** overlay of pure vector graphics on moving imagery; weathered paper; card deck.
- **Seed:** Generative found-footage substitute (grainy monochrome procedural texture) with hard-edged coloured vector overlays whose behaviour is *independent* of the footage. The tension between the two layers is the piece. (G9, G1)

**71. AACM / Muhal Richard Abrams and the collective-notation tradition; Barry Guy's ensemble diagrams**
- **Look:** Hybrid pages: notated cells, boxes of free material, arrows indicating routes and cueing between sub-groups; large-ensemble traffic management.
- **Primitives:** cells, routing arrows, sub-group lanes, cue points.
- **Seed:** A traffic diagram for 12 agents: routes, merges, and hold-points, with density controlled by a conductor node. (G9, G12)

---

### H. Screen scores, animated notation, and real-time notation

The most directly relevant family — these works *are* animation.

**72. Cat Hope and the Decibel ScorePlayer (2010s–)**
- **Look:** Long horizontal bars and blocks of flat colour on black, representing sustained tones and noise bands; a vertical playhead line scrolls across, or the score scrolls under a fixed line. Extremely legible; designed for ensemble sync on tablets.
- **Read:** Networked playback keeps all performers on one playhead. Height = pitch, colour = instrument/technique, vertical thickness = bandwidth.
- **Primitives:** flat colour bars, hard vertical playhead, black ground, network sync.
- **Seed:** The reference implementation for "animated graphic score": a scrolling bar field with a fixed playhead, bars swelling as they cross. Add drift/noise so bars are not perfectly rectangular. (G4)

**73. Ryan Ross Smith — *Study* series / animatednotation.com (2011–)**
- **Look:** Geometric black-and-white systems: rotating radial arms sweeping past nodes, expanding circles reaching thresholds, dots travelling paths and colliding with fixed points. Performers play *when a moving thing meets a static thing*.
- **Read:** Coincidence-based. The notation contains no symbols at all — only geometry and timing.
- **Primitives:** rotating arms at differing angular rates, node rings, growing circles, collision detection.
- **Seed:** Multiple rotating arms at rationally-related speeds over rings of nodes; every arm/node coincidence fires. A polyrhythm engine that is *entirely* visual. Superb and easy to build. (G8)

**74. Lindsay Vickery — scrolling and generative screen scores (2000s–)**
- **Look:** Scrolling hybrid notation; some works generate their notation at runtime, so no two performances show the same page.
- **Primitives:** scroll transform, runtime notation generator, seed display.
- **Seed:** A score that composes itself just ahead of the playhead — show the "not yet written" region as blank and let the generator visibly fill it. (G9)

**75. Nick Didkovsky — *Zero Waste* (2001)**
- **Look:** A closed loop: notation is displayed, the pianist sight-reads it, the performance is transcribed, and the transcription becomes the next page. Error accumulates.
- **Primitives:** transcribe→display→perform cycle, generation counter, drift metric.
- **Seed:** Iterative degradation (relative of Lucier #36): a pattern re-read imperfectly each cycle, with generation number shown. (G12)

**76. Jason Freeman — *Glimmer* (2004) and real-time notation works**
- **Look:** Audience members holding light sticks become the score's input; notation is generated live on screens for the orchestra.
- **Primitives:** crowd input field, aggregation, live notation output.
- **Seed:** A crowd of noisy input agents whose aggregate statistics generate a clean notated line — visualise both the noisy source and the smooth output. (G12)

**77. Gerhard E. Winkler, Pedro Rebelo, Harris Wulfson (*LiveScore*), and the wider screen-score literature**
- **Look:** Real-time-generated staves and graphics reacting to performer input; feedback between what is played and what is next displayed.
- **Primitives:** analysis→generation loop, latency budget, display buffer.
- **Seed:** Show the latency explicitly — a visible gap between "played" and "written," which is the central ergonomic problem of the whole genre. (G12)

---

### I. Data, code, and generative scores

**78. Hans-Christoph Steiner — *Solitude* (2004), a Pure Data patch drawn as a score**
- **Look:** A functioning Pd patch whose objects and connections are arranged to form a picture — the program is legible both as software and as a drawing.
- **Primitives:** node-graph with orthogonal edges, patch-cord aesthetics, layout as image.
- **Seed:** A node graph that is arranged into a representational figure while remaining a valid dataflow network; animate signal packets along the cords. (G12)

**79. IanniX, Nodal, Ossia score, Iannix-descendant sequencers**
- **Look:** Coloured curves in 2D/3D with multiple cursors moving at independent speeds, firing triggers.
- **Seed:** See #57 — build this. Then reuse it as the runtime for other entries in this document. (G2, G12)

**80. Cellular automata, L-systems, and reaction–diffusion as scores**
- **Look:** Rule 110 / Rule 30 raster fields; Turing patterns (spots, labyrinths); L-system branching. Self-generating notation with no author per frame.
- **Read:** Read a row per time-step, or scan a column, or treat local density as amplitude.
- **Primitives:** CA lattice, Gray–Scott field, L-system turtle.
- **Seed:** Rule 110 generating a raster that a playhead reads as a rhythm; separately, a Gray–Scott field whose blob count drives density. (G3, G7)

**81. Sonification and data-driven scores (seismograms, ECG, weather, market, telemetry)**
- **Look:** Instrument traces: continuous inked lines on rolling drum paper, calibration marks, timestamps, hour-lines.
- **Read:** Physical time, mechanically inscribed. A seismogram drum is a spiral score.
- **Primitives:** rolling drum with helical trace, calibration ticks, event spikes.
- **Seed:** A seismograph drum rendered as a spiral; the pen responds to a signal; the drum slowly advances so the trace helixes downward. Beautiful and mechanically honest. (G8, G2)

**82. Brian Eno — generative systems (*Discreet Music* 1975, *77 Million Paintings*, *Bloom*)**
- **Look:** The published score of *Discreet Music* is a systems diagram — a signal path with two tape machines and a long delay. Later works: slowly recombining visual layers with no repeat.
- **Primitives:** loop lengths that never coincide, layer crossfades, long-period phase.
- **Seed:** N layers with mutually prime periods; the composite never repeats within any plausible viewing time. State the recurrence period on screen. (G12, G9)

**83. Steve Reich — *Pendulum Music* (1968) and phase processes**
- **Look:** *Pendulum Music*'s score is a setup description: microphones swung over speakers until feedback pulses settle into unison. Phase pieces can be drawn as two identical patterns sliding past each other.
- **Read:** The physics performs the piece; the ending is a state, not a moment.
- **Primitives:** damped pendulum simulation, feedback pulses, phase offset between two identical rings.
- **Seed:** Two identical rings of marks rotating at slightly different rates; render the moiré between them; run to unison and stop. (G8)

---

### J. Notation traditions and vernacular systems worth mining

Not "graphic scores" in the avant-garde sense, but rich, non-Western or non-standard visual logics that broaden the collection well beyond the European post-war canon.

**84. Chinese *jianzipu* (guqin tablature)**
- **Look:** Composite ideograms — each character is assembled from abbreviated components specifying string, fret position, and which finger does what. A dense column-set of invented compound glyphs.
- **Read:** Top-to-bottom, right-to-left. Specifies *action*, not pitch or rhythm.
- **Primitives:** compositional glyph system (radicals combined into a single mark), vertical columns.
- **Seed:** A procedural compound-glyph generator: 4 radical slots combining into one character; animate assembly and disassembly. (G6)

**85. Korean *jeongganbo* (15th c.)**
- **Look:** A vertical grid of square boxes; each box is one beat and may be subdivided; symbols sit inside cells. One of the earliest notations to represent duration by *area*.
- **Primitives:** vertical cell column, nested subdivision, area = time.
- **Seed:** A vertical playhead descending a column of cells that recursively subdivide as it approaches. (G3)

**86. Byzantine and Znamenny neumes; Western early neumes (*cheironomic*)**
- **Look:** Small curved and angular marks above text, originally recording the *shape of a conductor's hand movement* rather than absolute pitch. Znamenny "hooks and banners" are elegant, calligraphic, and semi-abstract.
- **Read:** Gestural memory aids, not exact prescriptions.
- **Primitives:** stroke-gesture glyphs above a text baseline, relative contour.
- **Seed:** Text with generated gestural marks above it; hovering "performs" the gesture as a moving contour. (G5, G6)

**87. Ethiopian *melekket***
- **Look:** Small characters above chant text acting as pointers to memorised melodic formulas — a compression scheme, each sign standing for a whole phrase.
- **Primitives:** pointer-glyphs referencing a phrase library.
- **Seed:** A compression/decompression animation: a short sign expands into a long contour, then re-collapses. (G6)

**88. Javanese *kepatihan*, Indian *sargam* and *kolam*-like cyclic diagrams, and cyclic time notations**
- **Look:** Cycles marked by structural punctuation (gong points), with time as a repeating ring rather than a line.
- **Primitives:** ring with unevenly weighted punctuation points, nested cycles of different lengths.
- **Seed:** Nested rings (16 / 8 / 4 / 2 beats) rotating together; the gong coincidence is the visual and structural event. (G8)

**89. Curwen/Kodály hand signs and Egyptian cheironomy reliefs**
- **Look:** The body as notation — fixed hand shapes at fixed heights for scale degrees.
- **Primitives:** pose library, vertical position = pitch.
- **Seed:** Abstracted hand-shapes rising and falling on a pitch axis, morphing between poses. (G6)

**90. Weaving drafts, knitting charts, and change-ringing methods**
- **Look:** Binary grids with threading/tie-up/treadling blocks (weaving) or permutation "blue lines" tracing a bell's path through every permutation (change ringing) — a genuinely beautiful braid diagram.
- **Read:** Change-ringing methods are permutation scores: no bell may move more than one place per row.
- **Primitives:** permutation braid, grid of binary lifts, path-highlighting.
- **Seed:** A change-ringing braid: 8 lines weaving through permutations with one highlighted path. Formally strict, visually gorgeous, and completely under-used. (G3)

---
## 3. Building the animations

### 3.1 Mapping table (space → sound → motion)

Use these as the default semantics. Deviating from them is fine, but deviate *deliberately* and consistently within a sketch.

| Visual property | Conventional meaning | Animation consequence |
|---|---|---|
| x position | time (onset) | playhead contact |
| x extent | duration | sustain / hold state |
| y position | pitch or register (usually log-frequency) | vertical placement, band membership |
| y extent | bandwidth / cluster width | blur, band thickness, noisiness |
| stroke weight | dynamic | opacity or glow on activation |
| fill density / hatching | textural density, tremolo rate | particle rate, flicker frequency |
| colour hue | timbre / instrument / material family | channel identity |
| colour saturation | intensity of technique | activation feedback |
| curvature | glissando rate | motion speed along path |
| angle | rate of change | shear, skew |
| proximity of marks | perceived grouping | attraction/repulsion forces |
| overlap of layers | simultaneity / interference | multiply blend, moiré, beating |
| whitespace | silence | *the most important element; protect it* |

### 3.2 Ten rendering primitives to build once and reuse

Build these as a small shared library; almost every sketch below is a composition of them.

1. `Playhead(mode)` — linear | scrolling | radial | free-agent; emits `onEnter(mark)` / `onExit(mark)`.
2. `Field(generator, seed)` — deterministic mark generation from a seed; must be re-runnable.
3. `MarkTypes` — rect, blob, polyline, glyph, dot-swarm, hatch-patch, comb, wedge, arc.
4. `TransparencyStack(layers)` — drift/rotate layers, compute and expose intersections.
5. `StaffAlongPath(path)` — lay N parallel lines and place marks by arc-length (needed for Crumb, Xenakis, UPIC).
6. `GlyphAtlas(families, params)` — procedural symbol generator with continuous parameters (Logothetis, jianzipu).
7. `NodeGraph(nodes, edges)` — orthogonal routing, packet animation, feedback loops (Tudor, Pd, conduction).
8. `SpectrumCanvas` — image ↔ spectrogram, log-frequency, both directions (ANS, Oramics, drawn sound).
9. `PaperTexture(substrate)` — grain, fibre, misregistration, xerox falloff, film jitter. **Do not skip this**; it is what separates these sketches from clip-art.
10. `Sonifier(events)` — even if silent, define the mapping; the visual logic improves when a real sound mapping exists behind it (WebAudio: one oscillator/noise-band pool, ADSR from mark geometry).

### 3.3 Fifteen sketch briefs

Each is buildable in a single file. Ordered roughly by ascending difficulty.

1. **Three-Band Graph** (Feldman #6) — 3 register lanes on faint graph paper, sparse boxes, strict cursor, resolved-pitch dots inside boxes. Near-silence as a design goal.
2. **Any-Orientation Field** (Brown #7) — black bars, 4 free agents, whole-canvas slow rotation, fading trails.
3. **Coincidence Engine** (R. R. Smith #73) — rotating arms at ratios 3:4:5:7 over node rings; flash on coincidence. Add a beat-history strip along the bottom.
4. **Scratch Plate** (ANS #54) — scratch-off black canvas, vertical read-line, 720 microtonal lanes, phosphor palette.
5. **Ten Lanes** (Oramics #55) — 10 stacked hand-drawable parameter lanes under one read-head; a real instrument, not a picture.
6. **Perpendicular Machine** (Cage *Variations* #12) — drifting transparencies, five parameter lines, mechanically dropped perpendiculars with numeric readouts.
7. **The Spine** (Cardew #16) — persistent horizontal line + scrolling generated abstract figures that deform, clip, and orbit it. Extreme scale contrast. No key, ever.
8. **Ruled Surface** (Xenakis #20) — 60 straight lines between two animated guide curves; toggle to a Brownian-walker mode.
9. **Three Panels** (Stockhausen *Studie II* #43) — log-frequency band rects, trapezoid envelopes, shared playhead, ruler-drawn aesthetic.
10. **Texture Engine** (Ligeti/Wehinger #21) — six emitter types (burst, comb, smear, wedge, swarm, blob) scrolling on white; colour = family.
11. **One-Bit** (Ikeda #47) — pure black/white, barcode columns from a data stream, counting numerals, one inverted frame per second. No grey allowed anywhere.
12. **Room Recursion** (Lucier #36) — any source image re-rendered through a fixed transfer function N times; show generation counter; watch detail collapse to resonance.
13. **Layer Tension** (Marclay *Screen Play* #70) — procedural grainy monochrome "footage" beneath hard-edged coloured vector overlays that ignore it.
14. **Change-Ringing Braid** (#90) — 8 lines through permutations, one highlighted, strict adjacency rule.
15. **IanniX Runtime** (#57/#79) — curves + independent cursors + triggers. Build last, then re-implement briefs 1, 3, 7 and 10 *inside* it to prove the model.

### 3.4 Rules of thumb for making these look right

- **Protect the silence.** Most of these scores are 70–90% empty. A dense canvas is the single most common failure mode. Target 15% ink coverage unless the entry says otherwise (Ikeda, Applebaum, Braxton are the exceptions).
- **Seed everything.** Every field generator takes a seed; display it; make it URL-shareable. Re-runnability is what makes a generative score a *score* rather than a picture.
- **Mark the traversal.** If the viewer can't tell how time is moving through the space, the sketch has failed as a score, however pretty it is.
- **One grammar per sketch, two at most.** Hybrids of four grammars look like an AI moodboard.
- **Give the marks physics or history, not just positions.** Ink bleeds, paper misregisters, film jitters, phosphor decays, hands overshoot. Perfect vectors read as template.
- **Irregular time is allowed.** Wadada Leo Smith's velocity units, Cage's indeterminacy, Applebaum's wandering pointer: a playhead that moves at a constant rate is a choice, not a law.
- **Respect reduced motion.** Offer a static/stepped mode; some of these strobe.
- **Do not add an explanatory legend** unless the entry has one (Logothetis, Oramics, Studie II do; Cardew, Brown, Applebaum emphatically do not). Withholding the key is part of the form.

### 3.5 A parameter schema for generated pieces

A single JSON shape that most sketches here can be described by — useful if the sketches are to be catalogued, seeded, or generated in batches.

```json
{
  "id": "spine-0042",
  "lineage": ["Cardew Treatise", "Brown December 1952"],
  "grammar": ["G1", "G5"],
  "seed": 42,
  "substrate": { "type": "ink-on-vellum", "grain": 0.18, "misregistration": 0.4 },
  "palette": { "ground": "#EDE7DA", "ink": "#141210", "accents": ["#8C1D18"] },
  "field": {
    "generator": "poisson-figures",
    "count": [12, 40],
    "coverage": 0.15,
    "scaleContrast": 8.0,
    "persistentElements": ["horizontal-spine"]
  },
  "traversal": {
    "mode": "scrolling",
    "rate": 0.06,
    "rateJitter": 0.2,
    "agents": 1,
    "loop": true
  },
  "mapping": {
    "x": "time", "y": "log-frequency",
    "strokeWeight": "dynamic",
    "hue": "material-family"
  },
  "audio": { "enabled": true, "voices": 8, "engine": "band-noise+sine" },
  "accessibility": { "reducedMotion": "stepped", "maxFlashHz": 3 }
}
```

---

## 4. Coverage map

Confirming the requested breadth, by domain:

| Domain | Entries |
|---|---|
| Acoustic / instrumental | 1, 6–32, 84–90 |
| Electro-acoustic / mixed / apparatus | 25, 33–42, 63 |
| Electronic studio & machine-legible | 43–50 |
| Drawn sound / optical synthesis | 51–57 |
| Conceptual, event, Fluxus | 3, 58–64 |
| Improvisation & conduction systems | 65–71 |
| Animated & screen scores | 72–77 |
| Generative, code, data | 44, 78–83 |
| Non-Western & vernacular systems | 84–90 |

By visual grammar: G1 (7, 14, 21, 29, 70), G2 (1, 10, 20, 31, 40, 56, 79, 81), G3 (6, 28, 43, 49, 50, 53, 85, 90), G4 (4, 6, 21, 43, 66, 72), G5 (17, 30, 54, 63, 65, 86), G6 (9, 18, 19, 30, 65, 66, 84, 87, 89), G7 (5, 15, 25, 61, 80), G8 (13, 22, 23, 26, 51, 73, 81, 83, 88), G9 (8, 11, 12, 23, 35, 45, 70, 74), G10 (46, 47, 48, 51–56, 64), G11 (2, 27, 58–62, 68), G12 (33, 35–39, 67, 69, 75–79, 82).

---

## 5. Appendix: further names worth researching before a second pass

Composers and practitioners in this lineage whose scores are worth investigating directly, listed for breadth rather than described here: Sylvano Bussotti's later theatre works; Franco Evangelisti; Bengt Hambraeus; Krzysztof Penderecki's *Threnody* band-notation; Witold Lutosławski's aleatoric boxes; Robert Moran (*Four Visions*, city-scale scores); Dieter Schnebel (*MO-NO*, *Glossolalie*); Cathy Berberian; Else Marie Pade; Delia Derbyshire; Beatriz Ferreyra; Ruth Anderson; Maryanne Amacher; Annea Lockwood; Sarah Hennies; Ashley Fure; Catherine Lamb; Ryoko Akama; Éliane Radigue; Yuji Takahashi; Toru Takemitsu (*Corona* graphic circles); Joji Yuasa; Halim El-Dabh; Ahmed Malek; Julius Eastman; Alvin Singleton; Tyshawn Sorey; Nicole Mitchell; Matana Roberts (*Coin Coin* graphic panels); Pamela Z; Laurie Spiegel (*Music Mouse* as instrument-score); Laurie Anderson; Tristan Perich (1-bit drawings); Golan Levin (*Yellowtail*, *Scribble*); Toshio Iwai (*Electroplankton*, TENORI-ON); Zach Lieberman; Memo Akten; Ryoichi Kurokawa; Herman Kolgen; Robert Henke (*Lumière*, laser scores).

Note for a second pass: this catalogue is still weighted toward Europe and North America, 1950–1980. The highest-value additions would be more from Latin America (e.g. the Centro Latinoamericano de Altos Estudios Musicales circle in Buenos Aires), Africa, the Arab world, and South and Southeast Asia — and more from living practitioners under 40 working in screen and networked notation.

---

## 6. Reminder on use

Everything above is a description of a *grammar*, written to be recomposed. Generate original fields, original glyphs, original instruction texts, and original palettes. Do not reproduce, trace, or closely imitate any specific published page, and credit the lineage in a caption where a sketch is clearly indebted to one work — that attribution is both correct and interesting to the viewer.
