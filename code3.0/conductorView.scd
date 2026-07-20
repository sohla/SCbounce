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
                NetAddr.new("127.0.0.1", NetAddr.langPort).sendMsg("/airkit/seek",
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

    // Cmd+. → stop the refresh Routine before the window closes. Otherwise
    // AppClock keeps ticking waveView.refresh after Qt has freed the view,
    // and the queued drawFunc call sees a nil vv.bounds → nil.width throw.
    CmdPeriod.doOnce({
        if (~updater.notNil) { ~updater.stop; ~updater = nil };
    });

    // Factory returned to main.sc's layout: builds the transport bar every
    // time it's invoked. Buttons and mouseDown talk to the controller via
    // /airkit/seek and /airkit/pause OSCdefs (defined in conductorController.scd).
    // The state-button row sends /airkit/state and reflects changes from
    // both local clicks and external OSC (both trigger the same OSCdef).
    {
        var roomStates = [\idle, \tuning, \piece, \curtain, \silent];
        var stateColors = (
            idle:    Color.gray(0.5),
            tuning:  Color(0.9, 0.7, 0.2),
            piece:   Color(0.3, 0.7, 0.3),
            curtain: Color(0.9, 0.5, 0.2),
            silent:  Color(0.85, 0.25, 0.25)
        );
        var stateButtons = ();
        var setActiveState = { |state|
            stateButtons.keysValuesDo { |s, btn|
                btn.value = if (s == state) { 1 } { 0 };
            };
        };
        var stateButtonList = roomStates.collect { |state|
            var col = stateColors[state];
            var btn = Button()
                .maxHeight_(36)
                .states_([
                    [state.asString, Color.gray(0.75), Color.gray(0.15)],
                    [state.asString, Color.white,      col]
                ])
                .action_({ |b|
                    // Send OSC — controller updates state AND the OSCdef
                    // below fires (both listen to /airkit/state) to sync
                    // the button visuals. Don't rely on b.value; the
                    // round-trip is source of truth so external OSC
                    // triggers the same visual update as a local click.
                    NetAddr.new("127.0.0.1", NetAddr.langPort)
                        .sendMsg("/airkit/state", state.asString);
                });
            stateButtons[state] = btn;
            btn
        };

        // Reflect state changes from any source (local click, external OSC,
        // internal curtain trigger). Deferred to AppClock — OSCdefs fire on
        // SystemClock and Qt widget mutation must happen on the GUI thread.
        OSCdef(\conductorViewState, { |msg|
            var state = msg[1].asString.asSymbol;
            { setActiveState.(state) }.defer;
        }, '/airkit/state');

        // On mount, ask the controller for current ~roomState. The
        // reply lands on /airkit/state/reply → setActiveState fires with
        // whatever state the room is currently in.
        OSCdef(\conductorViewStateReply, { |msg|
            var state = msg[1].asString.asSymbol;
            { setActiveState.(state) }.defer;
        }, '/airkit/state/reply');
        NetAddr.new("127.0.0.1", NetAddr.langPort).sendMsg("/airkit/getState");

        View().layout_(VLayout(
            HLayout(
                Button()
                    .minHeight_(100)
                    .maxWidth_(100)
                    .states_([
                        ["Play", Color.black, Color.green.darken(0.5)],
                        ["Stop", Color.black, Color.red.darken(0.5)]
                    ])
                    .action_({ |b|
                        if (b.value == 1) {
                            NetAddr.new("127.0.0.1", NetAddr.langPort).sendMsg("/airkit/seek", ~frozenT ? 0);
                        } {
                            NetAddr.new("127.0.0.1", NetAddr.langPort).sendMsg("/airkit/pause");
                        };
                    }),
                waveView
            ).spacing_(4).margins_(0),
            HLayout(*stateButtonList).spacing_(2).margins_(0)
        ).spacing_(4).margins_(0))
        .maxHeight_(150);
    };
};
