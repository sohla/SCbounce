var m = ~model;
var synth;

m.accelMassFilteredAttack = 0.5;
m.accelMassFilteredDecay = 0.1;

//------------------------------------------------------------
SynthDef(\inabottle, { |out, frq=111, gate=0, amp = 0, dust=10, tone = 0.8, bits = 0.01|
	var env = EnvGen.ar(Env.asr(1.3,1.0,8.0), gate, doneAction:Done.freeSelf);
  var sig = GVerb.ar(
		PitchShift.ar(
			Splay.ar({Dust.ar(dust)}!100)
			,0.2
			,bits
			,mul:4
			),
		1.4,
		1,
		tone
	);
	Out.ar(out, sig * amp);
}).add;

~init = ~init <> {
	synth = Synth(\inabottle, [\frq, 140.rrand(80), \gate, 1]);
};

~deinit = ~deinit <> {
	synth.free;
};

//------------------------------------------------------------
~next = {|d|

	var amp = m.accelMassFiltered.lincurve(0,2.5,0.0,1.0,-3);
  var tone = d.sensors.gyroEvent.z.lincurve(-1,1,0.5,0.91,-3);
  var bits = m.rrateMassFiltered.lincurve(0,1.5,0.01,0.03,-3);

	synth.set(\amp, amp);
	synth.set(\tone, tone);
	synth.set(\bits, bits);
};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|
	// [d.sensors.quatEvent.x, d.sensors.quatEvent.y, d.sensors.quatEvent.z];
	// [m.accelMassFiltered * 0.1, d.sensors.gyroEvent.x * 0.1];
	// [m.accelMass + m.rrateMassFiltered, m.accelMassFiltered,m.rrateMassThreshold];
	[m.rrateMassFiltered];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z];


};
