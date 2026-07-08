
var m = ~model;
var phase = 4;

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

// Approach note — earlier revisions of this file tried to derive the chord
// from `chord_root` + `chord_roman`, then either fabricated a theoretical
// triad or computed intervals from ~scoreVoicePool relative to the notated
// root. Both approaches produced audibly wrong voicings: the roman-numeral
// path invented notes not in the score, and the pool-relative-to-root path
// still forced the notated root into the chord even when the score didn't
// play it that way.
//
// The visualizer's `~scoreChordFreqs` in lib/score_synth.scd solves this
// by ignoring the chord labels entirely and voicing directly from
// fitted_notes. The technique below is a direct port with two small
// changes: it returns OFFSETS from a base MIDI rather than absolute
// frequencies (so \octave still transposes the chord), and it limits the
// voicing to 3 tones for marimba clarity rather than padding to 4.

// marimba filename parser (same as cotf_marimba1).
var parseMarimbaNote = { |fileStem|
	var pattern = "([0-9])[ ]*([a-g])$";
	var parts = fileStem.findRegexp(pattern);
	var octave, letter, noteStr;
	if(parts.size < 3, { Error("Invalid marimba note format: %".format(fileStem)).throw });
	octave = parts[1][1];
	letter = parts[2][1].toUpper;
	noteStr = letter ++ octave;
	noteToMidi.(noteStr)
};

var sampleFilter = "Marimba ln mf l1x";
var folder = PathName("~/Music/cotf_samples/African Marimba");
var samplesLib;

// Voice a chord from fitted_notes. Direct port of ~scoreChordFreqs from
// lib/score_synth.scd. Key steps:
//   1. Union both halves of fitted_notes so half-bar sparsity doesn't
//      produce thin voicings (e.g. bar 2 V has E-B-G# in h0 but only E
//      in h1 — using only h0's pool would drop the third half the time).
//   2. Dedupe by pitch class, keeping each PC's SCORE-LOWEST occurrence.
//      fitted_notes is heavily octave-doubled (multiple Es, multiple Bs)
//      but has each chord tone represented only once at its lowest voice,
//      so this surfaces every unique tone without picking up doubles.
//   3. Octave-fold each unique pitch into [baseMidi - 12, baseMidi + 12]
//      so the voicing sits in a coherent register regardless of where
//      in the orchestra the pitch originated.
//   4. Return offsets from baseMidi so ~octave (from ~next) still
//      transposes the whole chord — the customEvent math takes care of
//      combining offset + 12*~octave into absolute MIDI.
//
// Fallback when the score isn't loaded or fitted_notes is empty:
// A major triad voiced around baseMidi (offsets [-3, 1, 4] = A3-C#4-E4
// when baseMidi is 60).
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
			pitches.do { |m|
				var pc = m % 12;
				if(seen.includes(pc).not) {
					seen.add(pc);
					unique.add(m);
				};
			};
			// precompute bounds outside the loop — putting `baseMidi - 12`
			// directly inside `while { v < baseMidi - 12 }` triggers a SC
			// parser quirk that reads the `-` as a binary op on the block's
			// return value (which is `true` when the condition last held),
			// giving the confusing "'-' not understood for true" error.
			// literals like the reference's `while { v < 48 }` don't hit
			// this because there's no binary op inside the condition block.
			voiced = unique.asArray.collect({ |m|
				var v = m;
				var lo = baseMidi - 12;
				var hi = baseMidi + 12;
				while { v < lo } { v = v + 12 };
				while { v > hi } { v = v - 12 };
				v;
			});
			voiced = voiced.sort;
			// keep at most 3 voices — dense chords muddy the marimba tail
			voiced = voiced.copyRange(0, (voiced.size - 1).min(2));
			result = voiced.collect({ |m| m - baseMidi })
		};
	};
	result
};

// invert a voicing array. invIdx=0 is as-voiced, invIdx=1 moves the
// lowest tone up an octave, invIdx=2 moves the two lowest tones up.
// works on any voicing size, so 3-tone triads and 4-tone tetrads invert
// the same way.
//   [0, 4, 7]        inv 1 → [4, 7, 12]         inv 2 → [7, 12, 16]
//   [-3, 1, 4]       inv 1 → [1, 4, 9]          inv 2 → [4, 9, 13]
var invertChord = { |intervals, invIdx|
	var result = intervals.copy;
	invIdx.do {
		var first = result.first;
		result = result.drop(1) ++ [first + 12]
	};
	result
};

