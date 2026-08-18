/*
gestures:    [beat, tilt, shake]
description: Pdef pattern with Pslide melody (harp2 idiom) drawing indices from ~scoreVoicePool; \range (from ~pieceNext's accelMassFiltered mapping) sets how many top-of-pool notes cycle through per group. Idle/tuning/curtain use range=1 (single-note ostinato); piece opens the slide window with motion.
sound:       Italian harpsichord; still device → single held pitch; active motion → expanding melodic sweep across the pool; state-driven amp/octave/dur
pitch:       ~scoreVoicePool wrapped to pitch class, indexed by Pslide's \slideIdx. Root = 0 (note carries absolute PC). \octave state-driven (idle 4–6, tuning 5, piece 4–6 from tilt, curtain 3–4). \ptch = sample rate multiplier for continuous bend (tuning ramps 0.7 → 1.0 over 15 s). Sparse sample set covered by findClosestSample.
rhythm:      per-note on ~beatClock; \dur state-driven; \range 1..8 in piece from accelMassFiltered (harp2 pattern)
instruments: [Clavelium]
*/

var m = ~model;
var ob = ~outBus ? 0;
var lastTime = 0;
var tuneTime = 0;
var group;

//------------------------------------------------------------
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

// Harpsichord filename parser — see cotf_harpsichord1.sc for the shape trace.
var parseHarpsichordNote = { |fileStem|
	var pattern = "([0-9])[ ]*([a-g](#|b)?)$";
	var parts = fileStem.findRegexp(pattern);
	var octave, note, noteStr;
	if(parts.size < 3, { Error("Invalid harpsichord note format: %".format(fileStem)).throw });
	octave = parts[1][1];
	note = parts[2][1].toUpper;
	noteStr = note ++ octave;
	noteToMidi.(noteStr)
};

var folder = PathName("~/Music/cotf_samples/Harpsichord");
var samplesLib;
var loading = false;   // true while ~init is waiting on the sample reads;
                       // ~deinit clears it so a load in flight bails out
                       // instead of building an unreachable Pdef. See ~init.

// Unique per-env event type — see cotf_harp1.sc for rationale.
var eventTypeName = (\customEvent_ ++ m.ptn).asSymbol;

// Cap for the Pslide window. Pool.size varies per beat; range higher than
// pool.size just wraps back to index 0 (wrapAt), which is musically the same
// as a smaller range. 8 matches harp2's notes.size and gives enough headroom.
var maxRange = 8;

//------------------------------------------------------------

m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.5;
m.rrateMassFilteredAttack = 0.7;
m.rrateMassFilteredDecay = 0.3;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
SynthDef(\stereoSamplerH, {|bufnum=0, out=0, amp=1, rate=1, ptch=1, start=0, pan=0, freq=440,
    attack=0.01, decay=0.1, sustain=0.3, release=1.2, gate=1, cutoff=20000, rq=1|
	var lr = rate * BufRateScale.kr(bufnum) * ptch;
	var env = EnvGen.kr(Env.new([0, 1, 1, 0], [attack, sustain, release]), doneAction: 2);
	var sig = PlayBuf.ar(2, bufnum, rate: [lr, lr * 1.0017] - 0.027, startPos: start * BufFrames.kr(bufnum), loop: 0);
	sig = Balance2.ar(sig[0], sig[1], pan, amp * env);
	Out.ar(out, sig);
}).add;

