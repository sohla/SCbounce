// Plan 2 + score overlay (pattern-driven variant). Same transport as
// sync_plan2_score.scd: SC plays the audio via DiskIn, a Routine walks
// *_beats.txt and fires \scoreBass, \scoreChord, \beatClick on tracked
// downbeats and true beats. The difference is that ~playScoreVoice (the
// per-subdivision pluck) is *not* triggered from the Routine. Instead this
// file:
//
//   - boots a ~beatClock = TempoClock slaved to the /beat OSC stream the
//     Routine already emits (OSCdef \beatSync, lifted from beat_consumer.scd),
//   - maintains ~scoreVoicePool / ~scoreVoiceAmp env vars holding the current
//     half-bar's fitted_notes as integer MIDI pitches and the matching amp,
//
// so that any downstream interactive Pdef can play on ~beatClock and read
// ~scoreVoicePool through a Pfunc { } closure. The file *does not* define
// its own Pdef — the clock is left free for the user's interactive layer.
// A commented-out template at the bottom shows the idiom.
//
// One ~beatClock "beat" == one beats.txt event (one subdivision). With
// ~scoreEventsPerBeat = 4 (the _mul4 default), four ~beatClock beats fit one
// true musical beat. Use `\dur, 4` in a Pbind for once-per-true-beat firing,
// `\dur, 8` for once per 2/4 bar, `\dur, 1` for once per subdivision.
//
// Usage (same as sync_plan2_score.scd):
//   sclang sc/sync_plan2_pattern_score.scd
//   ~go.value;             // start audio + Routine + clock sync
//   ~seek.(60.0);          // jump; ~onResync fires; downstream Pdefs restart
//   ~beek.(100);
//   ~stop.value;
//
//   ~scoreVoicePool        // -> [MIDI ints] for the current half (read-only;
//                          //   the Routine updates this each event)
//   ~scoreVoiceAmp         // -> float, current amp floor
//
// Overrides before running:
//   ~beatsPath, ~musicPath, ~scorePath, ~scoreAnchorBeat, ~bufFrames.

