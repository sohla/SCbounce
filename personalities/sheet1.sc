var m = ~model;
var synth;
//------------------------------------------------------------
// intial state
//------------------------------------------------------------
Routine{

SynthDef(\sheet1, { |out, frq=111, gate=0, amp = 0, pchx=0|
	var env = EnvGen.ar(Env.asr(0.3,1.0,2.0), gate, doneAction:Done.freeSelf);
	var follow = Amplitude.kr(amp, 0.0001, 0.1);
	// var sig = Saw.ar(frq.lag(2),0.3 * env * amp.lag(1));
	var trig = PinkNoise.ar(0.01) * env * follow;
	var sig =  DynKlank.ar(`[[30,37,42,46,49].midicps + pchx.lag(0.1).midicps, nil, [2, 1, 1, 1]], trig);
	var dly = DelayC.ar(sig,0.03,[0.02,0.027]);
	Out.ar(out, dly);
}).add;
		s.sync;
}.play;

~init = ~init <> {
	synth = Synth(\sheet1, [\frq, 140.rrand(80), \gate, 1]);
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

~onHit = {|state|

};


//------------------------------------------------------------
// do all the work(logic) taking data in and playing pattern/synth
//------------------------------------------------------------
~next = {|d|

	// var a = m.accelMassFiltered.squared.squared * 0.1;//m.accelMass * m.accelMass * m.accelMass * 0.5;
	var a = m.accelMass * 0.5;
	var f = 50 + (m.accelMassFiltered * 100);
	var pchs = [0,12,24,36,48];
	var i = (d.sensors.gyroEvent.y.abs / pi) * (pchs.size);
	// pchs[i.floor].postln;
	if(a<0.02,{a=0});
	if(a>0.9,{a=0.5});
	synth.set(\amp, a * 0.2);
	// synth.set(\frq,1 + (d.sensors.gyroEvent.y.abs));
	synth.set(\pchx,pchs[i.floor]);
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
	// [d.sensors.gyroEvent.x, d.sensors.gyroEvent.y, d.sensors.gyroEvent.z];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z];


};
