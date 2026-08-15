/*
gestures: [beat, shake]
description: simple sin + saw synth; pitch and amp mapped directly from gestures
sound:	warm sine + saw drone with warm to buzzing timbre
pitch: stepped through a local voice pool
rhythm: none
instruments:    [Template]
*/

var m = ~model;
var synth;
var baseMidi = 60; // C4
var lastTime = 0;
var step = 0;

// the conductor used to hand pitch material down as ~scoreVoicePool.
// standalone, the pool is local.
var voicePool = [0, 2, 4, 7, 9];

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
	synth = Synth(\simple, [
		\freq,       ((voicePool.first.asInteger % 12) + baseMidi).midicps,
		\amp,        0,
		\attack,     0.5,
		\decay,      0.1,
		\sustain,    1.0,
		\release,    1.0,
		\lagAttack,  0.002,
		\lagRelease, 0.9,
		\ffreq,      5000,
	]);
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	synth.set(\gate, 0);
};

//------------------------------------------------------------
// visual : one mark per pitch step. the drone has no discrete events of
// its own, so the step timer is the traversal — a mark is the moment the
// pitch moves. Atlas grammar G8 (event-triggered).
//
//   pitch step -> a mark appears
//   gesture    -> nothing yet (kept deliberately plain)
~next = {|d|

	var amp = (m.accelMass + m.rrateMass).lincurve(0, 2.0, -90, -10, -1);
	synth.set(\amp, amp.dbamp);

	if(TempoClock.beats > (lastTime + 2), {
		lastTime = TempoClock.beats;
		step = step + 1;
		synth.set(\freq, ((voicePool.wrapAt(step).asInteger % 12) + baseMidi - 12).midicps);

		(type: \customVisualEvent, amp: 0, dur: 0.01, viewID: d.port,
			shape: \circle,
			startSize: 60, endSize: 260,
			startColor: Color.cyan, endColor: Color.blue.alpha_(0.0),
			startWidth: 4, endWidth: 0.5,
			duration: 1.5).play;
	});
};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|
	[m.accelMass];
};
