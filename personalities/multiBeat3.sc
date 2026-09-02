var m = ~model;
var group;

var beat = 1.0;
var divs = [1, 2, 4, 8] * 2;
var pool = [0, 7, 12] + 36;

var ratios    = [1, 3.0, 5.01, 7.17];
var ringAmps  = [1, 0.2, 0.6, 0.15];
var ringTimes = [2.4, 1.6, 1.0, 0.7] * 0.1;

//------------------------------------------------------------
m.accelMassFilteredAttack = 0.99;
m.accelMassFilteredDecay = 0.6;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;

//------------------------------------------------------------
SynthDef(\multiBeatWind, {|out=0, freq=220, amp=0.2, pan=0, gate=1,
    attack=0.02, decay=0.1, sustain=0.2,release=0.9, ffreq=4000|
	var env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate, doneAction: 2);
	var tone = SinOsc.ar(freq * [1,1.004] * 0.5 * SinOsc.ar(8).range(0.95, 1.05), 0, 0.2);
	var exc = BrownNoise.ar(0.03);
	var sig = DynKlank.ar(`[freq * ratios, ringAmps, ringTimes], exc) ;
	sig = LPF.ar(sig, ffreq.clip(80, 12000));
	sig = sig + DelayC.ar(sig, 0.03, [0.02, 0.027]) + tone;
	Out.ar(out, Pan2.ar(sig * env * amp, pan));
}).add;

//------------------------------------------------------------
~init = ~init <> {
	group = Group.new;

	Pdef(m.ptn,
		Pbind(
			\instrument, \multiBeatWind,
			\group, group,

			\div,  Pswitch(divs.collect({ |n| Pn(n, n) }),              Pkey(\divIdx)),
			\step, Pswitch(divs.collect({ |n| Pseries(0, 1, n) }),      Pkey(\divIdx)),
			\note, Pswitch(divs.collect({ |n| Pseq(pool.keep(n), 1) }), Pkey(\divIdx)),
			\octave, 3,
			\dur,  Pkey(\div).reciprocal * beat,
			\legato, 1.0,
			\attack, 0.06,
			\release, 1.2,
			\pan, Pwhite(-0.6, 0.6),

			\type, \customVisualEvent,
			\shape, \line,
			\numPoints, 24,
			\sx, (Pkey(\step) / Pkey(\div) * 1.5) - 0.75,
			\ex, Pkey(\sx),
			\sy, Pfunc({ |e| (e[\note] + (e[\root] ? 0)).linlin(-3, 15, 0.6, -0.6) }),
			\ey, Pkey(\sy),
			\startSize, Pkey(\dur) * 320,
			\endSize, Pkey(\dur) * 320,
			\startColor, Color.new(0.49, 1.0, 0.63),
			\endColor, Color.new(0.05, 0.35, 0.15).alpha_(0.0),
			\startWidth, (Pkey(\amp) * 26) + 1,
			\endWidth, 0.4,
			\duration, Pkey(\dur) * 3,
			\modulation, Pfunc({ |e|
				(freq: 0.4, amp: (e[\ffreq] ? 2000).linlin(700, 6000, 1, 14),
					harmonics: 2, type: \normal)
			}),

			\args, #[],
		)
	);

	Pdef(m.ptn).set(\divIdx, 0);
	Pdef(m.ptn).set(\amp, 0);
	Pdef(m.ptn).set(\ffreq, 2000);
	Pdef(m.ptn).set(\root, 0);

	Pdef(m.ptn).play(quant: 1);
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
	};
};

//------------------------------------------------------------
~next = {|d|
	var idx = m.accelMassFiltered.lincurve(0, 1.5, 0, divs.size - 1, 1)
		.round.asInteger.clip(0, divs.size - 1);
	var amp = m.accelMassFiltered.lincurve(0, 2.0, -80, -17, -1);
	var ffreq = m.accelMassFiltered.lincurve(0, 2.0, 200, 6000, 2);

	Pdef(m.ptn).set(\viewID, d.port);
	Pdef(m.ptn).set(\divIdx, idx);
	Pdef(m.ptn).set(\amp, amp.dbamp);
	Pdef(m.ptn).set(\ffreq, ffreq);
	Pdef(m.ptn).set(\root, m.com.root ? 0);
};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|

	// [yellow, magenta, cyan]

	// RAW values
	// Velocity
	// [d.sensors.velocity.x, d.sensors.velocity.y, d.sensors.velocity.z] * 30;
	// Acceleration
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z] * 0.1;
	// Gyro
	// [(d.sensors.gyroEvent.x / pi).fold(-0.5,0.5) * 2];//roll
	// [(d.sensors.gyroEvent.y / pi.half)];//up down
	// [(d.sensors.gyroEvent.z / pi).fold(-0.5,0.5) * 2];//left right

	// MODEL values
	// Acceleration
	// [m.accelMass, m.accelMassFiltered].lincurve(0.0,5.0,0.0,1.0,0);
	// Rotation Rate
	// [m.rrateMass, m.rrateMassFiltered].lincurve(0.0,1.0,0.0,1.0,0);

	// COMPUTED values : this file's own ~next, recomputed
	// [m.accelMassFiltered.lincurve(0, 1.5, 0, divs.size - 1, 1).round / (divs.size - 1)];//divIdx
	// [m.accelMassFiltered.lincurve(0, 2.0, -80, -17, -1).dbamp];//amp
	// [m.accelMassFiltered.lincurve(0, 2.0, 200, 6000, 2) / 6000];//ffreq
	// [(m.com.root ? 0) * 0.1];//root

	[m.accelMass, m.accelMassFiltered, (m.com.root ? 0) * 0.1];
};
