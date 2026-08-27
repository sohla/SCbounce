/*
gestures:    [tilt, shake]
description: Direction-sensitive gestural instrument. Rotating gyroY down fires a falling arpeggio; rotating up fires a rising one. Activity (m.accelMassFiltered) → tier picks arpeggio length base (\low = 1, \med = 3, \high = 5 notes). Engagement (per-load integral of activity × tickDt) → DEPTH: extends arpeggio length beyond tier's base as the arc advances. Hidden reveal: rapid direction reversal within 500 ms fires a CHORD instead of another arpeggio.
sound:       Harp samples. Silent at rest. Each direction change fires a scale-fragment arpeggio. Later in the arc, arpeggios lengthen. Rapid slam gestures → chord bloom.
pitch:       Root from a local voice pool + baseMidi=60; arpeggio intervals [0, 12, 24].
rhythm:      No Pdef — every note fires from SystemClock-scheduled Event.play, triggered by direction events in ~next.
instruments: [Velaphone]
*/

var m = ~model;
var group;
var samplesLib;
var findSampleBuffer;

// State needed by direction detection (unavoidable — requires memory
// of previous sample) + arc accumulator + last-known-root memo.
//   engagement        — per-load arc
//   currentRoot       — refreshed on a slow timer from the voice pool
//   prevY             — gyroY value from last tick (for direction diff)
//   dir               — last classified direction (\up/\down/\still)
//   lastDirChangeTime — SystemClock.seconds of last direction transition
var engagement = 0;
var currentRoot = 60;
var prevY = 0;
var dir = \still;
var lastDirChangeTime = 0;
var beatTime = 0;
var step = 0;
var baseMidi = 60;

// the conductor used to hand pitch material down as ctx.voicePool.
// standalone, the pool is local.
var voicePool = [0, 2, 5, 7, 9];

// Constants
var reversalWindow = 0.5;     // seconds
var noteInterval = 0.08;      // seconds between arpeggio notes
var tickDt = 0.033;
// var arpIntervals = [0, 4, 7, 12, 16, 19, 24];   // root, 3rd, 5th, 8ve, +3rd, +5th, +8ve
var arpIntervals = [0, 12, 24];

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
// visual : one mark per note. the arpeggio walks up or down the canvas
// as it walks the intervals, so the direction of the gesture is legible
// as the direction of the marks. Atlas grammar G8 (event-triggered).
//
//   note index in arpeggio -> vertical position   (\sy -> \ey)
//   direction              -> which way it walks
//   tier                   -> mark size
//   reversal (chord)       -> all marks at once, wider
fireArpeggio = { |port, root, direction, tier, ampScale = 1.0, lengthBonus = 0|
	var base = switch(tier, \low, { 1 }, \med, { 3 }, \high, { 5 });
	var noteCount = (base + lengthBonus).clip(1, arpIntervals.size);
	var releaseTime = switch(tier, \low, { 1.0 }, \med, { 1.5 }, \high, { 2.5 });
	var notes = arpIntervals.copyRange(0, noteCount - 1);
	if (direction == \down, { notes = notes.reverse });
	notes.do({ |semi, i|
		SystemClock.sched(i * noteInterval, {
			var midi = (root + semi).asInteger;
			var buf, rate, y;
			if (midi.odd, {
				buf = findSampleBuffer.(midi - 1);
				rate = 1.midiratio;
			}, {
				buf = findSampleBuffer.(midi);
				rate = 1;
			});
			y = (semi / 24).linlin(0, 1, 0.7, -0.7);
			if (buf.notNil, {
				(
					instrument: \cascadeNote,
					bufnum:     buf,
					rate:       rate,
					amp:        0.3 * ampScale,
					release:    releaseTime,
					attack:     0.01,
					pan:        (-0.2).rrand(0.2),
					group:      group,

					type:       \customVisualEvent,
					viewID:     port,
					shape:      \circle,
					sy:         y,
					ey:         y,
					startSize:  switch(tier, \low, { 60 }, \med, { 110 }, \high, { 170 }),
					endSize:    10,
					startColor: Color.new(1.0, 0.9, 0.5),
					endColor:   Color.red.alpha_(0.0),
					startWidth: 3,
					endWidth:   0.5,
					duration:   1.2,
				).play;
			});
			nil
		});
	});
};

fireChord = { |port, root, tier, ampScale = 1.0|
	var noteCount = switch(tier, \low, { 3 }, \med, { 4 }, \high, { 5 });
	var notes = arpIntervals.copyRange(0, noteCount - 1);
	var releaseTime = switch(tier, \low, { 2.0 }, \med, { 3.0 }, \high, { 4.0 });
	notes.do({ |semi|
		var midi = (root + semi).asInteger;
		var buf, rate, y;
		if (midi.odd, {
			buf = findSampleBuffer.(midi - 1);
			rate = 1.midiratio;
		}, {
			buf = findSampleBuffer.(midi);
			rate = 1;
		});
		y = (semi / 24).linlin(0, 1, 0.7, -0.7);
		if (buf.notNil, {
			(
				instrument: \cascadeNote,
				bufnum:     buf,
				rate:       rate,
				amp:        0.4 * ampScale,
				release:    releaseTime,
				attack:     0.02,
				pan:        (-0.3).rrand(0.3),
				group:      group,

				type:       \customVisualEvent,
				viewID:     port,
				shape:      \hexagon,
				sy:         y,
				ey:         y,
				startSize:  260,
				endSize:    20,
				startColor: Color.white,
				endColor:   Color.red.alpha_(0.0),
				startWidth: 5,
				endWidth:   0.5,
				duration:   2.0,
			).play;
		});
	});
};

// runCascadeStep — direction/reversal detection + arpeggio/chord fire.
runCascadeStep = { |port, ampScale = 1.0, rootOverride = nil, countEngagement = true|
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

	if (newDir != dir and: { newDir != \still }, {
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
			fireChord.(port, root, tier, ampScale);
		}, {
			fireArpeggio.(port, root, newDir, tier, ampScale, lengthBonus);
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

	group = Group.new;
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
~next = { |d|
	runCascadeStep.(d.port, 0.4, nil, true);

	if (TempoClock.beats > (beatTime + 1), {
		beatTime = TempoClock.beats;
		step = step + 1;
		currentRoot = voicePool.wrapAt(step).asInteger.wrap(0, 11) + baseMidi;
	});
};

// ============================================================
~plotMin = -2;
~plotMax = 100;
~plot = { |d, p|
	var dirNum = switch(dir, \down, { -1 }, \still, { 0 }, \up, { 1 });
	[engagement, m.accelMassFiltered * 30, dirNum]
};
