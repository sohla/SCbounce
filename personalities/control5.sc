var m = ~model;
m.midiChannel = 4;

//------------------------------------------------------------	
// intial state
//------------------------------------------------------------	

~init = ~init <> { 

	Pdef(m.ptn,
		Pbind(
			\note, Prand([0,2,5,4].stutter(6),inf),
			\root, Pseq([0].stutter(24),inf),
			\octave, Prand([2,3,4],inf),
			\func, Pfunc({|e| ~onEvent.(e)}),
			\args, #[],
		);
	);

	Pdef(m.ptn).set(\dur,0.5);
	// Pdef(m.ptn).set(\octave,4);
	Pdef(m.ptn).set(\amp,0.5);
	Pdef(m.ptn).play();
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

	var vel = 100;
	var note = 60 + m.com.root - 12	;

	// if(state == true,{
	// 	m.midiOut.noteOn(m.midiChannel, note  , vel);
	// },{
	// 	m.midiOut.noteOff(m.midiChannel, note, 0);
	// });
};


//------------------------------------------------------------	
// do all the work(logic) taking data in and playing pattern/synth
//------------------------------------------------------------	
~next = {|d| 

	var oct = ((0.2 + m.rrateMassFiltered.cubed) * 25).mod(3).floor;

	Pdef(m.ptn).set(\dur,(m.accelMassFiltered * 1.2 * m.rrateMassThreshold.reciprocal).reciprocal);
	Pdef(m.ptn).set(\amp, 0.6);
	// Pdef(m.ptn).set(\octave, 4 + oct);

};

~nextMidiOut = {|d|
	m.midiOut.control(m.midiChannel, 0, m.accelMassFiltered * 64 );
};			

//------------------------------------------------------------	
// plot with min and max
//------------------------------------------------------------	
~plotMin = -3;
~plotMax = 3;

~plot = { |d,p|
	// d.sensors.accelEvent.z.postln;
	// [m.accelMass, m.accelMassFiltered,m.accelMassAmpThreshold];
	// [m.accelMass + m.rrateMassFiltered, m.accelMassFiltered,m.rrateMassThreshold];
	//[m.rrateMassFiltered, m.rrateMassThreshold, m.accelMassAmp];
	//[d.sensors.gyroEvent.x, d.sensors.gyroEvent.y, d.sensors.gyroEvent.z];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	[d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z];


};
