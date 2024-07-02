var m = ~model;
m.midiChannel = 3;

//------------------------------------------------------------
// intial state
//------------------------------------------------------------

~init = ~init <> {

	Pdef(m.ptn,
		Pbind(
			\note, Pseq([0,16,4,19].stutter(1),inf),
			\root, Pseq([0,0,-5,-3,0,0,-5,-3,-1,-1,0,0,-5,-3,0,0,-5,-3,2,2].stutter(8),inf),
			\func, Pfunc({|e| ~onEvent.(e)}),
			\args, #[],
		);
	);

	Pdef(m.ptn).set(\dur,0.17);
	Pdef(m.ptn).set(\octave,3);
	Pdef(m.ptn).set(\amp,0.8);
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

	// var vel = 100;
	// var note = 60 + m.com.root - 24	;

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

	var oct = ((0.2 + m.rrateMassFiltered.cubed) * 25).mod(2).floor;

	// Pdef(m.ptn).set(\dur,(m.accelMassFiltered * 1 * m.rrateMassThreshold.reciprocal).reciprocal);
	Pdef(m.ptn).set(\amp, 0.3);
	// Pdef(m.ptn).set(\octave, 2 + oct);
	// Pdef(m.ptn).set(\root, m.com.root);

	// ((m.accelMassFiltered * 2.0 * m.rrateMassThreshold.reciprocal).reciprocal).postln;
};

~nextMidiOut = {|d|
	// m.midiOut.control(m.midiChannel, 1, m.accelMassFiltered * 64 );
};

//------------------------------------------------------------
// plot with min and max
//------------------------------------------------------------
~plotMin = -1;
~plotMax = 1;

~plot = { |d,p|
	[d.sensors.rrateEvent.sumabs * 0.1, m.rrateMass * 10, m.accelMassFiltered];
	// [m.accelMass, m.accelMassFiltered,m.accelMassAmpThreshold];
	// [m.accelMass + m.rrateMassFiltered, m.accelMassFiltered,m.rrateMassThreshold];
	// [m.rrateMassFiltered, m.rrateMassThreshold, m.accelMassAmp];
	// [d.sensors.gyroEvent.x, d.sensors.gyroEvent.y, d.sensors.gyroEvent.z];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z];


};

