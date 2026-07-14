// AirKit conductor — engine side.
//
// Owns: score/meta/beat file loading, the TempoClock (~beatClock) and its
// /beat phase-lock loop, audio playback (~playSynth via DiskIn), the score-
// walking Routine (~scheduleFromIndex), transport actions (~go, ~seek,
// ~beek, ~stop), and OSCdefs on /airkit/go /airkit/seek /airkit/beek
// /airkit/pause so the view (or any client) can drive transport by message.
//
// Split from conductorView.scd on 2026-07-13 — conductorView now holds
// only UI and talks to this file through /airkit/* OSC. Loaded from main.sc
// via Require before conductorView.

var root, libDir;

root   = thisProcess.nowExecutingPath.dirname.dirname;
libDir = root +/+ "code3.0" +/+ "lib";

// Structural constants — set unconditionally so re-runs don't inherit stale
// values from a previous evaluation.
~scoreTicksPerBeat  = 480;
~scoreEventsPerBeat = 4;
~scoreBeatsPerBar   = 2;
// ~scoreAnchorBeat    = ~scoreAnchorBeat ? 0;
~scoreAnchorBeat = 3;

// Playback clock state read by the view for the playhead.
~playStartT = nil;
~frozenT    = ~frozenT ? 0;

// COTF: at the show QLab owns the Beethoven audio; the conductor then walks
// the score and drives ~beatClock WITHOUT local DiskIn playback. Default true
// preserves standalone behaviour. Set false in cotf/config.scd.
~conductorAudioEnabled = ~conductorAudioEnabled ? true;

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

// --- score-context state read by downstream personalities -----------------
~scoreVoicePool = ~scoreVoicePool ? [69];   // current half's MIDI pitches (Int)
~scoreVoiceAmp  = ~scoreVoiceAmp  ? 0.15;
// ~scoreGroup is created inside s.waitForBoot (below), once the server is
// live. Building it here would leave the env var pointing at a node ID that
// the live server doesn't actually own — synths targeting it would log
// "FAILURE IN SERVER /s_new Group <id> not found".

// --- TempoClock slaved to /beat -------------------------------------------
// The producer here is the Routine below (which sends ~addr.sendMsg("/beat", i)
// per event); the OSCdef catches its own producer in the same sclang process.
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

