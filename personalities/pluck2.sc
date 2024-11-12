var m = ~model;
var synth;

m.accelMassFilteredAttack = 0.5;
m.accelMassFilteredDecay = 0.9;

//------------------------------------------------------------
SynthDef(\pluck2, { |out=0, amp=0, pch=30, frq=30, gate=0 |
	var env = EnvGen.ar(Env.asr(0.1,1.0,0.3), gate, doneAction:0);
	var sig = Impulse.ar(pch.linlin(30,300,1,30));
	var dly = Decay.ar(sig, 0.01, BrownNoise.ar(0.1));
	var plk = Pluck.ar(WhiteNoise.ar, sig, frq.reciprocal * 0.125, 0.125 * frq.reciprocal, 8, 0.9, 0.7);
	var ton = SinOsc.ar([1,1.03] * pch,0,((plk*2)+(dly*0.1))*1);
	Out.ar(out, ton+(dly*0.01) * amp * env);
}).add;

~init = ~init <> {
	synth = Synth(\pluck2, [\frq, 440.rrand(80), \gate, 1]);
};

~deinit = ~deinit <> {
	synth.free;
};

//------------------------------------------------------------
~next = {|d|

	var pch = 110 + (m.accelMass * 220);
	var frq= 40 + (m.accelMassFiltered * 50);

	synth.set(\pch,pch);
	synth.set(\frq,frq);
	synth.set(\amp, 0.3);

	if(m.accelMass < 0.15,{
		synth.set(\gate,0)},{
		synth.set(\gate,1)}
	);

}
;
//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;
~plot = { |d,p|
	// [d.sensors.quatEvent.x, d.sensors.quatEvent.y, d.sensors.quatEvent.z];
	// [m.accelMassFiltered * 0.1, d.sensors.gyroEvent.x * 0.1];
	// [m.accelMass + m.rrateMassFiltered, m.accelMassFiltered,m.rrateMassThreshold];
	// [m.rrateMassFiltered, m.rrateMass];
	[d.sensors.gyroEvent.x, d.sensors.gyroEvent.y, d.sensors.gyroEvent.z];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z];
};
