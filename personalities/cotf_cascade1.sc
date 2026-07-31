/*
gestures:    [tilt, shake]
description: Direction-sensitive gestural instrument. Rotating gyroY down fires a falling arpeggio; rotating up fires a rising one. Activity (m.accelMassFiltered) → tier picks arpeggio length base (\low = 1, \med = 3, \high = 5 notes). Engagement (per-load integral of activity × tickDt, accumulated in idle/piece/curtain) → DEPTH: extends arpeggio length beyond tier's base as the arc advances. Hidden reveal: rapid direction reversal within 500 ms fires a CHORD instead of another arpeggio.
sound:       Harp samples. Silent at rest. Each direction change fires a scale-fragment arpeggio. Later in the arc, arpeggios lengthen. Rapid slam gestures → chord bloom.
pitch:       Root from ctx.voicePool.first + baseMidi=60; arpeggio intervals [0, 4, 7, 12, 16, 19, 24]. Reversed for down-direction. \tuning locks root to A4.
rhythm:      No Pdef — every note fires from SystemClock-scheduled Event.play, triggered by direction events in state ticks.
instruments: [Velaphone]
*/

// Uses recipes from concert_p_files.md:
//   §5  dedicated Group + idempotent ~deinit
//   §16 harp sampler + odd-note detune
//   §23 tier from activity signal — read m.accelMassFiltered directly per tick
//   §24 direction + reversal derived-gesture primitives
//   §26 hidden-layer gesture-sequence reveal (reversal → chord)
// Engagement-as-arc: one accumulator lives in each performing state's
// tick (via runCascadeStep helper with countEngagement flag). Tuning
// still triggers arpeggios but doesn't accumulate.

var m = ~model;
var ob = ~outBus ? 0;
var group;
var samplesLib;
var findSampleBuffer;

// State needed by direction detection (unavoidable — requires memory
// of previous sample) + arc accumulator + last-known-root memo.
//   engagement        — per-load arc, accumulated in idle/piece/curtain
//   currentRoot       — refreshed on ~onBeat from voicePool
//   prevY             — gyroY value from last tick (for direction diff)
//   dir               — last classified direction (\up/\down/\still)
//   lastDirChangeTime — SystemClock.seconds of last direction transition (for reversal window)
var engagement = 0;
var currentRoot = 60;
var prevY = 0;
var dir = \still;
var lastDirChangeTime = 0;

// Constants
var reversalWindow = 0.5;     // seconds
var noteInterval = 0.08;      // seconds between arpeggio notes
var tickDt = 0.033;
// var arpIntervals = [0, 4, 7, 12, 16, 19, 24];   // root, 3rd, 5th, 8ve, +3rd, +5th, +8ve
var arpIntervals = [0, 12, 24];   // root, 3rd, 5th, 8ve, +3rd, +5th, +8ve

var fireArpeggio, fireChord, runCascadeStep;

// ============================================================
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

var folder = PathName("~/Music/cotf_samples/harp");

// ============================================================
m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.9;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

// ============================================================
SynthDef(\cascadeNote, {
	|bufnum=0, out=0, amp=0.3, rate=1, gate=1,
	 attack=0.01, decay=0.1, sustain=0.7, release=1.5, pan=0|
	var lr = rate * BufRateScale.kr(bufnum);
	var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction: 2);
	var sig = PlayBuf.ar(2, bufnum, rate: [lr, lr * 1.0017], startPos: 0, loop: 0);
	sig = Balance2.ar(sig[0], sig[1], pan, amp * env);
	Out.ar(out, sig);
}).add;

// ============================================================
// noteCount base from tier, extended by lengthBonus (from engagement).
// Direction \up walks intervals ascending; \down descends.
fireArpeggio = { |root, direction, tier, ampScale = 1.0, lengthBonus = 0|
	var base = switch(tier, \low, { 1 }, \med, { 3 }, \high, { 5 });
	var noteCount = (base + lengthBonus).clip(1, arpIntervals.size);
	var releaseTime = switch(tier, \low, { 1.0 }, \med, { 1.5 }, \high, { 2.5 });
	var notes = arpIntervals.copyRange(0, noteCount - 1);
	if (direction == \down, { notes = notes.reverse });
	notes.do({ |semi, i|
		SystemClock.sched(i * noteInterval, {
			var midi = (root + semi).asInteger;
			var buf, rate;
			if (midi.odd, {
				buf = findSampleBuffer.(midi - 1);
				rate = 1.midiratio;
			}, {
				buf = findSampleBuffer.(midi);
				rate = 1;
			});
			if (buf.notNil, {
				(
					instrument: \cascadeNote,
					bufnum:     buf,
					rate:       rate,
					amp:        0.3 * ampScale,
					release:    releaseTime,
					attack:     0.01,
					pan:        (-0.2).rrand(0.2),
					out:        ob,
					group:      group,
					type:       \note,
				).play;
			});
			nil
		});
	});
};