//------------------------------------------------------------
// complex 2-bar rhythmic pattern of CHORD STRIKES. each slot is either
// an inversion index (0/1/2 → one triad hit) or nil (rest).
//
// bar 1 — root/first/second inv, breath, root/first, longer breath:
//   inv:  0    1    2   nil   0    1   nil
//   dur:  3    1    2   2     3    2   3      (dotted 8th, 16th, 8th, rest 8th, ...)
//
// bar 2 — extended sequence with syncopated 16th rests:
//   inv:  0   nil   1    2    0   nil   1    2
//   dur:  2   1     3    2    2   1     2    3
//
// 15 events, 3 rests, patternLen = 32 clock beats = 2 music bars.
var patternInv = [ 0, 1, 2, nil, 0, 1, nil,   0, nil, 1, 2, 0, nil, 1, 2];
var patternDur = [ 3, 1, 2, 2,   3, 2, 3,     2, 1,   3, 2, 2, 1,   2, 3];

var patternLen  = patternDur.sum;
var eventStarts = [0] ++ patternDur.integrate.drop(-1);

//------------------------------------------------------------

m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.5;
m.rrateMassFilteredAttack = 0.7;
m.rrateMassFilteredDecay = 0.3;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
SynthDef(\stereoSampler, {|bufnum=0, out=0, amp=1, rate=1, start=0, pan=0, freq=440,
    attack=0.01, decay=0.1, sustain=0.3, release=1.2, gate=1, cutoff=20000, rq=1|
	var lr = rate * BufRateScale.kr(bufnum);
	var env = EnvGen.kr(Env.new([0, 1, 1, 0], [attack, sustain, release]), doneAction: 2);
	var sig = PlayBuf.ar(2, bufnum, rate: [lr, lr * 1.0017], startPos: start * BufFrames.kr(bufnum), loop: 0);
	sig = Balance2.ar(sig[0], sig[1], pan, amp * env);
	Out.ar(out, sig);
}).add;

//------------------------------------------------------------
~init = ~init <> {

	var findClosestSample = { |targetMidi|
		var closest = samplesLib.minItem({|sample| (sample.midiNote - targetMidi).abs });
		var semitoneDiff = targetMidi - closest.midiNote;
		(buffer: closest.buffer, rate: semitoneDiff.midiratio)
	};

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

	// customEvent handler — now array-aware. when ~note is an array (a chord),
	// findClosestSample is called per element, producing arrays for ~bufnum
	// and ~rate. the subsequent \note dispatch multichannel-expands into
	// one synth per element, all firing at the same clock instant.
	Event.addEventType(\customEvent, {|e|
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
		Pdef(m.ptn,
			Pbind(
				\instrument, \stereoSampler,
				\type, \customEvent,

				// dur — clock-derived, follows patternDur.
				\dur, Pfunc{ |e|
					var pos = (~beatClock.beats - phase).mod(patternLen);
					var slot = (eventStarts.indexOfGreaterThan(pos) ? patternInv.size) - 1;
					patternDur.wrapAt(slot.max(0))
				},

				// note — clock-derived slot → inversion index → chord ARRAY.
				// voicing comes from fitted_notes (via chordVoicedOffsets),
				// so the chord is exactly what the score plays. no chord_root/
				// roman parsing, no fabricated triads. offsets are relative to
				// MIDI 60; customEvent's `+ 12*~octave` transposes the whole
				// voicing up/down as ~next modulates octave.
				// nil slot returns Rest() (silent event, still advances by dur).
				\note, Pfunc{ |e|
					var voicing = chordVoicedOffsets.(60);
					var pos = (~beatClock.beats - phase).mod(patternLen);
					var slot = (eventStarts.indexOfGreaterThan(pos) ? patternInv.size) - 1;
					var invIdx = patternInv.wrapAt(slot.max(0));
					if(invIdx.isNil) { Rest() } { invertChord.(voicing, invIdx) }
				},

				// root fixed at 0 — voicing already carries the correct absolute
				// pitch classes (offsets from MIDI 60 into the target register).
				// ~octave from ~next still transposes via customEvent's math.
				\root, 0,
			);
		);
		~scoreAnchorBeat = 3;

		Pdef(m.ptn).play(~beatClock, quant: [~scoreBeatsPerBar * ~scoreEventsPerBeat, phase]);
	};
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	Pdef(m.ptn).remove;
	fork{
		1.0.yield;
		samplesLib.do({|sample|
			postf("buffer dealloc [%] \n", sample.buffer);
			sample.buffer.free;
			s.sync;
		});
	};
};

//------------------------------------------------------------
~next = {|d|
	// three simultaneous synths per hit — pull amp lower than the single-note
	// personalities so the summed level stays in the same neighbourhood.
	// (3 voices summing at max amp X is roughly +9.5 dB vs one voice at X.)
	var amp = m.accelMassFiltered.lincurve(0, 1.4, -40, -4, -1);
	var oct = (d.sensors.gyroEvent.y / pi.half).lincurve(-1, 1, 4, 6, 1).asInteger;
	Pdef(m.ptn).set(\amp, amp.dbamp);
	Pdef(m.ptn).set(\octave, oct);
};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|
	[(d.sensors.gyroEvent.y / pi.half)];
};
