var m = ~model;
var isOn = false;

var notes = [0,12,10,0,0,12,12,10,10,9,7,7,5,2,5];
var note = notes[0];


m.midiChannel = 8;
m.accelMassThreshold = 0.9;
m.rrateMassThreshold = 0.1;



//------------------------------------------------------------	
// intial state
//------------------------------------------------------------	

~init = ~init <> { 

	Pdef(m.ptn,
		Pbind(
			\note, Pseq([0],inf),
			
			\args, #[],
		);
	);

	Pdef(m.ptn).set(\dur,0.5);
	Pdef(m.ptn).set(\octave,4);
	Pdef(m.ptn).set(\amp,0.8);

	// change notes
	Pdef(m.ptn,Pbind( 
		\note, Pseq([[-12,0],10,7,2,5,4,-3,-5],inf),
		\root, Pseq([0,5,-2,3,-4,1,-5].stutter(16),inf),
		//\root, Pseq([0,3,5,2].stutter(16),inf)
	));

};

//------------------------------------------------------------	
// triggers
//------------------------------------------------------------	
~onEvent = {|e|
};

~onHit = {|state|
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

	//Pdef(m.ptn).set(\root,m.com.root);
	Pdef(m.ptn).set(\dur,(m.accelMassFiltered * 4).reciprocal);

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

	[m.rrateMassFiltered];
};
//------------------------------------------------------------	
// midi control
//------------------------------------------------------------	
~midiControllerValue = {|num,val|

	[num,val].postln;
};
