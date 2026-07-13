// Plan 2 + pattern score + GUI experiment. Transport mirrors
// sync_plan2_pattern_score.scd exactly: DiskIn streams the audio, a Routine
// walks *_beats.txt, OSCdef \beatSync slaves ~beatClock to /beat, and
// ~scoreVoicePool / ~scoreVoiceAmp are updated each event so downstream
// Pdefs can play on the synced clock.
//
// The new layer is a Window with two stacked panels on the same time axis:
//
//   1. SoundFileView — the whole audio waveform with a moving playhead.
//   2. UserView      — five normalized curves from tick_index.json
//                      (tension, brightness, density, register, loudness),
//                      one lane each. The same red playhead line crosses it.
//
// Cursor and curves are repainted on AppClock at ~30 Hz. REPL control is
// unchanged from sync_plan2_pattern_score.scd:
//   ~go.value;   ~seek.(60);   ~beek.(100);   ~stop.value;
//
// Anchor edits invalidate the curves' x-mapping (ticks → audio time goes
// through ~scoreAnchorBeat). After changing ~scoreAnchorBeat, re-run
// ~buildCurves.value to rebuild the curve cache, then ~paramView.refresh.
//
// Overrides before running:
//   ~beatsPath, ~musicPath, ~scorePath, ~scoreAnchorBeat, ~bufFrames.

