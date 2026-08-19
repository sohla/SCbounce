/*
gestures:    [beat, shake, tilt]
description: Pdef pattern firing per-note marimba chord strikes on ~beatClock; 2-bar rhythmic pattern of triads (with inversions and rests) voiced from score fitted_notes; gesture drives amp + octave; tuning uses \ptch for smooth pitch bend
sound:       marimba mallet triads; 15-strike 2-bar pattern with syncopated rests; chord voicing follows the score's actual harmony (fitted_notes union, not roman-numeral fabrication); tuning bends smoothly into pitch via \ptch
pitch:       chord ARRAY from ~scoreVoicePool via chordVoicedOffsets (up to 3 tones, octave-folded to [baseMidi±12]); patternInv drives inversion per slot; \octave state-driven; \ptch = sample rate multiplier for continuous bend (tuning ramps 0.7 → 1.0 over 15 s, applied to all three voices simultaneously)
rhythm:      fixed 2-bar pattern (patternInv + patternDur, 15 events across 32 clock beats); phase offset = 4 for pickup alignment; \dur is Pfunc-locked to patternDur (not state-modulatable)
instruments: [Gravitone]
*/

var m = ~model;
var ob = ~outBus ? 0; // capture NOW — ~init bodies run under topEnvironment.use
var phase = 4;
var tuneTime = 0;
var group;   // dedicated Group for this personality's synths — see
             // concert_p_files.md §5 (Pdef personalities need this)

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
var loading = false;   // true while ~init is waiting on the sample reads;
                       // ~deinit clears it so a load in flight bails out
                       // instead of building an unreachable Pdef. See ~init.

// Unique per-env event type — see cotf_harp1.sc for rationale.
var eventTypeName = (\customEvent_ ++ m.ptn).asSymbol;

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
~init = ~init <> {

	var findClosestSample = { |targetMidi|
		var closest = samplesLib.minItem({|sample| (sample.midiNote - targetMidi).abs });
		var semitoneDiff = targetMidi - closest.midiNote;
		(buffer: closest.buffer, rate: semitoneDiff.midiratio)
	};

	loading = true;

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

	s.sync;
	postf("[marimba2] all buffers loaded (%) \n", samplesLib.size);

	if(loading.not or: { samplesLib.isNil },{
		postf("[marimba2] load cancelled — unloaded while samples were loading \n");
	},{
		// Name any file that didn't come back rather than failing silently.
		// We still build: one bad sample costs its own notes, not the seat.
		samplesLib.do({ |sample|
			if(sample.buffer.numFrames.isNil or: { sample.buffer.numFrames == 0 },{
				postf("[marimba2] sample failed to load : % \n", sample.name);
			});
		});

		// customEvent handler — now array-aware. when ~note is an array (a chord),
		// findClosestSample is called per element, producing arrays for ~bufnum
		// and ~rate. the subsequent \note dispatch multichannel-expands into
		// one synth per element, all firing at the same clock instant.
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
					\instrument, \stereoSampler,
					\out, ob,
					\group, group,   // route every event's synth into our group
					\type, eventTypeName,

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
					// voicing up/down as ~pieceNext modulates octave.
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
					// ~octave from ~pieceNext still transposes via customEvent's math.
					\root, 0,
				);
			);

			Pdef(m.ptn).play(~beatClock, quant: [~scoreBeatsPerBar * ~scoreEventsPerBeat, phase]);
			// cotf: seed envir so a stickless seat is silent — SC's Event default
			// amp is 0.1, and the ~*Next tick hooks (the only writers of \amp) run
			// only while the seat's device is enabled. Envir .set, not a Pbind key:
			// Pbind keys override the envir and would defeat the hooks' .set.
			Pdef(m.ptn).set(\amp, 0);

		};

		// ~onResync in d.env (per-device dispatch — no cross-device clobber).
		// Body in topEnvironment.use so ~beatClock etc. resolve.
		~onResync = { |idx|
			topEnvironment.use {
				Pdef(m.ptn).stop;
				s.bind { group.freeAll };
				Pdef(m.ptn).play(~beatClock, quant: [~scoreBeatsPerBar * ~scoreEventsPerBeat, phase]);
			};
		};
	});
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	loading = false;

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
// ~next = {|d| };  // state-gated ticks handle everything

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
	var amp = m.rrateMassFiltered.lincurve(0, 1.0, -70, -25, -4);
	Pdef(m.ptn).set(\amp, amp.dbamp);
	Pdef(m.ptn).set(\octave, [4, 5, 6].choose);
	Pdef(m.ptn).set(\ptch, 1);
};

~tuningNext = {|d, ctx|
	var amp = m.rrateMassFiltered.lincurve(0, 1.0, -70, -25, -4);
	var tt = 15.0;
	var elapsed = TempoClock.beats - tuneTime;

	Pdef(m.ptn).set(\amp, amp.dbamp);
	Pdef(m.ptn).set(\octave, 5);

	if (elapsed < tt, {
		Pdef(m.ptn).set(\ptch, (elapsed / tt).linlin(0, 1, 0.7, 1.0));
	}, {
		Pdef(m.ptn).set(\ptch, 1);
	});
};

~pieceNext = {|d, ctx|
	var amp = m.accelMassFiltered.lincurve(0, 1, -40, -4, -1);
	var oct = (d.sensors.gyroEvent.y / pi.half).lincurve(-1, 1, 5, 7, 1).asInteger;

	if (m.accelMass < 0.01, {
		amp = -10;
	});

	Pdef(m.ptn).set(\amp, amp.dbamp);
	Pdef(m.ptn).set(\octave, oct);
	Pdef(m.ptn).set(\ptch, 1);
};

~curtainNext = {|d, ctx|
	var amp = m.rrateMassFiltered.lincurve(0, 1.0, -70, -32, -4);
	Pdef(m.ptn).set(\amp, amp.dbamp);
	Pdef(m.ptn).set(\octave, [3, 4].choose);
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
~plot = { |d,p|
	[m.accelMassFiltered];
};