//------------------------------------------------------------
~init = ~init <> {

	// Sparse-set nearest-neighbour lookup (dulcimer/marimba pattern).
	var findClosestSample = { |targetMidi|
		var closest = samplesLib.minItem({|sample| (sample.midiNote - targetMidi).abs });
		var semitoneDiff = targetMidi - closest.midiNote;
		(buffer: closest.buffer, rate: semitoneDiff.midiratio)
	};

	loading = true;

	samplesLib = folder.entries
		.collect({ |path|
			var midiNote = parseHarpsichordNote.(path.fileNameWithoutExtension);
			var buffer = Buffer.read(s, path.fullPath, action:{|buf|
				postf("buffer alloc [%] \n", buf);
			});
			postf("loading sample : % (midi %) \n", path.fileNameWithoutExtension, midiNote);
			(name: path.fileNameWithoutExtension, buffer: buffer, midiNote: midiNote)
		});

	s.sync;
	postf("[harpsichord2] all buffers loaded (%) \n", samplesLib.size);

	if(loading.not or: { samplesLib.isNil },{
		postf("[harpsichord2] load cancelled — unloaded while samples were loading \n");
	},{
		// Name any file that didn't come back rather than failing silently.
		// We still build: one bad sample costs its own notes, not the seat.
		samplesLib.do({ |sample|
			if(sample.buffer.numFrames.isNil or: { sample.buffer.numFrames == 0 },{
				postf("[harpsichord2] sample failed to load : % \n", sample.name);
			});
		});

		// customEvent handler — scalar path only (harpsichord2 plays single
		// notes, not chord arrays). If a piece-time chord variant is ever needed
		// swap in cotf_harpsichord1's array-aware handler.
		Event.addEventType(eventTypeName, {|e|
			var target = ~note + ~root + (12 * ~octave);
			var found = findClosestSample.(target);
			~bufnum = found.buffer;
			~rate = found.rate;
			~type = \note;
			currentEnvironment.play;
		});

		topEnvironment.use{
			group = Group.new;

			Pdef(m.ptn,
				Pbind(
					\instrument, \stereoSamplerH,
					\out, ob,
					\group, group,
					\type, eventTypeName,

					// harp2's Pslide idiom — dynamic \range windows a slice off
					// the head of the index list. step=0 keeps the window anchored
					// at index 0, so range=r cycles indices 0..r-1 repeatedly.
					// range=1 is a single-note drone (top of pool); range=8 sweeps
					// the top 8 pool slots.
					\slideIdx, Pslide(Array.series(maxRange, 0, 1), inf, Pkey(\range), 0, 0),

					// Look up the pool at slideIdx, reduce to pitch class. \root=0
					// so the customEvent math becomes pc + 12*~octave — ~octave
					// alone controls register. wrapAt handles pool.size < range
					// gracefully (small pools just repeat).
					\note, Pfunc { |e|
						var pool = (~scoreVoicePool ? [69]).asArray;
						var idx = (e[\slideIdx] ? 0).asInteger;
						pool.wrapAt(idx).asInteger.mod(12)
					},

					\root, 0,
					\args, #[]
				);
			);

			Pdef(m.ptn).play(~beatClock, quant: ~scoreBeatsPerBar * ~scoreEventsPerBeat);
			// cotf: seed envir so a stickless seat is silent — SC's Event default
			// amp is 0.1, and the ~*Next tick hooks (the only writers of \amp) run
			// only while the seat's device is enabled. Envir .set, not a Pbind key:
			// Pbind keys override the envir and would defeat the hooks' .set.
			Pdef(m.ptn).set(\amp, 0);
			Pdef(m.ptn).set(\dur, 1);
			Pdef(m.ptn).set(\range, 1);   // conservative default; state ticks override
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
	var amp = m.accelMassFiltered.lincurve(0, 2.2, -70, -4, -2);
	var dur = m.accelMassFiltered.lincurve(0, 2.5, 4, 1, -2).asInteger;

	Pdef(m.ptn).set(\amp, amp.dbamp);
	Pdef(m.ptn).set(\octave, [4,4.58, 5, 5.59, 6,6.17,7].choose);
	Pdef(m.ptn).set(\dur, dur);
	Pdef(m.ptn).set(\range, 2);
	Pdef(m.ptn).set(\ptch, 1);
};

~tuningNext = {|d, ctx|
	var amp = m.accelMassFiltered.lincurve(0, 2.0, -70, -15, -4);
	var tt = 15.0;
	var elapsed = TempoClock.beats - tuneTime;

	Pdef(m.ptn).set(\amp, amp.dbamp);
	Pdef(m.ptn).set(\octave, 5);
	Pdef(m.ptn).set(\dur, 2);
	Pdef(m.ptn).set(\range, 1);

	if (elapsed < tt, {
		Pdef(m.ptn).set(\ptch, (elapsed / tt).linlin(0, 1, 0.7, 1.0));
	}, {
		Pdef(m.ptn).set(\ptch, 1);
	});
};

// THE POINT: accelMassFiltered → \range → Pslide window width.
// Same shape as harp2's ~next: still device = 1 note; harder motion
// opens the slide up to maxRange (8) pool notes cycled per group.
~pieceNext = {|d, ctx|
	var amp = m.accelMassFiltered.lincurve(0, 2.0, -45, -8, -1);
	var oct = (d.sensors.gyroEvent.y / pi.half).lincurve(-1, 1, 5, 9, 1).asInteger;
	var range = m.accelMassFiltered.lincurve(0, 2.5, 1, maxRange, -1).floor;
	var dur = m.accelMassFiltered.lincurve(0, 2.5, 2, 1, -2).asInteger;

	Pdef(m.ptn).set(\amp, amp.dbamp);
	Pdef(m.ptn).set(\octave, oct);
	Pdef(m.ptn).set(\dur, dur);
	Pdef(m.ptn).set(\range, range);
	Pdef(m.ptn).set(\ptch, 1);
};

~curtainNext = {|d, ctx|
	var amp = m.rrateMassFiltered.lincurve(0, 1.0, -70, -30, -4);
	Pdef(m.ptn).set(\amp, amp.dbamp);
	Pdef(m.ptn).set(\octave, [3, 4].choose);
	Pdef(m.ptn).set(\dur, 4);
	Pdef(m.ptn).set(\range, 1);
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
~plot = {|d,p| [m.accelMassFiltered] };
