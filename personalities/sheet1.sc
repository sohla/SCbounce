var m = ~model;
var synth;
m.midiChannel = 1;
//------------------------------------------------------------
// intial state
//------------------------------------------------------------

SynthDef(\sheet1, { |out, frq=111, gate=0, amp = 1|
	var env = EnvGen.ar(Env.asr(0.3,1.0,2.0), gate, doneAction:Done.freeSelf);
	var sig = Saw.ar(frq.lag(2),0.3 * env * amp.lag(1));

	Out.ar(out, sig);
}).add;


~init = ~init <> {

	synth = Synth(\sheet1, [\frq, 140.rrand(80), \gate, 1]);


};
~stop = {
	"stop".postln;
	synth.set(\gate,0);
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


	// if(state == true,{
	// 	synth = Synth(\sheet1, [\frq, 140.rrand(80), \gate,1]);
	// 	{		 synth.set(\gate,0)}.defer(0.1);
	//
	// 	},{
	//
	// });
};


//------------------------------------------------------------
// do all the work(logic) taking data in and playing pattern/synth
//------------------------------------------------------------
~next = {|d|

	var a = m.accelMass * m.accelMass * m.accelMass * 0.1;
	var f = 50 + (m.accelMassFiltered * 100);
	if(a<0.25,{a=0});
	if(a>0.9,{a=0.9});
	synth.set(\amp, a);

	synth.set(\frq,f);
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
	[m.accelMassFiltered * m.accelMassFiltered * 0.1];
	// [m.accelMass + m.rrateMassFiltered, m.accelMassFiltered,m.rrateMassThreshold];
	// [m.rrateMassFiltered, m.rrateMassThreshold, m.accelMassAmp];
	// [d.sensors.gyroEvent.x, d.sensors.gyroEvent.y, d.sensors.gyroEvent.z];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z];


};
