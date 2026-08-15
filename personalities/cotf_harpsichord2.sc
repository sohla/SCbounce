/*
gestures:    [beat, tilt, shake]
description: Pdef pattern with Pslide melody drawing indices from a local voice pool; \range (from ~next's accelMassFiltered mapping) sets how many top-of-pool notes cycle through per group. Still device → single held pitch; active motion opens the slide window.
sound:       Italian harpsichord; still device → single held pitch; active motion → expanding melodic sweep across the pool
pitch:       local voice pool wrapped to pitch class, indexed by Pslide's \slideIdx. Root = 0 (note carries absolute PC). \octave from tilt (5–9). Sparse sample set covered by findClosestSample.
rhythm:      per-note; \range 1..8 from accelMassFiltered
instruments: [Clavelium]
*/

var m = ~model;
var lastTime = 0;
var group;

// the conductor used to hand pitch material down as ~scoreVoicePool.
// standalone, the pool is local.
var voicePool = [0, 2, 4, 5, 7, 9, 11];

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

var eventTypeName = (\customEvent_ ++ m.ptn).asSymbol;

// Cap for the Pslide window. Range higher than pool.size just wraps back
// to index 0 (wrapAt), which is musically the same as a smaller range.
// 8 gives enough headroom.
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
	var sig = PlayBuf.ar(2, bufnum, rate: [lr, lr * 1.0017], startPos: start * BufFrames.kr(bufnum), loop: 0);
	sig = Balance2.ar(sig[0], sig[1], pan, amp * env);
	Out.ar(out, sig);
}).add;

//------------------------------------------------------------
// visual : one mark per note. the Pslide window is the piece — how wide
// the melodic sweep is right now is exactly what the player is doing —
// so position across the canvas is the slide index and the window width
// is the mark's size. Atlas grammar G8 (event-triggered).
//
//   slide index -> horizontal position   (\sx -> \ex)
//   octave      -> vertical position     (\sy -> \ey)
//   range       -> mark size (how open the window is)
~init = ~init <> {

	// Sparse-set nearest-neighbour lookup.
	var findClosestSample = { |targetMidi|
		var closest = samplesLib.minItem({|sample| (sample.midiNote - targetMidi).abs });
		var semitoneDiff = targetMidi - closest.midiNote;
		(buffer: closest.buffer, rate: semitoneDiff.midiratio)
	};

	samplesLib = folder.entries
		.collect({ |path|
			var midiNote = parseHarpsichordNote.(path.fileNameWithoutExtension);
			var buffer = Buffer.read(s, path.fullPath, action:{|buf|
				postf("buffer alloc [%] \n", buf);
			});
			postf("loading sample : % (midi %) \n", path.fileNameWithoutExtension, midiNote);
			(name: path.fileNameWithoutExtension, buffer: buffer, midiNote: midiNote)
		});

	// customEvent handler — scalar path only (harpsichord2 plays single
	// notes, not chord arrays). If a chord variant is ever needed swap in
	// cotf_harpsichord1's array-aware handler. Hands on to
	// \customVisualEvent, which draws the mark and re-types to \note.
	Event.addEventType(eventTypeName, {|e|
		var target = ~note + ~root + (12 * ~octave);
		var found = findClosestSample.(target);
		~bufnum = found.buffer;
		~rate = found.rate;
		~type = \customVisualEvent;
		currentEnvironment.play;
	});

	group = Group.new;

	Pdef(m.ptn,
		Pbind(
			\instrument, \stereoSamplerH,
			\group, group,
			\type, eventTypeName,

			// Pslide idiom — dynamic \range windows a slice off the head of
			// the index list. step=0 keeps the window anchored at index 0,
			// so range=r cycles indices 0..r-1 repeatedly. range=1 is a
			// single-note drone (top of pool); range=8 sweeps the top 8.
			\slideIdx, Pslide(Array.series(maxRange, 0, 1), inf, Pkey(\range), 0, 0),

			// Look up the pool at slideIdx, reduce to pitch class. \root=0
			// so the customEvent math becomes pc + 12*~octave — ~octave
			// alone controls register. wrapAt handles pool.size < range
			// gracefully (small pools just repeat).
			\note, Pfunc { |e|
				var idx = (e[\slideIdx] ? 0).asInteger;
				voicePool.wrapAt(idx).asInteger.mod(12)
			},
			\root, 0,

			\shape, \square,
			\sx, Pfunc{|e| ((e[\slideIdx] ? 0) / (maxRange - 1)).linlin(0, 1, -0.8, 0.8) },
			\ex, Pkey(\sx),
			\sy, Pfunc{|e| (e[\octave] ? 6).linlin(5, 9, 0.6, -0.6) },
			\ey, Pkey(\sy),
			\startSize, Pfunc{|e| (e[\range] ? 1).linlin(1, 8, 40, 170) },
			\endSize, 10,
			\startColor, Color.new(1.0, 0.95, 0.85),
			\endColor, Color.new(0.7, 0.4, 0.1).alpha_(0.0),
			\startWidth, 4,
			\endWidth, 0.4,
			\duration, 0.9,

			\args, #[]
		);
	);

	Pdef(m.ptn).play(quant: 4);
	Pdef(m.ptn).set(\amp, 0);
	Pdef(m.ptn).set(\dur, 1);
	Pdef(m.ptn).set(\range, 1);
	Pdef(m.ptn).set(\octave, 6);
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
// THE POINT: accelMassFiltered → \range → Pslide window width.
// Still device = 1 note; harder motion opens the slide up to maxRange (8)
// pool notes cycled per group.
~next = {|d|
	var amp = m.accelMassFiltered.lincurve(0, 2.0, -45, -8, -1);
	var oct = (d.sensors.gyroEvent.y / pi.half).lincurve(-1, 1, 5, 9, 1).asInteger;
	var range = m.accelMassFiltered.lincurve(0, 2.5, 1, maxRange, -1).floor;
	var dur = m.accelMassFiltered.lincurve(0, 2.5, 2, 1, -2).asInteger;

	Pdef(m.ptn).set(\viewID, d.port);
	Pdef(m.ptn).set(\amp, amp.dbamp);
	Pdef(m.ptn).set(\octave, oct);
	Pdef(m.ptn).set(\dur, dur);
	Pdef(m.ptn).set(\range, range);
	Pdef(m.ptn).set(\ptch, 1);
};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = {|d,p| [m.accelMassFiltered] };