(
var root, libDir;

root   = thisProcess.nowExecutingPath.dirname.dirname;
libDir = root +/+ "sc" +/+ "lib";

// Same unconditional structural constants as sync_plan2_score.scd — see that
// file's comment for the reasoning (re-runs must not inherit stale values).
~scoreTicksPerBeat  = 480;
~scoreEventsPerBeat = 4;
~scoreBeatsPerBar   = 2;
~scoreAnchorBeat    = ~scoreAnchorBeat ? 0;

thisProcess.interpreter.executeFile(libDir +/+ "beatfile.scd");
thisProcess.interpreter.executeFile(libDir +/+ "beatclick.scd");
thisProcess.interpreter.executeFile(libDir +/+ "scorefile.scd");
thisProcess.interpreter.executeFile(libDir +/+ "score_synth.scd");
thisProcess.interpreter.executeFile(libDir +/+ "metafile.scd");

~beatsPath = (root +/+ "out" +/+ "Beethoven4_4_MSO_beats_2_mul4.txt");
~musicPath = (root +/+ "data" +/+ "audio" +/+ "Beethoven4_4_MSO.wav");
~scorePath = (root +/+ "data" +/+ "score" +/+ "tick_index_2.json");
~metaPath  = (root +/+ "out"  +/+ "Beethoven4_4_MSO_beats_2_mul4_meta.json");
~bufFrames = 65536;
~addr      = NetAddr("127.0.0.1", NetAddr.langPort);

~loadBeats.(~beatsPath);
~loadScore.(~scorePath);
~loadMeta.(~metaPath);

// --- score-context state read by downstream Pdefs --------------------------
~scoreVoicePool = ~scoreVoicePool ? [69];   // current half's MIDI pitches (Int)
~scoreVoiceAmp  = ~scoreVoiceAmp  ? 0.15;
// ~scoreGroup is created inside s.waitForBoot (below), once the server is
// live. Building it here would leave the env var pointing at a node ID that
// the live server doesn't actually own — Pdefs targeting it would log
// "FAILURE IN SERVER /s_new Group <id> not found".

// --- TempoClock slaved to /beat -------------------------------------------
// Body lifted from sc/beat_consumer.scd:7–91. The producer here is the
// Routine in this file (which sends ~addr.sendMsg("/beat", i) per event); the
// OSCdef catches its own producer in the same sclang process.
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
// `?` preserves any hook a downstream block has already registered.
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

// --- audio setup (same as sync_plan2_score.scd) ----------------------------
block {
    var sf = SoundFile.new;
    if (sf.openRead(~musicPath.standardizePath).not) {
        Error("could not open audio: %".format(~musicPath)).throw;
    };
    ~fileSR    = sf.sampleRate;
    ~fileChans = sf.numChannels;
    sf.close;
    "audio file: % Hz, % channels".format(~fileSR, ~fileChans).postln;
};

if (s.serverRunning and: { s.sampleRate.notNil and: { s.sampleRate != ~fileSR.asFloat } }) {
    Error(("server is running at % Hz but file needs % Hz. " ++
        "Reboot the server (cmd+B or s.reboot) and re-run this file.")
        .format(s.sampleRate, ~fileSR)).throw;
};
s.options.sampleRate = ~fileSR;

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

    // Always create a fresh score group on the live server. If a previous
    // run left one behind, its node ID may not exist after a server reboot
    // (or after s.freeAll). .free on a dead node is a noop.
    if (~scoreGroup.notNil) { ~scoreGroup.free; };
    ~scoreGroup = Group(s);

    ~stream = Buffer.cueSoundFile(s, ~musicPath.standardizePath, 0, ~fileChans, ~bufFrames);

    s.sync;

    ~playSynth = nil;
    ~routine   = nil;

    // The score-aware step. On every beats.txt event:
    //   1. /beat OSC fires — OSCdef(\beatSync) updates ~beatClock so any Pdef
    //      running on that clock is in phase with the audio.
    //   2. State update for downstream patterns: ~scoreVoicePool gets the
    //      current half's fitted_notes as integers, ~scoreVoiceAmp gets the
    //      loudness-floored amp.
    //   3. On D labels (tracked downbeats), play bass + chord stab + click.
    //   4. Print the full per-bar score entry (every field from tick_index.json)
    //      plus per-beat context (beat idx, half, isDown, isTrueBeat) on every event.
    //
    // No \scoreVoice is fired here — that's the downstream Pdef's job. See
    // the example template at the bottom of this file.
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

                // State update for downstream patterns. Replaces the
                // ~playScoreVoice call in sync_plan2_score.scd.
                ~scoreVoicePool = halves[half].collect(_.asInteger);
                ~scoreVoiceAmp  = voiceAmp;

                s.bind {
                    if (isTrueBeat) {
                        // Same h0-pin workaround as sync_plan2_score.scd; see
                        // that file for the rationale.
						// ~playScoreChord.(p, 0, chordAmp, chordCutoff);
                    };
                    if (isDown) {
						// ~playScoreBass.(p, half, bassAmp);
						// ~playBeatClick.(true, -1);
                    };
                };

                ("beat % | bar % | half % | down=% | trueBeat=% | tick=% | sec=% | phrase=% | key=% % | chord=%/% | T=% B=% D=% R=% L=% | scale=% | fitted=% | passing=% | motives=%".format(
                    i, p["bar"], half, isDown, isTrueBeat,
                    p["tick"], p["section_id"], p["phrase_id"],
                    p["key"], p["mode"],
                    p["chord_root"], p["chord_roman"],
                    p["tension"].asFloat.round(0.01),
                    p["brightness"].asFloat.round(0.01),
                    p["density"].asFloat.round(0.01),
                    p["register"].asFloat.round(0.01),
                    p["loudness"].asFloat.round(0.01),
                    p["active_scale"],
                    p["fitted_notes"],
                    p["fitted_passing_notes"],
                    p["active_motives"]
                )).postln;
                ("  m: loud=% dens=% flat=% dom=%[%..%Hz] bands=%".format(
                    m["loudness"].asFloat.round(0.01),
                    m["density"].asFloat.round(0.01),
                    m["flatness"].asFloat.round(0.01),
                    m["dom_band"].asInteger,
                    ~metaBandEdges[m["dom_band"].asInteger].asInteger,
                    ~metaBandEdges[m["dom_band"].asInteger + 1].asInteger,
                    m["bands"].collect({ |v| v.asFloat.round(0.01) })
                )).postln;

                ~addr.sendMsg("/beat", i);
            };
        }).play(SystemClock);
    };

    ~go = { ~seek.(0); };

    ~seek = { |seekT|
        Routine({
            var idx = ~beats.indexOfGreaterThan(seekT) ? ~beats.size;
            var startFrame = (seekT * ~fileSR).round.asInteger;
            var startT;

            ~routine.stop;
            ~playSynth.free;
            if (~stream.notNil) { ~stream.close; ~stream.free; };

            ~stream = Buffer.cueSoundFile(s, ~musicPath.standardizePath,
                startFrame, ~fileChans, ~bufFrames);
            s.sync;

            startT = SystemClock.seconds - seekT;
            s.bind { ~playSynth = Synth(\playStream, [\buf, ~stream]) };
            // Force ~beatSync to re-anchor on the next /beat — the seek made
            // the prior smoothing state stale.
            ~lastBeatT = nil;
            ~scheduleFromIndex.(idx, startT);
            "/sc/seek % -> frame %, beat %".format(seekT, startFrame, idx).postln;
        }).play(AppClock);
    };

    ~beek = { |beat|
        var clamped = beat.clip(0, ~beats.size - 1);
        ~seek.(~beats[clamped]);
    };

    ~stop = {
        ~routine.stop;
        ~playSynth.free;
        ~playSynth = nil;
    };

    OSCdef(\plan2pGo,    { ~go.value },                       '/sc/go');
    OSCdef(\plan2pSeek,  { |msg| ~seek.(msg[1].asFloat) },    '/sc/seek');
    OSCdef(\plan2pBeek,  { |msg| ~beek.(msg[1].asInteger) },  '/sc/beek');
    OSCdef(\plan2pPause, { ~stop.value },                     '/sc/pause');

    ("plan 2 + pattern score ready. % beats, % score entries, anchor=%."
        .format(~beats.size, ~scoreEntries.size, ~scoreAnchorBeat)).postln;
    "evaluate ~go.value to start. Define a Pdef on ~beatClock to play notes;".postln;
    "the Pdef reads ~scoreVoicePool/Amp via Pfunc { } closures.".postln;
});
)

