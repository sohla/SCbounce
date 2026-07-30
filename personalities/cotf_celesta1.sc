/*
gestures:    [beat, shake, tilt]
description: Pdef pattern firing per-note celesta sample synths on ~beatClock; single-note melody from score voice pool; gesture drives amp + octave; tuning uses \ptch for smooth pitch bend; odd-MIDI notes resample from nearest even-MIDI sample
sound:       high-register bell timbre; crisp attack, long ringing tail; spacious 8th-note grid so tails bloom; tuning bends smoothly into pitch via \ptch
pitch:       score voice pool wrapped to pitch class (root); \octave state-driven (idle random high, tuning 6, piece 5–7 from tilt, curtain low pair); \ptch = sample rate multiplier for continuous bend (tuning ramps 0.7 → 1.0 over 15 s); even-MIDI sample set + midiratio resample for odd notes
rhythm:      per-note; \dur state-driven (piece 2 = 8th grid, idle 3, tuning 2, curtain 4)
instruments: [Lumivox]
*/

var m = ~model;
var ob = ~outBus ? 0; // capture NOW — ~init bodies run under topEnvironment.use
var lastTime = 0;
var tuneTime = 0;
var group;   // dedicated Group for this personality's synths — see
             // concert_p_files.md §5 (Pdef personalities need this)

//------------------------------------------------------------
// note-name → MIDI parser. handles the "INSTR_ES_mf_<NOTE>.wav" naming
// convention used by both harp and celesta sample libraries. supports
// any file whose stem's last underscore-separated token matches
// [A-G](#|b)?[0-9] — e.g. C4, A#3, Bb5.
var noteToMidi = { |noteName|
		var pattern = "([A-G](#|b)?)([0-9])";
		var noteNames = "C C# D D# E F F# G G# A A# B";
		var parts, note, octave, noteIndex;
		parts = noteName.findRegexp(pattern);
		if(parts.size < 3, { Error("Invalid note format: %".format(noteName)).throw});
		note = parts[1][1];
		octave = parts[3][1].asInteger;
		note = note.replace("Cb", "B").replace("Db", "C#").replace("Eb", "D#")
								.replace("Fb", "E").replace("Gb", "F#").replace("Ab", "G#").replace("Bb", "A#");
	noteIndex = noteNames.split($ ).find([note]);
		(octave + 1) * 12 + noteIndex;
};

// sample folder. same naming convention as cotf_harp1 (INSTR_ES_mf_NOTE.wav):
//   harp   → ~/Music/cotf_samples/harp           (HA_ES_mf_*)
//   celesta → ~/Music/cotf_samples/Celesta_ES_mf (CE_ES_mf_*)
// only difference is the prefix and the pitch range (celesta is C3–C8 vs.
// harp's B0–C7). the .split($_).last extraction works unchanged because
// both libraries put the note token at the end of the underscore chain.
var folder = PathName("~/Music/cotf_samples/Celesta_ES_mf");
var samplesLib;

// Unique per-env event type — see cotf_harp1.sc for rationale.
// Shared \customEvent registrations clobber each other across devices.
var eventTypeName = (\customEvent_ ++ m.ptn).asSymbol;

//------------------------------------------------------------

m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.5;
m.rrateMassFilteredAttack = 0.7;
m.rrateMassFilteredDecay = 0.3;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
SynthDef(\stereoSampler, {|bufnum=0, out=0, amp=1, rate=1, ptch=1, start=0, pan=0, freq=440,
    attack=0.01, decay=0.1, sustain=0.3, release=1.2, gate=1,cutoff=20000, rq=1|
	// ptch multiplies the sample playback rate — continuous pitch bend.
	var lr = rate * BufRateScale.kr(bufnum) * ptch;
    var env = EnvGen.kr(Env.new([0, 1, 1, 0], [attack, sustain, release]), doneAction: 2);
	var sig = PlayBuf.ar(2, bufnum, rate: [lr, lr * 1.0017], startPos: start * BufFrames.kr(bufnum), loop: 0);
    sig = Balance2.ar(sig[0], sig[1], pan, amp * env);
    Out.ar(out, sig);
}).add;

