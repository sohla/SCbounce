var m = ~model;
m.midiChannel = 9;

//------------------------------------------------------------	
// pattern
//------------------------------------------------------------	
Pdef(m.ptn,
	Pbind(
		\note, Pseq([0,5,9],inf),
		\root, Pseq([0,5,-2,3].stutter(16),inf),
		\args, #[],
	);
);

//------------------------------------------------------------	
// intial state
//------------------------------------------------------------	

~init = ~init <> { 
	Pdef(m.ptn).set(\dur,0.5);
	Pdef(m.ptn).set(\octave,5);
	Pdef(m.ptn).set(\amp,0.8);
	Pdef(m.ptn).set(\type,\midi);
	Pdef(m.ptn).set(\midiout,m.midiOut);
	Pdef(m.ptn).set(\chan,m.midiChannel);
	Pdef(m.ptn).play();
};


//------------------------------------------------------------	
// triggers
//------------------------------------------------------------	
~onHit = {|state|

	var ch = 9;
	var n = [0].choose;
	var vel = 50;

	// if(state == true,{
	// 	m.midiOut.noteOn(m.midiChannel, 60 + n, vel);
	// },{
	// 	m.midiOut.noteOff(m.midiChannel, 60 + n, vel);
	// });
};

~onMoving = {|state|

};

~onAmp = {|v|
	// TODO
};

//------------------------------------------------------------	
// do all the work(logic) taking data in and playing pattern/synth
//------------------------------------------------------------	
~next = {|d| 

	var oct = ((0.2 + m.rrateMassFiltered.cubed) * 25).mod(4).floor;

	Pdef(m.ptn).set(\dur,(m.rrateMassFiltered * 14).reciprocal);
	Pdef(m.ptn).set(\amp, 0.4);
	Pdef(m.ptn).set(\octave, 4 + oct);

	m.midiOut.control(m.midiChannel, 0, m.rrateMassFiltered * 127 );
};

//------------------------------------------------------------	
// plot with min and max
//------------------------------------------------------------	

~plotMin = 0;
~plotMax = 1;

~plot = { |d,p|
	// [(m.rrateMassFiltered * 3).ceil.mod(3)];
	[m.rrateMassFiltered];
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





