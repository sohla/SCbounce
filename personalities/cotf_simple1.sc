/*
gestures: [beat, shake]
description: simple sin + saw synth; pitch and amp mapped directly from gestures; simple states implemented 
sound:	warm sine + saw drone with warm to buzzing timbre
pitch: first note in score voice pool
rhythm: none
instruments:    [Template]
*/

var m = ~model;
var ob = ~outBus ? 0;
var synth;
var baseMidi = 60; // C4
var lastTime = 0;
var tuneTime = 0;

//------------------------------------------------------------
// Use to tune AirSticks data filters
m.accelMassFilteredAttack = 0.98;
m.accelMassFilteredDecay = 0.99;
m.rrateMassFilteredAttack = 0.95;
m.rrateMassFilteredDecay = 0.5;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
SynthDef(\simple, {|out=0, amp=0.0, freq=440, attack=0.001, decay=0.03, sustain=0.8, release=0.59, gate=1, lagAttack=0.002, lagRelease=0.3, ffreq = 440|
	var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction: Done.freeSelf);
	var sig = Saw.ar(freq ,0.2,0.3) + SinOsc.ar(freq/2,0,1);
	var filter = RLPF.ar(sig, ffreq.lag(0.1), 0.2);
    Out.ar(out, filter!2 * env * amp.lagud(lagAttack, lagRelease));
}).add;

//------------------------------------------------------------
~init = ~init <> {
	topEnvironment.use{
		synth = Synth(\simple, [
			\out,     ob,
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
// Room-state routing — capture tuneTime on tuning entry so ~tuningNext
// can use it as an origin for any time-based envelope. Other branches
// are hook sites for one-shot state-entry logic.
~onRoomState = {|ctx|
	switch(ctx.state,
		\idle,    { 
			synth.set(\lagAttack, 0.2);
			synth.set(\lagRelease, 1.9);
			synth.set(\ffreq, 400);

		},
		\tuning,  { 
			tuneTime = TempoClock.beats;
			synth.set(\ffreq, 1000);
		},
		\piece,   { 
			synth.set(\lagAttack, 0.002);
			synth.set(\lagRelease, 0.9);
			synth.set(\ffreq, 5000);

		},
		\curtain, {
			synth.set(\lagAttack, 0.1);
			synth.set(\lagRelease, 1.9);
			synth.set(\ffreq, 500);

		},
		\silent, {
			synth.set(\amp, 0);
		}
	);
};

//------------------------------------------------------------
// State-gated ticks. Each fires at ~30 Hz while its state is current
// and .sets params on the long-lived synth.

// ~next = {|d| };  // state-gated ticks handle everything, always gets called regardless of roomState

~idleNext = {|d, ctx|
	var amp = m.accelMassFiltered.lincurve(0, 2.5, -90, -18, -1);
	synth.set(\amp, amp.dbamp);
	synth.set(\freq, 60.midicps);
};

~tuningNext = {|d, ctx|
	var amp = m.accelMassFiltered.lincurve(0, 2.5, -90, -18, -1);
	var tt = 5.0;
	synth.set(\amp, amp.dbamp);

	if( (TempoClock.beats-tuneTime) < tt, {
		var val = TempoClock.beats-tuneTime / tt;
		synth.set(\freq, val.linexp(0, 1, 60.midicps, 57.midicps));
	},{
		synth.set(\freq, 57.midicps);
	});
};

~pieceNext = {|d, ctx|
	var amp = (m.accelMass + m.rrateMass).lincurve(0, 2.0, -90, -10, -1);
	synth.set(\amp, amp.dbamp * ctx.loudness.linlin(0, 1, 0.3, 1.0));
};

~curtainNext = {|d, ctx|
	var amp = (m.accelMass + m.rrateMass).lincurve(0, 2.0, -90, -30, -1);
	synth.set(\amp, amp.dbamp);
};

//------------------------------------------------------------
// Beat-aligned hooks. ~onTick drives pitch from ctx.voicePool — hook
// runs inside d.env.use, so we read from ctx (not topEnvironment).
// s.bind so the /n_set lands on the same s.latency timeline as audio.
~onTick = {|ctx|
	
};
~onHalf    = {|ctx|
};
~onBeat    = {|ctx| 
	s.bind {
		synth.set(\freq,
			((ctx.voicePool.first.asInteger % 12) + baseMidi - 12).midicps);
	};

};
~onBar     = {|ctx| /* every downbeat */ };
~onPhrase  = {|ctx| /* on phrase change */ };
~onSection = {|ctx| /* on section change */ };
~onChord   = {|ctx| /* on chord change */ };
~onKey     = {|ctx| /* on key change */ };
~onScale   = {|ctx| /* on active_scale change */ };

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|
	[m.accelMass];
};
