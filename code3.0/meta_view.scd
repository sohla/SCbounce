// meta_view.scd — SC-side viewer for beats + audio-meta + tick_index.
//
// Standalone, viewer-only. Uses the same transport pattern as
// sync_plan2_pattern_score_gui.scd (DiskIn playback + click-to-seek + AppClock
// cursor tick) but does NOT fire the score synth (bass / chord / click) on
// beats. Purely for scrubbing + inspecting the data streams.
//
// Panels are declarative: each panel is a closure `~panelXxx.(parent, x, y, w, h)`
// returning `(view: v, refresh: { |t| ... })`. The layout at the top of the
// AppClock block is a plain array — add / remove / reorder by editing lines.
// The `~guiTick` routine walks the array and calls each panel's `refresh(t)`
// on every 30 Hz update.
//
// Shipped panels: info | waveform | sections | bands-MultiSlider.
// Stub closures for scalar lanes / bands heatmap / bands-per-lane sit below
// the shipped set — uncomment the corresponding row in the panels list to
// enable them (bodies are placeholders until the design settles).
//
// Overrides before running:
//   ~beatsPath, ~musicPath, ~scorePath, ~metaPath, ~scoreAnchorBeat, ~bufFrames.

(
var root, libDir;

root   = thisProcess.nowExecutingPath.dirname.dirname;
libDir = root +/+ "sc" +/+ "lib";

~scoreTicksPerBeat  = 480;
~scoreEventsPerBeat = 4;
~scoreBeatsPerBar   = 2;
~scoreAnchorBeat    = ~scoreAnchorBeat ? 0;

thisProcess.interpreter.executeFile(libDir +/+ "beatfile.scd");
thisProcess.interpreter.executeFile(libDir +/+ "scorefile.scd");
thisProcess.interpreter.executeFile(libDir +/+ "metafile.scd");

~beatsPath = ~beatsPath ? (root +/+ "out"  +/+ "Beethoven4_4_MSO_beats_2_mul4.txt");
~musicPath = ~musicPath ? (root +/+ "data" +/+ "audio" +/+ "Beethoven4_4_MSO.wav");
~scorePath = ~scorePath ? (root +/+ "data" +/+ "score" +/+ "tick_index_2.json");
~metaPath  = ~metaPath  ? (root +/+ "out"  +/+ "Beethoven4_4_MSO_beats_2_mul4_meta.json");
~bufFrames = ~bufFrames ? 65536;
~addr      = NetAddr("127.0.0.1", NetAddr.langPort);

~loadBeats.(~beatsPath);
~loadScore.(~scorePath);
~loadMeta.(~metaPath);

// --- audio file metadata (no server needed) --------------------------------
block {
    var sf = SoundFile.new;
    if (sf.openRead(~musicPath.standardizePath).not) {
        Error("could not open audio: %".format(~musicPath)).throw;
    };
    ~fileSR     = sf.sampleRate;
    ~fileChans  = sf.numChannels;
    ~fileFrames = sf.numFrames;
    ~fileDur    = ~fileFrames / ~fileSR;
    sf.close;
    "audio file: % Hz, % channels, % s"
        .format(~fileSR, ~fileChans, ~fileDur.round(0.01)).postln;
};

if (s.serverRunning and: { s.sampleRate.notNil and: { s.sampleRate != ~fileSR.asFloat } }) {
    Error(("server is running at % Hz but file needs % Hz. " ++
        "Reboot the server (cmd+B or s.reboot) and re-run this file.")
        .format(s.sampleRate, ~fileSR)).throw;
};
s.options.sampleRate = ~fileSR;

// --- cursor time estimate --------------------------------------------------
~playStartT = nil;
~frozenT    = ~frozenT ? 0;
~nowT       = {
    if (~playStartT.notNil)
        { (SystemClock.seconds - ~playStartT).clip(0, ~fileDur) }
        { ~frozenT }
};

// --- section timeline precompute ------------------------------------------
// Walk ~scoreEntries, map tick → true-beat → beats-file row → audio time
// (same math as ~buildCurves in sync_plan2_pattern_score_gui.scd). Group
// consecutive entries by section_id into contiguous colour bands.
~buildSections = {
    var sections = List.new;
    var current  = nil;
    var currStart = 0;
    ~scoreEntries.do { |e|
        var tick     = e["tick"].asInteger;
        var trueBeat = (tick / ~scoreTicksPerBeat) - ~scoreAnchorBeat;
        var beatIdx  = (trueBeat * ~scoreEventsPerBeat).round.asInteger;
        var sec      = (e["section_id"] ? "").asString;
        var t;
        if ((beatIdx >= 0) and: { beatIdx < ~beats.size }) {
            t = ~beats[beatIdx];
            if (current.isNil) {
                current   = sec;
                currStart = t;
            } {
                if (sec != current) {
                    sections.add((id: current, t_start: currStart, t_end: t));
                    current   = sec;
                    currStart = t;
                };
            };
        };
    };
    if (current.notNil) {
        sections.add((id: current, t_start: currStart, t_end: ~fileDur));
    };
    ~sections = sections.asArray;
    "built % section segments".format(~sections.size).postln;
    ~sections.size;
};
~buildSections.value;

// --- mono peak envelope for the waveform panel ---------------------------
// SoundFileView renders one lane per channel with no "collapse to mono"
// flag, so a stereo file draws as two stacked lanes. We compute a single
// max-abs envelope ourselves and draw it in a UserView instead. Cached
// per audio path so re-running the file doesn't recompute.
~waveformPeaksCache = ~waveformPeaksCache ? ();
~computeMonoPeaks = { |path, nPeaks|
    var sf = SoundFile.new;
    var totalFrames, chan, peaks, samplesPerPeak;
    var chunkFrames = 262144;
    var chunkBuf, chunkStart = 0;

    if (sf.openRead(path.standardizePath).not) {
        Error("could not open %".format(path)).throw;
    };
    totalFrames    = sf.numFrames;
    chan           = sf.numChannels;
    peaks          = FloatArray.newClear(nPeaks);
    samplesPerPeak = (totalFrames / nPeaks).ceil.asInteger.max(1);
    chunkBuf       = FloatArray.newClear(chunkFrames * chan);

    "computing waveform peaks (% frames, % ch, % peaks)…"
        .format(totalFrames, chan, nPeaks).postln;

    while { chunkStart < totalFrames } {
        var thisFrames  = (totalFrames - chunkStart).min(chunkFrames);
        var thisSamples = thisFrames * chan;
        var chunkPeakLo, chunkPeakHi;
        sf.seek(chunkStart, 0);
        sf.readData(chunkBuf);
        chunkPeakLo = (chunkStart / samplesPerPeak).floor.asInteger;
        chunkPeakHi = ((chunkStart + thisFrames - 1) / samplesPerPeak).floor.asInteger
            .min(nPeaks - 1);
        (chunkPeakLo .. chunkPeakHi).do { |k|
            var loFrame  = (k * samplesPerPeak).max(chunkStart);
            var hiFrame  = (((k + 1) * samplesPerPeak) - 1).min(chunkStart + thisFrames - 1);
            var loLocal  = (loFrame - chunkStart) * chan;
            var hiLocal  = ((hiFrame - chunkStart + 1) * chan) - 1;
            var chunkMax = chunkBuf.copyRange(loLocal, hiLocal).abs.maxItem;
            if (chunkMax > peaks[k]) { peaks[k] = chunkMax };
        };
        chunkStart = chunkStart + thisFrames;
    };
    sf.close;
    "waveform peaks done.".postln;
    peaks
};
~waveformPeaks = ~waveformPeaksCache[~musicPath.asSymbol] ?? {
    var p = ~computeMonoPeaks.(~musicPath, 2000);
    ~waveformPeaksCache[~musicPath.asSymbol] = p;
    p
};

// Deterministic per-id colour from a small hand-picked palette. Safer than
// Color.hsv (varies by SC version) and easier to read than random hues.
~sectionColor = { |id|
    var palette = [
        Color(0.85, 0.45, 0.35),   // warm red
        Color(0.55, 0.75, 0.35),   // olive
        Color(0.35, 0.65, 0.85),   // sky
        Color(0.85, 0.75, 0.35),   // amber
        Color(0.65, 0.45, 0.85),   // violet
        Color(0.85, 0.55, 0.75),   // pink
        Color(0.40, 0.75, 0.65),   // teal
        Color(0.75, 0.65, 0.50)    // sand
    ];
    palette[id.asString.hash.abs % palette.size];
};

// --- panel closures --------------------------------------------------------
// Each returns an Event: (view: <SC widget>, refresh: { |t| ... }).
// The `~guiTick` loop walks the panels list and calls `refresh(t)`.

~panelInfo = { |parent, x, y, w, h|
    var v = StaticText(parent, Rect(x, y, w, h))
        .stringColor_(Color.gray(0.85))
        .string_(
            "audio % Hz, % s | beats % | meta % (% bands, window=% s) | anchor=%"
            .format(~fileSR, ~fileDur.round(0.01),
                ~beats.size, ~metaEntries.size,
                ~metaBandEdges.size - 1, ~metaWindowSeconds,
                ~scoreAnchorBeat)
        );
    (view: v, refresh: { |t| })
};

~panelWaveform = { |parent, x, y, w, h|
    var v = UserView(parent, Rect(x, y, w, h))
        .background_(Color.gray(0.12))
        .drawFunc_({ |vv|
            var vw = vv.bounds.width;
            var vh = vv.bounds.height;
            var mid = vh / 2;
            var peaks = ~waveformPeaks;
            var n, dur;
            if (peaks.notNil and: { peaks.size > 0 }) {
                n = peaks.size;
                Pen.color = Color(0.6, 0.85, 1.0);
                Pen.width = 1;
                peaks.do { |p, i|
                    var xp = (i / n) * vw;
                    Pen.moveTo(xp @ (mid - (p * mid * 0.95)));
                    Pen.lineTo(xp @ (mid + (p * mid * 0.95)));
                };
                Pen.stroke;
            };
            // playhead cursor
            dur = ~fileDur;
            if (dur > 0) {
                Pen.color = Color(1.0, 0.4, 0.4, 0.9);
                Pen.width = 1.0;
                Pen.line(((~nowT.value / dur * vw) @ 0),
                         ((~nowT.value / dur * vw) @ vh));
                Pen.stroke;
            };
        });
    v.mouseDownAction_({ |vv, xarg, yarg, mod|
        var t = (xarg / vv.bounds.width * ~fileDur).clip(0, ~fileDur);
        ~seek.(t);
    });
    (
        view:    v,
        refresh: { |t| v.refresh }
    )
};

~panelSections = { |parent, x, y, w, h|
    var v = UserView(parent, Rect(x, y, w, h))
        .background_(Color.gray(0.14))
        .drawFunc_({ |vv|
            var vw = vv.bounds.width;
            var vh = vv.bounds.height;
            var dur = ~fileDur;
            var t;
            if ((dur > 0) and: { ~sections.notNil } and: { ~sections.size > 0 }) {
                Pen.width = 1;
                ~sections.do { |seg|
                    var x0 = seg[\t_start] / dur * vw;
                    var x1 = seg[\t_end]   / dur * vw;
                    Pen.color = ~sectionColor.(seg[\id]);
                    Pen.fillRect(Rect(x0, 0, (x1 - x0).max(1), vh));
                    Pen.color = Color.white.alpha_(0.85);
                    Pen.stringAtPoint(seg[\id].asString, ((x0 + 4) @ 4));
                };
                // playhead cursor
                t = ~nowT.value;
                Pen.color = Color(1.0, 0.4, 0.4, 0.9);
                Pen.width = 1.0;
                Pen.line(((t / dur * vw) @ 0), ((t / dur * vw) @ vh));
                Pen.stroke;
            };
        });
    v.mouseDownAction_({ |vv, x, y, mod|
        var t = (x / vv.bounds.width * ~fileDur).clip(0, ~fileDur);
        ~seek.(t);
    });
    (view: v, refresh: { |t| v.refresh })
};

~panelBandsMSlider = { |parent, x, y, w, h|
    var nBands = ~metaBandEdges.size - 1;
    var v = MultiSliderView(parent, Rect(x, y, w, h))
        .background_(Color.gray(0.12))
        .fillColor_(Color(0.5, 0.9, 0.5, 0.85))
        .strokeColor_(Color.gray(0.35))
        .elasticMode_(1)
        .drawLines_(false)     // no connecting line between adjacent slider tops
        .drawRects_(true)      // discrete bars per band
		.editable_(false)
        .isFilled_(true)
		.indexThumbSize_(160.0)
		.gap_(-1)
        .size_(nBands)
        .value_(Array.fill(nBands, 0));
    (
        view: v,
        refresh: { |t|
            var idx = ~beats.indexOfGreaterThan(t) ? ~beats.size;
            var m = ~metaAtBeat.((idx - 1).max(0));
            v.value = m["bands"].collect(_.asFloat);
        }
    )
};

// --- shared panel helpers -------------------------------------------------

// Click-to-seek action installed on every time-axis panel.
~seekOnClick = { |vv, xarg|
    ~seek.((xarg / vv.bounds.width * ~fileDur).clip(0, ~fileDur));
};

// Red vertical playhead line spanning the full height of the view.
~drawTimeCursor = { |vv|
    var dur = ~fileDur, vw = vv.bounds.width, vh = vv.bounds.height;
    if (dur > 0) {
        Pen.color = Color(1.0, 0.4, 0.4, 0.9);
        Pen.width = 1;
        Pen.line(((~nowT.value / dur * vw) @ 0), ((~nowT.value / dur * vw) @ vh));
        Pen.stroke;
    };
};

// Downsample a vector-per-entry field into a (vecSize × nCols) matrix by
// mean-per-column. Called once at panel creation and cached in the closure;
// the drawFunc reads the pre-built matrix each refresh.
~buildHeatMatrix = { |field, nCols|
    var n = ~metaEntries.size;
    var vecSize = ~metaEntries[0][field].size;
    var heat   = Array.fill(vecSize, { FloatArray.newClear(nCols) });
    var counts = FloatArray.newClear(nCols);
    ~metaEntries.do { |e, i|
        var col = ((i / n) * nCols).floor.asInteger.min(nCols - 1);
        e[field].do { |val, j|
            heat[j][col] = heat[j][col] + val.asFloat;
        };
        counts[col] = counts[col] + 1;
    };
    vecSize.do { |j|
        nCols.do { |c|
            if (counts[c] > 0) { heat[j][c] = heat[j][c] / counts[c] };
        };
    };
    heat
};

// Render a pre-built (vecSize × nCols) heatmap into a UserView, one lane
// per vec-index (top-down). `fillColorFor.(v)` picks the cell colour.
~drawHeatmap = { |vv, heat, fillColorFor|
    var vw = vv.bounds.width, vh = vv.bounds.height;
    var nLanes = heat.size;
    var nCols  = heat[0].size;
    var laneH = vh / nLanes;
    var colW  = (vw / nCols).max(1);
    nLanes.do { |j|
        var y0 = j * laneH;
        nCols.do { |c|
            var val = heat[j][c];
            if (val > 0.02) {
                Pen.color = fillColorFor.value(val);
                Pen.fillRect(Rect(c * colW, y0, colW, laneH));
            };
        };
    };
};

// Draw a stack of line-plot lanes into a UserView. `series` is an Array of
// Array-of-Float (one lane per outer index), `colors` matches, `labels`
// optional.
~drawLanes = { |vv, series, colors, labels|
    var vw = vv.bounds.width, vh = vv.bounds.height;
    var nLanes = series.size;
    var laneH = vh / nLanes;
    var n = series[0].size;
    if (n >= 2) {
        Pen.width = 1;
        nLanes.do { |lane|
            var y0 = lane * laneH;
            Pen.color = Color.gray(0.22);
            Pen.line((0 @ (y0 + laneH)), (vw @ (y0 + laneH)));
            Pen.stroke;
            if (labels.notNil) {
                Pen.color = colors[lane];
                Pen.stringAtPoint(labels[lane].asString, (6 @ (y0 + 3)));
            };
            Pen.color = colors[lane];
            Pen.width = 1.2;
            series[lane].do { |v, i|
                var xp = (i / n) * vw;
                var yp = y0 + laneH - (v * (laneH - 4)) - 2;
                if (i == 0) { Pen.moveTo(xp @ yp) } { Pen.lineTo(xp @ yp) };
            };
            Pen.stroke;
            Pen.width = 1;
        };
    };
};

// Colour ramps for the three heatmap panels. Each takes a normalised value
// in [0,1] and returns a Color; the three ramps keep the panels visually
// distinct at a glance (bands=orange, chroma=green, mfcc=blue).
~rampBands  = { |v| Color(v, v * 0.75, v * 0.30, 0.9) };
~rampChroma = { |v| Color(v * 0.30, v, v * 0.55, 0.9) };
~rampMfcc   = { |v| Color(v * 0.40, v * 0.65, v, 0.9) };

// --- graph panels (were stubs; filled in) --------------------------------

~panelScalarLanes = { |parent, x, y, w, h|
    var fields    = ["loudness", "density", "flatness"];
    var laneColor = [
        Color(0.80, 0.55, 0.95),   // loudness — violet
        Color(0.50, 0.90, 0.60),   // density — green
        Color(0.95, 0.65, 0.35)    // flatness — amber
    ];
    var series = fields.collect { |name|
        ~metaEntries.collect({ |e| e[name].asFloat })
    };
    var v = UserView(parent, Rect(x, y, w, h))
        .background_(Color.gray(0.10))
        .drawFunc_({ |vv|
            ~drawLanes.(vv, series, laneColor, fields);
            ~drawTimeCursor.(vv);
        });
    v.mouseDownAction_({ |vv, xarg| ~seekOnClick.(vv, xarg) });
    (view: v, refresh: { |t| v.refresh })
};

~panelBandsHeatmap = { |parent, x, y, w, h|
    var heat = ~buildHeatMatrix.("bands", 800);
    var v = UserView(parent, Rect(x, y, w, h))
        .background_(Color.gray(0.05))
        .drawFunc_({ |vv|
            var vw = vv.bounds.width, vh = vv.bounds.height;
            var laneH = vh / heat.size;
            ~drawHeatmap.(vv, heat, ~rampBands);
            // Hz-range labels down the left edge
            Pen.color = Color.white.alpha_(0.7);
            heat.size.do { |j|
                Pen.stringAtPoint(
                    ~metaBandEdges[j].asInteger.asString ++ "–" ++
                        ~metaBandEdges[j + 1].asInteger.asString,
                    (4 @ ((j * laneH) + 2)));
            };
            ~drawTimeCursor.(vv);
        });
    v.mouseDownAction_({ |vv, xarg| ~seekOnClick.(vv, xarg) });
    (view: v, refresh: { |t| v.refresh })
};

~panelBandsLanes = { |parent, x, y, w, h|
    var nBands = ~metaBandEdges.size - 1;
    var series = nBands.collect { |j|
        ~metaEntries.collect({ |e| e["bands"][j].asFloat })
    };
    var colors = Array.fill(nBands, { |j|
        // fade through orange→blue across bands so lane identity is legible
        Color(1 - (j / nBands * 0.6), 0.55, 0.3 + (j / nBands * 0.6))
    });
    var labels = nBands.collect { |j|
        ~metaBandEdges[j].asInteger.asString ++ "–" ++ ~metaBandEdges[j + 1].asInteger.asString
    };
    var v = UserView(parent, Rect(x, y, w, h))
        .background_(Color.gray(0.10))
        .drawFunc_({ |vv|
            ~drawLanes.(vv, series, colors, labels);
            ~drawTimeCursor.(vv);
        });
    v.mouseDownAction_({ |vv, xarg| ~seekOnClick.(vv, xarg) });
    (view: v, refresh: { |t| v.refresh })
};

// --- MFCC + chroma panels (new) ------------------------------------------

// Small helper: draw N labelled bars filling the view, given a values-per-
// beat closure that returns the current N values. Used by the two "current
// beat MSlider" panels below (chroma / mfcc). Written as a UserView (not
// MultiSliderView) so we can label each bar with its pitch class or coeff
// index.
~panelBarsAtBeat = { |parent, x, y, w, h, valuesFn, labels, ramp|
    var v = UserView(parent, Rect(x, y, w, h))
        .background_(Color.gray(0.12))
        .drawFunc_({ |vv|
            var vw = vv.bounds.width, vh = vv.bounds.height;
            var vals = valuesFn.value;
            var n = vals.size;
            var slotW = vw / n;
            var barMaxH = vh - 20;   // reserve 20 px for labels below
            vals.do { |val, i|
                var v2 = val.asFloat;
                var barH = v2 * barMaxH;
                Pen.color = ramp.value(v2);
                Pen.fillRect(Rect(i * slotW + 2, vh - 20 - barH, slotW - 4, barH));
                Pen.color = Color.gray(0.35);
                Pen.strokeRect(Rect(i * slotW + 2, vh - 20 - barMaxH, slotW - 4, barMaxH));
                // label
                Pen.color = Color.white.alpha_(0.85);
                Pen.stringAtPoint(labels[i].asString, ((i * slotW) + (slotW / 2) - 8) @ (vh - 16));
            };
        });
    (view: v, refresh: { |t| v.refresh })
};

~panelChromaMSlider = { |parent, x, y, w, h|
    ~panelBarsAtBeat.(parent, x, y, w, h,
        {
            var idx = ~beats.indexOfGreaterThan(~nowT.value) ? ~beats.size;
            var m = ~metaAtBeat.((idx - 1).max(0));
            m["chroma"].collect(_.asFloat)
        },
        ~metaChromaLabels,
        ~rampChroma
    )
};

~panelMfccMSlider = { |parent, x, y, w, h|
    var labels = Array.fill(~metaMfccN, { |i| i.asString });
    ~panelBarsAtBeat.(parent, x, y, w, h,
        {
            var idx = ~beats.indexOfGreaterThan(~nowT.value) ? ~beats.size;
            var m = ~metaAtBeat.((idx - 1).max(0));
            m["mfcc"].collect(_.asFloat)
        },
        labels,
        ~rampMfcc
    )
};

~panelChromaHeatmap = { |parent, x, y, w, h|
    var heat = ~buildHeatMatrix.("chroma", 800);
    var v = UserView(parent, Rect(x, y, w, h))
        .background_(Color.gray(0.05))
        .drawFunc_({ |vv|
            var vw = vv.bounds.width, vh = vv.bounds.height;
            var laneH = vh / heat.size;
            ~drawHeatmap.(vv, heat, ~rampChroma);
            Pen.color = Color.white.alpha_(0.7);
            heat.size.do { |j|
                Pen.stringAtPoint(~metaChromaLabels[j].asString,
                    (4 @ ((j * laneH) + 2)));
            };
            ~drawTimeCursor.(vv);
        });
    v.mouseDownAction_({ |vv, xarg| ~seekOnClick.(vv, xarg) });
    (view: v, refresh: { |t| v.refresh })
};

~panelMfccHeatmap = { |parent, x, y, w, h|
    var heat = ~buildHeatMatrix.("mfcc", 800);
    var v = UserView(parent, Rect(x, y, w, h))
        .background_(Color.gray(0.05))
        .drawFunc_({ |vv|
            var vw = vv.bounds.width, vh = vv.bounds.height;
            var laneH = vh / heat.size;
            ~drawHeatmap.(vv, heat, ~rampMfcc);
            Pen.color = Color.white.alpha_(0.7);
            heat.size.do { |j|
                Pen.stringAtPoint("m" ++ j.asString, (4 @ ((j * laneH) + 2)));
            };
            ~drawTimeCursor.(vv);
        });
    v.mouseDownAction_({ |vv, xarg| ~seekOnClick.(vv, xarg) });
    (view: v, refresh: { |t| v.refresh })
};

// --- server + audio buffer + transport (no score-synth firing) ------------
s.waitForBoot({
    if (s.sampleRate != ~fileSR.asFloat) {
        "WARNING: server SR (%) doesn't match file SR (%). Audio device may have refused the requested rate."
            .format(s.sampleRate, ~fileSR).warn;
    };

    SynthDef(\playStream, { |out = 0, buf, amp = 0.3|
        var sig = DiskIn.ar(2, buf, loop: 0);
        Out.ar(out, sig * amp);
    }).add;

    s.sync;

    ~stream = Buffer.cueSoundFile(s, ~musicPath.standardizePath, 0, ~fileChans, ~bufFrames);
    s.sync;

    ~playSynth = nil;
    ~routine   = nil;

    // Scheduling routine: walks ~beats and emits /beat OSC. No synths, no
    // score lookups — the routine's only job here is keeping downstream
    // OSC subscribers (any concurrent beat_consumer.scd, etc.) in phase.
    ~scheduleFromIndex = { |startIdx, anchorTime|
        ~routine.stop;
        ~routine = Routine({
            (startIdx .. ~beats.size - 1).do { |i|
                (anchorTime + ~beats[i] - SystemClock.seconds).max(0).wait;
                ~addr.sendMsg("/beat", i);
            };
        }).play(SystemClock);
    };

    ~go   = { ~seek.(0) };
    ~seek = { |seekT|
        Routine({
            var idx = ~beats.indexOfGreaterThan(seekT) ? ~beats.size;
            var startFrame = (seekT * ~fileSR).round.asInteger;

            ~routine.stop;
            ~playSynth.free;
            if (~stream.notNil) { ~stream.close; ~stream.free; };

            ~stream = Buffer.cueSoundFile(s, ~musicPath.standardizePath,
                startFrame, ~fileChans, ~bufFrames);
            s.sync;

            ~playStartT = SystemClock.seconds - seekT;
            s.bind { ~playSynth = Synth(\playStream, [\buf, ~stream]) };
            ~scheduleFromIndex.(idx, ~playStartT);
            "seek % -> frame %, beat %".format(seekT.round(0.01), startFrame, idx).postln;
        }).play(AppClock);
    };
    ~beek = { |beat| ~seek.(~beats[beat.clip(0, ~beats.size - 1)]) };
    ~stop = {
        ~frozenT    = ~nowT.value;
        ~routine.stop;
        ~playSynth.free;
        ~playSynth  = nil;
        ~playStartT = nil;
    };

    OSCdef(\mvGo,    { ~go.value },                       '/sc/go');
    OSCdef(\mvSeek,  { |msg| ~seek.(msg[1].asFloat) },    '/sc/seek');
    OSCdef(\mvBeek,  { |msg| ~beek.(msg[1].asInteger) },  '/sc/beek');
    OSCdef(\mvPause, { ~stop.value },                     '/sc/pause');

    // --- GUI. Edit the `panels` list to add / remove / reorder panels. -----
    AppClock.sched(0, {
        var winMargin = 8, gap = 6, winW = 1200;
        var panels = [
            (name: \info,       make: ~panelInfo,           h: 22),
            (name: \wave,       make: ~panelWaveform,       h: 200),
            (name: \sections,   make: ~panelSections,       h: 40),
            (name: \scalars,    make: ~panelScalarLanes,    h: 90),
            (name: \bandsMS,    make: ~panelBandsMSlider,   h: 140),
            (name: \bandsHeat,  make: ~panelBandsHeatmap,   h: 140),
            (name: \chromaMS,   make: ~panelChromaMSlider,  h: 90),
            (name: \chromaHeat, make: ~panelChromaHeatmap,  h: 140),
            (name: \mfccMS,     make: ~panelMfccMSlider,    h: 90),
            (name: \mfccHeat,   make: ~panelMfccHeatmap,    h: 140)
            // (name: \bandsLanes, make: ~panelBandsLanes,     h: 260)   // eight per-band line lanes; verbose
        ];
        var totalH, win, y;

        totalH = (winMargin * 2) + ((panels.size - 1) * gap) + panels.sum({ |p| p[\h] });

        if (~guiWin.notNil and: { ~guiWin.isClosed.not }) { ~guiWin.close };
        if (~guiTick.notNil) { ~guiTick.stop };

        win = Window("meta_view — % | % | %"
                .format(~musicPath.basename, ~scorePath.basename, ~metaPath.basename),
                Rect(60, 60, winW, totalH))
            .background_(Color.gray(0.08));
        ~guiWin = win;

        y = winMargin;
        ~guiPanels = panels.collect { |p|
            var pw = winW - (winMargin * 2);
            var panel = p[\make].value(win, winMargin, y, pw, p[\h]);
            y = y + p[\h] + gap;
            panel
        };

        ~guiTick = Routine({
            while { win.isClosed.not } {
                var t = ~nowT.value;
                ~guiPanels.do { |p| p[\refresh].value(t) };
                (1/30).wait;
            };
        }).play(AppClock);

        win.onClose_({
            if (~guiTick.notNil) { ~guiTick.stop; ~guiTick = nil };
        });
        win.front;
        nil;
    });

    ("meta_view ready. % beats, % score entries, % meta entries, % bands, anchor=%."
        .format(~beats.size, ~scoreEntries.size, ~metaEntries.size,
                ~metaBandEdges.size - 1, ~scoreAnchorBeat)).postln;
    "evaluate ~go.value to start playback (viewer-only — no score synth fires).".postln;
    "click on waveform or sections lane to seek. change ~scoreAnchorBeat then ~buildSections.value to re-align section bands.".postln;
});
)

