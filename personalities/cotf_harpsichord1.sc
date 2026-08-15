/*
gestures:    [beat, shake, tilt]
description: Pdef pattern firing per-note harpsichord sample synths; 3-note chord strikes voiced from a local chord pool; gesture drives amp + octave + dur
sound:       Italian harpsichord; bright plectrum attack, gently decaying tail; 3-note chord strikes on beat
pitch:       local chord pool (offsets around baseMidi 60, capped at 3 tones); \octave from tilt (7–9); sparse sample set (A/A#/B/C#/E/G/G# across oct 2–6) covered by findClosestSample (≤3 semitone shift)
rhythm:      per-note; \dur from gesture (2 → 1)
instruments: [Clavelium]
*/

var m = ~model;
var lastTime = 0;
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
// Two structural differences vs the celesta lib:
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

var eventTypeName = (\customEvent_ ++ m.ptn).asSymbol;

// Chord voicings, as offsets from MIDI 60. In the conductor build these
// were read live out of the score's fitted_notes so the chord was
// literally what the orchestra was playing. Standalone there is no
// score, so the voicings are a local pool cycled per bar. Offsets (not
// absolute pitches) so \octave still transposes the whole chord.
var chordPool = [
	[-3,  1,  4],   // A  major-ish, voiced round middle C
	[ 0,  4,  7],   // C  major
	[ 2,  5,  9],   // D  minor
	[-1,  2,  7],   // B  diminished-ish
];
var chordStep = 0;
var barTime = 0;
var barLen = 16;

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
	var sig = PlayBuf.ar(2, bufnum, rate: [lr, lr * 1.0017], startPos: start * BufFrames.kr(bufnum), loop: 0);
	sig = Balance2.ar(sig[0], sig[1], pan, amp * env);
	Out.ar(out, sig);
}).add;

//------------------------------------------------------------
// visual : one mark per chord strike. the plectrum attack is the whole
// character of the instrument, so the mark snaps open at full width and
// closes fast. a triad is three tones at once, hence the triangle.
// Atlas grammar G8 (event-triggered).
//
//   chord in pool -> rotation
//   octave        -> vertical position   (\sy -> \ey)
//   amp           -> mark size
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

	// customEvent handler — array-aware. when ~note is an array (a chord),
	// findClosestSample runs per element and produces arrays for
	// ~bufnum/~rate; the dispatch multichannel-expands into one synth per
	// element, all firing at the same clock instant. we hand on to
	// \customVisualEvent, which draws the mark and re-types to \note.
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
			\instrument, \stereoSamplerH,
			\group, group,
			\type, eventTypeName,
			// \strum, 0.4,

			// 3-note chord voicing from the local pool, as offsets
			// relative to baseMidi 60 — so ~root is 0.
			\note, Pfunc { chordPool.wrapAt(chordStep) },
			\root, 0,

			\shape, \triangle,
			\sy, Pfunc{|e| (e[\octave] ? 8).linlin(7, 9, 0.5, -0.5) },
			\ey, Pkey(\sy),
			\rotation, Pfunc{|e| (chordStep % chordPool.size) / chordPool.size * 2pi },
			\startSize, Pfunc{|e| (e[\amp] ? 0.2).linlin(0, 1, 40, 190) },
			\endSize, 12,
			\startColor, Color.new(1.0, 0.95, 0.85),
			\endColor, Color.new(0.7, 0.4, 0.1).alpha_(0.0),
			\startWidth, 5,
			\endWidth, 0.4,
			\duration, 0.9,

			\args, #[]
		);
	);

	Pdef(m.ptn).play(quant: 4);
	Pdef(m.ptn).set(\amp, 0);
	Pdef(m.ptn).set(\dur, 2);
	Pdef(m.ptn).set(\octave, 8);
	Pdef(m.ptn).set(\ptch, 1);
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
~next = {|d|
	var amp = m.accelMassFiltered.lincurve(0, 2.0, -75, -12, -1);
	var oct = (d.sensors.gyroEvent.y / pi.half).lincurve(-1, 1, 7, 9, 1).asInteger;
	var dur = m.accelMassFiltered.lincurve(0, 2.0, 2, 1, -2).asInteger;

	Pdef(m.ptn).set(\viewID, d.port);
	Pdef(m.ptn).set(\amp, amp.dbamp);
	Pdef(m.ptn).set(\octave, oct);
	Pdef(m.ptn).set(\dur, dur);
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
~plot = {|d,p| [m.accelMassFiltered] };
