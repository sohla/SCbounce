/*
gestures:    [beat, shake, tilt]
description: Pdef pattern firing per-note dulcimer sample synths; fixed 2-bar melody pattern with pool-driven pitch; gesture drives amp + octave
sound:       hammered-dulcimer repeated-note phrase; melody hovers on top of a local voice pool with dips to lower voices; sample-based
pitch:       local voice pool, offset-from-top per pattern step (offsets [0,1,2,3]); octave-folded into [C3..C5]; \octave from tilt
rhythm:      fixed 2-bar 16th-note pattern from patternOffset array; phase offset = 4 for pickup alignment
instruments: [Cellaris]
*/

var m = ~model;
var phase = 4;
var group;   // dedicated Group for this personality's synths
// bar length in clock beats. the conductor supplied this as
// ~scoreBeatsPerBar * ~scoreEventsPerBeat; standalone it is local.
var barLen = 16;

// the conductor used to hand pitch material down as ~scoreVoicePool —
// absolute MIDI notes fitted from the score. standalone, the pool is a
// local array of MIDI notes, low to high (the melody is the top of it).
var voicePool = [55, 59, 62, 67, 71, 74];

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

// dulcimer filename parser. filename shape:
//   "IL Hmr Dlcmr-<articulation> <letter><accidental?><octave><variant>"
//
// examples:
//   "IL Hmr Dlcmr-hrd A1b"   → letter A, octave 1, variant b (ignored)
//   "IL Hmr Dlcmr-hrd F#2a"  → letter F, accidental #, octave 2, variant a
//
// structural new elements vs previous libraries:
//   (a) articulation is embedded in the stem (hrd/med/sft/mtd/trill/I-V-I)
//       — we filter by articulation before loading. this is the same
//       shape as marimba's dyn+layer selectors but with different labels.
//   (b) each pitch has round-robin VARIANT LETTERS at the end (a/b/c/d)
//       for multiple mic/take samples. we strip those with a trailing
//       [a-z]? in the regex, so all variants of the same pitch resolve to
//       the same MIDI note (findClosestSample will pick whichever was
//       loaded first).
//   (c) coverage is G-major diatonic (A B C D E F# G) across octaves 1-4,
//       so chromatic pitches like Bb, C#, Eb, F, G# get pitch-shifted
//       from the nearest neighbor via findClosestSample — always ≤1
//       semitone shift because the gaps are only whole tones.
var parseDulcimerNote = { |fileStem|
	var pattern = "([A-G](#|b)?[0-9])[a-z]?$";
	var parts = fileStem.findRegexp(pattern);
	if(parts.size < 2, { Error("Invalid dulcimer note format: %".format(fileStem)).throw });
	noteToMidi.(parts[1][1])
};

// pick articulation. "hrd" = hard mallets, the main open tone.
// switch to "med" / "sft" / "mtd" for softer attacks, "trill" for
// pre-trilled samples, or "I-V-I" for the chord/cadence set.
var sampleFilter = "Dlcmr-hrd";
var folder = PathName("~/Music/cotf_samples/Celtic Hammered Dulcimer");
var samplesLib;

// Unique per-env event type.
var eventTypeName = (\customEvent_ ++ m.ptn).asSymbol;

//------------------------------------------------------------
// dulcimer plays repeated melody notes drawn from the voice pool. the
// pattern below is offsets FROM THE TOP of the pool: offset 0 picks the
// highest pool note (melody), offset 1 picks the second-highest, etc.
// pool.wrapAt handles overshoot by cycling.
//
// the rhythmic pattern hovers on the melody (offset 0) with occasional
// dips down to lower voices for variation — that's the characteristic
// hammered-dulcimer repeated-note phrase. 2 bars = 32 uniform 16ths.
//
// bar 1: hover on melody, dip 1-2 tones down for gracenotes
// bar 2: descend a bit further, then return
var patternOffset = [
	0, 0, 0, 1,   0, 0, 0, 2,   0, 1, 0, 0,   2, 1, 0, 0,   // bar 1
	0, 0, 1, 2,   0, 1, 0, 3,   2, 1, 0, 0,   1, 2, 1, 0    // bar 2
];
// NB: `1 ! N` gives an Array of N integer 1s (scalars). `[1] ! N` would give
// N copies of the array `[1]`, whose .sum is `[N]` (array-broadcast add) — that
// leaked an array into patternLen and blew up eventStarts.indexOfGreaterThan.
var patternDur = 1 ! patternOffset.size;   // uniform 16th grid

var patternLen  = patternDur.sum;              // 32 clock beats = 2 music bars
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
	// ptch multiplies the sample playback rate — continuous pitch bend.
	// ptch < 1 = lower/slower; ptch > 1 = higher/faster.
	var lr = rate * BufRateScale.kr(bufnum) * ptch;
	var env = EnvGen.kr(Env.new([0, 1, 1, 0], [attack, sustain, release]), doneAction: 2);
	var sig = PlayBuf.ar(2, bufnum, rate: [lr, lr * 1.0017], startPos: start * BufFrames.kr(bufnum), loop: 0);
	sig = Balance2.ar(sig[0], sig[1], pan, amp * env);
	Out.ar(out, sig);
}).add;

