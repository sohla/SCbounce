/*
gestures:    [beat, shake, tilt]
description: Pdef pattern firing per-note harpsichord sample synths on ~beatClock; idle/tuning/curtain play a single melody note from ~scoreVoicePool (harp1 style); piece plays a 3-note chord voiced from fitted_notes (marimba2 pattern); tuning uses \ptch for smooth pitch bend
sound:       Italian harpsichord; bright plectrum attack, gently decaying tail; idle single-note ostinato, piece 3-note chord strikes on beat
pitch:       Idle/tuning/curtain: score voice pool wrapped to pitch class (root); piece: fitted_notes-driven chord voicing (offsets around baseMidi 60, capped at 3 tones); \octave state-driven (idle 4–6, tuning 5, piece 4–6 from tilt, curtain 3–4); \ptch = sample rate multiplier for continuous bend (tuning ramps 0.7 → 1.0 over 15 s); sparse sample set (A/A#/B/C#/E/G/G# across oct 2–6) covered by findClosestSample (≤3 semitone shift)
rhythm:      per-note on ~beatClock; \dur state-driven (idle 2, tuning 2, piece 1, curtain 4)
instruments: [Clavelium]
*/

var m = ~model;
var ob = ~outBus ? 0;
var lastTime = 0;
var tuneTime = 0;
var group;

//------------------------------------------------------------
// shared: note-name → MIDI parser.
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

// Harpsichord filename parser. Filename shape:
//   "Harpsi Italian  <octave>[ ]?<letter>[#|b]?"
// Examples:
//   "Harpsi Italian  2a"    → octave 2, letter a               → A2
//   "Harpsi Italian  3 c#"  → octave 3, letter c, accidental # → C#3
//   "Harpsi Italian  3a#"   → octave 3, letter a, accidental # → A#3
//   "Harpsi Italian  6 g#"  → octave 6, letter g, accidental # → G#6
// Two structural differences vs harp/celesta libs:
//   (a) space-delimited stem with a double-space after "Italian"
//   (b) octave-then-letter order, with an OPTIONAL space between them
// The regex ([0-9])[ ]*([a-g](#|b)?)$ handles both spacing variants
// and captures octave + note-with-accidental in one shot. We then
// uppercase + reorder to feed noteToMidi.
// Coverage: sparse — 17 files, ~4 pitches per octave (mostly minor
// triad + Bb + G/G#). Missing pitches get resampled via
// findClosestSample; gaps are ≤3 semitones so no chipmunk-shift.
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

// Unique per-env event type — see cotf_harp1.sc for rationale.
var eventTypeName = (\customEvent_ ++ m.ptn).asSymbol;

//------------------------------------------------------------
// Voice a chord from fitted_notes — direct port of marimba2's
// chordVoicedOffsets (§16). Returns 3 offsets from baseMidi.
//   1. Union both halves of fitted_notes so half-bar sparsity doesn't
//      thin voicings.
//   2. Dedupe by pitch class (fitted_notes is octave-doubled).
//   3. Octave-fold each unique pitch into [baseMidi ± 12].
//   4. Sort ascending, cap at 3 tones, return offsets from baseMidi.
// Fallback: A major triad offsets [-3, 1, 4] when the score isn't
// loaded or fitted_notes is empty.
var chordVoicedOffsets = { |baseMidi = 60|
	var params, halves, raw, pitches, seen, unique, voiced, result;
	if(~paramsAtBeat.isNil or: { ~beats.isNil }) {
		result = [-3, 1, 4]
	} {
		params = ~paramsAtBeat.(~beatClock.beats.asInteger.clip(0, ~beats.size - 1));
		halves = params !? { |p| p["fitted_notes"] };
		raw = halves !? { |h| h.flatten };
		if(raw.isNil or: { raw.size == 0 }) {
			result = [-3, 1, 4]
		} {
			pitches = raw.collect(_.asInteger);
			seen = Set.new;
			unique = List.new;
			pitches.do { |mi|
				var pc = mi % 12;
				if(seen.includes(pc).not) {
					seen.add(pc);
					unique.add(mi);
				};
			};
			voiced = unique.asArray.collect({ |mi|
				var v = mi;
				var lo = baseMidi - 12;
				var hi = baseMidi + 12;
				while { v < lo } { v = v + 12 };
				while { v > hi } { v = v - 12 };
				v;
			});
			voiced = voiced.sort;
			voiced = voiced.copyRange(0, (voiced.size - 1).min(2));   // cap at 3
			result = voiced.collect({ |mi| mi - baseMidi })
		};
	};
	result
};

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
	// ptch multiplies playback rate — continuous pitch bend.
	var lr = rate * BufRateScale.kr(bufnum) * ptch;
	var env = EnvGen.kr(Env.new([0, 1, 1, 0], [attack, sustain, release]), doneAction: 2);
	var sig = PlayBuf.ar(2, bufnum, rate: [lr, lr * 1.0017] - 0.027, startPos: start * BufFrames.kr(bufnum), loop: 0);
	sig = Balance2.ar(sig[0], sig[1], pan, amp * env);
	Out.ar(out, sig);
}).add;

