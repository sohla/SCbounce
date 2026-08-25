/*
gestures:    [beat, shake, tilt]
description: Pdef pattern firing per-note dulcimer sample synths on ~beatClock; fixed 2-bar melody pattern with pool-driven pitch; state controls amp/octave/ptch, gesture drives amp + octave (piece) or amp only (idle/curtain)
sound:       hammered-dulcimer repeated-note phrase; melody hovers on top of ~scoreVoicePool with dips to lower voices; sample-based; tuning bends smoothly into pitch via \ptch
pitch:       score voice pool, offset-from-top per pattern step (offsets [0,1,2,3]); octave-folded into [C3..C5]; \octave state-driven; \ptch = sample rate multiplier for continuous bend (tuning ramps 0.7 → 1.0 over 15 s)
rhythm:      fixed 2-bar 16th-note pattern from patternOffset array; phase offset = 4 for pickup alignment; runs on ~beatClock always, amp gated per state
instruments: [Cellaris]
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
var loading = false;   // true while ~init is waiting on the sample reads;
                       // ~deinit clears it so a load in flight bails out
                       // instead of building an unreachable Pdef. See ~init.

// Unique per-env event type — see cotf_harp1.sc for rationale.
var eventTypeName = (\customEvent_ ++ m.ptn).asSymbol;

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
SynthDef(\stereoSampler, {|bufnum=0, out=0, amp=1, rate=1, ptch=1, start=0, pan=0, freq=440,
    attack=0.1, decay=0.1, sustain=0.3, release=0.8, gate=1, cutoff=20000, rq=1|
	var lr = rate * BufRateScale.kr(bufnum) * ptch;
	var env = EnvGen.kr(Env.new([0, 1, 1, 0], [attack, sustain, release]), gate, doneAction: 2);
	var te = EnvGen.kr(Env.perc(0.001, release * 1), gate, doneAction: 0);
	var sig = PlayBuf.ar(2, bufnum, rate: [lr, lr * 1.0017], startPos: start * BufFrames.kr(bufnum), loop: 0);
	var mod = SinOsc.ar(freq * 2, LFCub.ar(1,10,10), 1).tanh;
	var tone = LFTri.ar(freq * 0.5 * LFCub.ar(1,0,0.01,1), 0, 0.4).tanh * te;
	sig = (sig * mod + tone) * amp * env;
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

	loading = true;

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

	s.sync;
	postf("[dulcimer1] all buffers loaded (%) \n", samplesLib.size);

	if(loading.not or: { samplesLib.isNil },{
		postf("[dulcimer1] load cancelled — unloaded while samples were loading \n");
	},{
		// Name any file that didn't come back rather than failing silently.
		// We still build: one bad sample costs its own notes, not the seat.
		samplesLib.do({ |sample|
			if(sample.buffer.numFrames.isNil or: { sample.buffer.numFrames == 0 },{
				postf("[dulcimer1] sample failed to load : % \n", sample.name);
			});
		});

		// customEvent handler — array-aware form (same as marimba2 for
		// consistency, though dulcimer only fires single notes here).
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
			// ~note = (~note + ~root + (12 * ~octave)).asInteger;
			~freq = target.asInteger.midicps;

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

					// \note, 0,

					\root, Pfunc{ |e|
						var pos = (~beatClock.beats - phase).mod(patternLen);
						var slot = (eventStarts.indexOfGreaterThan(pos) ? patternOffset.size) - 1;
						var offset = patternOffset.wrapAt(slot.max(0));
						if(offset.isNil) {
							Rest()
						} {
							melodyMidiForOffset.(offset) - 60
						}
					},
					\args, #[],
				);
			);

			Pdef(m.ptn).play(~beatClock, quant: [~scoreBeatsPerBar * ~scoreEventsPerBeat, phase]);
			// cotf: seed envir so a stickless seat is silent — SC's Event default
			// amp is 0.1, and the ~*Next tick hooks (the only writers of \amp) run
			// only while the seat's device is enabled. Envir .set, not a Pbind key:
			// Pbind keys override the envir and would defeat the hooks' .set.
			Pdef(m.ptn).set(\amp, 0);
			// Melody now rides on \root (clock-driven Pfunc), so \note is only
			// the state ticks' offset. Seed it numeric: unset, ~note falls back
			// to the default pitch event's *Function*, and customEvent's
			// `~note + ~root + 12*~octave` silently builds a BinaryOpFunction
			// that blows up in findClosestSample's minItem ("Non Boolean in
			// test"). \piece and \curtain ticks never .set(\note, ...).
			Pdef(m.ptn).set(\note, 0);
			// Default dur = 1 (uniform 16th grid, matches the old Pfunc behaviour).
			// State hooks override this to change the melody firing rate — the
			// \note Pfunc still tracks pattern position on ~beatClock so the
			// melody continues to cycle regardless of dur.
			Pdef(m.ptn).set(\dur, 1);

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
// Room-state routing — Pdef stays playing across all states; per-state
// amp is set by the state-gated ticks so we only hear the melody during
// \piece. \silent handled one-shot here (no ~silentNext). Capture
// tuneTime on \tuning entry for any time-based envelope.
~onRoomState = {|ctx|
	switch(ctx.state,
		\idle,    { 
			Pdef(m.ptn).set(\ptch, 1.0);
		},
		\tuning,  { 
			tuneTime = TempoClock.beats; 
		},
		\piece,   { 
			Pdef(m.ptn).set(\ptch, 1.0);
					// note — clock-derived slot → pool offset → MIDI note, then
					// converted to an offset from baseMidi=60 so ~octave still
					// transposes via customEvent's `+ 12*~octave`.

		},
		\curtain, { 

		},
		\silent,  { 
			Pdef(m.ptn).set(\amp, 0); 
		}
	);
};

//------------------------------------------------------------
// State-gated ticks. Each fires at ~30 Hz while its state is current.
// dulcimer melody sits around C4 by default (baseMidi=60 + ~octave=5).
// Only \amp and \octave are state-modulatable — \dur and \note are
// Pfunc-locked in the Pbind (fixed pattern + voice-pool-driven pitch).

// Idle: quiet, wandering — pick a fresh random octave per tick so
// consecutive melody events land in different registers. Gesture
// (rotation rate) drives amp within a soft range. Melody pattern from
// the Pbind still plays underneath, spaced wider (dur 2) so it feels
// meditative. \ptch reset to 1 (in case we're arriving from \tuning
// mid-bend).
~idleNext = {|d, ctx|
	var amp = m.rrateMassFiltered.lincurve(0, 1.0, -60, -10, -4);
	var dur = m.accelMassFiltered.lincurve(0, 4.0, 2.0, 1.0, 7).asInteger;
	var atk = m.accelMassFiltered.lincurve(0, 2.0, 0.1, 0.03, 2);
	var rel = m.accelMassFiltered.lincurve(0, 2.0, 0.8, 1.3, 0.5);
	var notes = [0,4,7,9,11,12,4,17,19,24,17,12,9,5,2];
	var n = m.gyroYFiltered.lincurve(-1.0,1.0,0,notes.size,-1).asInteger;

	Pdef(m.ptn).set(\attack, atk);
	Pdef(m.ptn).set(\release, rel);
	Pdef(m.ptn).set(\amp, amp.dbamp);

	Pdef(m.ptn).set(\octave, [3, 4, 5].choose);
	Pdef(m.ptn).set(\ptch, [1,1.5].choose);  
	Pdef(m.ptn).set(\note, notes[n]);
	
	Pdef(m.ptn).set(\dur, dur);
};

// Tuning: "warming up" — hold quiet at octave 5, and over the first
// 15 s of the tuning state ramp \ptch from 0.7 up to 1.0 for a smooth
// pitch bend (starts flat, settles in tune). tuneTime captured on
// \tuning entry in ~onRoomState. After 15 s, sits at ptch = 1.
~tuningNext = {|d, ctx|
	var amp = m.rrateMassFiltered.lincurve(0, 1.0, -60, -10, -4);
	var tt = 15.0;
	var elapsed = TempoClock.beats - tuneTime;
	var dur = m.accelMassFiltered.lincurve(0, 4.0, 2.0, 1.0, 7).asInteger;

	Pdef(m.ptn).set(\amp, amp.dbamp);
	Pdef(m.ptn).set(\octave, [3, 4, 5].choose);
	Pdef(m.ptn).set(\dur, dur);

	if (elapsed < tt, {
		Pdef(m.ptn).set(\ptch, (elapsed / tt).linlin(0, 1, 0.85, 1.0));
		Pdef(m.ptn).set(\note, 2);

	}, {
		Pdef(m.ptn).set(\ptch, 1);
	});
};

// Piece: full gesture-driven amp + octave. \accelMassFiltered → amp,
// gyro Y (up/down tilt) → octave. \ptch reset to 1 so any residual bend
// from a preceding \tuning state is cleared. dur = 1 (uniform 16th grid
// matching the original patternDur design).
~pieceNext = {|d, ctx|
	var amp = m.accelMassFiltered.lincurve(0, 2.2, -80, -9, -1);
	var oct = (d.sensors.gyroEvent.y / pi.half).lincurve(-1, 1, 3, 7, 1).asInteger;
	var rel = m.accelMassFiltered.lincurve(0, 2.0, 0.8, 1.3, -1);
	var atk = m.accelMassFiltered.lincurve(0, 2.0, 0.1, 0.03, 2);

	Pdef(m.ptn).set(\amp, amp.dbamp * ctx.loudness.linlin(0, 1, 0.3, 1.0));
	Pdef(m.ptn).set(\octave, oct);
	Pdef(m.ptn).set(\attack, atk);
	Pdef(m.ptn).set(\release, rel);
	Pdef(m.ptn).set(\ptch, 1);
	Pdef(m.ptn).set(\note, 0);
	Pdef(m.ptn).set(\dur, 1);
};

// Curtain: soft farewell — quieter amp curve than idle, drop into
// lower octaves for a descending feel. Random-choose octave from a
// low pair so successive notes stagger between two registers. \ptch
// reset to 1 for safety. dur wider (3) — melody thins out as we exit.
~curtainNext = {|d, ctx|
	var amp = m.rrateMassFiltered.lincurve(0, 1.0, -70, -30, -4);
	Pdef(m.ptn).set(\amp, amp.dbamp);
	Pdef(m.ptn).set(\octave, [3, 4].choose);
	Pdef(m.ptn).set(\ptch, 1);
	Pdef(m.ptn).set(\dur, 3);
};

//------------------------------------------------------------
// Beat-aligned hooks. Empty stubs are placeholders — fill in as ideas
// arise. Basic ideas populated in ~onSection and ~onBar for testing.
~onTick    = {|ctx| 
};

~onHalf    = {|ctx|

};

~onBeat    = {|ctx| 

};

~onBar     = {|ctx|
	// "dulcimer onBar %  (sec % / phr %)".format(ctx.barIdx, ctx.sectionId, ctx.phraseId).postln;
};

~onPhrase  = {|ctx| 
};

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
