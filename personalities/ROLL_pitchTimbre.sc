var m = ~model;
var synth;

m.accelMassFilteredAttack = 0.5;
m.accelMassFilteredDecay = 0.1;

//------------------------------------------------------------
SynthDef(\treeWind, { |out, frq=111, gate=0, amp = 0, note=0, rate = 4, mix = 0.5|
	var env = EnvGen.ar(Env.asr(1.3,1.0,0.1), gate, doneAction:2);
	var follow = Amplitude.kr(amp, 0.3, 0.5);
	// var sig = Saw.ar(frq.lag(2),0.3 * env * amp.lag(1));
	var trig = SelectXFocus.ar(mix,
	[
		Decay.ar(Impulse.ar(rate,0,0.01),2,0.1),
		PinkNoise.ar(0.005)
	],0.5);
	var sig =  DynKlank.ar(`[[50 + note.lag(0.01)].midicps, nil, [2, 1, 1, 1]], trig * env * follow);
	var dly = DelayC.ar(sig,0.03,[0.02,0.027]);
	Out.ar(out, dly);
}).add;

~init = ~init <> {
	synth = Synth(\treeWind, [\frq, 140.rrand(80), \gate, 1]);
};

~deinit = ~deinit <> {
	synth.set(\gate, 0);
};

//------------------------------------------------------------
~next = {|d|

	var a = m.rrateMassFiltered.linlin(0.0,0.1,0.0,1.0);
	var notes = [0,2,4,5,7,9,11,12];
	var index = (d.sensors.gyroEvent.y/pi).linlin(-0.5,0.5,0.0,notes.size-1); 
	var mix = (d.sensors.gyroEvent.x/pi).lincurve(-0.2,0.0,1.0,0.0,-2); 

	var rate = m.rrateMassFiltered.lincurve(0,0.2,0.1,20,-2);
	synth.set(\amp, a * (index/notes.size).lincurve(0.0,1.0,1.0,0.0,-4).min(0.2).max(0.1)*1.5);
	synth.set(\note,notes[index.floor]);
  	synth.set(\rate, rate);
  	synth.set(\mix, mix);
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