//------------------------------------------------------------
~init = ~init <> {

	// nearest-sample lookup. sparse coverage → semitone-shift the
	// closest available buffer via midiratio. worst-case gap ≤3
	// semitones, well within acceptable pitch-shift range for a
	// plectrum instrument.
	var findClosestSample = { |targetMidi|
		var closest = samplesLib.minItem({|sample| (sample.midiNote - targetMidi).abs });
		var semitoneDiff = targetMidi - closest.midiNote;
		(buffer: closest.buffer, rate: semitoneDiff.midiratio)
	};

	// scan the folder, parse each stem, load buffer.
	samplesLib = folder.entries
		.collect({ |path|
			var midiNote = parseHarpsichordNote.(path.fileNameWithoutExtension);
			var buffer = Buffer.read(s, path.fullPath, action:{|buf|
				postf("buffer alloc [%] \n", buf);
			});
			postf("loading sample : % (midi %) \n", path.fileNameWithoutExtension, midiNote);
			(name: path.fileNameWithoutExtension, buffer: buffer, midiNote: midiNote)
		});

	// customEvent handler — array-aware (from marimba2). when ~note is
	// an array (a chord), findClosestSample runs per element and produces
	// arrays for ~bufnum/~rate; the \note dispatch multichannel-expands
	// into one synth per element, all firing at the same clock instant.
	Event.addEventType(eventTypeName, {|e|
		var target = ~note + ~root + (12 * ~octave);
		if(target.isArray) {
			~bufnum = target.collect({|n| findClosestSample.(n).buffer });
			~rate   = target.collect({|n| findClosestSample.(n).rate });
		} {
			var found = findClosestSample.(target);
			~bufnum = found.buffer;
			~rate = found.rate;
		};
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
				// \strum, 0.4,

				// ~note branches on room state:
				//   \piece → 3-note chord voicing from fitted_notes
				//            (offsets relative to baseMidi 60, ~root = 0)
				//   else   → single note; ~note = 0, ~root drives it
				\note, Pfunc {
					if (topEnvironment[\roomState] == \piece) {
						chordVoicedOffsets.(60)
					} {
						0
					}
				},

				// ~root branches too — in piece the chord voicing carries
				// absolute pitch classes so root = 0; otherwise use a
				// voice-pool pitch class (harp1 style).
				\root, Pfunc {
					if (topEnvironment[\roomState] == \piece) {
						0
					} {
						(~scoreVoicePool ? [69]).choose.wrap(0, 11).asInteger
					}
				},

				\args, #[]
			);
		);

		Pdef(m.ptn).play(~beatClock, quant: ~scoreBeatsPerBar * ~scoreEventsPerBeat);
		// cotf: seed envir so a stickless seat is silent — SC's Event default
		// amp is 0.1, and the ~*Next tick hooks (the only writers of \amp) run
		// only while the seat's device is enabled. Envir .set, not a Pbind key:
		// Pbind keys override the envir and would defeat the hooks' .set.
		Pdef(m.ptn).set(\amp, 0);
		Pdef(m.ptn).set(\dur, 2);   // sensible default; state ticks override
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
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	Pdef(m.ptn).remove;
	Event.eventTypes.removeAt(eventTypeName);

	// Kill synths first (latency-safe /g_freeAll), then free sample
	// buffers. Idempotent: notNil guards handle double-~deinit safely.
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
// State ticks — harp1 pattern (amp/octave/dur/ptch per state).
~idleNext = {|d, ctx|
	var amp = m.accelMassFiltered.lincurve(0, 2.0, -70, -20, -4);
	Pdef(m.ptn).set(\amp, amp.dbamp);
	Pdef(m.ptn).set(\octave, [4, 5, 6].choose);
	Pdef(m.ptn).set(\dur, 2);
	Pdef(m.ptn).set(\ptch, 1.5);
};

~tuningNext = {|d, ctx|
	var amp = m.accelMassFiltered.lincurve(0, 2.0, -70, -19, -4);
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
	var amp = m.accelMassFiltered.lincurve(0, 2.0, -75, -12, -1);
	var oct = (d.sensors.gyroEvent.y / pi.half).lincurve(-1, 1, 7, 9, 1).asInteger;
	var dur = m.accelMassFiltered.lincurve(0, 2.0, 2, 1, -2).asInteger;
	Pdef(m.ptn).set(\amp, amp.dbamp);
	Pdef(m.ptn).set(\octave, oct);
	Pdef(m.ptn).set(\dur, dur);
	Pdef(m.ptn).set(\ptch, 1);
};

~curtainNext = {|d, ctx|
	var amp = m.rrateMassFiltered.lincurve(0, 1.0, -70, -30, -4);
	Pdef(m.ptn).set(\amp, amp.dbamp);
	Pdef(m.ptn).set(\octave, [4,5].choose);
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
~plot = {|d,p| [m.accelMassFiltered] };
