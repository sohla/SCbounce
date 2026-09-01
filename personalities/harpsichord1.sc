/*
gestures:    [beat, shake, tilt, twist]
description: A harpsichord under three-axis control, one gesture per parameter and no parameter shared. The stream is fixed — a chord every eighth, forever — and everything expressive happens on the axes: tilt chooses which chord out of the pool, twist opens the strum from a block into a spread arpeggio, and shaking sets the level and gates the whole thing on and off. Nothing is random and nothing evolves on its own; hold the stick still in one attitude and it repeats the same chord exactly, which is the point of a keyboard instrument with no dynamics.
sound:       Italian harpsichord samples, three voices per strike, plectrum attack intact
pitch:       four triads as offsets around MIDI 60, chosen by tilt; nearest-sample lookup covers the gaps in a sparse library
rhythm:      a chord every eighth while you are moving, silence when you are not
instruments: [Clavelium]
*/

var m = ~model;
var group;
var samplesLib;

var eventTypeName = (\customEvent_ ++ m.ptn).asSymbol;

var folder = PathName("~/Downloads/cotf_samples/Harpsichord");

// Triads as offsets from MIDI 60, so \octave transposes the whole chord.
// Tilt indexes this list — four attitudes, four chords.
var chordPool = [
	[ 0,  4,  7],
	[ 2,  5,  9],
	[-3,  0,  4],
	[-1,  2,  7],
];

//------------------------------------------------------------
// note-name -> MIDI, and the library's own stem shape:
//   "Harpsi Italian  3 c#" / "Harpsi Italian  4a#" -> octave then letter,
// with the space between them optional.
var noteToMidi = { |noteName|
	var pattern = "([A-G](#|b)?)([0-9])";
	var noteNames = "C C# D D# E F F# G G# A A# B";
	var parts, note, octave, noteIndex;
	parts = noteName.findRegexp(pattern);
	if(parts.size < 3, { Error("Invalid note format: %".format(noteName)).throw });
	note = parts[1][1];
	octave = parts[3][1].asInteger;
	note = note.replace("Cb", "B").replace("Db", "C#").replace("Eb", "D#")
		.replace("Fb", "E").replace("Gb", "F#").replace("Ab", "G#").replace("Bb", "A#");
	noteIndex = noteNames.split($ ).find([note]);
	(octave + 1) * 12 + noteIndex;
};

var parseHarpsichordNote = { |fileStem|
	var parts = fileStem.findRegexp("([0-9])[ ]*([a-g](#|b)?)$");
	if(parts.size < 3, { Error("Invalid harpsichord note format: %".format(fileStem)).throw });
	noteToMidi.(parts[2][1].toUpper ++ parts[1][1]);
};

//------------------------------------------------------------
m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.5;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
SynthDef(\harpsiVoice, {|out=0, bufnum=0, amp=0.2, rate=1, start=0, pan=0,
    attack=0.003, sustain=0.3, release=1.0|
	var env = EnvGen.kr(Env.new([0, 1, 1, 0], [attack, sustain, release]), doneAction: 2);
	var sig = PlayBuf.ar(2, bufnum,
		rate: rate * BufRateScale.kr(bufnum),
		startPos: start * BufFrames.kr(bufnum), loop: 0);
	Out.ar(out, Balance2.ar(sig[0], sig[1], pan, amp * env));
}).add;

