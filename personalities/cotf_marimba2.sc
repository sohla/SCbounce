/*
gestures:    [beat, shake, tilt]
description: Pdef pattern firing per-note marimba chord strikes; 2-bar rhythmic pattern of triads (with inversions and rests) voiced from a local chord pool; gesture drives amp + octave
sound:       marimba mallet triads; 15-strike 2-bar pattern with syncopated rests
pitch:       chord ARRAY from a local voicing pool; patternInv drives inversion per slot; \octave from tilt
rhythm:      fixed 2-bar pattern (patternInv + patternDur, 15 events across 32 clock beats); phase offset = 4 for pickup alignment
instruments: [Gravitone]
*/

var m = ~model;
var phase = 4;
var group;
// bar length in clock beats. the conductor supplied this as
// ~scoreBeatsPerBar * ~scoreEventsPerBeat; standalone it is local.
var barLen = 16;

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

// Chord voicings, as offsets from MIDI 60. In the conductor build these
// were read live out of the score's fitted_notes so the chord was
// literally what the orchestra was playing. Standalone there is no
// score, so the voicings are a local pool cycled per bar. Offsets (not
// absolute pitches) so \octave still transposes the whole chord — the
// customEvent math combines offset + 12*~octave into absolute MIDI.
var chordPool = [
	[-3,  1,  4],   // A  major-ish, voiced round middle C
	[ 0,  4,  7],   // C  major
	[ 2,  5,  9],   // D  minor
	[-1,  2,  7],   // B  diminished-ish
];
var chordStep = 0;
var barTime = 0;

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

var eventTypeName = (\customEvent_ ++ m.ptn).asSymbol;

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
SynthDef(\stereoSampler, {|bufnum=0, out=0, amp=1, rate=1, ptch=1, start=0, pan=0, freq=440,
    attack=0.01, decay=0.1, sustain=0.3, release=1.2, gate=1, cutoff=20000, rq=1|
	var lr = rate * BufRateScale.kr(bufnum) * ptch;
	var env = EnvGen.kr(Env.new([0, 1, 1, 0], [attack, sustain, release]), doneAction: 2);
	var sig = PlayBuf.ar(2, bufnum, rate: [lr, lr * 1.0017], startPos: start * BufFrames.kr(bufnum), loop: 0);
	sig = Balance2.ar(sig[0], sig[1], pan, amp * env);
	Out.ar(out, sig);
}).add;

//------------------------------------------------------------
// visual : one mark per chord strike. a triad is three tones at once, so
// the mark is a triangle — inversion turns it, and the rhythmic slot
// walks it across the canvas. Rests draw nothing, so the syncopation
// reads as gaps. Atlas grammar G8 (event-triggered).
//
//   slot in 2-bar pattern -> horizontal position   (\sx -> \ex)
//   inversion             -> rotation
//   octave                -> vertical position     (\sy -> \ey)
//   amp                   -> mark size
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

	// customEvent handler — array-aware. when ~note is an array (a chord),
	// findClosestSample is called per element, producing arrays for ~bufnum
	// and ~rate. the subsequent dispatch multichannel-expands into one
	// synth per element, all firing at the same clock instant. we hand on
	// to \customVisualEvent, which draws the mark and re-types to \note.
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
		~type = \customVisualEvent;
		currentEnvironment.play;
	});

	group = Group.new;

	Pdef(m.ptn,
		Pbind(
			\instrument, \stereoSampler,
			\group, group,
			\type, eventTypeName,

			// dur — clock-derived, follows patternDur.
			\dur, Pfunc{ |e|
				var pos = (TempoClock.beats - phase).mod(patternLen);
				var slot = (eventStarts.indexOfGreaterThan(pos) ? patternInv.size) - 1;
				patternDur.wrapAt(slot.max(0))
			},

			// note — clock-derived slot → inversion index → chord ARRAY.
			// nil slot returns Rest() (silent event, still advances by dur).
			\note, Pfunc{ |e|
				var voicing = chordPool.wrapAt(chordStep);
				var pos = (TempoClock.beats - phase).mod(patternLen);
				var slot = (eventStarts.indexOfGreaterThan(pos) ? patternInv.size) - 1;
				var invIdx = patternInv.wrapAt(slot.max(0));
				if(invIdx.isNil) { Rest() } { invertChord.(voicing, invIdx) }
			},

			// root fixed at 0 — voicing already carries the correct absolute
			// pitch classes (offsets from MIDI 60 into the target register).
			// ~octave from ~next still transposes via customEvent's math.
			\root, 0,

			\shape, \triangle,
			\sx, Pfunc{|e|
				var pos = (TempoClock.beats - phase).mod(patternLen);
				(pos / patternLen).linlin(0, 1, -0.85, 0.85)
			},
			\ex, Pkey(\sx),
			\sy, Pfunc{|e| (e[\octave] ? 5).linlin(3, 7, 0.6, -0.6) },
			\ey, Pkey(\sy),
			\rotation, Pfunc{|e|
				var pos = (TempoClock.beats - phase).mod(patternLen);
				var slot = (eventStarts.indexOfGreaterThan(pos) ? patternInv.size) - 1;
				((patternInv.wrapAt(slot.max(0)) ? 0) / 3) * 2pi
			},
			\startSize, Pfunc{|e| if(e.isRest, { 0 }, { (e[\amp] ? 0.2).linlin(0, 1, 50, 200) }) },
			\endSize, Pfunc{|e| if(e.isRest, { 0 }, { 10 }) },
			\startColor, Color.new(1.0, 0.85, 0.4),
			\endColor, Color.new(0.8, 0.3, 0.0).alpha_(0.0),
			\startWidth, 4,
			\endWidth, 0.5,
			\duration, 1.2,
		);
	);

	Pdef(m.ptn).play(quant: [barLen, phase]);
	Pdef(m.ptn).set(\amp, 0);
	Pdef(m.ptn).set(\octave, 5);
	Pdef(m.ptn).set(\ptch, 1);
};

//------------------------------------------------------------
~deinit = ~deinit <> {
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
~next = {|d|
	var amp = m.accelMassFiltered.lincurve(0, 1, -40, -4, -1);
	var oct = (d.sensors.gyroEvent.y / pi.half).lincurve(-1, 1, 5, 7, 1).asInteger;

	if (m.accelMass < 0.01, {
		amp = -10;
	});

	Pdef(m.ptn).set(\viewID, d.port);
	Pdef(m.ptn).set(\amp, amp.dbamp);
	Pdef(m.ptn).set(\octave, oct);
	Pdef(m.ptn).set(\ptch, 1);

	// advance the chord once per bar
	if (TempoClock.beats > (barTime + barLen), {
		barTime = TempoClock.beats;
		chordStep = chordStep + 1;
	});
};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|
	[m.accelMassFiltered];
};
