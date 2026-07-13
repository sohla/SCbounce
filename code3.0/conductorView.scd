// AirKit conductor — view side.
//
// Owns: waveform view, playhead, transport buttons. Reads ~fileDur /
// ~playStartT / ~frozenT from top env (set by conductorController.scd) and
// sends transport commands via /airkit/seek and /airkit/pause OSC. No direct
// calls into engine state.
//
// Split from the original conductorView.scd on 2026-07-13; engine lives in
// conductorController.scd. Load order: controller first, then this file.

var waveformPeaks;

var computeMonoPeaks = { |path, nPeaks|
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
    while { chunkStart < totalFrames } {
        var thisFrames = (totalFrames - chunkStart).min(chunkFrames);
        var chunkPeakLo, chunkPeakHi;
        sf.seek(chunkStart, 0);
        sf.readData(chunkBuf);
        chunkPeakLo = (chunkStart / samplesPerPeak).floor.asInteger;
        chunkPeakHi = ((chunkStart + thisFrames - 1) / samplesPerPeak).floor.asInteger
            .min(nPeaks - 1);
        (chunkPeakLo .. chunkPeakHi).do { |k|
            var loFrame = (k * samplesPerPeak).max(chunkStart);
            var hiFrame = (((k + 1) * samplesPerPeak) - 1).min(chunkStart + thisFrames - 1);
            var loLocal = (loFrame - chunkStart) * chan;
            var hiLocal = ((hiFrame - chunkStart + 1) * chan) - 1;
            var chunkMax = chunkBuf.copyRange(loLocal, hiLocal).abs.maxItem;
            if (chunkMax > peaks[k]) { peaks[k] = chunkMax };
        };
        chunkStart = chunkStart + thisFrames;
    };
    sf.close;
    peaks
};

// Playhead time in seconds. Derived from controller state: when playing,
// use SystemClock delta since ~playStartT; when paused, use ~frozenT.
~nowT = {
    if (~playStartT.notNil)
        { (SystemClock.seconds - ~playStartT).clip(0, ~fileDur) }
        { ~frozenT }
};

~conductorPeaksCache = ~conductorPeaksCache ? ();
waveformPeaks = ~conductorPeaksCache[~musicPath.asSymbol] ?? {
    var p = computeMonoPeaks.(~musicPath, 2000);
    ~conductorPeaksCache[~musicPath.asSymbol] = p;
    p
};

block {
    var waveView = UserView()
        .background_(Color.gray(0.12))
        .drawFunc_({ |vv|
            var vw = vv.bounds.width, vh = vv.bounds.height, mid = vh / 2;
            var dur = ~fileDur ? 0;
            if (waveformPeaks.notNil and: { waveformPeaks.size > 0 }) {
                var n = waveformPeaks.size;
                Pen.color = Color(0.6, 0.85, 1.0);
                Pen.width = 1;
                waveformPeaks.do { |p, i|
                    var xp = (i / n) * vw;
                    Pen.moveTo(xp @ (mid - (p * mid * 0.95)));
                    Pen.lineTo(xp @ (mid + (p * mid * 0.95)));
                };
                Pen.stroke;
            };
            if (dur > 0) {
                var xc = ~nowT.value / dur * vw;
                Pen.color = Color(1.0, 0.4, 0.4, 0.9);
                Pen.width = 1;
                Pen.line((xc @ 0), (xc @ vh));
                Pen.stroke;
            };
        })
        .mouseDownAction_({ |vv, xarg|
            var dur = ~fileDur ? 0;
            if (dur > 0) {
                NetAddr.new("127.0.0.1", 57120).sendMsg("/airkit/seek",
                    (xarg / vv.bounds.width * dur).clip(0, dur));
            };
        });

    ~updater = Routine {
        loop {
            waveView.refresh;
            (1/30).yield;
        };
    };
    AppClock.play(~updater);

    // Factory returned to main.sc's layout: builds the transport bar every
    // time it's invoked. Buttons and mouseDown talk to the controller via
    // /airkit/seek and /airkit/pause OSCdefs (defined in conductorController.scd).
    {
        View().layout_(HLayout(
            Button()
                .minHeight_(100)
                .maxWidth_(100)
                .states_([
                    ["Play", Color.black, Color.green.darken(0.5)],
                    ["Stop", Color.black, Color.red.darken(0.5)]
                ])
                .action_({ |b|
                    if (b.value == 1) {
                        NetAddr.new("127.0.0.1", 57120).sendMsg("/airkit/seek", ~frozenT ? 0);
                    } {
                        NetAddr.new("127.0.0.1", 57120).sendMsg("/airkit/pause");
                    };
                }),
            waveView
        ).spacing_(4).margins_(0))
        .maxHeight_(100);
    };
};
