/*
gestures:    [beat, tilt, shake]
description: Grain-scale playback of a single vocal "aah" sample (G#4, MIDI 68). Pdef always ticks; each event fires a short grain. EVERY Pdef param is set inline from ~next — Pbind holds ONLY static routing.
sound:       Continuous vocal texture; grainy stream when subdivisions are fine, sparse tones when slow.
pitch:       \rate from a local voice pool folded to ±6 semis
rhythm:      \dur set from ~next. The `subDivs` table is declared but unused.
instruments: [Lumivox]
*/
/*

- too sensitive to motion
- warmer sound
*/
var m = ~model;
var group;

var samplePath = "~/Music/cotf_samples/voice/aah.wav";
var samplePitchMidi = 68;   // G#4 — intrinsic pitch of aah.wav
var sampleBuffer;

// the conductor used to hand pitch material down as ctx.voicePool.
// standalone, the pool is local.
var voicePool = [0, 3, 5, 7, 10];

// Clock-beat durations.
var subDivs = [8, 4, 2, 1, 0.5, 0.25];

m.accelMassFilteredAttack = 0.9;
m.accelMassFilteredDecay = 0.4;
m.rrateMassFilteredAttack = 0.7;
m.rrateMassFilteredDecay = 0.3;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
// \rate drives the sample playback rate (pitch shift multiplier).
// \freq is the actual playing frequency (Hz) — used inside for sub + FreqShift.
// \grainAtk / \grainDec shape the grain envelope (Env.perc). Total grain
// length = grainAtk + grainDec. \grainCurve is the perc envelope curve
// (negative = exponential decay; -4 default).
SynthDef(\voiceGrain, {|out=0, bufnum=0, amp=0.5, rate=1, freq=440, start=0,
    grainAtk=0.02, grainDec=0.28, grainCurve = -4, pan=0|
	var env = EnvGen.kr(Env.perc(grainAtk, grainDec, 1, grainCurve), doneAction: 2);
	var lr = rate * BufRateScale.kr(bufnum);
	var sig = PlayBuf.ar(2, bufnum, rate: lr, startPos: start * BufFrames.kr(bufnum), loop: 0);
	// var sub = LFTri.ar(freq * 2 , 0, 0.1);
	// sig = FreqShift.ar(sig, freq * 0.5,0,0.3) + sig + sub;
	Out.ar(out, Pan2.ar(sig, pan, amp * env));
}).add;

//------------------------------------------------------------
// visual : one mark per grain. the two things that define a grain are
// where in the sample it reads from and how long it lasts, so the
// playhead is the horizontal position and the grain envelope is the
// mark's lifetime — a fine grain stream reads as a dense scatter, a slow
// one as isolated blooms. Atlas grammar G8 (event-triggered).
//
//   start (playhead) -> horizontal position   (\sx -> \ex)
//   rate (pitch)     -> vertical position     (\sy -> \ey)
//   grainDec         -> how long the mark lives
//   amp              -> mark size
~init = ~init <> {
	group = Group.new;

	sampleBuffer = Buffer.read(s, samplePath.standardizePath, action:{|buf|
		postf("voice sample loaded: % (% frames, % channels) \n",
			samplePath, buf.numFrames, buf.numChannels);
	});

	// Pbind — ONLY static routing.
	Pdef(m.ptn,
		Pbind(
			\instrument, \voiceGrain,
			\group, group,
			\pan, Pwhite(-0.15, 0.15),

			\type, \customVisualEvent,
			\shape, \blobby,
			\sx, Pfunc{|e| (e[\start] ? 0).linlin(0, 1, -0.85, 0.85) },
			\ex, Pkey(\sx),
			\sy, Pfunc{|e| (e[\rate] ? 1).explin(0.5, 2, 0.6, -0.6) },
			\ey, Pkey(\sy),
			\startSize, Pfunc{|e| (e[\amp] ? 0.1).linlin(0, 1, 30, 200) },
			\endSize, 10,
			\startColor, Color.new(1.0, 0.7, 0.8),
			\endColor, Color.new(0.5, 0.1, 0.3).alpha_(0.0),
			\startWidth, 3,
			\endWidth, 0.4,
			\duration, Pfunc{|e| ((e[\grainAtk] ? 0.02) + (e[\grainDec] ? 0.28)) * 3 },

			\args, #[],
		);
	);

	// Static constant — set ONCE (never changes over personality life).
	Pdef(m.ptn).set(\bufnum, sampleBuffer.bufnum);
	// Silent defaults so pre-first-tick events don't scream.
	Pdef(m.ptn).set(\amp, 0);
	Pdef(m.ptn).set(\dur, 1);
	Pdef(m.ptn).set(\grainAtk, 0.02);
	Pdef(m.ptn).set(\grainDec, 0.28);
	Pdef(m.ptn).set(\rate, 1);
	Pdef(m.ptn).set(\freq, samplePitchMidi.midicps);
	Pdef(m.ptn).set(\start, 0);

	Pdef(m.ptn).play(quant: 4);
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	Pdef(m.ptn).remove;
	fork {
		if (group.notNil) {
			s.bind { group.freeAll };
			s.sync;
			group.free;
			group = nil;
		};
		if (sampleBuffer.notNil) {
			sampleBuffer.free;
			sampleBuffer = nil;
		};
	};
};

//------------------------------------------------------------
// Full inline mapping: accel → amp/grainAtk/grainDec, y-tilt → playhead,
// voicePool → rate + freq. `midi` captured once so \rate and \freq
// describe the SAME picked note. Sharper attack + shorter decay as
// motion increases (punchy on hits, legato when still).
~next = {|d|
	var midi = samplePitchMidi + (voicePool.choose.asInteger.mod(12) - samplePitchMidi.mod(12)).wrap(-6, 5);

	Pdef(m.ptn).set(\viewID,   d.port);
	Pdef(m.ptn).set(\amp,      m.accelMassFiltered.lincurve(0, 2.0, -70, 0, -4).dbamp);
	Pdef(m.ptn).set(\dur,      1);
	Pdef(m.ptn).set(\grainAtk, 0.02);
	Pdef(m.ptn).set(\grainDec, 0.5);
	Pdef(m.ptn).set(\rate,     (midi - samplePitchMidi).midiratio);
	Pdef(m.ptn).set(\freq,     midi.midicps);
	Pdef(m.ptn).set(\start,    m.gyroYFiltered.clip(-1, 1).lincurve(-1, 1, 0, 0.98, 1));
};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p| [m.gyroXFiltered, m.accelMassFiltered] };
