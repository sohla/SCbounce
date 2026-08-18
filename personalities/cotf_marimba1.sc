/*
gestures:    [beat, shake, tilt]
description: Pdef pattern firing per-note marimba sample synths on ~beatClock; single-note melody from score voice pool; gesture drives amp + octave; tuning uses \ptch for smooth pitch bend; nearest-sample lookup for pitch class
sound:       bright African marimba mallet strikes; mid-to-upper register (F2..B6); crisp transient, natural tail; tuning bends smoothly into pitch via \ptch
pitch:       score voice pool wrapped to pitch class (root); \octave state-driven (idle random, tuning 5, piece 6–8 from tilt, curtain low pair); \ptch = sample rate multiplier for continuous bend (tuning ramps 0.7 → 1.0 over 15 s)
rhythm:      per-note; \dur state-driven (piece 1 = 16th grid, idle 2, tuning 2, curtain 3)
instruments: [Gravitone]
*/

var m = ~model;
var ob = ~outBus ? 0; // capture NOW — ~init bodies run under topEnvironment.use
var lastTime = 0;
var tuneTime = 0;
var group;   // dedicated Group for this personality's synths — see
             // concert_p_files.md §5 (Pdef personalities need this)

//------------------------------------------------------------
// note-name → MIDI parser. same as cotf_harp1 / cotf_celesta1 — takes
// a normalized "<letter><octave>" string like "F2" or "A#3" and returns
// the MIDI note number. we normalize the marimba's "<octave><letter>"
// filename token into this shape before calling.
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

// marimba-specific stem parser. filename shape:
//   "Marimba ln <dyn> l<N>x  <octave><note>.aif"
// where <dyn> = p|mf|f, <N> = 1..3, <octave> = 2..6, <note> = one of:
//   " d" " f"   — with a leading space between digit and letter
//   "a"  "b"    — attached to the digit with no space
// two structural differences vs. harp/celesta parsing:
//   (a) space-delimited, not underscore-delimited
//   (b) octave-then-letter order, not letter-then-octave
// regex ([0-9])[ ]*([a-g])$ handles both spacing variants and captures
// octave + letter separately. we then reorder + uppercase to feed noteToMidi.
var parseMarimbaNote = { |fileStem|
	var pattern = "([0-9])[ ]*([a-g])$";
	var parts = fileStem.findRegexp(pattern);
	var octave, letter, noteStr;
	if(parts.size < 3, { Error("Invalid marimba note format: %".format(fileStem)).throw });
	octave = parts[1][1];                        // digit as string
	letter = parts[2][1].toUpper;                // letter, uppercased
	noteStr = letter ++ octave;                  // "F" ++ "2" → "F2"
	noteToMidi.(noteStr)
};

// filter selector for the folder scan. picks one dynamic + one layer to
// yield 18 unique pitches (F2..B6, minus D2 and A5 which aren't in the
// library). change to "Marimba ln f l1x" for a louder set, "l2x"/"l3x"
// for other round-robin takes.
var sampleFilter = "Marimba ln mf l1x";
var folder = PathName("~/Music/cotf_samples/African Marimba");
var samplesLib;
var loading = false;   // true while ~init is waiting on the sample reads;
                       // ~deinit clears it so a load in flight bails out
                       // instead of building an unreachable Pdef. See ~init.

// Unique per-env event type — see cotf_harp1.sc for rationale.
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
    attack=0.01, decay=0.1, sustain=0.3, release=1.2, gate=1, cutoff=20000, rq=1|
	// ptch multiplies the sample playback rate — continuous pitch bend.
	var lr = rate * BufRateScale.kr(bufnum) * ptch;
	var env = EnvGen.kr(Env.new([0, 1, 1, 0], [attack, sustain, release]), doneAction: 2);
	var sig = PlayBuf.ar(2, bufnum, rate: [lr, lr * 1.0017], startPos: start * BufFrames.kr(bufnum), loop: 0);
	sig = Balance2.ar(sig[0], sig[1], pan, amp * env);
	Out.ar(out, sig);
}).add;