~seek.(0)
~scoreAnchorBeat = 3
// ===========================================================================
// Example downstream Pdef — uncomment, evaluate after ~go.value to hear it.
// Picks one random pitch from the current half's fitted_notes per producer
// beat (one beats.txt event in _mul4 = one sixteenth note). The Pdef plays
// on ~beatClock, which is already in phase with the audio via OSCdef \beatSync.
// ---------------------------------------------------------------------------

(
// quant aligns the Pdef's first event to a multiple of N ~beatClock beats.
// One ~beatClock beat == one beats.txt event (one subdivision), so:
//   quant: 1                                  -> next subdivision
//   quant: ~scoreEventsPerBeat                -> next true beat (4 in _mul4)
//   quant: ~scoreBeatsPerBar * ~scoreEventsPerBeat
//                                             -> next bar start (8 in 2/4 _mul4)
//   quant: [N, P]                             -> multiple of N + phase P
//                                                (e.g. [8, 4] = mid-bar of a 2/4 bar)
// `~beatClock.beats` is anchored at 0 on the first /beat (the file's first
// tracked downbeat), so all multiples of (beats_per_bar * events_per_beat)
// fall on subsequent downbeats.
Pdef(\scoreVoicePattern, Pbind(
    \instrument, \scoreVoice,
    \group,      ~scoreGroup,
	\dur,        Pseq([0.25, 0.25,0.25] * 4, inf),
	\note,       Pseq([0], inf),
	\octave ,	1,
	\root,       Pfunc { ~scoreVoicePool.choose.asInteger},
    \amp,        Pfunc { ~scoreVoiceAmp * 2},
    \pan,        Pwhite(-0.3, 0.3),
)).play(~beatClock, quant: ~scoreBeatsPerBar * ~scoreEventsPerBeat);

// Restart the pattern on seek/big jump so it re-quants to the new position.
~onResync = { |idx|
    Pdef(\scoreVoicePattern).stop;
    ~scoreGroup.freeAll;
    Pdef(\scoreVoicePattern).play(~beatClock,
        quant: ~scoreBeatsPerBar * ~scoreEventsPerBeat);
};
)

 Pdef(\scoreVoicePattern).stop;        // halt the pattern (audio + Routine continue)

// ===========================================================================
// Cleanup — evaluate this block to fully tear down state between runs. Safe
// to call multiple times (nil-guarded). Frees server-side group + buffer +
// playStream, stops the slaved clock, frees OSCdefs and the example Pdef.
// Use whenever the file is about to be re-evaluated to avoid leaking nodes
// or stacking OSCdefs.
// ---------------------------------------------------------------------------
(
"cleanup: sync_plan2_pattern_score".postln;

Pdef(\scoreVoicePattern).stop;
// Pdef.removeAt(\scoreVoicePattern);

if (~routine.notNil)    { ~routine.stop;     ~routine    = nil; };
if (~playSynth.notNil)  { ~playSynth.free;   ~playSynth  = nil; };
if (~stream.notNil)     { ~stream.close;     ~stream.free; ~stream = nil; };
if (~scoreGroup.notNil) { ~scoreGroup.free;  ~scoreGroup = nil; };
if (~beatClock.notNil)  { ~beatClock.stop;   ~beatClock  = nil; };

OSCdef(\beatSync).free;
OSCdef(\plan2pGo).free;
OSCdef(\plan2pSeek).free;
OSCdef(\plan2pBeek).free;
OSCdef(\plan2pPause).free;
OSCdef(\scoreReload).free;                   // installed by scorefile.scd
OSCdef(\metaReload).free;                    // installed by metafile.scd


~lastBeatT      = nil;
~onResync       = nil;
~scoreVoicePool = nil;
~scoreVoiceAmp  = nil;
~metaEntries    = nil;

"done.".postln;

)

