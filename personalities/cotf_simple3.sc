/*
gestures:    [beat, shake, tilt]
description: Pdef pattern firing per-note Saw+Sine synths through an RLPF; gesture drives amp, pitch, filter cutoff (roll → ffreq) and dur
sound:       pulsing filtered saw+sine
pitch:       pitch class from a local voice pool, gesture-tilted octave 7–10
rhythm:      note per \dur tick; dur 1 or 2 from accel
instruments: [Template]
*/

var m = ~model;
var lastTime = 0;
var idleNotes = [0,2,4,5,7,5,4,2];
var group;   // dedicated Group for this personality's synths

// the conductor used to hand pitch material down as ctx.voicePool.
// standalone, the pool is local.
var voicePool = [0, 2, 4, 7, 9];

//------------------------------------------------------------

m.accelMassFilteredAttack = 0.7;
m.accelMassFilteredDecay = 0.2;
m.rrateMassFilteredAttack = 0.999;
m.rrateMassFilteredDecay = 0.6;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
// Simple sine synth with ADSR + detune. Per-note lifetime (self-frees
// via Done.freeSelf), driven by the Pdef in ~init.
SynthDef(\simple, {|out=0, amp=0.0, freq=440, attack=0.001, decay=0.03, sustain=0.8, release=0.59, gate=1, ffreq = 1440|
	var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction: Done.freeSelf);
	var sig = Saw.ar(freq, LFCub.ar(freq/2,0,pi),0.3) + SinOsc.ar(freq/2,0,1);
	var filter = RLPF.ar(sig, ffreq, 0.3);
    Out.ar(out, filter!2 * env * amp);
}).add;

//------------------------------------------------------------
// visual : one mark per note. the filter is the expressive control here
// — roll opens and closes it — so the mark's width is the cutoff and its
// height on the canvas is the pitch. Atlas grammar G8 (event-triggered).
//
//   freq  -> vertical position   (\sy -> \ey)
//   ffreq -> stroke weight
//   amp   -> mark size
~init = ~init <> {
	group = Group.new;

	Pdef(m.ptn,
		Pbind(
			\instrument, \simple,
			\group, group,   // route every event's synth into our group

			\type, \customVisualEvent,
			\shape, \wave,
			\sy, Pfunc{|e| (e[\freq] ? 440).explin(80, 4000, 0.7, -0.7) },
			\ey, Pkey(\sy),
			\startSize, Pfunc{|e| (e[\amp] ? 0.1).linlin(0, 0.5, 40, 200) },
			\endSize, 20,
			\startWidth, Pfunc{|e| (e[\ffreq] ? 1440).explin(400, 8000, 1, 8) },
			\endWidth, 0.4,
			\startColor, Color.new(0.6, 1.0, 0.7),
			\endColor, Color.new(0.1, 0.5, 0.3).alpha_(0.0),
			\duration, 1.0,

			\args, #[],
		);
	);

	Pdef(m.ptn).play(quant: 0);
	Pdef(m.ptn).set(\amp, 0);
	Pdef(m.ptn).set(\dur, 1);
	Pdef(m.ptn).set(\freq, 60.midicps);
	Pdef(m.ptn).set(\attack, 0.01);
	Pdef(m.ptn).set(\release, 0.8);
};

//------------------------------------------------------------
~deinit = ~deinit <> {
	Pdef(m.ptn).remove;
	// Nuclear stop on unload. freeAll wrapped in s.bind so it lands
	// after any in-flight /s_new bundle (Pbind sends at s.latency). The
	// group.free itself doesn't need latency — it targets the group
	// node which our freeAll didn't touch, so nothing races on it.
	if (group.notNil) {
		s.bind { group.freeAll };
		group.free;
		group = nil;
	};
};

//------------------------------------------------------------
~next = {|d|
	var amp = m.accelMassFiltered.lincurve(0, 2.5, -70, -8, -1);
	var oct = (d.sensors.gyroEvent.y / pi.half).lincurve(-1, 1, 4, 8, 1).asInteger;
	var pitch = voicePool.choose.asInteger.wrap(0, 11);
	var durs = [2,1,1,2] * 0.125;
	var dur = m.accelMassFiltered.lincurve(0, 2.0, 0, durs.size-1, 1).asInteger;
	var ffreq = ((d.sensors.gyroEvent.x / pi).fold(-0.5,0.5) * 2).lincurve(-1.0, 1.0, 400, 8000, 3);

	Pdef(m.ptn).set(\viewID, d.port);
	Pdef(m.ptn).set(\ffreq, ffreq);
	Pdef(m.ptn).set(\dur, durs[dur]);
	Pdef(m.ptn).set(\freq, (pitch + (12 * oct)).midicps);
	Pdef(m.ptn).set(\amp, amp.dbamp);
	Pdef(m.ptn).set(\detune, 1);
	Pdef(m.ptn).set(\attack, 0.01);
	Pdef(m.ptn).set(\release, 0.8);
};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|
	// [(m.accelMassFiltered + m.rrateMassFiltered).half, m.accelMassFiltered];
		// [(d.sensors.gyroEvent.z / pi)];//left right
	[m.gyroZFiltered.fold(-0.5,0.5).lincurve(-0.5,0.5,0,1,-1)];

};
