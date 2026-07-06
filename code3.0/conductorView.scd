var renderRate = 30;
var spacing = 4;

var controlView = {
	View()
    .maxHeight_(70)
		.layout_(GridLayout.rows([
      [
        Button()
          .minHeight_(50)
          .maxWidth_(100)
          .states_([["Pause", Color.black, Color.red.darken(0.5)], ["Play", Color.black, Color.green.darken(0.5)]])
          .action_({|b|
            if(b.value == 0, {
              ~stop.();
            }, {
              ~seek.(0);
              ~scoreAnchorBeat = 3;
            });
          })
      ],[
        UserView()
          .background_(Color.black)
          .frameRate_(30)
          .animate_(true)
          .drawFunc_({|v|
            var vw = v.bounds.width;
            var vh = v.bounds.height;
            Pen.fillColor = Color.red.alpha_(0.5);
            Pen.addRect(Rect(0, 0, vw, vh));
            Pen.fill;
            Pen.fillColor = Color.grey;
            Pen.addRect(Rect(2, 2, vw-4, vh-4));
            Pen.fill;
          })


      ]
    ])
      
    );
};

var root, libDir;

root   = thisProcess.nowExecutingPath.dirname.dirname;
libDir = root +/+ "code3.0" +/+ "lib";

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

controlView;


