var m = ~model;
m.midiChannel = 1;

//------------------------------------------------------------
// intial state
//------------------------------------------------------------

~init = ~init <> {

	Pdef(m.ptn,
		Pbind(
			\note, Pseq([-5,0,4],inf),
			\root, Pseq([0,5,-2,3,-4,1,-5].stutter(16),inf),

			// \dur, 0.4,
			// \func, Pfunc({|e| ~onEvent.(e)}),
			\args, #[],
		);
	);
		Pdef(m.ptn).set(\dur,0.5);

	Pdef(m.ptn).play();
	// Pdef(\metronom, Pbind( \dur, 0.4, \degree, 16)).play;
	// m.ptn.postln;
};


~stop = {
	"stop".postln;
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

	// var oct = ((0.2 + m.rrateMassFiltered.cubed) * 25).mod(3).floor;
	//
	var dd = (m.accelMassFiltered * 0.5).reciprocal.squared;
	 if(dd<1,{
		if(Pdef(~model.ptn).isPlaying == false,{
			Pdef(~model.ptn).resume();
		});
	},{
		dd = 0.1;
		Pdef(m.ptn).set(\dur,dd);

		Pdef(~model.ptn).pause();

	});
	// dd.postln;


	Pdef(m.ptn).set(\dur,dd);
	// Pdef(m.ptn).set(\amp, 0.3);
	// Pdef(m.ptn).set(\octave, 4 + oct);

	// ((m.accelMassFiltered * 2.0 * m.rrateMassThreshold.reciprocal).reciprocal).postln;
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
	[m.accelMassFiltered * 0.02,m.accelMassFiltered * m.accelMassFiltered * 0.001,0];
	// [m.accelMass + m.rrateMassFiltered, m.accelMassFiltered,m.rrateMassThreshold];
	// [m.rrateMassFiltered, m.rrateMassThreshold, m.accelMassAmp];
	// [d.sensors.gyroEvent.x, d.sensors.gyroEvent.y, d.sensors.gyroEvent.z];
	// [d.sensors.rrateEvent.x, d.sensors.rrateEvent.y, d.sensors.rrateEvent.z];
	// [d.sensors.accelEvent.x, d.sensors.accelEvent.y, d.sensors.accelEvent.z];


};