//------------------------------------------------------------
~init = ~init <> {

	var result;

	// linear scan through samplesLib for the buffer whose midiNote matches
	// the request. returns nil if not found (which the caller must handle —
	// see the celesta pitch-range note above).
	var findSampleBuffer = {|note|
		var bufnum;
		samplesLib.do({|sample|
			if(sample.midiNote == note,{
				bufnum = sample.buffer;
			});
		});
		bufnum
	};

	// load every file in the folder, parsing the note token and computing
	// the target MIDI note. same shape as cotf_harp1 — the naming convention
	// is identical between the two libraries, so no per-library special-casing.
	samplesLib = folder.entries.collect({ |path|
		var note = path.fileNameWithoutExtension.split($_).last;
		var buffer = Buffer.read(s, path.fullPath, action:{ |buf|
			postf("buffer alloc [%] \n", buf);
		});
		postf("loading sample : % \n", path.fileNameWithoutExtension);
		(name: path.fileNameWithoutExtension, buffer: buffer, midiNote: noteToMidi.(note))
	});

	// custom event handler — inherited from cotf_harp1 with no changes.
	// odd-MIDI requests get resampled up a semitone from the nearest lower
	// even-MIDI sample (rate = 1.midiratio). works here because celesta,
	// like harp, is provided as a mostly-even-MIDI sample set.
	// asInteger because SC's default ~octave is 5.0 (Float); the addition
	// promotes ~note to Float and .odd is not defined on Float.
	Event.addEventType(eventTypeName, {|e|
		~note = (~note + ~root + (12 * ~octave)).asInteger;
		if(~note.odd,{
			~bufnum = findSampleBuffer.(~note-1);
				~rate = 1.midiratio;
		},{
			~bufnum = findSampleBuffer.(~note);
				~rate = 1;
		});
			~type = \note;
			currentEnvironment.play;
	});

	topEnvironment.use{
		group = Group.new;

		Pdef(m.ptn,
			Pbind(
				\instrument, \stereoSampler,
				\out, ob,
				\group, group,   // route every event's synth into our group
				\type, eventTypeName,
				\note, 0,
				\root, Pfunc { ~scoreVoicePool.choose.wrap(0,11).asInteger},
			);
		);

		Pdef(m.ptn).play(~beatClock, quant: ~scoreBeatsPerBar * ~scoreEventsPerBeat);
		// cotf: seed envir so a stickless seat is silent — SC's Event default
		// amp is 0.1, and the ~*Next tick hooks (the only writers of \amp) run
		// only while the seat's device is enabled. Envir .set, not a Pbind key:
		// Pbind keys override the envir and would defeat the hooks' .set.
		Pdef(m.ptn).set(\amp, 0);
		// Default dur = 2 (8th-note grid, half rate of harp/marimba) — gives
		// the celesta's bell tail room to bloom. State hooks override.
		Pdef(m.ptn).set(\dur, 2);

		// Reload guard: ~onRoomState only fires on state *change*, so on
		// a reload during \tuning nothing would pause the freshly-started
		// Pdef and you'd get the pattern layered on top of ~tuningNext
		// single hits. Mirror ~onRoomState's \tuning branch here — pause
		// immediately (quant means no events fire in between) and capture
		// tuneTime so the \ptch ramp resumes from now.
		if (~roomState == \tuning) {
			Pdef(m.ptn).pause;
			tuneTime = TempoClock.beats;
		};

	};

	// ~onResync lives in d.env (per-device dispatch from conductor's
	// beatSync OSCdef — no cross-device clobber). Body wraps in
	// topEnvironment.use so ~beatClock / ~roomState / ~scoreBeatsPerBar
	// resolve. On re-anchor: stop Pdef + freeAll; restart only outside
	// \tuning (tuning uses single-hit ~tuningNext instead).
	~onResync = { |idx|
		topEnvironment.use {
			Pdef(m.ptn).stop;
			s.bind { group.freeAll };
			if (~roomState != \tuning) {
				Pdef(m.ptn).play(~beatClock, quant: ~scoreBeatsPerBar * ~scoreEventsPerBeat);
			};
		};
	};
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	Pdef(m.ptn).remove;
	// Remove our per-env event type from Event.eventTypes so reloads don't
	// accumulate stale entries.
	Event.eventTypes.removeAt(eventTypeName);

	// Kill synths first (latency-safe /g_freeAll), then free sample
	// buffers — order matters so no PlayBuf is still reading from a
	// buffer we're about to /b_free. fork so s.sync actually waits.
	// Both cleanups are notNil-guarded so ~deinit stays idempotent —
	// titleView off+on calls unLoadPersonality then loadPersonality,
	// each of which fires ~deinit on the same env, so without the
	// samplesLib guard we'd .free every buffer twice ("Cannot call
	// free on a Buffer that has been freed"). Match the group guard.
	fork {
		if (group.notNil) {
			s.bind { group.freeAll };
			s.sync;
			group.free;
			group = nil;
		};
		if (samplesLib.notNil) {
			samplesLib.do({|sample|
				postf("buffer dealloc [%] \n", sample.buffer);
				sample.buffer.free;
				s.sync;
			});
			samplesLib = nil;
		};
	};
};

//------------------------------------------------------------
// ~next = {|d| };  // state-gated ticks handle everything

