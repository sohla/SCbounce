var m = ~model;
m.midiChannel = 9;
m.accelMassThreshold = 0.5;
m.rrateMassThreshold = 0.1;

//------------------------------------------------------------	
// intial state
//------------------------------------------------------------	

~init = ~init <> { 

	Pdef(m.ptn,
		Pbind(
			\note, Pseq([-5,0,4],inf),
			\root, Pseq([0,5,-2,3,-4,1,-5].stutter(16),inf),
			\func, Pfunc({|e| ~onEvent.(e)}),
			\args, #[],
		);
	);

	Pdef(m.ptn).set(\dur,0.5);
	Pdef(m.ptn).set(\octave,5);
	Pdef(m.ptn).set(\amp,0.8);
};


//------------------------------------------------------------	
// triggers
//------------------------------------------------------------	

~onEvent = {|e|
	m.com.root = e.root;
	m.com.dur = e.dur;
};

~onHit = {|state|

	var vel = 80;

	if(state == true,{
		m.midiOut.noteOn(m.midiChannel, 60 + m.com.root + 24 , vel);
	},{
		m.midiOut.noteOff(m.midiChannel, 60 + m.com.root + 24, vel);
	});
};

~onMoving = {|state|

	if(state == true,{
		Pdef(m.ptn).resume();
	},{
		Pdef(m.ptn).pause();
	});
};


//------------------------------------------------------------	
// do all the work(logic) taking data in and playing pattern/synth
//------------------------------------------------------------	
~next = {|d| 

	var oct = ((0.2 + m.rrateMassFiltered.cubed) * 25).mod(4).floor;

	Pdef(m.ptn).set(\dur,(m.accelMassFiltered * 8).reciprocal);
	Pdef(m.ptn).set(\amp, 0.4);
	Pdef(m.ptn).set(\octave, 4 + oct);

};

~nextMidiOut = {|d|
	m.midiOut.control(m.midiChannel, 0, m.rrateMassFiltered * 127 );
};			

//------------------------------------------------------------	
// plot with min and max
//------------------------------------------------------------	

~plotMin = 0;
~plotMax = 1;

~plot = { |d,p|
	[m.rrateMassFiltered, m.accelMassFiltered];
};
//------------------------------------------------------------	
// midi control
//------------------------------------------------------------	
~midiControllerValue = {|num,val|

	//[num,val].postln;

	// if(num == 4,{ threshold = 0.01 + (val * 0.99)});

	// threshold = threshold * 2;
	// midiOut.control(m.midiChannel, num, val * 127 );

};





