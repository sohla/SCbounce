var m = ~model;
var synth;
//------------------------------------------------------------
// intial state
//------------------------------------------------------------

SynthDef(\pluck1, { |out=0, amp=0, pch=30, frq=30, gate=0 |
	var env = EnvGen.ar(Env.asr(0.1,1.0,0.3), gate, doneAction:0);
	var sig = Impulse.ar(pch.linlin(30,300,1,30));
	var dly = Decay.ar(sig, 0.01, BrownNoise.ar(0.1));
	var plk = Pluck.ar(WhiteNoise.ar, sig, frq.reciprocal, frq.reciprocal, 8, 0.9, 0.7);
	var ton = SinOsc.ar([1,1.03] * pch,0,((plk*2)+(dly*0.1))*1);
	Out.ar(out, ton+(dly*0.01) * amp * env);
}).add;


~init = ~init <> {
	synth = Synth(\pluck1, [\frq, 140.rrand(80), \gate, 0]);
};

~play =  {
	// synth.set(\gate,1);
};

~stop = {
	// synth.set(\gate,0);
};

~deinit = ~deinit <> {
	synth.free;
};

//------------------------------------------------------------
// triggers
//------------------------------------------------------------

// example feeding the community
~onEvent = {|e|
	m.com.root = e.root;
	m.com.dur = e.dur;

};

//------------------------------------------------------------
// do all the work(logic) taking data in and playing pattern/synth
//------------------------------------------------------------
~next = {|d|

	var pch = 20 + (m.accelMass * 60);
	var frq= 210 + (m.accelMassFiltered * 100);
	synth.set(\pch,pch);
	synth.set(\frq,frq);

	synth.set(\amp, 0.8);

	if(m.accelMass < 0.2,{
		synth.set(\gate,0);
	},{
		synth.set(\gate,1);
	});

};

~nextMidiOut = {|d|
	// m.midiOut.control(m.midiChannel, 0, m.accelMassFiltered * 64 );
};

//------------------------------------------------------------
// plot with min and max
//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;

~plot = { |d,p|
	// [d.sensors.quatEvent.x, d.sensors.quatEvent.y, d.sensors.quatEvent.z];
	[m.accelMassFiltered * 0.1, d.sensors.gyroEvent.x * 0.1];
	// [m.accelMass + m.rrateMassFiltered, m.accelMassFiltered,m.rrateMassThreshold];
	// [m.rrateMassFiltered, m.rrateMassThreshold, m.accelMassAmp];
	// [d.sensors.gyroEvent.x * d.sensors.gyroEvent.y * d.sensors.gyroEvent.z * 0.1];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z];


};