//------------------------------------------------------------
// visual : one burst per strike, thrown from the centre of the canvas.
// The Pbind holds only the geometry that never changes — the shape, the
// jitter at the centre, how long the mark lives. Everything an axis
// controls is pushed from ~next instead, so the whole mapping is one
// column of .sets you can read top to bottom against the sound.
//
// Lineage: the saturated-primary colour-field improvisation scores —
// flat fills, one hue per chord, no grey. A triangle because a triad is
// three tones struck at once. Atlas grammar G8, palette §0.5.
//
//   chord   -> hue, and the rotation of the triangle
//   amp     -> how far the burst opens, and its weight
//   strum   -> nothing visual; you hear it as the spread
~init = ~init <> {

	// Nearest-sample lookup, array-aware at the call site. The library is
	// 17 files over four octaves, so a target is never more than two
	// semitones from a real sample.
	var findClosestSample = { |targetMidi|
		var closest = samplesLib.minItem({ |sample| (sample.midiNote - targetMidi).abs });
		(buffer: closest.buffer, rate: (targetMidi - closest.midiNote).midiratio)
	};

	samplesLib = folder.entries.collect({ |path|
		var midiNote = parseHarpsichordNote.(path.fileNameWithoutExtension);
		var buffer = Buffer.read(s, path.fullPath, action: { |buf|
			postf("buffer alloc [%] \n", buf);
		});
		(name: path.fileNameWithoutExtension, buffer: buffer, midiNote: midiNote)
	});
	postf("loading harpsichord : % samples \n", samplesLib.size);

	// A chord arrives as an array of notes, so the lookup runs per element
	// and ~bufnum / ~rate come out as arrays. The dispatch multichannel
	// expands them into one synth per note, all on the same instant —
	// \strum is what pulls them apart again.
	Event.addEventType(eventTypeName, { |e|
		var target = ~note + ~root + (12 * ~octave);
		if(target.isArray) {
			~bufnum = target.collect({ |n| findClosestSample.(n).buffer });
			~rate   = target.collect({ |n| findClosestSample.(n).rate });
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
			\instrument, \harpsiVoice,
			\group, group,
			\type, eventTypeName,

			\note, Pfunc({ |e| chordPool.wrapAt(e[\chordIdx] ? 0) }),
			\root, 0,
			\dur, 0.2,
			\legato, 0.6,
			\pan, Pwhite(-0.25, 0.25),

			\shape, \triangle,
			\fill, true,
			\sx, Pwhite(-0.05, 0.05),
			\sy, Pwhite(-0.05, 0.05),
			\ex, Pkey(\sx),
			\ey, Pkey(\sy),
			\duration, 1.1,

			\args, #[],
		);
	);

	Pdef(m.ptn).play(quant: 1);
	Pdef(m.ptn).pause;

	// Seed everything ~next owns — the first events fire before it runs.
	Pdef(m.ptn).set(\amp, 0);
	Pdef(m.ptn).set(\chordIdx, 0);
	Pdef(m.ptn).set(\octave, 5);
	Pdef(m.ptn).set(\strum, 0);
	Pdef(m.ptn).set(\rotation, 0);
	Pdef(m.ptn).set(\startSize, 20);
	Pdef(m.ptn).set(\endSize, 120);
	Pdef(m.ptn).set(\startWidth, 4);
	Pdef(m.ptn).set(\endWidth, 0.3);
	Pdef(m.ptn).set(\startColor, Color.hsv(0, 1, 1, 0.8));
	Pdef(m.ptn).set(\endColor, Color.hsv(0, 1, 0.4, 0.0));
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
			samplesLib.do({ |sample|
				postf("buffer dealloc [%] \n", sample.buffer);
				sample.buffer.free;
				s.sync;
			});
			samplesLib = nil;
		};
	};
};

//------------------------------------------------------------
// One axis, one job, and none of them overlap :
//
//   tilt  (gyro Y)  -> which chord, and with it the hue and the rotation
//   twist (gyro Z)  -> strum, block chord to spread arpeggio
//   shake (accel)   -> level, register, how far the burst opens, on/off
~next = {|d|
	var chordIdx = (d.sensors.gyroEvent.y / pi.half)
		.linlin(-1, 1, 0, chordPool.size - 1).round.asInteger;
	var strum = (d.sensors.gyroEvent.z / pi).fold(-0.5, 0.5).abs.linlin(0, 0.5, 0, 0.07);
	var amp = m.accelMassFiltered.lincurve(0, 1.6, -70, -13, -1).dbamp;
	var oct = m.accelMassFiltered.lincurve(0, 1.6, 5, 6, 1).round.asInteger;
	var hue = chordIdx / chordPool.size;

	Pdef(m.ptn).set(\viewID, d.port);
	Pdef(m.ptn).set(\chordIdx, chordIdx);
	Pdef(m.ptn).set(\strum, strum);
	Pdef(m.ptn).set(\amp, amp);
	Pdef(m.ptn).set(\octave, oct);

	Pdef(m.ptn).set(\rotation, chordIdx / chordPool.size * 2pi);
	Pdef(m.ptn).set(\startSize, 20 + (amp * 160));
	Pdef(m.ptn).set(\endSize, 90 + (amp * 500));
	Pdef(m.ptn).set(\startWidth, 2 + (amp * 14));
	Pdef(m.ptn).set(\startColor, Color.hsv(hue, 0.85, 1.0, 0.8));
	Pdef(m.ptn).set(\endColor, Color.hsv(hue, 0.85, 0.35, 0.0));

	if(m.accelMassFiltered > 0.02, {
		if(Pdef(m.ptn).isPlaying.not, {
			Pdef(m.ptn).resume(quant: 0.5);
		});
	}, {
		if(Pdef(m.ptn).isPlaying, {
			Pdef(m.ptn).pause();
		});
	});
};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|
	[m.accelMassFiltered, (d.sensors.gyroEvent.y / pi.half),
		(d.sensors.gyroEvent.z / pi).fold(-0.5, 0.5)];
};
