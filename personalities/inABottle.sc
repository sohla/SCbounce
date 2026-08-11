var m = ~model;
var synth;
var tsynth;

m.accelMassFilteredAttack = 0.3;
m.accelMassFilteredDecay = 0.1;
m.rrateMassFilteredAttack = 0.2;
m.rrateMassFilteredDecay = 0.08;
m.gyroFilteredAttack = 0.7;
m.gyroFilteredDecay = 0.7;


//------------------------------------------------------------
SynthDef(\timWind1, { |out, freq=65, gate=0, amp = 0.3, pchx=0|
	var env = EnvGen.ar(Env.asr(0.007,1.0,5.0), gate, doneAction:Done.freeSelf);
	var follow = Amplitude.kr(amp, 0.03, 0.03);
	var trig = PinkNoise.ar(0.04) * env * follow.lag(2);
	var sig =  DynKlank.ar(`[[freq, freq*2].lag(3), [1,0.4,0.3], [2, 1, 1, 1]], trig);
  	var tone = SinOsc.ar([freq * 3, freq * 0.5] * LFNoise2.ar(12,0.02,1), LFNoise2.ar(3,6),[0.04,0.4 ]* env * 6);
	var dly = DelayC.ar(sig + tone,0.03,[0.02,0.027]);
	var eq = BLowShelf.ar(dly,1000,0.4, -7).tanh;
	Out.ar(out, eq * amp.lag(0.1) * 0.2);
}).add;

//------------------------------------------------------------
SynthDef(\inabottle, { |out, frq=111, gate=0, amp = 0, dust=10, tone = 0.8, bits = 0.01|
	var env = EnvGen.ar(Env.asr(1.3,1.0,8.0), gate, doneAction:Done.freeSelf);
  var sig = GVerb.ar(
		PitchShift.ar(
			Splay.ar({Dust.ar(dust)}!10)
			,0.3
			,bits
			,mul:2
			),
		1.74,
		2,
		tone
	);
	sig = 
	Out.ar(out, sig * amp * env);
}).add;

~init = ~init <> {
	synth = Synth(\inabottle, [\frq, 140.rrand(80), \gate, 1]);
	tsynth = Synth(\timWind1, [\frq, 140.rrand(80), \gate, 1]);
};

~deinit = ~deinit <> {
	synth.set(\gate, 0);
	tsynth.set(\gate, 0);
};

//------------------------------------------------------------
~next = {|d|

  var amp = m.rrateMassFiltered.lincurve(0.0,0.1,-70,1,-8);
  var pos = (d.sensors.gyroEvent.z / pi).fold(-0.5,0.5) * 2;
  var tone = pos.lincurve(-1,1,0.1,0.91,-3);
  var bits = m.rrateMassFiltered.lincurve(0,1.5,0.01,0.9,-3);

	var tamp = ((1+m.accelMassFiltered).pow(6)-1).clip(0.0,1.0).lincurve(0.0,1.0,-70,-4,-5);
  var freq = ((d.sensors.gyroEvent.z / pi).fold(-0.5,0.5) * 2).linlin(-1.0,1.0,-10,10).lcurve;

	synth.set(\amp, amp.dbamp);
	synth.set(\tone, tone);
	synth.set(\bits, bits);

	tsynth.set(\amp, tamp.dbamp);
	tsynth.set(\freq, 140 + (freq * 70));

};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|
	// [d.sensors.quatEvent.x, d.sensors.quatEvent.y, d.sensors.quatEvent.z];
	// [m.accelMassFiltered * 0.1, d.sensors.gyroEvent.x * 0.1];
	// [m.accelMass + m.rrateMassFiltered, m.accelMassFiltered,m.rrateMassThreshold];
	// [m.rrateMassFiltered];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z];
	[m.accelMass, (1+m.accelMassFiltered).pow(2)-1];



};