(
var root, libDir;

root   = thisProcess.nowExecutingPath.dirname.dirname;
libDir = root +/+ "sc" +/+ "lib";

~scoreTicksPerBeat  = 480;
~scoreEventsPerBeat = 4;
~scoreBeatsPerBar   = 2;
~scoreAnchorBeat    = ~scoreAnchorBeat ? 0;

thisProcess.interpreter.executeFile(libDir +/+ "beatfile.scd");
thisProcess.interpreter.executeFile(libDir +/+ "beatclick.scd");
thisProcess.interpreter.executeFile(libDir +/+ "scorefile.scd");
thisProcess.interpreter.executeFile(libDir +/+ "score_synth.scd");
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

~scoreVoicePool = ~scoreVoicePool ? [69];
~scoreVoiceAmp  = ~scoreVoiceAmp  ? 0.15;

// --- TempoClock slaved to /beat (same as sync_plan2_pattern_score.scd) -----
~beatClock       = ~beatClock ?? { TempoClock.new(2).permanent_(true) };
~smoothing       = 0.1;
~kPmin           = 0.9;
~kPmax           = 1.0;
~kPSens          = 0.01;
~tempoMin        = 0.05;
~tempoMax        = 50;
~anchorThreshold = 2.0;
~obsTempo        = 2;
~lastBeatT       = nil;
~onResync        = ~onResync ? { |idx| };

OSCdef(\beatSync, { |msg|
    var now = SystemClock.seconds;
    var idx = msg[1];
    var interval, observedTempo, phaseError, kPdyn, newTempo;
    if(~lastBeatT.isNil) {
        ~beatClock.beats = idx;
        ~lastBeatT = now;
        ~onResync.value(idx);
        "beat sync: anchored at idx %".format(idx).postln;
    } {
        phaseError = idx - ~beatClock.beats;
        if(phaseError.abs > ~anchorThreshold) {
            ~beatClock.beats = idx;
            ~lastBeatT = now;
            ~onResync.value(idx);
            "beat sync: re-anchored at idx % (err was %)".format(idx, phaseError.round(0.001)).postln;
        } {
            interval      = now - ~lastBeatT;
            observedTempo = 1 / interval;
            ~obsTempo     = (~smoothing * ~obsTempo) + ((1 - ~smoothing) * observedTempo);
            kPdyn         = ~kPmin + ((~kPmax - ~kPmin) * tanh(phaseError.abs / ~kPSens));
            newTempo      = ~obsTempo * (1 + (kPdyn * phaseError));
            ~beatClock.tempo = newTempo.clip(~tempoMin, ~tempoMax);
            ~lastBeatT = now;
        };
    };
}, "/beat");

// --- audio setup (numFrames + total duration are extras needed by the GUI) ---
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

// --- playhead time estimate (used by the GUI cursor) ----------------------
// ~playStartT is the SystemClock.seconds reading at which audio-time would
// have been 0 — i.e. (now - ~playStartT) is the current audio position.
// ~stop snapshots ~frozenT so the cursor stays put after stop.
~playStartT = nil;
~frozenT    = ~frozenT ? 0;
~nowT       = {
    if (~playStartT.notNil)
        { (SystemClock.seconds - ~playStartT).clip(0, ~fileDur) }
        { ~frozenT }
};

// --- score-curve precompute -----------------------------------------------
// For every score entry, map its tick → true-beat → beats-file row → audio
// time. Inverse of scorefile.scd's ~paramsAtBeat. Entries whose mapped beat
// index falls outside the beats file are dropped (intro lead-in / tail).
~buildCurves = {
    var fields = ["tension", "brightness", "density", "register", "loudness"];
    var pts = Array.new(~scoreEntries.size);
    ~scoreEntries.do { |e|
        var tick     = e["tick"].asInteger;
        var trueBeat = (tick / ~scoreTicksPerBeat) - ~scoreAnchorBeat;
        var beatIdx  = (trueBeat * ~scoreEventsPerBeat).round.asInteger;
        if ((beatIdx >= 0) and: { beatIdx < ~beats.size }) {
            pts.add([~beats[beatIdx]] ++ fields.collect({ |k|
                var v = e[k];
                if (v.isNil) { 0.0 } { v.asFloat.clip(0, 1) }
            }));
        };
    };
    ~curvePoints = pts;
    ~curveFields = fields;
    "built % curve points (% fields)".format(pts.size, fields.size).postln;
    pts.size;
};
~buildCurves.value;

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

    if (~scoreGroup.notNil) { ~scoreGroup.free; };
    ~scoreGroup = Group(s);

    ~stream = Buffer.cueSoundFile(s, ~musicPath.standardizePath, 0, ~fileChans, ~bufFrames);

    s.sync;

    ~playSynth = nil;
    ~routine   = nil;

    // Identical Routine body to sync_plan2_pattern_score.scd — kept verbatim
    // so the audible behaviour is byte-equivalent. The only env-var the GUI
    // reads is ~playStartT, which is set by ~seek (below), not in here.
    ~scheduleFromIndex = { |startIdx, anchorTime|
        var beatInBar = 0;
        var eventsPerBar = ~scoreBeatsPerBar * ~scoreEventsPerBeat;
        ~routine.stop;
        ~routine = Routine({
            (startIdx .. ~beats.size - 1).do { |i|
                var isDown, isTrueBeat, p, m, half, halves, voiceAmp, bassAmp, chordAmp, chordCutoff;
                (anchorTime + ~beats[i] - SystemClock.seconds).max(0).wait;
                isDown = (~labels[i] == \D);
                if (isDown) { beatInBar = 0 } { beatInBar = beatInBar + 1 };
                isTrueBeat = (beatInBar % ~scoreEventsPerBeat) == 0;

                p      = ~paramsAtBeat.(i);
                m      = ~metaAtBeat.(i);
                halves = p["fitted_notes"];
                half   = (beatInBar * halves.size / eventsPerBar).floor.clip(0, halves.size - 1);

                voiceAmp    = 0.04 + (0.18 * p["loudness"].asFloat);
                bassAmp     = 0.05 + (0.25 * p["loudness"].asFloat);
                chordAmp    = 0.08 + (0.20 * p["loudness"].asFloat);
                chordCutoff = 600 + (3000 * p["tension"].asFloat);

                ~scoreVoicePool = halves[half].collect(_.asInteger);
                ~scoreVoiceAmp  = voiceAmp;

                s.bind {
                    if (isTrueBeat) {
                        ~playScoreChord.(p, 0, chordAmp, chordCutoff);
                    };
                    if (isDown) {
                        ~playScoreBass.(p, half, bassAmp);
                        ~playBeatClick.(true, -1);
                    };
                };

                ~addr.sendMsg("/beat", i);
            };
        }).play(SystemClock);
    };

    ~go = { ~seek.(0); };

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
            ~lastBeatT = nil;
            ~scheduleFromIndex.(idx, ~playStartT);
            "/sc/seek % -> frame %, beat %".format(seekT, startFrame, idx).postln;
        }).play(AppClock);
    };

    ~beek = { |beat|
        var clamped = beat.clip(0, ~beats.size - 1);
        ~seek.(~beats[clamped]);
    };

    ~stop = {
        ~frozenT = ~nowT.value;        // freeze cursor at last estimated position
        ~routine.stop;
        ~playSynth.free;
        ~playSynth = nil;
        ~playStartT = nil;
    };

    OSCdef(\plan2pgGo,    { ~go.value },                       '/sc/go');
    OSCdef(\plan2pgSeek,  { |msg| ~seek.(msg[1].asFloat) },    '/sc/seek');
    OSCdef(\plan2pgBeek,  { |msg| ~beek.(msg[1].asInteger) },  '/sc/beek');
    OSCdef(\plan2pgPause, { ~stop.value },                     '/sc/pause');

    // --- GUI ---------------------------------------------------------------
    // Built on AppClock (Qt widgets must be created on the GUI thread). The
    // whole thing is one-shot — the closure returns nil so AppClock.sched
    // doesn't re-schedule.
    AppClock.sched(0, {
        var win, sfView, paramView, infoText, sfTop;
        var fields    = ~curveFields;
        var laneColor = [
            Color(0.95, 0.50, 0.40),    // tension   — warm red
            Color(0.95, 0.85, 0.40),    // brightness — yellow
            Color(0.50, 0.90, 0.60),    // density   — green
            Color(0.40, 0.75, 0.95),    // register  — sky
            Color(0.80, 0.55, 0.95)     // loudness  — violet
        ];
        var winW = 1200, winH = 560;
        var sfH  = 200, paramH = winH - sfH - 60;
        var infoH = 24;

        if (~guiWin.notNil and: { ~guiWin.isClosed.not }) { ~guiWin.close };
        if (~guiTick.notNil) { ~guiTick.stop };

        win = Window("sync_plan2_pattern_score_gui — % | %"
            .format(~musicPath.basename, ~scorePath.basename),
            Rect(80, 80, winW, winH))
            .background_(Color.gray(0.08));
        ~guiWin = win;

        infoText = StaticText(win, Rect(8, 4, winW - 16, infoH))
            .stringColor_(Color.gray(0.85))
            .string_("loading audio for waveform display…");

        sfView = SoundFileView(win, Rect(8, infoH + 8, winW - 16, sfH))
            .background_(Color.gray(0.12))
            .waveColors_([Color(0.6, 0.85, 1.0)])
            .gridOn_(false)
            .timeCursorOn_(true)
            .timeCursorColor_(Color(1.0, 0.4, 0.4))
            .drawsWaveForm_(true);

        sfTop = SoundFile.new;
        if (sfTop.openRead(~musicPath.standardizePath)) {
            sfView.soundfile = sfTop;
            sfView.read(0, sfTop.numFrames);
            sfTop.close;
            infoText.string_(
                "audio % Hz, %s   |   score % entries (% in-range)   |   beats %   |   anchor=%"
                .format(~fileSR, ~fileDur.round(0.01),
                    ~scoreEntries.size, ~curvePoints.size,
                    ~beats.size, ~scoreAnchorBeat));
        } {
            infoText.string_("could not open audio for waveform: %".format(~musicPath));
        };

        ~sfView = sfView;

        paramView = UserView(win, Rect(8, infoH + 8 + sfH + 8, winW - 16, paramH))
            .background_(Color.gray(0.12))
            .drawFunc_({ |v|
                var w   = v.bounds.width;
                var h   = v.bounds.height;
                var dur = ~fileDur;
                var pts = ~curvePoints;
                var laneH;
                if (pts.notNil and: { pts.size > 1 } and: { dur > 0 }) {
                    laneH = h / fields.size;

                    // lane separators + labels
                    Pen.width = 1;
                    fields.do { |name, lane|
                        var y0 = lane * laneH;
                        Pen.color = Color.gray(0.25);
                        Pen.line((0 @ (y0 + laneH)), (w @ (y0 + laneH)));
                        Pen.stroke;
                        Pen.color = laneColor[lane];
                        Pen.stringAtPoint(name, (6 @ (y0 + 4)));
                    };

                    // curves
                    Pen.width = 1.4;
                    fields.do { |name, lane|
                        var y0 = lane * laneH;
                        Pen.color = laneColor[lane];
                        pts.do { |row, i|
                            var t  = row[0];
                            var v2 = row[lane + 1];
                            var x  = t / dur * w;
                            var y  = y0 + laneH - (v2 * laneH);
                            if (i == 0) { Pen.moveTo(x @ y) } { Pen.lineTo(x @ y) };
                        };
                        Pen.stroke;
                    };

                    // playhead — aligned with the SoundFileView cursor
                    Pen.color = Color(1.0, 0.4, 0.4, 0.9);
                    Pen.width = 1.0;
                    Pen.line(((~nowT.value / dur * w) @ 0),
                             ((~nowT.value / dur * w) @ h));
                    Pen.stroke;
                };
            });
        ~paramView = paramView;

        // GUI tick on AppClock. Reads ~nowT and pushes the audio position to
        // both panes. Terminates cleanly when the window is closed.
        ~guiTick = Routine({
            while { win.isClosed.not } {
                var t = ~nowT.value;
                sfView.timeCursorPosition_((t * ~fileSR).asInteger);
                paramView.refresh;
                (1/30).wait;
            };
        }).play(AppClock);

        win.onClose_({
            if (~guiTick.notNil) { ~guiTick.stop; ~guiTick = nil };
        });
        win.front;
        nil;
    });

    ("plan 2 + pattern score + GUI ready. % beats, % score entries, anchor=%."
        .format(~beats.size, ~scoreEntries.size, ~scoreAnchorBeat)).postln;
    "evaluate ~go.value to start. window shows waveform + 5 score curves.".postln;
    "after editing ~scoreAnchorBeat: ~buildCurves.value; ~paramView.refresh.".postln;
});
)