//------------------------------------------------------------
// Room-state routing — Pdef stays playing across all states; per-state
// amp is set by the state-gated ticks so we only hear the celesta during
// \piece. \silent handled one-shot here (no ~silentNext). Capture
// tuneTime on \tuning entry for any time-based envelope.
~onRoomState = {|ctx|
	// Dispatched under d.env.use in conductorController, so ~beatClock /
	// ~scoreBeatsPerBar / ~scoreEventsPerBeat resolve to nil here — wrap
	// the whole switch in topEnvironment.use so score vars resolve.
	// m / group / tuneTime are lexical vars, unaffected by env swap.
	topEnvironment.use {
		switch(ctx.state,
			\idle,    {
				Pdef(m.ptn).resume(~beatClock, quant: ~scoreBeatsPerBar * ~scoreEventsPerBeat);
			},
			\tuning,  {
				tuneTime = TempoClock.beats;
				Pdef(m.ptn).pause;
				s.bind { group.freeAll };
				m.accelMassFilteredAttack = 0.99;
				m.accelMassFilteredDecay = 0.99;
			},
			\piece,   {
				m.accelMassFilteredAttack = 0.99;
				m.accelMassFilteredDecay = 0.5;
				Pdef(m.ptn).resume(~beatClock, quant: ~scoreBeatsPerBar * ~scoreEventsPerBeat);
			},
			\curtain, {
				Pdef(m.ptn).resume(~beatClock, quant: ~scoreBeatsPerBar * ~scoreEventsPerBeat);
			},
			\silent,  {
				Pdef(m.ptn).set(\amp, 0);
			}
		);
	};
};

//------------------------------------------------------------
~idleNext = {|d, ctx|
	var amp = m.accelMassFiltered.lincurve(0, 2.0, -70, -20, -4);
	Pdef(m.ptn).set(\amp, amp.dbamp);
	Pdef(m.ptn).set(\octave, [5, 6, 7].choose);
	Pdef(m.ptn).set(\dur, 3);
	Pdef(m.ptn).set(\ptch, 1);
};

~tuningNext = {|d, ctx|
	// Pdef is paused for tuning (see ~onRoomState). Instead, fire a
	// single celesta hit when accelMassFiltered crosses the threshold —
	// throttled to once every 2 s via lastTime (harp1 ~idleNext idiom).
	// \ptch rides the 0.7 → 1.0 ramp over 15 s so each triggered hit
	// sounds progressively closer to true pitch.
	var amp = m.accelMassFiltered.lincurve(0, 2.0, -70, -20, -4);
	var tt = 15.0;
	var elapsed = TempoClock.beats - tuneTime;
	var ptch = if (elapsed < tt) {
		(elapsed / tt).linlin(0, 1, 1.2, 1.0)
	} { 1.0 };

	if (m.accelMassFiltered > 0.5, {
		if (TempoClock.beats > (lastTime + 0.2), {
			// Fire via Event.play with our per-env event type so the
			// even/odd sample lookup + midiratio resample logic in ~init's
			// handler runs unchanged. Target = A5 (root 9 + octave 6).
			(
				instrument: \stereoSampler,
				type:       eventTypeName,
				out:        ob,
				group:      group,
				note:       0,
				root:       9,
				octave:     6,
				amp:        amp.dbamp,
				ptch:       ptch,
			).play;
			lastTime = TempoClock.beats;
		});
	});
};

~pieceNext = {|d, ctx|
	var amp = m.accelMassFiltered.lincurve(0, 2.0, -70, -10, -1);
	var oct = (d.sensors.gyroEvent.y / pi.half).lincurve(-1, 1, 6, 8, 1).asInteger;
	Pdef(m.ptn).set(\amp, amp.dbamp);
	Pdef(m.ptn).set(\octave, oct);
	Pdef(m.ptn).set(\dur, 2);
	Pdef(m.ptn).set(\ptch, 1);
};

~curtainNext = {|d, ctx|
	var amp = m.rrateMassFiltered.lincurve(0, 1.0, -70, -30, -4);
	Pdef(m.ptn).set(\amp, amp.dbamp);
	Pdef(m.ptn).set(\octave, [7, 8].choose);
	Pdef(m.ptn).set(\dur, 4);
	Pdef(m.ptn).set(\ptch, 1);
};

//------------------------------------------------------------
~onTick    = {|ctx| };
~onHalf    = {|ctx| };
~onBeat    = {|ctx| };
~onBar     = {|ctx| };
~onPhrase  = {|ctx| };
~onSection = {|ctx| };
~onChord   = {|ctx| };
~onKey     = {|ctx| };
~onScale   = {|ctx| };

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|
	[(d.sensors.gyroEvent.y / pi.half)];//up down
};
