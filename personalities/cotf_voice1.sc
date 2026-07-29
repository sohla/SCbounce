/*
gestures:    [beat, tilt, shake]
description: Grain-scale playback of a single vocal "aah" sample (G#4, MIDI 68). Pdef on ~beatClock always ticks; each event fires a short grain. EVERY Pdef param is set inline from the state ticks — Pbind holds ONLY static routing. No helper functions: each state re-declares its full mapping (amp, dur, grainDur, rate, start) in one place so behaviour is explicit and easy to change per state.
sound:       Continuous vocal texture; grainy stream when subdivisions are fine, sparse tones when slow.
pitch:       Per-state \rate — idle/tuning at source pitch (rate=1); piece from ctx.voicePool folded to ±6 semis (inline in ~pieceNext); curtain a slight bend down.
rhythm:      \dur set per state — piece uses gyroXFiltered → subDivs (inline lincurve); other states use fixed durs.
instruments: [Lumivox]
*/

var m = ~model;
var ob = ~outBus ? 0;
var group;

var samplePath = "~/Music/cotf_samples/voice/aah.wav";
var samplePitchMidi = 68;   // G#4 — intrinsic pitch of aah.wav
var sampleBuffer;

// Clock-beat durations. Piece picks by gyroXFiltered (inline in ~pieceNext).
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
~init = ~init <> {
	topEnvironment.use{
		group = Group.new;

		sampleBuffer = Buffer.read(s, samplePath.standardizePath, action:{|buf|
			postf("voice sample loaded: % (% frames, % channels) \n",
				samplePath, buf.numFrames, buf.numChannels);
		});

		// Pbind — ONLY static routing. See concert_p_files.md §22.
		Pdef(m.ptn,
			Pbind(
				\instrument, \voiceGrain,
				\out, ob,
				\group, group,
				\pan, Pwhite(-0.15, 0.15),
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

		Pdef(m.ptn).play(~beatClock, quant: ~scoreBeatsPerBar * ~scoreEventsPerBeat);
	};

	~onResync = { |idx|
		topEnvironment.use {
			Pdef(m.ptn).stop;
			s.bind { group.freeAll };
			Pdef(m.ptn).play(~beatClock, quant: ~scoreBeatsPerBar * ~scoreEventsPerBeat);
		};
	};
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
~onRoomState = { |ctx|
	switch(ctx.state,
		\idle,    { },
		\tuning,  { },
		\piece,   { },
		\curtain, { },
		\silent,  { Pdef(m.ptn).set(\amp, 0); }
	);
};

//------------------------------------------------------------
// State ticks — EVERY Pdef param set inline here, per state. No helpers.

// idle — quiet, fast grains near start of sample, sharp attack + quick decay.
~idleNext = {|d, ctx|
	Pdef(m.ptn).set(\amp,      m.accelMassFiltered.lincurve(0, 1.0, -60, -3, -4).dbamp);
	Pdef(m.ptn).set(\dur,      1);
	Pdef(m.ptn).set(\grainAtk, 0.005);
	Pdef(m.ptn).set(\grainDec, 0.055);
	Pdef(m.ptn).set(\rate,     [0.5,1,2].choose);
	Pdef(m.ptn).set(\freq,     samplePitchMidi.midicps);
	Pdef(m.ptn).set(\start,    rrand(0, 0.8));
};

// tuning — sparse, quiet, swelled attack + long tail.
~tuningNext = {|d, ctx|
	Pdef(m.ptn).set(\amp,      m.accelMassFiltered.lincurve(0, 1.0, -60, -25, -4).dbamp);
	Pdef(m.ptn).set(\dur,      4);
	Pdef(m.ptn).set(\grainAtk, 0.1);
	Pdef(m.ptn).set(\grainDec, 0.4);
	Pdef(m.ptn).set(\rate,     1);
	Pdef(m.ptn).set(\freq,     samplePitchMidi.midicps);
	Pdef(m.ptn).set(\start,    0.5);
};

// piece — full inline mapping: accel → amp/grainAtk/grainDec,
// x-tilt → subdivision, y-tilt → playhead, voicePool → rate + freq.
// `midi` captured once so \rate and \freq describe the SAME picked note.
// Sharper attack + shorter decay as motion increases (punchy on hits,
// legato when still).
~pieceNext = {|d, ctx|
	var midi = samplePitchMidi + ((ctx.voicePool ? [69]).choose.asInteger.mod(12) - samplePitchMidi.mod(12)).wrap(-6, 5);
	Pdef(m.ptn).set(\amp,      m.accelMassFiltered.lincurve(0, 2.0, -70, 0, -4).dbamp);
	Pdef(m.ptn).set(\dur,      1);
	Pdef(m.ptn).set(\grainAtk, 0.02);
	Pdef(m.ptn).set(\grainDec, 0.5);
	Pdef(m.ptn).set(\rate,     (midi - samplePitchMidi).midiratio);
	Pdef(m.ptn).set(\freq,     midi.midicps);
	Pdef(m.ptn).set(\start,    m.gyroYFiltered.clip(-1, 1).lincurve(-1, 1, 0, 0.98, 1));
};

// curtain — quiet fade, gentle attack + very long decay, slight bend down.
~curtainNext = {|d, ctx|
	Pdef(m.ptn).set(\amp,      m.accelMassFiltered.lincurve(0, 1.0, -70, -35, -4).dbamp);
	Pdef(m.ptn).set(\dur,      6);
	Pdef(m.ptn).set(\grainAtk, 0.15);
	Pdef(m.ptn).set(\grainDec, 0.6);
	Pdef(m.ptn).set(\rate,     (-3).midiratio);
	Pdef(m.ptn).set(\freq,     (samplePitchMidi - 3).midicps);
	Pdef(m.ptn).set(\start,    m.gyroYFiltered.clip(-1, 1).lincurve(-1, 1, 0.5, 0.98, 1));
};

//------------------------------------------------------------
~onTick    = { |ctx| };
~onHalf    = { |ctx| };
~onBeat    = { |ctx| };
~onBar     = { |ctx| };
~onPhrase  = { |ctx| };
~onSection = { |ctx| };
~onChord   = { |ctx| };
~onKey     = { |ctx| };
~onScale   = { |ctx| };

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p| [m.gyroXFiltered, m.accelMassFiltered] };
