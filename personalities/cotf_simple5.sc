var m = ~model;
var synth;
// Wrap ~scoreVoicePool pitches into a single octave starting here (MIDI).
// 60 = C4. Change to move the wrapped octave up or down.
var baseMidi = 60;

m.accelMassFilteredAttack = 0.98;
m.accelMassFilteredDecay = 0.2;
m.rrateMassFilteredAttack = 0.95;
m.rrateMassFilteredDecay = 0.5;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
SynthDef(\simple, {|out=0, amp=0.0, freq=440, attack=0.001, decay=0.03, sustain=0.8, release=0.59, gate=1|
	var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction: Done.freeSelf);
	var sig = Saw.ar(freq,0.2,0.1) + SinOsc.ar(freq/2,0,1)!2;
    Out.ar(out, sig * env * amp.lagud(0.03, 2.1));
}).add;

//------------------------------------------------------------
// One long-lived \simple runs from ~init to ~deinit. Amp is driven every
// ~next tick (IMU rate) from accelMassFiltered; freq changes are driven
// by ~onBeat, dispatched by the conductor's score Routine on every true
// musical beat. No barAnchor walk, no beatOffset, no ~beatClock polling
// — hook timing comes from the Routine's SystemClock sleep, so fires
// land tight against the audio (offset only by s.latency in ~init).
~init = ~init <> {
	topEnvironment.use{
		// ~init runs before the score Routine ticks, so ctx doesn't exist
		// yet — read ~scoreVoicePool from topEnvironment for the first
		// note only. Later notes come from ctx.voicePool in ~onBeat.
		synth = Synth(\simple, [
			\freq,    ((~scoreVoicePool.first.asInteger % 12) + baseMidi).midicps,
			\amp,     0,
			\attack,  0.5,
			\decay,   0.1,
			\sustain, 1.0,
			\release, 1.0,
		]);
	};
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	synth.set(\gate, 0);
};

//------------------------------------------------------------
// Gesture-driven amp. Runs at ~30 Hz on AppClock (IMU rate) — timing
// is not critical, response should feel live, so no s.bind.
~next = {|d|
	var amp = (m.accelMass + m.rrateMass).lincurve(0, 2.0, -90, -2, -1);
	synth.set(\amp, amp.dbamp);
};

//------------------------------------------------------------
// Beat-locked pitch. ctx.voicePool is the current half's MIDI pitches
// (populated by the score Routine from p["fitted_notes"]). Hook body
// runs inside d.env.use, so we can't reach ~scoreVoicePool directly —
// ctx carries everything the hook needs. s.bind so the /n_set lands on
// the same s.latency timeline as the audio.
~onTick = {|ctx|
	// s.bind {
	// 	synth.set(\freq,
	// 		((ctx.voicePool.first.asInteger % 12) + baseMidi - 12).midicps);
	// };
};

~onChord = {|ctx|
// ctx.keys.postln;
	s.bind {
		synth.set(\freq,
			((ctx.voicePool.first.asInteger % 12) + baseMidi - 12).midicps);
	};
};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|
	[(m.accelMass + m.rrateMass).half.half];
};
