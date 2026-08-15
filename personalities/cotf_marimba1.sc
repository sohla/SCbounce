/*
gestures:    [beat, shake, tilt]
description: Pdef pattern firing per-note marimba sample synths; single-note melody from a local voice pool; gesture drives amp + octave; nearest-sample lookup for pitch class
sound:       bright African marimba mallet strikes; mid-to-upper register (F2..B6); crisp transient, natural tail
pitch:       local voice pool wrapped to pitch class (root); \octave from tilt (6–8); \ptch = sample rate multiplier for continuous bend
rhythm:      per-note; \dur = 1 (16th grid)
instruments: [Gravitone]
*/

var m = ~model;
var lastTime = 0;
var group;   // dedicated Group for this personality's synths

// the conductor used to hand pitch material down as ~scoreVoicePool.
// standalone, the pool is local.
var voicePool = [0, 2, 4, 7, 9];

//------------------------------------------------------------
// note-name → MIDI parser. same as cotf_celesta1 — takes a normalized
// "<letter><octave>" string like "F2" or "A#3" and returns the MIDI note
// number. we normalize the marimba's "<octave><letter>" filename token
// into this shape before calling.
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
// two structural differences vs. celesta parsing:
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

// Unique per-env event type — one personality per device, so the name
// carries the pattern key to stay distinct.
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
// visual : one mark per struck bar. the marimba is a row of tuned bars,
// so the score is a row too — pitch class walks the mark across the
// canvas and octave lifts it. Atlas grammar G8 (event-triggered).
//
//   pitch class -> horizontal position   (\sx -> \ex)
//   octave      -> vertical position     (\sy -> \ey)
//   amp         -> mark size
~init = ~init <> {

	// nearest-sample lookup. the marimba has only D/F/A/B per octave —
	// that's 4 of 12 chromatic pitches, mixed odd and even, so an
	// odd/even resample trick doesn't work. instead: find the closest
	// available MIDI note in samplesLib and compute the semitone diff →
	// rate. worst-case shift is ~2 semitones, well within acceptable
	// pitch-shift range for a mallet instrument where the transient is
	// more identifying than the tail.
	var findClosestSample = { |targetMidi|
		var closest = samplesLib.minItem({|sample| (sample.midiNote - targetMidi).abs });
		var semitoneDiff = targetMidi - closest.midiNote;
		(buffer: closest.buffer, rate: semitoneDiff.midiratio)
	};

	// scan folder, filter to the chosen dynamic+layer, parse each name.
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

	// custom event handler — compute the target MIDI note, set the buffer
	// and rate, then hand on to \customVisualEvent, which draws the mark
	// and re-types to \note itself.
	Event.addEventType(eventTypeName, {|e|
		var found;
		~note = ~note + ~root + (12 * ~octave);
		found = findClosestSample.(~note);
		~bufnum = found.buffer;
		~rate = found.rate;
		~type = \customVisualEvent;
		currentEnvironment.play;
	});

	group = Group.new;

	Pdef(m.ptn,
		Pbind(
			\instrument, \stereoSampler,
			\group, group,   // route every event's synth into our group
			\type, eventTypeName,
			\note, 0,
			\root, Pfunc { voicePool.choose.wrap(0,11).asInteger - 24 },

			\shape, \square,
			\sx, Pfunc{|e| (e[\root] ? 0).wrap(0, 11).linlin(0, 11, -0.8, 0.8) },
			\ex, Pkey(\sx),
			\sy, Pfunc{|e| (e[\octave] ? 6).linlin(4, 8, 0.6, -0.6) },
			\ey, Pkey(\sy),
			\startSize, Pfunc{|e| (e[\amp] ? 0.2).linlin(0, 1, 40, 180) },
			\endSize, 10,
			\startColor, Color.new(1.0, 0.85, 0.4),
			\endColor, Color.new(0.8, 0.3, 0.0).alpha_(0.0),
			\startWidth, 4,
			\endWidth, 0.5,
			\duration, 1.0,
		);
	);

	Pdef(m.ptn).play(quant: 4);
	Pdef(m.ptn).set(\amp, 0);
	Pdef(m.ptn).set(\dur, 1);
	Pdef(m.ptn).set(\octave, 6);
	Pdef(m.ptn).set(\ptch, 1);
};

//------------------------------------------------------------
~deinit = ~deinit <> {
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
~next = {|d|
	var amp = m.accelMassFiltered.lincurve(0, 1.4, -50, -5, -1);
	var oct = (d.sensors.gyroEvent.y / pi.half).lincurve(-1, 1, 6, 8, 1).asInteger;

	Pdef(m.ptn).set(\viewID, d.port);
	Pdef(m.ptn).set(\amp, amp.dbamp);
	Pdef(m.ptn).set(\octave, oct);
	Pdef(m.ptn).set(\dur, 0.2);
	Pdef(m.ptn).set(\ptch, 1);
};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|
	[(d.sensors.gyroEvent.y / pi.half)];   // up-down tilt drives \octave
};