// ===========================================================================
// REPL playground — evaluate any of these after the file's main block has
// finished booting (look for the 'plan 2 + pattern score + GUI ready' line).
// ---------------------------------------------------------------------------

~go.value;
~seek.(60);
~beek.(3020);
~stop.value;
~scoreAnchorBeat = 3


// Anchor tuning: change, rebuild curve cache, repaint.
~scoreAnchorBeat = 3; ~buildCurves.value; ~paramView.refresh;

// ===========================================================================
// Cleanup — evaluate to fully tear down state between runs. Safe to call
// multiple times (nil-guarded). Frees server-side group + buffer + playStream,
// stops the slaved clock, frees OSCdefs, closes the GUI.
// ---------------------------------------------------------------------------
(
"cleanup: sync_plan2_pattern_score_gui".postln;

if (~guiTick.notNil)    { ~guiTick.stop;     ~guiTick    = nil; };
if (~guiWin.notNil and: { ~guiWin.isClosed.not }) { ~guiWin.close; };
~guiWin    = nil;
~sfView    = nil;
~paramView = nil;

if (~routine.notNil)    { ~routine.stop;     ~routine    = nil; };
if (~playSynth.notNil)  { ~playSynth.free;   ~playSynth  = nil; };
if (~stream.notNil)     { ~stream.close;     ~stream.free; ~stream = nil; };
if (~scoreGroup.notNil) { ~scoreGroup.free;  ~scoreGroup = nil; };
if (~beatClock.notNil)  { ~beatClock.stop;   ~beatClock  = nil; };

OSCdef(\beatSync).free;
OSCdef(\plan2pgGo).free;
OSCdef(\plan2pgSeek).free;
OSCdef(\plan2pgBeek).free;
OSCdef(\plan2pgPause).free;
OSCdef(\scoreReload).free;
OSCdef(\metaReload).free;

~lastBeatT      = nil;
~onResync       = nil;
~scoreVoicePool = nil;
~scoreVoiceAmp  = nil;
~metaEntries    = nil;
~playStartT     = nil;
~frozenT        = nil;

"done.".postln;
)
