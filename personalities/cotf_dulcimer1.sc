
var m = ~model;
var ob = ~outBus ? 0; // capture NOW — ~init bodies run under topEnvironment.use
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

//------------------------------------------------------------
// dulcimer plays repeated melody notes drawn from ~scoreVoicePool. the
// pattern below is offsets FROM THE TOP of the pool: offset 0 picks the
// highest pool note (~melody), offset 1 picks the second-highest, etc.
// pool.wrapAt handles overshoot by cycling — since fitted_notes carries
// multiple octaves of the same chord tones, wrapping through the array
// naturally surfaces "common notes" at varied octaves without any
// per-personality PC-frequency logic.
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

	// pick a MIDI note from ~scoreVoicePool by "offset from top" index.
	// offset 0 = pool.last (melody), 1 = pool[size-2], etc. pool.wrapAt
	// cycles when offset overshoots pool.size, so a sparse 1-note pool
	// still returns something reasonable rather than nil.
	//
	// after selection, we OCTAVE-FOLD into the dulcimer's comfortable
	// range [C3, C5] = [48, 72]. the pool often contains pitches an
	// octave or two above/below the dulcimer's sweet spot (Beethoven
	// bar 2's fitted_notes go up to MIDI 88 = E6), and blind pitch-shift
	// there sounds chipmunky. folding preserves the pitch class the
	// pool identified while keeping the actual playback register sane.
	// ~octave from ~next still transposes the whole line ±12 for
	// expression.
	var melodyMidiForOffset = { |offset|
		var pool = ~scoreVoicePool ? [69];
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

	// customEvent handler — array-aware form (same as marimba2 for
	// consistency, though dulcimer only fires single notes here).
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
				\out, ob,
				\type, \customEvent,

				\dur, Pfunc{ |e|
					var pos = (~beatClock.beats - phase).mod(patternLen);
					var slot = (eventStarts.indexOfGreaterThan(pos) ? patternOffset.size) - 1;
					patternDur.wrapAt(slot.max(0))
				},

				// note — clock-derived slot → pool offset → MIDI note, then
				// converted to an offset from baseMidi=60 so ~octave still
				// transposes via customEvent's `+ 12*~octave`.
				\note, Pfunc{ |e|
					var pos = (~beatClock.beats - phase).mod(patternLen);
					var slot = (eventStarts.indexOfGreaterThan(pos) ? patternOffset.size) - 1;
					var offset = patternOffset.wrapAt(slot.max(0));
					if(offset.isNil) {
						Rest()
					} {
						melodyMidiForOffset.(offset) - 60
					}
				},

				\root, 0,
			);
		);
		~scoreAnchorBeat = 3;

		Pdef(m.ptn).play(~beatClock, quant: [~scoreBeatsPerBar * ~scoreEventsPerBeat, phase]);

		// If the personality loads mid-run and the room isn't in \piece,
		// pause immediately so the dulcimer doesn't play during \idle
		// or \tuning while waiting for the first state transition.
		if (~roomState != \piece) { Pdef(m.ptn).pause };
	};
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	Pdef(m.ptn).remove;
	samplesLib.do({|sample|
		postf("buffer dealloc [%] \n", sample.buffer);
		sample.buffer.free;
		s.sync;
	});
};

//------------------------------------------------------------
~next = {|d|
	// dulcimer melody sits around C4 by default (baseMidi=60 + ~octave=5).
	// ~octave 4..6 lets the tilt shift the whole line ±12 semitones.
	var amp = m.accelMassFiltered.lincurve(0, 1.4, -40, -8, -1);
	var oct = (d.sensors.gyroEvent.y / pi.half).lincurve(-1, 1, 1, 6, 1).asInteger;
	Pdef(m.ptn).set(\amp, amp.dbamp);
	Pdef(m.ptn).set(\octave, oct);
};

//------------------------------------------------------------
// Room-state routing — pause the Pdef outside \piece so the dulcimer
// melody only plays during the actual piece. On \piece, resume with
// the same [barLen, phase] quant as ~init so the pattern re-aligns.
~onRoomState = {|ctx|
	switch(ctx.state,
		\idle,    { Pdef(m.ptn).pause; },
		\tuning,  { Pdef(m.ptn).pause; },
		\piece,   { Pdef(m.ptn).resume(~beatClock, quant: [~scoreBeatsPerBar * ~scoreEventsPerBeat, phase]); },
		\curtain, { /* let ~curtainNext fade; pause happens on next state change */ },
	);
};

//------------------------------------------------------------
// State-gated ticks — ~next always runs (gesture → amp/octave), these
// override amp per state so silence is enforced regardless of gesture.
~idleNext    = {|d| Pdef(m.ptn).set(\amp, 0); };
~tuningNext  = {|d| Pdef(m.ptn).set(\amp, 0); };
~pieceNext   = {|d| /* gesture-driven amp already set by ~next */ };
~curtainNext = {|d| Pdef(m.ptn).set(\amp, -60.dbamp); };

//------------------------------------------------------------
// Beat-aligned hooks. Empty stubs are placeholders — fill in as ideas
// arise. Basic ideas populated in ~onSection and ~onBar for testing.
~onTick    = {|ctx| /* every subdivision */ };
~onHalf    = {|ctx| /* on half-bar change */ };
~onBeat    = {|ctx| /* every true beat */ };
~onBar     = {|ctx|
	// "dulcimer onBar %  (sec % / phr %)".format(ctx.barIdx, ctx.sectionId, ctx.phraseId).postln;
};
~onPhrase  = {|ctx| /* on phrase change */ };
~onSection = {|ctx|
	// Character shift per section: scale the pattern's amp base — quieter
	// in intro/coda, fuller in dev sections. Applied on top of ~next's
	// gesture amp so it acts as a section-wide loudness envelope.
	switch(ctx.sectionId,
		"intro",  { Pdef(m.ptn).set(\amp, -18.dbamp); },
		"A",      { Pdef(m.ptn).set(\amp, -12.dbamp); },
		"dev",    { Pdef(m.ptn).set(\amp,  -8.dbamp); },
		"B",      { Pdef(m.ptn).set(\amp, -10.dbamp); },
		"recap",  { Pdef(m.ptn).set(\amp, -12.dbamp); },
		"coda",   { Pdef(m.ptn).set(\amp, -20.dbamp); }
	);
};
~onChord   = {|ctx| /* on chord change */ };
~onKey     = {|ctx| /* on key change */ };
~onScale   = {|ctx| /* on active_scale change */ };

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|
	[(d.sensors.gyroEvent.y / pi.half)];
};