//------------------------------------------------------------
~init = ~init <> {

	// nearest-sample lookup. unlike harp/celesta (whose sample sets are
	// mostly even-MIDI so an odd-note request can always resample up a
	// semitone from note-1), the marimba has only D/F/A/B per octave —
	// that's 4 of 12 chromatic pitches, mixed odd and even. so the
	// odd/even trick doesn't work. instead: find the closest available
	// MIDI note in samplesLib and compute the semitone diff → rate.
	// worst-case shift is ~2 semitones (D-F gap is 3, F-A is 4, A-B is 2,
	// B-D is 3), well within acceptable pitch-shift range for a mallet
	// instrument where the transient is more identifying than the tail.
	var findClosestSample = { |targetMidi|
		var closest = samplesLib.minItem({|sample| (sample.midiNote - targetMidi).abs });
		var semitoneDiff = targetMidi - closest.midiNote;
		(buffer: closest.buffer, rate: semitoneDiff.midiratio)
	};

	// scan folder, filter to the chosen dynamic+layer, parse each name.
	loading = true;

	samplesLib = folder.entries
		.select({|path| path.fileName.contains(sampleFilter) })
		.collect({ |path|
			var midiNote = parseMarimbaNote.(path.fileNameWithoutExtension);
			var buffer = Buffer.read(s, path.fullPath, action:{|buf|
				postf("buffer alloc [%] \n", buf);
			});
			postf("loading sample : % (midi %) \n", path.fileNameWithoutExtension, midiNote);
			(name: path.fileNameWithoutExtension, buffer: buffer, midiNote: midiNote)
		});

	s.sync;
	postf("[marimba1] all buffers loaded (%) \n", samplesLib.size);

	if(loading.not or: { samplesLib.isNil },{
		postf("[marimba1] load cancelled — unloaded while samples were loading \n");
	},{
		// Name any file that didn't come back rather than failing silently.
		// We still build: one bad sample costs its own notes, not the seat.
		samplesLib.do({ |sample|
			if(sample.buffer.numFrames.isNil or: { sample.buffer.numFrames == 0 },{
				postf("[marimba1] sample failed to load : % \n", sample.name);
			});
		});

		// custom event handler — same idea as harp/celesta but the ~bufnum /
		// ~rate assignment goes through findClosestSample instead of the
		// odd/even branch. otherwise identical: compute the target MIDI note,
		// set the buffer and rate, switch \type back to \note and re-dispatch.
		Event.addEventType(eventTypeName, {|e|
			var found;
			~note = ~note + ~root + (12 * ~octave);
			found = findClosestSample.(~note);
			~bufnum = found.buffer;
			~rate = found.rate;
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
					\root, Pfunc { ~scoreVoicePool.choose.wrap(0,11).asInteger - 24 },
				);
			);

			Pdef(m.ptn).play(~beatClock, quant: ~scoreBeatsPerBar * ~scoreEventsPerBeat);
			// cotf: seed envir so a stickless seat is silent — SC's Event default
			// amp is 0.1, and the ~*Next tick hooks (the only writers of \amp) run
			// only while the seat's device is enabled. Envir .set, not a Pbind key:
			// Pbind keys override the envir and would defeat the hooks' .set.
			Pdef(m.ptn).set(\amp, 0);
			Pdef(m.ptn).set(\dur, 1);   // default 16th grid; state hooks override

		};

		// ~onResync in d.env (per-device dispatch — no cross-device clobber).
		// Body in topEnvironment.use so ~beatClock etc. resolve.
		~onResync = { |idx|
			topEnvironment.use {
				Pdef(m.ptn).stop;
				s.bind { group.freeAll };
				Pdef(m.ptn).play(~beatClock, quant: ~scoreBeatsPerBar * ~scoreEventsPerBeat);
			};
		};
	});
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	loading = false;

	Pdef(m.ptn).remove;
	Event.eventTypes.removeAt(eventTypeName);

	// Kill synths first (latency-safe /g_freeAll), then free sample
	// buffers — order matters so no PlayBuf is still reading from a
	// buffer we're about to /b_free. fork so s.sync actually waits.
	// Idempotent: notNil guards let ~deinit fire twice safely.
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
// amp is set by the state-gated ticks so we only hear the marimba during
// \piece. \silent handled one-shot here (no ~silentNext). Capture
// tuneTime on \tuning entry for any time-based envelope.
~onRoomState = {|ctx|
	switch(ctx.state,
		\idle,    { },
		\tuning,  { tuneTime = TempoClock.beats },
		\piece,   { },
		\curtain, { },
		\silent,  { Pdef(m.ptn).set(\amp, 0); }
	);
};

//------------------------------------------------------------
~idleNext = {|d, ctx|
	var amp = m.rrateMassFiltered.lincurve(0, 1.0, -60, -20, -4);
	Pdef(m.ptn).set(\amp, amp.dbamp);
	Pdef(m.ptn).set(\octave, [5, 6, 7].choose);
	Pdef(m.ptn).set(\dur, 2);
	Pdef(m.ptn).set(\ptch, 1);
};

~tuningNext = {|d, ctx|
	var amp = m.rrateMassFiltered.lincurve(0, 1.0, -70, -22, -4);
	var tt = 15.0;
	var elapsed = TempoClock.beats - tuneTime;

	Pdef(m.ptn).set(\amp, amp.dbamp);
	Pdef(m.ptn).set(\octave, 5);
	Pdef(m.ptn).set(\dur, 2);

	if (elapsed < tt, {
		Pdef(m.ptn).set(\ptch, (elapsed / tt).linlin(0, 1, 0.7, 1.0));
	}, {
		Pdef(m.ptn).set(\ptch, 1);
	});
};

~pieceNext = {|d, ctx|
	var amp = m.accelMassFiltered.lincurve(0, 1.4, -50, -5, -1);
	var oct = (d.sensors.gyroEvent.y / pi.half).lincurve(-1, 1, 6, 8, 1).asInteger;
	Pdef(m.ptn).set(\amp, amp.dbamp);
	Pdef(m.ptn).set(\octave, oct);
	Pdef(m.ptn).set(\dur, 1);
	Pdef(m.ptn).set(\ptch, 1);
};

~curtainNext = {|d, ctx|
	var amp = m.rrateMassFiltered.lincurve(0, 1.0, -70, -28, -4);
	Pdef(m.ptn).set(\amp, amp.dbamp);
	Pdef(m.ptn).set(\octave, [4, 5].choose);
	Pdef(m.ptn).set(\dur, 3);
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
	[(d.sensors.gyroEvent.y / pi.half)];   // up-down tilt drives \octave
};
