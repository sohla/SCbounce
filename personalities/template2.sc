var m = ~model;
var synth;
m.accelMassFilteredAttack = 0.7;
m.accelMassFilteredDecay = 0.2;


SynthDef(\template, {
    |out=0, gate=1, freq=111, amp=0.3|
	var env = EnvGen.ar(Env.perc(0.001,0.2), gate, doneAction:2);
	var sig = SinOsc.ar(freq);
	Out.ar(out, sig * env * amp);
}).add;
~init = ~init <> {
	Pdef(m.ptn,
		Pbind(
			\instrument, \template,
			\scale, Scale.major,
			\octave, 5,
			\note, Pseq((0..7), inf),
			\legato, 1,
			\amp, Pwhite(0.1,0.3, inf),
			\func, Pfunc({|e| ~onEvent.(e)}),
			\args, #[]
		);
	);

	Pdef(m.ptn).play(quant:0.1);
};
~deinit = ~deinit <> {
	Pdef(m.ptn).remove;
};

//------------------------------------------------------------
// triggers
//------------------------------------------------------------

// example feeding the community
~onEvent = {|e|
	if(e.root != m.com.root,{
		// "key change".postln;
		Pdef(m.ptn).reset;
	});
	Pdef(m.ptn).set(\root, m.com.root);
};


//------------------------------------------------------------
// do all the work(logic) taking data in and playing pattern/synth
//------------------------------------------------------------
~next = {|d|

	var dur = m.accelMassFiltered.linexp(0,2.5,0.5,0.05);
	Pdef(m.ptn).set(\dur, dur);

	if(m.accelMassFiltered > 0.1,{
		if( Pdef(m.ptn).isPlaying.not,{
			Pdef(m.ptn).resume(quant:0.1);
		});
	},{
		if( Pdef(m.ptn).isPlaying,{
			Pdef(m.ptn).pause();
		});
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
	// [d.sensors.rrateEvent.x, m.rrateMass * 0.1, m.accelMassFiltered * 0.5];
	[m.accelMass * 0.1, m.accelMassFiltered.linlin(0,3,0,1)];
	// [m.rrateMassFiltered, m.rrateMassThreshold];
	// [m.rrateMassFiltered, m.rrateMassThreshold, m.accelMassAmp];
	// [d.sensors.gyroEvent.x, d.sensors.gyroEvent.y, d.sensors.gyroEvent.z];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z];


};