fireChord = { |root, tier, ampScale = 1.0|
	var noteCount = switch(tier, \low, { 3 }, \med, { 4 }, \high, { 5 });
	var notes = arpIntervals.copyRange(0, noteCount - 1);
	var releaseTime = switch(tier, \low, { 2.0 }, \med, { 3.0 }, \high, { 4.0 });
	notes.do({ |semi|
		var midi = (root + semi).asInteger;
		var buf, rate;
		if (midi.odd, {
			buf = findSampleBuffer.(midi - 1);
			rate = 1.midiratio;
		}, {
			buf = findSampleBuffer.(midi);
			rate = 1;
		});
		if (buf.notNil, {
			(
				instrument: \cascadeNote,
				bufnum:     buf,
				rate:       rate,
				amp:        0.4 * ampScale,
				release:    releaseTime,
				attack:     0.02,
				pan:        (-0.3).rrand(0.3),
				out:        ob,
				group:      group,
				type:       \note,
			).play;
		});
	});
};

// runCascadeStep — direction/reversal detection + arpeggio/chord fire.
// Each performing state's tick calls this with per-state parameters.
// countEngagement=false for \tuning (direction still fires but arc
// doesn't advance).
runCascadeStep = { |ampScale = 1.0, rootOverride = nil, countEngagement = true|
	var now = SystemClock.seconds;
	var dy = m.gyroYFiltered - prevY;
	var newDir = case
		{ dy > 0.02 }    { \up }
		{ dy < 0.02.neg } { \down }
		{ true }         { \still };
	var activity = m.accelMassFiltered;

	prevY = m.gyroYFiltered;

	if (countEngagement, {
		engagement = engagement + (activity * tickDt);
	});

	if (newDir != dir and: { newDir != \still } and: { ~roomState != \silent }, {
		var isReversal = (dir != \still)
		                 and: { newDir != dir }
		                 and: { (now - lastDirChangeTime) < reversalWindow };
		var root = rootOverride ?? { currentRoot };
		var tier = case
			{ activity < 0.05 } { \low }
			{ activity < 0.5 }  { \med }
			{ true }            { \high };
		var lengthBonus = engagement.linlin(0, 300, 0, 2).round.asInteger;

		if (isReversal, {
			fireChord.(root, tier, ampScale);
		}, {
			fireArpeggio.(root, newDir, tier, ampScale, lengthBonus);
		});

		dir = newDir;
		lastDirChangeTime = now;
	});

	if (newDir == \still, { dir = \still });
};

// ============================================================
~init = ~init <> {
	findSampleBuffer = {|note|
		var b;
		samplesLib.do({|sample|
			if(sample.midiNote == note,{ b = sample.buffer });
		});
		b
	};

	samplesLib = folder.entries.collect({ |path|
		var note = path.fileNameWithoutExtension.split($_).last;
		var buffer = Buffer.read(s, path.fullPath, action:{ |buf|
			postf("buffer alloc [%] \n", buf);
		});
		postf("loading sample : % \n", path.fileNameWithoutExtension);
		(name: path.fileNameWithoutExtension, buffer: buffer, midiNote: noteToMidi.(note))
	});

	topEnvironment.use {
		group = Group.new;
	};

	// Outside the use block — the conductor dispatches this as
	// d.env.use { ~onResync.(idx) }, so it must live in the DEVICE env.
	// No Pdef here; the clock re-anchor only strands scheduled notes.
	~onResync = { |idx|
		if (group.notNil, { s.bind { group.freeAll } });
	};
};

// ============================================================
~deinit = ~deinit <> {
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

// ============================================================
~onRoomState = { |ctx|
	// Nothing to snapshot per-state — helper reads ~roomState directly
	// to suppress triggers in \silent.
};

// ============================================================
// State ticks — call runCascadeStep with per-state params. NO ~next.

~idleNext = { |d, ctx|
	// Idle: quieter arpeggios, voice-pool pitch, engagement counts.
	runCascadeStep.(0.2, nil, true);
};

~tuningNext = { |d, ctx|
	// Tuning: direction still triggers, but locked to A4 (root=69).
	// Engagement does NOT accumulate (preparation, not performance).
	runCascadeStep.(0.5, 69, false);
};

~pieceNext = { |d, ctx|
	// Piece: full amp, voice-pool pitch, engagement counts.
	runCascadeStep.(0.4, nil, true);
};

~curtainNext = { |d, ctx|
	// Curtain: fading, voice-pool pitch, engagement counts.
	runCascadeStep.(0.4, nil, true);
};

// ============================================================
~onTick    = { |ctx| };
~onHalf    = { |ctx| };
~onBeat    = { |ctx|
	currentRoot = (ctx.voicePool ? [69]).first.asInteger.wrap(0, 11) + 60;
};
~onBar     = { |ctx| };
~onPhrase  = { |ctx| };
~onSection = { |ctx|
	// Read `engagement` here for any discrete section-locked decisions.
	// Currently: lengthBonus is continuous (read live in
	// runCascadeStep), so no explicit section swap needed. Left as
	// a hook site.
};
~onChord   = { |ctx| };
~onKey     = { |ctx| };
~onScale   = { |ctx| };

// ============================================================
~plotMin = -2;
~plotMax = 100;
~plot = { |d, p|
	var dirNum = switch(dir, \down, { -1 }, \still, { 0 }, \up, { 1 });
	[engagement, m.accelMassFiltered * 30, dirNum]
};
