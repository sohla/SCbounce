var m = ~model;
var synth;

//------------------------------------------------------------
SynthDef(\sheet2, { |out, frq=111, gate=0, amp = 0, pchx=0|
	var env = EnvGen.ar(Env.asr(0.3,1.0,8.0), gate, doneAction:Done.freeSelf);
	var follow = Amplitude.kr(amp, 0.0001, 0.5);
	// var sig = Saw.ar(frq.lag(2),0.3 * env * amp.lag(1));
	var trig = PinkNoise.ar(0.01) * env * follow;
	var sig =  DynKlank.ar(`[[30,32,40,46,60].midicps + pchx.lag(1).midicps, nil, [3, 2, 1, 1]], trig);
	var dly = DelayC.ar(sig,0.03,[0.02,0.027]);
	Out.ar(out, dly);
}).add;

~init = ~init <> {
	synth = Synth(\sheet2, [\frq, 140.rrand(80), \gate, 1]);
};

~deinit = ~deinit <> {
	synth.free;
};

//------------------------------------------------------------
~next = {|d|

	var a = m.accelMass * 0.5;
	var f = 50 + (m.accelMassFiltered * 100);
	var pchs = [60,64,68,72] - 12;
	var i = (d.sensors.gyroEvent.y.abs / pi) * (pchs.size);
	// pchs[i.floor].postln;
	if(a<0.02,{a=0});
	if(a>0.9,{a=0.9});
	synth.set(\amp, a * 0.3);
	synth.set(\pchx,pchs[i.floor]);
};

//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|
	// [d.sensors.quatEvent.x, d.sensors.quatEvent.y, d.sensors.quatEvent.z];
	[m.accelMassFiltered * 0.1, d.sensors.gyroEvent.x * 0.1];
	// [m.accelMass + m.rrateMassFiltered, m.accelMassFiltered,m.rrateMassThreshold];
	// [m.rrateMassFiltered, m.rrateMassThreshold, m.accelMassAmp];
	// [d.sensors.gyroEvent.x, d.sensors.gyroEvent.y, d.sensors.gyroEvent.z];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z];


};