//------------------------------------------------------------
// visual : one mark per hammer strike. the phrase is a fixed 2-bar
// pattern, so the score is that pattern laid out left to right — the
// mark walks the canvas as the phrase cycles and drops down whenever
// the melody dips to a lower voice. Rests draw nothing.
// Atlas grammar G8 (event-triggered).
//
//   slot in 2-bar phrase -> horizontal position   (\sx -> \ex)
//   pool offset          -> vertical position     (\sy -> \ey)
//   amp                  -> mark size
~init = ~init <> {

	var findClosestSample = { |targetMidi|
		var closest = samplesLib.minItem({|sample| (sample.midiNote - targetMidi).abs });
		var semitoneDiff = targetMidi - closest.midiNote;
		(buffer: closest.buffer, rate: semitoneDiff.midiratio)
	};

	// pick a MIDI note from the voice pool by "offset from top" index.
	// offset 0 = pool.last (melody), 1 = pool[size-2], etc. pool.wrapAt
	// cycles when offset overshoots pool.size, so a sparse 1-note pool
	// still returns something reasonable rather than nil.
	//
	// after selection, we OCTAVE-FOLD into the dulcimer's comfortable
	// range [C3, C5] = [48, 72]. folding preserves the pitch class the
	// pool identified while keeping the actual playback register sane.
	// ~octave from ~next still transposes the whole line ±12 for
	// expression.
	var melodyMidiForOffset = { |offset|
		var pool = voicePool;
		var picked, lo, hi;
		lo = 48;
		hi = 72;
		if(pool.size == 0) {
			picked = 69
		} {
			picked = pool.wrapAt(pool.size - 1 - offset).asInteger;
			while({ picked > hi }, { picked = picked - 12 });
			while({ picked < lo }, { picked = picked + 12 });
		};
		picked
	};

	samplesLib = folder.entries
		.select({|path| path.fileName.contains(sampleFilter) })
		.collect({ |path|
			var midiNote = parseDulcimerNote.(path.fileNameWithoutExtension);
			var buffer = Buffer.read(s, path.fullPath, action:{|buf|
				postf("buffer alloc [%] \n", buf);
			});
			postf("loading sample : % (midi %) \n", path.fileNameWithoutExtension, midiNote);
			(name: path.fileNameWithoutExtension, buffer: buffer, midiNote: midiNote)
		});

	// customEvent handler — array-aware form (for consistency with the
	// other samplers, though dulcimer only fires single notes here).
	// Hands on to \customVisualEvent, which draws the mark and re-types
	// to \note itself.
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
			\group, group,   // route every event's synth into our group
			\type, eventTypeName,

			// note — clock-derived slot → pool offset → MIDI note, then
			// converted to an offset from baseMidi=60 so ~octave still
			// transposes via customEvent's `+ 12*~octave`.
			\note, Pfunc{ |e|
				var pos = (TempoClock.beats - phase).mod(patternLen);
				var slot = (eventStarts.indexOfGreaterThan(pos) ? patternOffset.size) - 1;
				var offset = patternOffset.wrapAt(slot.max(0));
				if(offset.isNil) {
					Rest()
				} {
					melodyMidiForOffset.(offset) - 60
				}
			},

			\root, 0,

			\shape, \circle,
			\sx, Pfunc{|e|
				var pos = (TempoClock.beats - phase).mod(patternLen);
				(pos / patternLen).linlin(0, 1, -0.85, 0.85)
			},
			\ex, Pkey(\sx),
			\sy, Pfunc{|e|
				var pos = (TempoClock.beats - phase).mod(patternLen);
				var slot = (eventStarts.indexOfGreaterThan(pos) ? patternOffset.size) - 1;
				(patternOffset.wrapAt(slot.max(0)) ? 0).linlin(0, 3, -0.5, 0.5)
			},
			\ey, Pkey(\sy),
			\startSize, Pfunc{|e| if(e.isRest, { 0 }, { (e[\amp] ? 0.2).linlin(0, 1, 30, 140) }) },
			\endSize, Pfunc{|e| if(e.isRest, { 0 }, { 8 }) },
			\startColor, Color.new(0.9, 0.8, 1.0),
			\endColor, Color.new(0.4, 0.2, 0.7).alpha_(0.0),
			\startWidth, 3,
			\endWidth, 0.4,
			\duration, 0.8,

			\args, #[],
		);
	);

	Pdef(m.ptn).play(quant: [barLen, phase]);
	Pdef(m.ptn).set(\amp, 0);
	// Default dur = 1 (uniform 16th grid). ~next overrides this to change
	// the melody firing rate — the \note Pfunc still tracks pattern
	// position on the clock so the melody continues to cycle regardless.
	Pdef(m.ptn).set(\dur, 1);
	Pdef(m.ptn).set(\octave, 5);
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
// Full gesture-driven amp + octave. accelMassFiltered → amp, gyro Y
// (up/down tilt) → octave. dur = 1 (uniform 16th grid matching the
// patternDur design).
~next = {|d|
	var amp = m.accelMassFiltered.lincurve(0, 1.4, -60, -9, -1);
	var oct = (d.sensors.gyroEvent.y / pi.half).lincurve(-1, 1, 3, 6, 1).asInteger;

	Pdef(m.ptn).set(\viewID, d.port);
	Pdef(m.ptn).set(\amp, amp.dbamp);
	Pdef(m.ptn).set(\octave, oct);
	Pdef(m.ptn).set(\ptch, 1);
	Pdef(m.ptn).set(\dur, 1);
};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|
	[(d.sensors.gyroEvent.y / pi.half)];
};