// ===========================================================================
// REPL playground — evaluate individually after the boot line prints.
// ---------------------------------------------------------------------------

~go.value;
~seek.(60);
~beek.(3020);
~stop.value;

// After tuning ~scoreAnchorBeat, rebuild sections so the coloured bands
// re-align. The section panel repaints on the next ~guiTick.
~scoreAnchorBeat = 3; ~buildSections.value;

// ===========================================================================
// Cleanup — evaluate to fully tear down state between runs. Safe to re-run
// (nil-guarded). Frees server-side buffer + playStream, stops the beat
// routine, tears down the GUI + OSCdefs.
// ---------------------------------------------------------------------------
(
"cleanup: meta_view".postln;

if (~routine.notNil)   { ~routine.stop;   ~routine   = nil };
if (~playSynth.notNil) { ~playSynth.free; ~playSynth = nil };
if (~stream.notNil)    { ~stream.close;   ~stream.free; ~stream = nil };
if (~guiTick.notNil)   { ~guiTick.stop;   ~guiTick   = nil };
if (~guiWin.notNil)    { if (~guiWin.isClosed.not) { ~guiWin.close }; ~guiWin = nil };

OSCdef(\mvGo).free;
OSCdef(\mvSeek).free;
OSCdef(\mvBeek).free;
OSCdef(\mvPause).free;
OSCdef(\scoreReload).free;
OSCdef(\metaReload).free;

~playStartT = nil;
"done.".postln;
)