// --- audio setup -----------------------------------------------------------
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

    SynthDef(\playStream, { |out = 0, buf, amp = 0.5|
        var sig = DiskIn.ar(2, buf, loop: 0);
        Out.ar(out, sig * amp);
    }).add;

    s.sync;

    // Always create a fresh score group on the live server. If a previous
    // run left one behind, its node ID may not exist after a server reboot
    // (or after s.freeAll). .free on a dead node is a noop.
    if (~scoreGroup.notNil) { ~scoreGroup.free; };
    ~scoreGroup = Group(s);

    if (~conductorAudioEnabled) {
        ~stream = Buffer.cueSoundFile(s, ~musicPath.standardizePath, 0, ~fileChans, ~bufFrames);

        s.sync;
    };

    ~playSynth = nil;
    ~routine   = nil;

    // The score-aware step. On every beats.txt event:
    //   1. Update ~scoreVoicePool / ~scoreVoiceAmp state read by
    //      downstream personalities.
    //   2. Dispatch personality hooks (~onTick / ~onBeat / ~onBar /
    //      ~onSection) to every enabled device's env, driven by this
    //      Routine's SystemClock sleep against ~beats[i] — precise
    //      beat timing, no AppClock polling.
    //   3. Send /beat OSC so OSCdef(\beatSync) updates ~beatClock.
    //
    // barIdx / lastSectionId are declared inside ~scheduleFromIndex so
    // every seek starts a fresh closure — barIdx resets, section change
    // re-fires from prevSectionId = nil.
    ~scheduleFromIndex = { |startIdx, anchorTime|
        var beatInBar = 0;
        var barIdx = -1;
        var lastSectionId = nil;
        var lastPhraseId  = nil;
        var lastHalf      = -1;
        var lastChord     = nil;
        var lastKey       = nil;
        var lastScale     = nil;
        var eventsPerBar = ~scoreBeatsPerBar * ~scoreEventsPerBeat;
        ~routine.stop;
        ~routine = Routine({
            (startIdx .. ~beats.size - 1).do { |i|
                var isDown, isTrueBeat, p, m, half, halves, voiceAmp, bassAmp, chordAmp, chordCutoff;
                var sectionId, sectionChanged;
                var phraseId, phraseChanged, halfChanged;
                var chord, chordChanged, key, keyChanged, scale, scaleChanged;
                var ctx;
                (anchorTime + ~beats[i] - SystemClock.seconds).max(0).wait;
                isDown = (~labels[i] == \D);
                if (isDown) { beatInBar = 0 } { beatInBar = beatInBar + 1 };
                if (isDown) { barIdx = barIdx + 1 };
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

                // Change-detection: build a comparable string/int for each
                // tracked field so identical-content values compare equal
                // even if the underlying score returned a fresh dict.
                sectionId = p["section_id"];
                phraseId  = p["phrase_id"];
                chord     = "% %".format(p["chord_root"], p["chord_roman"]);
                key       = "% %".format(p["key"],        p["mode"]);
                scale     = p["active_scale"];

                sectionChanged = sectionId != lastSectionId;
                phraseChanged  = phraseId  != lastPhraseId;
                halfChanged    = half      != lastHalf;
                chordChanged   = chord     != lastChord;
                keyChanged     = key       != lastKey;
                scaleChanged   = scale     != lastScale;

                ctx = (
                    idx:            i,
                    beatInBar:      beatInBar,
                    isDown:         isDown,
                    isTrueBeat:     isTrueBeat,
                    barIdx:         barIdx,
                    half:           half,
                    prevHalf:       lastHalf,
                    halfChanged:    halfChanged,
                    sectionId:      sectionId,
                    prevSectionId:  lastSectionId,
                    sectionChanged: sectionChanged,
                    phraseId:       phraseId,
                    prevPhraseId:   lastPhraseId,
                    phraseChanged:  phraseChanged,
                    chord:          chord,
                    prevChord:      lastChord,
                    chordChanged:   chordChanged,
                    key:            key,
                    prevKey:        lastKey,
                    keyChanged:     keyChanged,
                    scale:          scale,
                    prevScale:      lastScale,
                    scaleChanged:   scaleChanged,
                    voicePool:      ~scoreVoicePool,
                    voiceAmp:       voiceAmp,
                    p:              p,
                    m:              m
                );

                ~devices.keysValuesDo { |port, d|
                    if (d.env.notNil and: { d.enabled == true }) {
                        d.env.use {
                            ~onTick.(ctx);
                            if (halfChanged)    { ~onHalf.(ctx) };
                            if (isTrueBeat)     { ~onBeat.(ctx) };
                            if (isDown)         { ~onBar.(ctx) };
                            if (phraseChanged)  { ~onPhrase.(ctx) };
                            if (sectionChanged) { ~onSection.(ctx) };
                            if (chordChanged)   { ~onChord.(ctx) };
                            if (keyChanged)     { ~onKey.(ctx) };
                            if (scaleChanged)   { ~onScale.(ctx) };
                        };
                    };
                };
                lastSectionId = sectionId;
                lastPhraseId  = phraseId;
                lastHalf      = half;
                lastChord     = chord;
                lastKey       = key;
                lastScale     = scale;

                s.bind {
                    if (isTrueBeat) {
                        // ~playScoreChord.(p, 0, chordAmp, chordCutoff);
                    };
                    if (isDown) {
                        // ~playScoreBass.(p, half, bassAmp);
                        // ~playBeatClick.(true, -1);
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
            var startT;

            ~routine.stop;
            ~playSynth.free;
            // nil out after freeing: with ~conductorAudioEnabled=false no new
            // stream is cued below, so a stale reference here would make the
            // NEXT seek close an already-freed Buffer — the throw aborts the
            // routine before ~scheduleFromIndex and the score walker never
            // restarts (found live on the 2026-07-14 bench, second QLab fire).
            if (~stream.notNil) { ~stream.close; ~stream.free; ~stream = nil; };

            if (~conductorAudioEnabled) {
                ~stream = Buffer.cueSoundFile(s, ~musicPath.standardizePath,
                    startFrame, ~fileChans, ~bufFrames);
                s.sync;
            };

            startT = SystemClock.seconds - seekT;
            if (~conductorAudioEnabled) {
                s.bind { ~playSynth = Synth(\playStream, [\buf, ~stream]) };
            };
            ~playStartT = SystemClock.seconds - seekT;
            ~lastBeatT  = nil;
            ~scheduleFromIndex.(idx, startT);
            "/airkit/seek % -> frame %, beat %".format(seekT, startFrame, idx).postln;
        }).play(AppClock);
    };

    ~beek = { |beat|
        var clamped = beat.clip(0, ~beats.size - 1);
        ~seek.(~beats[clamped]);
    };

    ~stop = {
        ~frozenT    = ~nowT.value;
        ~routine.stop;
        ~playSynth.free;
        ~playSynth  = nil;
        ~playStartT = nil;
    };

    OSCdef(\conductorGo,    { ~go.value },                       '/airkit/go');
    OSCdef(\conductorSeek,  { |msg| ~seek.(msg[1].asFloat) },    '/airkit/seek');
    OSCdef(\conductorBeek,  { |msg| ~beek.(msg[1].asInteger) },  '/airkit/beek');
    OSCdef(\conductorPause, { ~stop.value },                     '/airkit/pause');

    // --- [COTF] room state: idle | tuning | piece (see API.md) --------------
    // State != transport: entering piece does not start the score walker —
    // /airkit/go|seek do. Setups that never send /airkit/state keep today's
    // behaviour (~roomState stays \idle, nothing else changes).
    ~roomState = ~roomState ? \idle;
    OSCdef(\conductorState, { |msg|
        var new = msg[1].asString.asSymbol;
        var old = ~roomState;
        if ([\idle, \tuning, \piece].includes(new).not) {
            "unknown /airkit/state: %".format(msg[1]).postln;
        } {
            ~roomState = new;
            switch(new,
                \idle,   { ~stop.value },
                \tuning, { ~stop.value; ~scoreVoicePool = [69]; ~scoreVoiceAmp = 0.15; },
                \piece,  { }
            );
            ~devices.keysValuesDo { |port, d|
                if (d.env.notNil and: { d.enabled == true }) {
                    d.env.use { ~onState.(old, new) };
                };
            };
            "room state: % -> %".format(old, new).postln;
        };
    }, '/airkit/state');

    OSCdef(\conductorGetState, { |msg, time, addr|
        addr.sendMsg("/airkit/state/reply", ~roomState.asString);
    }, '/airkit/getState');

    ("conductor ready. % beats, % score entries, anchor=%."
        .format(~beats.size, ~scoreEntries.size, ~scoreAnchorBeat)).postln;
    "conductorController: send /airkit/go, /airkit/seek <t>, /airkit/beek <i>, /airkit/pause to drive transport.".postln;
});
